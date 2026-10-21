package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.server.level.ServerLevel;
import com.oceania.raft.RaftRiders;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

public class SharkEntity extends Monster {

	private static final EntityDataAccessor<Byte> PHASE =
		SynchedEntityData.defineId(SharkEntity.class, EntityDataSerializers.BYTE);

	private static final EntityDataAccessor<Byte> VARIANT =
		SynchedEntityData.defineId(SharkEntity.class, EntityDataSerializers.BYTE);

	public static final byte PHASE_CRUISE = 0;
	public static final byte PHASE_CIRCLE = 1;
	public static final byte PHASE_WINDUP = 2;
	public static final byte PHASE_CHARGE = 3;
	public static final byte PHASE_WITHDRAW = 4;

	private static final int DRY_GRACE = 60;

	private static final int HUNT_DEPTH = 64;

	private static final double SURFACE_RIDE = 0.35;

	private int dryTicks;

	private long lastParryTick = Long.MIN_VALUE / 2;

	private boolean variantLocked;

	private boolean variantResolved;

	public float bankAmount;
	public float bankAmountO;

	public float jawOpen;

	public SharkEntity(EntityType<? extends SharkEntity> type, Level level) {
		super(type, level);

		this.moveControl = new SmoothSwimmingMoveControl(this, 85, 5, 0.02F, 0.1F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 10);

		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 26.0)
			.add(Attributes.MOVEMENT_SPEED, 1.1)
			.add(Attributes.ATTACK_DAMAGE, 11.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.0)
			.add(Attributes.ARMOR, 5.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.SCALE, 1.0);
	}

	public SharkVariant getVariant() {
		return SharkVariant.byId(this.entityData.get(VARIANT));
	}

	@Override
	protected net.minecraft.network.chat.Component getTypeName() {
		return net.minecraft.network.chat.Component.translatable(
			"entity.oceania.shark." + this.getVariant().getSerializedName());
	}

	public void setVariant(SharkVariant variant) {
		this.entityData.set(VARIANT, (byte) variant.ordinal());
		this.applyVariant(variant);
	}

	private void applyVariant(SharkVariant variant) {
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(variant.health());
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(variant.damage());
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(variant.speed());

		this.getAttribute(Attributes.SCALE).setBaseValue(variant.scale());
	}

	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
			net.minecraft.world.level.ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty,
			EntitySpawnReason spawnType,
			net.minecraft.world.entity.SpawnGroupData groupData) {

		if (!this.variantLocked) {
			this.resolveVariant(level);
			this.setHealth(this.getMaxHealth());
		}
		return super.finalizeSpawn(level, difficulty, spawnType, groupData);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new SharkHuntGoal(this));

		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(3, new SharkCruiseGoal(this, 1.0, 36));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new SharkTargetGoals.Swimmers(this));
		this.targetSelector.addGoal(3, new SharkTargetGoals.Prey(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PHASE, PHASE_CRUISE);
		builder.define(VARIANT, (byte) 0);
	}

	public byte getPhase() {
		return this.entityData.get(PHASE);
	}

	public void setPhase(byte phase) {
		this.entityData.set(PHASE, phase);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	private void holdBelowSurface() {
		if (!this.isInWater()) {
			return;
		}
		net.minecraft.world.phys.AABB box = this.getBoundingBox();
		net.minecraft.core.BlockPos.MutableBlockPos probe = new net.minecraft.core.BlockPos.MutableBlockPos(
			Mth.floor(this.getX()), Mth.floor(box.maxY), Mth.floor(this.getZ()));

		int steps = 4;
		while (steps-- > 0 && !this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
			probe.move(net.minecraft.core.Direction.DOWN);
		}
		if (!this.level().getFluidState(probe).is(net.minecraft.tags.FluidTags.WATER)) {
			return;
		}
		double surface = probe.getY() + this.level().getFluidState(probe).getHeight(this.level(), probe);
		double over = box.maxY - (surface + box.getYsize() * SURFACE_RIDE);
		if (over > 0.0) {
			this.setPos(this.getX(), this.getY() - over, this.getZ());
			Vec3 motion = this.getDeltaMovement();
			if (motion.y > 0.0) {
				this.setDeltaMovement(motion.x, 0.0, motion.z);
			}
		}
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		return 0.0F;
	}

	@Override
	public boolean checkSpawnObstruction(LevelReader level) {

		this.resolveVariant(level);
		return level.isUnobstructed(this);
	}

	private void resolveVariant(LevelReader level) {
		if (this.variantResolved || this.variantLocked) {
			return;
		}
		this.setVariant(variantFor(level, this.blockPosition(), this.random));
		this.variantResolved = true;
		this.refreshDimensions();
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
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player) {
			this.lastParryTick = this.level().getGameTime();
		}
		return hurt;
	}

	public boolean wasParriedSince(long since) {
		return this.lastParryTick >= since;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {

			this.bankAmountO = this.bankAmount;
			float turn = Mth.degreesDifference(this.yRotO, this.getYRot());
			this.bankAmount += (Mth.clamp(turn / 6.0F, -1.0F, 1.0F) - this.bankAmount) * 0.25F;

			byte phase = this.getPhase();
			float wantJaw = (phase == PHASE_WINDUP || phase == PHASE_CHARGE) ? 1.0F : 0.0F;
			this.jawOpen += (wantJaw - this.jawOpen) * 0.25F;
			return;
		}

		this.holdBelowSurface();

		if (this.isInWater()) {
			this.dryTicks = 0;
		} else {
			this.dryTicks++;
			if (this.dryTicks > DRY_GRACE && this.dryTicks % 20 == 0) {
				this.hurt(this.damageSources().dryOut(), 2.0F);
			}
			if (this.onGround()) {

				this.setDeltaMovement(this.getDeltaMovement()
					.add((this.random.nextFloat() * 2.0F - 1.0F) * 0.2, 0.4, (this.random.nextFloat() * 2.0F - 1.0F) * 0.2));
				this.setYRot(this.random.nextFloat() * 360.0F);
				this.setOnGround(false);
				this.needsSync = true;
			}
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (this.isEffectiveAi() && this.isInWater()) {
			this.moveRelative(this.getSpeed(), input);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
			if (this.getTarget() == null) {
				this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
			}
		} else {
			super.travel(input);
		}
	}

	public static boolean worthLoitering(LivingEntity target, long gameTime) {
		return target.isInWater()
			|| RaftRiders.isOnRaft(target, gameTime);
	}

	public static boolean isHuntable(LivingEntity target, long gameTime) {
		if (!target.isInWater()) {
			return false;
		}
		if (RaftRiders.isOnRaft(target, gameTime)) {
			return false;
		}
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("DryTicks", this.dryTicks);
		tag.putString("Variant", this.getVariant().getSerializedName());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		this.dryTicks = tag.getIntOr("DryTicks", 0);
		tag.getString("Variant").ifPresent(name -> {
			for (SharkVariant v : SharkVariant.values()) {
				if (v.getSerializedName().equals(name)) {

					this.entityData.set(VARIANT, (byte) v.ordinal());
					this.applyVariant(v);
					this.variantLocked = true;
					break;
				}
			}
		});
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
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
	public int getMaxAirSupply() {
		return 4800;
	}

	@Override
	protected int increaseAirSupply(int air) {
		return this.getMaxAirSupply();
	}

	private static SharkVariant variantFor(LevelReader level, BlockPos pos,
	                                       net.minecraft.util.RandomSource random) {
		String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		return switch (biome) {
			case "reef", "barren_flank" -> SharkVariant.REEF;

			case "lagoon", "sandy_shelf" -> SharkVariant.BULL;
			case "kelp_forest", "rocky_floor" -> SharkVariant.TIGER;
			case "seamount_flank" -> random.nextInt(4) == 0 ? SharkVariant.GREAT_WHITE : SharkVariant.TIGER;
			case "abyssal_plain", "ridge", "trench" -> SharkVariant.GREAT_WHITE;

			default -> SharkVariant.byId(random.nextInt(SharkVariant.BY_ID.length));
		};
	}

	public static boolean checkSharkSpawnRules(EntityType<SharkEntity> type,
	                                           net.minecraft.world.level.ServerLevelAccessor level,
	                                           EntitySpawnReason spawnType,
	                                           BlockPos pos,
	                                           net.minecraft.util.RandomSource random) {
		return level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
			&& pos.getY() >= level.getSeaLevel() - HUNT_DEPTH
			&& level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
			&& level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);

	}
}
