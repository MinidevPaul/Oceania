package com.oceania.worldgen;

import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

public final class OceaniaFluidPicker implements Aquifer.FluidPicker {
	public static final boolean DRY = Boolean.getBoolean("oceania.dry");

	private static final int NO_FLUID = DimensionType.MIN_Y * 2;

	private final Aquifer.FluidStatus lava;
	private final Aquifer.FluidStatus sea;
	private final Aquifer.FluidStatus air;
	private final Aquifer.FluidStatus dry;
	private final Aquifer.FluidStatus[] lavaSea;
	private final int lavaLevel;
	private final int minY;
	private final OceanFields fields;

	public OceanFields oceania$fields() {
		return this.fields;
	}

	private OceaniaFluidPicker(int lavaLevel, int minY, int height, Aquifer.FluidStatus sea, OceanFields fields) {
		this.lavaLevel = lavaLevel;
		this.minY = minY;
		this.lava = new Aquifer.FluidStatus(lavaLevel, Blocks.LAVA.defaultBlockState());
		this.sea = sea;
		this.air = new Aquifer.FluidStatus(NO_FLUID, Blocks.AIR.defaultBlockState());

		this.dry = new Aquifer.FluidStatus(NO_FLUID, Blocks.WATER.defaultBlockState());
		this.lavaSea = new Aquifer.FluidStatus[height + 1];
		this.fields = fields;
	}

	public static Aquifer.FluidPicker create(Aquifer.FluidPicker vanilla, NoiseGeneratorSettings settings, RandomState randomState) {
		int minY = settings.noiseSettings().minY();
		if (minY >= -64 && !DRY) {
			return vanilla;
		}
		int lavaLevel = Math.min(-54, minY + 50);
		OceanFields fields = FieldFunction.fields(randomState);
		return new OceaniaFluidPicker(lavaLevel, minY, settings.noiseSettings().height(),
			new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid()), fields);
	}

	@Override
	public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
		if (DRY) return air;
		if (y < lavaLevel) return lava;

		double[] col = fields.columnValues(x, z);
		double crater = col[1];
		if (y < crater) {
			return new Aquifer.FluidStatus((int) crater, Blocks.LAVA.defaultBlockState());
		}
		double floor = col[0];

		int seaTop = (int) Math.floor(floor - OceanFields.LAVA_SEA_DEPTH);
		if (y < seaTop) return lavaSea(seaTop);

		if (y < floor - col[2]) {
			return dry;
		}
		return sea;
	}

	private Aquifer.FluidStatus lavaSea(int top) {
		int i = top - minY;
		if (i < 0) return lava;
		if (i >= lavaSea.length) i = lavaSea.length - 1;
		Aquifer.FluidStatus s = lavaSea[i];
		if (s == null) {
			s = new Aquifer.FluidStatus(top, Blocks.LAVA.defaultBlockState());
			lavaSea[i] = s;
		}
		return s;
	}

	public boolean isSea(Aquifer.FluidStatus status) {
		return status == sea;
	}

	public double floorAt(int x, int z) {
		return fields.floorAt(x, z);
	}
}
