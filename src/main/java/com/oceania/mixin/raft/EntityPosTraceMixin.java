package com.oceania.mixin.raft;

import com.oceania.raft.RaftMoveTrace;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityPosTraceMixin {
	@Inject(method = "setPosRaw", at = @At("HEAD"))
	private void oceania$tracePosition(double x, double y, double z, CallbackInfo ci) {
		if (!RaftMoveTrace.armed) return;
		Entity self = (Entity) (Object) this;
		if (!self.level().isClientSide() || !(self instanceof Player player) || !player.isLocalPlayer()) return;
		Vec3 old = self.position();
		double dx = x - old.x;
		double dy = y - old.y;
		double dz = z - old.z;
		if (dx == 0 && dy == 0 && dz == 0) return;
		RaftMoveTrace.record(String.format("setPos by (%.4f, %.4f, %.4f) to (%.3f, %.3f, %.3f) via %s", dx, dy, dz, x, y, z, RaftMoveTrace.frames(6)));
	}
}
