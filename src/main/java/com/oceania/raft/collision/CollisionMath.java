/*
 * Adapted from Create (https://github.com/Creators-of-Create/Create),
 * Copyright (c) The Create Team / The Creators of Create, MIT License.
 * See LICENSE-THIRD-PARTY.md.
 */
package com.oceania.raft.collision;

import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class CollisionMath {

	private CollisionMath() {}

	public static final boolean[] trueAndFalse = { true, false };

	public static final Vec3 CENTER_OF_ORIGIN = new Vec3(.5, .5, .5);

	public static Vec3 rotate(Vec3 vec, double deg, Axis axis) {
		if (deg == 0)
			return vec;
		if (vec == Vec3.ZERO)
			return vec;

		float angle = (float) (deg / 180f * Math.PI);
		double sin = Mth.sin(angle);
		double cos = Mth.cos(angle);
		double x = vec.x;
		double y = vec.y;
		double z = vec.z;

		if (axis == Axis.X)
			return new Vec3(x, y * cos - z * sin, z * cos + y * sin);
		if (axis == Axis.Y)
			return new Vec3(x * cos + z * sin, y, z * cos - x * sin);
		if (axis == Axis.Z)
			return new Vec3(x * cos - y * sin, y * cos + x * sin, z);
		return vec;
	}
}
