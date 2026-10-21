package com.oceania.mob;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CoconutCrabEntity extends AbstractCrab {

	private static final EntityDataAccessor<Byte> DATA_MORPH =
		SynchedEntityData.defineId(CoconutCrabEntity.class, EntityDataSerializers.BYTE);

	private static final String TAG_MORPH = "Morph";

	private boolean morphFromNbt;

	private static final float FLEE_RADIUS = 11.0F;
	private static final double FLEE_FAST = 1.55;
	private static final double FLEE_SLOW = 1.15;

	public CoconutCrabEntity(EntityType<? extends CoconutCrabEntity> type, Level level) {
		super(type, level, FLEE_RADIUS, FLEE_FAST, FLEE_SLOW);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()

			.add(Attributes.MAX_HEALTH, 4.0)

			.add(Attributes.MOVEMENT_SPEED, 0.44)

			.add(Attributes.FOLLOW_RANGE, 12.0)

			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected Goal strollGoal() {
		return new WaterAvoidingRandomStrollGoal(this, 0.9);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MORPH, (byte) 0);
	}

	public CrabMorph getMorph() {
		return CrabMorph.byId(this.entityData.get(DATA_MORPH));
	}

	public void setMorph(CrabMorph morph) {
		this.entityData.set(DATA_MORPH, (byte) morph.ordinal());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte(TAG_MORPH, (byte) this.getMorph().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		tag.getInt(TAG_MORPH).ifPresent(morph -> {
			this.entityData.set(DATA_MORPH, (byte) (int) morph);
			this.morphFromNbt = true;
		});
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
	                                    EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
		if (!this.morphFromNbt) {
			this.setMorph(this.random.nextInt(4) == 0 ? CrabMorph.ORANGE : CrabMorph.BLUE);
		}
		return super.finalizeSpawn(level, difficulty, reason, spawnData);
	}

	public enum CrabMorph {
		BLUE,
		ORANGE;

		private static final CrabMorph[] VALUES = values();

		public static CrabMorph byId(byte id) {
			return VALUES[Math.floorMod(id, VALUES.length)];
		}
	}
}
