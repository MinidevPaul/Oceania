package com.oceania.mixin.mob;

import com.oceania.worldgen.StructureProximity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Drowned.class)
public class DrownedSpawnMixin {
	private static final int RUIN_RADIUS = Integer.getInteger("oceania.drowned.ruin", 80);
	private static final int MONUMENT_RADIUS = Integer.getInteger("oceania.drowned.monument", 80);

	private static final int RARITY = Integer.getInteger("oceania.drowned.rarity", 40);

	private static final int MIN_DEPTH = Integer.getInteger("oceania.drowned.depth", 24);

	private static final int FLOOR_REACH = Integer.getInteger("oceania.drowned.floor", 20);

	private static final ResourceKey<StructureSet> OCEAN_RUINS =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("ocean_ruins"));
	private static final ResourceKey<StructureSet> OCEAN_MONUMENTS =
		ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("ocean_monuments"));

	@Inject(method = "checkDrownedSpawnRules", at = @At("HEAD"), cancellable = true)
	private static void oceania$onlyNearRuinsAndMonuments(
			EntityType<Drowned> type, ServerLevelAccessor level, EntitySpawnReason spawnType, BlockPos pos,
			RandomSource random, CallbackInfoReturnable<Boolean> cir) {

		if (spawnType != EntitySpawnReason.NATURAL && spawnType != EntitySpawnReason.CHUNK_GENERATION) return;

		if (level.getDifficulty() == Difficulty.PEACEFUL
			|| !level.getFluidState(pos).is(FluidTags.WATER)
			|| pos.getY() > level.getSeaLevel() - MIN_DEPTH
			|| random.nextInt(RARITY) != 0) {
			cir.setReturnValue(false);
			return;
		}

		if (!oceania$floorWithin(level, pos, FLOOR_REACH)) {
			cir.setReturnValue(false);
			return;
		}

		cir.setReturnValue(StructureProximity.near(level, pos, OCEAN_RUINS, RUIN_RADIUS)
			|| StructureProximity.near(level, pos, OCEAN_MONUMENTS, MONUMENT_RADIUS));
	}

	private static boolean oceania$floorWithin(ServerLevelAccessor level, BlockPos pos, int reach) {
		int bottom = Math.max(level.getMinY(), pos.getY() - reach);
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
		for (int y = pos.getY() - 1; y >= bottom; y--) {
			if (level.getBlockState(probe.setY(y)).isSolid()) return true;
		}
		return false;
	}
}
