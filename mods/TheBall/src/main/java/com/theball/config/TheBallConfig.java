package com.theball.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theball.TheBallMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuración del servidor: {@code config/theball.json}. Todo lo "de balance" está aquí para poder ajustarlo sin
 * recompilar. Si el archivo no existe se crea con los valores por defecto; si le faltan claves (versión nueva del
 * mod) se rellenan y se vuelve a guardar. {@code /theball reload} lo vuelve a leer.
 *
 * Los nombres de los campos se escriben en el JSON en snake_case ({@code requireFreeSpace -> require_free_space}).
 */
public final class TheBallConfig {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.disableHtmlEscaping()
			.create();

	private static TheBallConfig instance = new TheBallConfig();

	public static TheBallConfig get() {
		return instance;
	}

	// ------------------------------------------------------------------ secciones

	private static final int CONFIG_VERSION = 4;
	/** Para migrar configs antiguas (se sube solo al cargar). */
	public int configVersion = 0;
	public Throne throne = new Throne();
	public Xp xp = new Xp();
	public Rewards rewards = new Rewards();
	public Crown crown = new Crown();
	public Scepter scepter = new Scepter();
	public Explosive explosive = new Explosive();
	public World world = new World();

	public static final class World {
		/** Completa con aluminio los chunks antiguos (solo roca virgen y una vez por chunk). false = apagado. */
		public boolean retrogen = true;
	}

	public static final class Throne {
		/** Antes de añadir una hoja en el trono se comprueba que la bola quepa. */
		public boolean requireFreeSpace = true;
		/** Distancia a la que se ve la barra de jefe de la Reina mientras alguien la alimenta. */
		public int bossBarRange = 24;
		/** Ranking top 3 de aportes en el HUD (a la izquierda) mientras se mira a la Reina. */
		public boolean tributeWall = true;
	}

	public static final class Xp {
		/** XP de cada hoja añadida en el trono: siempre el mismo, sea la hoja 10 o la 2000 (sin multiplicadores). */
		public double perSheet = 1.0;
		/** Bonus de cada hito de 100 hojas (20 hitos), que cobra quien pone la hoja que lo completa. */
		public int[] milestoneXp = {12, 18, 26, 37, 51, 69, 92, 129, 201, 294, 415, 598, 904, 1303, 1815, 2472, 3307,
				4367, 5708, 22523};
		public boolean payInCreative = false;
	}

	public static final class Rewards {
		public double minShareForLootPercent = 10.0;
		public double minShareForMedalPercent = 2.0;
		/** El Rey se decide en las últimas N hojas (con 1 papel por hoja, los últimos N papeles). */
		public int finalStretchSheets = 350;
		/** Cuenta atrás del Cónclave (la coronación) desde que la Reina llega a 2000 hojas. */
		public int conclaveCountdownSeconds = 600;
		/** Para optar a la corona hay que haber puesto al menos este % del tramo final. El Rey es el de mayor aporte total entre los que cumplen. */
		public double minFinalStretchPercent = 10.0;
		public boolean oncePerPlayer = false;
		/** Si nadie llega al porcentaje mínimo, el premio va al que más papel ha puesto. */
		public boolean rewardTopIfNone = true;
		public boolean broadcastCoronation = true;
		public int foodBocata = 8;
		public int foodDurum = 4;
	}

	public static final class Crown {
		public BallMode ballMode = new BallMode();
		public Bounce bounce = new Bounce();
		public Reflect reflect = new Reflect();
		public Decree decree = new Decree();
	}

	/** Modo Bola: doble toque de Shift para entrar y salir; sin duración ni espera. */
	public static final class BallMode {
		public boolean enabled = true;
		public float damage = 4.0f;
	}

	public static final class Bounce {
		public boolean enabled = true;
		public double minFallBlocks = 3.0;
		public double maxBounceBlocks = 6.0;
	}

	public static final class Reflect {
		public boolean enabled = true;
		public double chancePercent = 30.0;
	}

	public static final class Decree {
		public boolean enabled = true;
		public double radius = 8.0;
		public int cooldownTicks = 900;
	}

	public static final class Scepter {
		public BouncyBall bouncyBall = new BouncyBall();
		public ExplosiveBall explosiveBall = new ExplosiveBall();
		public Dash dash = new Dash();
	}

	/** Mantener click derecho: lanza una bola explosiva (más carga = más rápida). */
	public static final class ExplosiveBall {
		public int costFoil = 5;
		public int chargeTicks = 20;
		public float damage = 14.0f;
		public double radius = 4.5;
		public int cooldownTicks = 40;
	}

	/** Agachado + click: Embestida, un impulso hacia delante que aparta a los enemigos del camino. */
	public static final class Dash {
		public int costFoil = 2;
		public double speed = 1.6;
		public float damage = 4.0f;
		public int cooldownTicks = 100;
	}

	public static final class BouncyBall {
		public int costFoil = 1;
		public boolean freeWithFullSet = true;
		public float damage = 6.0f;
		public int maxBounces = 4;
		public int cooldownTicks = 8;
	}

	public static final class Explosive {
		public boolean enabled = true;
		public int costFoil = 50;
		public int chargeTicks = 60;
		/** Duracion del modo (tras la ascension), en ticks. */
		public int modeTicks = 400;
		/** Cargas (bolas en orbita) con las que empieza el modo. */
		public int orbs = 8;
		/** Velocidad de vuelo horizontal en el modo (bloques por tick; 0,8 = 16 bloques/s). */
		public double flySpeed = 0.8;
		/** Bolas de una salva (mantener click derecho). */
		public int salvoShots = 3;
		/** Cargas que gasta la bola gigante. */
		public int giantCost = 3;
		/** Cargas que gasta el golpe al suelo (agachado + click). */
		public int slamCost = 3;
		public float slamDamage = 25.0f;
		public double slamRadius = 6.0;
		public double slamRange = 48.0;
		/** Dano de las bolas en orbita a quien se acerca (cada 0,5 s). */
		public float orbitDamage = 3.0f;
		public float shotDamage = 6.0f;
		public double shotRadius = 3.0;
		public float finaleDamage = 30.0f;
		public double finaleRadius = 7.0;
		public int cooldownTicks = 3600;
		public boolean breakBlocks = false;
		public int maxParticlesPerTick = 200;
	}

	// ------------------------------------------------------------------ utilidades

	/** XP de una hoja: constante (el bonus grande va aparte, por hito: {@code xp.milestone_xp}). */
	public double xpForSheet(int sheet) {
		return sheet < 1 ? 0.0 : Math.max(0.0, xp.perSheet);
	}

	// ------------------------------------------------------------------ carga / guardado

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("theball.json");
	}

	/** Lee el archivo (o lo crea). Devuelve null si todo fue bien, o el mensaje de error si el JSON está mal. */
	public static String load() {
		Path path = path();
		TheBallConfig loaded = null;
		String error = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, TheBallConfig.class);
			} catch (Exception e) {
				error = e.getMessage();
				TheBallMod.LOGGER.error("No se pudo leer {}: se mantienen los valores anteriores", path, e);
			}
		}
		if (error != null) {
			return error;
		}
		if (loaded == null) {
			loaded = new TheBallConfig();
		}
		loaded.fillMissing();
		instance = loaded;
		save();
		return null;
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			TheBallMod.LOGGER.error("No se pudo guardar {}", path, e);
		}
	}

	/** Gson deja a null las secciones que no estén en el archivo: las rellenamos con los valores por defecto. */
	private void fillMissing() {
		TheBallConfig d = new TheBallConfig();
		if (throne == null) throne = d.throne;
		if (xp == null) xp = d.xp;
		if (xp.milestoneXp == null || xp.milestoneXp.length == 0) xp.milestoneXp = d.xp.milestoneXp;
		if (rewards == null) rewards = d.rewards;
		if (crown == null) crown = d.crown;
		if (crown.ballMode == null) crown.ballMode = d.crown.ballMode;
		if (crown.bounce == null) crown.bounce = d.crown.bounce;
		if (crown.reflect == null) crown.reflect = d.crown.reflect;
		if (crown.decree == null) crown.decree = d.crown.decree;
		if (scepter == null) scepter = d.scepter;
		if (scepter.bouncyBall == null) scepter.bouncyBall = d.scepter.bouncyBall;
		if (scepter.explosiveBall == null) scepter.explosiveBall = d.scepter.explosiveBall;
		if (scepter.dash == null) scepter.dash = d.scepter.dash;
		if (configVersion < CONFIG_VERSION) {
			// v2 (1.1.0 final): el Decreto real pasa a estar activo por defecto.
			if (configVersion < 2) {
				crown.decree.enabled = true;
			}
			// v3: la bola explosiva del cetro es mas grande
			scepter.explosiveBall.radius = Math.max(scepter.explosiveBall.radius, 4.5);
			// v4: la bola explosiva hace mas dano que la rebotona
			scepter.explosiveBall.damage = Math.max(scepter.explosiveBall.damage, 14.0f);
			configVersion = CONFIG_VERSION;
		}
		if (explosive == null) explosive = d.explosive;
		if (world == null) world = d.world;
		// Valores que rompen el servidor (división por cero, bucles vacíos): se fuerzan a un mínimo.
		explosive.orbs = Math.max(1, explosive.orbs);
		explosive.chargeTicks = Math.max(1, explosive.chargeTicks);
		explosive.modeTicks = Math.max(1, explosive.modeTicks);
		explosive.salvoShots = Math.max(1, explosive.salvoShots);
		if (scepter.explosiveBall.chargeTicks < 1) scepter.explosiveBall.chargeTicks = 1;
	}
}
