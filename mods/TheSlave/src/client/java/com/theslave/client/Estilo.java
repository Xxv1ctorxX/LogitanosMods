package com.theslave.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Paleta y piezas de dibujo comunes de las pantallas (sin iconos: solo formas y texto). */
final class Estilo {
	static final int PIEDRA = 0xFF2A2C31;
	static final int PIEDRA_CLARA = 0xFF4A4D55;
	static final int PIEDRA_OSCURA = 0xFF17181C;
	static final int ALMA = 0xFF5FD7E8;
	static final int HUESO = 0xFF9A968B;
	static final int HUESO_CLARO = 0xFFD8D3C4;
	static final int SANGRE = 0xFF8A1C1C;

	private Estilo() {
	}

	static void fondo(GuiGraphicsExtractor g, int w, int h) {
		g.fillGradient(0, 0, w, h, 0xE6000000, 0xF2060810);
	}

	/** Marco biselado de piedra. */
	static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PIEDRA);
		g.fill(x, y, x + w, y + 2, PIEDRA_CLARA);
		g.fill(x, y, x + 2, y + h, PIEDRA_CLARA);
		g.fill(x, y + h - 2, x + w, y + h, PIEDRA_OSCURA);
		g.fill(x + w - 2, y, x + w, y + h, PIEDRA_OSCURA);
	}

	static void hueco(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean lleno) {
		g.fill(x, y, x + w, y + h, PIEDRA_OSCURA);
		g.outline(x, y, w, h, lleno ? ALMA : PIEDRA_CLARA);
	}
}
