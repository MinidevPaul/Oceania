package com.oceania.registry;

import com.oceania.Oceania;
import com.oceania.raft.RaftHelmBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

public final class OceaniaBlocks {
	public static final Block RAFT_HELM = register("raft_helm", RaftHelmBlock::new,

		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(4.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());

	private OceaniaBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Oceania.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
