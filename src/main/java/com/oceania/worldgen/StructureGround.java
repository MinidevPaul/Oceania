package com.oceania.worldgen;

import com.oceania.mixin.worldgen.BeardifierPiecesAccessor;
import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

public final class StructureGround {

	private static final int MARGIN = 2;
	private static final int DEPTH = 12;

	private static final ThreadLocal<List<Beardifier.Rigid>> PIECES = new ThreadLocal<>();

	private StructureGround() {
	}

	public static void remember(Beardifier beardifier) {
		PIECES.set(beardifier == null ? null : ((BeardifierPiecesAccessor) (Object) beardifier).oceania$pieces());
	}

	public static boolean supports(int x, int y, int z) {
		List<Beardifier.Rigid> pieces = PIECES.get();
		if (pieces == null || pieces.isEmpty()) {
			return false;
		}
		for (Beardifier.Rigid rigid : pieces) {
			if (rigid.terrainAdjustment() != TerrainAdjustment.BEARD_THIN) {
				continue;
			}
			var box = rigid.box();
			if (x < box.minX() - MARGIN || x > box.maxX() + MARGIN
					|| z < box.minZ() - MARGIN || z > box.maxZ() + MARGIN) {
				continue;
			}
			int groundY = box.minY() + rigid.groundLevelDelta();
			if (y >= groundY - DEPTH && y <= box.maxY()) {
				return true;
			}
		}
		return false;
	}
}
