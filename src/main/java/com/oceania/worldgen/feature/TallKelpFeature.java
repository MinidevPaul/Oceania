package com.oceania.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record TallKelpFeature() implements Feature {
	public static final MapCodec<TallKelpFeature> CODEC = MapCodec.unit(TallKelpFeature::new);

	private static final int SURFACE_GAP = 2, MAX_LENGTH = 48;

	@Override
	public MapCodec<TallKelpFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).is(Blocks.WATER)) return false;
		if (!level.getBlockState(origin.below()).isSolid()) return false;

		int seaLevel = level.getSeaLevel();
		int room = seaLevel - SURFACE_GAP - origin.getY();
		if (room < 3) return false;

		int length = Mth.clamp(random.nextInt(3) == 0 ? 3 + random.nextInt(Math.max(1, room / 2)) : room - random.nextInt(4),
			3, Math.min(room, MAX_LENGTH));

		BlockState stem = Blocks.KELP_PLANT.defaultBlockState();
		BlockPos.MutableBlockPos pos = origin.mutable();
		for (int i = 0; i < length; i++) {
			if (!level.getBlockState(pos).is(Blocks.WATER)) return i > 0;
			level.setBlock(pos, i == length - 1
				? Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, random.nextInt(4) + 20)
				: stem, 2);
			pos.move(0, 1, 0);
		}
		return true;
	}
}
