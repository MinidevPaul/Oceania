package com.oceania.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.List;
import java.util.Optional;

public final class FloorJigsawStructure extends Structure {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.cavestructures", "true"));
	public static final MapCodec<FloorJigsawStructure> CODEC = RecordCodecBuilder.<FloorJigsawStructure>mapCodec(i -> i.group(
			settingsCodec(i),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
			Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
			IntProviders.codec(0, 1024).fieldOf("floor_depth").forGetter(s -> s.floorDepth),
			Codec.INT.optionalFieldOf("min_y", -600).forGetter(s -> s.minY),
			Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(s -> s.useExpansionHack),
			Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases),
			DimensionPadding.CODEC.optionalFieldOf("dimension_padding", DimensionPadding.ZERO).forGetter(s -> s.dimensionPadding),
			LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.APPLY_WATERLOGGING).forGetter(s -> s.liquidSettings)
		).apply(i, FloorJigsawStructure::new))
		.validate(FloorJigsawStructure::verifyRange);

	private final Holder<StructureTemplatePool> startPool;
	private final Optional<Identifier> startJigsawName;
	private final int maxDepth;
	private final IntProvider floorDepth;
	private final int minY;
	private final boolean useExpansionHack;
	private final int maxDistanceFromCenter;
	private final List<PoolAliasBinding> poolAliases;
	private final DimensionPadding dimensionPadding;
	private final LiquidSettings liquidSettings;

	private FloorJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, Optional<Identifier> startJigsawName,
								 int maxDepth, IntProvider floorDepth, int minY, boolean useExpansionHack, int maxDistanceFromCenter,
								 List<PoolAliasBinding> poolAliases, DimensionPadding dimensionPadding, LiquidSettings liquidSettings) {
		super(settings);
		this.startPool = startPool;
		this.startJigsawName = startJigsawName;
		this.maxDepth = maxDepth;
		this.floorDepth = floorDepth;
		this.minY = minY;
		this.useExpansionHack = useExpansionHack;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.poolAliases = poolAliases;
		this.dimensionPadding = dimensionPadding;
		this.liquidSettings = liquidSettings;
	}

	private static DataResult<FloorJigsawStructure> verifyRange(FloorJigsawStructure s) {
		int margin = switch (s.terrainAdaptation()) {
			case NONE -> 0;
			case BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE -> 12;
		};
		return s.maxDistanceFromCenter + margin > 128
			? DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128")
			: DataResult.success(s);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		if (!ENABLED) return Optional.empty();
		ChunkPos chunk = ctx.chunkPos();
		int floor = ctx.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
			Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		int y = floor - floorDepth.sample(ctx.random());
		if (y < minY) return Optional.empty();
		BlockPos start = new BlockPos(chunk.getMinBlockX(), y, chunk.getMinBlockZ());
		return JigsawPlacement.addPieces(ctx, startPool, startJigsawName, maxDepth, start, useExpansionHack, Optional.empty(),
			new JigsawStructure.MaxDistance(maxDistanceFromCenter), PoolAliasLookup.create(poolAliases, start, ctx.seed()), dimensionPadding, liquidSettings);
	}

	@Override
	public StructureType<?> type() {
		return OceaniaWorldgenStructures.FLOOR_JIGSAW;
	}
}
