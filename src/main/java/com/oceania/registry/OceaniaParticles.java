package com.oceania.registry;

import com.oceania.Oceania;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class OceaniaParticles {

	public static final SimpleParticleType BIOLUM =
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Oceania.id("biolum"), FabricParticleTypes.simple());

	private OceaniaParticles() {
	}

	public static void init() {
	}
}
