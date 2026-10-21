package com.oceania.worldgen.field;

import net.minecraft.util.Mth;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.Oceania;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public record FieldFunction(Output output) implements DensityFunction {

	public static final Identifier TYPE_ID = Oceania.id("field");
	public static final Identifier SEED_ID = Oceania.id("field_seed");

	public enum Output implements StringRepresentable {
		FLOOR("floor", -720, 400),
		CONTINENTS("continents", -1, 1),
		EROSION("erosion", -1, 1),
		WEIRDNESS("weirdness", -1, 1),
		LAND("land", 0, 1),
		CORE("core", 0, 1),
		TYPE("type", 0, 4),
		AGE("age", -1, 1),
		TRENCH("trench", 0, 1),
		RIDGE("ridge", 0, 1),
		CLIFF("cliff", 0, 1),

		WALL("wall", 0, 1),

		CAVE("cave", 0, 1),

		SEA_CAVE("sea_cave", 0, 1),

		BLUE_HOLE("blue_hole", 0, 1),

		SEAL("seal", -64, 256),

		CAVE_AIR("cave_air", -64, 256),

		WET("wet", 0, 1),

		WET_EDGE("wet_edge", 0, 1),

		CAVE_BIOMES("cave_biomes", 0, 1),

		CAVE_DEPTH_SCALE("cave_depth_scale", 1.0 / 128, 1.0),

		VEINS("veins", 0, 1);

		public static final Codec<Output> CODEC = StringRepresentable.fromEnum(Output::values);
		private final String name;
		private final double min, max;

		Output(String name, double min, double max) {
			this.name = name;
			this.min = min;
			this.max = max;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final MapCodec<FieldFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Output.CODEC.fieldOf("output").forGetter(FieldFunction::output)
	).apply(i, FieldFunction::new));

	public static void register() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, TYPE_ID, MAP_CODEC);
	}

	public static long seedFrom(PositionalRandomFactory fieldFactory) {
		return fieldFactory.fromHashOf(SEED_ID).nextLong();
	}

	public static long seedFrom(RandomState randomState) {
		return seedFrom(randomState.getOrCreateRandomFactory(TYPE_ID));
	}

	public static OceanFields fields(RandomState randomState) {
		return OceanFields.of(seedFrom(randomState));
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {

		OceanFields fields = OceanFields.of(seedFrom(context.createRandom(TYPE_ID).forkPositional()));
		return new Sampler(fields, output);
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.of((float) output.min, (float) output.max);
	}

	@Override
	public int domainAxes() {
		return AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<FieldFunction> codec() {
		return MAP_CODEC;
	}

	public record Sampler(OceanFields fields, Output output) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
			int sizeY = volume.sizeY();
			for (int z = 0; z < volume.sizeZ(); z++) {
				int blockZ = volume.blockZ(z);
				for (int x = 0; x < volume.sizeX(); x++) {
					outputBuffer.setRange(volume.indexUnchecked(x, 0, z), sizeY, value(volume.blockX(x), blockZ));
				}
			}
		}

		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			return value(blockX, blockZ);
		}

		private float value(int x, int z) {
			OceanFields.Sample s = fields.sample(x, z);
			return (float) switch (output) {
				case FLOOR -> s.floorY;
				case CONTINENTS -> s.continents();
				case EROSION -> s.erosion();
				case WEIRDNESS -> s.weirdness();
				case LAND -> s.landMask;
				case CORE -> s.core ? 1 : 0;
				case TYPE -> s.type;
				case AGE -> s.age;
				case TRENCH -> s.trenchT;
				case RIDGE -> s.ridgeT;
				case CLIFF -> s.cliff;
				case WALL -> s.wall;
				case CAVE -> s.caveMask;
				case SEA_CAVE -> s.seaCave;
				case BLUE_HOLE -> s.blueHole;
				case SEAL -> s.seal;
				case CAVE_AIR -> s.caveAir;
				case WET -> s.wet;
				case WET_EDGE -> s.wetEdge;
				case CAVE_BIOMES -> OceanFields.CAVE_BIOMES ? 1 : 0;

				case CAVE_DEPTH_SCALE -> 1.0 / Mth.lerp(s.wet, OceanFields.CAVE_DEPTH_SCALE, OceanFields.WET_CAVE_SCALE);
				case VEINS -> OceanFields.VEINS ? 1 : 0;
			};
		}
	}
}
