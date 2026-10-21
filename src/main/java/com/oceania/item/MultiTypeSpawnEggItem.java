package com.oceania.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;

public abstract class MultiTypeSpawnEggItem extends SpawnEggItem {
	protected MultiTypeSpawnEggItem(Item.Properties properties) {
		super(properties);
	}

	protected abstract EntityType<? extends Mob> chooseType(LevelAccessor level, BlockPos pos);

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		BlockPos clicked = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState state = level.getBlockState(clicked);
		if (level.getBlockEntity(clicked) instanceof Spawner spawner) {
			spawner.setEntityId(this.chooseType(level, clicked), level.getRandom());
			level.sendBlockUpdated(clicked, state, state, 3);
			level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, clicked);
			stack.shrink(1);
			return InteractionResult.CONSUME;
		}
		BlockPos target = state.getCollisionShape(level, clicked).isEmpty()
			? clicked
			: clicked.relative(face);

		Entity spawned = this.chooseType(level, target).spawn(serverLevel, stack,
			context.getPlayer(), target, EntitySpawnReason.SPAWN_ITEM_USE, true,
			!Objects.equals(clicked, target) && face == Direction.UP);
		if (spawned != null) {
			stack.consume(1, context.getPlayer());
			level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clicked);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
			return InteractionResult.PASS;
		}
		if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
			return InteractionResult.FAIL;
		}
		Entity spawned = this.chooseType(level, pos).spawn(serverLevel, stack, player, pos,
			EntitySpawnReason.SPAWN_ITEM_USE, false, false);
		if (spawned == null) {
			return InteractionResult.PASS;
		}
		stack.consume(1, player);
		player.awardStat(Stats.ITEM_USED.get(this));
		level.gameEvent(player, GameEvent.ENTITY_PLACE, spawned.position());
		return InteractionResult.CONSUME;
	}
}
