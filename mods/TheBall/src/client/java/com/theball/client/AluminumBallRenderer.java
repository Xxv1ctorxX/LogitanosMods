package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.theball.TheBallMod;
import com.theball.entity.AluminumBallEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dibuja la bola como una esfera generada por código (malla latitud/longitud) con la textura
 * de papel arrugado. Se escala con el diámetro y rota según lo que ha rodado.
 *
 * Las normales se calculan DESPUÉS de rotar los vértices, así la luz del mundo no gira con la bola
 * (solo gira la textura).
 */
public class AluminumBallRenderer extends EntityRenderer<AluminumBallEntity, AluminumBallRenderState> {
	private static final Identifier TEXTURE = TheBallMod.id("textures/entity/aluminum_ball.png");

	private static final int LAT = 16;
	private static final int LON = 32;
	private static final int COLS = LON + 1;
	private static final int VERTS = (LAT + 1) * COLS;

	/** Dirección unitaria de cada vértice de la esfera (sin rotar). */
	private static final float[] DIR = new float[VERTS * 3];
	private static final float[] UV = new float[VERTS * 2];

	static {
		for (int i = 0; i <= LAT; i++) {
			double theta = Math.PI * i / LAT; // 0 = polo norte
			for (int j = 0; j <= LON; j++) {
				double phi = 2.0 * Math.PI * j / LON;
				int idx = i * COLS + j;
				DIR[idx * 3] = (float) (Math.sin(theta) * Math.cos(phi));
				DIR[idx * 3 + 1] = (float) Math.cos(theta);
				DIR[idx * 3 + 2] = (float) (Math.sin(theta) * Math.sin(phi));
				UV[idx * 2] = (float) j / LON;
				UV[idx * 2 + 1] = (float) i / LAT;
			}
		}
	}

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
				(pose, consumer) -> addSphere(pose, consumer, rotation, light));

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void addSphere(PoseStack.Pose pose, VertexConsumer consumer, Quaternionf rotation, int light) {
		// Rotamos las direcciones una sola vez.
		float[] rotated = new float[VERTS * 3];
		Vector3f v = new Vector3f();
		for (int idx = 0; idx < VERTS; idx++) {
			v.set(DIR[idx * 3], DIR[idx * 3 + 1], DIR[idx * 3 + 2]);
			rotation.transform(v);
			rotated[idx * 3] = v.x;
			rotated[idx * 3 + 1] = v.y;
			rotated[idx * 3 + 2] = v.z;
		}

		for (int i = 0; i < LAT; i++) {
			for (int j = 0; j < LON; j++) {
				int a = i * COLS + j;
				int b = (i + 1) * COLS + j;
				int c = (i + 1) * COLS + j + 1;
				int d = i * COLS + j + 1;
				// Orden antihorario visto desde fuera.
				vertex(pose, consumer, rotated, a, light);
				vertex(pose, consumer, rotated, d, light);
				vertex(pose, consumer, rotated, c, light);
				vertex(pose, consumer, rotated, b, light);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float[] rotated, int idx, int light) {
		float nx = rotated[idx * 3];
		float ny = rotated[idx * 3 + 1];
		float nz = rotated[idx * 3 + 2];
		consumer.addVertex(pose, nx * 0.5f, ny * 0.5f, nz * 0.5f)
				.setColor(-1)
				.setUv(UV[idx * 2], UV[idx * 2 + 1])
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
