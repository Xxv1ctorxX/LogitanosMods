package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.theball.TheBallMod;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Corona hecha por código: aro de 12 segmentos (radio 0,5, alto 0,22), seis puntas con una perla en la punta y
 * las joyas en la parte delantera (+Z). Base en y = 0. Una misma textura gris (mitad izquierda: metal, mitad
 * derecha: faceta de joya) se tiñe con el color de cada pieza, así hay corona de plata, de oro y joyas de colores
 * sin texturas extra.
 *
 * Siempre de oro. Variantes ({@code tier}): 0 rubi · 1 +zafiros · 2 +esmeraldas · 3 +rubi trasero · 4 coronada (diamantes).
 */
public final class CrownMesh {
	public static final Identifier TEXTURE = TheBallMod.id("textures/entity/crown.png");

	public static final int SILVER = 0xFFE6ECF2;
	public static final int GOLD = 0xFFFFC83A;
	private static final int DARK_GOLD = 0xFFC48A1E;
	private static final int BLUE = 0xFF3A7BFF;
	private static final int RED = 0xFFE03A3A;
	private static final int GREEN = 0xFF3ACF5A;
	private static final int DIAMOND = 0xFFC8F8FF;
	private static final int PEARL = 0xFFFFFFFF;

	private static final int SEGMENTS = 12;
	private static final float R = 0.5f;
	private static final float R_IN = 0.43f;
	private static final float BAND = 0.22f;

	private CrownMesh() {
	}

	/** Variante según las hojas de la bola (y si está coronada). */
	public static int tierFor(int sheets, boolean crowned) {
		if (crowned) {
			return 4;
		}
		return Math.min(3, sheets / 500);
	}

	public static void add(PoseStack.Pose pose, VertexConsumer c, int light, int tier) {
		// La corona de la Reina es siempre de oro; las joyas y el diamante crecen con las hojas.
		int metal = GOLD;
		int dark = DARK_GOLD;
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = i * Math.PI * 2.0 / SEGMENTS;
			double a1 = (i + 1) * Math.PI * 2.0 / SEGMENTS;
			float x0 = (float) Math.sin(a0), z0 = (float) Math.cos(a0);
			float x1 = (float) Math.sin(a1), z1 = (float) Math.cos(a1);
			float nx = (float) Math.sin((a0 + a1) / 2), nz = (float) Math.cos((a0 + a1) / 2);
			// aro: exterior, interior y canto superior
			quad(pose, c, light, metal, 0f, 0.5f,
					x0 * R, 0, z0 * R, x1 * R, 0, z1 * R, x1 * R, BAND, z1 * R, x0 * R, BAND, z0 * R, nx, 0, nz);
			quad(pose, c, light, dark, 0f, 0.5f,
					x1 * R_IN, 0, z1 * R_IN, x0 * R_IN, 0, z0 * R_IN, x0 * R_IN, BAND, z0 * R_IN, x1 * R_IN, BAND, z1 * R_IN, -nx, 0, -nz);
			quad(pose, c, light, metal, 0f, 0.5f,
					x0 * R_IN, BAND, z0 * R_IN, x0 * R, BAND, z0 * R, x1 * R, BAND, z1 * R, x1 * R_IN, BAND, z1 * R_IN, 0, 1, 0);
			// moldura inferior y superior (un poco mas ancha y mas oscura)
			float RR = R + 0.02f;
			quad(pose, c, light, dark, 0f, 0.5f,
					x0 * RR, 0, z0 * RR, x1 * RR, 0, z1 * RR, x1 * RR, 0.04f, z1 * RR, x0 * RR, 0.04f, z0 * RR, nx, 0, nz);
			quad(pose, c, light, dark, 0f, 0.5f,
					x0 * RR, BAND - 0.04f, z0 * RR, x1 * RR, BAND - 0.04f, z1 * RR, x1 * RR, BAND, z1 * RR, x0 * RR, BAND, z0 * RR, nx, 0, nz);
			// puntas: piramide de 4 caras en los segmentos pares, alternando altas y bajas
			if (i % 2 == 0) {
				float h = BAND + (i % 4 == 0 ? 0.34f : 0.24f);
				double am = (a0 + a1) / 2;
				float mr = (R + R_IN) / 2;
				float px = (float) Math.sin(am) * mr;
				float pz = (float) Math.cos(am) * mr;
				quad(pose, c, light, metal, 0f, 0.5f,
						x0 * R, BAND, z0 * R, x1 * R, BAND, z1 * R, px, h, pz, px, h, pz, nx, 0.4f, nz);
				quad(pose, c, light, dark, 0f, 0.5f,
						x1 * R_IN, BAND, z1 * R_IN, x0 * R_IN, BAND, z0 * R_IN, px, h, pz, px, h, pz, -nx, 0.4f, -nz);
				quad(pose, c, light, metal, 0f, 0.5f,
						x1 * R, BAND, z1 * R, x1 * R_IN, BAND, z1 * R_IN, px, h, pz, px, h, pz, z1, 0.4f, -x1);
				quad(pose, c, light, dark, 0f, 0.5f,
						x0 * R_IN, BAND, z0 * R_IN, x0 * R, BAND, z0 * R, px, h, pz, px, h, pz, -z0, 0.4f, x0);
				// bola en la punta: perla blanca, o diamante en la Reina coronada
				box(pose, c, light, tier >= 4 ? DIAMOND : PEARL, 0.5f, 1f, px, h + 0.03f, pz, 0.04f);
			}
		}
		// --- joyas en el aro, de delante (+Z) hacia atras
		float gy = BAND / 2;
		float gr = R + 0.015f;
		gem(pose, c, light, RED, 0, gr, gy, 0.055f);
		if (tier >= 1) {
			gem(pose, c, light, BLUE, Math.PI / 3, gr, gy, 0.05f);
			gem(pose, c, light, BLUE, -Math.PI / 3, gr, gy, 0.05f);
		}
		if (tier >= 2) {
			gem(pose, c, light, GREEN, 2 * Math.PI / 3, gr, gy, 0.05f);
			gem(pose, c, light, GREEN, -2 * Math.PI / 3, gr, gy, 0.05f);
		}
		if (tier >= 3) {
			gem(pose, c, light, RED, Math.PI, gr, gy, 0.05f);
		}
		// (quitado: el "gran diamante" delantero sobresalía del aro y rompía la silueta; la coronada
		// ya se distingue por los diamantes de las puntas)
	}

	private static void gem(PoseStack.Pose pose, VertexConsumer c, int light, int color, double angle, float r, float y, float s) {
		box(pose, c, light, color, 0.5f, 1f, (float) Math.sin(angle) * r, y, (float) Math.cos(angle) * r, s);
	}

	/** Caja centrada en (x, y, z) con semilados (sx, sy, sz). */
	public static void cuboid(PoseStack.Pose pose, VertexConsumer c, int light, int color, float u0, float u1,
			float x, float y, float z, float sx, float sy, float sz) {
		float x1 = x - sx, x2 = x + sx, y1 = y - sy, y2 = y + sy, z1 = z - sz, z2 = z + sz;
		quad(pose, c, light, color, u0, u1, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
		quad(pose, c, light, color, u0, u1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
		quad(pose, c, light, color, u0, u1, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
		quad(pose, c, light, color, u0, u1, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
		quad(pose, c, light, color, u0, u1, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, 0, 1, 0);
		quad(pose, c, light, color, u0, u1, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, 0, -1, 0);
	}

	/** Cubito centrado en (x, y, z) de semilado s. */
	private static void box(PoseStack.Pose pose, VertexConsumer c, int light, int color, float u0, float u1,
			float x, float y, float z, float s) {
		float x1 = x - s, x2 = x + s, y1 = y - s, y2 = y + s, z1 = z - s, z2 = z + s;
		quad(pose, c, light, color, u0, u1, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
		quad(pose, c, light, color, u0, u1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
		quad(pose, c, light, color, u0, u1, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
		quad(pose, c, light, color, u0, u1, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
		quad(pose, c, light, color, u0, u1, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, 0, 1, 0);
		quad(pose, c, light, color, u0, u1, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, 0, -1, 0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int color, float u0, float u1,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float nx, float ny, float nz) {
		vertex(pose, c, light, color, ax, ay, az, u0, 1, nx, ny, nz);
		vertex(pose, c, light, color, bx, by, bz, u1, 1, nx, ny, nz);
		vertex(pose, c, light, color, cx, cy, cz, u1, 0, nx, ny, nz);
		vertex(pose, c, light, color, dx, dy, dz, u0, 0, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int color, float x, float y, float z,
			float u, float v, float nx, float ny, float nz) {
		c.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
