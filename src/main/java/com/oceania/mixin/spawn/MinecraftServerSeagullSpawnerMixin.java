package com.oceania.mixin.spawn;

import com.google.common.collect.ImmutableList;
import com.oceania.Oceania;
import com.oceania.spawn.CrabSpawner;
import com.oceania.spawn.GiantSquidSpawner;
import com.oceania.spawn.JellyfishSpawner;
import com.oceania.spawn.SeagullSpawner;
import com.oceania.spawn.WhaleSpawner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.CustomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(MinecraftServer.class)
public class MinecraftServerSeagullSpawnerMixin {

	@ModifyArg(
		method = "createLevels",
		at = @At(
			value = "INVOKE",

			target = "Lnet/minecraft/server/level/ServerLevel;<init>("
				+ "Lnet/minecraft/server/MinecraftServer;"
				+ "Ljava/util/concurrent/Executor;"
				+ "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
				+ "Lnet/minecraft/world/level/storage/ServerLevelData;"
				+ "Lnet/minecraft/resources/ResourceKey;"
				+ "Lnet/minecraft/world/level/dimension/LevelStem;"
				+ "ZJ"
				+ "Ljava/util/List;"
				+ "Z)V",
			ordinal = 0),
		index = 8)
	private List<CustomSpawner> oceania$addSeagullSpawner(List<CustomSpawner> spawners) {
		List<CustomSpawner> withGulls = ImmutableList.<CustomSpawner>builder()
			.addAll(spawners)
			.add(new SeagullSpawner())
			.add(new CrabSpawner())
			.add(new JellyfishSpawner())
			.add(new WhaleSpawner())
			.add(new GiantSquidSpawner())
			.build();

		Oceania.LOGGER.info("[seagull] spawner added to the overworld custom spawner list ({} spawners)", withGulls.size());
		CrabSpawner.announce(withGulls.size());
		JellyfishSpawner.announce(withGulls.size());
		WhaleSpawner.announce(withGulls.size());
		GiantSquidSpawner.announce(withGulls.size());
		return withGulls;
	}
}
