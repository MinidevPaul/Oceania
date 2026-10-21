package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.server.level.ServerLevel;
import com.oceania.registry.OceaniaEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class WhaleHitboxPart extends Entity {

	public static final double BODY_LENGTH = 24.0;

	private static final EntityDataAccessor<Byte> DATA_SEGMENT =
		SynchedEntityData.defineId(WhaleHitboxPart.class, EntityDataSerializers.BYTE);

	@Nullable
	private final WhaleEntity parent;

	public WhaleHitboxPart(EntityType<? extends WhaleHitboxPart> type, Level level) {
		super(type, level);
		this.parent = null;
	}

	public WhaleHitboxPart(WhaleEntity parent, Segment segment) {
		super(OceaniaEntities.WHALE_PART, parent.level());
		this.parent = parent;
		this.entityData.set(DATA_SEGMENT, (byte) (segment.ordinal() + 1));

		this.refreshDimensions();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SEGMENT, (byte) 0);
	}

	public void followParent() {
		WhaleEntity whale = this.parent;
		if (whale == null || whale.isRemoved()) {
			return;
		}

		Segment segment = this.segment();

		Vec3 axis = this.calculateViewVector(whale.getXRot(), whale.yBodyRot);
		double along = segment.forwardOffset;

		double axisY = whale.getY() + whale.getBbHeight() * 0.5;

		this.setPos(
			whale.getX() + axis.x * along,
			axisY + axis.y * along - segment.box.height() * 0.5,
			whale.getZ() + axis.z * along
		);
		this.setYRot(whale.yBodyRot);
		this.setXRot(whale.getXRot());

		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
		this.xo = this.getX();
		this.yo = this.getY();
		this.zo = this.getZ();

		this.setDeltaMovement(Vec3.ZERO);

		this.needsSync = true;
	}

	@Override
	public void tick() {
		this.firstTick = false;
		if (!this.level().isClientSide() && (this.parent == null || !this.parent.isAlive())) {

			this.discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		if (this.parent == null || this.isInvulnerableToBase(damageSource)) {
			return false;
		}

		return this.parent.hurtServer(level, damageSource, amount);
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return this.segment().box;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		if (DATA_SEGMENT.equals(accessor)) {
			this.refreshDimensions();
		}

		super.onSyncedDataUpdated(accessor);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isInvisible() {
		return true;
	}

	@Nullable
	@Override
	public ItemStack getPickResult() {
		return this.parent == null ? null : this.parent.getPickResult();
	}

	@Override
	public boolean is(Entity entity) {
		return this == entity || this.parent == entity;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput compoundTag) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput compoundTag) {
	}

	@Nullable
	public WhaleEntity parent() {
		return this.parent;
	}

	public boolean isPartOf(Entity entity) {
		return this.parent == entity;
	}

	public Segment segment() {

		int index = this.entityData.get(DATA_SEGMENT) - 1;
		return index >= 0 && index < Segment.VALUES.length ? Segment.VALUES[index] : Segment.CHEST;
	}

	public enum Segment {
		SNOUT(0.045F, 5.0F, 4.0F),
		HEAD(0.17F, 6.0F, 5.0F),
		CHEST(0.33F, 6.0F, 5.0F),
		MID(0.50F, 6.0F, 5.0F),
		REAR(0.68F, 5.0F, 4.0F),
		PEDUNCLE(0.84F, 4.0F, 3.0F),
		FLUKES(0.97F, 6.0F, 2.0F);

		public static final Segment[] VALUES = values();

		public final float fraction;

		public final double forwardOffset;
		public final EntityDimensions box;

		Segment(float fraction, float width, float height) {
			this.fraction = fraction;

			this.forwardOffset = (0.5F - fraction) * BODY_LENGTH;

			this.box = EntityDimensions.fixed(width, height);
		}
	}
}
