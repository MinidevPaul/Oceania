package com.oceania.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

import java.util.List;
import java.util.Optional;

public final class FloorStrongholdStructure extends Structure {
	public static final MapCodec<FloorStrongholdStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			IntProviders.codec(0, 1024).fieldOf("floor_depth").forGetter(s -> s.floorDepth),
			Codec.INT.optionalFieldOf("min_y", -600).forGetter(s -> s.minY)
		).apply(i, FloorStrongholdStructure::new));

	private final IntProvider floorDepth;
	private final int minY;

	private FloorStrongholdStructure(StructureSettings settings, IntProvider floorDepth, int minY) {
		super(settings);
		this.floorDepth = floorDepth;
		this.minY = minY;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		if (!FloorJigsawStructure.ENABLED) return Optional.empty();
		ChunkPos chunk = ctx.chunkPos();
		int floor = ctx.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
			Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		int top = floor - floorDepth.sample(ctx.random());
		if (top < minY) return Optional.empty();

		BlockPos at = new BlockPos(chunk.getMiddleBlockX(), top, chunk.getMiddleBlockZ());
		return Optional.of(new GenerationStub(at, builder -> generatePieces(builder, ctx, top)));
	}

	private void generatePieces(StructurePiecesBuilder builder, GenerationContext ctx, int top) {
		int attempt = 0;
		StrongholdPieces.StartPiece start;
		do {
			builder.clear();
			ctx.random().setLargeFeatureSeed(ctx.seed() + attempt++, ctx.chunkPos().x(), ctx.chunkPos().z());
			StrongholdPieces.resetPieces();
			start = new StrongholdPieces.StartPiece(ctx.random(), ctx.chunkPos().getBlockX(2), ctx.chunkPos().getBlockZ(2));
			builder.addPiece(start);
			start.addChildren(start, builder, ctx.random());
			List<StructurePiece> pending = start.pendingChildren;
			while (!pending.isEmpty()) {
				int j = ctx.random().nextInt(pending.size());
				StructurePiece piece = pending.remove(j);
				piece.addChildren(start, builder, ctx.random());
			}
			moveTopTo(builder, top);
		} while (builder.isEmpty() || start.portalRoomPiece == null);
	}

	private void moveTopTo(StructurePiecesBuilder builder, int top) {
		BoundingBox box = builder.getBoundingBox();
		int offset = top - box.maxY();
		if (box.minY() + offset < minY) offset = minY - box.minY();
		builder.offsetPiecesVertically(offset);
	}

	@Override
	public StructureType<?> type() {
		return OceaniaWorldgenStructures.FLOOR_STRONGHOLD;
	}
}
