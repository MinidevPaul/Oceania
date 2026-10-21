package com.oceania.raft;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RaftNetworking {
	private RaftNetworking() {
	}

	public record UsePayload(int raftId, BlockPos pos, Direction face, Vec3 hit) implements CustomPacketPayload {
		public static final Type<UsePayload> TYPE = new Type<>(Oceania.id("raft_use"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UsePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeBlockPos(p.pos);
				buf.writeEnum(p.face);
				writeVec3(buf, p.hit);
			},
			buf -> new UsePayload(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class), readVec3(buf)));

		@Override
		public Type<UsePayload> type() {
			return TYPE;
		}
	}

	public record AttackPayload(int raftId, BlockPos pos, Direction face, boolean start) implements CustomPacketPayload {
		public static final Type<AttackPayload> TYPE = new Type<>(Oceania.id("raft_attack"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AttackPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeBlockPos(p.pos);
				buf.writeEnum(p.face);
				buf.writeBoolean(p.start);
			},
			buf -> new AttackPayload(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean()));

		@Override
		public Type<AttackPayload> type() {
			return TYPE;
		}
	}

	public record WalkerPayload(int raftId, Vec3 local) implements CustomPacketPayload {
		public static final Type<WalkerPayload> TYPE = new Type<>(Oceania.id("raft_walker"));
		public static final StreamCodec<RegistryFriendlyByteBuf, WalkerPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				writeVec3(buf, p.local);
			},
			buf -> new WalkerPayload(buf.readVarInt(), readVec3(buf)));

		@Override
		public Type<WalkerPayload> type() {
			return TYPE;
		}
	}

	public record BlocksPayload(int raftId, CompoundTag blocks) implements CustomPacketPayload {
		public static final Type<BlocksPayload> TYPE = new Type<>(Oceania.id("raft_blocks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BlocksPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeNbt(p.blocks);
			},
			buf -> new BlocksPayload(buf.readVarInt(), buf.readNbt()));

		@Override
		public Type<BlocksPayload> type() {
			return TYPE;
		}
	}

	public record BlockPayload(int raftId, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) implements CustomPacketPayload {
		public static final Type<BlockPayload> TYPE = new Type<>(Oceania.id("raft_block"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BlockPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(Block.getId(p.state));
				buf.writeNullable(p.nbt, (b, tag) -> b.writeNbt(tag));
			},
			buf -> new BlockPayload(buf.readVarInt(), buf.readBlockPos(), Block.stateById(buf.readVarInt()), buf.readNullable(b -> (CompoundTag) b.readNbt())));

		@Override
		public Type<BlockPayload> type() {
			return TYPE;
		}
	}

	public record BlockEventPayload(int raftId, BlockPos pos, int eventType, int data) implements CustomPacketPayload {
		public static final Type<BlockEventPayload> TYPE = new Type<>(Oceania.id("raft_block_event"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BlockEventPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.eventType);
				buf.writeVarInt(p.data);
			},
			buf -> new BlockEventPayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<BlockEventPayload> type() {
			return TYPE;
		}
	}

	public record BreakProgressPayload(int raftId, BlockPos pos, int stage) implements CustomPacketPayload {
		public static final Type<BreakProgressPayload> TYPE = new Type<>(Oceania.id("raft_break_progress"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BreakProgressPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.stage);
			},
			buf -> new BreakProgressPayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt()));

		@Override
		public Type<BreakProgressPayload> type() {
			return TYPE;
		}
	}

	public record RidersPayload(int raftId, int[] playerIds, double[] locals) implements CustomPacketPayload {
		public static final Type<RidersPayload> TYPE = new Type<>(Oceania.id("raft_riders"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RidersPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.raftId);
				buf.writeVarIntArray(p.playerIds);
				for (double d : p.locals) buf.writeDouble(d);
			},
			buf -> {
				int raftId = buf.readVarInt();
				int[] ids = buf.readVarIntArray();
				double[] locals = new double[ids.length * 3];
				for (int i = 0; i < locals.length; i++) locals[i] = buf.readDouble();
				return new RidersPayload(raftId, ids, locals);
			});

		@Override
		public Type<RidersPayload> type() {
			return TYPE;
		}
	}

	static void writeVec3(RegistryFriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	static Vec3 readVec3(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(UsePayload.TYPE, UsePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AttackPayload.TYPE, AttackPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WalkerPayload.TYPE, WalkerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BlocksPayload.TYPE, BlocksPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BlockPayload.TYPE, BlockPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BlockEventPayload.TYPE, BlockEventPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BreakProgressPayload.TYPE, BreakProgressPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RidersPayload.TYPE, RidersPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UsePayload.TYPE, (payload, context) -> RaftInteraction.handleUse(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(AttackPayload.TYPE, (payload, context) -> RaftInteraction.handleAttack(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WalkerPayload.TYPE, (payload, context) -> RaftInteraction.handleWalker(context.player(), payload));
	}

	public static void sendToTracking(Entity entity, CustomPacketPayload payload) {
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
