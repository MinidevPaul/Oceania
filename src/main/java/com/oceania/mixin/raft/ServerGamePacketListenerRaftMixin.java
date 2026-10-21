package com.oceania.mixin.raft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.oceania.Oceania;
import com.oceania.raft.RaftMoveTrace;
import com.oceania.raft.RaftRider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerRaftMixin {
	@Shadow
	public ServerPlayer player;

	@ModifyExpressionValue(

		method = "handlePlayerPositionChange",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;noBlocksAround(Lnet/minecraft/world/entity/Entity;)Z")
	)
	private boolean oceania$notFloatingOnRaft(boolean noBlocksAround) {
		return noBlocksAround && !((RaftRider) this.player).oceania$isOnRaft(this.player.level().getGameTime());
	}

	@Unique
	private long oceania$lastTraceLog = Long.MIN_VALUE / 2;

	@Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
	private void oceania$traceTeleport(PositionMoveRotation target, Set<Relative> relative, CallbackInfo ci) {
		if (!RaftMoveTrace.ENABLED || !RaftMoveTrace.watchesServerSide(this.player)) return;
		long now = this.player.level().getGameTime();
		if (now - oceania$lastTraceLog < 10) return;
		oceania$lastTraceLog = now;
		Oceania.LOGGER.warn("[movetrace:server] correction for {}: from {} to {} relative={} via {}", this.player.getName().getString(),
			RaftMoveTrace.fmt(this.player.position()), RaftMoveTrace.fmt(target.position()), relative, RaftMoveTrace.frames(7));
	}

	@Unique
	private static final double OCEANIA_FOOT_TOLERANCE = 0.1;

	@Unique
	private long oceania$lastFootLog = Long.MIN_VALUE / 2;

	@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("RETURN"), cancellable = true)
	private void oceania$forgiveTheFloor(LevelReader level, Entity entity, AABB oldBox, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		AABB newBox = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
		VoxelShape oldShape = Shapes.create(oldBox.deflate(1.0E-5F));
		StringBuilder shapes = new StringBuilder();
		boolean onlyFloor = true;
		for (VoxelShape shape : level.getPreMoveCollisions(entity, newBox.deflate(1.0E-5F), oldBox.getBottomCenter())) {
			if (Shapes.joinIsNotEmpty(shape, oldShape, BooleanOp.AND)) continue;
			AABB bounds = shape.bounds();
			boolean floor = bounds.maxY <= newBox.minY + OCEANIA_FOOT_TOLERANCE;
			if (!floor) onlyFloor = false;
			shapes.append(floor ? " floor " : " NEW ").append(RaftMoveTrace.fmt(bounds));
		}
		if (onlyFloor) cir.setReturnValue(false);
		if (RaftMoveTrace.ENABLED && RaftMoveTrace.watchesServerSide(this.player)) {
			long now = this.player.level().getGameTime();
			if (now - oceania$lastFootLog >= 20) {
				oceania$lastFootLog = now;
				Oceania.LOGGER.warn("[movetrace:server] move of {} met something new: old box {} new box {} shapes:{} -> {}",
					this.player.getName().getString(), RaftMoveTrace.fmt(oldBox), RaftMoveTrace.fmt(newBox), shapes, onlyFloor ? "forgiven as the floor" : "rejected");
			}
		}
	}
}
