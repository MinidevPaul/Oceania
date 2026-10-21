package com.oceania.raft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RaftTickers {
	private static final ThreadLocal<RaftLevel> ACTIVE = new ThreadLocal<>();

	private RaftTickers() {
	}

	public static @Nullable RaftLevel active() {
		return ACTIVE.get();
	}

	public static @Nullable BlockEntityTicker<BlockEntity> serverOnly(RaftLevel raft, ServerLevel real, BlockEntity be) {
		if (be instanceof AbstractFurnaceBlockEntity) {
			return (level, pos, state, entity) -> run(raft, () ->
				AbstractFurnaceBlockEntity.serverTick(real, pos, state, (AbstractFurnaceBlockEntity) entity));
		}
		if (be instanceof CampfireBlockEntity) {
			RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> check = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
			return (level, pos, state, entity) -> {
				CampfireBlockEntity campfire = (CampfireBlockEntity) entity;
				if (state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
					run(raft, () -> CampfireBlockEntity.cookTick(real, pos, state, campfire, check));
				} else {
					CampfireBlockEntity.cooldownTick(raft, pos, state, campfire);
				}
			};
		}
		if (be instanceof BrewingStandBlockEntity) {
			return (level, pos, state, entity) -> run(raft, () ->
				BrewingStandBlockEntity.serverTick(real, pos, state, (BrewingStandBlockEntity) entity));
		}
		return null;
	}

	private static void run(RaftLevel raft, Runnable body) {
		RaftLevel before = ACTIVE.get();
		ACTIVE.set(raft);
		try {
			body.run();
		} finally {
			ACTIVE.set(before);
		}
	}
}
