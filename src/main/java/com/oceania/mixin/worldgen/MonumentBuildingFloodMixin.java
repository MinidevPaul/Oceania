package com.oceania.mixin.worldgen;

import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(OceanMonumentPieces.MonumentBuilding.class)
public class MonumentBuildingFloodMixin {
	private static final int HEADROOM = 2;

	@ModifyArg(
		method = "postProcess",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces$MonumentBuilding;"
				+ "generateWaterBox(Lnet/minecraft/world/level/WorldGenLevel;"
				+ "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;IIIIII)V",
			ordinal = 0),
		index = 6)
	private int oceania$capFloodToBuilding(int height) {
		int span = ((StructurePiece) (Object) this).getBoundingBox().getYSpan();
		int capped = Math.min(height, span + HEADROOM);
		if (capped != height) {
			com.oceania.Oceania.LOGGER.info("[monument] flood capped from {} to {} (building {} tall)",
					height, capped, span);
		}
		return capped;
	}
}
