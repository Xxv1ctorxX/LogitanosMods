package com.theball.client;

import com.theball.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class TheBallClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.ALUMINUM_BALL, AluminumBallRenderer::new);
		EntityRenderers.register(ModEntities.GOAL, GoalRenderer::new);
		EntityRenderers.register(ModEntities.GOAL_PART, InvisibleRenderer::new);
	}
}
