package com.oceania.client;

import com.oceania.client.biolum.BiolumDebug;
import com.oceania.client.biolum.BiolumParticle;
import com.oceania.client.biolum.SurfaceGlow;
import com.oceania.client.debug.ClientSmoke;
import com.oceania.client.raft.RaftClient;
import com.oceania.client.render.CoconutCrabRenderer;
import com.oceania.client.render.CrownJellyRenderer;
import com.oceania.client.render.GiantSquidRenderer;
import com.oceania.client.render.KingCrabRenderer;
import com.oceania.client.render.MoonJellyRenderer;
import com.oceania.client.render.NettleJellyRenderer;
import com.oceania.client.render.RaftRenderer;
import com.oceania.client.render.ReefCrabRenderer;
import com.oceania.client.render.SeagullRenderer;
import com.oceania.client.render.SharkRenderer;
import com.oceania.client.render.WhaleRenderer;
import com.oceania.client.weather.RainDebug;
import com.oceania.item.TurtleArmor;
import com.oceania.raft.RaftTicker;
import com.oceania.registry.OceaniaEntities;
import com.oceania.registry.OceaniaParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class OceaniaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SharkRenderer.register();
		WhaleRenderer.register();
		SeagullRenderer.register();
		CoconutCrabRenderer.register();
		ReefCrabRenderer.register();
		KingCrabRenderer.register();
		GiantSquidRenderer.register();
		MoonJellyRenderer.register();
		NettleJellyRenderer.register();
		CrownJellyRenderer.register();
		EntityRendererRegistry.register(OceaniaEntities.RAFT, RaftRenderer::new);
		registerTurtleArmorTooltip();

		ParticleProviderRegistry.getInstance().register(OceaniaParticles.BIOLUM, BiolumParticle.Provider::new);
		SurfaceGlow.init();
		BiolumDebug.init();
		RainDebug.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				RaftTicker.collideAll(client.level, client.level.entitiesForRendering());
			}
		});

		RaftClient.init();

		ClientSmoke.init();
	}

	private static void registerTurtleArmorTooltip() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (TurtleArmor.isTurtlePiece(stack)) {
				int at = Math.min(1, lines.size());
				for (int i = 1; i <= 4; i++) {
					lines.add(at + i - 1, Component.translatable("item.oceania.turtle_armor.tooltip." + i).withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}
}
