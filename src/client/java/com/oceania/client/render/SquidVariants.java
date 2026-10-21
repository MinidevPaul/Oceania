package com.oceania.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.squid.Squid;

public final class SquidVariants {
	private static final Identifier NORMAL = tex("squid");
	private static final Identifier RED = tex("squid_red");
	private static final Identifier WHITE = tex("squid_white");

	private static final int BUCKETS = 6;

	private SquidVariants() {
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("oceania", "textures/entity/squid/" + name + ".png");
	}

	public static Identifier textureFor(Squid squid) {
		return switch (Math.floorMod(squid.getUUID().hashCode(), BUCKETS)) {
			case 4 -> RED;
			case 5 -> WHITE;
			default -> NORMAL;
		};
	}
}
