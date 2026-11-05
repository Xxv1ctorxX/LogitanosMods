package com.theslave.client;

import com.theslave.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Bautizo: el esclavo recien creado pide un nombre. */
public class NombreScreen extends Screen {
	private final int entidad;
	private EditBox caja;

	public NombreScreen(int entidad) {
		super(Component.translatable("theslave.nombre.titulo"));
		this.entidad = entidad;
	}

	@Override
	protected void init() {
		caja = new EditBox(font, width / 2 - 80, height / 2 - 6, 160, 20, Component.translatable("theslave.nombre.titulo"));
		caja.setMaxLength(24);
		addRenderableWidget(caja);
		setInitialFocus(caja);
		addRenderableWidget(Button.builder(Component.translatable("theslave.nombre.aceptar"), b -> enviar())
				.bounds(width / 2 - 50, height / 2 + 24, 100, 20).build());
	}

	private void enviar() {
		String n = caja.getValue().trim();
		if (!n.isEmpty()) {
			ClientPlayNetworking.send(new Payloads.Nombre(entidad, n));
		}
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 257 || e.key() == 335) {
			enviar();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		Estilo.fondo(g, width, height);
		Estilo.panel(g, width / 2 - 110, height / 2 - 60, 220, 120);
		g.centeredText(font, Component.translatable("theslave.nombre.titulo"), width / 2, height / 2 - 44, Estilo.HUESO_CLARO);
		g.centeredText(font, Component.translatable("theslave.nombre.pista"), width / 2, height / 2 - 28, Estilo.HUESO);
		super.extractRenderState(g, mx, my, partial);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
