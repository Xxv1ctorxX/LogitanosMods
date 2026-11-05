package com.theball.throne;

import com.theball.config.TheBallConfig;
import com.theball.entity.AluminumBallEntity;
import com.theball.power.ExplosiveAbility;
import com.theball.registry.ModAdvancements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

/**
 * El Cónclave: la coronación de la Reina de Aluminio (2000 hojas en el trono). Ocurre una sola vez por bola (bit
 * {@link Tribute#BIT_CROWN}) y avanza en el tick de la bola ({@link AluminumBallEntity#coronationTick}, que se guarda,
 * así que la cuenta atrás sobrevive a un reinicio).
 *
 * <pre>
 *  0           aviso a todo el servidor: cuenta atrás (5 min) y los 3 candidatos (los 3 primeros del ranking)
 *  C           los candidatos conectados (si falta alguien, entra el siguiente del ranking) se teletransportan
 *              junto a la Reina y suben en haces de luz (3 s); quedan quietos e invulnerables
 *  C+100       juicio: un haz de luz recorre a los candidatos y se detiene en uno, que es descartado (cae despacio);
 *              se repite hasta que queda uno
 *  ...         el elegido recibe la corona (anillo de luz que baja a su cabeza), destello y cohetes
 *  +100        se entregan los premios y todos quedan libres
 * </pre>
 * El Rey es el primero del ranking entre los candidatos que han aportado lo mínimo del tramo final
 * ({@link Rewards#judgmentOrder}); el espectáculo solo lo dramatiza.
 */
public final class Coronation {
	/** Separación de los tres cuando están juntos delante de la Reina (radianes). */
	private static final double GROUP_SPACING = 0.45;
	/** Velocidad de la órbita (radianes por tick). */
	private static final double ORBIT_W = 0.04;
	/** Cuando los tres ya giran con su separación perfecta, empieza la subida lenta. */
	private static final int RISE_START = 100;
	private static final int ASCEND_TICKS = RISE_START + 140;
	/** La órbita frena durante los últimos ticks de la subida y se quedan quietos arriba. */
	private static final int ORBIT_DECEL = 60;
	private static final int HOLD_END = ASCEND_TICKS + 40;
	/** Cada ronda: 60 ticks recorriendo, 20 ticks parado en el elegido y 20 de pausa. */
	private static final int ROUND_TICKS = 100;
	private static final int ROUND_SCAN = 80;
	private static final int CROWN_DESCEND = 40;
	private static final int CELEBRATION = 100;
	private static final int PROTECT_TICKS = 400;
	/** Cuánto sube la Reina durante el espectáculo (bloques) y cuánto tarda en bajar al final (ticks). */
	private static final double QUEEN_LIFT = 10.0;
	private static final int QUEEN_DESCEND = 60;
	/** Prólogo: los tres juntos delante de la Reina mientras ella habla (ticks) y cuándo dice cada frase. */
	private static final int PRELUDE_TICKS = 200; // = SPEECH_AT[2]: el puesto 1 empieza a orbitar justo cuando se dice «que comience el juicio»
	/** Máximo que se espera a que lleguen los tres (ticks); pasado ese tiempo se les coloca. */
	private static final int GATHER_MAX = 400;
	private static final int[] SPEECH_AT = {20, 110, 200};

	/** Espectáculos en marcha (solo memoria: si se reinicia el servidor en pleno juicio, se corona directamente). */
	private static final Map<UUID, Show> SHOWS = new HashMap<>();
	/** Jugadores invulnerables hasta cierto momento (mientras participan y mientras caen). */
	private static final Map<UUID, Long> PROTECTED = new HashMap<>();

	/** Modo prueba (/theball coronation test): el jugador + 2 aldeanos hacen de candidatos. Nada se entrega ni se guarda. */
	private static final class TestInfo {
		final List<LivingEntity> villagers = new ArrayList<>();
		UUID player;
		int savedSheets;
		boolean savedCrowned;
	}

	private static final Map<UUID, TestInfo> TESTS = new HashMap<>();
	/** Los aldeanos de prueba, para poder tratarlos como candidatos. */
	private static final Map<UUID, LivingEntity> TEST_ACTORS = new HashMap<>();

	/** Candidato: un jugador conectado o un aldeano de prueba. */
	private static LivingEntity actor(ServerLevel level, UUID id) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
		if (p != null) {
			return p;
		}
		LivingEntity e = TEST_ACTORS.get(id);
		return e != null && e.isAlive() ? e : null;
	}

	/**
	 * Lanza el espectáculo ya, con el jugador y 2 aldeanos como candidatos (el jugador será el Rey). La bola pasa a
	 * 2000 hojas solo durante la prueba. false si ya hay una coronación en marcha en esa bola.
	 */
	public static boolean startTest(ServerLevel level, AluminumBallEntity ball, ServerPlayer player) {
		if (ball.coronationTick >= 0 || TESTS.containsKey(ball.getUUID())) {
			return false;
		}
		TestInfo ti = new TestInfo();
		ti.player = player.getUUID();
		ti.savedSheets = ball.getSheets();
		ti.savedCrowned = ball.isCrowned();
		for (int i = 0; i < 2; i++) {
			var v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (v == null) {
				continue;
			}
			v.setNoAi(true);
			v.setPermanentlyInvulnerable(true);
			v.setSilent(true);
			v.setCustomName(Component.literal("Candidato " + (i + 2)));
			v.setCustomNameVisible(true);
			v.setPos(player.getX() + (i == 0 ? 1.5 : -1.5), player.getY(), player.getZ());
			level.addFreshEntity(v);
			ti.villagers.add(v);
			TEST_ACTORS.put(v.getUUID(), v);
		}
		TESTS.put(ball.getUUID(), ti);
		ball.setSheets(AluminumBallEntity.MAX_SHEETS);
		ball.coronationTick = countdownTicks(); // salta la cuenta atrás: el espectáculo empieza en el próximo tick
		return true;
	}

	/** Fin (o interrupción) de una prueba: se suelta a todos, se quitan los aldeanos y la bola vuelve a como estaba. */
	private static void endTest(ServerLevel level, AluminumBallEntity ball, TestInfo ti) {
		ball.setLift(0.0f);
		ball.coronationTick = -1;
		ball.setSheets(ti.savedSheets);
		ball.setCrowned(ti.savedCrowned);
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(ti.player);
		if (p != null) {
			p.setNoGravity(false);
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PROTECT_TICKS, 0, false, false, true));
			PROTECTED.put(ti.player, level.getGameTime() + PROTECT_TICKS);
		}
		for (LivingEntity v : ti.villagers) {
			TEST_ACTORS.remove(v.getUUID());
			v.discard();
		}
	}

	/** Un descartado que se aparta a la derecha del elegido, con buena vista, para ver cómo lo coronan. */
	private static final class Flyer {
		Vec3 pos;
		/** Ángulo actual alrededor de la Reina (se desliza por el círculo, sin atravesarla). */
		double angle;
		/** 0 = el primer espectador (el más cercano al elegido), 1 = el segundo. */
		final int slot;

		Flyer(Vec3 pos, int slot, double angle) {
			this.pos = pos;
			this.slot = slot;
			this.angle = angle;
		}
	}

	private static final class Show {
		/** order.get(0) es el Rey; el resto se descarta del último al segundo. */
		final List<UUID> order = new ArrayList<>();
		/** Ángulo actual de cada candidato alrededor de la Reina. */
		final Map<UUID, Double> angle = new HashMap<>();
		/** Ángulo de cada uno mientras están juntos delante de la Reina (prólogo). */
		final Map<UUID, Double> group = new HashMap<>();
		/** 0 = juntos delante de la Reina, 1 = separados en el círculo. */
		/** Tick del espectáculo (tras el prólogo); -1 = aún no ha empezado el movimiento. */
		int showTick = -1;
		/** Tick en que los descartados ya están en su puesto y empieza la coronación del elegido (-1 = aún no). */
		int crownAt = -1;
		/** Espera de cada candidato antes de deslizarse a su puesto (el de la derecha primero). */
		final Map<UUID, Integer> delay = new HashMap<>();
		/** Jugadores a los que ya se les ha puesto la música (los que lleguen después, también la oyen). */
		final Set<UUID> musicListeners = new HashSet<>();
		/** Ya están los tres reunidos delante de la Reina y empieza el diálogo (readyAt = tick en que ocurrió). */
		boolean ready;
		int readyAt;
		final Map<UUID, String> names = new HashMap<>();
		final Set<UUID> frozen = new HashSet<>();
		/** Descartados: se sitúan a los lados, cerca del elegido, hasta que acaba el espectáculo. */
		final Map<UUID, Flyer> flyers = new HashMap<>();
		UUID king;
		/** La corona que la Reina pone sobre la cabeza del Rey (se quita al terminar). */
		com.theball.entity.PowerVisualEntity halo;
	}

	private Coronation() {
	}

	/** ¿Está este jugador protegido por el Cónclave? (lo usa el filtro de daño) */
	public static boolean isProtected(ServerPlayer player) {
		Long until = PROTECTED.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() > until) {
			PROTECTED.remove(player.getUUID());
			return false;
		}
		return true;
	}

	/** La bola deja el trono (trono roto, etc.) en pleno espectáculo: se libera a los candidatos. */
	public static void abort(ServerLevel level, AluminumBallEntity ball) {
		stopMusic(level);
		TestInfo ti = TESTS.remove(ball.getUUID());
		if (ti != null) {
			discardHalo(SHOWS.remove(ball.getUUID()));
			endTest(level, ball, ti);
			return;
		}
		Show show = SHOWS.remove(ball.getUUID());
		if (show == null) {
			return;
		}
		discardHalo(show);
		long now = level.getGameTime();
		for (UUID id : show.order) {
			LivingEntity p = actor(level, id);
			if (p != null) {
				p.setNoGravity(false);
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PROTECT_TICKS, 0, false, false, true));
				PROTECTED.put(id, now + PROTECT_TICKS);
			}
		}
	}

	private static void discardHalo(Show show) {
		if (show != null && show.halo != null) {
			show.halo.discard();
			show.halo = null;
		}
	}

	/** Quien presencie el espectáculo (hasta 100 bloques) oye la música: se revisa cada segundo y se la pone a quien se acerque. */
	private static final double MUSIC_RANGE = 100.0;

	private static void syncMusic(ServerLevel level, AluminumBallEntity ball, Show show) {
		Vec3 near = ThroneLogic.top(ball);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(near) < MUSIC_RANGE * MUSIC_RANGE && show.musicListeners.add(p.getUUID())) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
						new com.theball.network.MusicPayload(com.theball.network.MusicPayload.START, 0));
			}
		}
	}

	private static void sendMusic(ServerLevel level, Vec3 near, int action, int ticks) {
		for (ServerPlayer p : level.players()) {
			if (near == null || p.position().distanceToSqr(near) < 96.0 * 96.0) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.theball.network.MusicPayload(action, ticks));
			}
		}
	}

	/** Corta la música de golpe (se interrumpe el espectáculo). */
	private static void stopMusic(ServerLevel level) {
		sendMusic(level, null, com.theball.network.MusicPayload.STOP, 0);
	}

	/** Fin normal: la música se difumina poco a poco (5 s) cuando todos ya están en el suelo. */
	private static void fadeMusic(ServerLevel level) {
		sendMusic(level, null, com.theball.network.MusicPayload.FADE, 100);
	}

	/** ¿Está el espectáculo del Coronamiento en marcha en esta Reina? (el HUD solo muestra su nombre) */
	public static boolean inProgress(AluminumBallEntity ball) {
		return SHOWS.containsKey(ball.getUUID());
	}

	/** Salta la cuenta atrás: el espectáculo empieza en el próximo tick. false si no hay cuenta atrás en marcha. */
	public static boolean skipCountdown(AluminumBallEntity ball) {
		if (ball.coronationTick < 0 || ball.coronationTick >= countdownTicks()) {
			return false;
		}
		ball.coronationTick = countdownTicks();
		return true;
	}

	/** Al parar el servidor: el estado en memoria no debe pasar a otro mundo. */
	public static void clearAll() {
		SHOWS.clear();
		PROTECTED.clear();
		TESTS.clear();
		TEST_ACTORS.clear();
	}

	// ------------------------------------------------------------------ aviso

	public static void start(ServerLevel level, AluminumBallEntity ball) {
		ball.coronationTick = 0;
		int seconds = countdownTicks() / 20;
		List<Contributor> candidates = Rewards.candidates(ball.getTribute());
		StringBuilder names = new StringBuilder();
		for (Contributor c : candidates) {
			names.append(names.isEmpty() ? "" : ", ").append(c.name()).append(" (")
					.append(String.format("%.1f", ball.getTribute().sharePercent(c.uuid()))).append(" %)");
		}
		if (TheBallConfig.get().rewards.broadcastCoronation) {
			var players = level.getServer().getPlayerList();
			players.broadcastSystemMessage(Component.translatable("message.theball.conclave.start", clock(seconds))
					.withStyle(ChatFormatting.GOLD), false);
			players.broadcastSystemMessage(Component.translatable("message.theball.conclave.candidates", names.toString())
					.withStyle(ChatFormatting.YELLOW), false);
		}
		// Aviso legible en la barra de acciones (el título a pantalla grande y en negrita no se podía leer).
		Component notice = Component.translatable("message.theball.conclave.start", clock(seconds)).withStyle(ChatFormatting.GOLD);
		Vec3 topPos = ThroneLogic.top(ball);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(topPos) < 96.0 * 96.0) {
				p.sendOverlayMessage(notice);
			}
		}
		Fanfare.play(level, ThroneLogic.top(ball), com.theball.registry.ModSounds.CORONATION_START, 4.0f, 1.0f);
	}

	private static int countdownTicks() {
		return Math.max(20, TheBallConfig.get().rewards.conclaveCountdownSeconds * 20);
	}

	private static String clock(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	// ------------------------------------------------------------------ tick

	public static void tick(ServerLevel level, AluminumBallEntity ball) {
		int t = ball.coronationTick++;
		int countdown = countdownTicks();
		if (t < countdown) {
			tickCountdown(level, ball, t, countdown);
			return;
		}
		int u = t - countdown;
		Show show = SHOWS.get(ball.getUUID());
		if (u == 0) {
			show = begin(level, ball);
			if (show == null) {
				finish(level, ball, null); // nadie conectado: se decide por la regla y los premios quedan pendientes
				return;
			}
		} else if (show == null) {
			finish(level, ball, null); // reinicio del servidor en pleno juicio
			return;
		}
		if (u % 20 == 0) {
			syncMusic(level, ball, show);
		}
		if (!show.ready) {
			tickGather(level, ball, show, u);
			return;
		}
		int w = u - show.readyAt;
		speak(level, ball, w);
		if (w < PRELUDE_TICKS) {
			tickPrelude(level, ball, show, w);
			return;
		}
		tickShow(level, ball, show, w - PRELUDE_TICKS);
	}

	/** Reunión suave: cada candidato sube un poco desde donde está y se desliza delante de la Reina; se espera a los tres. */
	private static void tickGather(ServerLevel level, AluminumBallEntity ball, Show show, int u) {
		Vec3 center = center(ball);
		boolean all = true;
		for (UUID id : show.frozen) {
			LivingEntity p = actor(level, id);
			if (p == null) {
				continue;
			}
			Vec3 target = slot(ball, show, id, 0.0);
			boolean timeout = u >= GATHER_MAX;
			place(level, p, target, center, 0.55, timeout ? 0.0 : 100.0 * 100.0);
			p.setNoGravity(true);
			PROTECTED.put(id, level.getGameTime() + 20L * 60);
			if (p.level() != level || p.position().distanceToSqr(target) > 2.25) {
				all = false;
			}
			if (u % 20 == 0 && p instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.theball.queen.gather").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
			}
			if (u % 6 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, true, false, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
			}
		}
		if (all || u >= GATHER_MAX + 20) {
			show.ready = true;
			show.readyAt = u;
		}
	}

	/** Frases de la Reina (barra de acciones): cada una se mantiene ~4 s; la última sigue mientras empieza la órbita. */
	private static void speak(ServerLevel level, AluminumBallEntity ball, int w) {
		Vec3 center = center(ball);
		for (int i = 0; i < SPEECH_AT.length; i++) {
			// cada frase se mantiene ~4,5 s en la barra de acciones (se reenvía cada segundo)
			if (w >= SPEECH_AT[i] && w < SPEECH_AT[i] + 80 && (w - SPEECH_AT[i]) % 20 == 0) {
				Component line = Component.translatable("message.theball.queen.speech." + (i + 1)).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC);
				Vec3 top = ThroneLogic.top(ball);
				for (ServerPlayer sp : level.players()) {
					if (sp.position().distanceToSqr(top) < 96.0 * 96.0) {
						sp.sendOverlayMessage(line);
					}
				}
				if (w == SPEECH_AT[i]) {
					Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_BELL.value(), 3.0f, 0.6f + i * 0.2f);
				}
			}
		}
	}

	/** Prólogo: los candidatos juntos delante de la Reina, que les habla; luego los separa y los sube. */
	private static void tickPrelude(ServerLevel level, AluminumBallEntity ball, Show show, int u) {
		Vec3 center = center(ball);
		for (UUID id : show.frozen) {
			LivingEntity p = actor(level, id);
			if (p == null) {
				continue;
			}
			place(level, p, slot(ball, show, id, 0.0), center);
			p.setNoGravity(true);
			PROTECTED.put(id, level.getGameTime() + 20L * 60);
			if (u % 4 == 0) {
				beam(level, center, p.position().add(0.0, 1.0, 0.0), false);
			}
		}
	}

	// ------------------------------------------------------------------ cuenta atrás

	private static void tickCountdown(ServerLevel level, AluminumBallEntity ball, int t, int countdown) {
		Vec3 center = ball.position().add(0.0, ball.getDiameter() / 2.0, 0.0);
		double d = ball.getDiameter();
		double progress = t / (double) countdown;
		// Luz que crece despacio alrededor de la Reina.
		if (t % 4 == 0) {
			double a = t * 0.12;
			double r = d / 2.0 + 0.5;
			int n = 1 + (int) (progress * 3);
			for (int k = 0; k < n; k++) {
				double ak = a + k * Math.PI * 2.0 / n;
				level.sendParticles(ParticleTypes.END_ROD, true, false, center.x + Math.cos(ak) * r,
						center.y - d / 2.0 + ((t / 4.0) % 20) / 20.0 * (d + 2.0), center.z + Math.sin(ak) * r, 1, 0, 0, 0, 0);
			}
		}
		if (t % 20 != 0) {
			return;
		}
		int remaining = (countdown - t) / 20;
		// Barra de acción para los que están cerca, con los candidatos.
		String names = candidateNames(ball);
		Component hud = Component.translatable("message.theball.conclave.hud", clock(remaining), names).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(center) < 64.0 * 64.0) {
				p.sendOverlayMessage(hud);
			}
		}
		// Avisos a todo el servidor.
		if (remaining == 480 || remaining == 300 || remaining == 180 || remaining == 120 || remaining == 60 || remaining == 30) {
			level.getServer().getPlayerList().broadcastSystemMessage(
					Component.translatable("message.theball.conclave.remaining", clock(remaining), names)
							.withStyle(ChatFormatting.YELLOW), false);
			Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_BELL.value(), 3.0f, 1.0f);
		}
		if (remaining <= 10 && remaining >= 1) {
			Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_BELL.value(), 3.0f, 0.6f + (10 - remaining) * 0.07f);
			titleNear(level, center, 96.0, Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.empty(), 0, 20, 5);
		}
	}

	private static String candidateNames(AluminumBallEntity ball) {
		StringBuilder sb = new StringBuilder();
		for (Contributor c : Rewards.candidates(ball.getTribute())) {
			sb.append(sb.isEmpty() ? "" : ", ").append(c.name());
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ espectáculo

	/** Reúne a los candidatos y los sube. Devuelve null si no hay nadie conectado. */
	private static Show begin(ServerLevel level, AluminumBallEntity ball) {
		Tribute tribute = ball.getTribute();
		// Los 3 primeros del ranking que estén conectados (los que no, ceden su sitio al siguiente).
		List<Contributor> chosen = new ArrayList<>();
		TestInfo test = TESTS.get(ball.getUUID());
		if (test != null) {
			ServerPlayer tp = level.getServer().getPlayerList().getPlayer(test.player);
			if (tp != null) {
				chosen.add(new Contributor(tp.getUUID(), tp.getGameProfile().name(), 1000, 350)); // el Rey de la prueba
			}
			int k = 0;
			for (LivingEntity v : test.villagers) {
				chosen.add(new Contributor(v.getUUID(), "Candidato " + (k++ + 2), 500, 0));
			}
		} else {
			for (Contributor c : tribute.top(1000)) {
				if (chosen.size() < 3 && level.getServer().getPlayerList().getPlayer(c.uuid()) != null) {
					chosen.add(c);
				}
			}
		}
		if (chosen.isEmpty()) {
			return null;
		}
		Show show = new Show();
		for (int i = 0; i < chosen.size(); i++) {
			Contributor c = chosen.get(i);
			var fd = ball.getThroneFacing();
			double g = Math.atan2(fd.getStepZ(), fd.getStepX()) + (i - (chosen.size() - 1) / 2.0) * GROUP_SPACING;
			show.group.put(c.uuid(), g);
			show.angle.put(c.uuid(), g);
			show.names.put(c.uuid(), c.name());
			// Cada uno empieza a orbitar cuando el anterior ya ha ganado la separación objetivo con él.
			double gap = chosen.size() >= 2 ? Math.PI * 2.0 / chosen.size() - GROUP_SPACING : 0.0;
			show.delay.put(c.uuid(), (int) Math.round(i * gap / ORBIT_W));
		}
		for (Contributor c : Rewards.judgmentOrder(chosen)) {
			show.order.add(c.uuid());
		}
		show.king = show.order.get(0);
		long now = level.getGameTime();
		Vec3 center = center(ball);
		for (UUID id : show.order) {
			LivingEntity p = actor(level, id);
			if (p == null) {
				continue;
			}
			show.frozen.add(id);
			PROTECTED.put(id, now + 20L * 60);
			p.setNoGravity(true);
			p.removeEffect(MobEffects.SLOW_FALLING);
			// Sin teletransporte: cada uno sube un poco desde donde está y se desliza hasta su sitio (ver tickGather).
		}
		SHOWS.put(ball.getUUID(), show);
		// Música de fondo: solo durante el espectáculo (se corta al terminar en finish/abort).
		Vec3 mp = ThroneLogic.top(ball);
		StringBuilder names = new StringBuilder();
		for (UUID id : show.order) {
			names.append(names.isEmpty() ? "" : ", ").append(show.names.get(id));
		}
		noticeNear(level, ThroneLogic.top(ball), 96.0, Component.translatable("title.theball.conclave.begins")
				.withStyle(ChatFormatting.GOLD),
				Component.literal(names.toString()).withStyle(ChatFormatting.YELLOW));
		Fanfare.play(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0f, 1.2f);
		return show;
	}

	private static Vec3 center(AluminumBallEntity ball) {
		return ball.position().add(0.0, ball.getDiameter() / 2.0, 0.0);
	}

	/** Punto del círculo donde flota un candidato; {@code lift} va de 0 (suelo) a 1 (junto a la Reina). */
	/** Ángulo recorrido por la órbita (acumulado) tras {@code s} ticks: constante y frenando al final de la subida. */
	private static double theta(double t) {
		if (t <= 0) {
			return 0.0;
		}
		double e = ASCEND_TICKS - ORBIT_DECEL;
		if (t <= e) {
			return ORBIT_W * t;
		}
		double x = Math.min(1.0, (t - e) / ORBIT_DECEL);
		return ORBIT_W * e + ORBIT_W * ORBIT_DECEL * (x - x * x * x + x * x * x * x / 2.0);
	}

	/** Órbita en orden: el 1 (el de la derecha) empieza a girar, luego el 2 y luego el 3, siempre con la misma separación. */
	private static void updateOrbit(Show show, int u) {
		if (show.order.size() < 2) {
			return;
		}
		for (UUID id : show.order) {
			double g = show.group.getOrDefault(id, 0.0);
			double travel = Math.max(0.0, theta(u) - theta(show.delay.getOrDefault(id, 0)));
			show.angle.put(id, g - travel);
		}
	}

	/** Al principio van elevados un poco; la elevación se funde con la subida (en el suelo vuelven a su altura normal). */
	private static double groupBonus(Show show) {
		if (show.showTick < 0) {
			return 1.0;
		}
		return 1.0 - smooth((show.showTick - RISE_START) / (double) (ASCEND_TICKS - RISE_START));
	}

	private static Vec3 slot(AluminumBallEntity ball, Show show, UUID id, double lift) {
		double a = show.angle.getOrDefault(id, 0.0);
		double d = ball.getDiameter();
		double r = d / 2.0 + 6.5; // a distancia para ver a la Reina entera
		Vec3 base = ball.position();
		double y = base.y + 0.3 + lift * (d * 0.35) + 1.2 * groupBonus(show); // juntos: un poco elevados; arriba, algo por debajo del centro de la Reina
		return new Vec3(base.x + Math.cos(a) * r, y, base.z + Math.sin(a) * r);
	}

	/**
	 * Lleva a un candidato hacia un punto. A los jugadores se les empuja con velocidad (el cliente se mueve solo, así se ve
	 * fluido a 60 fps y su cámara queda libre); solo si están lejos (reunión inicial) se les teletransporta, conservando
	 * hacia dónde miran. Los aldeanos de prueba se colocan directamente y miran a {@code lookAt}.
	 */
	private static void place(ServerLevel level, LivingEntity e, Vec3 at, Vec3 lookAt) {
		place(level, e, at, lookAt, 1.2, 36.0);
	}

	/** {@code smoothDist2}: por encima de esa distancia (al cuadrado) o en otra dimensión se teletransporta. */
	private static void place(ServerLevel level, LivingEntity e, Vec3 at, Vec3 lookAt, double maxSpeed, double smoothDist2) {
		if (e instanceof ServerPlayer sp) {
			Vec3 d = at.subtract(sp.position());
			if (sp.level() != level || d.lengthSqr() > smoothDist2) {
				sp.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(net.minecraft.world.entity.Relative.X_ROT,
						net.minecraft.world.entity.Relative.Y_ROT), 0f, 0f, true);
				sp.setDeltaMovement(Vec3.ZERO);
			} else {
				Vec3 v = d.scale(0.5);
				double len = v.length();
				if (len > maxSpeed) {
					v = v.scale(maxSpeed / len);
				}
				sp.setDeltaMovement(v);
				sp.syncVelocity = true;
			}
			sp.fallDistance = 0.0;
		} else {
			e.teleportTo(level, at.x, at.y, at.z, Set.of(), yawTo(at, lookAt), 0f, true);
		}
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static void tickShow(ServerLevel level, AluminumBallEntity ball, Show show, int u) {
		Vec3 center = center(ball);
		int n = show.order.size();
		show.showTick = u;
		// Línea de tiempo: alinearse (en orden) → subida lenta → juicio → coronación → celebración y bajada.
		int rounds = n - 1;
		int scanStart = HOLD_END;
		int lastDiscard = scanStart + ROUND_TICKS * (rounds - 1) + ROUND_SCAN;
		int judgmentEnd = rounds == 0 ? HOLD_END : lastDiscard;
		// La coronación espera a que los descartados estén en su puesto (crownAt); el anillo baja y luego se corona.
		int crownTick = show.crownAt >= 0 ? show.crownAt + CROWN_DESCEND : 1_000_000;
		int endTick = crownTick + CELEBRATION;
		double lift;
		if (u < RISE_START) {
			lift = 0.0;
		} else if (u < ASCEND_TICKS) {
			lift = smooth((u - RISE_START) / (double) (ASCEND_TICKS - RISE_START));
		} else if (u >= endTick - QUEEN_DESCEND) {
			lift = 1.0 - smooth((u - (endTick - QUEEN_DESCEND)) / (double) QUEEN_DESCEND);
		} else {
			lift = 1.0;
		}
		// La Reina sube con ellos (el trono queda abajo) y todos bajan juntos, suavemente, hasta el suelo.
		ball.setLift((float) (QUEEN_LIFT * lift));
		boolean watchersReady = tickFlyers(level, ball, show, lift);
		moveKingToFront(ball, show, u);
		if (show.crownAt < 0 && u >= judgmentEnd + 40 && (watchersReady || u >= judgmentEnd + 340)) {
			show.crownAt = u;
		}
		// ¿Quién ha dejado la partida? Cuenta como descartado.
		for (UUID id : new ArrayList<>(show.frozen)) {
			if (actor(level, id) == null) {
				show.frozen.remove(id);
			}
		}
		if (show.frozen.isEmpty()) {
			discardHalo(SHOWS.remove(ball.getUUID()));
			finish(level, ball, null);
			return;
		}

		// --- subida y mantenimiento: cada candidato en su sitio, quieto
		if (!show.frozen.isEmpty()) {
			for (UUID id : show.frozen) {
				LivingEntity p = actor(level, id);
				if (p == null) {
					continue;
				}
				Vec3 at = slot(ball, show, id, lift);
				place(level, p, at, center);
				p.setNoGravity(true);
				PROTECTED.put(id, level.getGameTime() + 20L * 60);
			}
		}
		if (u >= RISE_START && u < ASCEND_TICKS) {
			for (UUID id : show.frozen) {
				Vec3 at = slot(ball, show, id, lift);
				Vec3 ground = slot(ball, show, id, 0.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, at.x, (at.y + ground.y) / 2.0, at.z, 4,
						0.25, Math.max(0.1, (at.y - ground.y) / 2.0), 0.25, 0.0);
				if (u % 4 == 0) {
					ring(level, at.x, at.y - 0.9, at.z, 0.9, 8);
				}
			}
			if (u % 15 == 0) {
				Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0f, 0.6f + u / (float) ASCEND_TICKS * 0.8f);
			}
		} else if (u == ASCEND_TICKS) {
			Fanfare.play(level, center, SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.0f);
		}

		// --- juicio: rondas de recorrido y descarte (del último al segundo)
		if (rounds > 0 && u >= scanStart && u < lastDiscard + 1) {
			int r = (u - scanStart) / ROUND_TICKS;
			int local = (u - scanStart) % ROUND_TICKS;
			UUID victim = show.order.get(n - 1 - r);
			if (local < ROUND_SCAN) {
				// quienes siguen en la ronda (el Rey y los descartables que quedan)
				List<UUID> alive = new ArrayList<>();
				for (int i = 0; i <= n - 1 - r; i++) {
					if (show.frozen.contains(show.order.get(i))) {
						alive.add(show.order.get(i));
					}
				}
				if (!alive.isEmpty()) {
					UUID target = local < 60 ? alive.get((local / 10) % alive.size()) : (alive.contains(victim) ? victim : alive.get(alive.size() - 1));
					beam(level, center, slot(ball, show, target, 1.0).add(0.0, 0.9, 0.0), local % 2 == 0);
					if (local % 10 == 0) {
						Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0f, 0.7f + (local < 60 ? 0.15f * ((local / 10) % 3) : 0.5f));
					}
				}
			}
			if (local == ROUND_SCAN) {
				discard(level, ball, show, victim, rounds - r);
			}
		}

		// --- el elegido: haz dorado y anillo de luz que baja a su cabeza
		LivingEntity king = actor(level, show.king);
		if (king == null || !show.frozen.contains(show.king)) {
			UUID next = null;
			for (UUID id : show.order) {
				if (show.frozen.contains(id)) {
					next = id;
					break;
				}
			}
			show.king = next;
			king = next == null ? null : actor(level, next);
		}
		if (king == null) {
			discardHalo(SHOWS.remove(ball.getUUID()));
			finish(level, ball, null);
			return;
		}
		Vec3 kp = king.position();
		if (u > (rounds == 0 ? HOLD_END : lastDiscard) && u < crownTick) {
			if (u % 2 == 0) {
				beam(level, center, kp.add(0.0, 1.0, 0.0), true);
			}
			int since = show.crownAt < 0 ? -1 : u - show.crownAt;
			if (since >= 0 && u % 3 == 0) {
				double hy = kp.y + 3.0 - 2.4 * (since / (double) CROWN_DESCEND);
				ring(level, kp.x, hy, kp.z, 0.7, 10);
			}
			if (u % 20 == 0) {
				Fanfare.play(level, center, SoundEvents.NOTE_BLOCK_BELL.value(), 2.5f, 1.0f + (u % 3) * 0.12f);
			}
		}

		// --- coronación y celebración
		if (u == crownTick) {
			ball.setCrowned(true);
			if (show.halo == null) {
				show.halo = com.theball.entity.PowerVisualEntity.create(com.theball.registry.ModEntities.POWER_VISUAL, level, king,
						com.theball.entity.PowerVisualEntity.KIND_HALO);
				level.addFreshEntity(show.halo);
			}
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, false, kp.x, kp.y + 2.0, kp.z, 1, 0, 0, 0, 0);
			level.sendParticles(new DustParticleOptions(0xFFD040, 2.0f), true, false, kp.x, kp.y + 1.5, kp.z, 60, 0.6, 0.8, 0.6, 0.0);
			Fanfare.play(level, kp, SoundEvents.TOTEM_USE, 2.0f, 1.0f);
			Fanfare.play(level, kp, SoundEvents.BEACON_ACTIVATE, 2.5f, 1.2f);
			noticeNear(level, ThroneLogic.top(ball), 96.0, Component.literal(show.names.getOrDefault(show.king, "?"))
					.withStyle(ChatFormatting.GOLD),
					Component.translatable("subtitle.theball.conclave.king").withStyle(ChatFormatting.YELLOW));
			if (TheBallConfig.get().rewards.broadcastCoronation) {
				level.getServer().getPlayerList().broadcastSystemMessage(
						Component.translatable("message.theball.conclave.king", show.names.getOrDefault(show.king, "?"))
								.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
			}
		}
		if (u > crownTick && u < crownTick + CELEBRATION) {
			int c = u - crownTick;
			// Como en el Modo Dios del aluminio: bolas de aluminio orbitando al Rey con su estela de chispas.
			int orbs = TheBallConfig.get().explosive.orbs;
			for (int i = 0; i < orbs; i++) {
				Vec3 o = ExplosiveAbility.orbitPos(kp, i, orbs, c);
				level.sendParticles(new DustParticleOptions(0xFFFFFF, 1.5f), true, false, o.x, o.y, o.z, 1, 0, 0, 0, 0);
				Vec3 prev = ExplosiveAbility.orbitPos(kp, i, orbs, c - 2);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, prev.x, prev.y, prev.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			if (c % 12 == 0) {
				double a = c * 0.9;
				Fanfare.rocket(level, kp.add(Math.cos(a) * 2.0, 3.0, Math.sin(a) * 2.0), 1 + (c / 30) % 2,
						c % 24 == 0 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL,
						Fanfare.SILVER, Fanfare.WHITE);
			}
			double d = ball.getDiameter();
			Vec3 base = ball.position();
			for (int k = 0; k < 3; k++) {
				double a = c * 0.05 + k * (Math.PI * 2.0 / 3.0);
				double rr = d / 2.0 + 1.0;
				for (int h = 0; h < 6; h++) {
					level.sendParticles(ParticleTypes.END_ROD, true, false, base.x + Math.cos(a) * rr, base.y + h * 3.0 + (c % 3),
							base.z + Math.sin(a) * rr, 1, 0.02, 0.5, 0.02, 0.0);
				}
			}
		}
		if (u >= crownTick + CELEBRATION) {
			discardHalo(SHOWS.remove(ball.getUUID()));
			finish(level, ball, show.king);
		}
	}

	/** Separación (radianes) entre el elegido y los espectadores: suficiente para verse unos a otros. */
	private static final double WATCH_SPACING = 0.6;

	/**
	 * Los descartados se deslizan POR EL CÍRCULO (sin cruzar a la Reina) hasta la derecha del elegido, que no se mueve:
	 * mismo recurso que la fila inicial delante de la Reina, pero con el elegido como referencia. Miran hacia él.
	 * Devuelve true cuando todos ya están en su puesto.
	 */
	private static boolean tickFlyers(ServerLevel level, AluminumBallEntity ball, Show show, double lift) {
		if (show.king == null) {
			return true;
		}
		double d = ball.getDiameter();
		double r = d / 2.0 + 6.5;
		Vec3 base = ball.position();
		double y = base.y + 0.3 + lift * d * 0.35;
		double kingAngle = show.angle.getOrDefault(show.king, 0.0);
		Vec3 kingSpot = new Vec3(base.x + Math.cos(kingAngle) * r, y, base.z + Math.sin(kingAngle) * r);
		boolean ready = true;
		for (Map.Entry<UUID, Flyer> e : show.flyers.entrySet()) {
			LivingEntity p = actor(level, e.getKey());
			if (p == null) {
				continue;
			}
			Flyer f = e.getValue();
			// A la derecha del elegido (mirando a la Reina, la derecha es el sentido de ángulo decreciente).
			double target = kingAngle - WATCH_SPACING * (f.slot + 1);
			double delta = Math.atan2(Math.sin(target - f.angle), Math.cos(target - f.angle));
			double step = Math.max(0.004, Math.min(0.05, Math.abs(delta) * 0.06));
			f.angle += Math.abs(delta) <= step ? delta : Math.signum(delta) * step;
			Vec3 at = new Vec3(base.x + Math.cos(f.angle) * r, y, base.z + Math.sin(f.angle) * r);
			f.pos = at;
			place(level, p, at, kingSpot);
			p.setNoGravity(true);
			PROTECTED.put(e.getKey(), level.getGameTime() + 20L * 60);
			if (Math.abs(delta) > 0.03) {
				ready = false;
			}
		}
		return ready;
	}

	/** Cuando solo queda uno, se coloca delante de la Reina (hacia donde mira el trono). */
	/** Hasta el último descarte siguen girando; después los que quedan se quedan quietos (el elegido no se mueve). */
	private static void moveKingToFront(AluminumBallEntity ball, Show show, int u) {
		int rounds = show.order.size() - 1;
		int arrive = rounds == 0 ? HOLD_END : HOLD_END + ROUND_TICKS * (rounds - 1) + ROUND_SCAN;
		if (u < arrive) {
			updateOrbit(show, u);
		}
	}

	private static double smooth(double p) {
		p = Math.max(0.0, Math.min(1.0, p));
		return p * p * (3.0 - 2.0 * p);
	}

	/** Anillo de luz horizontal (chispas: brillan y duran poco, no se acumulan). */
	private static void ring(ServerLevel level, double x, double y, double z, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** Haz de luz entre dos puntos. */
	private static void beam(ServerLevel level, Vec3 from, Vec3 to, boolean bright) {
		int steps = 10;
		for (int i = 1; i <= steps; i++) {
			double f = i / (double) steps;
			level.sendParticles(bright ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, true, false,
					from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f, from.z + (to.z - from.z) * f, 1, 0, 0, 0, 0);
		}
	}

	private static void discard(ServerLevel level, AluminumBallEntity ball, Show show, UUID victim, int left) {
		LivingEntity p = actor(level, victim);
		String name = show.names.getOrDefault(victim, "?");
		show.frozen.remove(victim);
		Vec3 at = p != null ? p.position().add(0.0, 1.0, 0.0) : center(ball);
		level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.25);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		Fanfare.play(level, at, SoundEvents.GLASS_BREAK, 3.0f, 0.8f);
		Fanfare.play(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0f, 1.4f);
		if (p != null) {
			// Se aparta volando a un lado de la Reina, cerca del elegido, y mira desde allí (ver tickFlyers).
			show.flyers.put(victim, new Flyer(p.position(), show.flyers.size(), show.angle.getOrDefault(victim, 0.0)));
			PROTECTED.put(victim, level.getGameTime() + 20L * 60);
		}
		noticeNear(level, ThroneLogic.top(ball), 96.0, Component.translatable("title.theball.conclave.discarded", name)
				.withStyle(ChatFormatting.RED),
				Component.translatable(left > 1 ? "subtitle.theball.conclave.discarded" : "subtitle.theball.conclave.discarded_last")
						.withStyle(ChatFormatting.GRAY));
	}

	// ------------------------------------------------------------------ final

	/** Entrega los premios y libera a todos. {@code kingId} = null: se decide por la regla (y se entrega a pendientes). */
	private static void finish(ServerLevel level, AluminumBallEntity ball, UUID kingId) {
		TestInfo ti = TESTS.remove(ball.getUUID());
		if (ti != null) {
			fadeMusic(level);
			discardHalo(SHOWS.remove(ball.getUUID()));
			endTest(level, ball, ti);
			return; // prueba: no se entrega nada ni se marca la corona
		}
		ball.coronationTick = -1;
		ball.setLift(0.0f);
		fadeMusic(level);
		ball.getTribute().markGiven(Tribute.BIT_CROWN);
		ball.setCrowned(true);
		discardHalo(SHOWS.remove(ball.getUUID()));
		long now = level.getGameTime();
		for (ServerPlayer p : level.players()) {
			if (PROTECTED.containsKey(p.getUUID())) {
				p.setNoGravity(false);
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PROTECT_TICKS, 0, false, false, true));
				PROTECTED.put(p.getUUID(), now + PROTECT_TICKS);
			}
		}
		Contributor king = null;
		if (kingId != null) {
			for (Contributor c : ball.getTribute().toList()) {
				if (c.uuid().equals(kingId)) {
					king = c;
				}
			}
		}
		if (king == null) {
			king = Rewards.chooseKing(ball.getTribute());
		}
		List<Contributor> winners = Rewards.grantFinal(level, ball, king);
		Vec3 top = ThroneLogic.top(ball);
		Fanfare.play(level, top, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(top) < 64.0 * 64.0) {
				ModAdvancements.grant(p, ModAdvancements.CORONATION);
			}
		}
		if (!winners.isEmpty() && kingId == null && TheBallConfig.get().rewards.broadcastCoronation) {
			level.getServer().getPlayerList().broadcastSystemMessage(
					Component.translatable("message.theball.conclave.king", winners.get(0).name())
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		}
	}

	/** Aviso de una línea en la barra de acciones (título y subtítulo juntos): legible y sin tapar la pantalla. */
	private static void noticeNear(ServerLevel level, Vec3 pos, double range, Component title, Component subtitle) {
		Component line = Component.empty().append(title).append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY)).append(subtitle);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(pos) < range * range) {
				p.sendOverlayMessage(line);
			}
		}
	}

	private static void titleNear(ServerLevel level, Vec3 pos, double range, Component title, Component subtitle, int in, int stay, int out) {
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(pos) < range * range) {
				p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
				p.connection.send(new ClientboundSetTitleTextPacket(title));
				p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			}
		}
	}
}
