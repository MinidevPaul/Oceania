package com.oceania.worldgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;

import java.util.Optional;

public final class FloorMineshaftStructure extends Structure {
	public static final MapCodec<FloorMineshaftStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			MineshaftStructure.Type.CODEC.fieldOf("mineshaft_type").forGetter(s -> s.type),
			IntProviders.codec(0, 1024).fieldOf("floor_depth").forGetter(s -> s.floorDepth),
			Codec.INT.optionalFieldOf("min_y", -600).forGetter(s -> s.minY),
			Codec.intRange(0, 1024).optionalFieldOf("max_floor_spread", 120).forGetter(s -> s.maxFloorSpread)
		).apply(i, FloorMineshaftStructure::new));

	private final MineshaftStructure.Type type;
	private final IntProvider floorDepth;
	private final int minY;
	private final int maxFloorSpread;

	private FloorMineshaftStructure(StructureSettings settings, MineshaftStructure.Type type, IntProvider floorDepth, int minY, int maxFloorSpread) {
		super(settings);
		this.type = type;
		this.floorDepth = floorDepth;
		this.minY = minY;
		this.maxFloorSpread = maxFloorSpread;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		if (!FloorJigsawStructure.ENABLED) return Optional.empty();

		ctx.random().nextDouble();
		ChunkPos chunk = ctx.chunkPos();
		StructurePiecesBuilder builder = new StructurePiecesBuilder();
		WorldgenRandom random = ctx.random();
		MineshaftPieces.MineShaftRoom room = new MineshaftPieces.MineShaftRoom(0, random, chunk.getBlockX(2), chunk.getBlockZ(2), type);
		builder.addPiece(room);
		room.addChildren(room, builder, random);

		BoundingBox box = builder.getBoundingBox();
		ChunkGenerator gen = ctx.chunkGenerator();
		int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
		int[][] columns = {
			{(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2},
			{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()},
		};
		for (int[] c : columns) {
			int floor = gen.getFirstOccupiedHeight(c[0], c[1], Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
			lowest = Math.min(lowest, floor);
			highest = Math.max(highest, floor);
		}
		if (highest - lowest > maxFloorSpread) return Optional.empty();
		int top = lowest - floorDepth.sample(random);
		int offset = top - box.maxY();
		if (box.minY() + offset < minY) {
			offset = minY - box.minY();

			if (box.maxY() + offset > lowest - floorDepth.minInclusive()) return Optional.empty();
		}
		builder.offsetPiecesVertically(offset);

		BlockPos at = new BlockPos(chunk.getMiddleBlockX(), 50 + offset, chunk.getMinBlockZ());
		return Optional.of(new GenerationStub(at, Either.right(builder)));
	}

	@Override
	public StructureType<?> type() {
		return OceaniaWorldgenStructures.FLOOR_MINESHAFT;
	}
}
