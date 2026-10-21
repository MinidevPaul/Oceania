package com.oceania.debug;

import com.oceania.Oceania;
import com.oceania.village.HutVillage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class TradeSmoke {
	private static final boolean ENABLED = "trades".equals(System.getProperty("oceania.smoke", ""));
	private static final int SAMPLES = 24;

	private static final int CHART_SAMPLES = 3;

	private TradeSmoke() {
	}

	public static void init() {
		if (!ENABLED) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				run(server.overworld());
			} catch (Exception e) {
				Oceania.LOGGER.error("[trades] failed", e);
			} finally {
				server.halt(false);
			}
		});
	}

	private static void run(ServerLevel level) {
		Oceania.LOGGER.info("[trades] villager type for {}: {}", HutVillage.ISLAND_JUNGLE.identifier(),
			VillagerType.BY_BIOME.get(HutVillage.ISLAND_JUNGLE));
		int levelsChecked = 0;
		int uneven = 0;
		for (Holder.Reference<VillagerProfession> profession : BuiltInRegistries.VILLAGER_PROFESSION.listElements().toList()) {
			if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) continue;
			for (int tier = 1; tier <= 5; tier++) {
				Map<String, Integer> tally = new TreeMap<>();
				Map<Integer, Integer> sizes = new TreeMap<>();
				long start = System.nanoTime();
				int samples = profession.is(VillagerProfession.CARTOGRAPHER) ? CHART_SAMPLES : SAMPLES;
				for (int sample = 0; sample < samples; sample++) {
					Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
					if (villager == null) throw new IllegalStateException("no villager");
					villager.setVillagerData(villager.getVillagerData()
						.withType(level.registryAccess(), VillagerType.JUNGLE)
						.withProfession(profession)
						.withLevel(tier));
					MerchantOffers offers = villager.getOffers();
					sizes.merge(offers.size(), 1, Integer::sum);
					for (MerchantOffer offer : offers) tally.merge(describe(offer), 1, Integer::sum);
					villager.discard();
				}
				long ms = (System.nanoTime() - start) / 1_000_000L;
				String name = profession.key().identifier().getPath();
				Oceania.LOGGER.info("[trades] {} level {}: offers per villager {} ({} villagers, {} ms)", name, tier, sizes, samples, ms);
				for (Map.Entry<String, Integer> e : tally.entrySet()) {
					Oceania.LOGGER.info(String.format(Locale.ROOT, "[trades]     %3d x %s", e.getValue(), e.getKey()));
				}
				levelsChecked++;
				if (sizes.size() > 1) uneven++;
			}
		}
		Oceania.LOGGER.info("[trades] done: {} profession levels checked, {} with an uneven offer count", levelsChecked, uneven);
	}

	private static String describe(MerchantOffer offer) {
		StringBuilder sb = new StringBuilder();
		sb.append(cost(offer.getItemCostA()));
		offer.getItemCostB().ifPresent(b -> sb.append(" + ").append(cost(b)));
		sb.append(" -> ").append(stack(offer.getResult()));
		sb.append(String.format(Locale.ROOT, " (%d/%d, x%.2f)", offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier()));
		return sb.toString();
	}

	private static String cost(ItemCost cost) {
		return cost.count() + " " + cost.item().unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
	}

	private static String stack(ItemStack stack) {
		StringBuilder sb = new StringBuilder();
		sb.append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
		if (stack.has(DataComponents.POTION_CONTENTS)) sb.append(" potion=").append(stack.get(DataComponents.POTION_CONTENTS).potion().flatMap(Holder::unwrapKey).map(k -> k.identifier().getPath()).orElse("?"));
		if (stack.has(DataComponents.STORED_ENCHANTMENTS)) sb.append(" stored=").append(stack.get(DataComponents.STORED_ENCHANTMENTS));
		if (stack.has(DataComponents.ENCHANTMENTS) && !stack.get(DataComponents.ENCHANTMENTS).isEmpty()) sb.append(" ench=").append(stack.get(DataComponents.ENCHANTMENTS));
		if (stack.has(DataComponents.DYED_COLOR)) sb.append(" dyed");
		if (stack.has(DataComponents.MAP_ID)) sb.append(" map#").append(stack.get(DataComponents.MAP_ID).id());
		if (stack.has(DataComponents.ITEM_NAME)) sb.append(" name=").append(stack.get(DataComponents.ITEM_NAME).getString());
		return sb.toString();
	}
}
