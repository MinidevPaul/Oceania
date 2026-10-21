package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import com.oceania.Oceania;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftItem;
import com.oceania.raft.RaftLayouts;
import com.oceania.raft.RaftLevel;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RaftWallCommand {
	private RaftWallCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var raftwall = Commands.literal("raftwall")
				.executes(ctx -> run(ctx.getSource(), 2, null))
				.then(Commands.argument("height", IntegerArgumentType.integer(1, 3))
					.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "height"), null)))

				.then(Commands.literal("use")
					.then(Commands.argument("block", StringArgumentType.word())
						.executes(ctx -> run(ctx.getSource(), 0, StringArgumentType.getString(ctx, "block")))));
			var raftmoor = Commands.literal("raftmoor")
				.executes(ctx -> moor(ctx.getSource(), false))
				.then(Commands.literal("open").executes(ctx -> moor(ctx.getSource(), false)))
				.then(Commands.literal("wall").executes(ctx -> moor(ctx.getSource(), true)))

				.then(Commands.literal("ride").executes(ctx -> moorAndRide(ctx.getSource())));
			dispatcher.register(Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(raftwall)
				.then(raftmoor));
		});
	}

	private static int run(CommandSourceStack source, int height, String useBlock) {
		ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p
			: source.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
		if (player == null) {
			source.sendFailure(Component.literal("no player online"));
			return 0;
		}
		ServerLevel level = player.level();

		if (player.isPassenger()) player.stopRiding();
		BlockPos water = deepWater(level, player.blockPosition());
		if (water == null) {
			source.sendFailure(Component.literal("no open water three deep within 384 blocks"));
			return 0;
		}
		double surface = RaftSmoke.waterSurface(level, water);
		Vec3 at = new Vec3(water.getX() + 0.5, surface - RaftEntity.DRAFT, water.getZ() + 0.5);
		for (RaftEntity old : level.getEntitiesOfClass(RaftEntity.class, new AABB(at, at).inflate(16.0))) {
			old.discard();
		}
		RaftEntity raft = RaftItem.spawn(level, at, 0, RaftLayouts.starter());
		RaftLevel deck = raft.raftLevel();
		for (int y = 1; y <= height; y++) {
			for (int z = 0; z <= 2; z++) {
				deck.setBlock(new BlockPos(2, y, z), Blocks.JUNGLE_PLANKS.defaultBlockState(), 3);
			}
		}

		deck.setBlock(new BlockPos(0, 1, 2), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
		deck.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST.defaultBlockState(), 3);
		if (useBlock != null) {
			Identifier id = Identifier.tryParse(useBlock.contains(":") ? useBlock : "minecraft:" + useBlock);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) {
				source.sendFailure(Component.literal("no such block " + useBlock));
				return 0;
			}
			if (!deck.setBlock(new BlockPos(1, 1, 0), block.defaultBlockState(), 3)) {
				source.sendFailure(Component.literal("the raft refused " + useBlock));
				return 0;
			}
		}
		Vec3 stand = raft.toWorld(new Vec3(0.5, 1.0, 0.5));
		Vec3 ahead = raft.rotateToWorld(new Vec3(1.0, 0.0, 0.0));
		float yaw = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
		player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), yaw, 0.0F, false);
		String where = String.format(Locale.ROOT,
			"raft %d at %s heading %.1f, deck top y=%.2f, wall %d high at local x=2 (world corner %s), player at local (0.5, 1, 0.5) facing yaw %.1f",
			raft.getId(), raft.position(), raft.getYRot(), at.y + 1.0, height, raft.toWorld(new Vec3(2.0, 1.0, 0.0)), yaw);
		Oceania.LOGGER.info("[raftwall] {}", where);
		source.sendSuccess(() -> Component.literal(where), false);
		return 1;
	}

	private static BlockPos deepWater(ServerLevel level, BlockPos around) {
		for (int r = 0; r <= 48; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x = around.getX() + dx * 8;
					int z = around.getZ() + dz * 8;
					boolean clear = true;
					for (int ox = -3; ox <= 3 && clear; ox++) {
						for (int oz = -3; oz <= 3 && clear; oz++) {
							for (int y = 60; y <= 62 && clear; y++) {
								if (!level.getFluidState(new BlockPos(x + ox, y, z + oz)).is(FluidTags.WATER)) clear = false;
							}
							for (int y = 63; y <= 70 && clear; y++) {
								if (!level.getBlockState(new BlockPos(x + ox, y, z + oz)).isAir()) clear = false;
							}
						}
					}
					if (clear) return new BlockPos(x, 62, z);
				}
			}
		}
		return null;
	}

	private static int moor(CommandSourceStack source, boolean atWall) {
		return moor(source, atWall, false);
	}

	private static int moorAndRide(CommandSourceStack source) {
		return moor(source, false, true);
	}

	private static int moor(CommandSourceStack source, boolean atWall, boolean ride) {
		ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p
			: source.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
		if (player == null) {
			source.sendFailure(Component.literal("no player online"));
			return 0;
		}
		ServerLevel level = player.level();
		if (player.isPassenger()) player.stopRiding();

		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();

		BlockPos water = deepWater(level, player.blockPosition());
		if (water == null) {
			source.sendFailure(Component.literal("no open water three deep within 384 blocks"));
			return 0;
		}
		double surface = RaftSmoke.waterSurface(level, water);
		Vec3 pivotAt = new Vec3(water.getX() + 0.5, surface - RaftEntity.DRAFT, water.getZ() + 0.5);
		for (RaftEntity old : level.getEntitiesOfClass(RaftEntity.class, new AABB(pivotAt, pivotAt).inflate(16.0))) {
			old.discard();
		}
		RaftEntity raft = RaftItem.spawn(level, pivotAt, 0, RaftLayouts.starter());

		Vec3 origin = raft.position();
		int minX = Mth.floor(origin.x) - 2, maxX = Mth.floor(origin.x) + 5;
		int hullZ = Mth.floor(origin.z);

		int padY = (int) Math.floor(surface);
		for (int x = minX; x <= maxX; x++) {
			for (int z = hullZ - 5; z <= hullZ - 1; z++) {
				for (int y = padY - 2; y <= padY; y++) level.setBlock(new BlockPos(x, y, z), Blocks.SANDSTONE.defaultBlockState(), 3);
				for (int y = padY + 1; y <= padY + 3; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
			}
		}

		RaftLevel deck = raft.raftLevel();
		deck.setBlock(new BlockPos(2, 1, 0), Blocks.JUNGLE_PLANKS.defaultBlockState(), 3);
		deck.setBlock(new BlockPos(2, 2, 0), Blocks.JUNGLE_PLANKS.defaultBlockState(), 3);

		deck.setBlock(new BlockPos(1, 1, 2), Blocks.TORCH.defaultBlockState(), 3);

		double standX = origin.x + (atWall ? 2.5 : 0.5);
		double standZ = origin.z - 1.2;
		player.teleportTo(level, standX, padY + 1.0, standZ, Set.of(), 0.0F, 0.0F, false);
		double deckTop = origin.y + 1.0;
		String where = String.format(Locale.ROOT,
			"raft %d moored at %s heading %.1f; footprint x %.1f..%.1f z %.1f..%.1f; shore top y=%d, deck top y=%.3f, "
				+ "step %.3f; wall at local (2,1..2,0); player at %.2f %.2f %.2f facing +z at the %s column",
			raft.getId(), origin, raft.getYRot(), origin.x, origin.x + 3, origin.z, origin.z + 3,
			padY + 1, deckTop, deckTop - (padY + 1), standX, padY + 1.0, standZ, atWall ? "walled" : "open");
		Oceania.LOGGER.info("[raftmoor] {}", where);
		source.sendSuccess(() -> Component.literal(where), false);

		if (ride) return ride(source, player, raft) ? 1 : 0;
		return 1;
	}

	private static boolean ride(CommandSourceStack source, ServerPlayer player, RaftEntity raft) {
		if (raft.blocks().seats().isEmpty()) {
			source.sendFailure(Component.literal("that raft has no seat"));
			return false;
		}
		BlockPos helm = raft.blocks().seats().get(0);
		if (!raft.sit(player, helm)) {
			source.sendFailure(Component.literal("the helm refused the player"));
			return false;
		}
		String where = String.format(Locale.ROOT, "player seated at raft %d helm %s, at %s",
			raft.getId(), helm.toShortString(), player.position());
		Oceania.LOGGER.info("[raftmoor] {}", where);
		source.sendSuccess(() -> Component.literal(where), false);
		return true;
	}

}
