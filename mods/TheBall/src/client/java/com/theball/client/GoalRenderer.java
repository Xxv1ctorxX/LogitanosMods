package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.theball.TheBallMod;
import com.theball.entity.GoalEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Dibuja la portería: palos blancos (postes, larguero, postes traseros y raíles del suelo) y la red
 * (techo inclinado, fondo y laterales). Se dibuja en coordenadas locales con la boca mirando al norte y se
 * gira según hacia dónde mire.
 */
public class GoalRenderer extends EntityRenderer<GoalEntity, GoalRenderState> {
	private static final Identifier POST = TheBallMod.id("textures/block/goal_post.png");
	private static final Identifier NET = TheBallMod.id("textures/block/goal_net.png");

	private static final float HW = (float) (GoalEntity.WIDTH / 2.0);
	private static final float H = (float) GoalEntity.HEIGHT;
	private static final float BH = (float) GoalEntity.BACK_HEIGHT;
	private static final float HD = (float) (GoalEntity.DEPTH / 2.0);
	private static final float B = (float) (GoalEntity.BAR / 2.0);
	/** Tamaño de cada cuadrado de red, en bloques. */
	private static final float CELL = 0.4f;

	public GoalRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public GoalRenderState createRenderState() {
		return new GoalRenderState();
	}

	@Override
	public void extractRenderState(GoalEntity entity, GoalRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.facing = entity.getFacing();
	}

	/** El larguero (su caja) puede quedar fuera de la pantalla mientras se ve la red: siempre se dibuja. */
	@Override
	public boolean shouldRender(GoalEntity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick) {
		return true;
	}

	@Override
	public void submit(GoalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;

		poseStack.pushPose();
		// El modelo mira al norte (yRot 180): lo giramos hacia donde mire la boca.
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.facing.toYRot());

		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(POST),
				(pose, consumer) -> frame(pose, consumer, light));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(NET),
				(pose, consumer) -> net(pose, consumer, light));

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	// ---------------------------------------------------------------- estructura

	private static void frame(PoseStack.Pose pose, VertexConsumer c, int light) {
		// Postes delanteros y larguero.
		box(pose, c, -HW - B, 0, -HD - B, -HW + B, H, -HD + B, light);
		box(pose, c, HW - B, 0, -HD - B, HW + B, H, -HD + B, light);
		box(pose, c, -HW - B, H - 2 * B, -HD - B, HW + B, H, -HD + B, light);
		// Postes traseros y barra trasera.
		box(pose, c, -HW - B, 0, HD - B, -HW + B, BH, HD + B, light);
		box(pose, c, HW - B, 0, HD - B, HW + B, BH, HD + B, light);
		box(pose, c, -HW - B, BH - B, HD - B, HW + B, BH + B, HD + B, light);
		// Raíles del suelo a los lados y barras inclinadas del techo.
		box(pose, c, -HW - B, 0, -HD, -HW + B, 2 * B, HD, light);
		box(pose, c, HW - B, 0, -HD, HW + B, 2 * B, HD, light);
	}

	// ---------------------------------------------------------------- red

	private static void net(PoseStack.Pose pose, VertexConsumer c, int light) {
		// Techo inclinado: del larguero a la barra trasera.
		panel(pose, c, light, -HW, H - B, -HD, HW, H - B, -HD, HW, BH, HD, -HW, BH, HD);
		// Fondo.
		panel(pose, c, light, -HW, 0, HD, HW, 0, HD, HW, BH, HD, -HW, BH, HD);
		// Laterales (trapecios).
		panel(pose, c, light, -HW, 0, -HD, -HW, 0, HD, -HW, BH, HD, -HW, H - B, -HD);
		panel(pose, c, light, HW, 0, -HD, HW, 0, HD, HW, BH, HD, HW, H - B, -HD);
	}

	/**
	 * Panel de red entre 4 esquinas (p00, p10, p11, p01), partido en cuadrados de {@link #CELL} para que
	 * cada uno use la textura entera.
	 */
	private static void panel(PoseStack.Pose pose, VertexConsumer c, int light,
			float x00, float y00, float z00, float x10, float y10, float z10,
			float x11, float y11, float z11, float x01, float y01, float z01) {
		float lenU = dist(x00, y00, z00, x10, y10, z10);
		float lenV = dist(x00, y00, z00, x01, y01, z01);
		int nu = Math.max(1, Math.round(lenU / CELL));
		int nv = Math.max(1, Math.round(lenV / CELL));

		// Normal aproximada del panel.
		float ux = x10 - x00, uy = y10 - y00, uz = z10 - z00;
		float vx = x01 - x00, vy = y01 - y00, vz = z01 - z00;
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (nl < 1.0E-6f) {
			return;
		}
		nx /= nl;
		ny /= nl;
		nz /= nl;

		for (int i = 0; i < nu; i++) {
			for (int j = 0; j < nv; j++) {
				float s0 = i / (float) nu;
				float s1 = (i + 1) / (float) nu;
				float t0 = j / (float) nv;
				float t1 = (j + 1) / (float) nv;
				vertex(pose, c, light, bilerp(x00, x10, x11, x01, s0, t0), bilerp(y00, y10, y11, y01, s0, t0), bilerp(z00, z10, z11, z01, s0, t0), 0, 1, nx, ny, nz);
				vertex(pose, c, light, bilerp(x00, x10, x11, x01, s1, t0), bilerp(y00, y10, y11, y01, s1, t0), bilerp(z00, z10, z11, z01, s1, t0), 1, 1, nx, ny, nz);
				vertex(pose, c, light, bilerp(x00, x10, x11, x01, s1, t1), bilerp(y00, y10, y11, y01, s1, t1), bilerp(z00, z10, z11, z01, s1, t1), 1, 0, nx, ny, nz);
				vertex(pose, c, light, bilerp(x00, x10, x11, x01, s0, t1), bilerp(y00, y10, y11, y01, s0, t1), bilerp(z00, z10, z11, z01, s0, t1), 0, 0, nx, ny, nz);
			}
		}
	}

	private static float bilerp(float a00, float a10, float a11, float a01, float s, float t) {
		return a00 * (1 - s) * (1 - t) + a10 * s * (1 - t) + a11 * s * t + a01 * (1 - s) * t;
	}

	private static float dist(float x1, float y1, float z1, float x2, float y2, float z2) {
		float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// ---------------------------------------------------------------- primitivas

	private static void box(PoseStack.Pose pose, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, int light) {
		// Norte (z1) y sur (z2).
		quad(pose, c, light, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
		quad(pose, c, light, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
		// Oeste (x1) y este (x2).
		quad(pose, c, light, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
		quad(pose, c, light, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
		// Arriba (y2) y abajo (y1).
		quad(pose, c, light, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, 0, 1, 0);
		quad(pose, c, light, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, 0, -1, 0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer c, int light,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float nx, float ny, float nz) {
		vertex(pose, c, light, ax, ay, az, 0, 1, nx, ny, nz);
		vertex(pose, c, light, bx, by, bz, 1, 1, nx, ny, nz);
		vertex(pose, c, light, cx, cy, cz, 1, 0, nx, ny, nz);
		vertex(pose, c, light, dx, dy, dz, 0, 0, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, float x, float y, float z, float u, float v,
			float nx, float ny, float nz) {
		c.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
