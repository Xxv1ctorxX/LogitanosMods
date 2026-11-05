package com.theslave.client;

import com.theslave.Aptitudes;
import com.theslave.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Elección de la pareja a fundir y vista previa exacta del resultado antes de gastar el Sello. */
public class FusionScreen extends Screen {
	private final Payloads.AbrirFusion info;
	private final List<int[]> parejas = new ArrayList<>();
	private int elegida;
    private boolean invertir;

	public FusionScreen(Payloads.AbrirFusion info) {
		super(Component.translatable("theslave.fusion.titulo"));
		this.info = info;
		int n = info.candidatos().size();
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				parejas.add(new int[] { i, j });
			}
		}
	}

	private String nombre(int i) {
		String n = info.candidatos().get(i).nombre();
		return n.isEmpty() ? "?" : n;
	}

	@Override
	protected void init() {
		int x = width / 2 - 150;
		int y = height / 2 - 86;
		for (int k = 0; k < parejas.size(); k++) {
			int[] p = parejas.get(k);
			final int idx = k;
			addRenderableWidget(Button.builder(Component.literal(nombre(p[0]) + " + " + nombre(p[1])), bt -> elegida = idx)
					.bounds(x, y + k * 22, 140, 20).build());
		}
        addRenderableWidget(Button.builder(Component.translatable("theslave.fusion.intercambiar"),b -> invertir=!invertir)
                .bounds(width/2+10,height/2+48,140,18).build());

		addRenderableWidget(Button.builder(Component.translatable("theslave.fusion.confirmar"), b -> confirmar())
				.bounds(width / 2 - 150, height / 2 + 90, 140, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("theslave.fusion.cancelar"), b -> onClose())
				.bounds(width / 2 + 10, height / 2 + 90, 140, 20).build());
	}

	private void confirmar() {
		if (parejas.isEmpty()) {
			return;
		}
		int[] p = pareja();
		ClientPlayNetworking.send(new Payloads.FusionElegir(info.altar(), info.candidatos().get(p[0]).entidad(),
				info.candidatos().get(p[1]).entidad()));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		Estilo.fondo(g, width, height);
		Estilo.panel(g, width / 2 - 160, height / 2 - 112, 320, 224);
		g.centeredText(font, Component.translatable("theslave.fusion.titulo"), width / 2, height / 2 - 104, Estilo.HUESO_CLARO);
		if (!parejas.isEmpty()) {
			int[] p = pareja();
			float[] m = Aptitudes.fusion(info.candidatos().get(p[0]).merito(), info.candidatos().get(p[1]).merito());
			int x = width / 2 + 10;
			int y = height / 2 - 66;
			g.text(font, Component.translatable("theslave.fusion.resultado"), x, y - 12, Estilo.ALMA, false);
			for (int i = 0; i < 5; i++) {
				g.text(font, Aptitudes.linea(m, i), x, y + i * 12, Estilo.HUESO_CLARO, false);
			}
			g.text(font, Aptitudes.especialidadTexto(m), x, y + 5 * 12 + 6, Estilo.ALMA, false);
            g.text(font,Component.translatable("theslave.fusion.izquierda",font.plainSubstrByWidth(nombre(p[0]),82)),x,height/2+16,Estilo.HUESO,false);
            g.text(font,Component.translatable("theslave.fusion.derecha",font.plainSubstrByWidth(nombre(p[1]),82)),x,height/2+30,Estilo.HUESO,false);
            retrato(g,p,width/2-92,height/2-14);

		}
		g.text(font, Component.translatable("theslave.fusion.aviso"), width / 2 - 150, height / 2 + 76, Estilo.HUESO, false);
		super.extractRenderState(g, mx, my, partial);
	}

    private int[] pareja() {
        int[] par=parejas.get(elegida);
        return invertir?new int[]{par[1],par[0]}:par;
    }
    private void retrato(GuiGraphicsExtractor g,int[] par,int x,int y) {
        int a=info.candidatos().get(par[0]).variante(),b=info.candidatos().get(par[1]).variante();
        var skin=com.theslave.TheSlaveMod.id("textures/entity/esclavo_f_"+Math.floorMod(b,3)+"_"+Math.floorMod(a,3)+".png");
        cara(g,skin,x+8,y,8,8,8,8);
        cara(g,skin,x+8,y+16,20,20,8,12);
        cara(g,skin,x,y+16,44,20,4,12);
        cara(g,skin,x+24,y+16,36,52,4,12);
        cara(g,skin,x+8,y+40,4,20,4,12);
        cara(g,skin,x+16,y+40,20,52,4,12);
    }
    private void cara(GuiGraphicsExtractor g,net.minecraft.resources.Identifier skin,int x,int y,int u,int v,int w,int h) {
        g.blit(skin,x,y,x+w*2,y+h*2,u/64f,(u+w)/64f,v/64f,(v+h)/64f);
    }

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
