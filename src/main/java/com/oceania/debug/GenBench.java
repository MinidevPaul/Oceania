package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class GenBench {
	private static final int AUTO_RADIUS = Integer.getInteger("oceania.bench.radius", -1);
	private static final int AUTO_OFFSET = Integer.getInteger("oceania.bench.offset", 10000);

	private static final Integer AUTO_X = Integer.getInteger("oceania.bench.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.bench.z");
	private static final boolean AUTO_HALT = Boolean.parseBoolean(System.getProperty("oceania.bench.halt", "true"));
	private static final String TAG = System.getProperty("oceania.height", "vanilla");

	private static ServerLevel level;
	private static List<ChunkPos> pending = List.of();
	private static int radius;
	private static long startNs;
	private static long startHeap;
	private static boolean running;
	private static boolean haltWhenDone;
	private static int ticks;

	private GenBench() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_RADIUS >= 0) {
				Oceania.LOGGER.info("[bench] auto-start: tag={} radius={} offset={}", TAG, AUTO_RADIUS, AUTO_OFFSET);
				start(server.overworld(), AUTO_RADIUS, AUTO_X != null ? AUTO_X : AUTO_OFFSET,
					AUTO_Z != null ? AUTO_Z : AUTO_OFFSET, AUTO_HALT);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(GenBench::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("bench")
					.then(Commands.literal("report").executes(ctx -> {
						ctx.getSource().sendSuccess(() -> Component.literal(report(ctx.getSource().getLevel())), false);
						return 1;
					}))
					.then(Commands.literal("reset").executes(ctx -> {
						GenStats.resetAll();
						ctx.getSource().sendSuccess(() -> Component.literal("gen stats reset"), false);
						return 1;
					}))
					.then(Commands.literal("gen")
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 64))
							.executes(ctx -> startFromCommand(ctx.getSource().getLevel(), ctx.getSource().getPosition().x, ctx.getSource().getPosition().z,
								IntegerArgumentType.getInteger(ctx, "radius"), 10000))
							.then(Commands.argument("offset", IntegerArgumentType.integer(0, 1_000_000))
								.executes(ctx -> startFromCommand(ctx.getSource().getLevel(), ctx.getSource().getPosition().x, ctx.getSource().getPosition().z,
									IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "offset")))))))));
	}

	private static int startFromCommand(ServerLevel lvl, double x, double z, int r, int offset) {
		if (running) return 0;
		start(lvl, r, (int) x + offset, (int) z + offset, false);
		return 1;
	}

	public static void start(ServerLevel lvl, int r, int centerX, int centerZ, boolean halt) {
		level = lvl;
		radius = r;
		haltWhenDone = halt;
		ChunkPos center = new ChunkPos(centerX >> 4, centerZ >> 4);
		List<ChunkPos> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				list.add(new ChunkPos(center.x() + dx, center.z() + dz));
			}
		}
		pending = list;
		GenStats.resetAll();
		Runtime rt = Runtime.getRuntime();
		startHeap = rt.totalMemory() - rt.freeMemory();
		ticks = 0;
		startNs = System.nanoTime();
		running = true;

		for (ChunkPos pos : list) {
			lvl.getChunkSource().addTicketWithRadius(TicketType.FORCED, pos, 1);
		}
		Oceania.LOGGER.info("[bench] started: {} chunks around {} (radius {})", list.size(), center, r);
	}

	private static void tick(MinecraftServer server) {
		if (!running) return;
		ticks++;
		int done = 0;
		for (ChunkPos pos : pending) {
			if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null) done++;
		}
		if (ticks % 100 == 0) {
			Oceania.LOGGER.info("[bench] {}/{} chunks full after {} s", done, pending.size(), (System.nanoTime() - startNs) / 1e9);
		}
		if (done == pending.size()) {
			finish(server);
		}
	}

	private static void finish(MinecraftServer server) {
		running = false;
		long wallNs = System.nanoTime() - startNs;
		String text = buildReport(level, wallNs, pending.size());
		Oceania.LOGGER.info("[bench] RESULT\n{}", text);
		Path out = server.getServerDirectory().resolve("oceania-bench.txt");
		try {
			Files.writeString(out, text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			Oceania.LOGGER.warn("[bench] could not write {}", out, e);
		}
		for (ChunkPos pos : pending) {
			level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, pos, 1);
		}
		if (haltWhenDone) {
			Oceania.LOGGER.info("[bench] halting server");
			server.halt(false);
		}
	}

	private static String buildReport(ServerLevel lvl, long wallNs, int chunks) {
		StringBuilder sb = new StringBuilder();
		sb.append("=== oceania gen bench: tag=").append(TAG)
			.append(" world height=").append(lvl.getHeight()).append(" min_y=").append(lvl.getMinY())
			.append(" radius=").append(radius).append(" chunks=").append(chunks).append(" ===\n");
		sb.append(String.format("  wall time: %.1f s  (%.1f ms/chunk, %.1f chunks/s)%n", wallNs / 1e9, wallNs / 1e6 / chunks, chunks / (wallNs / 1e9)));
		sb.append(GenStats.report());

		long bytes = 0;
		int sampled = 0;
		int step = Math.max(1, pending.size() / 64);
		for (int i = 0; i < pending.size() && sampled < 64; i += step) {
			ChunkPos pos = pending.get(i);
			LevelChunk chunk = lvl.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk == null) continue;
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), lvl.registryAccess());
			try {
				ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buf, new ClientboundLevelChunkWithLightPacket(chunk, lvl.getLightEngine(), null, null));
				bytes += buf.readableBytes();
				sampled++;
			} finally {
				buf.release();
			}
		}
		if (sampled > 0) {
			sb.append(String.format("  chunk+light packet: avg %.1f KB over %d chunks%n", bytes / 1024.0 / sampled, sampled));
		}
		Runtime rt = Runtime.getRuntime();
		System.gc();
		System.gc();
		long used = rt.totalMemory() - rt.freeMemory();
		sb.append(String.format("  heap: used %.0f MB (was %.0f MB at start), max %.0f MB, loaded chunks %d%n",
			used / 1048576.0, startHeap / 1048576.0, rt.maxMemory() / 1048576.0, lvl.getChunkSource().getLoadedChunksCount()));
		return sb.toString();
	}

	private static String report(ServerLevel lvl) {
		return "gen stats (height " + lvl.getHeight() + "):\n" + GenStats.report();
	}
}
