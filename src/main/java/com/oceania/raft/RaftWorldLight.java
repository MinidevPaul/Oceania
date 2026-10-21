package com.oceania.raft;

import com.oceania.Oceania;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RaftWorldLight {

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private enum Mode {

		ON,

		MOORED,

		OFF
	}

	private static final Mode MODE = mode();

	private static final double MOORED_SPEED = Double.parseDouble(System.getProperty("oceania.raftLightSpeed", "0.05"));

	private static Mode mode() {
		String name = System.getProperty("oceania.raftLight", "on").toUpperCase(Locale.ROOT);
		for (Mode m : Mode.values()) {
			if (m.name().equals(name)) return m;
		}
		Oceania.LOGGER.warn("[raft] -Doceania.raftLight={} is not one of on, moored, off; using on", name);
		return Mode.ON;
	}

	private static long placements, removals, refusals, disowned, nanos, peakTickNanos;

	private static long workTicks;

	private static long peakAtWorkTick, peakUpdates;
	private static boolean reported;

	public static void resetCounters() {
		placements = removals = refusals = disowned = nanos = peakTickNanos = workTicks = peakAtWorkTick = peakUpdates = 0;
		reported = false;
	}

	public static String costReport() {
		return String.format(Locale.ROOT,
			"mode=%s placed=%d removed=%d refused=%d disowned=%d over %d tick(s) that set a block; %.2f ms total in setBlock, "
				+ "%.3f ms mean per such tick, %.3f ms peak (on tick %d of those, which set %d block(s))",
			MODE.name().toLowerCase(Locale.ROOT), placements, removals, refusals, disowned, workTicks,
			nanos / 1.0E6, workTicks == 0 ? 0.0 : nanos / 1.0E6 / workTicks, peakTickNanos / 1.0E6,
			peakAtWorkTick, peakUpdates);
	}

	private final RaftEntity raft;

	private final Map<BlockPos, Integer> placed = new HashMap<>();

	private final Map<BlockPos, Integer> desired = new HashMap<>();
	private boolean moored = true;

	RaftWorldLight(RaftEntity raft) {
		this.raft = raft;
	}

	public Set<BlockPos> owned() {
		return placed.keySet();
	}

	void tick() {
		if (!(raft.level() instanceof ServerLevel level)) return;
		desired.clear();
		if (wanted()) {
			for (RaftBlocks.Emitter e : raft.blocks().emitters()) {
				BlockPos pos = e.pos();

				BlockPos at = BlockPos.containing(raft.toWorld(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));

				desired.merge(at, e.level(), Math::max);
			}
		}
		if (placed.isEmpty() && desired.isEmpty()) return;
		long started = System.nanoTime();

		long before = placements + removals;

		Iterator<Map.Entry<BlockPos, Integer>> owned = placed.entrySet().iterator();
		while (owned.hasNext()) {
			Map.Entry<BlockPos, Integer> mine = owned.next();
			Integer want = desired.get(mine.getKey());
			if (want != null && want.intValue() == mine.getValue().intValue()) continue;
			if (take(level, mine.getKey())) owned.remove();
		}
		for (Map.Entry<BlockPos, Integer> want : desired.entrySet()) {
			if (placed.containsKey(want.getKey())) continue;
			if (give(level, want.getKey(), want.getValue())) placed.put(want.getKey().immutable(), want.getValue());
		}
		long spent = System.nanoTime() - started;
		if (placements + removals != before) {
			workTicks++;
			nanos += spent;
			if (spent > peakTickNanos) {
				peakTickNanos = spent;
				peakAtWorkTick = workTicks;
				peakUpdates = placements + removals - before;
			}
		}
	}

	private boolean wanted() {
		if (MODE == Mode.OFF || raft.blocks().isEmpty()) return false;
		if (MODE == Mode.ON) return true;

		double speed = raft.getDeltaMovement().horizontalDistance();
		moored = speed < (moored ? MOORED_SPEED : MOORED_SPEED * 0.5);
		return moored;
	}

	private boolean take(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) return false;
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.LIGHT)) {

			disowned++;
			return true;
		}
		BlockState back = state.getValue(LightBlock.WATERLOGGED)
			? Blocks.WATER.defaultBlockState()
			: Blocks.AIR.defaultBlockState();
		level.setBlock(pos, back, FLAGS, 512);
		removals++;
		return true;
	}

	private boolean give(ServerLevel world, BlockPos pos, int level) {
		if (!world.isLoaded(pos)) return false;
		BlockState state = world.getBlockState(pos);
		boolean water = state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0;
		if (!state.isAir() && !water) {

			refusals++;
			return false;
		}
		BlockState light = Blocks.LIGHT.defaultBlockState()
			.setValue(LightBlock.LEVEL, Math.min(level, LightBlock.MAX_LEVEL))
			.setValue(LightBlock.WATERLOGGED, water);
		if (!world.setBlock(pos, light, FLAGS, 512)) return false;
		placements++;
		if (!reported) {
			reported = true;

			Oceania.LOGGER.info("[raft] world lighting live: raft {} put light {} at {} into {}, {} emitter(s) on the deck",
				raft.getId(), light.getValue(LightBlock.LEVEL), pos.toShortString(),
				water ? "water" : "air", raft.blocks().emitters().size());
		}
		return true;
	}

	void clear() {
		if (!(raft.level() instanceof ServerLevel level)) return;
		placed.keySet().removeIf(pos -> take(level, pos));
	}

	void save(ValueOutput output) {
		if (placed.isEmpty()) return;
		int[] flat = new int[placed.size() * 4];
		int i = 0;
		for (Map.Entry<BlockPos, Integer> e : placed.entrySet()) {
			flat[i++] = e.getKey().getX();
			flat[i++] = e.getKey().getY();
			flat[i++] = e.getKey().getZ();
			flat[i++] = e.getValue();
		}
		output.putIntArray("WorldLights", flat);
	}

	void load(ValueInput input) {
		placed.clear();
		input.getIntArray("WorldLights").ifPresent(flat -> {
			for (int i = 0; i + 3 < flat.length; i += 4) {
				placed.put(new BlockPos(flat[i], flat[i + 1], flat[i + 2]), flat[i + 3]);
			}
		});
	}
}
