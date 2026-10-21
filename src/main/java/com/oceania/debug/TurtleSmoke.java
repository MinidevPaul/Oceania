package com.oceania.debug;

import com.oceania.Oceania;
import com.oceania.item.TurtleArmor;
import com.oceania.registry.OceaniaItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TurtleSmoke {
	private static final boolean ENABLED = "turtle".equals(System.getProperty("oceania.smoke", ""));
	private static final int MAX_AIR = 300;

	private static final int WINDOW = 280;
	private static final int WINDOWS = 8;
	private static final int DEPTH = 5;

	private static final class Subject {
		final String name;
		final int pieces;
		final double bonus;
		Mob mob;
		long drained;

		Subject(String name, int pieces, double bonus) {
			this.name = name;
			this.pieces = pieces;
			this.bonus = bonus;
		}
	}

	private static final List<Subject> subjects = new ArrayList<>();
	private static BlockPos site;
	private static int tick = -1;
	private static int failures;

	private TurtleSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(TurtleSmoke::setUp);
		ServerTickEvents.END_SERVER_TICK.register(TurtleSmoke::tick);
	}

	private static void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		site = findDeepWater(level, level.getRespawnData().pos());
		if (site == null) {
			Oceania.LOGGER.error("[smoke] no column of open water {} deep within 96 blocks of spawn {}", DEPTH + 3, level.getRespawnData().pos());
			Oceania.LOGGER.info("[smoke] DONE failures=1");
			server.halt(false);
			return;
		}

		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 1; dz++) level.setChunkForced((site.getX() >> 4) + dx, (site.getZ() >> 4) + dz, true);
		}
		int top = site.getY();
		ItemStack helmet = new ItemStack(Items.TURTLE_HELMET);
		ItemStack chest = new ItemStack(OceaniaItems.TURTLE_CHESTPLATE);
		ItemStack legs = new ItemStack(OceaniaItems.TURTLE_LEGGINGS);
		ItemStack boots = new ItemStack(OceaniaItems.TURTLE_BOOTS);
		ItemStack respiration = new ItemStack(Items.TURTLE_HELMET);
		Holder<Enchantment> resp = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.RESPIRATION);
		respiration.enchant(resp, 3);

		subjects.add(place(level, site, 0, new Subject("bare", 0, 0)));
		subjects.add(place(level, site, 1, new Subject("shell", 1, 1), helmet));
		subjects.add(place(level, site, 2, new Subject("chestplate only", 1, 1), chest));
		subjects.add(place(level, site, 3, new Subject("shell+chest", 2, 3), helmet, chest));
		subjects.add(place(level, site, 4, new Subject("shell+chest+legs", 3, 7), helmet, chest, legs));
		subjects.add(place(level, site, 5, new Subject("full set", 4, 15), helmet, chest, legs, boots));
		subjects.add(place(level, site, 6, new Subject("full set + Respiration III", 4, 18), respiration, chest, legs, boots));
		Oceania.LOGGER.info("[smoke] turtle: {} pigs at y={} under water topped at y={} near {}", subjects.size(), top - DEPTH, top, site);
		tick = 0;
	}

	private static Subject place(ServerLevel level, BlockPos site, int index, Subject subject, ItemStack... pieces) {
		Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		if (pig == null) throw new IllegalStateException("no pig");
		double x = site.getX() + 0.5 + 2 * index;
		double z = site.getZ() + 0.5;
		pig.snapTo(x, site.getY() - DEPTH, z, 0.0F, 0.0F);
		pig.setNoAi(true);
		pig.setNoGravity(true);
		pig.setPermanentlyInvulnerable(true);
		pig.setSilent(true);
		pig.setPersistenceRequired();
		for (ItemStack piece : pieces) {
			EquipmentSlot slot = pig.getEquipmentSlotForItem(piece);
			pig.setItemSlot(slot, piece.copy());
			pig.setDropChance(slot, 0);
		}
		level.addFreshEntity(pig);
		subject.mob = pig;
		return subject;
	}

	private static BlockPos findDeepWater(ServerLevel level, BlockPos from) {
		for (int r = 0; r <= 96; r += 8) {
			for (int dx = -r; dx <= r; dx += 8) {
				for (int dz = -r; dz <= r; dz += 8) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int x = from.getX() + dx;
					int z = from.getZ() + dz;
					int top = topWater(level, x, z);
					if (top == Integer.MIN_VALUE) continue;
					boolean deep = true;
					for (int i = 0; i < 7 && deep; i++) {
						for (int y = top; y >= top - DEPTH - 3 && deep; y--) {
							BlockPos p = new BlockPos(x + 2 * i, y, z);
							deep = level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p).getBlock() == net.minecraft.world.level.block.Blocks.WATER;
						}
					}
					if (deep) return new BlockPos(x, top, z);
				}
			}
		}
		return null;
	}

	private static int topWater(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
		for (int y = 100; y > level.getMinY(); y--) {
			p.setY(y);
			if (!level.getBlockState(p).isAir()) return level.getFluidState(p).is(FluidTags.WATER) ? y : Integer.MIN_VALUE;
		}
		return Integer.MIN_VALUE;
	}

	private static void tick(MinecraftServer server) {
		if (tick < 0) return;
		tick++;
		if (tick == 1) {

			ServerLevel level = server.overworld();
			for (Pig stale : level.getEntitiesOfClass(Pig.class, new AABB(site).inflate(32, 24, 32))) {
				if (subjects.stream().noneMatch(s -> s.mob == stale)) stale.discard();
			}

			for (Subject s : subjects) {
				check(s.mob.isEyeInFluid(FluidTags.WATER), s.name + ": eyes in water at " + s.mob.position());
				check(TurtleArmor.piecesWorn(s.mob) == s.pieces, s.name + ": wearing " + TurtleArmor.piecesWorn(s.mob) + " pieces, expected " + s.pieces);
				s.mob.setAirSupply(MAX_AIR);
				Oceania.LOGGER.info("[smoke] t=1 {}: pieces={} oxygen_bonus={} air={}", s.name, TurtleArmor.piecesWorn(s.mob),
					s.mob.getAttributeValue(Attributes.OXYGEN_BONUS), s.mob.getAirSupply());
			}
			return;
		}
		int t = tick - 1;
		if (t % WINDOW == 0) {
			for (Subject s : subjects) {
				s.drained += MAX_AIR - s.mob.getAirSupply();
				s.mob.setAirSupply(MAX_AIR);
			}
			Oceania.LOGGER.info("[smoke] window {} of {} done", t / WINDOW, WINDOWS);
		}
		if (t < WINDOW * WINDOWS) return;

		long ticks = (long) WINDOW * WINDOWS;
		for (Subject s : subjects) {
			double bonus = s.mob.getAttributeValue(Attributes.OXYGEN_BONUS);
			check(bonus == s.bonus, s.name + ": oxygen bonus " + bonus + ", expected " + s.bonus);
			check(s.mob.isAlive() && s.mob.isEyeInFluid(FluidTags.WATER), s.name + ": still submerged and alive at the end");
			double p = 1.0 / (s.bonus + 1);
			double expected = ticks * p;
			double sd = Math.sqrt(ticks * p * (1 - p));
			double factor = s.drained == 0 ? Double.POSITIVE_INFINITY : (double) ticks / s.drained;
			check(Math.abs(s.drained - expected) <= 4 * sd + 1, String.format(Locale.ROOT,
				"%s: lost %d air in %d ticks, expected %.1f (sd %.1f): air lasts %.1fx, expected %.0fx",
				s.name, s.drained, ticks, expected, sd, factor, s.bonus + 1));
		}

		for (Subject s : subjects) s.mob.discard();
		Oceania.LOGGER.info("[smoke] DONE failures={}", failures);
		server.halt(false);
		tick = -1;
	}

	private static void check(boolean ok, String what) {
		if (ok) {
			Oceania.LOGGER.info("[smoke] PASS {}", what);
		} else {
			failures++;
			Oceania.LOGGER.error("[smoke] FAIL {}", what);
		}
	}
}
