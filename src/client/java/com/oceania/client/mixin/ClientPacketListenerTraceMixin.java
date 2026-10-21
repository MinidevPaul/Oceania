package com.oceania.client.mixin;

import com.oceania.raft.RaftMoveTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerTraceMixin {
	@Unique
	private double oceania$x, oceania$y, oceania$z;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void oceania$beforePosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			oceania$x = player.getX();
			oceania$y = player.getY();
			oceania$z = player.getZ();
		}
	}

	@Inject(method = "handleMovePlayer", at = @At("RETURN"))
	private void oceania$afterPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		if (!RaftMoveTrace.armed) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		RaftMoveTrace.record(String.format("SERVER POSITION PACKET id=%d relative=%s target=%s: player moved from (%.3f, %.3f, %.3f) to %s",
			packet.id(), packet.relatives(), RaftMoveTrace.fmt(packet.change().position()), oceania$x, oceania$y, oceania$z, RaftMoveTrace.fmt(player.position())));
		RaftMoveTrace.dump("server position packet while on or beside a raft");
	}

	@Inject(method = "handleSetEntityMotion", at = @At("RETURN"))
	private void oceania$afterMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (!RaftMoveTrace.armed) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || packet.id() != player.getId()) return;
		RaftMoveTrace.record("server velocity packet for the player: now " + RaftMoveTrace.fmt(player.getDeltaMovement()));
	}

	@Inject(method = "handleSetEntityPassengersPacket", at = @At("RETURN"))
	private void oceania$afterPassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
		if (!RaftMoveTrace.armed) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		boolean mine = packet.getVehicle() == player.getId() || Arrays.stream(packet.getPassengers()).anyMatch(id -> id == player.getId());
		Entity vehicle = mc.level.getEntity(packet.getVehicle());
		if (mine || vehicle instanceof com.oceania.raft.RaftEntity) {
			RaftMoveTrace.record("passengers packet: vehicle #" + packet.getVehicle() + " (" + (vehicle == null ? "?" : vehicle.getType().toShortString()) + ") passengers=" + Arrays.toString(packet.getPassengers()));
		}
	}
}
