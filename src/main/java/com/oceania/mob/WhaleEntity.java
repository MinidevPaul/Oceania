package com.oceania.mob;

import net.minecraft.server.level.ServerLevel;
import com.oceania.spawn.SeagullSpawner;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.world.level.ChunkPos;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import com.oceania.registry.OceaniaSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WhaleEntity extends WaterAnimal {

	private static final EntityDataAccessor<Float> GAPE =
		SynchedEntityData.defineId(WhaleEntity.class, EntityDataSerializers.FLOAT);

	private static final EntityDataAccessor<Byte> SURFACE_PHASE =
		SynchedEntityData.defineId(WhaleEntity.class, EntityDataSerializers.BYTE);

	public static final byte PHASE_DOWN = 0;
	public static final byte PHASE_ASCEND = 1;
	public static final byte PHASE_BLOW = 2;

	public static final byte PHASE_SOUND = 3;

	private static final float GAPE_STEPS = 64.0F;

	private static final float GAPE_SMOOTHING = 0.25F;

	private static final int POD_EXTRA_MIN = 0;
	private static final int POD_EXTRA_MAX = 2;

	private static final double POD_SPREAD_MIN = 45.0;
	private static final double POD_SPREAD_MAX = 95.0;

	private static final double POD_SPREAD_Y = 10.0;

	private static final int POD_PLACE_TRIES = 8;

	public static final int MIN_SPAWN_DEPTH = 24;

	public static final int MAX_SPAWN_DEPTH = 80;

	private float gapeSmooth;
	private float gapeSmoothO;

	private final WhaleHitboxPart[] parts = new WhaleHitboxPart[WhaleHitboxPart.Segment.VALUES.length];

	public WhaleEntity(EntityType<? extends WhaleEntity> type, Level level) {
		super(type, level);

		this.moveControl = new SmoothSwimmingMoveControl(this, 15, 8, 0.02F, 0.1F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 4);

	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 100.0)

			.add(Attributes.KNOCKBACK_RESISTANCE, 0.75)

			.add(Attributes.MOVEMENT_SPEED, 0.95)

			.add(Attributes.FOLLOW_RANGE, 32.0);

	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new WhaleSurfaceGoal(this));
		this.goalSelector.addGoal(2, new WhaleLungeGoal(this));
		this.goalSelector.addGoal(3, new WhaleCruiseGoal(this));

		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GAPE, 0.0F);
		builder.define(SURFACE_PHASE, PHASE_DOWN);
	}

	public float getGape() {
		return this.level().isClientSide() ? this.gapeSmooth : this.entityData.get(GAPE);
	}

	public float getGape(float partialTick) {
		return this.level().isClientSide()
			? Mth.lerp(partialTick, this.gapeSmoothO, this.gapeSmooth)
			: this.entityData.get(GAPE);
	}

	public void setGape(float gape) {
		float clamped = Mth.clamp(gape, 0.0F, 1.0F);
		this.entityData.set(GAPE, Math.round(clamped * GAPE_STEPS) / GAPE_STEPS);
	}

	public byte getSurfacePhase() {
		return this.entityData.get(SURFACE_PHASE);
	}

	public void setSurfacePhase(byte phase) {
		this.entityData.set(SURFACE_PHASE, phase);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	protected void handleAirSupply(ServerLevel level, int preTickAirSupply) {

	}

	@Override
	public int getMaxAirSupply() {
		return 3000;
	}

	@Override
	protected int increaseAirSupply(int air) {
		return this.getMaxAirSupply();
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return 1;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return OceaniaSounds.WHALE_SONG;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 900;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public int getMaxHeadXRot() {
		return 1;
	}

	@Override
	public int getMaxHeadYRot() {
		return 1;
	}

	@Override
	protected void doWaterSplashEffect() {
		Vec3 motion = this.getDeltaMovement();
		float strength = Math.min(1.0F, (float) Math.sqrt(
			motion.x * motion.x * 0.2 + motion.y * motion.y + motion.z * motion.z * 0.2) * 0.2F);
		this.playSound(strength < 0.25F ? this.getSwimSplashSound() : this.getSwimHighSpeedSplashSound(),
			strength, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);

		float surface = Mth.floor(this.getY()) + 1.0F;
		for (int i = 0; i < 8; i++) {
			double dx = (this.random.nextDouble() * 2.0 - 1.0) * this.getBbWidth();
			double dz = (this.random.nextDouble() * 2.0 - 1.0) * this.getBbWidth();
			this.level().addParticle(ParticleTypes.SPLASH,
				this.getX() + dx, surface, this.getZ() + dz, motion.x, motion.y, motion.z);
		}
		this.gameEvent(GameEvent.SPLASH);
	}

	@Override
	public void travel(Vec3 input) {
		if (this.isEffectiveAi() && this.isInWater()) {
			this.moveRelative(this.getSpeed(), input);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
		} else {
			super.travel(input);
		}
	}

	public void steerToward(Vec3 dir, float maxTurn, float maxPitch) {
		float wantYaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
		float wantPitch = (float) -(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))
			* (180.0 / Math.PI));
		float yaw = Mth.rotateIfNecessary(this.getYRot(), wantYaw, maxTurn);
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.setXRot(Mth.rotateIfNecessary(this.getXRot(),
			Mth.clamp(wantPitch, -maxPitch, maxPitch), maxTurn));
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {

			this.gapeSmoothO = this.gapeSmooth;
			this.gapeSmooth += (this.entityData.get(GAPE) - this.gapeSmooth) * GAPE_SMOOTHING;
			return;
		}

		for (WhaleHitboxPart.Segment segment : WhaleHitboxPart.Segment.VALUES) {
			WhaleHitboxPart part = this.parts[segment.ordinal()];
			if (part != null && !part.isRemoved()) {
				part.followParent();
				continue;
			}
			WhaleHitboxPart fresh = new WhaleHitboxPart(this, segment);
			fresh.followParent();
			this.parts[segment.ordinal()] = fresh;
			this.level().addFreshEntity(fresh);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		for (WhaleHitboxPart part : this.parts) {
			if (part != null) {
				part.discard();
			}
		}
		super.remove(reason);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
	                                    EntitySpawnReason spawnType, SpawnGroupData groupData) {

		this.setAirSupply(this.getMaxAirSupply());
		this.setXRot(0.0F);
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);

		if (groupData != null) {

			return groupData;
		}
		if (!podsAllowed(spawnType)) {
			return result;
		}

		WhalePodData pod = new WhalePodData();
		int extra = POD_EXTRA_MIN + this.random.nextInt(POD_EXTRA_MAX - POD_EXTRA_MIN + 1);
		for (int i = 0; i < extra; i++) {
			WhaleEntity companion = OceaniaEntities.WHALE.create(level.getLevel(), EntitySpawnReason.NATURAL);
			if (companion == null) {
				continue;
			}
			if (placeCompanion(level, companion)) {

				companion.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, pod);
				level.addFreshEntity(companion);
			} else {
				companion.discard();
			}
		}
		return pod;
	}

	private static boolean podsAllowed(EntitySpawnReason spawnType) {
		return spawnType == EntitySpawnReason.NATURAL
			|| spawnType == EntitySpawnReason.CHUNK_GENERATION
			|| EntitySpawnReason.isSpawner(spawnType);
	}

	private boolean placeCompanion(ServerLevelAccessor level, WhaleEntity companion) {
		for (int attempt = 0; attempt < POD_PLACE_TRIES; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double dist = POD_SPREAD_MIN + this.random.nextDouble() * (POD_SPREAD_MAX - POD_SPREAD_MIN);
			double x = this.getX() + Math.cos(angle) * dist;
			double z = this.getZ() + Math.sin(angle) * dist;
			double y = this.getY() + (this.random.nextDouble() - 0.5) * POD_SPREAD_Y;
			companion.snapTo(x, y, z, this.getYRot(), 0.0F);
			BlockPos pos = companion.blockPosition();

			AABB box = companion.getBoundingBox();
			if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
					BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
				continue;
			}

			if (level.getFluidState(pos).is(FluidTags.WATER)
				&& level.getFluidState(pos.above()).is(FluidTags.WATER)
				&& isOffshore(level, pos.getX(), pos.getZ())
				&& level.noCollision(companion)) {
				return true;
			}
		}
		return false;
	}

	public static class WhalePodData implements SpawnGroupData {
	}

	public static boolean checkWhaleSpawnRules(EntityType<WhaleEntity> type,
	                                           ServerLevelAccessor level,
	                                           EntitySpawnReason spawnType,
	                                           BlockPos pos,
	                                           RandomSource random) {
		return pos.getY() <= level.getSeaLevel() - MIN_SPAWN_DEPTH
			&& level.getFluidState(pos).is(FluidTags.WATER)
			&& level.getFluidState(pos.above()).is(FluidTags.WATER)
			&& level.getFluidState(pos.below()).is(FluidTags.WATER)
			&& isOffshore(level, pos.getX(), pos.getZ());
	}

	public static final double MIN_LAND_DISTANCE = 100.0;

	private static final int LAND_SCAN_STEP = 16;

	private static final java.util.Map<Long, Boolean> OFFSHORE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static boolean isOffshore(ServerLevelAccessor level, int blockX, int blockZ) {
		OceanFields fields = SeagullSpawner.fieldsFor(level.getLevel());
		if (fields == null) {
			return true;
		}
		long key = ChunkPos.pack(blockX >> 4, blockZ >> 4);
		Boolean cached = OFFSHORE_CACHE.get(key);
		if (cached != null) {
			return cached;
		}

		double cx = ((blockX >> 4) << 4) + 8.0;
		double cz = ((blockZ >> 4) << 4) + 8.0;

		double radius = MIN_LAND_DISTANCE + 12.0;
		boolean offshore = scanOffshore(fields, cx, cz, radius);
		OFFSHORE_CACHE.put(key, offshore);
		return offshore;
	}

	private static boolean scanOffshore(OceanFields fields, double cx, double cz, double radius) {
		if (fields.floorAt(Mth.floor(cx), Mth.floor(cz)) >= OceanFields.SEA_LEVEL) {
			return false;
		}
		for (double r = LAND_SCAN_STEP; r <= radius; r += LAND_SCAN_STEP) {
			int steps = Math.max(8, (int) Math.ceil(2.0 * Math.PI * r / LAND_SCAN_STEP));
			for (int i = 0; i < steps; i++) {
				double a = (2.0 * Math.PI * i) / steps;
				int x = Mth.floor(cx + Math.cos(a) * r);
				int z = Mth.floor(cz + Math.sin(a) * r);
				if (fields.floorAt(x, z) >= OceanFields.SEA_LEVEL) {
					return false;
				}
			}
		}
		return true;
	}
}
