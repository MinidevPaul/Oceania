package com.oceania.raft;

import com.oceania.raft.collision.CollisionList;
import com.oceania.raft.collision.ContinuousOBBCollider;
import com.oceania.raft.collision.Matrix3d;
import com.oceania.raft.collision.OrientedBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class RaftCollider {
	private static final double HORIZONTAL_EPSILON = 1 / 128.0;

	public static boolean debug = false;

	private enum PlayerType { NONE, CLIENT, REMOTE, SERVER }

	public record Contact(RaftEntity raft, Vec3 local) {
	}

	public static @Nullable Contact clientContact;

	private static final Map<Level, Set<Entity>> SUPPORTED = new WeakHashMap<>();
	private static final Map<Level, Set<Entity>> NOW = new WeakHashMap<>();

	private RaftCollider() {
	}

	public static void beginClientTick(Level level) {
		clientContact = null;
		beginTick(level);
	}

	public static void beginServerTick(Level level) {
		beginTick(level);
	}

	private static void beginTick(Level level) {
		Set<Entity> now = NOW.remove(level);
		SUPPORTED.put(level, now == null ? Set.of() : now);
	}

	public static void collideEntities(RaftEntity raft) {
		RaftBlocks blocks = raft.blocks();
		if (blocks.isEmpty()) return;
		List<AABB> colliders = blocks.colliders();
		if (colliders.isEmpty()) return;
		Level level = raft.level();
		AABB bounds = raft.getBoundingBox();
		List<Entity> entities = level.getEntitiesOfClass(Entity.class, bounds.inflate(1.0).expandTowards(0, 3, 0), e -> shouldCollide(raft, e));
		if (entities.isEmpty()) return;

		Vec3 raftPos = raft.position();
		Vec3 raftPrevPos = new Vec3(raft.xo, raft.yo, raft.zo);
		Vec3 raftMotion = raftPos.subtract(raftPrevPos);
		float yawRad = raft.yawRadians();
		float prevYawRad = raft.yRotO * ((float) Math.PI / 180F);
		float yawDelta = raft.getYRot() - raft.yRotO;
		Matrix3d worldToLocal = new Matrix3d().asYRotation(yawRad);
		Vec3 pivot = blocks.pivot();
		Vec3 pivotWorld = new Vec3(raftPos.x + pivot.x, raftPos.y, raftPos.z + pivot.z);

		CollisionList collidable = new CollisionList();
		CollisionList dense = new CollisionList();
		CollisionList.Populate populate = new CollisionList.Populate(collidable);

		for (Entity entity : entities) {
			PlayerType type = playerType(level, entity);
			if (type == PlayerType.SERVER) {
				markServerPlayer(raft, (Player) entity);
				continue;
			}
			if (type == PlayerType.REMOTE) continue;

			Vec3 entityPos = entity.position();
			Vec3 entityMotion = entity.getDeltaMovement();
			AABB entityBounds = entity.getBoundingBox();
			if (type == PlayerType.CLIENT && entityBounds.getYsize() > 1) {
				entityBounds = entityBounds.contract(0, 2 / 16.0, 0);
			}

			Vec3 worldCenter = entityBounds.getCenter();
			Vec3 localCenter = worldToLocal.transform(worldCenter.subtract(pivotWorld)).add(pivot.x, 0, pivot.z);
			Vec3 localMotion = worldToLocal.transform(entityMotion.subtract(raftMotion));
			OrientedBB obb = new OrientedBB(entityBounds.move(localCenter.subtract(worldCenter)).inflate(1.0E-7));
			obb.setRotation(worldToLocal);

			boolean supported = SUPPORTED.getOrDefault(level, Set.of()).contains(entity);
			boolean grounded = supported || entity.onGround();
			float stepHeight = grounded && entityMotion.y <= 0 ? entity.maxUpStep() : 0;
			AABB search = obb.getAsAABB().expandTowards(localMotion).inflate(0.5 + entity.maxUpStep());
			collidable.size = 0;
			dense.size = 0;
			for (AABB box : colliders) {
				if (box.intersects(search)) {
					populate.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
				}
			}
			if (collidable.size == 0) continue;

			ContinuousOBBCollider.CollisionResponse response = ContinuousOBBCollider.collideMany(collidable, dense, obb, localMotion, stepHeight, true, supported);
			if (debug && type == PlayerType.NONE) {
				com.oceania.Oceania.LOGGER.info("[collider] {} localCenter={} localMotion={} boxes={} -> resp={} surface={} temporal={} normal={}",
					entity.getType().toShortString(), localCenter, localMotion, collidable.size, response.collisionResponse, response.surfaceCollision, response.temporalResponse, response.normal);
			}

			Vec3 motionResponse = response.temporalResponse == 1 ? localMotion
				: localMotion.normalize().scale(localMotion.length() * response.temporalResponse);
			motionResponse = worldToLocal.transformTransposed(motionResponse).add(raftMotion);
			Vec3 totalResponse = worldToLocal.transformTransposed(response.collisionResponse);
			boolean hardCollision = !totalResponse.equals(Vec3.ZERO);
			boolean temporalCollision = response.temporalResponse != 1;

			Vec3 ahead = response.temporalNormal;
			boolean temporalVertical = temporalCollision && Math.abs(ahead.y) > Math.max(Math.abs(ahead.x), Math.abs(ahead.z));

			boolean surfaceCollision = response.surfaceCollision
				&& (response.collisionResponse.y > 0 || (temporalVertical && ahead.y > 0));
			if (!hardCollision && !surfaceCollision) continue;

			if (temporalVertical) {
				entityMotion = new Vec3(entityMotion.x, motionResponse.y, entityMotion.z);
			}
			if (hardCollision) {
				double mx = entityMotion.x, my = entityMotion.y, mz = entityMotion.z;
				double ix = totalResponse.x, iy = totalResponse.y, iz = totalResponse.z;
				if (mx != 0 && Math.abs(ix) > HORIZONTAL_EPSILON && (mx > 0) == (ix < 0)) entityMotion = entityMotion.multiply(0, 1, 1);
				if (my != 0 && iy != 0 && (my > 0) == (iy < 0)) entityMotion = new Vec3(entityMotion.x, raftMotion.y, entityMotion.z);
				if (mz != 0 && Math.abs(iz) > HORIZONTAL_EPSILON && (mz > 0) == (iz < 0)) entityMotion = entityMotion.multiply(1, 1, 0);

				boolean sideways = Math.abs(ix) > HORIZONTAL_EPSILON || Math.abs(iz) > HORIZONTAL_EPSILON;
				if (sideways && !supported && type == PlayerType.CLIENT && entity.isInWater()
					&& !intersectsAny(colliders, obb.getAsAABB().move(0, 0.6, 0).deflate(0.01, 0, 0.01))) {
					entityMotion = new Vec3(entityMotion.x, Math.max(entityMotion.y, 0.3), entityMotion.z);
				}
			}

			Vec3 allowed = collideWithWorld(totalResponse, entity);
			entity.setPos(entityPos.x + allowed.x, entityPos.y + allowed.y, entityPos.z + allowed.z);
			entityPos = entity.position();
			entity.syncVelocity = true;

			if (entity instanceof ItemEntity && raftMotion.lengthSqr() > 0) entity.needsSync = true;

			if (surfaceCollision) {
				NOW.computeIfAbsent(level, l -> new HashSet<>()).add(entity);
				entity.fallDistance = 0;
				entity.setOnGround(true);
				if (entity instanceof ItemEntity) {
					entityMotion = entityMotion.multiply(0.5, 1, 0.5);
				}
				Vec3 contact = contactPointMotion(raft, entityPos, raftPrevPos, prevYawRad, pivot);
				allowed = collideWithWorld(contact, entity);
				entity.setPos(entityPos.x + allowed.x, entityPos.y, entityPos.z + allowed.z);
				if (yawDelta != 0) {
					dragYaw(entity, yawDelta);
				}
				if (type == PlayerType.CLIENT) {
					clientContact = new Contact(raft, raft.toLocal(entity.position()));
				}
			}
			entity.setDeltaMovement(entityMotion);
			if (debug && type == PlayerType.NONE) {
				com.oceania.Oceania.LOGGER.info("[collider]   -> pos={} motion={} onGround={}", entity.position(), entityMotion, entity.onGround());
			}
		}
	}

	private static void markServerPlayer(RaftEntity raft, Player player) {
		Vec3 local;
		long now = player.level().getGameTime();
		if (player instanceof RaftRider rider && rider.oceania$walkerRaft() == raft.getId() && now - rider.oceania$walkerTime() <= RaftTicker.WALKER_FRESH_TICKS) {
			local = rider.oceania$walkerLocal();
		} else {
			local = raft.toLocal(player.position());
		}
		AABB b = raft.blocks().localBounds();
		boolean insideFootprint = local.x >= b.minX - 0.5 && local.x <= b.maxX + 0.5 && local.z >= b.minZ - 0.5 && local.z <= b.maxZ + 0.5;
		boolean nearDeck = local.y >= b.minY - 0.5 && local.y <= b.maxY + 2.0;
		if (insideFootprint && nearDeck && player instanceof RaftRider rider) {
			rider.oceania$markOnRaft(now);

			player.setOnGround(true);
			player.resetFallDistance();

			Vec3 velocity = player.getDeltaMovement();
			if (velocity.y < 0) player.setDeltaMovement(velocity.x, 0, velocity.z);
			if (!player.isPassenger()) {
				player.xxa = 0;
				player.zza = 0;
			}
		}
	}

	private static boolean intersectsAny(List<AABB> boxes, AABB box) {
		for (AABB other : boxes) {
			if (other.intersects(box)) return true;
		}
		return false;
	}

	public static Vec3 backOffFromDeckEdge(Player player, Vec3 motion, MoverType type) {
		Contact contact = clientContact;
		if (contact == null || !player.level().isClientSide() || player.getAbilities().flying || motion.y > 0) return motion;
		if ((type != MoverType.SELF && type != MoverType.PLAYER) || !player.isShiftKeyDown()) return motion;
		RaftEntity raft = contact.raft();
		List<AABB> colliders = raft.blocks().colliders();
		AABB box = player.getBoundingBox();
		double step = player.maxUpStep();
		double d = motion.x;
		double e = motion.z;
		while (d != 0 && !hasFooting(player, raft, colliders, box.move(d, -step, 0))) d = shrink(d);
		while (e != 0 && !hasFooting(player, raft, colliders, box.move(0, -step, e))) e = shrink(e);
		while (d != 0 && e != 0 && !hasFooting(player, raft, colliders, box.move(d, -step, e))) {
			d = shrink(d);
			e = shrink(e);
		}
		return new Vec3(d, motion.y, e);
	}

	private static double shrink(double v) {
		if (v < 0.05 && v >= -0.05) return 0;
		return v > 0 ? v - 0.05 : v + 0.05;
	}

	private static boolean hasFooting(Player player, RaftEntity raft, List<AABB> colliders, AABB worldBox) {
		if (!player.level().noCollision(player, worldBox)) return true;
		return intersectsAny(colliders, localBounds(raft, worldBox));
	}

	private static AABB localBounds(RaftEntity raft, AABB world) {
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		double y = 0;
		for (int i = 0; i < 4; i++) {
			Vec3 local = raft.toLocal(new Vec3((i & 1) == 0 ? world.minX : world.maxX, world.minY, (i & 2) == 0 ? world.minZ : world.maxZ));
			minX = Math.min(minX, local.x); maxX = Math.max(maxX, local.x);
			minZ = Math.min(minZ, local.z); maxZ = Math.max(maxZ, local.z);
			y = local.y;
		}
		return new AABB(minX, y, minZ, maxX, y + (world.maxY - world.minY), maxZ);
	}

	public static boolean standsOnDeck(Player player) {
		if (player.level().isClientSide()) return player.isLocalPlayer() && clientContact != null;
		return player instanceof RaftRider rider && rider.oceania$isOnRaft(player.level().getGameTime());
	}

	private static boolean shouldCollide(RaftEntity raft, Entity entity) {
		if (entity instanceof RaftEntity || entity.isPassenger() || entity.noPhysics || !entity.isAlive()) return false;
		if (entity instanceof Player player) return !player.isSpectator();
		return entity instanceof ItemEntity;
	}

	private static PlayerType playerType(Level level, Entity entity) {
		if (!(entity instanceof Player player)) return PlayerType.NONE;
		if (!level.isClientSide()) return PlayerType.SERVER;
		return player.isLocalPlayer() ? PlayerType.CLIENT : PlayerType.REMOTE;
	}

	private static Vec3 contactPointMotion(RaftEntity raft, Vec3 worldPoint, Vec3 prevPos, float prevYawRad, Vec3 pivot) {
		Vec3 rel = new Vec3(worldPoint.x - prevPos.x - pivot.x, worldPoint.y - prevPos.y, worldPoint.z - prevPos.z - pivot.z).yRot(prevYawRad);
		Vec3 local = new Vec3(rel.x + pivot.x, rel.y, rel.z + pivot.z);
		return raft.toWorld(local).subtract(worldPoint);
	}

	private static void dragYaw(Entity entity, float yawDelta) {
		entity.setYRot(entity.getYRot() + yawDelta);
		entity.yRotO += yawDelta;
		if (entity instanceof LivingEntity living) {
			living.setYHeadRot(living.getYHeadRot() + yawDelta);
			living.yHeadRotO += yawDelta;
			living.setYBodyRot(living.yBodyRot + yawDelta);
			living.yBodyRotO += yawDelta;
		}
	}

	private static Vec3 collideWithWorld(Vec3 motion, Entity e) {
		if (motion.lengthSqr() == 0) return motion;
		AABB box = e.getBoundingBox();
		List<VoxelShape> list = e.level().getEntityCollisions(e, box.expandTowards(motion));
		Vec3 moved = Entity.collideBoundingBox(e, motion, box, e.level(), list);
		boolean hitX = motion.x != moved.x;
		boolean hitY = motion.y != moved.y;
		boolean hitZ = motion.z != moved.z;
		boolean grounded = e.onGround() || hitY && motion.y < 0;
		float step = e.maxUpStep();
		if (step > 0 && grounded && (hitX || hitZ)) {
			Vec3 stepped = Entity.collideBoundingBox(e, new Vec3(motion.x, step, motion.z), box, e.level(), list);
			Vec3 up = Entity.collideBoundingBox(e, new Vec3(0, step, 0), box.expandTowards(motion.x, 0, motion.z), e.level(), list);
			if (up.y < step) {
				Vec3 across = Entity.collideBoundingBox(e, new Vec3(motion.x, 0, motion.z), box.move(up), e.level(), list).add(up);
				if (across.horizontalDistanceSqr() > stepped.horizontalDistanceSqr()) stepped = across;
			}
			if (stepped.horizontalDistanceSqr() > moved.horizontalDistanceSqr()) {
				return stepped.add(Entity.collideBoundingBox(e, new Vec3(0, -stepped.y + motion.y, 0), box.move(stepped), e.level(), list));
			}
		}
		return moved;
	}
}
