package com.oceania.client.raft;

import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

public final class RaftLight implements RaftLevel.Lighting {
	private static final int SKY_RESAMPLE_TICKS = 20;

	private final RaftEntity raft;
	private boolean dirty = true;
	private int originX, originY, originZ, sizeX, sizeY, sizeZ;
	private byte[] block = new byte[0];
	private byte[] sky = new byte[0];
	private int skySource = 15;
	private int lastSkySample = Integer.MIN_VALUE;

	public RaftLight(RaftEntity raft) {
		this.raft = raft;
	}

	@Override
	public void markDirty() {
		dirty = true;
	}

	@Override
	public int get(LightLayer layer, BlockPos pos) {
		if (raft.tickCount - lastSkySample >= SKY_RESAMPLE_TICKS) sampleSky();
		if (dirty) rebuild();
		int x = pos.getX() - originX, y = pos.getY() - originY, z = pos.getZ() - originZ;
		if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
			return layer == LightLayer.SKY ? skySource : 0;
		}
		int i = index(x, y, z);
		return layer == LightLayer.SKY ? sky[i] : block[i];
	}

	private int index(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}

	private void sampleSky() {
		lastSkySample = raft.tickCount;
		AABB b = raft.blocks().localBounds();
		Vec3 above = raft.toWorld(new Vec3((b.minX + b.maxX) * 0.5, b.maxY + 0.5, (b.minZ + b.maxZ) * 0.5));
		int sampled = raft.level().getBrightness(LightLayer.SKY, BlockPos.containing(above));
		if (sampled != skySource) {
			skySource = sampled;
			dirty = true;
		}
	}

	private void rebuild() {
		dirty = false;
		RaftLevel level = raft.raftLevel();
		AABB b = raft.blocks().localBounds();
		originX = (int) Math.floor(b.minX) - 1;
		originY = (int) Math.floor(b.minY) - 1;
		originZ = (int) Math.floor(b.minZ) - 1;
		sizeX = (int) Math.ceil(b.maxX) - originX + 1;
		sizeY = (int) Math.ceil(b.maxY) - originY + 1;
		sizeZ = (int) Math.ceil(b.maxZ) - originZ + 1;
		int n = sizeX * sizeY * sizeZ;
		if (block.length != n) {
			block = new byte[n];
			sky = new byte[n];
		} else {
			java.util.Arrays.fill(block, (byte) 0);
			java.util.Arrays.fill(sky, (byte) 0);
		}
		byte[] opacity = new byte[n];
		byte[] emission = new byte[n];
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					cursor.set(originX + x, originY + y, originZ + z);
					BlockState state = level.getBlockState(cursor);
					int i = index(x, y, z);
					opacity[i] = (byte) Math.min(15, state.getLightDampening());
					emission[i] = (byte) state.getLightEmission();
				}
			}
		}
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		for (int i = 0; i < n; i++) {
			if (emission[i] > 0) {
				block[i] = emission[i];
				queue.add(i);
			}
		}
		propagate(queue, block, opacity, false);

		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					if (x == 0 || y == 0 || z == 0 || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1) {
						int i = index(x, y, z);
						sky[i] = (byte) skySource;
						queue.add(i);
					}
				}
			}
		}
		propagate(queue, sky, opacity, true);
	}

	private void propagate(ArrayDeque<Integer> queue, byte[] light, byte[] opacity, boolean isSky) {
		while (!queue.isEmpty()) {
			int i = queue.poll();
			int v = light[i];
			if (v <= 1) continue;
			int x = i % sizeX;
			int z = (i / sizeX) % sizeZ;
			int y = i / (sizeX * sizeZ);
			for (Direction d : Direction.values()) {
				int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) continue;
				int j = index(nx, ny, nz);
				int loss = isSky && d == Direction.DOWN && v == 15 ? opacity[j] : Math.max(1, opacity[j]);
				int nv = v - loss;
				if (nv > light[j]) {
					light[j] = (byte) nv;
					queue.add(j);
				}
			}
		}
	}
}
