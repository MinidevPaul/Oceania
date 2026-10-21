package com.oceania.registry;

import com.oceania.Oceania;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class OceaniaSounds {

	public static final SoundEvent WHALE_SONG = register("entity.whale.song");

	public static final SoundEvent WHALE_BLOW = register("entity.whale.blow");

	private static SoundEvent register(String path) {
		Identifier id = Oceania.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
			SoundEvent.createVariableRangeEvent(id));
	}

	private OceaniaSounds() {
	}

	public static void init() {
	}
}
