package com.oceania.raft;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public class RaftPlaceContext extends BlockPlaceContext {
	private final RaftEntity raft;

	public RaftPlaceContext(RaftEntity raft, RaftLevel level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
		super(level, player, hand, stack, hit);
		this.raft = raft;
	}

	private float localYaw() {
		Player player = getPlayer();
		return player == null ? 0 : Mth.wrapDegrees(player.getYRot() - raft.getYRot());
	}

	@Override
	public float getRotation() {
		return localYaw();
	}

	@Override
	public Direction getHorizontalDirection() {
		return getPlayer() == null ? Direction.NORTH : Direction.fromYRot(localYaw());
	}

	@Override
	public Direction getNearestLookingDirection() {
		return getNearestLookingDirections()[0];
	}

	@Override
	public Direction[] getNearestLookingDirections() {
		Player player = getPlayer();
		if (player == null) return super.getNearestLookingDirections();
		return orderedByNearest(player.getXRot(), localYaw());
	}

	static Direction[] orderedByNearest(float xRot, float yRot) {
		float f = xRot * Mth.DEG_TO_RAD;
		float g = -yRot * Mth.DEG_TO_RAD;
		float h = Mth.sin(f);
		float i = Mth.cos(f);
		float j = Mth.sin(g);
		float k = Mth.cos(g);
		boolean east = j > 0.0F;
		boolean up = h < 0.0F;
		boolean south = k > 0.0F;
		float l = east ? j : -j;
		float m = up ? -h : h;
		float n = south ? k : -k;
		float o = l * i;
		float p = n * i;
		Direction dx = east ? Direction.EAST : Direction.WEST;
		Direction dy = up ? Direction.UP : Direction.DOWN;
		Direction dz = south ? Direction.SOUTH : Direction.NORTH;
		if (l > n) {
			if (m > o) {
				return order(dy, dx, dz);
			}
			return p > m ? order(dx, dz, dy) : order(dx, dy, dz);
		} else if (m > p) {
			return order(dy, dz, dx);
		} else {
			return o > m ? order(dz, dx, dy) : order(dz, dy, dx);
		}
	}

	private static Direction[] order(Direction a, Direction b, Direction c) {
		return new Direction[]{a, b, c, c.getOpposite(), b.getOpposite(), a.getOpposite()};
	}
}
