package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Esfera de diámetro 1 centrada en el origen (malla latitud/longitud), con la textura de papel arrugado.
 * Las normales se calculan DESPUÉS de rotar, así la luz del mundo no gira con la bola (solo la textura).
 * La usan la bola, la esfera del Modo Bola y los proyectiles del cetro.
 */
public final class SphereMesh {
	/** Luz máxima (los proyectiles brillan). */
	public static final int FULL_BRIGHT = 0xF000F0;

	private static final int LAT = 16;
	private static final int LON = 32;
	private static final int COLS = LON + 1;
	private static final int VERTS = (LAT + 1) * COLS;

	/** Dirección unitaria de cada vértice de la esfera (sin rotar). */
	private static final float[] DIR = new float[VERTS * 3];
	private static final float[] UV = new float[VERTS * 2];

	static {
		for (int i = 0; i <= LAT; i++) {
			// Los polos se separan un pelo (0,02 rad): con vertices coincidentes los cuadrilateros degenerados de
			// la tapa se pintaban como un circulo oscuro en uno de los polos.
			double theta = Math.PI * i / LAT; // 0 = polo norte
			if (i == 0) {
				theta = 0.02;
			} else if (i == LAT) {
				theta = Math.PI - 0.02;
			}
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

	private SphereMesh() {
	}

	public static void add(PoseStack.Pose pose, VertexConsumer consumer, Quaternionf rotation, int light, int color) {
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
				vertex(pose, consumer, rotated, a, light, color);
				vertex(pose, consumer, rotated, d, light, color);
				vertex(pose, consumer, rotated, c, light, color);
				vertex(pose, consumer, rotated, b, light, color);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float[] rotated, int idx, int light, int color) {
		float nx = rotated[idx * 3];
		float ny = rotated[idx * 3 + 1];
		float nz = rotated[idx * 3 + 2];
		consumer.addVertex(pose, nx * 0.5f, ny * 0.5f, nz * 0.5f)
				.setColor(color)
				.setUv(UV[idx * 2], UV[idx * 2 + 1])
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
