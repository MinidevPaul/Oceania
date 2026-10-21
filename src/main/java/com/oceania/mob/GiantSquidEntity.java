package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.server.level.ServerLevel;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class GiantSquidEntity extends Monster {

	private static final EntityDataAccessor<Float> DATA_JET =
		SynchedEntityData.defineId(GiantSquidEntity.class, EntityDataSerializers.FLOAT);

	private static final EntityDataAccessor<Float> DATA_STRIKE =
		SynchedEntityData.defineId(GiantSquidEntity.class, EntityDataSerializers.FLOAT);

	public static final double STRIKE_DAMAGE = 21.0;

	public static final double STRIKE_REACH = 7.0;

	public static final int STRIKE_COOLDOWN = 70;

	private static final double CRUISE_SPEED = 0.09;

	private static final double JET_SPEED = 0.62;

	private static final float JET_DECAY = 0.88F;

	private static final float TURN_RATE = 0.055F;

	public float headYaw;
	public float headPitch;

	public float headYawO;
	public float headPitchO;

	public float roll;
	public float rollO;

	public float turnLag;
	public float turnLagO;

	public float wavePhase;
	public float wavePhaseO;

	private float jetEnvelope;
	private int strikeTimer;
	private int nextJet;

	private float wantYaw;
	private float wantPitch;

	public GiantSquidEntity(EntityType<? extends GiantSquidEntity> type, Level level) {
		super(type, level);
		this.setPathfindingMalus(PathType.WATER, 0.0F);

		this.nextJet = 120 + this.random.nextInt(200);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()

			.add(Attributes.MAX_HEALTH, 80.0)
			.add(Attributes.ARMOR, 6.0)
			.add(Attributes.ATTACK_DAMAGE, STRIKE_DAMAGE)
			.add(Attributes.ATTACK_KNOCKBACK, 1.6)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)

			.add(Attributes.FOLLOW_RANGE, 64.0)

			.add(Attributes.MOVEMENT_SPEED, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new GiantSquidHuntGoal(this));
		this.goalSelector.addGoal(2, new GiantSquidCruiseGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new GiantSquidTargetGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_JET, 0.0F);
		builder.define(DATA_STRIKE, 0.0F);
	}

	public float getJet() {
		return this.entityData.get(DATA_JET);
	}

	public float getStrike() {
		return this.entityData.get(DATA_STRIKE);
	}

	@Override
	public void travel(Vec3 travelVector) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public void aiStep() {
		this.headYawO = this.headYaw;
		this.headPitchO = this.headPitch;
		this.rollO = this.roll;
		this.turnLagO = this.turnLag;
		this.wavePhaseO = this.wavePhase;

		super.aiStep();

		if (!this.level().isClientSide()) {
			this.serverSteer();
		}

		this.wavePhase += this.jetEnvelope > 0.05F ? 0.18F : 0.055F;
	}

	private void serverSteer() {
		float yawBefore = this.headYaw;

		this.headYaw = approachAngle(this.headYaw, this.wantYaw, TURN_RATE * (1.0F + this.jetEnvelope));
		this.headPitch = approachAngle(this.headPitch, this.wantPitch, TURN_RATE * 0.7F);

		float yawRate = Mth.degreesDifference(yawBefore * Mth.RAD_TO_DEG,
			this.headYaw * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
		this.roll = Mth.lerp(0.18F, this.roll, Mth.clamp(-yawRate * 9.0F, -0.8F, 0.8F));

		this.turnLag = Mth.lerp(0.10F, this.turnLag, Mth.clamp(yawRate * 14.0F, -1.1F, 1.1F));

		if (this.jetEnvelope > 0.001F) {
			this.jetEnvelope *= JET_DECAY;
		} else {
			this.jetEnvelope = 0.0F;
		}
		this.entityData.set(DATA_JET, this.jetEnvelope);

		if (--this.nextJet <= 0 && this.getTarget() == null) {
			this.beginJet();
			this.nextJet = 220 + this.random.nextInt(320);
		}
		if (this.strikeTimer > 0) {
			this.strikeTimer--;
		}

		Vec3 forward = headingVector();

		double speed = CRUISE_SPEED + (JET_SPEED - CRUISE_SPEED) * this.jetEnvelope;
		Vec3 motion = this.jetEnvelope > 0.05F
			? forward.scale(-speed)
			: forward.scale(speed);

		this.setDeltaMovement(motion);

		this.setYRot(this.headYaw * Mth.RAD_TO_DEG - 90.0F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		this.setXRot(this.headPitch * Mth.RAD_TO_DEG);
	}

	public void setWantedHeading(float yaw, float pitch) {
		this.wantYaw = yaw;
		this.wantPitch = Mth.clamp(pitch, -1.2F, 1.2F);
	}

	public void aimAt(Vec3 point) {
		Vec3 d = point.subtract(this.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		this.setWantedHeading((float) Mth.atan2(d.z, d.x), (float) -Mth.atan2(d.y, flat));
	}

	public Vec3 headingVector() {
		float cp = Mth.cos(this.headPitch);
		return new Vec3(Mth.cos(this.headYaw) * cp, -Mth.sin(this.headPitch),
			Mth.sin(this.headYaw) * cp);
	}

	public void beginJet() {
		this.jetEnvelope = 1.0F;
	}

	public boolean canStrike() {
		return this.strikeTimer <= 0;
	}

	public boolean isFacing(net.minecraft.world.entity.Entity target, double tolerance) {
		Vec3 toTarget = target.position().subtract(this.position());
		if (toTarget.lengthSqr() < 1.0E-4) {
			return true;
		}
		return this.headingVector().dot(toTarget.normalize()) > Math.cos(tolerance);
	}

	public static final double STRIKE_ARC = 0.5;

	public boolean strike(LivingEntity victim) {
		this.strikeTimer = STRIKE_COOLDOWN;
		this.entityData.set(DATA_STRIKE, 1.0F);
		boolean hurt = this.level() instanceof ServerLevel serverLevel
			&& victim.hurtServer(serverLevel, this.damageSources().mobAttack(this), (float) STRIKE_DAMAGE);
		if (hurt) {

			Vec3 pull = this.position().subtract(victim.position()).normalize().scale(0.55);
			victim.push(pull.x, pull.y * 0.4, pull.z);
		}
		return hurt;
	}

	public void relaxStrike() {
		this.entityData.set(DATA_STRIKE,
			Math.max(0.0F, this.entityData.get(DATA_STRIKE) - 0.08F));
	}

	private static float approachAngle(float current, float want, float rate) {
		float diff = Mth.wrapDegrees((want - current) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
		return current + Mth.clamp(diff, -rate, rate);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("HeadYaw", this.headYaw);
		tag.putFloat("HeadPitch", this.headPitch);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		this.headYaw = tag.getFloatOr("HeadYaw", 0.0F);
		this.headPitch = tag.getFloatOr("HeadPitch", 0.0F);
		this.wantYaw = this.headYaw;
		this.wantPitch = this.headPitch;
	}

	public static boolean checkGiantSquidSpawnRules(EntityType<GiantSquidEntity> type,
	                                                ServerLevelAccessor level,
	                                                EntitySpawnReason spawnType,
	                                                BlockPos pos,
	                                                net.minecraft.util.RandomSource random) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
			return false;
		}

		return pos.getY() < level.getLevel().getSeaLevel() - 90;
	}

	public boolean isHuntable(LivingEntity other) {
		if (other instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		return other.getType() == OceaniaEntities.WHALE;
	}
}
