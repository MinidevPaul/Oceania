package com.oceania.client.raft;

import com.oceania.raft.RaftLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

public class RaftUseOnContext extends UseOnContext {
	public RaftUseOnContext(RaftLevel level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
		super(level, player, hand, stack, hit);
	}
}
