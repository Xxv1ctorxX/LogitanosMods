package com.theslave.client;

import com.theslave.net.Payloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Cine del ritual: barras negras, vineta y subtitulos letra a letra. */
public final class RitualOverlay {
	private static int fase;
	private static float intensidad;
	private static float suave;
	private static String subtitulo = "";
	private static long inicioSub;
	private static long ultimo;

	private RitualOverlay() {
	}

	public static void recibir(Payloads.Escena e) {
		fase = e.fase();
		intensidad = Float.isFinite(e.intensidad())?Math.clamp(e.intensidad(),0,1):0;
        if (e.subtitulo().isEmpty() && ahora() - inicioSub > 5000) subtitulo = "";
		if (!e.subtitulo().isEmpty() && !e.subtitulo().equals(subtitulo)) {
			subtitulo = e.subtitulo();
			inicioSub = ahora();
		}

		if (fase == 0) {
			subtitulo = "";
		}
		ultimo = ahora();
	}

	public static void reiniciar() {
		fase = 0;
		subtitulo = "";
		suave = 0;
	}

	public static void tick() {
		float objetivo = fase == 0 ? 0f : Math.max(0.35f, intensidad);
		suave += (objetivo - suave) * 0.06f;
		if (fase != 0 && ahora() - ultimo > 15000) {
			reiniciar();
		}
	}

	public static void dibujar(GuiGraphicsExtractor g, DeltaTracker dt) {
		if (suave < 0.01f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int a = (int) Math.min(255, suave * 170);
		int capas = 14;
		for (int i = 0; i < capas; i++) {
			int alfa = (int) (a * 0.55f * (1f - i / (float) capas));
			int c = alfa << 24;
			int gx = i * w / 90;
			int gy = i * h / 90;
			g.fill(0, gy, gx + 1, h - gy, c);
			g.fill(w - gx - 1, gy, w, h - gy, c);
		}
		g.fill(0, 0, w, h, ((int) (a * 0.25f)) << 24);
		int barra = (int) (h * 0.05f * Math.min(1f, suave * 1.6f));
		g.fill(0, 0, w, barra, 0xFF000000);
		g.fill(0, h - barra, w, h, 0xFF000000);
		if (!subtitulo.isEmpty()) {
			Minecraft mc = Minecraft.getInstance();
			int n = (int) Math.clamp((ahora()-inicioSub)*32/1000,0,subtitulo.length());
			String visible = subtitulo.substring(0, n);
			int x = (w - mc.font.width(subtitulo)) / 2;
			g.text(mc.font, visible, x, h - barra - 26, Estilo.HUESO_CLARO, true);
		}
	}
    private static long ahora() {return System.nanoTime()/1_000_000;}
}
