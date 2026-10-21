package com.oceania.debug;

import com.mojang.authlib.GameProfile;
import com.oceania.Oceania;
import com.oceania.item.TurtleArmor;
import com.oceania.registry.OceaniaItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.Locale;
import java.util.UUID;

public final class TurtleMineSmoke {
	private static final boolean ENABLED = "turtlemine".equals(System.getProperty("oceania.smoke", ""));
	private static int failures;

	private TurtleMineSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(TurtleMineSmoke::run);
	}

	private static final class Subject extends ServerPlayer {
		boolean submerged;
		boolean grounded = true;

		Subject(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name), ClientInformation.createDefault());
		}

		@Override
		public boolean isEyeInFluid(TagKey<Fluid> tag) {
			return submerged;
		}

		@Override
		public boolean onGround() {
			return grounded;
		}
	}

	private static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockState stone = Blocks.STONE.defaultBlockState();
		Holder<Enchantment> aqua = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.AQUA_AFFINITY);
		ItemStack shellAqua = new ItemStack(Items.TURTLE_HELMET);
		shellAqua.enchant(aqua, 1);

		Subject bare = subject(level, "bare");
		Subject three = subject(level, "three_pieces", new ItemStack(Items.TURTLE_HELMET), new ItemStack(OceaniaItems.TURTLE_CHESTPLATE), new ItemStack(OceaniaItems.TURTLE_LEGGINGS));
		Subject full = subject(level, "full_set", new ItemStack(Items.TURTLE_HELMET), new ItemStack(OceaniaItems.TURTLE_CHESTPLATE), new ItemStack(OceaniaItems.TURTLE_LEGGINGS), new ItemStack(OceaniaItems.TURTLE_BOOTS));
		Subject fullAqua = subject(level, "full_set_aqua_affinity", shellAqua, new ItemStack(OceaniaItems.TURTLE_CHESTPLATE), new ItemStack(OceaniaItems.TURTLE_LEGGINGS), new ItemStack(OceaniaItems.TURTLE_BOOTS));
		Subject bareAqua = subject(level, "bare_aqua_affinity", shellAqua.copy());

		for (Subject s : new Subject[]{fullAqua, bareAqua}) {
			EnchantmentHelper.forEachModifier(s.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD,
				(attribute, modifier) -> s.getAttributes().getInstance(attribute).addTransientModifier(modifier));
		}
		check(TurtleArmor.piecesWorn(three) == 3, "three_pieces wears 3");
		check(TurtleArmor.fullSet(full) && TurtleArmor.fullSet(fullAqua), "full sets wear 4");
		check(!TurtleArmor.fullSet(bareAqua), "bare with the shell alone is not a full set");

		speed(bare, stone, false, true, 1.0, "bare on land");
		speed(bare, stone, true, true, 0.2, "bare submerged on the floor (vanilla 0.2)");
		speed(bare, stone, true, false, 0.04, "bare submerged floating (vanilla 0.2 / 5): the two branches are live");
		speed(bareAqua, stone, true, true, 1.0, "bare with Aqua Affinity submerged (vanilla 1): the enchantment is applied");
		speed(three, stone, true, false, 0.04, "three pieces submerged floating: full vanilla penalty");
		speed(full, stone, false, true, 1.0, "full set on land");
		speed(full, stone, false, false, 0.2, "full set jumping on land: the jump penalty stays out of water");
		speed(full, stone, true, true, 1.0, "full set submerged on the floor: land speed");
		speed(full, stone, true, false, 1.0, "full set submerged floating: land speed");
		speed(fullAqua, stone, true, false, 1.0, "full set with Aqua Affinity submerged floating: 1, no stacking");

		Oceania.LOGGER.info("[smoke] DONE failures={}", failures);
		server.halt(false);
	}

	private static Subject subject(ServerLevel level, String name, ItemStack... pieces) {
		Subject s = new Subject(level, name);
		for (ItemStack piece : pieces) {
			s.setItemSlot(s.getEquipmentSlotForItem(piece), piece);
		}
		return s;
	}

	private static void speed(Subject s, BlockState state, boolean submerged, boolean grounded, double expected, String what) {
		s.submerged = submerged;
		s.grounded = grounded;
		float speed = s.getDestroySpeed(state);
		check(Math.abs(speed - expected) < 1e-4, String.format(Locale.ROOT, "%s: destroy speed %.4f, expected %.2f", what, speed, expected));
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
