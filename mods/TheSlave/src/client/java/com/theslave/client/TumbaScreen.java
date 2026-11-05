package com.theslave.client;

import com.theslave.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** La lapida: nombre, epitafio y la lista de requisitos del ritual. */
public class TumbaScreen extends Screen {
	private Payloads.TumbaInfo info;
	private Button recoger;
	private int ticks;

	public TumbaScreen(Payloads.TumbaInfo info) {
		super(Component.translatable("theslave.tumba.titulo"));
		this.info = info;
	}

	public void actualizar(Payloads.TumbaInfo nueva) {
		this.info = nueva;
		if (recoger != null) {
			recoger.active = nueva.listo() && !nueva.enCurso();
		}
	}

	public boolean es(BlockPos pos) {
		return info.pos().equals(pos);
	}

	@Override
	protected void init() {
		int x = width / 2;
		int y = height / 2 + 84;
		recoger = Button.builder(Component.translatable("theslave.tumba.recoger_alma"), b -> recogerAlma())
				.bounds(x - 60, y, 120, 20).build();
		recoger.active = info.listo() && !info.enCurso();
		addRenderableWidget(recoger);
	}

	private void recogerAlma() {
		if (info.listo() && !info.enCurso()) {
			ClientPlayNetworking.send(new Payloads.AccionTumba(info.pos(), 1));
			onClose();
		}
	}

	@Override
	public void tick() {
		if (++ticks % 10 == 0) {
			ClientPlayNetworking.send(new Payloads.AccionTumba(info.pos(), 0));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 257 || e.key() == 335) {
			recogerAlma();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		Estilo.fondo(g, width, height);
		int cx = width / 2;
		int top = height / 2 - 110;
		int w = 200;
		int h = 190;
		// Remate redondeado de la lapida hecho con franjas
		for (int i = 0; i < 30; i++) {
			int ancho = (int) (Math.sqrt(900 - (30 - i) * (30 - i)) * (w / 2.0 / 30.0));
			g.fill(cx - ancho, top + i, cx + ancho, top + i + 1, Estilo.PIEDRA);
		}
		Estilo.panel(g, cx - w / 2, top + 30, w, h - 30);
		g.fill(cx - w / 2 + 6, top + 36, cx + w / 2 - 6, top + 37, Estilo.PIEDRA_OSCURA);

		Component nombre = info.nombre().isEmpty() ? Component.translatable("theslave.tumba.sin_nombre") : Component.literal(info.nombre());
		g.centeredText(font, nombre, cx, top + 44, Estilo.HUESO_CLARO);
		g.centeredText(font, Component.translatable("theslave.tumba.epitafio", info.dia()), cx, top + 58, Estilo.HUESO);
		g.centeredText(font, Component.translatableWithFallback("theslave.causa." + info.causa(), "Causa desconocida"), cx, top + 70, Estilo.HUESO);
		if (info.muertes() > 1) {
			g.centeredText(font, Component.translatable("theslave.tumba.muertes", info.muertes()), cx, top + 82, Estilo.SANGRE);
		}
		int y = top + 100;
		for (Payloads.Linea l : info.lineas()) {
			int color = l.cumplida() ? Estilo.ALMA : Estilo.HUESO;
			Estilo.hueco(g, cx - w / 2 + 12, y, 8, 8, l.cumplida());
			if (l.cumplida()) {
				g.fill(cx - w / 2 + 14, y + 2, cx - w / 2 + 18, y + 6, Estilo.ALMA);
			}
			g.text(font, Component.translatable("theslave.req." + l.clave()), cx - w / 2 + 26, y, color, false);
			if (!l.detalle().isEmpty()) {
				g.text(font, l.detalle(), cx + w / 2 - 12 - font.width(l.detalle()), y, color, false);
			}
			y += 11;
		}
		if (info.enCurso()) {
			g.centeredText(font, Component.translatable("theslave.tumba.en_curso"), cx, y + 6, Estilo.SANGRE);
		}
		super.extractRenderState(g, mx, my, partial);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
