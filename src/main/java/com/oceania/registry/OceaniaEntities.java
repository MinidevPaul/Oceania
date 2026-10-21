package com.oceania.registry;

import com.oceania.Oceania;
import com.oceania.config.OceaniaConfig;
import com.oceania.mob.AbstractJellyfish;
import com.oceania.mob.CoconutCrabEntity;
import com.oceania.mob.CrownJellyEntity;
import com.oceania.mob.GiantSquidEntity;
import com.oceania.mob.KingCrabEntity;
import com.oceania.mob.MoonJellyEntity;
import com.oceania.mob.NettleJellyEntity;
import com.oceania.mob.ReefCrabEntity;
import com.oceania.mob.SeagullEntity;
import com.oceania.mob.SharkEntity;
import com.oceania.mob.WhaleEntity;
import com.oceania.mob.WhaleHitboxPart;
import com.oceania.raft.RaftEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

public final class OceaniaEntities {
	public static final EntityType<SharkEntity> SHARK = register("shark",
		FabricEntityType.Builder.createMob(SharkEntity::new, MobCategory.MONSTER, mob -> mob
				.defaultAttributes(SharkEntity::createAttributes)
				.spawnPlacement(SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(type, level, reason, pos, random) -> OceaniaConfig.get().spawnSharks
						&& SharkEntity.checkSharkSpawnRules(type, level, reason, pos, random)))
			.sized(1.6F, 0.9F)
			.clientTrackingRange(10));
	public static final EntityType<WhaleEntity> WHALE = register("whale",
		FabricEntityType.Builder.createMob(WhaleEntity::new, MobCategory.AXOLOTLS, mob -> mob
				.defaultAttributes(WhaleEntity::createAttributes)
				.spawnPlacement(SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(type, level, reason, pos, random) -> OceaniaConfig.get().spawnWhales
						&& WhaleEntity.checkWhaleSpawnRules(type, level, reason, pos, random)))
			.sized(4.0F, 4.0F)
			.clientTrackingRange(10));
	public static final EntityType<WhaleHitboxPart> WHALE_PART = register("whale_part",
		EntityType.Builder.<WhaleHitboxPart>of(WhaleHitboxPart::new, MobCategory.MISC)
			.sized(4.0F, 4.0F)
			.clientTrackingRange(10)
			.noSummon());
	public static final EntityType<SeagullEntity> SEAGULL = register("seagull",
		EntityType.Builder.of(SeagullEntity::new, MobCategory.AMBIENT)
			.sized(1.2F, 0.5F)
			.clientTrackingRange(8)

			.noLootTable());
	public static final EntityType<CoconutCrabEntity> COCONUT_CRAB = register("coconut_crab",
		EntityType.Builder.of(CoconutCrabEntity::new, MobCategory.CREATURE)
			.sized(0.9F, 0.6F)
			.clientTrackingRange(8));
	public static final EntityType<ReefCrabEntity> REEF_CRAB = register("reef_crab",
		EntityType.Builder.of(ReefCrabEntity::new, MobCategory.WATER_AMBIENT)
			.sized(0.7F, 0.45F)
			.clientTrackingRange(8));
	public static final EntityType<KingCrabEntity> KING_CRAB = register("king_crab",
		EntityType.Builder.of(KingCrabEntity::new, MobCategory.UNDERGROUND_WATER_CREATURE)
			.sized(1.6F, 0.8F)
			.clientTrackingRange(10));
	public static final EntityType<GiantSquidEntity> GIANT_SQUID = register("giant_squid",
		FabricEntityType.Builder.createMob(GiantSquidEntity::new, MobCategory.MONSTER, mob -> mob
				.defaultAttributes(GiantSquidEntity::createAttributes)
				.spawnPlacement(SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(type, level, reason, pos, random) -> OceaniaConfig.get().spawnGiantSquid
						&& GiantSquidEntity.checkGiantSquidSpawnRules(type, level, reason, pos, random)))
			.sized(3.0F, 2.4F)
			.clientTrackingRange(10)
			.fireImmune());
	public static final EntityType<MoonJellyEntity> MOON_JELLY = register("moon_jelly",
		EntityType.Builder.of(MoonJellyEntity::new, MobCategory.AMBIENT)
			.sized(0.7F, 0.9F)
			.clientTrackingRange(6));
	public static final EntityType<NettleJellyEntity> NETTLE_JELLY = register("nettle_jelly",
		EntityType.Builder.of(NettleJellyEntity::new, MobCategory.AMBIENT)
			.sized(0.7F, 1.2F)
			.clientTrackingRange(6));
	public static final EntityType<CrownJellyEntity> CROWN_JELLY = register("crown_jelly",
		EntityType.Builder.of(CrownJellyEntity::new, MobCategory.AMBIENT)
			.sized(0.7F, 0.9F)
			.clientTrackingRange(6));

	public static final EntityType<RaftEntity> RAFT = register("raft",
		EntityType.Builder.<RaftEntity>of(RaftEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.0F)
			.clientTrackingRange(10)
			.updateInterval(1));

	private OceaniaEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Oceania.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SEAGULL, SeagullEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(COCONUT_CRAB, CoconutCrabEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(REEF_CRAB, ReefCrabEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KING_CRAB, KingCrabEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MOON_JELLY, AbstractJellyfish.createAttributes());
		FabricDefaultAttributeRegistry.register(NETTLE_JELLY, AbstractJellyfish.createAttributes());
		FabricDefaultAttributeRegistry.register(CROWN_JELLY, AbstractJellyfish.createAttributes());
	}
}
