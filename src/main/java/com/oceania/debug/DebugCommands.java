package com.oceania.debug;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

public final class DebugCommands {

	private DebugCommands() {
	}

	public static boolean inDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	public static boolean visibleTo(CommandSourceStack source) {

		if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return false;
		}
		return inDevelopment() || source.getServer().isDedicatedServer();
	}
}
