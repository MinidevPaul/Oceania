package com.oceania.registry;

import com.oceania.Oceania;
import com.oceania.item.CrabSpawnEggItem;
import com.oceania.item.JellyfishSpawnEggItem;
import com.oceania.item.TurtleArmor;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Function;

public final class OceaniaItems {

	public static final SpawnEggItem SHARK_SPAWN_EGG = register("shark_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(OceaniaEntities.SHARK)));
	public static final SpawnEggItem WHALE_SPAWN_EGG = register("whale_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(OceaniaEntities.WHALE)));
	public static final CrabSpawnEggItem CRAB_SPAWN_EGG = register("crab_spawn_egg", p -> new CrabSpawnEggItem(p.spawnEgg(OceaniaEntities.REEF_CRAB)));
	public static final SpawnEggItem GIANT_SQUID_SPAWN_EGG = register("giant_squid_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(OceaniaEntities.GIANT_SQUID)));
	public static final JellyfishSpawnEggItem JELLYFISH_SPAWN_EGG = register("jellyfish_spawn_egg", p -> new JellyfishSpawnEggItem(p.spawnEgg(OceaniaEntities.MOON_JELLY)));

	public static final Item TURTLE_CHESTPLATE = register("turtle_chestplate", p -> TurtleArmor.piece(ArmorType.CHESTPLATE, p));
	public static final Item TURTLE_LEGGINGS = register("turtle_leggings", p -> TurtleArmor.piece(ArmorType.LEGGINGS, p));
	public static final Item TURTLE_BOOTS = register("turtle_boots", p -> TurtleArmor.piece(ArmorType.BOOTS, p));

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Oceania.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static void init() {

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output ->
			output.insertAfter(Items.TURTLE_HELMET, TURTLE_CHESTPLATE, TURTLE_LEGGINGS, TURTLE_BOOTS));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(SHARK_SPAWN_EGG);
			output.accept(WHALE_SPAWN_EGG);
			output.accept(CRAB_SPAWN_EGG);
			output.accept(GIANT_SQUID_SPAWN_EGG);
			output.accept(JELLYFISH_SPAWN_EGG);
		});
		registerDispenserBehaviour();
	}

	private static void registerDispenserBehaviour() {
		for (SpawnEggItem egg : new SpawnEggItem[]{SHARK_SPAWN_EGG, WHALE_SPAWN_EGG, CRAB_SPAWN_EGG,
				GIANT_SQUID_SPAWN_EGG, JELLYFISH_SPAWN_EGG}) {
			DispenserBlock.registerBehavior(egg, new DefaultDispenseItemBehavior() {
				@Override
				public ItemStack execute(BlockSource source, ItemStack stack) {
					Direction facing = source.state().getValue(DispenserBlock.FACING);
					BlockPos target = source.pos().relative(facing);
					try {

						EntityType<?> type = egg instanceof CrabSpawnEggItem
							? CrabSpawnEggItem.crabFor(source.level(), target)
							: SpawnEggItem.getType(stack);
						if (type == null) return ItemStack.EMPTY;
						type.spawn(source.level(), stack, null, target,
							EntitySpawnReason.DISPENSER, facing != Direction.UP, false);
					} catch (Exception exception) {
						Oceania.LOGGER.error("Error dispensing {} at {}",
							BuiltInRegistries.ITEM.getKey(egg), source.pos(), exception);
						return ItemStack.EMPTY;
					}
					stack.shrink(1);
					source.level().gameEvent(null, GameEvent.ENTITY_PLACE, source.pos());
					return stack;
				}
			});
		}
	}
}
