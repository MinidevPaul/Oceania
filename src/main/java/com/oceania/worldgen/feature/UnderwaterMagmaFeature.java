package com.oceania.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.worldgen.BubbleSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.OptionalInt;
import java.util.function.Predicate;

public record UnderwaterMagmaFeature(int floorSearchRange, int placementRadiusAroundFloor,
                                     float placementProbabilityPerValidPosition) implements Feature {
	public static final MapCodec<UnderwaterMagmaFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.intRange(0, 512).fieldOf("floor_search_range").forGetter(UnderwaterMagmaFeature::floorSearchRange),
		Codec.intRange(0, 64).fieldOf("placement_radius_around_floor")
			.forGetter(UnderwaterMagmaFeature::placementRadiusAroundFloor),
		Codec.floatRange(0.0F, 1.0F).fieldOf("placement_probability_per_valid_position")
			.forGetter(UnderwaterMagmaFeature::placementProbabilityPerValidPosition)
	).apply(i, UnderwaterMagmaFeature::new));

	@Override
	public MapCodec<UnderwaterMagmaFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		OptionalInt floorY = floorY(level, origin);
		if (floorY.isEmpty()) return false;

		BlockPos floor = origin.atY(floorY.getAsInt());
		int r = placementRadiusAroundFloor;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		boolean any = false;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (random.nextFloat() >= placementProbabilityPerValidPosition) continue;
					at.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
					if (!valid(level, at)) continue;
					BlockPos placed = at.immutable();
					level.setBlock(placed, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
					BubbleSources.record(level, placed);

					if (level.getBlockState(placed.above()).is(Blocks.WATER)) {
						BubbleColumnBlock.updateColumn(Blocks.BUBBLE_COLUMN, level, placed.above(),
							level.getBlockState(placed));
					}
					any = true;
				}
			}
		}
		return any;
	}

	private OptionalInt floorY(WorldGenLevel level, BlockPos origin) {
		Predicate<BlockState> insideColumn = state -> state.is(Blocks.WATER);
		Predicate<BlockState> validEdge = state -> !state.is(Blocks.WATER);
		return Column.scan(level, origin, floorSearchRange, insideColumn, validEdge)
			.map(Column::getFloor).orElseGet(OptionalInt::empty);
	}

	private static boolean valid(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.WATER) || state.isAir()) return false;
		if (seeThrough(level, pos.below(), Direction.UP)) return false;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (seeThrough(level, pos.relative(side), side.getOpposite())) return false;
		}
		return true;
	}

	private static boolean seeThrough(LevelAccessor level, BlockPos pos, Direction covered) {
		VoxelShape face = level.getBlockState(pos).getFaceOcclusionShape(covered);
		return face == Shapes.empty() || !Block.isShapeFullBlock(face);
	}
}
