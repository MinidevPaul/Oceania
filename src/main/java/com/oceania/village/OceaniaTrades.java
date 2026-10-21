package com.oceania.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.Oceania;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class OceaniaTrades {
	private OceaniaTrades() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Oceania.id("pick_one"), PickOneFunction.MAP_CODEC);
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Oceania.id("regional_chart"), RegionalChartFunction.MAP_CODEC);
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Oceania.id("villager_roll"), VillagerRollCondition.MAP_CODEC);
	}

	public static final class PickOneFunction extends LootItemConditionalFunction {
		public static final MapCodec<PickOneFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
			.and(Item.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("items").forGetter(f -> f.items))
			.apply(i, PickOneFunction::new));

		private final List<Holder<Item>> items;

		private PickOneFunction(Optional<Holder<LootItemCondition>> condition, List<Holder<Item>> items) {
			super(condition);
			this.items = items;
		}

		@Override
		public MapCodec<PickOneFunction> codec() {
			return MAP_CODEC;
		}

		@Override
		protected ItemStack run(ItemStack stack, LootContext context) {
			return stack.transmuteCopy(this.items.get(context.getRandom().nextInt(this.items.size())).value());
		}
	}

	public record VillagerRollCondition(int choices, int choice, String salt) implements LootItemCondition {
		public static final MapCodec<VillagerRollCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.intRange(1, 1024).fieldOf("choices").forGetter(VillagerRollCondition::choices),
			Codec.intRange(0, 1023).fieldOf("choice").forGetter(VillagerRollCondition::choice),
			Codec.STRING.optionalFieldOf("salt", "").forGetter(VillagerRollCondition::salt)
		).apply(i, VillagerRollCondition::new));

		@Override
		public MapCodec<VillagerRollCondition> codec() {
			return MAP_CODEC;
		}

		@Override
		public Set<ContextKey<?>> getReferencedContextParams() {
			return Set.of(LootContextParams.THIS_ENTITY);
		}

		@Override
		public boolean test(LootContext context) {
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			int roll = entity == null
				? context.getRandom().nextInt(this.choices)
				: Math.floorMod(Objects.hash(entity.getUUID(), this.salt), this.choices);
			return roll == this.choice;
		}
	}

	public static final class RegionalChartFunction extends LootItemConditionalFunction {
		public static final MapCodec<RegionalChartFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
			.and(Codec.BYTE.optionalFieldOf("scale", (byte) 4).forGetter(f -> f.scale))
			.apply(i, RegionalChartFunction::new));

		private final byte scale;

		private RegionalChartFunction(Optional<Holder<LootItemCondition>> condition, byte scale) {
			super(condition);
			this.scale = scale;
		}

		@Override
		public MapCodec<RegionalChartFunction> codec() {
			return MAP_CODEC;
		}

		@Override
		public Set<ContextKey<?>> getReferencedContextParams() {
			return Set.of(LootContextParams.ORIGIN);
		}

		@Override
		protected ItemStack run(ItemStack stack, LootContext context) {
			Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
			if (origin == null || !stack.is(Items.FILLED_MAP)) return stack;
			MapItem.applyNewSavedData(context.getLevel(), stack, Mth.floor(origin.x), Mth.floor(origin.z), this.scale, true, false);
			return stack;
		}
	}
}
