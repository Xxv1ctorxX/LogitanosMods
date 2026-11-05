package com.theslave.client;

import com.theslave.comercio.Comercio;
import com.theslave.comercio.Comercio.Oferta;
import com.theslave.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mostrador de un Notario de Ceniza: ofertas con su precio exacto; el servidor cobra y decide. */
public class NotarioScreen extends Screen {
	private Payloads.AbrirNotario info;
	private static final int FILA = 27;

	public NotarioScreen(Payloads.AbrirNotario info) {
		super(Component.translatable("theslave.notario.titulo"));
		this.info = info;
	}

	public boolean es(int entidad) {
		return info.entidad() == entidad;
	}

	public void actualizar(Payloads.AbrirNotario nueva) {
		this.info = nueva;
		rebuildWidgets();
	}

	private boolean disponible(Oferta o) {
		return switch (o) {
			case ADQUISICION -> !info.reserva() && info.libres() > 0;
			case TRIBUTO -> info.difunto();
			case SELLO -> info.fusionables();
			case DOBLE -> info.difuntoFusionado();
			case DEVOLVER -> info.devolver();
			default -> true;
		};
	}

	private int top() {
		return Math.max(4, (height - panelAlto()) / 2);
	}

	private int panelAlto() {
		return 62 + Oferta.values().length * FILA + 16;
	}

	@Override
	protected void init() {
		int x = width / 2 + 62;
		int y = top() + 62;
		for (Oferta o : Oferta.values()) {
			Button b = Button.builder(Component.translatable(o == Oferta.DEVOLVER ? "theslave.notario.devolver" : "theslave.notario.comprar"),
					bt -> ClientPlayNetworking.send(new Payloads.Comprar(info.entidad(), o.ordinal())))
					.bounds(x, y + 2, 64, 20).build();
			b.active = disponible(o);
			addRenderableWidget(b);
			y += FILA;
		}
	}

	private static String precio(Comercio.Precio p) {
		List<String> t = new ArrayList<>();
		if (p.hierro() > 0) {
			t.add(Component.translatable("theslave.notario.p_hierro", p.hierro()).getString());
		}
		if (p.oro() > 0) {
			t.add(Component.translatable("theslave.notario.p_oro", p.oro()).getString());
		}
		if (p.esmeralda() > 0) {
			t.add(Component.translatable("theslave.notario.p_esmeralda", p.esmeralda()).getString());
		}
		if (p.diamante() > 0) {
			t.add(Component.translatable("theslave.notario.p_diamante", p.diamante()).getString());
		}
		if (p.almasPorTipo() > 0) {
			t.add(Component.translatable("theslave.notario.p_almas", p.almasPorTipo()).getString());
		}
		return String.join(", ", t);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		Estilo.fondo(g, width, height);
		int w = 330;
		int x0 = width / 2 - w / 2;
		int t = top();
		Estilo.panel(g, x0, t, w, panelAlto());
		g.centeredText(font, Component.translatable("theslave.notario.titulo"), width / 2, t + 8, Estilo.HUESO_CLARO);
		g.centeredText(font, Component.translatable("theslave.notario.frase"), width / 2, t + 20, Estilo.HUESO);
		g.text(font, Component.translatable("theslave.notario.vinculos", info.ocupadas(), 3), x0 + 10, t + 34, Estilo.HUESO, false);
		g.text(font, Component.translatable("theslave.notario.almas", info.hambrienta(), info.vigilante(), info.acechante()),
				x0 + 10, t + 46, Estilo.ALMA, false);
		int y = t + 62;
		for (Oferta o : Oferta.values()) {
			boolean ok = disponible(o);
			int color = ok ? Estilo.HUESO_CLARO : Estilo.HUESO;
			g.text(font, Component.translatable("theslave.oferta." + o.name().toLowerCase()), x0 + 10, y + 3, color, false);
			String p = o == Oferta.DEVOLVER ? Component.translatable("theslave.notario.p_devolver").getString()
					: precio(Comercio.precio(o, info.ocupadas()));
			g.text(font, p, x0 + 10, y + 14, Estilo.HUESO, false);
			y += FILA;
		}
		if (!info.mensaje().isEmpty()) {
			boolean bien = info.mensaje().startsWith("ok.");
			g.centeredText(font, Component.translatable("theslave.notario." + (bien ? "hecho" : "error"), Component.translatable(
					bien ? "theslave.oferta." + info.mensaje().substring(3) : info.mensaje())), width / 2, y + 2,
					bien ? Estilo.ALMA : Estilo.SANGRE);
		}
		super.extractRenderState(g, mx, my, partial);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
