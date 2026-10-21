package com.oceania.raft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class RaftLayouts {
	private RaftLayouts() {
	}

	public static RaftBlocks starter() {
		return starter(Blocks.JUNGLE_PLANKS);
	}

	public static RaftBlocks starter(Block planks) {
		RaftBlocks blocks = new RaftBlocks();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				blocks.put(new BlockPos(x, 0, z), planks.defaultBlockState());
			}
		}
		blocks.put(new BlockPos(1, 1, 1), RaftHelmBlock.withWood(planks));
		blocks.setCore(new BlockPos(1, 1, 1));
		return blocks;
	}
}
