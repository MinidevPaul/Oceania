package com.oceania.worldgen.placement;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

public final class ColumnBiomeFilter implements PlacementFilter {
	private static final ColumnBiomeFilter INSTANCE = new ColumnBiomeFilter();
	public static final MapCodec<ColumnBiomeFilter> CODEC = MapCodec.unit(() -> INSTANCE);

	private ColumnBiomeFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
		PlacedFeature feature = ctx.topFeature()
			.orElseThrow(() -> new IllegalStateException("column_biome on an unregistered feature, or one that should not restrict the biome"));
		int top = ctx.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ());
		Holder<Biome> biome = ctx.getLevel().getBiome(new BlockPos(pos.getX(), top, pos.getZ()));
		return ctx.generator().getBiomeGenerationSettings(biome).hasFeature(feature);
	}

	@Override
	public MapCodec<ColumnBiomeFilter> codec() {
		return CODEC;
	}
}
