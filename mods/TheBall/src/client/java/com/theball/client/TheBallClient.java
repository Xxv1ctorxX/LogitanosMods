package com.theball.client;

import com.theball.entity.FoilShotEntity;
import com.theball.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class TheBallClient implements ClientModInitializer {
	/** Cámara que tenía el jugador antes del Modo Bola (null = no estamos en Modo Bola). */
	private static CameraType cameraBeforeBall;

	/** En Modo Bola y en el modo Aluminio explosivo la cámara pasa a tercera persona (desde dentro de la esfera no se ve nada) y luego se restaura. */
	private static void tickBallCamera(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			cameraBeforeBall = null;
			return;
		}
		boolean inBall = !mc.level.getEntitiesOfClass(com.theball.entity.PowerVisualEntity.class,
				mc.player.getBoundingBox().inflate(2.0),
				e -> (e.getKind() == com.theball.entity.PowerVisualEntity.KIND_BALL
						|| e.getKind() == com.theball.entity.PowerVisualEntity.KIND_HALO) && e.getOwnerEntity() == mc.player).isEmpty();
		if (inBall && cameraBeforeBall == null) {
			cameraBeforeBall = mc.options.getCameraType();
			if (cameraBeforeBall.isFirstPerson()) {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (!inBall && cameraBeforeBall != null) {
			mc.options.setCameraType(cameraBeforeBall);
			cameraBeforeBall = null;
		}
	}

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.ALUMINUM_BALL, AluminumBallRenderer::new);
		EntityRenderers.register(ModEntities.GOAL, GoalRenderer::new);
		EntityRenderers.register(ModEntities.GOAL_PART, InvisibleRenderer::new);
		EntityRenderers.register(ModEntities.FOIL_SHOT, ctx -> new ShotRenderer<>(ctx, FoilShotEntity.SIZE, -1));
		EntityRenderers.register(ModEntities.EXPLOSIVE_SHOT, ctx -> new ShotRenderer<>(ctx, 0.8f, 0xFFFFE6C0));
		EntityRenderers.register(ModEntities.POWER_VISUAL, PowerVisualRenderer::new);
		net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(
				com.theball.TheBallMod.id("scepter"), ScepterSpecialRenderer.Unbaked.MAP_CODEC);
		ClientTickEvents.END_CLIENT_TICK.register(TheBallClient::tickBallCamera);
		QueenBarHud.register();
		CoronationMusic.register();
	}
}
