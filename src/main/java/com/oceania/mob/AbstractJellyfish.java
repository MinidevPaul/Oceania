package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.oceania.Oceania;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public abstract class AbstractJellyfish extends WaterAnimal {

	public static final int TINT_COUNT = 3;

	private static final float DRIFT_HORIZONTAL = floatProperty("oceania.jellyfish.drift", 0.038F);
	private static final float DRIFT_VERTICAL = floatProperty("oceania.jellyfish.drift.vertical", 0.016F);

	private static final float COAST = 0.97F;

	private static final float PULSE_SPREAD = 0.18F;

	private static final EntityDataAccessor<Byte> DATA_TINT =
		SynchedEntityData.defineId(AbstractJellyfish.class, EntityDataSerializers.BYTE);

	private static final String TAG_TINT = "Tint";

	private boolean tintFromNbt;

	public float leanX;
	public float leanXO;
	public float leanZ;
	public float leanZO;

	private static final float LEAN_PER_SPEED = 16.0F;
	private static final float MAX_LEAN = 0.70F;

	private static final float LEAN_SMOOTHING = 0.07F;

	private float tx;
	private float ty;
	private float tz;

	private float thrust;

	private int pulsePeriod;
	private int pulseOffset;

	protected AbstractJellyfish(EntityType<? extends AbstractJellyfish> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes().add(Attributes.MAX_HEALTH, 4.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new JellyfishDriftGoal(this));
	}

	protected abstract int basePulseTicks();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TINT, (byte) 0);
	}

	public int getTint() {
		return Math.floorMod(this.entityData.get(DATA_TINT), TINT_COUNT);
	}

	public void setTint(int tint) {
		this.entityData.set(DATA_TINT, (byte) Math.floorMod(tint, TINT_COUNT));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte(TAG_TINT, (byte) this.getTint());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		tag.getInt(TAG_TINT).ifPresent(tint -> {
			this.setTint(tint);
			this.tintFromNbt = true;
		});
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
	                                    EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
		if (!this.tintFromNbt) {
			this.setTint(this.random.nextInt(TINT_COUNT));
		}
		return super.finalizeSpawn(level, difficulty, reason, spawnData);
	}

	public float bellTurns(float partialTick) {
		if (this.pulsePeriod == 0) {
			this.resolvePulse();
		}
		long now = this.level().getGameTime() + this.pulseOffset;
		return (Math.floorMod(now, this.pulsePeriod) + partialTick) / this.pulsePeriod;
	}

	private void resolvePulse() {
		RandomSource random = RandomSource.create(this.getUUID().getLeastSignificantBits());
		float spread = 1.0F - PULSE_SPREAD + 2.0F * PULSE_SPREAD * random.nextFloat();
		this.pulsePeriod = Math.max(2, Math.round(this.basePulseTicks() * spread));
		this.pulseOffset = random.nextInt(this.pulsePeriod);
	}

	@Override
	public void travel(Vec3 travelVector) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	private static final double SUBMERSION = Double.parseDouble(
		System.getProperty("oceania.jellyfish.submersion", "0.25"));

	private static final int SURFACE_SCAN = 8;

	private void keepUnderWater() {

		if (this.level().isClientSide()) {
			return;
		}

		double top = this.getY() + this.getBbHeight();
		if (top < this.level().getSeaLevel() - SURFACE_SCAN) {
			return;
		}

		net.minecraft.core.BlockPos.MutableBlockPos probe =
			new net.minecraft.core.BlockPos.MutableBlockPos(
				this.getBlockX(), Mth.floor(top), this.getBlockZ());
		if (!this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {

			probe.move(net.minecraft.core.Direction.DOWN);
			while (probe.getY() > this.level().getMinY()
				&& !this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
				probe.move(net.minecraft.core.Direction.DOWN);
			}
			if (!this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
				return;
			}
		} else {
			for (int i = 0; i < SURFACE_SCAN; i++) {
				probe.move(net.minecraft.core.Direction.UP);
				if (!this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
					probe.move(net.minecraft.core.Direction.DOWN);
					break;
				}
			}
		}

		double surface = probe.getY()
			+ this.level().getFluidState(probe).getHeight(this.level(), probe);
		double maxY = surface - this.getBbHeight() - SUBMERSION;
		if (this.getY() <= maxY) {
			return;
		}

		this.setPos(this.getX(), maxY, this.getZ());
		Vec3 delta = this.getDeltaMovement();
		if (delta.y > 0.0) {

			this.setDeltaMovement(delta.x, 0.0, delta.z);
		}
	}

	private void updateLean() {
		this.setYRot(0.0F);
		this.yBodyRot = 0.0F;
		this.yHeadRot = 0.0F;

		this.leanXO = this.leanX;
		this.leanZO = this.leanZ;

		Vec3 v = this.getDeltaMovement();
		float wantX = (float) Mth.clamp(v.z * LEAN_PER_SPEED, -MAX_LEAN, MAX_LEAN);
		float wantZ = (float) Mth.clamp(-v.x * LEAN_PER_SPEED, -MAX_LEAN, MAX_LEAN);
		this.leanX = Mth.lerp(LEAN_SMOOTHING, this.leanX, wantX);
		this.leanZ = Mth.lerp(LEAN_SMOOTHING, this.leanZ, wantZ);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.updateLean();
		this.keepUnderWater();

		if (this.isInWater()) {

			float stroke = Mth.sin(this.bellTurns(0.0F) * Mth.TWO_PI);
			this.thrust = Math.max(this.thrust * COAST, Math.max(0.0F, stroke));

			if (!this.level().isClientSide()) {
				this.setDeltaMovement(this.tx * this.thrust, this.ty * this.thrust, this.tz * this.thrust);
			}

		} else {

			if (!this.level().isClientSide()) {
				double dy = this.getDeltaMovement().y;
				MobEffectInstance levitation = this.getEffect(MobEffects.LEVITATION);
				if (levitation != null) {
					dy = 0.05 * (levitation.getAmplifier() + 1);
				} else if (!this.isNoGravity()) {
					dy -= this.getGravity();
				}
				this.setDeltaMovement(0.0, dy * 0.98, 0.0);
			}
			this.thrust = 0.0F;
		}
	}

	public void setDriftVector(float x, float y, float z) {
		this.tx = x;
		this.ty = y;
		this.tz = z;
	}

	public boolean hasDriftVector() {
		return this.tx != 0.0F || this.ty != 0.0F || this.tz != 0.0F;
	}

	public static float driftHorizontal() {
		return DRIFT_HORIZONTAL;
	}

	public static float driftVertical() {
		return DRIFT_VERTICAL;
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	@Nullable
	protected SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	@Nullable
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected MovementEmission getMovementEmission() {
		return MovementEmission.EVENTS;
	}

	private static float floatProperty(String key, float fallback) {
		String raw = System.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		try {
			return Float.parseFloat(raw);
		} catch (NumberFormatException e) {
			Oceania.LOGGER.warn("[jellyfish] -D{}={} is not a number; using {}", key, raw, fallback);
			return fallback;
		}
	}

	static EnumSet<Goal.Flag> moveOnly() {
		return EnumSet.of(Goal.Flag.MOVE);
	}
}
