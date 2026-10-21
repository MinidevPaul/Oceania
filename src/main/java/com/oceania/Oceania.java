package com.oceania;

import com.oceania.config.OceaniaConfig;
import com.oceania.debug.CaveShares;
import com.oceania.debug.VillageLocate;
import com.oceania.debug.GenBench;
import com.oceania.debug.LandSpawnScan;
import com.oceania.debug.MobCensus;
import com.oceania.debug.ChartSearchSmoke;
import com.oceania.debug.OreScan;
import com.oceania.debug.RaftSmoke;
import com.oceania.debug.TurtleMineSmoke;
import com.oceania.debug.TurtleSmoke;
import com.oceania.debug.RaftLightCommand;
import com.oceania.debug.RaftWallCommand;
import com.oceania.debug.VillageSmoke;
import com.oceania.debug.TradeSmoke;
import com.oceania.debug.SpawnScan;
import com.oceania.item.TurtleArmor;
import com.oceania.raft.RaftBeds;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftInteraction;
import com.oceania.raft.RaftItems;
import com.oceania.raft.RaftNetworking;
import com.oceania.raft.RaftTicker;
import com.oceania.registry.OceaniaBlocks;
import com.oceania.registry.OceaniaEntities;
import com.oceania.registry.OceaniaItems;
import com.oceania.registry.OceaniaParticles;
import com.oceania.registry.OceaniaSounds;
import com.oceania.weather.OceaniaWeather;
import com.oceania.debug.MapDump;
import com.oceania.debug.SliceDump;
import com.oceania.registry.OceaniaFeatures;
import com.oceania.spawn.RaftSpawnHandler;
import com.oceania.village.HutVillage;
import com.oceania.worldgen.BubbleSources;
import com.oceania.worldgen.structure.OceaniaWorldgenStructures;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import com.oceania.worldgen.placement.OceaniaPlacements;
import com.oceania.worldgen.surface.FloorDepthCondition;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Oceania implements ModInitializer {
	public static final String MOD_ID = "oceania";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		OceaniaConfig.load();

		FieldFunction.register();
		OceaniaFeatures.init();
		BubbleSources.init();
		OceaniaPlacements.init();
		FloorDepthCondition.register();
		OceaniaWorldgenStructures.init();
		HutVillage.init();

		OceaniaBlocks.init();
		OceaniaItems.init();
		TurtleArmor.init();
		OceaniaEntities.init();
		OceaniaSounds.init();
		OceaniaParticles.init();
		OceaniaWeather.init();

		RaftItems.init();
		RaftTicker.initServer();
		RaftSpawnHandler.init();
		RaftNetworking.init();
		RaftInteraction.init();
		RaftBeds.init();

		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			if (entity instanceof RaftEntity raft) {
				MinecraftServer server = player.level().getServer();
				server.schedule(new TickTask(server.getTickCount(), () -> {
					if (!raft.isRemoved() && !player.hasDisconnected()) raft.sendBlocksTo(player);
				}));
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOGGER.info("[perf] engagement: {}", com.oceania.perf.PerfFlags.engagement()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OceanFields.releaseAll());

		GenBench.init();
		MapDump.init();
		SliceDump.init();
		CaveShares.init();
		VillageLocate.init();
		SpawnScan.init();
		MobCensus.init();
		LandSpawnScan.init();
		RaftSmoke.init();
		TradeSmoke.init();
		OreScan.init();
		TurtleSmoke.init();
		TurtleMineSmoke.init();
		ChartSearchSmoke.init();
		VillageSmoke.init();
		com.oceania.debug.FeatureReadTrace.init();
		com.oceania.debug.SurfaceDepthScan.init();
		RaftWallCommand.init();
		RaftLightCommand.init();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> LOGGER.info("[worldgen] lifecycle: registries {}, level {}",
			server.registryAccess().allRegistriesLifecycle(), server.getWorldData().worldGenSettingsLifecycle()));

		LOGGER.info("[perf] flags: {}", com.oceania.perf.PerfFlags.report());
		LOGGER.info("Oceania initialised (26.3 port, milestone 7: measurement and the debug tools)");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
