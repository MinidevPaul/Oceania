package com.oceania.mixin.worldgen;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OceanMonumentStructure.class)
public class OceanMonumentStructureMixin {
	private static final int VANILLA_BASE_Y = 39;

	private static final int FOOTPRINT = 58;

	private static final int MAX_FLOOR_SPREAD = Integer.getInteger("oceania.monument.spread", 40);

	private static final int SAMPLE_STEP = 8;

	private static int[] oceania$floorAcrossFootprint(Structure.GenerationContext ctx) {
		int originX = ctx.chunkPos().getBlockX(9) - FOOTPRINT / 2;
		int originZ = ctx.chunkPos().getBlockZ(9) - FOOTPRINT / 2;
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int dx = 0; dx <= FOOTPRINT; dx += SAMPLE_STEP) {
			for (int dz = 0; dz <= FOOTPRINT; dz += SAMPLE_STEP) {
				int floor = ctx.chunkGenerator().getFirstOccupiedHeight(
					originX + Math.min(dx, FOOTPRINT), originZ + Math.min(dz, FOOTPRINT),
					Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
				low = Math.min(low, floor);
				high = Math.max(high, floor);
			}
		}
		return new int[] {low, high};
	}

	private static void oceania$moveWhole(StructurePiece building, int dy) {
		if (dy == 0) {
			return;
		}
		building.move(0, dy, 0);
		if (building instanceof MonumentBuildingAccessor accessor) {
			for (StructurePiece room : accessor.oceania$childPieces()) {
				room.move(0, dy, 0);
			}
		}
	}

	@ModifyReturnValue(method = "findGenerationPoint", at = @At("RETURN"))
	private Optional<Structure.GenerationStub> oceania$refuseSlopedSites(
			Optional<Structure.GenerationStub> sited, Structure.GenerationContext ctx) {
		if (sited.isEmpty()) {
			return sited;
		}
		int[] floor = oceania$floorAcrossFootprint(ctx);
		int spread = floor[1] - floor[0];
		if (spread > MAX_FLOOR_SPREAD) {
			com.oceania.Oceania.LOGGER.info("[monument] {} refused: floor spreads {} blocks across the footprint, over the {} allowed",
					ctx.chunkPos(), spread, MAX_FLOOR_SPREAD);
			return Optional.empty();
		}
		com.oceania.Oceania.LOGGER.info("[monument] {} sited: floor spreads {} blocks across the footprint",
				ctx.chunkPos(), spread);
		return sited;
	}

	@Inject(method = "generatePieces", at = @At("TAIL"))
	private static void oceania$lowerToFloor(StructurePiecesBuilder builder, Structure.GenerationContext ctx, CallbackInfo ci) {

		int[] floor = oceania$floorAcrossFootprint(ctx);
		int floorY = floor[1];
		for (StructurePiece piece : builder.build().pieces()) {
			oceania$moveWhole(piece, floorY - piece.getBoundingBox().minY());
		}
		com.oceania.Oceania.LOGGER.info("[monument] {} seated at y={} on the high side of a floor that runs {} to {} ({} blocks of leg)",
				ctx.chunkPos(), floorY, floor[0], floor[1], floor[1] - floor[0]);
	}

	@ModifyReturnValue(method = "regeneratePiecesAfterLoad", at = @At("RETURN"))
	private static PiecesContainer oceania$keepLoadedY(PiecesContainer rebuilt, ChunkPos chunkPos, long seed, PiecesContainer saved) {
		if (saved.isEmpty() || rebuilt.isEmpty()) {
			return rebuilt;
		}
		int savedY = saved.pieces().get(0).getBoundingBox().minY();
		for (StructurePiece piece : rebuilt.pieces()) {
			oceania$moveWhole(piece, savedY - piece.getBoundingBox().minY());
		}
		return rebuilt;
	}
}
