package com.oceania.raft;

import com.oceania.registry.OceaniaEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RaftItem extends Item {

	private final Block planks;

	public RaftItem(Block planks, Properties properties) {
		super(properties);
		this.planks = planks;
	}

	public Block planks() {
		return planks;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.is(FluidTags.WATER)) {
			return InteractionResult.PASS;
		}

		if (!level.getEntitiesOfClass(RaftEntity.class, new AABB(pos).inflate(1.0)).isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		double surface = pos.getY() + fluid.getHeight(level, pos);
		RaftEntity raft = spawn(level, new Vec3(hit.getLocation().x, surface - RaftEntity.DRAFT, hit.getLocation().z), player.getYRot(), RaftLayouts.starter(planks));
		if (raft == null) {
			return InteractionResult.FAIL;
		}
		for (RaftEntity other : level.getEntitiesOfClass(RaftEntity.class, raft.getBoundingBox().inflate(0.5))) {
			if (other != raft) {
				raft.discard();
				return InteractionResult.FAIL;
			}
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	public static RaftEntity spawn(Level level, Vec3 pivotWorld, float yaw, RaftBlocks blocks) {
		RaftEntity raft = OceaniaEntities.RAFT.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (raft == null) return null;
		Vec3 pivot = blocks.pivot();
		raft.setBlocks(blocks);
		raft.setYRot(yaw);
		raft.setPos(pivotWorld.x - pivot.x, pivotWorld.y, pivotWorld.z - pivot.z);
		level.addFreshEntity(raft);
		return raft;
	}
}
