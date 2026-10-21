package com.oceania.raft;

import com.oceania.Oceania;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.stream.Collectors;

public final class RaftMoveTrace {
	public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("oceania.moveTrace", "false"));
	private static final int RING_SIZE = 500;
	private static final int DUMP_COOLDOWN = 100;

	public static volatile boolean armed;

	private static final ArrayDeque<String> RING = new ArrayDeque<>();
	private static int tick;
	private static int lastDump = -DUMP_COOLDOWN;

	private RaftMoveTrace() {
	}

	public static void tick() {
		tick++;
	}

	public static int currentTick() {
		return tick;
	}

	public static synchronized void record(String line) {
		if (RING.size() >= RING_SIZE) RING.pollFirst();
		RING.addLast("t" + tick + " " + line);
	}

	public static synchronized void dump(String reason) {
		if (tick - lastDump < DUMP_COOLDOWN) return;
		lastDump = tick;
		Oceania.LOGGER.warn("[movetrace] {} at t{}; the last {} events follow\n{}\n[movetrace] end", reason, tick, RING.size(), String.join("\n", RING));
	}

	public static String frames(int limit) {
		return StackWalker.getInstance().walk(stream -> stream
			.skip(1)
			.filter(f -> !f.getClassName().startsWith("com.oceania.raft.RaftMoveTrace"))
			.filter(f -> !(f.getClassName().equals("net.minecraft.world.entity.Entity") && (f.getMethodName().startsWith("setPos") || f.getMethodName().equals("moveTo") || f.getMethodName().equals("absMoveTo"))))
			.limit(limit)
			.map(RaftMoveTrace::frame)
			.collect(Collectors.joining(" < ")));
	}

	private static String frame(StackWalker.StackFrame f) {
		String cls = f.getClassName();
		String shortName = cls.substring(cls.lastIndexOf('.') + 1);
		String owner = cls.startsWith("net.minecraft.") ? shortName
			: cls.startsWith("com.oceania.") ? "oceania:" + shortName
			: cls.startsWith("me.jellysquid.mods.lithium.") || cls.startsWith("net.caffeinemc.mods.lithium.") ? "lithium:" + shortName
			: cls.startsWith("org.embeddedt.modernfix.") ? "modernfix:" + shortName
			: cls;
		return owner + "." + f.getMethodName() + ":" + f.getLineNumber();
	}

	public static boolean watchesServerSide(net.minecraft.server.level.ServerPlayer player) {
		if (player instanceof RaftRider rider && rider.oceania$isOnRaft(player.level().getGameTime())) return true;
		if (player.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) return true;
		return !player.level().getEntitiesOfClass(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class, player.getBoundingBox().inflate(4.0)).isEmpty();
	}

	public static String fmt(Vec3 v) {
		return String.format("(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
	}

	public static String fmt(AABB b) {
		return String.format("[%.3f %.3f %.3f -> %.3f %.3f %.3f]", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
	}
}
