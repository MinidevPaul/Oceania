package com.oceania.mixin.weather;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface ServerLevelWeatherAccessor {

	@Accessor("RAIN_DELAY")
	static IntProvider oceania$rainDelay() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("RAIN_DELAY")
	static void oceania$setRainDelay(IntProvider provider) {
		throw new AssertionError();
	}

	@Accessor("RAIN_DURATION")
	static IntProvider oceania$rainDuration() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("RAIN_DURATION")
	static void oceania$setRainDuration(IntProvider provider) {
		throw new AssertionError();
	}

	@Accessor("THUNDER_DELAY")
	static IntProvider oceania$thunderDelay() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("THUNDER_DELAY")
	static void oceania$setThunderDelay(IntProvider provider) {
		throw new AssertionError();
	}

	@Accessor("THUNDER_DURATION")
	static IntProvider oceania$thunderDuration() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("THUNDER_DURATION")
	static void oceania$setThunderDuration(IntProvider provider) {
		throw new AssertionError();
	}
}
