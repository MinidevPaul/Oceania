package com.oceania.client.render;

import com.oceania.raft.RaftLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class RaftBlockView implements BlockAndTintGetter {
	private final RaftLevel level;
	private final ClientLevel world;

	public RaftBlockView(RaftLevel level, ClientLevel world) {
		this.level = level;
		this.world = world;
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return level.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return level.getBlockState(pos);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return level.getFluidState(pos);
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinY() {
		return level.getMinY();
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return world.getLightEngine();
	}

	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		return level.getBrightness(layer, pos);
	}

	@Override
	public int getRawBrightness(BlockPos pos, int skyDarken) {
		return level.getRawBrightness(pos, skyDarken);
	}

	@Override
	public CardinalLighting cardinalLighting() {
		return world.cardinalLighting();
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver resolver) {
		return world.getBlockTint(level.worldBlockPos(pos), resolver);
	}
}
