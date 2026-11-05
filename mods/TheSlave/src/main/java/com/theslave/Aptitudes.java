package com.theslave;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Aptitudes de un esclavo: cinco méritos (positivo = mejor) que suman aproximadamente cero. El mérito se traduce en el
 * factor de cada aptitud: apetito, minería y recuperación son tiempos o consumos (menos es mejor); resistencia y
 * agilidad son capacidades (más es mejor). Un normal se mueve entre -0,15 y +0,20; solo un fusionado especializado
 * puede llegar a +0,60.
 */
public final class Aptitudes {
	public static final int APETITO = 0;
	public static final int RESISTENCIA = 1;
	public static final int AGILIDAD = 2;
	public static final int MINERIA = 3;
	public static final int RECUPERACION = 4;
	public static final String[] CLAVES = { "apetito", "resistencia", "agilidad", "mineria", "recuperacion" };

	private Aptitudes() {
	}

	public static float[] generar(RandomSource r) {
		float[] m = new float[5];
		float suma = 0;
		for (int i = 0; i < 5; i++) {
			m[i] = (r.nextFloat() * 2f - 1f) * 0.2f;
			suma += m[i];
		}
		float media = suma / 5f;
		float max = 0;
		for (int i = 0; i < 5; i++) {
			m[i] -= media;
			max = Math.max(max, m[i]);
		}
		// Siempre hay una especialidad clara (al menos +0,10) y ninguna carencia mayor que -0,15.
		if (max < 0.10f) {
			int k = r.nextInt(5);
			float extra = 0.10f - m[k];
			m[k] = 0.10f;
			for (int i = 0; i < 5; i++) {
				if (i != k) {
					m[i] -= extra / 4f;
				}
			}
		}
		return equilibrar(m, 0.20f, -0.15f);
	}

	/** Acota cada mérito y reparte el exceso para que la suma no sea positiva. */
	private static float[] equilibrar(float[] m, float tope, float suelo) {
		for (int pasada = 0; pasada < 4; pasada++) {
			for (int i = 0; i < 5; i++) {
				m[i] = Math.max(suelo, Math.min(tope, m[i]));
			}
			float suma = 0;
			for (float v : m) {
				suma += v;
			}
			if (suma <= 0.02f) {
				break;
			}
			int especial = especialidad(m);
			for (int i = 0; i < 5; i++) {
				if (i != especial) {
					m[i] -= suma / 4f;
				}
			}
		}
		return m;
	}

	public static int especialidad(float[] m) {
		int k = 0;
		for (int i = 1; i < m.length; i++) {
			if (m[i] > m[k]) {
				k = i;
			}
		}
		return k;
	}

	/** Resultado de unir dos individuos: la mejor especialidad de cada uno (potenciada, con tope) y el resto compensado. */
	public static float[] fusion(float[] a, float[] b) {
		float[] r = new float[5];
		int sa = especialidad(a);
		int sb = especialidad(b);
		for (int i = 0; i < 5; i++) {
			r[i] = (a[i] + b[i]) / 2f;
		}
        if (sa==sb) {
            r[sa]=Math.min(0.60f,Math.max(r[sa],(a[sa]+b[sa])*1.5f));
        } else {
		    r[sa] = Math.min(0.60f, Math.max(r[sa], a[sa] * 1.8f));
		    r[sb] = Math.min(0.60f, Math.max(r[sb], b[sb] * 1.8f));
        }
		float suma = 0;
		for (float v : r) {
			suma += v;
		}
		// Lo que se gana en las especialidades se paga en el resto, hasta un máximo de -0,30 por aptitud.
		if (suma > 0.02f) {
			int n = 0;
			for (int i = 0; i < 5; i++) {
				if (i != sa && i != sb) {
					n++;
				}
			}
			for (int i = 0; i < 5; i++) {
				if (i != sa && i != sb) {
					r[i] = Math.max(-0.30f, r[i] - suma / Math.max(1, n));
				}
			}
		}
		for (int i = 0; i < 5; i++) {
			r[i] = Math.max(-0.30f, Math.min(0.60f, r[i]));
		}
		return r;
	}

	/** Factor multiplicativo de la aptitud i (consumo o tiempo si menos es mejor). */
	public static double factor(float[] m, int i) {
		if (m == null) {
			return 1.0;
		}
		return switch (i) {
			case RESISTENCIA, AGILIDAD -> 1.0 + m[i];
			default -> Math.max(0.3, 1.0 - m[i]);
		};
	}

	public static Component especialidadTexto(float[] m) {
		return Component.translatable("theslave.apt.esp." + CLAVES[especialidad(m)]);
	}

	/** Una línea por aptitud con su variación respecto a lo normal. */
	public static Component linea(float[] m, int i) {
		double f = factor(m, i);
		int pct = (int) Math.round((f - 1.0) * 100);
		boolean bueno = m[i] >= 0;
		return Component.translatable("theslave.apt." + CLAVES[i]).append(Component.literal(": " + (pct >= 0 ? "+" : "") + pct + " %")
				.withStyle(bueno ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
	}
}
