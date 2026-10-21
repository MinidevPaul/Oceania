package com.oceania.raft;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RaftItems {

	public static final Item RAFT = register("raft", Blocks.JUNGLE_PLANKS);

	public static final Map<Block, Item> HOUSEBOATS = houseboats();

	private RaftItems() {
	}

	private static Map<Block, Item> houseboats() {
		Map<Block, Item> map = new LinkedHashMap<>();
		map.put(Blocks.OAK_PLANKS, register("raft_oak", Blocks.OAK_PLANKS));
		map.put(Blocks.SPRUCE_PLANKS, register("raft_spruce", Blocks.SPRUCE_PLANKS));
		map.put(Blocks.BIRCH_PLANKS, register("raft_birch", Blocks.BIRCH_PLANKS));
		map.put(Blocks.JUNGLE_PLANKS, RAFT);
		map.put(Blocks.ACACIA_PLANKS, register("raft_acacia", Blocks.ACACIA_PLANKS));
		map.put(Blocks.DARK_OAK_PLANKS, register("raft_dark_oak", Blocks.DARK_OAK_PLANKS));
		map.put(Blocks.MANGROVE_PLANKS, register("raft_mangrove", Blocks.MANGROVE_PLANKS));
		map.put(Blocks.CHERRY_PLANKS, register("raft_cherry", Blocks.CHERRY_PLANKS));
		map.put(Blocks.PALE_OAK_PLANKS, register("raft_pale_oak", Blocks.PALE_OAK_PLANKS));
		map.put(Blocks.POPLAR_PLANKS, register("raft_poplar", Blocks.POPLAR_PLANKS));
		map.put(Blocks.BAMBOO_PLANKS, register("raft_bamboo", Blocks.BAMBOO_PLANKS));
		return Collections.unmodifiableMap(map);
	}

	public static Item houseboat(Block planks) {
		return HOUSEBOATS.getOrDefault(planks, RAFT);
	}

	private static Item register(String name, Block planks) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Oceania.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new RaftItem(planks, new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void init() {

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
			HOUSEBOATS.values().forEach(output::accept));
	}
}
