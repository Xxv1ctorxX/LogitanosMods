package com.theball.world;

import com.theball.TheBallMod;
import com.theball.config.TheBallConfig;
import com.theball.registry.ModAttachments;
import com.theball.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Aluminio en chunks que ya estaban generados cuando se instaló el mod. La generación normal (BiomeModifications)
 * solo actúa al crear un chunk; esto completa los antiguos, con estas reglas (ante la duda, NO se pone):
 * <ul>
 * <li><b>Frecuencia como el hierro vanilla</b>: por chunk, 90 intentos de 9 en Y 80..384, 10 de 9 en Y -24..56 y 10 de 4
 * en el fondo..Y 72.</li>
 * <li><b>Solo roca virgen</b>: cada bloque de la veta exige que todo el cubo de 5×5×5 que lo rodea sea roca natural
 * (piedra, pizarra, tuff, menas, tierra, grava...). Cualquier aire, líquido, antorcha, madera, ladrillo, cofre, raíl,
 * cristal, etc. descarta ese bloque: así nunca toca cuevas, minas, túneles, casas, estructuras ni zonas recorridas.</li>
 * <li><b>Solo con los vecinos cargados</b>: un chunk no se procesa hasta que sus 8 vecinos están cargados (así no hay
 * franjas vacías en los bordes y todas las comprobaciones son fiables). Cada chunk coloca bloques solo dentro de sí
 * mismo.</li>
 * <li><b>Una sola vez por chunk</b> (marca persistente en el chunk) y nunca si ya tiene aluminio. Resultado determinista
 * (semilla del mundo + posición del chunk).</li>
 * <li>Se puede apagar con {@code world.retrogen = false} en {@code config/theball.json}.</li>
 * </ul>
 */
public final class OreRetrogen {
	private static final int CHECKS_PER_TICK = 12;

	private record Pending(ServerLevel level, ChunkPos pos) {
	}

	private static int totalChunks;
	private static long totalBlocks;
	private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private OreRetrogen() {
	}

	public static void clear() {
		QUEUE.clear();
		QUEUED.clear();
	}

	public static void register() {
		if (com.theball.registry.ModWorldgen.skipped()) {
			return;
		}
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!TheBallConfig.get().world.retrogen || !level.dimension().equals(Level.OVERWORLD)
					|| chunk.hasAttached(ModAttachments.RETROGEN_DONE)) {
				return;
			}
			if (generated || hasAluminum(chunk)) {
				chunk.setAttached(ModAttachments.RETROGEN_DONE, true); // ya lo puso la generación normal: no hay nada que hacer
				return;
			}
			if (QUEUED.add(chunk.getPos().pack())) {
				QUEUE.add(new Pending(level, chunk.getPos()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> processQueue());
	}

	private static void processQueue() {
		int budget = Math.min(CHECKS_PER_TICK, QUEUE.size());
		for (int i = 0; i < budget; i++) {
			Pending p = QUEUE.poll();
			if (p == null) {
				return;
			}
			LevelChunk chunk = p.level().getChunkSource().getChunkNow(p.pos().x(), p.pos().z());
			if (chunk == null || chunk.hasAttached(ModAttachments.RETROGEN_DONE)) {
				QUEUED.remove(p.pos().pack()); // se descargó (volverá a entrar al cargarse) o ya está hecho
				continue;
			}
			if (!neighborsLoaded(p.level(), p.pos())) {
				QUEUE.add(p); // todavía no es seguro: se espera
				continue;
			}
			QUEUED.remove(p.pos().pack());
			try {
				int n = run(p.level(), chunk);
				totalChunks++;
				totalBlocks += n;
				if (totalChunks % 100 == 0) {
					TheBallMod.LOGGER.info("Aluminio retroactivo: {} chunks completados, {} bloques de aluminio en total", totalChunks, totalBlocks);
				}
				chunk.setAttached(ModAttachments.RETROGEN_DONE, true);
			} catch (RuntimeException e) {
				TheBallMod.LOGGER.error("Aluminio retroactivo: fallo en el chunk {} (se deja sin tocar)", p.pos(), e);
				chunk.setAttached(ModAttachments.RETROGEN_DONE, true); // no reintentar en bucle
			}
		}
	}

	private static boolean neighborsLoaded(ServerLevel level, ChunkPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean hasAluminum(LevelChunk chunk) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.maybeHas(s -> s.is(ModBlocks.ALUMINUM_ORE) || s.is(ModBlocks.DEEPSLATE_ALUMINUM_ORE))) {
				return true;
			}
		}
		return false;
	}

	/** Coloca las vetas en el chunk (los 8 vecinos deben estar cargados). Devuelve cuántos bloques ha puesto. */
	public static int run(ServerLevel level, LevelChunk chunk) {
		if (!neighborsLoaded(level, chunk.getPos())) {
			return 0;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ chunk.getPos().pack() * 341873128712L ^ 0x414C554DL);
		int minY = level.getMinY();
		int placed = 0;
		// (intentos, tamaño, altura mínima, altura máxima, trapezoidal?) como ore_iron_upper / _middle / _small
		placed += attempts(level, chunk, random, 90, 9, 80, 384, true);
		placed += attempts(level, chunk, random, 10, 9, -24, 56, true);
		placed += attempts(level, chunk, random, 10, 4, minY, 72, false);
		return placed;
	}

	/** Desplazamientos de una veta de este tamaño (esfera), para elegir bloques distintos y contiguos. */
	private static List<int[]> offsets(double radius) {
		List<int[]> list = new ArrayList<>();
		int r = (int) Math.ceil(radius);
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z <= radius * radius) {
						list.add(new int[] {x, y, z});
					}
				}
			}
		}
		return list;
	}

	private static final List<int[]> SPHERE_BIG = offsets(2.3);
	private static final List<int[]> SPHERE_SMALL = offsets(1.5);

	private static int attempts(ServerLevel level, LevelChunk chunk, RandomSource random, int count, int size, int minY,
			int maxY, boolean trapezoid) {
		int placed = 0;
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		List<int[]> sphere = size > 4 ? SPHERE_BIG : SPHERE_SMALL;
		for (int n = 0; n < count; n++) {
			int x = baseX + random.nextInt(16);
			int z = baseZ + random.nextInt(16);
			int span = maxY - minY;
			int y;
			if (trapezoid) {
				int half = span / 2;
				y = minY + random.nextInt(span - half + 1) + random.nextInt(half + 1);
			} else {
				y = minY + random.nextInt(span + 1);
			}
			List<int[]> shuffled = new ArrayList<>(sphere);
			Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
			for (int i = 0; i < size && i < shuffled.size(); i++) {
				int[] o = shuffled.get(i);
				BlockPos pos = new BlockPos(x + o[0], y + o[1], z + o[2]);
				if (place(level, chunk, pos)) {
					placed++;
				}
			}
		}
		return placed;
	}

	private static boolean place(ServerLevel level, LevelChunk chunk, BlockPos pos) {
		// Solo dentro de este chunk (los vecinos solo se leen) y lejos de los límites del mundo.
		if ((pos.getX() >> 4) != chunk.getPos().x() || (pos.getZ() >> 4) != chunk.getPos().z()
				|| pos.getY() <= level.getMinY() + 3 || pos.getY() >= level.getMaxY() - 3) {
			return false;
		}
		BlockState state = chunk.getBlockState(pos);
		BlockState ore;
		if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
			ore = ModBlocks.ALUMINUM_ORE.defaultBlockState();
		} else if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
			ore = ModBlocks.DEEPSLATE_ALUMINUM_ORE.defaultBlockState();
		} else {
			return false;
		}
		// Roca virgen: todo el cubo de 5x5x5 alrededor tiene que ser natural y sólido (ante la duda, no se pone).
		BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					near.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					LevelChunk c = level.getChunkSource().getChunkNow(near.getX() >> 4, near.getZ() >> 4);
					if (c == null || !isNaturalSolid(c.getBlockState(near))) {
						return false;
					}
				}
			}
		}
		chunk.setBlockState(pos, ore, 0);
		return true;
	}

	/** Bloques que forman la roca natural del subsuelo. Todo lo demás (aire, agua, madera, antorchas...) cuenta como duda. */
	private static boolean isNaturalSolid(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty()) {
			return false;
		}
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.STONE_ORE_REPLACEABLES)
				|| s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(BlockTags.ORES)
				|| s.is(BlockTags.DIRT) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL)
				|| s.is(net.minecraft.world.level.block.Blocks.CALCITE) || s.is(net.minecraft.world.level.block.Blocks.TUFF)
				|| s.is(ModBlocks.ALUMINUM_ORE) || s.is(ModBlocks.DEEPSLATE_ALUMINUM_ORE);
	}

	/** Resultado de {@link #stats}. */
	public record Stats(int chunks, long aluminum, long exposed, int minY, int maxY) {
	}

	/**
	 * Cuenta el aluminio en los chunks CARGADOS alrededor de un chunk (radio en chunks) y cuántos bloques tienen aire o
	 * líquido pegado (en una zona de roca virgen deberían ser 0 en lo que añade el retrogen).
	 */
	public static Stats stats(ServerLevel level, ChunkPos center, int radius) {
		int chunks = 0;
		long al = 0;
		long exposed = 0;
		int minSeen = Integer.MAX_VALUE;
		int maxSeen = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
		for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
			for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
				LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
				if (c == null) {
					continue;
				}
				chunks++;
				LevelChunkSection[] sections = c.getSections();
				for (int si = 0; si < sections.length; si++) {
					if (!sections[si].maybeHas(s -> s.is(ModBlocks.ALUMINUM_ORE) || s.is(ModBlocks.DEEPSLATE_ALUMINUM_ORE))) {
						continue;
					}
					int baseY = c.getMinY() + si * 16;
					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								BlockState s = sections[si].getBlockState(x, y, z);
								if (!s.is(ModBlocks.ALUMINUM_ORE) && !s.is(ModBlocks.DEEPSLATE_ALUMINUM_ORE)) {
									continue;
								}
								al++;
								pos.set(c.getPos().getMinBlockX() + x, baseY + y, c.getPos().getMinBlockZ() + z);
								minSeen = Math.min(minSeen, pos.getY());
								maxSeen = Math.max(maxSeen, pos.getY());
								for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
									near.setWithOffset(pos, d);
									LevelChunk nc = level.getChunkSource().getChunkNow(near.getX() >> 4, near.getZ() >> 4);
									if (nc != null) {
										BlockState ns = nc.getBlockState(near);
										if (ns.isAir() || !ns.getFluidState().isEmpty()) {
											exposed++;
											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return new Stats(chunks, al, exposed, minSeen == Integer.MAX_VALUE ? 0 : minSeen, maxSeen == Integer.MIN_VALUE ? 0 : maxSeen);
	}
}
