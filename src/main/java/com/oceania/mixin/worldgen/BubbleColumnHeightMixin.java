package com.oceania.mixin.worldgen;

import com.oceania.worldgen.BubbleSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BubbleColumnBlock.class)
public class BubbleColumnHeightMixin {
	private static final int SHORT_MIN = 12, SHORT_MAX = 30, FULL_RARITY = 4;

	@Inject(method = "updateColumn(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
		at = @At("HEAD"), cancellable = true)
	private static void oceania$limitHeight(Block bubbleColumn, LevelAccessor level, BlockPos pos, BlockState state, BlockState below, CallbackInfo ci) {

		if (!oceania$isGenerated(level, pos)) return;
		ci.cancel();
		if (!oceania$canExistIn(state)) return;
		BlockState column = oceania$columnState(below);
		if (column.is(Blocks.WATER)) {

			level.setBlock(pos, column, 2);
			BlockPos.MutableBlockPos clear = pos.mutable().move(Direction.UP);
			while (level.getBlockState(clear).is(Blocks.BUBBLE_COLUMN)) {
				if (!level.setBlock(clear, column, 2)) return;
				clear.move(Direction.UP);
			}
			return;
		}

		int limit = oceania$reach(oceania$foot(level, pos));
		level.setBlock(pos, column, 2);
		BlockPos.MutableBlockPos up = pos.mutable().move(Direction.UP);
		for (int i = 1; i < limit && oceania$canExistIn(level.getBlockState(up)); i++) {
			if (!level.setBlock(up, column, 2)) return;
			up.move(Direction.UP);
		}

		while (level.getBlockState(up).is(Blocks.BUBBLE_COLUMN)) {
			if (!level.setBlock(up, Blocks.WATER.defaultBlockState(), 2)) return;
			up.move(Direction.UP);
		}
	}

	private static boolean oceania$isGenerated(LevelAccessor level, BlockPos posInColumn) {
		BlockPos foot = oceania$foot(level, posInColumn);
		if (BubbleSources.isGenerated(level, foot.below())) return true;
		int walls = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState side = level.getBlockState(foot.relative(d));
			if (side.is(Blocks.BLACKSTONE) || side.is(Blocks.BASALT)) walls++;
		}
		return walls >= 3;
	}

	private static boolean oceania$canExistIn(BlockState state) {
		return state.is(Blocks.BUBBLE_COLUMN)
			|| state.is(Blocks.WATER) && state.getFluidState().getAmount() >= 8 && state.getFluidState().isSource();
	}

	private static BlockState oceania$columnState(BlockState below) {
		if (below.is(Blocks.BUBBLE_COLUMN)) return below;
		if (below.is(Blocks.SOUL_SAND)) return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false);
		if (below.is(Blocks.MAGMA_BLOCK)) return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true);
		return Blocks.WATER.defaultBlockState();
	}

	private static BlockPos oceania$foot(LevelAccessor level, BlockPos posInColumn) {
		BlockPos.MutableBlockPos foot = posInColumn.mutable();
		for (int i = 0; i < 512 && level.getBlockState(foot.below()).is(Blocks.BUBBLE_COLUMN); i++) {
			foot.move(Direction.DOWN);
		}
		return foot.immutable();
	}

	private static int oceania$reach(BlockPos source) {
		int h = source.getX() * 0x9E3779B9 ^ source.getZ() * 0x85EBCA6B ^ source.getY() * 0xC2B2AE35;
		h ^= h >>> 15;
		h *= 0x2545F491;
		h ^= h >>> 13;
		int r = Math.abs(h);
		if (r % FULL_RARITY == 0) return Integer.MAX_VALUE;
		return SHORT_MIN + r / FULL_RARITY % (SHORT_MAX - SHORT_MIN + 1);
	}
}
