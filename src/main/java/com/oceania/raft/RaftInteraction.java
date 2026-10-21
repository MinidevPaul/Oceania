package com.oceania.raft;

import com.oceania.Oceania;
import com.oceania.registry.OceaniaBlocks;
import com.oceania.registry.OceaniaItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RaftInteraction {
	private RaftInteraction() {
	}

	public static final class Breaking {
		public final int raftId;
		public final BlockPos pos;
		public final long startTick;
		public int lastStage = -1;

		Breaking(int raftId, BlockPos pos, long startTick) {
			this.raftId = raftId;
			this.pos = pos;
			this.startTick = startTick;
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RaftInteraction::tick);
	}

	static @Nullable RaftEntity raft(ServerPlayer player, int raftId) {
		Entity e = player.level().getEntity(raftId);
		return e instanceof RaftEntity raft && !raft.isRemoved() ? raft : null;
	}

	static void handleUse(ServerPlayer player, RaftNetworking.UsePayload payload) {
		RaftEntity raft = raft(player, payload.raftId());
		if (raft == null || !raft.withinReach(player, payload.pos(), 1.0)) return;
		RaftLevel level = raft.raftLevel();
		if (level.getBlockState(payload.pos()).isAir()) return;
		BlockHitResult hit = new BlockHitResult(payload.hit(), payload.face(), payload.pos(), false);
		((RaftRider) player).oceania$setMenuRaft(raft);
		try {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (!stack.isItemEnabled(level.enabledFeatures())) return;
				InteractionResult result = useOn(player, raft, level, stack, hand, hit);

				if (com.oceania.debug.DebugCommands.inDevelopment()) {
					Oceania.LOGGER.info("[raft] use {} on {} at {} face {} with {} -> {}", hand, level.getBlockState(payload.pos()).getBlock(),
						payload.pos().toShortString(), payload.face(), stack, result);
				}
				if (result.consumesAction()) {
					if (result instanceof InteractionResult.Success success && success.shouldSwing()) player.swing(hand, stack.getInteractAnimation(), true);
					return;
				}
				if (result == InteractionResult.FAIL) return;
				if (!stack.isEmpty()) {

					InteractionResult used = player.gameMode.useItem(player, player.level(), stack, hand);
					if (used.consumesAction()) {
						if (used instanceof InteractionResult.Success success && success.shouldSwing()) player.swing(hand, stack.getInteractAnimation(), true);
						return;
					}
				}
			}
		} finally {

			confirm(player, raft, level, payload.pos());
			confirm(player, raft, level, payload.pos().relative(payload.face()));
		}
	}

	private static void confirm(ServerPlayer player, RaftEntity raft, RaftLevel level, BlockPos pos) {
		if (raft.isRemoved() || !raft.blocks().fits(pos)) return;
		RaftNetworking.send(player, new RaftNetworking.BlockPayload(raft.getId(), pos, level.getBlockState(pos), level.syncNbt(pos)));
	}

	private static InteractionResult useOn(ServerPlayer player, RaftEntity raft, RaftLevel level, ItemStack stack, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.getBlock().isEnabled(level.enabledFeatures())) return InteractionResult.FAIL;
		if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
			MenuProvider provider = state.getMenuProvider(level, pos);
			if (provider != null) {
				player.openMenu(provider);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		boolean holdingSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
		boolean sneakBypass = player.isSecondaryUseActive() && holdingSomething;
		ItemStack before = stack.copy();
		BlockPos worldPos = level.worldBlockPos(pos);
		if (!sneakBypass) {
			if (state.getBlock() instanceof BedBlock && hand == InteractionHand.MAIN_HAND) {
				RaftBeds.use(player, raft, pos, state);
				return InteractionResult.CONSUME;
			}

			if (state.getBlock() instanceof CampfireBlock && level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire
					&& raft.level() instanceof ServerLevel server && campfire.placeFood(server, player, stack)) {
				player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
				CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, worldPos, before);
				return InteractionResult.SUCCESS;
			}
			InteractionResult itemResult = state.useItemOn(stack, level, player, hand, hit);
			if (itemResult.consumesAction()) {
				CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, worldPos, before);
				return itemResult;
			}
			if (itemResult instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
				InteractionResult result = state.useWithoutItem(level, player, hit);
				if (result.consumesAction()) {
					openMenuIfNone(player, state, level, pos);
					CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, worldPos);
					return result;
				}
			}
		}
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		int count = stack.getCount();
		InteractionResult result;
		if (stack.getItem() instanceof RaftItem) {
			result = placeHelm(player, raft, level, stack, hand, hit);
		} else if (stack.getItem() instanceof BlockItem blockItem) {
			result = blockItem.place(new RaftPlaceContext(raft, level, player, hand, stack, hit));
		} else {
			result = stack.useOn(new UseOnContext(level, player, hand, stack, hit));
		}
		if (player.isCreative()) stack.setCount(count);
		if (result.consumesAction()) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, worldPos, before);
		return result;
	}

	private static void openMenuIfNone(ServerPlayer player, BlockState state, RaftLevel level, BlockPos pos) {
		if (player.containerMenu != player.inventoryMenu) return;
		if (state.getBlock() instanceof EnderChestBlock) {
			PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
			if (level.getBlockEntity(pos) instanceof EnderChestBlockEntity be) enderChest.setActiveChest(be);
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, enderChest), ENDER_CHEST_TITLE));
			player.awardStat(Stats.OPEN_ENDERCHEST);
			return;
		}
		MenuProvider provider = state.getMenuProvider(level, pos);
		if (provider != null) player.openMenu(provider);
	}

	private static final Component ENDER_CHEST_TITLE = Component.translatable("container.enderchest");

	private static InteractionResult placeHelm(ServerPlayer player, RaftEntity raft, RaftLevel level, ItemStack stack, InteractionHand hand, BlockHitResult hit) {
		BlockPlaceContext context = new RaftPlaceContext(raft, level, player, hand, stack, hit);
		if (!context.canPlace()) return InteractionResult.FAIL;
		BlockPos target = context.getClickedPos();
		BlockState helm = RaftHelmBlock.withWood(((RaftItem) stack.getItem()).planks());
		if (!level.isUnobstructed(helm, target, CollisionContext.of(player))) return InteractionResult.FAIL;
		if (!level.setBlock(target, helm, 11)) return InteractionResult.FAIL;
		SoundType sound = helm.getSoundType();
		level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(player, helm));
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		if (!player.isCreative()) stack.shrink(1);
		return InteractionResult.CONSUME;
	}

	static void handleWalker(ServerPlayer player, RaftNetworking.WalkerPayload payload) {
		((RaftRider) player).oceania$setWalker(payload.raftId(), payload.local(), player.level().getGameTime());
	}

	public static @Nullable Component problem(RaftEntity raft, BlockPos pos) {
		RaftBlocks blocks = raft.blocks();
		if (blocks.isCoreHelm(pos)) {
			return raft.isSeatOccupied(pos) ? Component.translatable("oceania.raft.helm_occupied") : null;
		}
		if (blocks.isCore(pos)) return Component.translatable("oceania.raft.core");
		if (blocks.get(pos) != null && blocks.get(pos).is(OceaniaBlocks.RAFT_HELM) && raft.isSeatOccupied(pos)) {
			return Component.translatable("oceania.raft.helm_occupied");
		}
		return null;
	}

	public static boolean removeHelm(RaftEntity raft, BlockPos pos, @Nullable ServerPlayer player) {
		RaftBlocks blocks = raft.blocks();
		BlockState state = raft.raftLevel().getBlockState(pos);
		boolean coreMoves = blocks.isCoreHelm(pos) || blocks.core() == null;
		if (coreMoves) {
			BlockPos successor = null;
			for (BlockPos seat : blocks.seats()) {

				boolean under = seat.getY() - 1 == pos.getY() && Math.abs(seat.getX() - pos.getX()) <= 1 && Math.abs(seat.getZ() - pos.getZ()) <= 1;
				if (!seat.equals(pos) && !under) {
					successor = seat;
					break;
				}
			}
			if (successor == null) {
				dismantle(raft, pos, player);
				return false;
			}
			blocks.setCore(successor);
		}
		raft.raftLevel().removeBlock(pos, false);
		if (player == null || !player.isCreative()) {
			spawnItem((ServerLevel) raft.level(), raft, raft.toWorld(Vec3.atCenterOf(pos)), new ItemStack(RaftHelmBlock.houseboat(state)));
		}

		if (coreMoves) raft.syncAll();
		return true;
	}

	public static void dismantle(RaftEntity raft, BlockPos helmPos, @Nullable ServerPlayer player) {
		ServerLevel real = (ServerLevel) raft.level();
		RaftLevel level = raft.raftLevel();
		BlockPos core = raft.blocks().core() != null ? raft.blocks().core() : helmPos;
		Set<BlockPos> absorbed = new HashSet<>();
		absorbed.add(core);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				absorbed.add(core.offset(dx, -1, dz));
			}
		}
		boolean creative = player != null && player.isCreative();
		if (!creative) {
			for (Map.Entry<BlockPos, BlockState> e : raft.blocks().all().entrySet()) {
				BlockPos pos = e.getKey();
				if (absorbed.contains(pos)) continue;
				BlockState state = e.getValue();
				BlockEntity blockEntity = level.getBlockEntity(pos);
				BlockPos worldPos = level.worldBlockPos(pos);
				Vec3 centre = raft.toWorld(Vec3.atCenterOf(pos));
				for (ItemStack drop : Block.getDrops(state, real, worldPos, blockEntity, player, ItemStack.EMPTY)) {
					spawnItem(real, null, centre, drop);
				}
				if (blockEntity instanceof Container container) {

					for (int i = 0; i < container.getContainerSize(); i++) {
						ItemStack held = container.getItem(i);
						if (!held.isEmpty()) spawnItem(real, null, centre, held.copy());
					}
					container.clearContent();
				}
			}
			spawnItem(real, null, raft.toWorld(Vec3.atCenterOf(helmPos)), new ItemStack(raft.houseboatItem()));
		}
		RaftBeds.onRaftRemoved(raft);
		raft.discard();
	}

	static void spawnItem(ServerLevel real, @Nullable RaftEntity raft, Vec3 centre, ItemStack stack) {
		double x = centre.x + Mth.nextDouble(real.getRandom(), -0.25, 0.25);
		double y = centre.y + Mth.nextDouble(real.getRandom(), -0.25, 0.25) - 0.125;
		double z = centre.z + Mth.nextDouble(real.getRandom(), -0.25, 0.25);
		spawnItemAt(real, raft, new Vec3(x, y, z), stack, null);
	}

	static void spawnItemAt(ServerLevel real, @Nullable RaftEntity raft, Vec3 at, ItemStack stack, @Nullable Vec3 velocity) {
		ItemEntity item = null;
		if (raft == null || !overDeck(raft, at)) {
			double surface = waterSurfaceBelow(real, at.x, at.y, at.z);
			if (!Double.isNaN(surface)) {
				item = new ItemEntity(real, at.x, surface - 0.2, at.z, stack,
					Mth.nextDouble(real.getRandom(), -0.05, 0.05), 0.0, Mth.nextDouble(real.getRandom(), -0.05, 0.05));
			}
		}
		if (item == null) {
			item = new ItemEntity(real, at.x, at.y, at.z, stack);
			if (velocity != null) item.setDeltaMovement(velocity);
		}
		item.setDefaultPickUpDelay();
		real.addFreshEntity(item);
	}

	private static boolean overDeck(RaftEntity raft, Vec3 world) {
		Vec3 local = raft.toLocal(world);
		for (net.minecraft.world.phys.AABB box : raft.blocks().colliders()) {
			if (local.x >= box.minX && local.x <= box.maxX && local.z >= box.minZ && local.z <= box.maxZ && box.minY < local.y) return true;
		}
		return false;
	}

	private static double waterSurfaceBelow(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		for (int i = 0; i < 4; i++) {
			net.minecraft.world.level.material.FluidState fluid = level.getFluidState(pos);
			if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
				return pos.getY() + fluid.getHeight(level, pos);
			}
			pos.move(0, -1, 0);
		}
		return Double.NaN;
	}

	private static float destroyProgress(ServerPlayer player, BlockState state, RaftLevel level, BlockPos pos) {
		if (player.onGround() || !((RaftRider) player).oceania$isOnRaft(player.level().getGameTime())) {
			return state.getDestroyProgress(player, level, pos);
		}
		player.setOnGround(true);
		try {
			return state.getDestroyProgress(player, level, pos);
		} finally {
			player.setOnGround(false);
		}
	}

	static void handleAttack(ServerPlayer player, RaftNetworking.AttackPayload payload) {
		RaftRider rider = (RaftRider) player;
		if (!payload.start()) {
			abort(player);
			return;
		}
		RaftEntity raft = raft(player, payload.raftId());
		if (raft == null || !raft.withinReach(player, payload.pos(), 1.0)) {
			abort(player);
			return;
		}
		RaftLevel level = raft.raftLevel();
		BlockPos pos = payload.pos();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			abort(player);
			return;
		}
		if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
			abort(player);
			return;
		}
		Component problem = problem(raft, pos);
		if (problem != null) {
			player.sendOverlayMessage(problem);
			abort(player);
			return;
		}
		Breaking current = rider.oceania$breaking();
		if (current != null && current.raftId == raft.getId() && current.pos.equals(pos)) return;
		abort(player);
		long now = player.level().getGameTime();
		if (player.isCreative()) {

			if (now >= rider.oceania$breakCooldownUntil()) {
				destroy(player, raft, pos);
				rider.oceania$setBreakCooldownUntil(now + 5);
			} else {
				rider.oceania$setBreaking(new Breaking(raft.getId(), pos, now));
			}
			return;
		}
		state.attack(level, pos, player);
		float f = destroyProgress(player, state, level, pos);
		if (f >= 1.0F) {
			destroy(player, raft, pos);
			return;
		}
		Breaking breaking = new Breaking(raft.getId(), pos, now);
		rider.oceania$setBreaking(breaking);
		breaking.lastStage = (int) (f * 10.0F);
		raft.syncBreakProgress(pos, breaking.lastStage);
	}

	private static void abort(ServerPlayer player) {
		RaftRider rider = (RaftRider) player;
		Breaking breaking = rider.oceania$breaking();
		if (breaking == null) return;
		rider.oceania$setBreaking(null);
		RaftEntity raft = raft(player, breaking.raftId);
		if (raft != null && breaking.lastStage >= 0) raft.syncBreakProgress(breaking.pos, -1);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RaftRider rider = (RaftRider) player;
			Breaking breaking = rider.oceania$breaking();
			if (breaking == null) continue;
			RaftEntity raft = raft(player, breaking.raftId);
			if (raft == null || !raft.withinReach(player, breaking.pos, 1.0) || !player.isAlive()) {
				abort(player);
				continue;
			}
			RaftLevel level = raft.raftLevel();
			BlockState state = level.getBlockState(breaking.pos);
			if (state.isAir()) {
				abort(player);
				continue;
			}
			long now = player.level().getGameTime();
			if (player.isCreative()) {
				if (now >= rider.oceania$breakCooldownUntil()) {
					rider.oceania$setBreaking(null);
					destroy(player, raft, breaking.pos);
					rider.oceania$setBreakCooldownUntil(now + 5);
				}
				continue;
			}
			float f = destroyProgress(player, state, level, breaking.pos) * (now - breaking.startTick + 1);
			int stage = (int) (f * 10.0F);
			if (f >= 1.0F) {
				rider.oceania$setBreaking(null);
				raft.syncBreakProgress(breaking.pos, -1);
				destroy(player, raft, breaking.pos);
				rider.oceania$setBreakCooldownUntil(now + 5);
				continue;
			}
			if (stage != breaking.lastStage) {
				breaking.lastStage = stage;
				raft.syncBreakProgress(breaking.pos, stage);
			}
		}
	}

	public static boolean destroy(ServerPlayer player, RaftEntity raft, BlockPos pos) {
		RaftLevel level = raft.raftLevel();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;
		if (!player.getMainHandItem().canDestroyBlock(state, level, pos, player)) return false;
		if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
		Block block = state.getBlock();
		Component problem = problem(raft, pos);
		if (problem != null) {
			player.sendOverlayMessage(problem);
			return false;
		}
		if (state.is(OceaniaBlocks.RAFT_HELM)) {
			block.playerWillDestroy(level, pos, state, player);
			removeHelm(raft, pos, player);
			return true;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		BlockState broken = block.playerWillDestroy(level, pos, state, player);
		boolean removed = level.removeBlock(pos, false);
		if (removed) block.destroy(level, pos, broken);
		if (player.isCreative()) return true;
		ItemStack tool = player.getMainHandItem();
		ItemStack toolBefore = tool.copy();
		boolean correctTool = player.hasCorrectToolForDrops(broken);
		tool.mineBlock(level, broken, pos, player);
		if (removed && correctTool) {
			player.awardStat(Stats.BLOCK_MINED.get(block));
			player.causeFoodExhaustion(0.005F);
			dropResources(player, raft, broken, pos, blockEntity, toolBefore);
		}
		return true;
	}

	private static void dropResources(ServerPlayer player, RaftEntity raft, BlockState state, BlockPos pos, @Nullable BlockEntity blockEntity, ItemStack tool) {
		ServerLevel real = player.level();
		BlockPos worldPos = raft.raftLevel().worldBlockPos(pos);
		Vec3 centre = raft.toWorld(Vec3.atCenterOf(pos));
		for (ItemStack drop : Block.getDrops(state, real, worldPos, blockEntity, player, tool)) {
			spawnItem(real, raft, centre, drop);
		}
		state.spawnAfterBreak(real, worldPos, tool, true);
	}
}
