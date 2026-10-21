package com.oceania.mixin.perf;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkSection.class)
public interface LevelChunkSectionStatesAccessor {
	@Accessor("states")
	PalettedContainer<BlockState> oceania$states();
}
