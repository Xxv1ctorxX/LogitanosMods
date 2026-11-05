package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.theball.TheBallMod;
import com.theball.entity.AluminumBallEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Dibuja la bola como una esfera generada por código ({@link SphereMesh}) con la textura de papel arrugado.
 * Se escala con el diámetro y rota según lo que ha rodado. Si está sentada en un trono, lleva corona
 * ({@link CrownMesh}) que crece con ella y cambia según las hojas.
 */
public class AluminumBallRenderer extends EntityRenderer<AluminumBallEntity, AluminumBallRenderState> {
	public static final Identifier TEXTURE = TheBallMod.id("textures/entity/aluminum_ball.png");

	/** Ancho de la corona respecto al diámetro de la bola. */
	private static final float CROWN_WIDTH = 0.6f;
	/** Inclinación hacia delante (grados). */
	private static final float CROWN_TILT = 0.0f;

	public AluminumBallRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public AluminumBallRenderState createRenderState() {
		return new AluminumBallRenderState();
	}

	@Override
	public void extractRenderState(AluminumBallEntity entity, AluminumBallRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.diameter = (float) entity.getDiameter();
		state.rotation.set(entity.rollRotationOld).slerp(entity.rollRotation, partialTick);
		state.seated = entity.isSeated();
		state.crownTier = CrownMesh.tierFor(entity.getSheets(), entity.isCrowned());
		state.facingYRot = entity.getThroneFacing().toYRot();
	}

	/** La corona sobresale por encima de la caja de la bola: ampliamos la caja de culling para no "perderla". */
	@Override
	protected AABB getBoundingBoxForCulling(AluminumBallEntity entity, float partialTick) {
		AABB box = super.getBoundingBoxForCulling(entity, partialTick);
		return entity.isSeated() ? box.expandTowards(0.0, entity.getDiameter() * 0.6 + 0.5, 0.0) : box;
	}

	@Override
	public void submit(AluminumBallRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float d = state.diameter;
		Quaternionf rotation = new Quaternionf(state.rotation);
		int light = state.lightCoords;

		poseStack.pushPose();
		// La posición de la entidad es el centro de la base: subimos medio diámetro.
		poseStack.translate(0.0, d / 2.0, 0.0);
		poseStack.scale(d, d, d); // la esfera de la malla tiene diámetro 1
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
				(pose, consumer) -> SphereMesh.add(pose, consumer, rotation, light, -1));
		poseStack.popPose();

		if (state.seated) {
			submitCrown(state, poseStack, collector);
		}
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * Corona apoyada en lo alto de la esfera: un aro de radio 0,3·d toca la esfera a 0,8·r por encima del centro,
	 * es decir, a 0,9·d de la base. Gira hacia donde mira el trono.
	 */
	private static void submitCrown(AluminumBallRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		float d = state.diameter;
		float scale = d * CROWN_WIDTH;
		int tier = state.crownTier;
		int light = tier >= 4 ? SphereMesh.FULL_BRIGHT : state.lightCoords;
		poseStack.pushPose();
		poseStack.translate(0.0, d * 0.905, 0.0); // el aro apoya justo sobre la esfera (antes se hundia y se fusionaba)
		poseStack.rotateDegrees(Axis.YP, -state.facingYRot);
		poseStack.rotateDegrees(Axis.XP, CROWN_TILT);
		poseStack.scale(scale, scale, scale);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CrownMesh.TEXTURE),
				(pose, consumer) -> CrownMesh.add(pose, consumer, light, tier));
		poseStack.popPose();
	}
}
