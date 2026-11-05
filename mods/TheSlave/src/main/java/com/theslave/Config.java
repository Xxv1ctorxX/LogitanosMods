package com.theslave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Configuración del servidor: {@code config/theslave/config.json}. */
public final class Config {
	public int presupuestoMinutos = 30;
	public int radioMenas = 24;
    /** Presupuesto compartido de lecturas de bloques: no se multiplica por numero de esclavos. */
    public int escaneoGlobalPorTick = 1024;
    public int escaneoPorEsclavo = 128;
	public int caducidadDias = 7;
	public boolean respetarProtecciones = true;
	public boolean oscuridadRitual = true;
	public boolean temblorCamara = true;
	/** Vinculos simultaneos por jugador (vivos + tumbas pendientes + contrato sin usar). El diseno fija 3. */
	public int maxPorJugador = 3;

	private static Config actual = new Config();

	public static Config get() {
		return actual;
	}
    public void validar() {
        presupuestoMinutos=Math.clamp(presupuestoMinutos,1,120);
        radioMenas=Math.clamp(radioMenas,1,32);
        escaneoGlobalPorTick=Math.clamp(escaneoGlobalPorTick,128,4096);
        escaneoPorEsclavo=Math.clamp(escaneoPorEsclavo,16,256);
        caducidadDias=Math.clamp(caducidadDias,1,365);maxPorJugador=3;
    }

	public static Path carpeta() {
		return FabricLoader.getInstance().getConfigDir().resolve("theslave");
	}

	public static void cargar() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Path file = carpeta().resolve("config.json");
		try {
			Files.createDirectories(carpeta());
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					Config c = gson.fromJson(r, Config.class);
					if (c != null) {
						actual = c;
					}
					if (actual.maxPorJugador != 3) {
						TheSlaveMod.LOGGER.info("maxPorJugador {} -> 3 (limite fijado por el diseno)", actual.maxPorJugador);
						actual.maxPorJugador = 3;
					}
				}
			}
            actual.validar();
			try (Writer w = Files.newBufferedWriter(file)) {
				gson.toJson(actual, w);
			}
		} catch (IOException | RuntimeException e) {
			TheSlaveMod.LOGGER.warn("No se pudo leer la configuración, se usan los valores por defecto", e);
		}
	}
}
