package com.oceania.worldgen.feature;

import com.mojang.serialization.MapCodec;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record AtollCactusFeature() implements Feature {
	public static final MapCodec<AtollCactusFeature> CODEC = MapCodec.unit(AtollCactusFeature::new);

	private static final int TRIES = 6, MIN_HEIGHT = 1, MAX_HEIGHT = 3;

	@Override
	public MapCodec<AtollCactusFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		OceanFields fields = FieldFunction.fields(level.getLevel().getChunkSource().randomState());
		OceanFields.Sample s = fields.sample(origin.getX(), origin.getZ());
		if (s.type != OceanFields.TYPE_CLUSTER || s.variant < 2) return false;

		int placed = 0;
		for (int i = 0; i < TRIES; i++) {
			BlockPos pos = origin.offset(random.nextInt(8) - 4, 0, random.nextInt(8) - 4);

			while (pos.getY() > level.getMinY() + 1 && level.getBlockState(pos).isAir()) pos = pos.below();
			if (!level.getBlockState(pos).is(Blocks.SAND)) continue;
			if (pos.getY() <= level.getSeaLevel()) continue;

			BlockPos base = pos.above();
			if (!level.getBlockState(base).isAir()) continue;

			boolean clear = true;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (!level.getBlockState(base.relative(d)).isAir()) {
					clear = false;
					break;
				}
			}
			if (!clear) continue;

			int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
			BlockState cactus = Blocks.CACTUS.defaultBlockState();
			for (int h = 0; h < height; h++) {
				BlockPos at = base.above(h);
				if (!level.getBlockState(at).isAir()) break;
				level.setBlock(at, cactus, 2);
				placed++;
			}
		}
		return placed > 0;
	}
}
