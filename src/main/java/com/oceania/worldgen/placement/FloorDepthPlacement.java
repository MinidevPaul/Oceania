package com.oceania.worldgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.function.Consumer;

public record FloorDepthPlacement(IntProvider depth, Heightmap.Types heightmap) implements PlacementModifier {
	public static final MapCodec<FloorDepthPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		IntProviders.codec(0, 1024).fieldOf("depth").forGetter(FloorDepthPlacement::depth),
		Heightmap.Types.CODEC.optionalFieldOf("heightmap", Heightmap.Types.OCEAN_FLOOR_WG).forGetter(FloorDepthPlacement::heightmap)
	).apply(i, FloorDepthPlacement::new));
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.cavedeco", "true"));

	@Override
	public void modify(PlacementContext ctx, RandomSource random, BlockPos pos, Consumer<BlockPos> output) {
		if (!ENABLED) return;

		int top = ctx.getHeight(heightmap, pos.getX(), pos.getZ()) - 1;
		int y = top - depth.sample(random);

		if (y <= ctx.getMinY() + 5) return;
		output.accept(pos.atY(y));
	}

	@Override
	public MapCodec<FloorDepthPlacement> codec() {
		return CODEC;
	}
}
