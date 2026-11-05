package com.theslave.client;

/** Geometria independiente del renderer: una sola cuerda, con longitud constante. */
public final class LatigoForma {
	public record Punto(double x, double y, double z, double radio) { }

	private LatigoForma() { }

	public static double suave(double t) {
		t = Math.clamp(t, 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}

	/** Anticipacion 0..3, despliegue 3..9, retorno 9..18 (ticks, no frames). */
	public static double despliegue(double tick) {
		if (tick <= 3 || tick >= 18) return 0;
		return tick <= 9 ? suave((tick - 3) / 6) : 1 - suave((tick - 9) / 9);
	}

	public static Punto[] animar(Punto[] reposo, double tick, double dx, double dy, double dz) {
		double abierto = despliegue(tick);
		if (abierto == 0) return reposo;
		Punto[] puntos = new Punto[reposo.length];
		puntos[0] = reposo[0];
		double norma = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (norma < 1e-9) { dx = 0; dy = 0; dz = -1; norma = 1; }
		dx /= norma; dy /= norma; dz /= norma;
		for (int i = 1; i < puntos.length; i++) {
			Punto a = reposo[i - 1], b = reposo[i];
			double vx = b.x - a.x, vy = b.y - a.y, vz = b.z - a.z;
			double largo = Math.sqrt(vx * vx + vy * vy + vz * vz);
			double s = i / (double) (puntos.length - 1);
			// La base se libera antes que la punta; no se superponen dos modelos.
			double mezcla = suave(abierto * 1.55 - s * 0.55);
			double pulso = Math.exp(-Math.pow((s - abierto) / 0.18, 2));
			double onda = Math.sin(Math.PI * 2 * (s - abierto)) * pulso * 0.8 * (1 - abierto);
			double nx = vx / largo * (1 - mezcla) + dx * mezcla;
			double ny = vy / largo * (1 - mezcla) + (dy + onda) * mezcla;
			double nz = vz / largo * (1 - mezcla) + dz * mezcla;
			double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (n < 1e-9) { nx = dx; ny = dy; nz = dz; n = 1; }
			Punto anterior = puntos[i - 1];
			puntos[i] = new Punto(anterior.x + nx / n * largo, anterior.y + ny / n * largo,
					anterior.z + nz / n * largo, b.radio);
		}
		return puntos;
	}
}
