package com.oceania.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.mob.SharkEntity;
import com.oceania.spawn.GiantSquidSpawner;
import com.oceania.spawn.WhaleSpawner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class MobCensus {
	private static final int DEFAULT_RADIUS = 128;

	private MobCensus() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("mobs")
					.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS))
					.then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int run(CommandSourceStack src, int radius) {
		ServerLevel level = src.getLevel();
		AABB box = AABB.ofSize(src.getPosition(), radius * 2, radius * 2, radius * 2);

		Map<String, Integer> byName = new TreeMap<>();
		Map<MobCategory, Integer> byCategory = new LinkedHashMap<>();
		int total = 0;

		for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Mob)) {
			MobCategory category = entity.getType().getCategory();
			byCategory.merge(category, 1, Integer::sum);
			String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

			if (entity instanceof SharkEntity shark) name += " (" + shark.getVariant().getSerializedName() + ")";
			byName.merge(name, 1, Integer::sum);
			total++;
		}

		StringBuilder sb = new StringBuilder(String.format("mobs within %d blocks: %d", radius, total));
		boolean overReach = false;
		for (Map.Entry<MobCategory, Integer> e : byCategory.entrySet()) {
			int despawn = e.getKey().getDespawnDistance();

			sb.append(String.format("%n  %-22s %3d alive | despawns past %d blocks | cap about %d, over loaded chunks",
				e.getKey().getName(), e.getValue(), despawn, capFor(level, e.getKey())));
			if (radius > despawn) overReach = true;
		}
		sb.append("\n  --");
		byName.forEach((name, count) -> sb.append(String.format("%n  %-22s %3d", name, count)));
		if (overReach) {
			sb.append(String.format("%n  NOTE: your radius of %d is larger than a despawn distance above. Nothing of"
				+ " that category%n  can exist in the extra volume, so a low count there is the rule, not scarcity.", radius));
		}
		sb.append("\n  Shark species are only a reading of natural spawning in water you have NOT summoned into:"
			+ "\n  a /summon with explicit Variant NBT keeps that species anywhere, and one without it, on land or"
			+ "\n  in any biome with no entry, picks uniformly at random rather than defaulting to reef.");
		sb.append("\n  Alive, not supply: /oceania spawn measures whether positions are legal, which stays high even"
			+ "\n  when a category is full and nothing more can spawn. Compare the two when something is missing.");

		sb.append("\n  ").append(WhaleSpawner.summary());
		sb.append("\n  ").append(GiantSquidSpawner.summary());
		src.sendSystemMessage(Component.literal(sb.toString()));
		return 1;
	}

	private static int capFor(ServerLevel level, MobCategory category) {
		int loaded = level.getChunkSource().getLoadedChunksCount();
		return category.getMaxInstancesPerChunk() * Math.max(1, loaded) / 289;
	}
}
