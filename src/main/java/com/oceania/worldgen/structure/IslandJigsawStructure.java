package com.oceania.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oceania.Oceania;
import com.oceania.worldgen.field.FieldFunction;
import com.oceania.worldgen.field.OceanFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
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

public final class IslandJigsawStructure extends Structure {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("oceania.outposts", "true"));
	private static final List<String> TYPE_NAMES = List.of("none", "barren", "large", "cluster", "guyot");

	public static final MapCodec<IslandJigsawStructure> CODEC = RecordCodecBuilder.<IslandJigsawStructure>mapCodec(i -> i.group(
			settingsCodec(i),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
			Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
			HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
			Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
			Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
			Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases),
			DimensionPadding.CODEC.optionalFieldOf("dimension_padding", DimensionPadding.ZERO).forGetter(s -> s.dimensionPadding),
			LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.APPLY_WATERLOGGING).forGetter(s -> s.liquidSettings),
			Codec.list(Codec.STRING).optionalFieldOf("exclude_island_types", List.of()).forGetter(s -> s.excludeIslandTypes)
		).apply(i, IslandJigsawStructure::new))
		.validate(IslandJigsawStructure::verify);

	private final Holder<StructureTemplatePool> startPool;
	private final Optional<Identifier> startJigsawName;
	private final int maxDepth;
	private final HeightProvider startHeight;
	private final boolean useExpansionHack;
	private final Optional<Heightmap.Types> projectStartToHeightmap;
	private final int maxDistanceFromCenter;
	private final List<PoolAliasBinding> poolAliases;
	private final DimensionPadding dimensionPadding;
	private final LiquidSettings liquidSettings;
	private final List<String> excludeIslandTypes;
	private final boolean[] excluded = new boolean[TYPE_NAMES.size()];

	private IslandJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, Optional<Identifier> startJigsawName,
								  int maxDepth, HeightProvider startHeight, boolean useExpansionHack, Optional<Heightmap.Types> projectStartToHeightmap,
								  int maxDistanceFromCenter, List<PoolAliasBinding> poolAliases, DimensionPadding dimensionPadding,
								  LiquidSettings liquidSettings, List<String> excludeIslandTypes) {
		super(settings);
		this.startPool = startPool;
		this.startJigsawName = startJigsawName;
		this.maxDepth = maxDepth;
		this.startHeight = startHeight;
		this.useExpansionHack = useExpansionHack;
		this.projectStartToHeightmap = projectStartToHeightmap;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.poolAliases = poolAliases;
		this.dimensionPadding = dimensionPadding;
		this.liquidSettings = liquidSettings;
		this.excludeIslandTypes = excludeIslandTypes;
		for (String name : excludeIslandTypes) {
			int idx = TYPE_NAMES.indexOf(name);
			if (idx >= 0) excluded[idx] = true;
		}
	}

	private static DataResult<IslandJigsawStructure> verify(IslandJigsawStructure s) {
		int margin = switch (s.terrainAdaptation()) {
			case NONE -> 0;
			case BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE -> 12;
		};
		if (s.maxDistanceFromCenter + margin > 128) {
			return DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128");
		}
		for (String name : s.excludeIslandTypes) {
			if (!TYPE_NAMES.contains(name)) return DataResult.error(() -> "unknown island type " + name + "; one of " + TYPE_NAMES);
		}
		return DataResult.success(s);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		if (!ENABLED) return Optional.empty();
		ChunkPos chunk = ctx.chunkPos();
		if (excludeIslandTypes.size() > 0) {
			OceanFields fields = FieldFunction.fields(ctx.randomState());
			double[] group = fields.groupAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
			int type = group == null ? OceanFields.TYPE_NONE : (int) group[3];
			if (type >= 0 && type < excluded.length && excluded[type]) {
				Oceania.LOGGER.debug("[outpost] {},{} refused: island type {}", chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), TYPE_NAMES.get(type));
				return Optional.empty();
			}
		}
		int y = startHeight.sample(ctx.random(), new WorldGenerationContext(ctx.chunkGenerator(), ctx.heightAccessor()));
		BlockPos start = new BlockPos(chunk.getMinBlockX(), y, chunk.getMinBlockZ());
		return JigsawPlacement.addPieces(ctx, startPool, startJigsawName, maxDepth, start, useExpansionHack, projectStartToHeightmap,
			new JigsawStructure.MaxDistance(maxDistanceFromCenter), PoolAliasLookup.create(poolAliases, start, ctx.seed()), dimensionPadding, liquidSettings);
	}

	@Override
	public StructureType<?> type() {
		return OceaniaWorldgenStructures.ISLAND_JIGSAW;
	}
}
