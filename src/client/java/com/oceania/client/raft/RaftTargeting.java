package com.oceania.client.raft;

import com.oceania.client.mixin.MinecraftRaftAccessor;
import com.oceania.raft.RaftEntity;
import com.oceania.raft.RaftItem;
import com.oceania.raft.RaftLevel;
import com.oceania.raft.RaftNetworking;
import com.oceania.raft.RaftPlaceContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class RaftTargeting {
	private RaftTargeting() {
	}

	public record RaftHit(RaftEntity raft, BlockHitResult local, Vec3 world) {
		public BlockPos pos() {
			return local.getBlockPos();
		}

		boolean sameBlock(RaftHit other) {
			return other.raft == raft && other.pos().equals(pos());
		}
	}

	private static @Nullable RaftHit current;

	private static @Nullable RaftHit breaking;
	private static int breakTicks;

	public static @Nullable RaftHit current() {
		return current;
	}

	public static void pick(Minecraft mc, float partialTick) {
		current = null;
		Entity camera = mc.getCameraEntity();
		if (camera == null || mc.level == null || mc.player == null) return;
		double reach = mc.player.blockInteractionRange();
		Vec3 from = camera.getEyePosition(partialTick);
		Vec3 dir = camera.getViewVector(partialTick);
		Vec3 to = from.add(dir.scale(reach));
		HitResult vanilla = mc.hitResult;
		double best = vanilla != null && vanilla.getType() != HitResult.Type.MISS ? vanilla.getLocation().distanceToSqr(from) : reach * reach;
		AABB ray = new AABB(from, to).inflate(1.0);
		CollisionContext context = CollisionContext.of(mc.player);
		RaftHit found = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof RaftEntity raft) || raft.blocks().isEmpty() || !raft.getBoundingBox().intersects(ray)) continue;
			Vec3 fromLocal = raft.toLocal(from, partialTick);
			Vec3 toLocal = raft.toLocal(to, partialTick);
			RaftLevel level = raft.raftLevel();
			for (Map.Entry<BlockPos, BlockState> entry : raft.blocks().all().entrySet()) {
				BlockPos pos = entry.getKey();
				if (new AABB(pos).clip(fromLocal, toLocal).isEmpty()) continue;
				VoxelShape shape = entry.getValue().getShape(level, pos, context);
				if (shape.isEmpty()) continue;
				BlockHitResult hit = shape.clip(fromLocal, toLocal, pos);
				if (hit == null) continue;

				double d = hit.getLocation().distanceToSqr(fromLocal);
				if (d < best) {
					best = d;
					found = new RaftHit(raft, hit, raft.toWorld(hit.getLocation(), partialTick));
				}
			}
		}
		if (found != null) {
			current = found;
			mc.hitResult = new EntityHitResult(found.raft, found.world);
			mc.crosshairPickEntity = found.raft;
		}
	}

	public static boolean handleUse(Minecraft mc) {
		RaftHit hit = current;
		if (hit == null || mc.player == null) return false;
		if (mc.gameMode != null && mc.gameMode.isDestroying()) return true;
		((MinecraftRaftAccessor) mc).oceania$setRightClickDelay(4);
		if (mc.player.isHandsBusy()) return true;

		ClientPlayNetworking.send(new RaftNetworking.UsePayload(hit.raft.getId(), hit.pos(), hit.local.getDirection(), hit.local.getLocation()));
		predictUse(mc, hit);
		return true;
	}

	private static void predictUse(Minecraft mc, RaftHit hit) {
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator()) return;
		RaftLevel level = hit.raft.raftLevel();
		BlockHitResult local = hit.local;
		BlockState state = level.getBlockState(local.getBlockPos());
		if (state.isAir()) return;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			InteractionResult result = predictUseOn(player, hit.raft, level, stack, hand, local, state);
			if (result.consumesAction()) {
				if (result instanceof InteractionResult.Success success && success.shouldSwing()) {
					player.swing(hand, stack.getInteractAnimation(), false);
				}
				return;
			}
			if (result == InteractionResult.FAIL || !stack.isEmpty()) return;
		}
	}

	private static InteractionResult predictUseOn(LocalPlayer player, RaftEntity raft, RaftLevel level, ItemStack stack, InteractionHand hand, BlockHitResult hit, BlockState state) {
		boolean holdingSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
		boolean sneakBypass = player.isSecondaryUseActive() && holdingSomething;
		if (!sneakBypass) {
			if (state.getBlock() instanceof BedBlock && hand == InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
			InteractionResult itemResult = state.useItemOn(stack, level, player, hand, hit);
			if (itemResult.consumesAction()) return itemResult;
			if (itemResult instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
				InteractionResult result = state.useWithoutItem(level, player, hit);
				if (result.consumesAction()) return result;
			}
		}
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (stack.getItem() instanceof RaftItem) return InteractionResult.CONSUME;
		int count = stack.getCount();
		InteractionResult result;
		if (stack.getItem() instanceof BlockItem blockItem) {
			result = blockItem.place(new RaftPlaceContext(raft, level, player, hand, stack, hit));
		} else {
			result = stack.useOn(new RaftUseOnContext(level, player, hand, stack, hit));
		}
		if (player.hasInfiniteMaterials()) stack.setCount(count);
		return result;
	}

	public static boolean handleAttackStart(Minecraft mc) {
		RaftHit hit = current;
		if (hit == null || mc.player == null) return false;
		if (((MinecraftRaftAccessor) mc).oceania$missTime() > 0 || mc.player.isHandsBusy()) return true;
		if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		startBreaking(hit);
		mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		return true;
	}

	public static boolean handleContinueAttack(Minecraft mc, boolean leftClick) {
		if (!leftClick || mc.player == null || mc.level == null) {
			stopBreaking();
			return false;
		}
		RaftHit hit = current;
		if (hit == null) {
			stopBreaking();
			return false;
		}
		if (((MinecraftRaftAccessor) mc).oceania$missTime() > 0 || mc.player.isUsingItem()) return true;
		if (breaking == null || !breaking.sameBlock(hit)) startBreaking(hit);
		BlockState state = hit.raft.raftLevel().getBlockState(hit.pos());
		if (state.isAir()) return true;
		breakTicks++;
		if (breakTicks % 4 == 0) {
			SoundType sound = state.getSoundType();
			mc.getSoundManager().play(new SimpleSoundInstance(sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F,
				sound.getPitch() * 0.5F, SoundInstance.createUnseededRandom(), hit.world.x, hit.world.y, hit.world.z));
		}
		crack(mc, hit, state);
		mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		return true;
	}

	private static void startBreaking(RaftHit hit) {
		stopBreaking();
		breaking = hit;
		breakTicks = 0;
		ClientPlayNetworking.send(new RaftNetworking.AttackPayload(hit.raft.getId(), hit.pos(), hit.local.getDirection(), true));
	}

	public static void stopBreaking() {
		if (breaking == null) return;
		RaftHit was = breaking;
		breaking = null;
		if (Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new RaftNetworking.AttackPayload(was.raft.getId(), was.pos(), was.local.getDirection(), false));
		}
	}

	private static void crack(Minecraft mc, RaftHit hit, BlockState state) {
		if (state.getRenderShape() == RenderShape.INVISIBLE || !state.shouldSpawnTerrainParticles() || mc.level == null) return;
		RandomSource random = mc.level.getRandom();
		BlockPos pos = hit.pos();
		AABB box = state.getShape(hit.raft.raftLevel(), pos).bounds();
		double x = pos.getX() + random.nextDouble() * (box.maxX - box.minX - 0.2) + 0.1 + box.minX;
		double y = pos.getY() + random.nextDouble() * (box.maxY - box.minY - 0.2) + 0.1 + box.minY;
		double z = pos.getZ() + random.nextDouble() * (box.maxZ - box.minZ - 0.2) + 0.1 + box.minZ;
		Direction face = hit.local.getDirection();
		switch (face) {
			case DOWN -> y = pos.getY() + box.minY - 0.1;
			case UP -> y = pos.getY() + box.maxY + 0.1;
			case NORTH -> z = pos.getZ() + box.minZ - 0.1;
			case SOUTH -> z = pos.getZ() + box.maxZ + 0.1;
			case WEST -> x = pos.getX() + box.minX - 0.1;
			case EAST -> x = pos.getX() + box.maxX + 0.1;
		}
		Vec3 world = hit.raft.toWorld(new Vec3(x, y, z));
		mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), world.x, world.y, world.z, 0.0, 0.0, 0.0);
	}
}
