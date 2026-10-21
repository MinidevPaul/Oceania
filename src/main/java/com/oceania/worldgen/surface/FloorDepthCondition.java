package com.oceania.worldgen.surface;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.Oceania;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

public record FloorDepthCondition(int depth, int blend) implements MaterialCondition {
	public static final MapCodec<FloorDepthCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.INT.fieldOf("depth").forGetter(FloorDepthCondition::depth),
		Codec.INT.optionalFieldOf("blend", 0).forGetter(FloorDepthCondition::blend)
	).apply(i, FloorDepthCondition::new));

	public static void register() {
		Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, Oceania.id("floor_depth"), CODEC);
	}

	@Override
	public MapCodec<FloorDepthCondition> codec() {
		return CODEC;
	}

	@Override
	public ConditionEvaluator compile(MaterialRuleContext ctx) {
		OceanFields fields = OceanFields.of(FieldFunction.seedFrom(ctx.getOrCreateRandomFactory(FieldFunction.TYPE_ID)));
		return new ConditionEvaluator() {
			private int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE, floor;

			@Override
			public boolean test() {
				int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
				if (x != lastX || z != lastZ) {
					lastX = x;
					lastZ = z;
					floor = Mth.floor(fields.floorAt(x, z));
				}
				int below = floor - y;
				if (below >= depth + blend) return true;
				if (below < depth) return false;
				return hash01(x, y, z) * blend < below - depth;
			}
		};
	}

	private static double hash01(int x, int y, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
