package com.oceania.debug;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class Surface {
	private Surface() {
	}

	public static int y(ServerLevel level, int x, int z) {

		level.getChunk(x >> 4, z >> 4);
		return Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getMinY() + 1);
	}
}
