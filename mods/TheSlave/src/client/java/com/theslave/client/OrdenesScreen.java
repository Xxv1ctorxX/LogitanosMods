package com.theslave.client;

import com.theslave.Aptitudes;
import com.theslave.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Órdenes de un esclavo propio: seguir al dueño o esperar donde está. */
public class OrdenesScreen extends Screen {
	private final Payloads.AbrirOrdenes info;

	public OrdenesScreen(Payloads.AbrirOrdenes info) {
		super(Component.translatable("theslave.orden.titulo"));
		this.info = info;
	}

	@Override
	protected void init() {
		int x = width / 2;
		int y = height / 2 + 56;
		Button seguir = Button.builder(Component.translatable("theslave.orden.seguirme"), b -> enviar(0))
				.bounds(x - 70, y, 140, 20).build();
		Button esperar = Button.builder(Component.translatable("theslave.orden.esperar"), b -> enviar(1))
				.bounds(x - 70, y + 26, 140, 20).build();
		seguir.active = !info.sigue();
		esperar.active = info.sigue();
		addRenderableWidget(seguir);
		addRenderableWidget(esperar);
	}

	private void enviar(int orden) {
		ClientPlayNetworking.send(new Payloads.Orden(info.entidad(), orden));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		Estilo.fondo(g, width, height);
		Estilo.panel(g, width / 2 - 120, height / 2 - 106, 240, 216);
		String nombre = info.nombre().isEmpty() ? "?" : info.nombre();
		g.centeredText(font, Component.literal(nombre), width / 2, height / 2 - 96, Estilo.HUESO_CLARO);
		g.centeredText(font, Component.translatable(info.fusionado() ? "theslave.orden.fusionado" : "theslave.orden.normal"),
				width / 2, height / 2 - 82, Estilo.HUESO);
		g.centeredText(font, Component.translatable(info.sigue() ? "theslave.orden.estado_sigue" : "theslave.orden.estado_espera"),
				width / 2, height / 2 - 68, Estilo.ALMA);
        g.centeredText(font,Aptitudes.especialidadTexto(info.merito()),width/2,height/2-48,Estilo.ALMA);
        for(int i=0;i<5;i++)g.text(font,Aptitudes.linea(info.merito(),i),width/2-108,height/2-29+i*14,Estilo.HUESO_CLARO,false);
		super.extractRenderState(g, mx, my, partial);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
