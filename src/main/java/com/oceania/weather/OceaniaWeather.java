package com.oceania.weather;

import net.minecraft.world.level.saveddata.WeatherData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oceania.Oceania;
import com.oceania.mixin.weather.ServerLevelWeatherAccessor;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.gamerules.GameRules;
import com.oceania.debug.DebugCommands;

public final class OceaniaWeather {

	private static final IntProvider RAIN_DELAY = range("oceania.weather.rain.delay", 7000, 60000);

	private static final IntProvider RAIN_DURATION = range("oceania.weather.rain.duration", 16000, 36000);

	private static final IntProvider THUNDER_DELAY = range("oceania.weather.thunder.delay", 9000, 72000);

	private static final IntProvider THUNDER_DURATION = range("oceania.weather.thunder.duration", 4800, 18000);

	private static final boolean ENABLED =
		!"false".equalsIgnoreCase(System.getProperty("oceania.weather", "true"));

	private OceaniaWeather() {
	}

	public static void init() {
		install();
		registerCommand();
	}

	private static void install() {
		if (!ENABLED) {
			Oceania.LOGGER.info("[weather] oceania.weather=false, vanilla weather left alone: rain {} / {}, thunder {} / {}",
				ServerLevelWeatherAccessor.oceania$rainDelay(), ServerLevelWeatherAccessor.oceania$rainDuration(),
				ServerLevelWeatherAccessor.oceania$thunderDelay(), ServerLevelWeatherAccessor.oceania$thunderDuration());
			return;
		}

		String before = describe(ServerLevelWeatherAccessor.oceania$rainDelay(),
			ServerLevelWeatherAccessor.oceania$rainDuration(),
			ServerLevelWeatherAccessor.oceania$thunderDelay(),
			ServerLevelWeatherAccessor.oceania$thunderDuration());

		ServerLevelWeatherAccessor.oceania$setRainDelay(RAIN_DELAY);
		ServerLevelWeatherAccessor.oceania$setRainDuration(RAIN_DURATION);
		ServerLevelWeatherAccessor.oceania$setThunderDelay(THUNDER_DELAY);
		ServerLevelWeatherAccessor.oceania$setThunderDuration(THUNDER_DURATION);

		String after = describe(ServerLevelWeatherAccessor.oceania$rainDelay(),
			ServerLevelWeatherAccessor.oceania$rainDuration(),
			ServerLevelWeatherAccessor.oceania$thunderDelay(),
			ServerLevelWeatherAccessor.oceania$thunderDuration());

		Oceania.LOGGER.info("[weather] vanilla {}", before);
		Oceania.LOGGER.info("[weather] oceania {}", after);
		if (before.equals(after)) {

			Oceania.LOGGER.error("[weather] the four weather providers did not change - the accessor resolved but wrote nothing");
		}
	}

	private static String describe(IntProvider rainDelay, IntProvider rainDuration,
	                               IntProvider thunderDelay, IntProvider thunderDuration) {
		return "rain delay " + rainDelay + ", rain duration " + rainDuration
			+ ", thunder delay " + thunderDelay + ", thunder duration " + thunderDuration;
	}

	private static IntProvider range(String key, int defaultLo, int defaultHi) {
		String raw = System.getProperty(key);
		if (raw == null || raw.isBlank()) return UniformInt.of(defaultLo, defaultHi);
		String[] parts = raw.split("[,:-]");
		if (parts.length == 2) {
			try {
				int lo = Integer.parseInt(parts[0].trim());
				int hi = Integer.parseInt(parts[1].trim());

				if (lo >= 1 && hi >= lo) return UniformInt.of(lo, hi);
			} catch (NumberFormatException ignored) {

			}
		}
		Oceania.LOGGER.warn("[weather] could not read -D{}={}, wanted lo,hi in ticks; using {}..{}",
			key, raw, defaultLo, defaultHi);
		return UniformInt.of(defaultLo, defaultHi);
	}

	private static void registerCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("oceania").requires(DebugCommands::visibleTo)
				.then(Commands.literal("weather")
					.executes(ctx -> report(ctx.getSource()))
					.then(Commands.literal("skip")
						.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000))
							.executes(ctx -> skip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ticks"))))))));
	}

	private static int report(CommandSourceStack src) {
		ServerLevel level = src.getLevel();
		WeatherData data = level.getWeatherData();
		boolean cycle = level.getGameRules().get(GameRules.ADVANCE_WEATHER);

		src.sendSuccess(() -> Component.literal("[weather] counters only - says nothing about what is visible where you stand"), false);
		src.sendSuccess(() -> Component.literal(String.format(
			"ranges: rain delay %s, rain duration %s, thunder delay %s, thunder duration %s%s",
			ServerLevelWeatherAccessor.oceania$rainDelay(), ServerLevelWeatherAccessor.oceania$rainDuration(),
			ServerLevelWeatherAccessor.oceania$thunderDelay(), ServerLevelWeatherAccessor.oceania$thunderDuration(),
			ENABLED ? "" : "  (oceania.weather=false, these are vanilla's)")), false);
		src.sendSuccess(() -> Component.literal(String.format(
			"timers: clear %d, rain %d, thunder %d ticks   doWeatherCycle=%s",
			data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(), cycle)), false);
		src.sendSuccess(() -> Component.literal(String.format(
			"flags:  raining=%s thundering=%s      levels: rain %.2f, thunder %.2f",
			data.isRaining(), data.isThundering(), level.getRainLevel(1.0F), level.getThunderLevel(1.0F))), false);
		src.sendSuccess(() -> Component.literal(String.format(
			"engine: isRaining()=%s (needs rain level > 0.20)   isThundering()=%s (needs rain x thunder > 0.90)",
			level.isRaining(), level.isThundering())), false);
		if (!cycle) {
			src.sendSuccess(() -> Component.literal("doWeatherCycle is OFF - the ranges above are never sampled"), false);
		}
		return 1;
	}

	private static int skip(CommandSourceStack src, int ticks) {
		ServerLevel level = src.getLevel();
		WeatherData data = level.getWeatherData();
		int clear = Math.max(0, data.getClearWeatherTime() - ticks);
		int rain = Math.max(1, data.getRainTime() - ticks);
		int thunder = Math.max(1, data.getThunderTime() - ticks);
		data.setClearWeatherTime(clear);
		data.setRainTime(rain);
		data.setThunderTime(thunder);
		src.sendSuccess(() -> Component.literal(String.format(
			"[weather] wound %d ticks off the timers: clear %d, rain %d, thunder %d",
			ticks, clear, rain, thunder)), false);
		return report(src);
	}
}
