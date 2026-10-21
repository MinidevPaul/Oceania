package com.oceania.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record CavePoolFeature() implements Feature {
	public static final MapCodec<CavePoolFeature> CODEC = MapCodec.unit(CavePoolFeature::new);

	private static final int DROP = 12;

	@Override
	public MapCodec<CavePoolFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		BlockPos.MutableBlockPos p = origin.mutable();
		if (!level.getBlockState(p).isAir()) return false;
		int fall = 0;
		while (fall < DROP && level.getBlockState(p.below()).isAir()) {
			p.move(Direction.DOWN);
			fall++;
		}
		BlockPos floor = p.below().immutable();
		BlockState ground = level.getBlockState(floor);
		if (!ground.is(BlockTags.LUSH_GROUND_REPLACEABLE) || !ground.isSolid()) return false;

		int r = 2 + random.nextInt(3);
		int fy = floor.getY();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();

		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				int depth = depthAt(d2, r);
				if (depth == 0) {

					if (d2 <= (r + 1) * (r + 1) && !level.getBlockState(q.set(floor.getX() + dx, fy, floor.getZ() + dz)).isSolid()) return false;
					continue;
				}
				if (!level.getBlockState(q.set(floor.getX() + dx, fy + 1, floor.getZ() + dz)).isAir()) return false;
				for (int k = 0; k < depth; k++) {
					if (!level.getBlockState(q.set(floor.getX() + dx, fy - k, floor.getZ() + dz)).isSolid()) return false;
				}

				if (!level.getBlockState(q.set(floor.getX() + dx, fy - depth, floor.getZ() + dz)).isSolid()) return false;
			}
		}

		BlockState water = Blocks.WATER.defaultBlockState();
		BlockState clay = Blocks.CLAY.defaultBlockState();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				int depth = depthAt(d2, r);
				if (depth == 0) {
					if (d2 <= (r + 1) * (r + 1) && random.nextInt(3) != 0) {
						level.setBlock(q.set(floor.getX() + dx, fy, floor.getZ() + dz), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
					}
					continue;
				}
				for (int k = 0; k < depth; k++) {
					level.setBlock(q.set(floor.getX() + dx, fy - k, floor.getZ() + dz), water, 2);
				}
				level.setBlock(q.set(floor.getX() + dx, fy - depth, floor.getZ() + dz), clay, 2);
			}
		}
		return true;
	}

	private static int depthAt(int d2, int r) {
		if (d2 > r * r) return 0;
		if (r >= 3 && d2 <= 1) return 3;
		if (d2 <= (r - 1) * (r - 1)) return 2;
		return 1;
	}
}
