package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class SeagullEntity extends Mob {

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public void travel(Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	public static final int BEAT_TICKS = 7;

	private static final float PHASE_TICKS_PER_ID = 7.31F;

	private static final float TOP_SPEED = 0.67F;

	private static final float TURN_SPEED = 0.30F;

	private static final float ACCELERATION = 0.002F;

	private static final float DECELERATION = 0.008F;

	private static final float YAW_PER_TICK = 2.0F;

	private static final float HARD_TURN_YAW = 1.5F;

	private static final double DELTA_BLEND = 0.2;

	private static final float GLIDE_DRIVE = 0.25F;

	private static final float FLAP_DRIVE = 0.70F;

	private static final float GLIDE_FLOOR = 0.10F;

	private static final float CLIMB_WEIGHT = 3.0F;

	private static final int DESPAWN_DISTANCE = 160;

	private static final int THUNDER_DESPAWN_DISTANCE = 48;

	private static final int ANCHOR_HEIGHT_MIN = 18;

	Vec3 moveTargetPoint = Vec3.ZERO;

	private BlockPos anchorPoint = BlockPos.ZERO;

	public SeagullEntity(EntityType<? extends SeagullEntity> type, Level level) {
		super(type, level);
		this.moveControl = new SeagullMoveControl(this);
		this.lookControl = new SeagullLookControl(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()

			.add(Attributes.MAX_HEALTH, 4.0)

			.add(Attributes.FOLLOW_RANGE, 4.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new SeagullAnchorGoal(this));
		this.goalSelector.addGoal(2, new SeagullCircleGoal(this));
	}

	@Override
	protected BodyRotationControl createBodyControl() {
		return new SeagullBodyRotationControl(this);
	}

	public BlockPos getAnchorPoint() {
		return this.anchorPoint;
	}

	public void setAnchorPoint(BlockPos pos) {
		int floor = this.level().getSeaLevel() + ANCHOR_HEIGHT_MIN;
		this.anchorPoint = pos.getY() < floor ? new BlockPos(pos.getX(), floor, pos.getZ()) : pos;
	}

	public float getUniqueFlapTickOffset() {
		return this.getId() * PHASE_TICKS_PER_ID;
	}

	public float getWingPhase(float ageInTicks) {
		return (ageInTicks + this.getUniqueFlapTickOffset()) * (Mth.TWO_PI / BEAT_TICKS);
	}

	public float getFlightSpeed() {
		Vec3 v = this.getDeltaMovement();
		return (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
	}

	public float getFlapAmplitude() {
		Vec3 v = this.getDeltaMovement();
		double drive = Math.sqrt(v.x * v.x + v.z * v.z) + v.y * CLIMB_WEIGHT;
		float t = (float) Mth.clamp((drive - GLIDE_DRIVE) / (FLAP_DRIVE - GLIDE_DRIVE), 0.0, 1.0);
		return GLIDE_FLOOR + (1.0F - GLIDE_FLOOR) * t;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return true;
	}

	@Override
	public void checkDespawn() {
		if (this.isPersistenceRequired() || this.requiresCustomPersistence()) {
			this.noActionTime = 0;
			return;
		}

		Player nearest = this.level().getNearestPlayer(this, -1.0);
		if (nearest == null) {
			return;
		}

		int radius = this.level().isThundering() ? THUNDER_DESPAWN_DISTANCE : DESPAWN_DISTANCE;

		double distSqr = nearest.distanceToSqr(this);
		if (distSqr > (double) radius * radius && this.removeWhenFarAway(distSqr)) {
			this.discard();
		} else {
			this.noActionTime = 0;
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
	                                    EntitySpawnReason spawnType, SpawnGroupData groupData) {
		this.setAnchorPoint(this.blockPosition());
		return super.finalizeSpawn(level, difficulty, spawnType, groupData);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		tag.getInt("AX").ifPresent(ax -> this.anchorPoint = new BlockPos(ax, tag.getIntOr("AY", 0), tag.getIntOr("AZ", 0)));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("AX", this.anchorPoint.getX());
		tag.putInt("AY", this.anchorPoint.getY());
		tag.putInt("AZ", this.anchorPoint.getZ());
	}

	private static class SeagullLookControl extends LookControl {
		SeagullLookControl(Mob mob) {
			super(mob);
		}

		@Override
		public void tick() {
		}
	}

	private class SeagullBodyRotationControl extends BodyRotationControl {
		SeagullBodyRotationControl(Mob mob) {
			super(mob);
		}

		@Override
		public void clientTick() {
			SeagullEntity.this.yHeadRot = SeagullEntity.this.yBodyRot;
			SeagullEntity.this.yBodyRot = SeagullEntity.this.getYRot();
		}
	}

	private class SeagullMoveControl extends MoveControl {

		private float speed = TURN_SPEED;

		SeagullMoveControl(Mob mob) {
			super(mob);
		}

		@Override
		public void tick() {
			if (SeagullEntity.this.horizontalCollision) {

				SeagullEntity.this.setYRot(SeagullEntity.this.getYRot() + 180.0F);
				this.speed = TURN_SPEED;
			}

			double dx = SeagullEntity.this.moveTargetPoint.x - SeagullEntity.this.getX();
			double dy = SeagullEntity.this.moveTargetPoint.y - SeagullEntity.this.getY();
			double dz = SeagullEntity.this.moveTargetPoint.z - SeagullEntity.this.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (Math.abs(horizontal) <= 1.0E-5F) {
				return;
			}

			double shorten = 1.0 - Math.abs(dy * 0.7F) / horizontal;
			dx *= shorten;
			dz *= shorten;
			horizontal = Math.sqrt(dx * dx + dz * dz);
			double total = Math.sqrt(dx * dx + dz * dz + dy * dy);

			float yawBefore = SeagullEntity.this.getYRot();
			float wanted = (float) Mth.atan2(dz, dx);
			float from = Mth.wrapDegrees(SeagullEntity.this.getYRot() + 90.0F);
			float to = Mth.wrapDegrees(wanted * (180.0F / (float) Math.PI));
			SeagullEntity.this.setYRot(Mth.approachDegrees(from, to, YAW_PER_TICK) - 90.0F);
			SeagullEntity.this.yBodyRot = SeagullEntity.this.getYRot();

			if (Mth.degreesDifferenceAbs(yawBefore, SeagullEntity.this.getYRot()) < HARD_TURN_YAW) {
				this.speed = Mth.approach(this.speed, TOP_SPEED, ACCELERATION * (TOP_SPEED / this.speed));
			} else {
				this.speed = Mth.approach(this.speed, TURN_SPEED, DECELERATION);
			}

			float pitch = (float) (-(Mth.atan2(-dy, horizontal) * 180.0F / (float) Math.PI));
			SeagullEntity.this.setXRot(pitch);
			float heading = SeagullEntity.this.getYRot() + 90.0F;
			double vx = this.speed * Mth.cos(heading * Mth.DEG_TO_RAD) * Math.abs(dx / total);
			double vz = this.speed * Mth.sin(heading * Mth.DEG_TO_RAD) * Math.abs(dz / total);
			double vy = this.speed * Mth.sin(pitch * Mth.DEG_TO_RAD) * Math.abs(dy / total);
			Vec3 delta = SeagullEntity.this.getDeltaMovement();
			SeagullEntity.this.setDeltaMovement(
				delta.add(new Vec3(vx, vy, vz).subtract(delta).scale(DELTA_BLEND)));
		}
	}
}
