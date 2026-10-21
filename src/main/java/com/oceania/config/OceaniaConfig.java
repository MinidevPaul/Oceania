package com.oceania.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.oceania.Oceania;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OceaniaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "oceania.json";
	private static OceaniaConfig instance = new OceaniaConfig();

	public boolean spawnSharks = true;
	public boolean spawnWhales = true;
	public boolean spawnGiantSquid = true;
	public boolean spawnJellyfish = true;
	public boolean spawnCrabs = true;

	public static OceaniaConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				OceaniaConfig read = GSON.fromJson(Files.readString(path), OceaniaConfig.class);
				if (read != null) {
					instance = read;
					Oceania.LOGGER.info("[config] loaded {}: {}", path, instance.summary());
					return;
				}
			} catch (IOException | JsonParseException e) {
				Oceania.LOGGER.warn("[config] could not read {}; using defaults ({})", path, e.toString());
			}
		}
		instance = new OceaniaConfig();
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(instance));
		} catch (IOException e) {
			Oceania.LOGGER.warn("[config] could not write {} ({})", path, e.toString());
		}
	}

	public String summary() {
		return "sharks=" + spawnSharks + " whales=" + spawnWhales + " giant_squid=" + spawnGiantSquid
			+ " jellyfish=" + spawnJellyfish + " crabs=" + spawnCrabs;
	}
}
