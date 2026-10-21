package com.oceania.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public final class WaterColumn {
	public static final int OCEAN = 0x3f76e4;
	public static final int DEEP = 0x1b3b8a;
	public static final int MAX_DEPTH = 160;
	private static final int UNSET = Integer.MIN_VALUE;
	private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

	private static final class Cache {
		long section = Long.MIN_VALUE;
		WeakReference<Object> level = new WeakReference<>(null);
		final int[] floor = new int[256];
		final int[] top = new int[256];
	}

	private WaterColumn() {
	}

	public static int tint(int color, BlockAndTintGetter level, BlockPos pos) {
		int depth = depthBelow(level, pos);
		if (depth <= 3) return color;
		double t1 = smooth((depth - 3) / 22.0);
		int c = lerp(color, OCEAN, t1);
		double t2 = smooth((depth - 30) / 120.0);
		return lerp(c, DEEP, t2);
	}

	public static int depthBelow(BlockAndTintGetter level, BlockPos pos) {
		int y = pos.getY();
		if (!(level instanceof RenderSectionRegion)) {

			return y - scanDown(level, pos.mutable(), y, y - MAX_DEPTH);
		}
		Cache c = CACHE.get();
		long section = SectionPos.asLong(pos.getX() >> 4, y >> 4, pos.getZ() >> 4);
		if (c.section != section || c.level.get() != level) {
			c.section = section;
			c.level = new WeakReference<>(level);
			Arrays.fill(c.floor, UNSET);
		}
		int i = (pos.getX() & 15) << 4 | (pos.getZ() & 15);
		int floor = c.floor[i], top = c.top[i];
		if (floor == UNSET || y <= floor) {
			floor = scanDown(level, pos.mutable(), y, y - MAX_DEPTH);
			top = y;
		} else if (y > top) {

			int stop = scanDown(level, pos.mutable(), y, top);
			if (stop > top) floor = stop;
			top = y;
		}
		c.floor[i] = floor;
		c.top[i] = top;
		return y - floor;
	}

	private static int scanDown(BlockAndTintGetter level, BlockPos.MutableBlockPos m, int from, int limit) {
		int y = from;
		m.setY(y);
		while (y > limit && level.getFluidState(m).is(FluidTags.WATER)) {
			y--;
			m.setY(y);
		}
		return y;
	}

	private static double smooth(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static int lerp(int a, int b, double t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bl = (int) Mth.lerp(t, a & 255, b & 255);

		return (a & 0xFF000000) | r << 16 | g << 8 | bl;
	}
}
