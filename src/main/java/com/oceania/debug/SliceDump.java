package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.worldgen.field.OceanFields;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SliceDump {
	private static final int AUTO_LEN = Integer.getInteger("oceania.slice.len", -1);
	private static final Integer AUTO_X = Integer.getInteger("oceania.slice.x");
	private static final Integer AUTO_Z = Integer.getInteger("oceania.slice.z");
	private static final boolean AUTO_ALONG_X = !"z".equalsIgnoreCase(System.getProperty("oceania.slice.axis", "x"));

	private static final int BELOW = 160, ABOVE = 40;

	private static final int DEEP = 64;

	private static final Integer AUTO_Y_LO = Integer.getInteger("oceania.slice.ylo");
	private static final Integer AUTO_Y_HI = Integer.getInteger("oceania.slice.yhi");

	private static final String AUTO_LIST = System.getProperty("oceania.slice.list", "");

	private static final int BAND = 32, BANDS = 10;

	private SliceDump() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (AUTO_LEN <= 0) return;
			ServerLevel level = server.overworld();
			if (!AUTO_LIST.isBlank()) {
				for (String entry : AUTO_LIST.split(";")) {
					String[] parts = entry.trim().split(",");
					if (parts.length < 2) continue;
					boolean alongX = parts.length < 3 || !"z".equalsIgnoreCase(parts[2].trim());
					Oceania.LOGGER.info("[slice] {}", render(level, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), AUTO_LEN, alongX, dir(level)));
				}
				server.halt(false);
				return;
			}
			BlockPos spawn = level.getRespawnData().pos();
			int cx = AUTO_X != null ? AUTO_X : spawn.getX(), cz = AUTO_Z != null ? AUTO_Z : spawn.getZ();
			Oceania.LOGGER.info("[slice] {}", render(level, cx, cz, AUTO_LEN, AUTO_ALONG_X, dir(level)));
			server.halt(false);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("slice")
					.executes(ctx -> slice(ctx.getSource(), 256, true))
					.then(Commands.argument("length", IntegerArgumentType.integer(16, 2048))
						.executes(ctx -> slice(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "length"), true))))));
	}

	private static int slice(CommandSourceStack src, int length, boolean alongX) {
		ServerLevel level = src.getLevel();
		String result = render(level, (int) src.getPosition().x, (int) src.getPosition().z, length, alongX, dir(level));
		src.sendSystemMessage(Component.literal(result));
		return 1;
	}

	private static Path dir(ServerLevel level) {
		return level.getServer().getServerDirectory().resolve("oceania-maps");
	}

	public static String render(ServerLevel level, int centreX, int centreZ, int length, boolean alongX, Path dir) {
		return render(level, centreX, centreZ, length, alongX, dir, AUTO_Y_LO, AUTO_Y_HI);
	}

	public static String render(ServerLevel level, int centreX, int centreZ, int length, boolean alongX, Path dir, Integer yLoWanted, Integer yHiWanted) {
		long start = System.nanoTime();
		OceanFields fields = MapDump.fields(level);
		int half = length / 2;
		int a0 = (alongX ? centreX : centreZ) - half;

		double floorMin = Double.MAX_VALUE, floorMax = -Double.MAX_VALUE;
		double[] floors = new double[length];

		double[] airs = new double[length];

		boolean[] wets = new boolean[length];
		int wetColumns = 0;
		for (int i = 0; i < length; i++) {
			int x = alongX ? a0 + i : centreX;
			int z = alongX ? centreZ : a0 + i;
			OceanFields.Sample sample = fields.sample(x, z);
			floors[i] = sample.floorY;
			airs[i] = sample.caveAir;
			wets[i] = sample.wet > 0.9;
			if (wets[i]) wetColumns++;
			floorMin = Math.min(floorMin, floors[i]);
			floorMax = Math.max(floorMax, floors[i]);
		}
		int yLo = Math.max(level.getMinY(), yLoWanted != null ? yLoWanted : (int) floorMin - BELOW);
		int yHi = Math.min(level.getMaxY(), yHiWanted != null ? yHiWanted : (int) floorMax + ABOVE);
		int height = yHi - yLo + 1;

		BufferedImage img = new BufferedImage(length, height, BufferedImage.TYPE_INT_RGB);
		long open = 0, rock = 0, water = 0, lava = 0;

		long deepAir = 0, deepWater = 0;

		long leaks = 0, flows = 0;
		int leakX = Integer.MIN_VALUE, leakY = 0, flowX = Integer.MIN_VALUE, flowY = 0;

		boolean[] prevWater = new boolean[height], prevAir = new boolean[height];
		boolean havePrev = false;
		int caveColumns = 0;
		double caveTop = Double.NaN, caveBottom = Double.NaN;

		long[] bandOpen = new long[BANDS], bandAll = new long[BANDS];
		long[] wetBandOpen = new long[BANDS], wetBandAll = new long[BANDS];
		long wetOpen = 0, wetUnder = 0, wetWater = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LevelChunk chunk = null;
		int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
		for (int i = 0; i < length; i++) {
			int x = alongX ? a0 + i : centreX;
			int z = alongX ? centreZ : a0 + i;
			if (chunk == null || (x >> 4) != chunkX || (z >> 4) != chunkZ) {
				chunkX = x >> 4;
				chunkZ = z >> 4;
				chunk = level.getChunk(chunkX, chunkZ);
			}

			int run = 0;
			boolean cave = false;
			boolean[] colWater = new boolean[height], colAir = new boolean[height];

			BlockState above = null;
			for (int y = yHi; y >= yLo; y--) {
				pos.set(x, y, z);
				BlockState state = chunk.getBlockState(pos);
				img.setRGB(i, yHi - y, colour(state));
				boolean isLava = state.getBlock() == Blocks.LAVA;
				boolean isWater = state.getBlock() == Blocks.WATER;
				boolean isOpen = state.isAir() || isWater || isLava;
				int yi = yHi - y;
				colWater[yi] = isWater;
				colAir[yi] = state.isAir();

				boolean leak = above != null && above.getBlock() == Blocks.WATER && state.isAir();
				if (!leak && havePrev && isWater && prevAir[yi]) leak = true;
				if (!leak && havePrev && state.isAir() && prevWater[yi]) leak = true;
				if (leak) {
					if (y >= floors[i] - airs[i]) {
						leaks++;
						if (leakX == Integer.MIN_VALUE) { leakX = x; leakY = y; }
					} else {
						flows++;
						if (flowX == Integer.MIN_VALUE) { flowX = x; flowY = y; }
					}
				}
				above = state;
				if (y < floors[i]) {
					int band = Math.min(BANDS - 1, (int) ((floors[i] - y) / BAND));
					bandAll[band]++;
					if (isOpen) bandOpen[band]++;
					if (wets[i]) {
						wetBandAll[band]++;
						wetUnder++;
						if (isOpen) {
							wetBandOpen[band]++;
							wetOpen++;
							if (isWater) wetWater++;
						}
					}
					if (isOpen) {
						open++;
						if (isWater) water++;
						if (isLava) lava++;
						if (y < floors[i] - DEEP) {
							if (isWater) deepWater++;
							else if (!isLava) deepAir++;
						}
					} else {
						rock++;
					}
					if (isOpen && y < floors[i] - 3) {
						run++;
						if (run >= 2) {
							cave = true;
							caveTop = Double.isNaN(caveTop) ? y : Math.max(caveTop, y);
							caveBottom = Double.isNaN(caveBottom) ? y : Math.min(caveBottom, y);
						}
					} else {
						run = 0;
					}
				}
			}
			prevWater = colWater;
			prevAir = colAir;
			havePrev = true;
			if (cave) caveColumns++;

			if (yLo <= OceanFields.SEA_LEVEL && OceanFields.SEA_LEVEL <= yHi && i % 4 == 0) {
				img.setRGB(i, yHi - OceanFields.SEA_LEVEL, 0xff0000);
			}
			int fy = (int) floors[i];
			if (yLo <= fy && fy <= yHi && i % 8 == 0) img.setRGB(i, yHi - fy, 0x00ff00);
		}

		try {
			Files.createDirectories(dir);
			String tag = level.getSeed() + "-" + centreX + "_" + centreZ + "-" + (alongX ? "x" : "z") + length
				+ (yLoWanted != null || yHiWanted != null ? "-y" + yLo + "_" + yHi : "");
			Path file = dir.resolve("slice-" + tag + ".png");
			ImageIO.write(img, "png", file.toFile());
			long under = open + rock;
			StringBuilder bands = new StringBuilder();
			for (int b = 0; b < BANDS; b++) {
				bands.append(String.format("%s%d%s: %.1f%%", b == 0 ? "" : " ", b * BAND, b == BANDS - 1 ? "+" : "-" + (b + 1) * BAND,
					bandAll[b] == 0 ? 0 : 100.0 * bandOpen[b] / bandAll[b]));
			}
			StringBuilder wetBands = new StringBuilder();
			for (int b = 0; b < BANDS; b++) {
				wetBands.append(String.format("%s%d%s: %.1f%%", b == 0 ? "" : " ", b * BAND, b == BANDS - 1 ? "+" : "-" + (b + 1) * BAND,
					wetBandAll[b] == 0 ? 0 : 100.0 * wetBandOpen[b] / wetBandAll[b]));
			}
			String wetReport = wetColumns == 0 ? "; no flooding columns on this line"
				: String.format("; flooding columns %d of %d (%.1f%%), in them %.2f%% open of which %.0f%% water, by depth [%s]",
					wetColumns, length, 100.0 * wetColumns / length,
					wetUnder == 0 ? 0 : 100.0 * wetOpen / wetUnder,
					wetOpen == 0 ? 0 : 100.0 * wetWater / wetOpen, wetBands);
			return String.format("slice %d blocks along %s at %d,%d, y %d..%d: %.1fs; floor %.0f..%.0f; "
					+ "under the floor %.2f%% open (%.2f%% water, %.2f%% lava); more than %d below it %d open, %.0f%% air and %.0f%% water; "
					+ "caves in %d of %d columns (%.1f%%)%s; water over air: %d%s; aquifer water running: %d%s; open by depth below floor [%s]%s; written to %s",
				length, alongX ? "x" : "z", centreX, centreZ, yLo, yHi, (System.nanoTime() - start) / 1e9, floorMin, floorMax,
				under == 0 ? 0 : 100.0 * open / under, under == 0 ? 0 : 100.0 * water / under, under == 0 ? 0 : 100.0 * lava / under,
				DEEP, deepAir + deepWater,
				deepAir + deepWater == 0 ? 0 : 100.0 * deepAir / (deepAir + deepWater),
				deepAir + deepWater == 0 ? 0 : 100.0 * deepWater / (deepAir + deepWater),
				caveColumns, length, 100.0 * caveColumns / length,
				Double.isNaN(caveTop) ? "" : String.format(", cave y %.0f..%.0f", caveBottom, caveTop),
				leaks, leakX == Integer.MIN_VALUE ? "" : String.format(" (first at %d,%d)", leakX, leakY),
				flows, flowX == Integer.MIN_VALUE ? "" : String.format(" (first at %d,%d)", flowX, flowY),
				bands, wetReport, file.toAbsolutePath());
		} catch (IOException e) {
			Oceania.LOGGER.error("[slice] failed", e);
			return "slice failed: " + e;
		}
	}

	private static int colour(BlockState state) {
		if (state.isAir()) return 0xf4f4f4;
		var b = state.getBlock();
		if (b == Blocks.WATER) return 0x2b6cb0;
		if (b == Blocks.LAVA) return 0xff7a18;
		if (b == Blocks.STONE) return 0x8a8a8a;
		if (b == Blocks.DEEPSLATE) return 0x4a4a52;
		if (b == Blocks.BASALT) return 0x33333a;
		if (b == Blocks.SAND) return 0xe8d9a0;
		if (b == Blocks.SANDSTONE) return 0xd0bc82;
		if (b == Blocks.GRAVEL) return 0x9a8f80;
		if (b == Blocks.GRASS_BLOCK) return 0x5a9e3a;
		if (b == Blocks.DIRT) return 0x7a5a3a;
		if (b == Blocks.BEDROCK) return 0x111111;

		if (b == Blocks.GRANITE) return 0xa8796a;
		if (b == Blocks.DIORITE) return 0xd8d8d2;
		if (b == Blocks.ANDESITE) return 0x9b9b9b;
		if (b == Blocks.TUFF) return 0x6c6c62;
		if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) return 0x101010;
		if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) return 0xe0a070;
		if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE) return 0xd97b3a;
		if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE) return 0xf5d020;
		if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return 0xe01010;
		if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) return 0x2040d0;
		if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return 0x20e0e0;
		if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return 0x20d050;

		if (b == Blocks.JUNGLE_LOG || b == Blocks.OAK_LOG || b == Blocks.JUNGLE_WOOD) return 0x4a3418;
		if (b == Blocks.JUNGLE_LEAVES || b == Blocks.OAK_LEAVES) return 0x2f7a22;
		if (b == Blocks.VINE || b == Blocks.COCOA) return 0x3f8a30;
		if (b == Blocks.BAMBOO || b == Blocks.SUGAR_CANE) return 0x7ab53a;
		if (b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN) return 0x5fae3a;
		if (b == Blocks.DANDELION || b == Blocks.POPPY) return 0xe8e040;
		if (b == Blocks.DEAD_BUSH) return 0x8a7040;
		if (b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS) return 0x2f8f6a;
		if (b == Blocks.KELP || b == Blocks.KELP_PLANT) return 0x3fa050;
		if (b == Blocks.SEA_PICKLE) return 0x9fbf3a;

		if (b == Blocks.DRIPSTONE_BLOCK) return 0x7d6a5a;
		if (b == Blocks.POINTED_DRIPSTONE) return 0xb08a6a;
		if (b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST) return 0x9a5cf0;
		if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD) return 0xc89cff;
		if (b == Blocks.CALCITE) return 0xe8e4dc;
		if (b == Blocks.SMOOTH_BASALT) return 0x3c3c44;
		if (b == Blocks.SCULK || b == Blocks.SCULK_VEIN) return 0x0b2a30;
		if (b == Blocks.SCULK_CATALYST || b == Blocks.SCULK_SENSOR) return 0x10606a;
		if (b == Blocks.SCULK_SHRIEKER) return 0x20c0d0;
		if (b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET) return 0x4f8a2a;
		if (b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA || b == Blocks.AZALEA_LEAVES || b == Blocks.FLOWERING_AZALEA_LEAVES) return 0x5fa040;
		if (b == Blocks.ROOTED_DIRT || b == Blocks.HANGING_ROOTS) return 0x8a6a4a;
		if (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT) return 0x7fb040;
		if (b == Blocks.SPORE_BLOSSOM) return 0xf070a0;
		if (b == Blocks.BIG_DRIPLEAF || b == Blocks.BIG_DRIPLEAF_STEM || b == Blocks.SMALL_DRIPLEAF) return 0x3f9a40;
		if (b == Blocks.CLAY) return 0xa0a8b8;
		if (b == Blocks.RAW_COPPER_BLOCK) return 0xc06a40;
		if (b == Blocks.RAW_IRON_BLOCK) return 0xc8a080;
		if (b == Blocks.GLOW_LICHEN) return 0x90c0a0;
		if (b == Blocks.SPAWNER || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.COBBLESTONE) return 0x6a7a6a;
		if (b == Blocks.DEEPSLATE_BRICKS || b == Blocks.DEEPSLATE_TILES || b == Blocks.POLISHED_DEEPSLATE || b == Blocks.CRACKED_DEEPSLATE_BRICKS || b == Blocks.CRACKED_DEEPSLATE_TILES || b == Blocks.DEEPSLATE_TILE_STAIRS || b == Blocks.DEEPSLATE_BRICK_STAIRS || b == Blocks.DEEPSLATE_TILE_SLAB || b == Blocks.DEEPSLATE_BRICK_SLAB) return 0x2c3038;
		if (b == Blocks.SOUL_LANTERN || b == Blocks.SOUL_FIRE || b == Blocks.SOUL_SAND && false) return 0x40d0e0;
		if (b == Blocks.REINFORCED_DEEPSLATE) return 0x1a2430;

		if (b == Blocks.BLACKSTONE) return 0x2a2630;
		if (b == Blocks.SOUL_SAND) return 0x5a4636;
		if (b == Blocks.MAGMA_BLOCK) return 0xc2410c;
		if (b == Blocks.BUBBLE_COLUMN) return 0x9fd8f0;
		if (b == Blocks.GLOW_LICHEN) return 0x86d07a;
		if (b == Blocks.CLAY) return 0x9aa3b0;
		if (b == Blocks.RAW_IRON_BLOCK || b == Blocks.RAW_COPPER_BLOCK) return 0xe8e8e8;
		if (b == Blocks.RAW_GOLD_BLOCK || b == Blocks.ANCIENT_DEBRIS) return 0xffd700;
		if (b == Blocks.TUBE_CORAL_BLOCK || b == Blocks.BRAIN_CORAL_BLOCK || b == Blocks.BUBBLE_CORAL_BLOCK
			|| b == Blocks.FIRE_CORAL_BLOCK || b == Blocks.HORN_CORAL_BLOCK) return 0xd052a0;
		return 0xff00ff;
	}
}
