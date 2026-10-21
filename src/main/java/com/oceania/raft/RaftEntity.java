package com.oceania.raft;

import com.oceania.registry.OceaniaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RaftEntity extends Entity {

	public static final double BASE_SPEED = Double.parseDouble(System.getProperty("oceania.raftSpeed", "8"));

	public static final float TURN_ACCEL = Float.parseFloat(System.getProperty("oceania.raftTurn", "0.5"));
	public static final double FRICTION = 0.9;

	public static final double FORWARD_ACCEL = BASE_SPEED / 20.0 * (1.0 - FRICTION);
	public static final double REVERSE_FACTOR = 0.25;

	public static final double DRAFT = 0.5;
	public static final int LERP_STEPS = 3;

	private static final EntityDataAccessor<String> SEATS = SynchedEntityData.defineId(RaftEntity.class, EntityDataSerializers.STRING);

	private static final double[] NUDGES = {0.125, 0.25, 0.5};

	private static final double[] SAMPLE_X = {0.5, 0.05, 0.95, 0.05, 0.95};
	private static final double[] SAMPLE_Z = {0.5, 0.05, 0.05, 0.95, 0.95};

	private RaftBlocks blocks = new RaftBlocks();
	private @Nullable RaftLevel raftLevel;
	private float deltaRotation;

	private float debugStrafe, debugForward;

	private final Map<BlockPos, Integer> breakStages = new HashMap<>();

	private final RaftWorldLight worldLight = new RaftWorldLight(this);

	public RaftEntity(EntityType<? extends RaftEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	public RaftBlocks blocks() {
		return blocks;
	}

	public RaftLevel raftLevel() {
		if (raftLevel == null) {
			raftLevel = new RaftLevel(this, level());
		}
		return raftLevel;
	}

	public void setBlocks(RaftBlocks newBlocks) {
		this.blocks = newBlocks;
		raftLevel().loadBlockEntities(newBlocks.takePendingNbt());
		raftLevel().markLightDirty();
		setBoundingBox(makeBoundingBox());
	}

	void onBlockChanged(BlockPos pos, BlockState old, BlockState state) {
		setBoundingBox(makeBoundingBox());
		raftLevel().markLightDirty();
		if (state.isAir()) breakStages.remove(pos);
	}

	public float yawRadians() {
		return getYRot() * Mth.DEG_TO_RAD;
	}

	public Vec3 toWorld(Vec3 local) {
		return toWorld(local, getX(), getY(), getZ(), yawRadians());
	}

	public Vec3 toWorld(Vec3 local, float partialTick) {
		Vec3 p = getPosition(partialTick);
		return toWorld(local, p.x, p.y, p.z, getViewYRot(partialTick) * Mth.DEG_TO_RAD);
	}

	private Vec3 toWorld(Vec3 local, double x, double y, double z, float yawRad) {
		Vec3 pivot = blocks.pivot();
		Vec3 rel = new Vec3(local.x - pivot.x, local.y, local.z - pivot.z).yRot(-yawRad);
		return new Vec3(x + pivot.x + rel.x, y + rel.y, z + pivot.z + rel.z);
	}

	public Vec3 toLocal(Vec3 world) {
		return toLocal(world, getX(), getY(), getZ(), yawRadians());
	}

	public Vec3 toLocal(Vec3 world, float partialTick) {
		Vec3 p = getPosition(partialTick);
		return toLocal(world, p.x, p.y, p.z, getViewYRot(partialTick) * Mth.DEG_TO_RAD);
	}

	private Vec3 toLocal(Vec3 world, double x, double y, double z, float yawRad) {
		Vec3 pivot = blocks.pivot();
		Vec3 rel = new Vec3(world.x - x - pivot.x, world.y - y, world.z - z - pivot.z).yRot(yawRad);
		return new Vec3(rel.x + pivot.x, rel.y, rel.z + pivot.z);
	}

	public Vec3 rotateToWorld(Vec3 localDir) {
		return localDir.yRot(-yawRadians());
	}

	public Vec3 rotateToLocal(Vec3 worldDir) {
		return worldDir.yRot(yawRadians());
	}

	@Override
	protected AABB makeBoundingBox(Vec3 at) {
		if (blocks == null || blocks.isEmpty()) {
			return super.makeBoundingBox(at);
		}
		AABB b = blocks.localBounds();
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			Vec3 corner = toWorld(new Vec3((i & 1) == 0 ? b.minX : b.maxX, 0, (i & 2) == 0 ? b.minZ : b.maxZ), at.x, at.y, at.z, yawRadians());
			minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
			minZ = Math.min(minZ, corner.z); maxZ = Math.max(maxZ, corner.z);
		}
		return new AABB(minX, at.y + b.minY, minZ, maxX, at.y + b.maxY, maxZ);
	}

	public void setDebugInput(float strafe, float forward) {
		this.debugStrafe = strafe;
		this.debugForward = forward;
	}

	public double sizeFactor() {
		return 1.0;
	}

	public Item houseboatItem() {
		BlockPos core = blocks.core();
		BlockState helm = core == null ? null : blocks.get(core);
		if (helm != null && helm.is(OceaniaBlocks.RAFT_HELM)) return RaftHelmBlock.houseboat(helm);
		BlockState under = core == null ? null : blocks.get(core.below());
		return RaftItems.houseboat(under == null ? Blocks.JUNGLE_PLANKS : under.getBlock());
	}

	public boolean withinReach(Player player, BlockPos local, double padding) {
		AABB cell = raftLevel().worldBounds(new AABB(local));
		double reach = player.blockInteractionRange() + padding;
		return cell.distanceToSqr(player.getEyePosition()) < reach * reach;
	}

	public boolean isSeatOccupied(BlockPos pos) {
		for (Entity passenger : getPassengers()) {
			if (pos.equals(seatOf(passenger))) return true;
		}
		return false;
	}

	public @Nullable BlockPos seatOf(Entity passenger) {
		Long packed = seats().get(passenger.getStringUUID());
		return packed == null ? null : BlockPos.of(packed);
	}

	private void setSeat(Entity passenger, @Nullable BlockPos seat) {
		Map<String, Long> seats = new LinkedHashMap<>(seats());
		if (seat == null) {
			seats.remove(passenger.getStringUUID());
		} else {
			seats.put(passenger.getStringUUID(), seat.asLong());
		}
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Long> e : seats.entrySet()) {
			if (out.length() > 0) out.append(';');
			out.append(e.getKey()).append('=').append(e.getValue());
		}
		entityData.set(SEATS, out.toString());
	}

	private Map<String, Long> seats() {
		String raw = entityData.get(SEATS);
		Map<String, Long> seats = new LinkedHashMap<>();
		if (raw.isEmpty()) return seats;
		for (String entry : raw.split(";")) {
			int eq = entry.indexOf('=');
			if (eq <= 0) continue;
			try {
				seats.put(entry.substring(0, eq), Long.parseLong(entry.substring(eq + 1)));
			} catch (NumberFormatException ignored) {

			}
		}
		return seats;
	}

	private @Nullable BlockPos freeSeat() {
		for (BlockPos seat : blocks.seats()) {
			if (!isSeatOccupied(seat)) return seat;
		}
		return null;
	}

	public boolean sit(Entity passenger, BlockPos helm) {
		if (level().isClientSide() || !blocks.seats().contains(helm) || isSeatOccupied(helm)) return false;
		setSeat(passenger, helm);
		if (passenger.startRiding(this)) return true;
		setSeat(passenger, null);
		return false;
	}

	public void sendBlocksTo(ServerPlayer player) {
		RaftNetworking.send(player, new RaftNetworking.BlocksPayload(getId(), blocks.write(raftLevel()::syncNbt)));
	}

	void syncAll() {
		if (level().isClientSide()) return;
		RaftNetworking.sendToTracking(this, new RaftNetworking.BlocksPayload(getId(), blocks.write(raftLevel()::syncNbt)));
	}

	void syncBlock(BlockPos pos) {
		if (level().isClientSide()) return;
		RaftNetworking.sendToTracking(this, new RaftNetworking.BlockPayload(getId(), pos, raftLevel().getBlockState(pos), raftLevel().syncNbt(pos)));
	}

	void syncBlockEvent(BlockPos pos, int type, int data) {
		RaftNetworking.sendToTracking(this, new RaftNetworking.BlockEventPayload(getId(), pos, type, data));
	}

	public void syncBreakProgress(BlockPos pos, int stage) {
		RaftNetworking.sendToTracking(this, new RaftNetworking.BreakProgressPayload(getId(), pos, stage));
	}

	public void applyBlocks(CompoundTag tag) {
		setBlocks(RaftBlocks.read(tag, level().holderLookup(Registries.BLOCK)));
	}

	public void applyBlock(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
		raftLevel().setBlock(pos, state, 19);
		if (nbt != null) raftLevel().applyBlockEntityNbt(pos, nbt);
	}

	public void setBreakStage(BlockPos pos, int stage) {
		if (stage < 0) {
			breakStages.remove(pos);
		} else {
			breakStages.put(pos, Math.min(stage, 9));
		}
	}

	public Map<BlockPos, Integer> breakStages() {
		return breakStages;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SEATS, "");
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {

		input.read("Blocks", CompoundTag.CODEC).ifPresent(tag ->
			setBlocks(RaftBlocks.read(tag, level().holderLookup(Registries.BLOCK))));
		worldLight.load(input);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("Blocks", CompoundTag.CODEC, blocks.write(raftLevel()::saveNbt));
		worldLight.save(output);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason != RemovalReason.UNLOADED_TO_CHUNK) worldLight.clear();
		super.remove(reason);
	}

	public java.util.Set<BlockPos> worldLights() {
		return worldLight.owned();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {

			setBoundingBox(makeBoundingBox());
		} else {
			if (blocks.isEmpty()) {
				discard();
				return;
			}
			tickServerMovement();
			RaftBeds.tick(this);

			worldLight.tick();
		}
		raftLevel().tickBlocks();

	}

	private void tickServerMovement() {
		float forward;
		float strafe;
		LivingEntity driver = getControllingPassenger();
		if (driver instanceof ServerPlayer player) {

			Input keys = player.getLastClientInput();
			forward = keys.forward() == keys.backward() ? 0.0F : (keys.forward() ? 1.0F : -1.0F);
			strafe = keys.left() == keys.right() ? 0.0F : (keys.left() ? 1.0F : -1.0F);
		} else if (driver != null) {
			forward = Mth.clamp(driver.zza, -1.0F, 1.0F);
			strafe = Mth.clamp(driver.xxa, -1.0F, 1.0F);
		} else {
			forward = debugForward;
			strafe = debugStrafe;
		}
		double size = sizeFactor();

		deltaRotation = (float) ((deltaRotation - strafe * TURN_ACCEL * size) * FRICTION);
		if (Math.abs(deltaRotation) < 0.01F) deltaRotation = 0;

		boolean wasBlocked = hullBlocked(getX(), getY(), getZ(), getYRot());
		if (deltaRotation != 0) {
			float newYaw = getYRot() + deltaRotation;

			Vec3 nudged = null;
			if (!wasBlocked && hullBlocked(getX(), getY(), getZ(), newYaw)) {
				for (double step : NUDGES) {
					for (int dir = 0; dir < 4 && nudged == null; dir++) {
						double nx = getX() + (dir == 0 ? step : dir == 1 ? -step : 0);
						double nz = getZ() + (dir == 2 ? step : dir == 3 ? -step : 0);
						if (!hullBlocked(nx, getY(), nz, newYaw)) nudged = new Vec3(nx, getY(), nz);
					}
					if (nudged != null) break;
				}
				if (nudged != null) setPos(nudged.x, nudged.y, nudged.z);
			}
			if (wasBlocked || nudged != null || !hullBlocked(getX(), getY(), getZ(), newYaw)) {
				setYRot(newYaw);
				setBoundingBox(makeBoundingBox());
			} else {
				deltaRotation = 0;
			}
		}

		double accel = forward * FORWARD_ACCEL * size;
		if (forward < 0) accel *= REVERSE_FACTOR;
		float yaw = yawRadians();
		Vec3 v = getDeltaMovement().add(-Mth.sin(yaw) * accel, 0, Mth.cos(yaw) * accel);
		double vy = 0;
		double surface = waterSurfaceY();
		if (!Double.isNaN(surface)) {
			vy = Mth.clamp((surface - DRAFT - getY()) * 0.5, -0.2, 0.2);
		} else {

			v = Vec3.ZERO;
		}
		v = new Vec3(v.x * FRICTION, vy, v.z * FRICTION);
		if (v.horizontalDistanceSqr() < 1.0e-6) v = new Vec3(0, v.y, 0);

		Vec3 next = position().add(v);
		if (!wasBlocked && v.horizontalDistanceSqr() > 0 && hullBlocked(next.x, next.y, next.z, getYRot())) {

			float yawDeg = getYRot();
			double free = 0, blocked = 1;
			for (int i = 0; i < 4; i++) {
				double mid = (free + blocked) * 0.5;
				Vec3 p = position().add(v.x * mid, v.y, v.z * mid);
				if (hullBlocked(p.x, p.y, p.z, yawDeg)) blocked = mid; else free = mid;
			}
			Vec3 at = position().add(v.x * free, v.y, v.z * free);
			double restX = v.x * (1 - free), restZ = v.z * (1 - free);
			double keptX = 0, keptZ = 0;
			if (Math.abs(restX) >= Math.abs(restZ)) {
				if (restX != 0 && !hullBlocked(at.x + restX, at.y, at.z, yawDeg)) keptX = restX;
				if (restZ != 0 && !hullBlocked(at.x + keptX, at.y, at.z + restZ, yawDeg)) keptZ = restZ;
			} else {
				if (restZ != 0 && !hullBlocked(at.x, at.y, at.z + restZ, yawDeg)) keptZ = restZ;
				if (restX != 0 && !hullBlocked(at.x + restX, at.y, at.z + keptZ, yawDeg)) keptX = restX;
			}
			next = new Vec3(at.x + keptX, at.y, at.z + keptZ);
			v = new Vec3(keptX != 0 ? v.x : 0, v.y, keptZ != 0 ? v.z : 0);
		}
		setDeltaMovement(v);
		setPos(next.x, next.y, next.z);
	}

	public boolean hullBlocked(double x, double y, double z, float yawDeg) {
		float yawRad = yawDeg * Mth.DEG_TO_RAD;
		Vec3 pivot = blocks.pivot();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (BlockPos p : blocks.shell()) {
			for (int i = 0; i < 10; i++) {
				double lx = p.getX() + SAMPLE_X[i % 5];
				double lz = p.getZ() + SAMPLE_Z[i % 5];
				double ly = p.getY() + (i < 5 ? 0.25 : 0.75);
				Vec3 rel = new Vec3(lx - pivot.x, ly, lz - pivot.z).yRot(-yawRad);
				cursor.set(Mth.floor(x + pivot.x + rel.x), Mth.floor(y + rel.y), Mth.floor(z + pivot.z + rel.z));
				BlockState state = level().getBlockState(cursor);
				if (state.isAir()) continue;
				if (!state.getCollisionShape(level(), cursor).isEmpty()) {
					if (debugHull) {
						com.oceania.Oceania.LOGGER.info("[hull] blocked at ({}, {}, {}) yaw {}: block {} at {} from local ({}, {}, {})", x, y, z, yawDeg, state, cursor, lx, ly, lz);
					}
					return true;
				}
			}
		}
		return false;
	}

	public static boolean debugHull;

	private double waterSurfaceY() {
		AABB b = blocks.localBounds();
		double cx = (b.minX + b.maxX) * 0.5, cz = (b.minZ + b.maxZ) * 0.5;
		double best = Double.NaN;
		for (int i = 0; i < 5; i++) {
			double lx = i == 0 ? cx : (i & 1) == 0 ? b.minX + 0.5 : b.maxX - 0.5;
			double lz = i == 0 ? cz : (i & 2) == 0 ? b.minZ + 0.5 : b.maxZ - 0.5;
			Vec3 column = toWorld(new Vec3(lx, 0, lz));
			double surface = waterSurfaceAt(Mth.floor(column.x), Mth.floor(column.z));
			if (!Double.isNaN(surface) && (Double.isNaN(best) || surface > best)) best = surface;
		}
		return best;
	}

	private double waterSurfaceAt(int bx, int bz) {
		int by = Mth.floor(getY() + DRAFT);
		for (int y = by + 1; y >= by - 1; y--) {
			BlockPos pos = new BlockPos(bx, y, bz);
			FluidState fluid = level().getFluidState(pos);
			if (fluid.is(FluidTags.WATER)) {
				return y + fluid.getHeight(level(), pos);
			}
		}
		return Double.NaN;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, LERP_STEPS);
	}

	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	@Override
	protected boolean isLocalClientAuthoritative() {
		return false;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (blocks.seats().isEmpty()) return null;
		List<Entity> passengers = getPassengers();
		return !passengers.isEmpty() && passengers.get(0) instanceof LivingEntity living ? living : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return seatOf(passenger) != null || freeSeat() != null;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (level().isClientSide()) return;
		BlockPos seat = seatOf(passenger);
		if (seat == null || !blocks.seats().contains(seat)) {
			BlockPos free = freeSeat();
			if (free != null) setSeat(passenger, free);
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (level().isClientSide()) return;
		dismounting = passenger;
		dismountingSeat = seatOf(passenger);
		setSeat(passenger, null);
	}

	private @Nullable Entity dismounting;
	private @Nullable BlockPos dismountingSeat;

	private Vec3 seatLocal(Entity passenger) {
		BlockPos seat = seatOf(passenger);
		if (seat == null) {

			List<BlockPos> seats = blocks.seats();
			if (seats.isEmpty()) return blocks.pivot().add(0, blocks.localBounds().maxY, 0);
			seat = seats.get(0);
		}
		return new Vec3(seat.getX() + 0.5, seat.getY() + RaftHelmBlock.HEIGHT, seat.getZ() + 0.5);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction callback) {
		Vec3 world = toWorld(seatLocal(passenger));
		Vec3 attach = passenger.getVehicleAttachmentPoint(this);
		callback.accept(passenger, world.x - attach.x, world.y - attach.y, world.z - attach.z);
		passenger.setYRot(passenger.getYRot() + deltaRotation);
		passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
		clampRotation(passenger);
	}

	@Override
	public boolean hasExactlyOnePlayerPassenger() {
		return false;
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		clampRotation(passenger);
	}

	private void clampRotation(Entity passenger) {
		passenger.setYBodyRot(getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot());
		float clamped = Mth.clamp(delta, -105.0F, 105.0F);
		passenger.yRotO += clamped - delta;
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		passenger.setYHeadRot(passenger.getYRot());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		BlockPos seat = seatOf(passenger);
		if (seat == null && passenger == dismounting) seat = dismountingSeat;
		dismounting = null;
		dismountingSeat = null;
		Vec3 local = seat != null
			? Vec3.atBottomCenterOf(seat).add(0, RaftHelmBlock.HEIGHT, 0)
			: blocks.pivot().add(0, blocks.localBounds().maxY, 0);
		return toWorld(local);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(houseboatItem());
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.NONE;
	}
}
