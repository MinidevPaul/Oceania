package com.oceania.client.render;

import com.oceania.raft.RaftEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RaftRenderState extends EntityRenderState {
	public @Nullable RaftEntity raft;

	public float yaw;
	public final List<Extracted> blockEntities = new ArrayList<>();

	public record Extracted(BlockPos pos, BlockEntityRenderState state) {
	}
}
