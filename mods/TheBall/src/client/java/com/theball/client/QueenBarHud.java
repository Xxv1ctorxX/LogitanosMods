package com.theball.client;

import com.theball.TheBallMod;
import com.theball.entity.AluminumBallEntity;
import com.theball.network.QueenBarPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Barra de la Reina, dibujada por el cliente (ligera: un par de rectángulos y una línea de texto, y nada si no se mira
 * a la Reina). Aparece con fundido, el relleno se desliza suave hacia su valor y el texto es una sola línea sin iconos.
 * El servidor solo manda cifras cuando miras a la Reina (QueenBarPayload).
 */
public final class QueenBarHud {
	private static final int WIDTH = 182;
	private static final int HEIGHT = 5;
	private static final int TOP = 34;
	/** Si deja de llegar el latido del servidor, la barra se esconde sola (desconexión, cambio de mundo...). */
	private static final long TIMEOUT_MS = 1500;

	private static boolean wanted;
	private static boolean crowned;
	private static int sheets;
	private static int shareTenths;
	private static List<String> topNames = List.of();
	private static List<Integer> topFoil = List.of();
	private static long lastPacket;
	private static float alpha;
	private static float shown = -1f; // fracción dibujada (se desliza hacia la real)

	private QueenBarHud() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(QueenBarPayload.TYPE, (payload, context) -> {
			if (!payload.show()) {
				wanted = false;
				return;
			}
			if (!wanted && alpha <= 0.01f) {
				shown = -1f; // aparece ya en su valor, sin animar desde cero
			}
			wanted = true;
			sheets = payload.sheets();
			shareTenths = payload.shareTenths();
			crowned = payload.crowned();
			topNames = payload.topNames();
			topFoil = payload.topFoil();
			lastPacket = System.currentTimeMillis();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, TheBallMod.id("queen_bar"), QueenBarHud::render);
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (wanted && System.currentTimeMillis() - lastPacket > TIMEOUT_MS) {
			wanted = false;
		}
		float dt = Math.min(delta.getRealtimeDeltaTicks(), 4f);
		alpha = Mth.clamp(alpha + (wanted ? 0.18f : -0.12f) * dt, 0f, 1f);
		if (alpha <= 0.01f) {
			return;
		}
		if (sheets < 0) {
			// Coronamiento en curso: sin barra ni ranking, solo el nombre en dorado.
			Font f = Minecraft.getInstance().font;
			g.centeredText(f, Component.translatable("bossbar.theball.queen_ceremony"), g.guiWidth() / 2, TOP, (int) (alpha * 255) << 24 | 0xFFD040);
			return;
		}
		float target = Mth.clamp(sheets / (float) AluminumBallEntity.MAX_SHEETS, 0f, 1f);
		shown = shown < 0f ? target : shown + (target - shown) * Math.min(1f, 0.25f * dt);

		Font font = Minecraft.getInstance().font;
		int x = (g.guiWidth() - WIDTH) / 2;
		int y = TOP;
		int a = (int) (alpha * 255) << 24;

		// texto: una línea
		String share = String.format("%.1f", shareTenths / 10.0);
		Component text;
		if (crowned || sheets >= AluminumBallEntity.MAX_SHEETS) {
			text = Component.translatable("bossbar.theball.queen_crowned", share);
		} else {
			int next = (sheets / 100 + 1) * 100;
			text = Component.translatable("bossbar.theball.queen", sheets, AluminumBallEntity.MAX_SHEETS, next, share);
		}
		g.centeredText(font, text, g.guiWidth() / 2, y - 12, a | (crowned ? 0xFFD040 : 0xFFFFFF));

		// marco + fondo
		g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, (int) (alpha * 176) << 24);
		g.fill(x, y, x + WIDTH, y + HEIGHT, (int) (alpha * 200) << 24 | 0x1A1E24);
		// relleno: franja clara arriba y algo más oscura abajo
		int fill = Math.round(WIDTH * shown);
		if (fill > 0) {
			int top = crowned ? 0xFFE58A : 0xF2F6FA;
			int bottom = crowned ? 0xD9A21E : 0x9FB0C0;
			g.fill(x, y, x + fill, y + HEIGHT - 2, a | top);
			g.fill(x, y + HEIGHT - 2, x + fill, y + HEIGHT, a | bottom);
		}
		// marcas de hito (una cada 5 %: son los 20 hitos)
		int marks = (int) (alpha * 150) << 24 | 0x1A1E24;
		for (int i = 1; i < 20; i++) {
			g.fill(x + WIDTH * i / 20, y, x + WIDTH * i / 20 + 1, y + HEIGHT, marks);
		}
		renderRanking(g, font);
	}

	/** Ranking del tributo: panel estrecho arriba a la izquierda, solo mientras se mira a la Reina. */
	private static void renderRanking(GuiGraphicsExtractor g, Font font) {
		int n = Math.min(topNames.size(), topFoil.size());
		if (n == 0) {
			return;
		}
		String[] medals = {"1", "2", "3"};
		int[] colors = {0xFFD040, 0xDDE4EA, 0xD08A50};
		int x = 6;
		int y = 60; // por debajo del texto y la barra de la Reina
		int rowH = 10;
		int width = font.width(Component.translatable("hologram.theball.tribute_title")) + 4;
		String[] rows = new String[n];
		for (int i = 0; i < n; i++) {
			rows[i] = medals[i] + ". " + topNames.get(i) + "  " + topFoil.get(i);
			width = Math.max(width, font.width(rows[i]) + 4);
		}
		int height = rowH * (n + 1) + 3;
		g.fill(x - 2, y - 2, x + width + 2, y + height - 1, (int) (alpha * 140) << 24);
		int a = (int) (alpha * 255) << 24;
		g.text(font, Component.translatable("hologram.theball.tribute_title"), x, y, a | 0xFFD040, false);
		for (int i = 0; i < n; i++) {
			g.text(font, rows[i], x, y + rowH * (i + 1) + 1, a | colors[i], false);
		}
	}
}
