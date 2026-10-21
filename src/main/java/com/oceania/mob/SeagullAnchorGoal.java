package com.oceania.mob;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public class SeagullAnchorGoal extends Goal {

	private static final int SEARCH_RADIUS = 160;

	private static final int CELL = 16;

	private static final int CELL_RADIUS = SEARCH_RADIUS / CELL;

	private static final int RESCAN_TICKS = 200;

	private static final int ALTITUDE_MIN = 18;
	private static final int ALTITUDE_SPAN = 14;

	private final SeagullEntity gull;

	private int nextScan;

	public SeagullAnchorGoal(SeagullEntity gull) {
		this.gull = gull;

	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public void tick() {
		if (this.nextScan > 0) {
			this.nextScan--;
			return;
		}

		this.nextScan = this.adjustedTickDelay(RESCAN_TICKS + this.gull.getRandom().nextInt(RESCAN_TICKS));

		if (!this.anchorStillGood()) {
			this.reanchor();
		}
	}

	private boolean anchorStillGood() {
		BlockPos anchor = this.gull.getAnchorPoint();
		if (BlockPos.ZERO.equals(anchor) || !this.isLandColumn(anchor.getX(), anchor.getZ())) {
			return false;
		}

		double dx = anchor.getX() + 0.5 - this.gull.getX();
		double dz = anchor.getZ() + 0.5 - this.gull.getZ();
		return dx * dx + dz * dz <= (double) SEARCH_RADIUS * SEARCH_RADIUS;
	}

	private boolean isLandColumn(int x, int z) {
		Level level = this.gull.level();
		return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) >= level.getSeaLevel();
	}

	private static Optional<BlockPos> closestCell(int radius, java.util.function.Predicate<BlockPos> test) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int ring = 0; ring <= radius; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					probe.set(dx, 0, dz);
					if (test.test(probe)) return Optional.of(probe.immutable());
				}
			}
		}
		return Optional.empty();
	}

	private void reanchor() {
		final int originX = this.gull.getBlockX();
		final int originZ = this.gull.getBlockZ();

		Optional<BlockPos> cell = closestCell(CELL_RADIUS,
			offset -> this.isLandColumn(originX + offset.getX() * CELL, originZ + offset.getZ() * CELL));
		if (cell.isEmpty()) {
			return;
		}

		BlockPos found = cell.get();
		int landX = originX + found.getX() * CELL;
		int landZ = originZ + found.getZ() * CELL;

		Level level = this.gull.level();
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, landX, landZ);
		int y = Math.max(ground, level.getSeaLevel())
			+ ALTITUDE_MIN + this.gull.getRandom().nextInt(ALTITUDE_SPAN);

		this.gull.setAnchorPoint(new BlockPos(landX, y, landZ));
	}
}
