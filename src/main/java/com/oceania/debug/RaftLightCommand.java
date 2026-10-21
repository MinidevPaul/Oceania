package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftWorldLight;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RaftLightCommand {
	private static final int DEFAULT_RADIUS = 8;

	private RaftLightCommand() {
	}

	public static void init() {

		ServerLifecycleEvents.SERVER_STARTED.register(server -> RaftWorldLight.resetCounters());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("raftlight")
					.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS))
					.then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int run(CommandSourceStack source, int radius) {
		ServerLevel level = source.getLevel();

		Set<BlockPos> claimed = new HashSet<>();
		int rafts = 0;
		for (Entity e : level.getAllEntities()) {
			if (e instanceof RaftEntity raft) {
				rafts++;
				claimed.addAll(raft.worldLights());
			}
		}

		Vec3 at = source.getEntity() != null ? source.getEntity().position()
			: level.players().stream().findFirst().map(Entity::position).orElse(source.getPosition());
		int centreX = SectionPos.blockToSectionCoord(at.x);
		int centreZ = SectionPos.blockToSectionCoord(at.z);
		List<BlockPos> lights = new ArrayList<>();
		int chunks = scan(level, centreX, centreZ, radius, lights);
		int found = lights.size(), owned = 0;
		List<BlockPos> orphans = new ArrayList<>();
		for (BlockPos pos : lights) {
			if (claimed.contains(pos)) {
				owned++;
			} else if (orphans.size() < 16) {
				orphans.add(pos);
			}
		}

		int missing = 0;
		for (BlockPos pos : claimed) {
			if (Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - centreX) > radius
				|| Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - centreZ) > radius) continue;
			if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.LIGHT)) missing++;
		}

		int unclaimed = found - owned;
		String report = String.format(Locale.ROOT,
			"raftlight r=%d around %d %d: %d light block(s) in %d loaded chunk(s) - %d claimed by %d raft(s), %d unclaimed; %d claimed cell(s) hold no light%s",
			radius, centreX << 4, centreZ << 4, found, chunks, owned, rafts, unclaimed, missing,
			orphans.isEmpty() ? "" : ", first unclaimed " + orphans.getFirst().toShortString());
		Oceania.LOGGER.info("[raftlight] {}", report);
		probe(level, at);
		Oceania.LOGGER.info("[raftlight] cost: {}", RaftWorldLight.costReport());
		if (!orphans.isEmpty()) {
			StringBuilder where = new StringBuilder();
			for (BlockPos pos : orphans) where.append(where.isEmpty() ? "" : " ").append(pos.toShortString());
			Oceania.LOGGER.info("[raftlight] unclaimed at: {}{}", where, unclaimed > orphans.size() ? " ..." : "");
		}
		source.sendSuccess(() -> Component.literal(report + "\ncost: " + RaftWorldLight.costReport()), false);
		return found;
	}

	public static int scan(ServerLevel level, int centreX, int centreZ, int radius, List<BlockPos> into) {
		int chunks = 0;
		for (int cx = centreX - radius; cx <= centreX + radius; cx++) {
			for (int cz = centreZ - radius; cz <= centreZ + radius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				chunks++;
				LevelChunkSection[] sections = chunk.getSections();
				for (int si = 0; si < sections.length; si++) {
					LevelChunkSection section = sections[si];

					if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.LIGHT))) continue;
					int baseX = SectionPos.sectionToBlockCoord(cx);
					int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
					int baseZ = SectionPos.sectionToBlockCoord(cz);
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (section.getBlockState(x, y, z).is(Blocks.LIGHT)) {
									into.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
								}
							}
						}
					}
				}
			}
		}
		return chunks;
	}

	private static void probe(ServerLevel level, Vec3 from) {
		RaftEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity e : level.getAllEntities()) {
			if (e instanceof RaftEntity raft && raft.distanceToSqr(from) < best) {
				best = raft.distanceToSqr(from);
				nearest = raft;
			}
		}
		if (nearest == null) {
			Oceania.LOGGER.info("[raftlight] no raft to probe from");
			return;
		}
		BlockPos centre = nearest.blockPosition();
		StringBuilder out = new StringBuilder();
		for (int d = 0; d <= 15; d += 3) {
			BlockPos at = centre.offset(d, 0, 0);
			out.append(' ').append(d).append("b=").append(level.getBrightness(LightLayer.BLOCK, at));
		}
		Oceania.LOGGER.info("[raftlight] world block light out from raft {} at {}:{} (all zero = the world never heard about the deck lamp)",
			nearest.getId(), centre.toShortString(), out);
	}
}
