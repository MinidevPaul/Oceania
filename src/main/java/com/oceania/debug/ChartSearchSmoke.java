package com.oceania.debug;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class ChartSearchSmoke {
	private static final boolean ENABLED = "chart".equals(System.getProperty("oceania.smoke", ""));

	private ChartSearchSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				ServerLevel level = server.overworld();
				BlockPos spawn = level.getRespawnData().pos();
				search(level, spawn, StructureTags.SHIPWRECK, "wreck, first");
				search(level, spawn, StructureTags.SHIPWRECK, "wreck, second");
				search(level, spawn, StructureTags.OCEAN_RUIN, "ruin");
				search(level, spawn.offset(4000, 0, 0), StructureTags.SHIPWRECK, "wreck, 4000 blocks east (fresh tiles)");
			} catch (Exception e) {
				Oceania.LOGGER.error("[chart] failed", e);
			} finally {
				server.halt(false);
			}
		});
	}

	private static void search(ServerLevel level, BlockPos from, TagKey<Structure> tag, String label) {
		HolderSet<Structure> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag).orElseThrow();
		long tiles = GenStats.FLOW.count.sum();
		long tileNs = GenStats.FLOW.ns.sum();
		long t = System.nanoTime();
		BlockPos found = level.findNearestMapStructure(set, from, 100, true);
		long ms = (System.nanoTime() - t) / 1_000_000L;
		Oceania.LOGGER.info("[chart] {}: {} ms, found {}, {} flow tiles built ({} ms of tile work)", label, ms,
			found == null ? "nothing" : found.toShortString(), GenStats.FLOW.count.sum() - tiles, (GenStats.FLOW.ns.sum() - tileNs) / 1_000_000L);
	}
}
