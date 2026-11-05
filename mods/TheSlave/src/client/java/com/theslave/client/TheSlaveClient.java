package com.theslave.client;

import com.theslave.TheSlaveMod;
import com.theslave.net.Payloads;
import com.theslave.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class TheSlaveClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LatigoRenderer.registrar();
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LatigoAnimado.TYPE, (p, ctx) ->
				ctx.client().execute(() -> LatigoAnimacion.recibir(p)));
		EntityRenderers.register(ModEntities.ESCLAVO, EsclavoRenderer::new);
		EntityRenderers.register(ModEntities.LA_MUERTE, LaMuerteRenderer::new);
		EntityRenderers.register(ModEntities.NOTARIO, NotarioRenderer::new);
		EntityRenderers.register(ModEntities.UMBRAL, UmbralRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(Payloads.AbrirOrdenes.TYPE, (p, ctx) ->
				ctx.client().execute(() -> ctx.client().setScreenAndShow(new OrdenesScreen(p))));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.AbrirFusion.TYPE, (p, ctx) ->
				ctx.client().execute(() -> ctx.client().setScreenAndShow(new FusionScreen(p))));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.AbrirNotario.TYPE, (p, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof NotarioScreen n && n.es(p.entidad())) {
				n.actualizar(p);
			} else {
				mc.setScreenAndShow(new NotarioScreen(p));
			}
		}));

		ClientPlayNetworking.registerGlobalReceiver(Payloads.AbrirNombre.TYPE, (p, ctx) ->
				ctx.client().execute(() -> ctx.client().setScreenAndShow(new NombreScreen(p.entidad()))));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.TumbaInfo.TYPE, (p, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof TumbaScreen t && t.es(p.pos())) {
				t.actualizar(p);
			} else {
				mc.setScreenAndShow(new TumbaScreen(p));
			}
		}));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Escena.TYPE, (p, ctx) ->
				ctx.client().execute(() -> RitualOverlay.recibir(p)));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> { RitualOverlay.tick(); LatigoAnimacion.tick(); });
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { RitualOverlay.reiniciar(); LatigoAnimacion.reiniciar(); });
		HudElementRegistry.addLast(TheSlaveMod.id("ritual"), RitualOverlay::dibujar);
	}
}
