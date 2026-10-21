package com.oceania.raft;

import com.oceania.registry.OceaniaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class RaftBlocks {

	public static final int MAX_FOOTPRINT = 32;

	public static final int MAX_HEIGHT = 16;

	public static final Vec3 DEFAULT_PIVOT = new Vec3(1.5, 0, 1.5);

	private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
	private Vec3 pivot = DEFAULT_PIVOT;

	private @Nullable BlockPos core;
	private AABB localBounds = new AABB(0, 0, 0, 1, 1, 1);
	private List<BlockPos> seats = List.of();
	private @Nullable List<AABB> colliders;
	private @Nullable List<BlockPos> shell;
	private @Nullable List<Emitter> emitters;

	private Map<BlockPos, CompoundTag> pendingNbt = new HashMap<>();

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public int size() {
		return blocks.size();
	}

	public Map<BlockPos, BlockState> all() {
		return Collections.unmodifiableMap(blocks);
	}

	public @Nullable BlockState get(BlockPos pos) {
		return blocks.get(pos);
	}

	public boolean has(BlockPos pos) {
		return blocks.containsKey(pos);
	}

	public void put(BlockPos pos, BlockState state) {
		blocks.put(pos.immutable(), state);
		invalidate();
	}

	public @Nullable BlockState remove(BlockPos pos) {
		BlockState removed = blocks.remove(pos);
		if (removed != null) invalidate();
		return removed;
	}

	public boolean fits(BlockPos pos) {
		if (blocks.isEmpty()) return true;
		int minX = Math.min((int) localBounds.minX, pos.getX());
		int maxX = Math.max((int) localBounds.maxX - 1, pos.getX());
		int minY = Math.min((int) localBounds.minY, pos.getY());
		int maxY = Math.max((int) localBounds.maxY - 1, pos.getY());
		int minZ = Math.min((int) localBounds.minZ, pos.getZ());
		int maxZ = Math.max((int) localBounds.maxZ - 1, pos.getZ());
		return maxX - minX + 1 <= MAX_FOOTPRINT && maxZ - minZ + 1 <= MAX_FOOTPRINT && maxY - minY + 1 <= MAX_HEIGHT;
	}

	public AABB localBounds() {
		return localBounds;
	}

	public Vec3 pivot() {
		return pivot;
	}

	public void setPivot(Vec3 pivot) {
		this.pivot = pivot;
	}

	public List<BlockPos> seats() {
		return seats;
	}

	public @Nullable BlockPos core() {
		return core;
	}

	public void setCore(@Nullable BlockPos core) {
		this.core = core == null ? null : core.immutable();
	}

	public boolean isCore(BlockPos pos) {
		if (core == null) return false;
		if (pos.equals(core)) return true;
		return pos.getY() == core.getY() - 1 && Math.abs(pos.getX() - core.getX()) <= 1 && Math.abs(pos.getZ() - core.getZ()) <= 1;
	}

	public boolean isCoreHelm(BlockPos pos) {
		return core != null && pos.equals(core);
	}

	public List<AABB> colliders() {
		if (colliders == null) {
			List<AABB> list = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos p = e.getKey();
				for (AABB box : e.getValue().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
					list.add(box.move(p.getX(), p.getY(), p.getZ()));
				}
			}
			colliders = list;
		}
		return colliders;
	}

	public record Emitter(BlockPos pos, int level) {
	}

	public List<Emitter> emitters() {
		if (emitters == null) {
			List<Emitter> list = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				int level = e.getValue().getLightEmission();
				if (level > 0) list.add(new Emitter(e.getKey(), level));
			}
			emitters = list;
		}
		return emitters;
	}

	public List<BlockPos> shell() {
		if (shell == null) {
			List<BlockPos> list = new ArrayList<>();
			for (BlockPos p : blocks.keySet()) {
				for (Direction d : Direction.values()) {
					if (!blocks.containsKey(p.relative(d))) {
						list.add(p);
						break;
					}
				}
			}
			shell = list;
		}
		return shell;
	}

	public Map<BlockPos, CompoundTag> takePendingNbt() {
		Map<BlockPos, CompoundTag> taken = pendingNbt;
		pendingNbt = new HashMap<>();
		return taken;
	}

	private void invalidate() {
		colliders = null;
		shell = null;
		emitters = null;
		if (blocks.isEmpty()) {
			localBounds = new AABB(0, 0, 0, 1, 1, 1);
			seats = List.of();
			return;
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		List<BlockPos> helms = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			BlockPos p = e.getKey();
			minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
			maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
			if (e.getValue().is(OceaniaBlocks.RAFT_HELM)) helms.add(p);
		}
		localBounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
		seats = List.copyOf(helms);
	}

	public CompoundTag write(Function<BlockPos, @Nullable CompoundTag> nbt) {
		CompoundTag tag = new CompoundTag();
		ListTag palette = new ListTag();
		Map<BlockState, Integer> paletteIndex = new HashMap<>();
		ListTag list = new ListTag();
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			BlockState state = e.getValue();
			int idx = paletteIndex.computeIfAbsent(state, s -> {
				palette.add(NbtUtils.writeBlockState(s));
				return palette.size() - 1;
			});
			CompoundTag entry = new CompoundTag();
			BlockPos p = e.getKey();
			entry.putIntArray("P", new int[]{p.getX(), p.getY(), p.getZ()});
			entry.putInt("S", idx);
			CompoundTag t = nbt.apply(p);
			if (t != null && !t.isEmpty()) entry.put("T", t);
			list.add(entry);
		}
		tag.put("Palette", palette);
		tag.put("Blocks", list);
		tag.putDouble("PivotX", pivot.x);
		tag.putDouble("PivotY", pivot.y);
		tag.putDouble("PivotZ", pivot.z);
		if (core != null) tag.store("Core", BlockPos.CODEC, core);
		return tag;
	}

	public static RaftBlocks read(CompoundTag tag, HolderGetter<Block> blocks) {
		RaftBlocks result = new RaftBlocks();
		ListTag palette = tag.getListOrEmpty("Palette");
		List<BlockState> states = new ArrayList<>(palette.size());
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(blocks, palette.getCompoundOrEmpty(i)));
		}
		ListTag list = tag.getListOrEmpty("Blocks");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			int[] p = entry.getIntArray("P").orElse(new int[0]);
			int s = entry.getIntOr("S", -1);
			if (p.length != 3 || s < 0 || s >= states.size()) continue;
			BlockPos pos = new BlockPos(p[0], p[1], p[2]);
			result.blocks.put(pos, states.get(s));
			entry.getCompound("T").ifPresent(t -> result.pendingNbt.put(pos, t));
		}
		if (tag.contains("PivotX")) {
			result.pivot = new Vec3(tag.getDoubleOr("PivotX", 0.0), tag.getDoubleOr("PivotY", 0.0), tag.getDoubleOr("PivotZ", 0.0));
		}
		result.invalidate();
		result.core = tag.read("Core", BlockPos.CODEC).orElse(null);

		if (result.core == null && !result.seats.isEmpty()) result.core = result.seats.get(0);
		return result;
	}
}
