package com.oceania.mixin.worldgen;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OceanMonumentPieces.MonumentBuilding.class)
public interface MonumentBuildingAccessor {
	@Accessor("childPieces")
	List<StructurePiece> oceania$childPieces();
}
