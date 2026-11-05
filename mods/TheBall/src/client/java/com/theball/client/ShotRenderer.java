package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.theball.entity.ExplosiveShotEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

/**
 * Proyectiles del cetro: una bolita de papel de aluminio brillante (siempre a plena luz) que gira sobre sí misma.
 * La bola de aluminio explosivo se tiñe de un blanco cálido, y la gigante del remate mide 2,4 bloques.
 */
public class ShotRenderer<T extends Entity> extends EntityRenderer<T, ShotRenderer.State> {
	private final float size;
	private final int color;

	public static class State extends EntityRenderState {
		float diameter;
		float spin;
		int color;
	}

	public ShotRenderer(EntityRendererProvider.Context context, float size, int color) {
		super(context);
		this.size = size;
		this.color = color;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		boolean giant = entity instanceof ExplosiveShotEntity shot && shot.isGiant();
		state.diameter = giant ? 2.4f : this.size;
		state.spin = (entity.tickCount + partialTick) * 0.35f;
		state.color = giant ? 0xFFFFF4D8 : this.color;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float d = state.diameter;
		Quaternionf rotation = new Quaternionf().rotationXYZ(state.spin, state.spin * 0.7f, 0.0f);
		int color = state.color;
		poseStack.pushPose();
		poseStack.translate(0.0, d / 2.0, 0.0);
		poseStack.scale(d, d, d);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(AluminumBallRenderer.TEXTURE),
				(pose, consumer) -> SphereMesh.add(pose, consumer, rotation, SphereMesh.FULL_BRIGHT, color));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
