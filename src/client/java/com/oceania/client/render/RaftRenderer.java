package com.oceania.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.oceania.client.raft.RaftLight;
import com.oceania.client.raft.RaftTargeting;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class RaftRenderer extends EntityRenderer<RaftEntity, RaftRenderState> {
	private final BlockEntityRenderDispatcher blockEntityRenderer;
	private @Nullable ModelBlockRenderer modelRenderer;
	private boolean modelRendererAo;

	public RaftRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blockEntityRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		this.shadowRadius = 0;
	}

	@Override
	public RaftRenderState createRenderState() {
		return new RaftRenderState();
	}

	@Override
	public void extractRenderState(RaftEntity raft, RaftRenderState state, float partialTick) {
		super.extractRenderState(raft, state, partialTick);
		state.raft = raft;
		state.yaw = raft.getViewYRot(partialTick);
		state.blockEntities.clear();
		if (raft.blocks().isEmpty()) return;
		RaftLevel level = raft.raftLevel();
		if (!level.hasLighting()) level.setLighting(new RaftLight(raft));

		Vec3 cameraPos = Minecraft.getInstance().gameRenderer.mainCamera().position();
		for (BlockEntity be : level.blockEntities().values()) {
			BlockEntityRenderState extracted = extract(be, partialTick, cameraPos);
			if (extracted != null) state.blockEntities.add(new RaftRenderState.Extracted(be.getBlockPos(), extracted));
		}
	}

	private <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable S extract(E be, float partialTick, Vec3 cameraPos) {
		BlockEntityRenderer<E, S> renderer = this.blockEntityRenderer.getRenderer(be);
		if (renderer == null || !be.getType().isValid(be.getBlockState())) return null;
		S state = renderer.createRenderState();
		renderer.extractRenderState(be, state, partialTick, cameraPos, null);
		return state;
	}

	@Override
	public void submit(RaftRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		RaftEntity raft = state.raft;
		if (raft != null && !raft.blocks().isEmpty() && Minecraft.getInstance().level != null) {
			RaftLevel level = raft.raftLevel();
			Vec3 pivot = raft.blocks().pivot();
			pose.pushPose();
			pose.translate(pivot.x, 0, pivot.z);
			pose.rotateDegrees(Axis.YP, -state.yaw);
			pose.translate(-pivot.x, 0, -pivot.z);
			submitBlocks(raft, level, pose, collector);
			submitBlockEntities(state, pose, collector, camera);
			submitCracks(raft, level, pose, collector);
			submitOutline(raft, level, pose, collector);
			pose.popPose();
		}
		super.submit(state, pose, collector, camera);
	}

	private ModelBlockRenderer modelRenderer() {
		Minecraft mc = Minecraft.getInstance();
		boolean ao = mc.options.ambientOcclusion().get();
		if (this.modelRenderer == null || this.modelRendererAo != ao) {
			this.modelRenderer = new ModelBlockRenderer(ao, false, mc.getBlockColors());
			this.modelRendererAo = ao;
		}
		return this.modelRenderer;
	}

	private static RenderType renderType(ChunkSectionLayer layer) {
		return switch (layer) {
			case SOLID -> RenderTypes.solidMovingBlock();
			case CUTOUT -> RenderTypes.cutoutMovingBlock();
			case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
		};
	}

	private void submitBlocks(RaftEntity raft, RaftLevel level, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		RaftBlockView view = new RaftBlockView(level, mc.level);
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		ModelBlockRenderer renderer = modelRenderer();
		boolean cutoutLeaves = mc.options.cutoutLeaves().get();

		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			collector.submitCustomGeometry(pose, renderType(layer), (origin, consumer) -> {
				PoseStack ps = new PoseStack();
				ps.last().set(origin);
				for (Map.Entry<BlockPos, BlockState> e : raft.blocks().all().entrySet()) {
					BlockState state = e.getValue();
					if (state.getRenderShape() != RenderShape.MODEL) continue;
					BlockPos pos = e.getKey();
					ChunkSectionLayer forced = ModelBlockRenderer.forceOpaque(cutoutLeaves, state) ? ChunkSectionLayer.SOLID : null;
					ps.pushPose();
					ps.translate(pos.getX(), pos.getY(), pos.getZ());
					BlockQuadOutput out = (x, y, z, quad, instance) -> {
						ChunkSectionLayer quadLayer = forced != null ? forced : quad.materialInfo().layer();
						if (quadLayer != layer) return;
						ps.pushPose();
						ps.translate(x, y, z);
						consumer.putBakedQuad(ps.last(), quad, instance);
						ps.popPose();
					};
					renderer.tesselateBlock(out, 0.0F, 0.0F, 0.0F, view, pos, state, models.get(state), state.getSeed(pos));
					ps.popPose();
				}
			});
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void submitBlockEntities(RaftRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		for (RaftRenderState.Extracted extracted : state.blockEntities) {
			BlockEntityRenderer renderer = this.blockEntityRenderer.getRenderer(extracted.state());
			if (renderer == null) continue;
			BlockPos pos = extracted.pos();
			pose.pushPose();
			pose.translate(pos.getX(), pos.getY(), pos.getZ());
			renderer.submit(extracted.state(), pose, collector, camera);
			pose.popPose();
		}
	}

	private void submitCracks(RaftEntity raft, RaftLevel level, PoseStack pose, SubmitNodeCollector collector) {
		if (raft.breakStages().isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		RaftBlockView view = new RaftBlockView(level, mc.level);
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		ModelBlockRenderer renderer = modelRenderer();
		for (Map.Entry<BlockPos, Integer> e : raft.breakStages().entrySet()) {
			BlockPos pos = e.getKey();
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
			int stage = Mth.clamp(e.getValue(), 0, ModelBakery.DESTROY_TYPES.size() - 1);
			collector.submitCustomGeometry(pose, ModelBakery.DESTROY_TYPES.get(stage), (origin, consumer) -> {
				PoseStack ps = new PoseStack();
				ps.last().set(origin);
				ps.translate(pos.getX(), pos.getY(), pos.getZ());
				VertexConsumer decal = new SheetedDecalTextureGenerator(consumer, ps.last(), 1.0F);
				BlockQuadOutput out = (x, y, z, quad, instance) -> {
					ps.pushPose();
					ps.translate(x, y, z);
					decal.putBakedQuad(ps.last(), quad, instance);
					ps.popPose();
				};
				renderer.tesselateBlock(out, 0.0F, 0.0F, 0.0F, view, pos, state, models.get(state), state.getSeed(pos));
			});
		}
	}

	private void submitOutline(RaftEntity raft, RaftLevel level, PoseStack pose, SubmitNodeCollector collector) {
		RaftTargeting.RaftHit hit = RaftTargeting.current();
		if (hit == null || hit.raft() != raft) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;
		BlockPos pos = hit.pos();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return;
		VoxelShape shape = state.getShape(level, pos, CollisionContext.of(mc.player));

		RenderType type = mc.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		float width = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
		pose.pushPose();
		pose.translate(pos.getX(), pos.getY(), pos.getZ());
		collector.submitShapeOutline(pose, shape, type, ARGB.black(102), width, false);
		pose.popPose();
	}
}
