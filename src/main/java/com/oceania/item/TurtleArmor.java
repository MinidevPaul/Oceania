package com.oceania.item;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

public final class TurtleArmor {
	public static final Identifier MATERIAL_ID = Oceania.id("turtle");
	public static final TagKey<Item> TURTLE_ARMOR = TagKey.create(Registries.ITEM, Oceania.id("turtle_armor"));
	public static final Identifier OXYGEN_MODIFIER_ID = Oceania.id("turtle_set_oxygen");
	public static final int DURABILITY_MULTIPLIER = 25;
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, MATERIAL_ID);
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static final ArmorMaterial MATERIAL = new ArmorMaterial(DURABILITY_MULTIPLIER,
		Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 5),
		9, SoundEvents.ARMOR_EQUIP_TURTLE, 0.0F, 0.0F, ItemTags.REPAIRS_TURTLE_HELMET, ASSET);

	private TurtleArmor() {
	}

	public static Item piece(ArmorType type, Item.Properties properties) {
		return new Item(properties.humanoidArmor(MATERIAL, type));
	}

	public static boolean isTurtlePiece(ItemStack stack) {
		return stack.is(TURTLE_ARMOR);
	}

	public static int piecesWorn(LivingEntity entity) {
		int n = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (isTurtlePiece(entity.getItemBySlot(slot))) n++;
		}
		return n;
	}

	public static boolean fullSet(LivingEntity entity) {
		return piecesWorn(entity) == ARMOR_SLOTS.length;
	}

	public static double oxygenBonus(int pieces) {
		return (1 << pieces) - 1;
	}

	public static int airFactor(int pieces) {
		return 1 << pieces;
	}

	public static void init() {
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) apply(entity);
		});
	}

	public static void apply(LivingEntity entity) {
		AttributeInstance air = entity.getAttribute(Attributes.OXYGEN_BONUS);
		if (air == null) return;
		int pieces = piecesWorn(entity);
		if (pieces == 0) {
			air.removeModifier(OXYGEN_MODIFIER_ID);
		} else {
			air.addOrUpdateTransientModifier(new AttributeModifier(OXYGEN_MODIFIER_ID, oxygenBonus(pieces), AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
