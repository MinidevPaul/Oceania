package com.oceania.mixin.perf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oceania.perf.PerfFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.material.MaterialSystem$1")
public class SurfaceFastWriteMixin {
	@WrapOperation(
		method = "setBlock(ILnet/minecraft/world/level/block/state/BlockState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState oceania$skipIdenticalWrite(final ChunkAccess chunk, final BlockPos pos, final BlockState state,
			final Operation<BlockState> original) {
		if (PerfFlags.SURFACE_FAST_WRITE
			&& chunk instanceof ProtoChunk proto
			&& !(chunk instanceof ImposterProtoChunk)
			&& !proto.isOutsideBuildHeight(pos.getY())) {
			PerfFlags.WRITES_SEEN.increment();
			LevelChunkSection section = proto.getSection(proto.getSectionIndex(pos.getY()));
			PalettedContainer<BlockState> states = ((LevelChunkSectionStatesAccessor) section).oceania$states();
			BlockState present = states.get(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()),
				SectionPos.sectionRelative(pos.getZ()));
			if (present == state) {
				PerfFlags.WRITES_SKIPPED.increment();
				return present;
			}
		}
		return original.call(chunk, pos, state);
	}
}
