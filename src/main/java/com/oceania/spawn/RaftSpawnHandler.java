package com.oceania.spawn;

import com.oceania.Oceania;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftItem;
import com.oceania.raft.RaftLayouts;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class RaftSpawnHandler {
	private RaftSpawnHandler() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			boolean firstJoin = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0;
			if (firstJoin) {
				giveStarterRaft(player);
			}
		});

	}

	public static void giveStarterRaft(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos column = player.blockPosition();
		double surface = waterSurface(level, column);
		if (Double.isNaN(surface)) {
			return;
		}
		Vec3 pivot = new Vec3(column.getX() + 0.5, surface - RaftEntity.DRAFT, column.getZ() + 0.5);
		RaftEntity raft = RaftItem.spawn(level, pivot, player.getYRot(), RaftLayouts.starter());
		if (raft == null) {
			return;
		}

		player.getInventory().setItem(0, new ItemStack(Items.FISHING_ROD));

		player.startRiding(raft, true, true);
		Oceania.LOGGER.info("[spawn] starter raft for {} at {}", player.getGameProfile().name(), raft.position());
	}

	private static double waterSurface(ServerLevel level, BlockPos column) {
		int sea = level.getSeaLevel();
		for (int y = sea + 1; y >= sea - 4; y--) {
			BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
			FluidState fluid = level.getFluidState(pos);
			if (fluid.is(FluidTags.WATER)) {
				if (!level.getBlockState(pos.above()).isAir()) return Double.NaN;
				return y + fluid.getHeight(level, pos);
			}
			if (!level.getBlockState(pos).isAir()) {
				return Double.NaN;
			}
		}
		return Double.NaN;
	}
}
