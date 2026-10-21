package com.oceania.mixin.worldgen;

import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Beardifier.class)
public interface BeardifierPiecesAccessor {
	@Accessor("pieces")
	List<Beardifier.Rigid> oceania$pieces();
}
