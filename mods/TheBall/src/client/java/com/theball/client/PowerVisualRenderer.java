package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.theball.entity.PowerVisualEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Dibuja los efectos pegados al jugador en la posición INTERPOLADA del dueño (no en la de la entidad visual,
 * que llega del servidor con un tick de retraso):
 * <ul>
 *   <li>Modo Bola: una esfera de aluminio que envuelve al jugador (encogido a la mitad) y rueda al moverse.
 *       Desde dentro (primera persona) no se ve: las caras miran hacia fuera.</li>
 *   <li>Halo: la corona de oro, enorme, girando despacio sobre la cabeza durante la Ascensión.</li>
 * </ul>
 */
public class PowerVisualRenderer extends EntityRenderer<PowerVisualEntity, PowerVisualRenderer.State> {
	public static class State extends EntityRenderState {
		int kind;
		boolean visible;
		Vec3 offset = Vec3.ZERO;
		float ownerHeight;
		final Quaternionf roll = new Quaternionf();
		float time;
	}

	public PowerVisualRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(PowerVisualEntity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick) {
		return true;
	}

	@Override
	public void extractRenderState(PowerVisualEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		Entity owner = entity.getOwnerEntity();
		state.kind = entity.getKind();
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		boolean ownFirstPerson = owner != null && owner == mc.player && mc.options.getCameraType().isFirstPerson();
		state.visible = owner != null && !(state.kind == PowerVisualEntity.KIND_BALL && ownFirstPerson);
		if (owner != null) {
			state.offset = owner.getPosition(partialTick).subtract(entity.getPosition(partialTick));
			state.ownerHeight = owner.getBbHeight();
		}
		state.roll.set(entity.rollOld).slerp(entity.roll, partialTick);
		state.time = entity.tickCount + partialTick;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(state.offset.x, state.offset.y, state.offset.z);
		if (state.kind == PowerVisualEntity.KIND_BALL) {
			// Lo bastante grande para tapar cuerpo, botas y corona del jugador encogido; apoyada en el suelo.
			float d = Math.max(1.3f, state.ownerHeight * 1.6f);
			Quaternionf rotation = new Quaternionf(state.roll);
			int light = state.lightCoords;
			poseStack.translate(0.0, d / 2.0, 0.0);
			poseStack.scale(d, d, d);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(AluminumBallRenderer.TEXTURE),
					(pose, consumer) -> SphereMesh.add(pose, consumer, rotation, light, -1));
		} else {
			float bob = (float) Math.sin(state.time * 0.15) * 0.05f;
			poseStack.translate(0.0, state.ownerHeight + 0.35 + bob, 0.0);
			poseStack.rotateDegrees(Axis.YP, state.time * 3.0f);
			poseStack.scale(1.1f, 0.8f, 1.1f);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CrownMesh.TEXTURE),
					(pose, consumer) -> CrownMesh.add(pose, consumer, SphereMesh.FULL_BRIGHT, 4));
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
