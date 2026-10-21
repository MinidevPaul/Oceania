package com.oceania.client.weather;

import com.oceania.debug.DebugCommands;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class RainDebug {

	private static final int LOOKAHEAD = 50000;

	private RainDebug() {
	}

	public static void init() {
		if (!DebugCommands.inDevelopment()) {
			return;
		}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
			ClientCommands.literal("rain").executes(ctx -> {
				report(ctx.getSource()::sendFeedback);
				return 1;
			})));
	}

	private static void report(Consumer<Component> out) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		out.accept(Component.literal("[rain] the heavy-rain phase - says nothing about whether it is raining at all"));
		if (level == null) {
			out.accept(Component.literal("  no level"));
			return;
		}
		float s = HeavyRain.strength(level);
		boolean heavy = s >= 0.5F;
		out.accept(Component.literal(String.format(
			"  signal   raw %+.3f against threshold %+.3f (+/- %.2f ramp)  ->  strength %.2f  =  %s",
			HeavyRain.rawFor(level), HeavyRainTuning.THRESHOLD, HeavyRainTuning.SOFTNESS, s, heavy ? "HEAVY" : "ordinary")));
		out.accept(Component.literal(String.format(
			"  applied  streak alpha x%.2f, splash count x%.2f  (splashes go as the SQUARE of the multiplier)",
			HeavyRain.streakMultiplier(level), HeavyRain.splashMultiplier(level) * HeavyRain.splashMultiplier(level))));
		out.accept(Component.literal(String.format(
			"  weather  rainLevel %.2f, thundering=%s  (unaffected by any of the above, by design)",
			level.getRainLevel(1.0F), level.isThundering())));
		int when = ticksToFlip(level, heavy);
		if (when < 0) {
			out.accept(Component.literal("  next     no change within " + LOOKAHEAD / 20 + " s"));
		} else {
			out.accept(Component.literal(String.format("  next     %s in about %d s (%d ticks)", heavy ? "eases off" : "turns HEAVY", when / 20, when)));
		}
		out.accept(Component.literal("  target   a third of raining time is heavy, in about 2.7 phases per spell of about 2.8 min"));
	}

	private static int ticksToFlip(ClientLevel level, boolean heavyNow) {
		long base = HeavyRain.dayTime(level);
		for (int d = 20; d < LOOKAHEAD; d += 20) {
			if ((HeavyRain.strengthAt(base + d) >= 0.5F) != heavyNow) return d;
		}
		return -1;
	}
}
