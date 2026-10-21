package com.oceania.worldgen.feature;

import com.oceania.worldgen.BubbleSources;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record VentFeature() implements Feature {
	public static final MapCodec<VentFeature> CODEC = MapCodec.unit(VentFeature::new);

	private static final int MIN_HEIGHT = 5, MAX_HEIGHT = 14;

	private static final int[] SKIRT_REACH = {24, 24, 16, 8};

	private static final int SKIRT_SPAN = 3;

	@Override
	public MapCodec<VentFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

		BlockPos base = origin.below();
		if (!level.getBlockState(base).isSolid() || !level.getBlockState(origin).is(Blocks.WATER)) return false;

		int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
		boolean up = random.nextInt(3) > 0;
		int placed = 0;

		int[][] lowest = new int[SKIRT_SPAN * 2 + 1][SKIRT_SPAN * 2 + 1];
		for (int[] row : lowest) java.util.Arrays.fill(row, Integer.MAX_VALUE);

		for (int h = 1; h <= height; h++) {

			double t = (double) (h - 1) / height;
			int radius = (int) Math.round(2.4 * (1 - t) + 0.6);
			BlockPos layer = base.above(h);
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dz == 0) continue;
					if (dx * dx + dz * dz > radius * radius + 1) continue;
					BlockPos pos = layer.offset(dx, 0, dz);
					if (!canBuildAt(level, pos)) continue;
					level.setBlock(pos, wall(level, pos, random, h, height), 2);
					lowest[dx + SKIRT_SPAN][dz + SKIRT_SPAN] = Math.min(lowest[dx + SKIRT_SPAN][dz + SKIRT_SPAN], pos.getY());
					placed++;
				}
			}
		}
		if (placed < 8) return false;

		skirt(level, base, random, lowest);

		level.setBlock(base, (up ? Blocks.SOUL_SAND : Blocks.MAGMA_BLOCK).defaultBlockState(), 2);

		BubbleSources.record(level, base);

		BubbleColumnBlock.updateColumn(Blocks.BUBBLE_COLUMN, level, base.above(), level.getBlockState(base));

		for (int i = 0; i < 16; i++) {
			BlockPos pos = base.above(1 + random.nextInt(height))
				.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			if (!level.getBlockState(pos).is(Blocks.WATER)) continue;
			Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			if (!level.getBlockState(pos.relative(face)).isSolid()) continue;
			level.setBlock(pos, Blocks.GLOW_LICHEN.defaultBlockState()
				.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face), true)
				.setValue(BlockStateProperties.WATERLOGGED, true), 2);
		}
		return true;
	}

	private static void skirt(WorldGenLevel level, BlockPos base, RandomSource random, int[][] lowest) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -SKIRT_SPAN; dx <= SKIRT_SPAN; dx++) {
			for (int dz = -SKIRT_SPAN; dz <= SKIRT_SPAN; dz++) {
				int lowY = lowest[dx + SKIRT_SPAN][dz + SKIRT_SPAN];
				if (lowY == Integer.MAX_VALUE) continue;
				int x = base.getX() + dx, z = base.getZ() + dz;
				int reach = SKIRT_REACH[Math.max(Math.abs(dx), Math.abs(dz))];

				int drop = 0;
				probe.set(x, lowY - 1, z);
				while (drop < reach && level.getFluidState(probe).is(FluidTags.WATER)) {
					probe.move(0, -1, 0);
					drop++;
				}

				if (level.getBlockState(probe).isSolid()) {
					for (int d = 1; d <= drop; d++) {
						probe.set(x, lowY - d, z);
						level.setBlock(probe, random.nextInt(3) == 0
							? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState(), 2);
					}
				} else {

					for (int y = lowY; y <= base.getY() + MAX_HEIGHT; y++) {
						probe.set(x, y, z);
						if (!isConeMaterial(level.getBlockState(probe))) break;
						level.setBlock(probe, Blocks.WATER.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	private static boolean isConeMaterial(BlockState state) {
		return state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.COPPER_ORE) || state.is(Blocks.GOLD_ORE);
	}

	private static boolean canBuildAt(WorldGenLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.WATER);
	}

	private static BlockState wall(WorldGenLevel level, BlockPos pos, RandomSource random, int h, int height) {
		int roll = random.nextInt(100);
		if (roll < 6) return Blocks.COPPER_ORE.defaultBlockState();
		if (roll < 9 && h < height / 2) return Blocks.GOLD_ORE.defaultBlockState();
		if (roll < 16 && level.getBlockState(pos.above()).isSolid()) return Blocks.MAGMA_BLOCK.defaultBlockState();
		return roll < 60 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
	}
}
