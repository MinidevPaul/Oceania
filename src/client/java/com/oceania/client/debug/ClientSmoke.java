package com.oceania.client.debug;

import com.mojang.logging.LogUtils;
import com.oceania.client.config.OceaniaConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.oceania.raft.RaftEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClientSmoke {
	private static final Logger LOG = LogUtils.getLogger();
	private static final String COMMAND_FILE = "oceania-client-smoke.txt";
	private static final int POLL_TICKS = 10;
	private static final List<String> steps = new ArrayList<>();
	private static Path commandFile;
	private static long consumed;
	private static int index;
	private static int wait;
	private static int ticks;
	private static boolean started;

	private ClientSmoke() {
	}

	public static void init() {
		String script = System.getProperty("oceania.client.smoke", "");
		if (script.isBlank()) return;
		queue(script);
		LOG.info("[client smoke] {} steps queued", steps.size());
		ClientTickEvents.END_CLIENT_TICK.register(ClientSmoke::tick);
	}

	private static void queue(String script) {
		Arrays.stream(script.split("[;\\r\\n]+")).map(String::trim).filter(s -> !s.isEmpty()).forEach(steps::add);
	}

	private static void tick(Minecraft mc) {
		if (commandFile == null) {
			commandFile = mc.gameDirectory.toPath().resolve(COMMAND_FILE);
			try {
				Files.writeString(commandFile, "");
			} catch (IOException e) {
				LOG.warn("[client smoke] cannot reset {}: {}", commandFile, e.toString());
			}
		}
		if (++ticks % POLL_TICKS == 0) pollCommandFile();
		if (index >= steps.size()) return;
		if (!started) {

			boolean inWorld = mc.player != null && mc.level != null;
			if (!inWorld && !(mc.gui.screen() instanceof TitleScreen)) return;
			started = true;
			LOG.info("[client smoke] {}, starting", inWorld ? "in world" : "on the title screen");
		}
		if (wait > 0) {
			wait--;
			return;
		}
		String step = steps.get(index++);
		try {
			run(mc, step);
		} catch (Exception e) {
			LOG.error("[client smoke] step '{}' failed: {}", step, e.toString());
		}
	}

	private static void pollCommandFile() {
		try {
			if (!Files.exists(commandFile)) return;
			long size = Files.size(commandFile);
			if (size <= consumed) return;
			byte[] all = Files.readAllBytes(commandFile);
			if (all.length <= consumed) return;
			String fresh = new String(all, (int) consumed, all.length - (int) consumed, StandardCharsets.UTF_8);
			int end = fresh.lastIndexOf('\n');
			if (end < 0) return;
			consumed += fresh.substring(0, end + 1).getBytes(StandardCharsets.UTF_8).length;
			int before = steps.size();
			queue(fresh.substring(0, end + 1));
			LOG.info("[client smoke] {} steps read from {}", steps.size() - before, COMMAND_FILE);
		} catch (IOException e) {
			LOG.warn("[client smoke] cannot read {}: {}", commandFile, e.toString());
		}
	}

	private static void run(Minecraft mc, String step) throws Exception {
		String[] parts = step.split("\\s+");
		String op = parts[0].toLowerCase(Locale.ROOT);
		String rest = step.length() > parts[0].length() ? step.substring(parts[0].length()).trim() : "";
		LocalPlayer player = mc.player;
		switch (op) {
			case "wait" -> wait = Integer.parseInt(parts[1]);
			case "log" -> LOG.info("[client smoke] {}", rest);
			case "shot" -> {
				String name = "oceania-" + parts[1] + ".png";
				Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
					message -> LOG.info("[client smoke] {}: {}", name, message.getString()));
			}
			case "cmd" -> command(mc, rest);
			case "ccmd" -> {

				if (player == null) throw new IllegalStateException("no player");
				LOG.info("[client smoke] client /{}", rest);
				player.connection.sendCommand(rest);
			}
			case "tp" -> {
				if (player == null) throw new IllegalStateException("no player");
				command(mc, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f",
					player.getX() + Double.parseDouble(parts[1]), player.getY() + Double.parseDouble(parts[2]), player.getZ() + Double.parseDouble(parts[3])));
			}
			case "tpa" -> command(mc, "tp @a " + parts[1] + " " + parts[2] + " " + parts[3]);
			case "look" -> {
				if (player == null) throw new IllegalStateException("no player");
				float yaw = Float.parseFloat(parts[1]);
				float pitch = Float.parseFloat(parts[2]);
				player.setYRot(yaw);
				player.setXRot(pitch);
				player.yRotO = yaw;
				player.xRotO = pitch;
				player.setYHeadRot(yaw);
				player.setYBodyRot(yaw);
			}
			case "summon" -> {

				if (player == null) throw new IllegalStateException("no player");
				String id = parts[1].contains(":") ? parts[1] : "oceania:" + parts[1];
				String nbt = parts.length > 5 ? " " + String.join(" ", Arrays.copyOfRange(parts, 5, parts.length)) : "";
				command(mc, String.format(Locale.ROOT, "summon %s %.2f %.2f %.2f%s", id,
					player.getX() + Double.parseDouble(parts[2]), player.getY() + Double.parseDouble(parts[3]), player.getZ() + Double.parseDouble(parts[4]), nbt));
			}
			case "interact" -> {

				if (player == null || mc.gameMode == null) throw new IllegalStateException("no player");
				Identifier type = Identifier.parse(parts[1].contains(":") ? parts[1] : "minecraft:" + parts[1]);
				Entity nearest = null;
				double best = 8 * 8;
				for (Entity entity : mc.level.entitiesForRendering()) {
					if (!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(type)) continue;
					double d = entity.distanceToSqr(player);
					if (d < best) {
						best = d;
						nearest = entity;
					}
				}
				if (nearest == null) {

					int budget = parts.length > 2 ? Integer.parseInt(parts[2]) : 200;
					if (budget <= 0) throw new IllegalStateException("no " + type + " within 8 blocks");
					steps.add(index, "interact " + parts[1] + " " + (budget - POLL_TICKS));
					wait = POLL_TICKS;
					return;
				}
				LOG.info("[client smoke] interact {} id {} at {}", type, nearest.getId(), nearest.blockPosition().toShortString());
				mc.gameMode.interact(player, nearest, new EntityHitResult(nearest), InteractionHand.MAIN_HAND);
			}
			case "close" -> {

				if (player == null) throw new IllegalStateException("no player");
				player.closeContainer();
			}
			case "hud" -> {
				boolean off = parts[1].equalsIgnoreCase("off");
				if (mc.gui.hud.isHidden() != off) mc.gui.hud.toggle();
			}
			case "screen" -> {
				switch (parts[1].toLowerCase(Locale.ROOT)) {
					case "config" -> mc.setScreenAndShow(new OceaniaConfigScreen(null));
					case "pause" -> mc.setScreenAndShow(new PauseScreen(true));
					case "title" -> mc.setScreenAndShow(new TitleScreen());
					case "mods" -> mc.setScreenAndShow(modsScreen(mc));
					default -> mc.setScreenAndShow(null);
				}
			}
			case "select" -> selectMod(mc, parts[1]);

			case "key" -> {
				for (String name : parts[1].split("\\+")) key(mc, name).setDown(true);
				steps.add(index, "keyup " + parts[1]);
				wait = Integer.parseInt(parts[2]);
			}

			case "turn" -> {
				player.setYRot(player.getYRot() + Float.parseFloat(parts[1]));
				if (parts.length > 2) player.setXRot(Float.parseFloat(parts[2]));
			}

			case "raftblock" -> {
				RaftEntity raft = null;
				double best = Double.MAX_VALUE;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof RaftEntity r && r.distanceToSqr(player) < best) {
						best = r.distanceToSqr(player);
						raft = r;
					}
				}
				if (raft == null) throw new IllegalStateException("no raft near the player");
				net.minecraft.core.BlockPos cell = new net.minecraft.core.BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
				net.minecraft.world.level.block.entity.BlockEntity be = raft.raftLevel().getBlockEntity(cell);
				StringBuilder items = new StringBuilder();
				if (be instanceof net.minecraft.world.Container container) {
					for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) items.append(i).append("=").append(container.getItem(i)).append(" ");
				} else if (be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity campfire) {
					for (int i = 0; i < campfire.getItems().size(); i++) if (!campfire.getItems().get(i).isEmpty()) items.append(i).append("=").append(campfire.getItems().get(i)).append(" ");
				}
				LOG.info("[client smoke] raftblock {} = {} be={} items=[{}]", cell.toShortString(), raft.raftLevel().getBlockState(cell),
					be == null ? "none" : be.getClass().getSimpleName(), items.toString().trim());
			}
			case "keyup" -> {
				for (String name : parts[1].split("\\+")) key(mc, name).setDown(false);
			}

			case "pos" -> {
				RaftEntity raft = null;
				double best = Double.MAX_VALUE;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof RaftEntity r && r.distanceToSqr(player) < best) {
						best = r.distanceToSqr(player);
						raft = r;
					}
				}
				LOG.info("[client smoke] pos world={} aim={}/{} onGround={} vehicle={} raft={} yaw={} local={} wallCell={} screen={}", player.position(), player.getYRot(), player.getXRot(), player.onGround(),
					player.getVehicle() == null ? "none" : player.getVehicle().getType().toShortString(),
					raft == null ? "none" : raft.position(), raft == null ? "-" : raft.getYRot(),
					raft == null ? "-" : raft.toLocal(player.position()),
					raft == null ? "-" : raft.toWorld(new net.minecraft.world.phys.Vec3(2.0, 1.0, 0.0)),
					mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
			}
			case "disconnect" -> mc.disconnectWithProgressScreen();
			case "quit" -> mc.stop();
			default -> throw new IllegalArgumentException("unknown step " + op);
		}
	}

	private static KeyMapping key(Minecraft mc, String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "forward" -> mc.options.keyUp;
			case "back" -> mc.options.keyDown;
			case "left" -> mc.options.keyLeft;
			case "right" -> mc.options.keyRight;
			case "jump" -> mc.options.keyJump;
			case "sneak" -> mc.options.keyShift;
			case "sprint" -> mc.options.keySprint;
			case "use" -> mc.options.keyUse;
			case "attack" -> mc.options.keyAttack;
			default -> throw new IllegalArgumentException("unknown key " + name);
		};
	}

	private static Screen modsScreen(Minecraft mc) throws ReflectiveOperationException {
		Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
		return (Screen) cls.getConstructor(Screen.class).newInstance(mc.gui.screen());
	}

	private static void selectMod(Minecraft mc, String modId) throws ReflectiveOperationException {
		Screen screen = mc.gui.screen();
		if (screen == null || !screen.getClass().getName().endsWith("ModsScreen")) throw new IllegalStateException("not on the mods screen");
		Field listField = screen.getClass().getDeclaredField("modList");
		listField.setAccessible(true);
		Object list = listField.get(screen);
		List<?> entries = (List<?>) list.getClass().getMethod("children").invoke(list);
		Class<?> entryClass = Class.forName("com.terraformersmc.modmenu.gui.widget.entries.ModListEntry");
		Method getMod = entryClass.getMethod("getMod");
		Method getId = Class.forName("com.terraformersmc.modmenu.util.mod.Mod").getMethod("getId");
		Method update = screen.getClass().getMethod("updateSelectedEntry", entryClass);
		for (Object entry : entries) {
			String id = (String) getId.invoke(getMod.invoke(entry));
			if (modId.equals(id)) {
				update.invoke(screen, entry);
				LOG.info("[client smoke] selected mod {}", id);
				return;
			}
		}
		throw new IllegalStateException("no mod " + modId + " in the list of " + entries.size());
	}

	private static void command(Minecraft mc, String text) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null) throw new IllegalStateException("no integrated server for '" + text + "'");
		LOG.info("[client smoke] /{}", text);
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), text));
	}
}
