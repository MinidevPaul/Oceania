package com.oceania.raft;

import com.oceania.Oceania;
import com.oceania.mixin.raft.PlayerSleepAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RaftBeds {
	private RaftBeds() {
	}

	public record RaftBed(UUID raft, BlockPos local, BlockPos lastWorld) {

		public static final Codec<RaftBed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("Raft").forGetter(RaftBed::raft),
			BlockPos.CODEC.fieldOf("Local").forGetter(RaftBed::local),
			BlockPos.CODEC.fieldOf("World").forGetter(RaftBed::lastWorld)
		).apply(instance, RaftBed::new));

		boolean sameBed(@Nullable RaftBed other) {
			return other != null && other.raft.equals(raft) && other.local.equals(local);
		}
	}

	private static final class Pending {
		final RaftBed bed;

		final Vec3 fallback;
		final float fallbackYaw;
		int ticks;

		Pending(RaftBed bed, Vec3 fallback, float fallbackYaw) {
			this.bed = bed;
			this.fallback = fallback;
			this.fallbackYaw = fallbackYaw;
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private static final int PENDING_TIMEOUT = 200;

	public static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register(RaftBeds::afterRespawn);
		ServerTickEvents.END_SERVER_TICK.register(RaftBeds::tick);
	}

	public static boolean hasBed(ServerPlayer player) {
		return ((RaftRider) player).oceania$bed() != null;
	}

	public static boolean isSleepingOnRaft(ServerPlayer player) {
		return ((RaftRider) player).oceania$sleepingIn() != null;
	}

	static void use(ServerPlayer player, RaftEntity raft, BlockPos pos, BlockState state) {
		RaftLevel level = raft.raftLevel();
		if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
			pos = pos.relative(state.getValue(BedBlock.FACING));
			state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof BedBlock)) return;
		}
		if (state.getValue(BedBlock.OCCUPIED)) {
			player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
			return;
		}
		if (player.isSleeping() || !player.isAlive()) return;
		Vec3 bedBottom = raft.toWorld(Vec3.atBottomCenterOf(pos));
		if (Math.abs(player.getX() - bedBottom.x) > 3.0 || Math.abs(player.getY() - bedBottom.y) > 2.0 || Math.abs(player.getZ() - bedBottom.z) > 3.0) {
			player.sendOverlayMessage(Player.BedSleepingProblem.TOO_FAR_AWAY.message());
			return;
		}

		RaftRider rider = (RaftRider) player;
		RaftBed bed = new RaftBed(raft.getUUID(), pos, BlockPos.containing(bedBottom));
		boolean same = bed.sameBed(rider.oceania$bed());
		rider.oceania$setBed(bed);
		player.setRespawnPosition(null, false);
		if (!same) player.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));

		BedRule rule = ((AbstractBedBlock) state.getBlock()).getBedRule(player.level(), bed.lastWorld());
		if (!rule.canSleep(player.level())) {
			player.sendOverlayMessage(rule.asProblem().message());
			return;
		}
		if (!player.isCreative()) {
			AABB around = new AABB(bedBottom.x - 8.0, bedBottom.y - 5.0, bedBottom.z - 8.0, bedBottom.x + 8.0, bedBottom.y + 5.0, bedBottom.z + 8.0);
			if (!player.level().getEntitiesOfClass(Monster.class, around, monster -> monster.isPreventingPlayerRest(player.level(), player)).isEmpty()) {
				player.sendOverlayMessage(Player.BedSleepingProblem.NOT_SAFE.message());
				return;
			}
		}

		level.setBlock(pos, state.setValue(BedBlock.OCCUPIED, true), 3);
		if (player.isPassenger()) player.stopRiding();
		player.setPose(Pose.SLEEPING);
		player.setSleepingPos(bed.lastWorld());
		hold(player, raft, pos, state);
		player.setDeltaMovement(Vec3.ZERO);
		player.needsSync = true;
		((PlayerSleepAccessor) player).oceania$setSleepCounter(0);
		rider.oceania$setSleepingIn(bed);
		player.awardStat(Stats.SLEEP_IN_BED);
		CriteriaTriggers.SLEPT_IN_BED.trigger(player);
		if (!player.level().canSleepThroughNights()) {
			player.sendOverlayMessage(Component.translatable("sleep.not_possible"));
		}
		player.level().updateSleepingPlayerList();
	}

	private static void hold(ServerPlayer player, RaftEntity raft, BlockPos pos, BlockState state) {
		Vec3 centre = raft.toWorld(new Vec3(pos.getX() + 0.5, pos.getY() + 0.6875, pos.getZ() + 0.5));
		float yaw = 90.0F - state.getValue(BedBlock.FACING).getOpposite().toYRot() - raft.getYRot();
		if (player.position().distanceToSqr(centre) > 1.0e-6 && player.connection != null) {
			player.connection.teleport(centre.x, centre.y, centre.z, yaw, 0.0F);
		} else {
			player.setPos(centre.x, centre.y, centre.z);
			player.setYRot(yaw);
			player.setXRot(0.0F);
		}
		player.setYBodyRot(yaw);
		player.setYHeadRot(yaw);
	}

	static void tick(RaftEntity raft) {
		for (Player p : raft.level().players()) {
			if (!(p instanceof ServerPlayer player) || !(p instanceof RaftRider rider)) continue;
			RaftBed bed = rider.oceania$bed();
			if (bed != null && bed.raft.equals(raft.getUUID())) {
				BlockPos world = BlockPos.containing(raft.toWorld(Vec3.atBottomCenterOf(bed.local)));
				if (!world.equals(bed.lastWorld)) rider.oceania$setBed(new RaftBed(bed.raft, bed.local, world));
			}
			RaftBed sleeping = rider.oceania$sleepingIn();
			if (sleeping == null || !sleeping.raft.equals(raft.getUUID())) continue;
			if (!player.isSleeping()) {
				onStopSleeping(player);
				continue;
			}
			BlockState state = raft.raftLevel().getBlockState(sleeping.local);
			if (!(state.getBlock() instanceof BedBlock)) {
				player.stopSleepInBed(true, true);
				continue;
			}
			hold(player, raft, sleeping.local, state);
		}
	}

	public static void onRaftRemoved(RaftEntity raft) {
		for (Player p : raft.level().players()) {
			if (!(p instanceof ServerPlayer player) || !(p instanceof RaftRider rider)) continue;
			RaftBed sleeping = rider.oceania$sleepingIn();
			if (sleeping != null && sleeping.raft.equals(raft.getUUID())) {
				rider.oceania$setSleepingIn(null);
				if (player.isSleeping()) player.stopSleepInBed(true, true);
			}
			RaftBed bed = rider.oceania$bed();
			if (bed != null && bed.raft.equals(raft.getUUID())) rider.oceania$setBed(null);
		}
	}

	public static void onStopSleeping(ServerPlayer player) {
		RaftRider rider = (RaftRider) player;
		RaftBed bed = rider.oceania$sleepingIn();
		if (bed == null) return;
		rider.oceania$setSleepingIn(null);
		if (player.level().getEntity(bed.raft) instanceof RaftEntity raft) {
			BlockState state = raft.raftLevel().getBlockState(bed.local);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED)) {
				raft.raftLevel().setBlock(bed.local, state.setValue(BedBlock.OCCUPIED, false), 3);
			}
		}
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive) return;
		RaftBed bed = ((RaftRider) newPlayer).oceania$bed();
		if (bed == null) return;

		ServerLevel level = newPlayer.level().getServer().overworld();
		Pending pending = new Pending(bed, newPlayer.position(), newPlayer.getYRot());
		newPlayer.teleportTo(level, bed.lastWorld.getX() + 0.5, bed.lastWorld.getY() + 1.0, bed.lastWorld.getZ() + 0.5, Set.of(), newPlayer.getYRot(), 0.0F, false);
		PENDING.put(newPlayer.getUUID(), pending);
	}

	private static java.util.Optional<Vec3> standUpSpot(RaftEntity raft, RaftBed bed, float worldYaw) {
		BlockState state = raft.raftLevel().getBlockState(bed.local);
		if (!(state.getBlock() instanceof BedBlock)) return java.util.Optional.empty();
		return BedBlock.findStandUpPosition(net.minecraft.world.entity.EntityTypes.PLAYER, raft.raftLevel(), bed.local, state.getValue(BedBlock.FACING), worldYaw - raft.getYRot());
	}

	private static void failRespawn(ServerPlayer player, Pending pending) {
		((RaftRider) player).oceania$setBed(null);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
		player.teleportTo(player.level().getServer().overworld(), pending.fallback.x, pending.fallback.y, pending.fallback.z, Set.of(), pending.fallbackYaw, 0.0F, false);
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Pending pending = entry.getValue();
			RaftBed bed = pending.bed;
			ServerLevel level = player.level();
			Entity entity = level.getEntity(bed.raft);
			if (entity instanceof RaftEntity raft && !raft.isRemoved()) {
				java.util.Optional<Vec3> spot = standUpSpot(raft, bed, player.getYRot());
				if (spot.isEmpty()) {
					Oceania.LOGGER.info("[raft] {}'s raft bed {} is gone or boxed in; world spawn instead", player.getGameProfile().name(), bed.local);
					failRespawn(player, pending);
				} else {
					Vec3 world = raft.toWorld(spot.get());
					Vec3 bedCentre = raft.toWorld(Vec3.atCenterOf(bed.local));

					float yaw = (float) (net.minecraft.util.Mth.atan2(bedCentre.z - world.z, bedCentre.x - world.x) * (180.0 / Math.PI)) - 90.0F;
					player.teleportTo(level, world.x, world.y, world.z, Set.of(), yaw, 0.0F, false);
					Oceania.LOGGER.info("[raft] {} respawned on raft {}", player.getGameProfile().name(), bed.raft);
				}
				it.remove();
				continue;
			}
			ChunkPos chunk = ChunkPos.containing(bed.lastWorld);
			if (pending.ticks == 0) level.getChunk(chunk.x(), chunk.z());
			pending.ticks++;
			if (level.areEntitiesLoaded(chunk.pack()) && pending.ticks > 20 || pending.ticks > PENDING_TIMEOUT) {

				Oceania.LOGGER.info("[raft] {}'s raft {} is gone; world spawn instead", player.getGameProfile().name(), bed.raft);
				failRespawn(player, pending);
				it.remove();
			}
		}
	}
}
