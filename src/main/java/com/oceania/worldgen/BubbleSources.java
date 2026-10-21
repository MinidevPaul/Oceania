package com.oceania.worldgen;

import com.mojang.serialization.Codec;
import com.oceania.Oceania;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public final class BubbleSources {
	private static final AttachmentType<List<Long>> GENERATED =
		AttachmentRegistry.createPersistent(Oceania.id("bubble_sources"), Codec.LONG.listOf());

	private BubbleSources() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SOUL_SAND)) forget(level, pos);
		});
	}

	private static ChunkAccess chunkAt(LevelReader level, BlockPos pos) {
		return level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY, false);
	}

	public static void record(LevelAccessor level, BlockPos pos) {
		ChunkAccess chunk = chunkAt(level, pos);
		if (chunk == null) return;
		long key = pos.asLong();
		List<Long> had = ((AttachmentTarget) chunk).getAttachedOrElse(GENERATED, List.of());
		if (had.contains(key)) return;
		List<Long> now = new ArrayList<>(had.size() + 1);
		now.addAll(had);
		now.add(key);
		((AttachmentTarget) chunk).setAttached(GENERATED, List.copyOf(now));
	}

	public static boolean isGenerated(LevelReader level, BlockPos pos) {
		ChunkAccess chunk = chunkAt(level, pos);
		if (chunk == null) return false;
		return ((AttachmentTarget) chunk).getAttachedOrElse(GENERATED, List.<Long>of()).contains(pos.asLong());
	}

	private static void forget(LevelAccessor level, BlockPos pos) {
		ChunkAccess chunk = chunkAt(level, pos);
		if (chunk == null) return;
		List<Long> had = ((AttachmentTarget) chunk).getAttachedOrElse(GENERATED, List.of());
		if (!had.contains(pos.asLong())) return;
		List<Long> now = new ArrayList<>(had);
		now.remove(Long.valueOf(pos.asLong()));
		((AttachmentTarget) chunk).setAttached(GENERATED, List.copyOf(now));
	}
}
