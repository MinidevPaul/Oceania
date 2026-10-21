package com.oceania.mixin.spawn;

import com.oceania.Oceania;
import com.oceania.worldgen.OceaniaWorld;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerSpawnMixin {
	@Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
	private static void oceania$spawnNearIsland(ServerLevel level, ServerLevelData data, boolean bonusChest, boolean debug,
												LevelLoadListener listener, CallbackInfo ci) {
		if (debug || level.dimension() != Level.OVERWORLD || !OceaniaWorld.isOceania(level)) return;
		OceanFields fields = FieldFunction.fields(level.getChunkSource().randomState());
		double[] spawn = fields.findOpenOceanSpawn(0, 0);
		BlockPos pos = new BlockPos((int) Math.floor(spawn[0]), OceanFields.SEA_LEVEL + 1, (int) Math.floor(spawn[1]));
		data.setSpawn(LevelData.RespawnData.of(level.dimension(), pos, (float) spawn[2], 0.0F));
		Oceania.LOGGER.info("[spawn] world spawn set to {} facing {}", pos, (float) spawn[2]);
		ci.cancel();
	}
}
