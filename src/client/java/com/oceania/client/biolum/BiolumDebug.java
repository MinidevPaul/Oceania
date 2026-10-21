package com.oceania.client.biolum;

import com.oceania.debug.DebugCommands;
import com.oceania.registry.OceaniaParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class BiolumDebug {
	private BiolumDebug() {
	}

	private static final int TEST_BURST = 80;

	public static void init() {
		if (!DebugCommands.inDevelopment()) {
			return;
		}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
			ClientCommands.literal("biolum")
				.executes(ctx -> {
					report(ctx.getSource()::sendFeedback);
					return 1;
				})
				.then(ClientCommands.literal("test").executes(ctx -> {
					forceBurst(ctx.getSource()::sendFeedback);
					return 1;
				}))));
	}

	private static void forceBurst(Consumer<Component> out) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			out.accept(Component.literal("[biolum] no level"));
			return;
		}
		Vec3 at = client.player.position();
		RandomSource random = level.getRandom();
		int created = 0;
		for (int i = 0; i < TEST_BURST; i++) {
			double angle = i / (double) TEST_BURST * Math.PI * 2;
			double radius = 0.6 + random.nextDouble() * 1.6;
			double x = at.x + Math.cos(angle) * radius;
			double z = at.z + Math.sin(angle) * radius;
			double y = at.y + 0.4 + random.nextDouble() * 0.8;
			Particle p = client.particleEngine.createParticle(
				OceaniaParticles.BIOLUM, x, y, z,
				(random.nextDouble() - 0.5) * 0.02, 0.005, (random.nextDouble() - 0.5) * 0.02);
			if (p != null) created++;
		}
		int madeCount = created;
		out.accept(Component.literal(String.format("[biolum] forced %d particles around you, every gate ignored  ->  %d created", TEST_BURST, madeCount)));
		if (madeCount == 0) {
			out.accept(Component.literal("  created 0 - no provider is registered for oceania:biolum. This is a REAL BUG and the gate is irrelevant."));
		} else {
			out.accept(Component.literal("  the particle works. If the ocean looked empty earlier, the gate was closed - run /biolum to see which term."));
			out.accept(Component.literal(String.format("  for reference, right now: %s", gateSummary(level))));
		}
	}

	private static String gateSummary(ClientLevel level) {
		level.updateSkyBrightness();
		int darken = level.getSkyDarken();
		return String.format("skyDarken %d -> darkness %.2f (needs > %d), rainLevel %.2f",
			darken, SurfaceGlow.darkStrengthFor(darken), SurfaceGlowTuning.SKY_DARKEN_MIN, level.getRainLevel(1.0F));
	}

	private static void report(Consumer<Component> out) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		out.accept(Component.literal("[biolum] the gate, term by term - says nothing about whether you can SEE them"));
		if (level == null || client.player == null) {
			out.accept(Component.literal("  no level"));
			return;
		}
		level.updateSkyBrightness();
		int darken = level.getSkyDarken();
		int transcribed = SurfaceGlow.skyDarkenTranscribed(level);
		float dark = SurfaceGlow.darkStrengthFor(darken);
		out.accept(Component.literal(String.format(
			"  darkness   skyDarken %d  -> strength %.2f   (0 below %d, full at %d; 0 is clear noon, 11 midnight, 5 a daytime storm)",
			darken, dark, SurfaceGlowTuning.SKY_DARKEN_MIN, SurfaceGlowTuning.SKY_DARKEN_FULL)));
		out.accept(Component.literal(String.format(
			"             independent transcription reads %d%s", transcribed,
			transcribed == darken ? " (agrees)" : " <- DISAGREES: vanilla's updateSkyBrightness formula has changed, this needs re-deriving")));
		out.accept(Component.literal(String.format(
			"  particles  video setting %s -> rate x%.2f   (never zero: MINIMAL would otherwise delete the effect)",
			client.options.particles().get(), SurfaceGlow.thinningFor(client))));
		List<String> rows = new ArrayList<>();
		int considered = 0;
		for (Entity entity : level.entitiesForRendering()) {
			if (!SurfaceGlow.triggersFor(entity)) continue;
			considered++;
			if (entity.distanceToSqr(client.player.position()) > 64 * 64) continue;
			double dx = entity.getX() - entity.xOld;
			double dy = entity.getY() - entity.yOld;
			double dz = entity.getZ() - entity.zOld;
			double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double surface = SurfaceGlow.waterSurfaceY(level, entity);
			double depth = surface - entity.getY();
			float near = Double.isNaN(surface) ? 0.0F : SurfaceGlow.depthStrengthFor(depth);

			String why;
			if (dark <= 0) {
				why = "BLOCKED BY DAYLIGHT - global, nothing emits however the terms below read";
			} else if (!entity.isInWater()) {
				why = "NOT IN WATER";
			} else if (speed < SurfaceGlowTuning.MIN_SPEED) {
				why = String.format("TOO SLOW (needs %.3f blocks/tick)", SurfaceGlowTuning.MIN_SPEED);
			} else if (Double.isNaN(surface)) {
				why = String.format("NO WATER SURFACE within %.0f blocks above it", SurfaceGlowTuning.DEPTH_FADE + 2);
			} else if (near <= 0) {
				why = String.format("TOO DEEP (nothing past %.1f)", SurfaceGlowTuning.DEPTH_FADE);
			} else {
				why = String.format("EMITTING at strength %.2f", dark * near);
			}
			rows.add(String.format("  %-14s %-58s  speed %.3f, surfaceY %.2f, depth %.1f, water=%s",
				entity.getType().getDescription().getString(), why, speed, surface, depth, entity.isInWater()));
		}
		out.accept(Component.literal(String.format("  triggers   %d in the level, %d within 64 blocks:", considered, rows.size())));
		if (rows.isEmpty()) {
			out.accept(Component.literal("    none - the trigger is players, boats and rafts, sharks and whales (not fish)"));
		} else {
			for (String row : rows) out.accept(Component.literal(row));
		}
	}
}
