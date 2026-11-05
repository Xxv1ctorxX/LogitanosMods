package com.theslave.worldgen;

import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModEntities;
import com.theslave.worldgen.Enclaves.Tipo;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Los cuatro refugios. Todo se coloca dentro del chunk del enclave (no toca chunks vecinos), la entrada está siempre en
 * la celda local (2, 7) y la sala queda al este. Nada se propaga después: la infección es solo lo que se coloca aquí.
 */
final class Estructuras {
	private static final int FLAGS = 2 | 16;

	private final ServerLevel sl;
	private final int ox;
	private final int oz;

	private Estructuras(ServerLevel sl, LevelChunk chunk) {
		this.sl = sl;
		this.ox = chunk.getPos().getMinBlockX();
		this.oz = chunk.getPos().getMinBlockZ();
	}

	private static BlockState ex(String nombre) {
		return ModBlocks.EXTRA.get(nombre).defaultBlockState();
	}

	private BlockPos at(int x, int y, int z) {
		return new BlockPos(ox + x, y, oz + z);
	}

	private void set(int x, int y, int z, BlockState st) {
		if (x >= 0 && x <= 15 && z >= 0 && z <= 15 && y > sl.getMinY() && y < sl.getMaxY()) {
			sl.setBlock(at(x, y, z), st, FLAGS);
		}
	}

	private BlockState get(int x, int y, int z) {
		return sl.getBlockState(at(x, y, z));
	}

	private void caja(int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					set(x, y, z, st);
				}
			}
		}
	}

	private static final BlockState AIRE = Blocks.AIR.defaultBlockState();

	/** Construye y devuelve la altura de la entrada. También coloca al Notario. */
	static int construir(ServerLevel sl, LevelChunk chunk, Tipo tipo) {
		Estructuras e = new Estructuras(sl, chunk);
		SplittableRandom r = new SplittableRandom(chunk.getPos().pack() * 6364136223846793005L + sl.getSeed());
		int entrada;
		int sueloSala;
		switch (tipo) {
			case CUEVA -> {
                sueloSala = e.entradaCueva();
                if (sueloSala == Integer.MIN_VALUE) return Integer.MIN_VALUE;
                e.pasilloCueva(sueloSala);
				e.sala(sueloSala, e.murosPiedra(sueloSala), 3);
				e.interior(sueloSala, r, tipo);
                entrada = sueloSala + 1;
			}
			case SUPERFICIE -> {
				int s = e.superficie();
				sueloSala = s - 7;
                if(!e.sinContenido(sueloSala-1,s+4))return Integer.MIN_VALUE;
				e.escalera(s, sueloSala);
				e.sala(sueloSala, e.murosPiedra(sueloSala), 3);
				e.interior(sueloSala, r, tipo);
				e.ruina(s, r);
				e.marcasSuperficie(s, r);
				entrada = s;
			}
			case CASA -> {
				int s = e.superficie();
				sueloSala = s - 7;
                if(!e.sinContenido(sueloSala-1,s+4))return Integer.MIN_VALUE;
				e.escalera(s, sueloSala);
				e.sala(sueloSala, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
				e.interior(sueloSala, r, tipo);
				e.casaSuperior(s, r);
				e.marcasSuperficie(s, r);
				entrada = s;
			}
			default -> {
				int fy = e.suelo();
                if (fy == Integer.MIN_VALUE || !e.sinContenido(fy-2,fy+10)) return Integer.MIN_VALUE;
				sueloSala = fy;
				e.refugioNether(fy);
				e.interior(sueloSala, r, tipo);
				entrada = fy + 1;
			}
		}
		e.notario(sueloSala + 1);
		return entrada;
	}

	// ---------------------------------------------------------------- terreno

	private int superficie() {
		LevelChunk c = sl.getChunk(ox >> 4, oz >> 4);
		return c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 2, 7);
	}

	/** Exige una plataforma natural y un volumen abierto, no una pequeña cueva o un suelo artificial sobre lava. */
    private int suelo() {
        for (int y = Math.min(110, sl.getMaxY() - 12); y > 34; y--) {
            if (!get(2,y,7).isSolid() || !get(6,y,7).isSolid()) continue;
            boolean camino = true;
            for (int x=2;x<=8;x++) {
                if (!get(x,y,7).isSolid() || !get(x,y,7).getFluidState().isEmpty()) camino=false;
                for(int h=1;h<=3;h++) if(!get(x,y+h,7).isAir()) camino=false;
            }
            if (!camino) continue;
            int firmes=0;
            for(int x=2;x<=15;x++) for(int z=2;z<=12;z++) if(get(x,y,z).isSolid()) firmes++;
            if(firmes<120)continue;
            int abiertos=0;
            for(int x=2;x<=14;x+=2) for(int z=3;z<=11;z+=2) for(int h=1;h<=10;h++)
                if(get(x,y+h,z).isAir()) abiertos++;
            if(abiertos>=300) return y;
        }
        return Integer.MIN_VALUE;
    }

    private boolean sinContenido(int y0,int y1) {
        if(y0<=sl.getMinY() || y1>=sl.getMaxY())return false;
        for(int x=0;x<=15;x++)for(int z=2;z<=12;z++)for(int y=y0;y<=y1;y++) {
            BlockPos p=at(x,y,z);
            if(!sl.getFluidState(p).isEmpty() || sl.getBlockEntity(p)!=null)return false;
        }
        return true;
    }

    /** Entrada natural de dos bloques de altura conectada al borde occidental del chunk. */
    private int entradaCueva() {
        int superior = Math.min(superficie() - 10, 45);
        for(int y=superior;y>sl.getMinY()+8;y--) {
            boolean acceso=true;
            for(int x=0;x<=2;x++) {
                if(!get(x,y,7).isSolid() || !get(x,y,7).getFluidState().isEmpty()
                    || !get(x,y+1,7).isAir() || !get(x,y+2,7).isAir()) acceso=false;
            }
            if (!acceso) continue;
            // No vaciar una sala atravesada por una bolsa de agua/lava o por contenedores de otra estructura.
            boolean seguro=true;
            for(int x=3;x<=15;x++) for(int z=3;z<=11;z++) for(int h=0;h<=5;h++) {
                BlockPos pos=at(x,y+h,z);
                if(!sl.getFluidState(pos).isEmpty() || sl.getBlockEntity(pos)!=null) seguro=false;
            }
            if(seguro) return y;
        }
        return Integer.MIN_VALUE;
    }

    private void pasilloCueva(int y0) {
        // Solo la unión lateral; no pozo, escalera ni pista en superficie.
        for(int x=3;x<=8;x++) {
            set(x,y0,7,ex(y0<0?"pizarra_infectada":"piedra_infectada"));
            set(x,y0+1,7,AIRE);
            set(x,y0+2,7,AIRE);
        }
    }

	private BlockState murosPiedra(int y0) {
		return y0 < 0 ? ex("pizarra_infectada") : Blocks.STONE_BRICKS.defaultBlockState();
	}

	// ---------------------------------------------------------------- sala interior

	/** Sala de 6x7 (x 9..14, z 4..10) con puerta al oeste en (8, 7). Altura interior h. */
	private void sala(int y0, BlockState muro, int h) {
		for (int x = 8; x <= 15; x++) {
			for (int z = 3; z <= 11; z++) {
				for (int y = y0; y <= y0 + h + 1; y++) {
					boolean borde = x == 8 || x == 15 || z == 3 || z == 11 || y == y0 || y == y0 + h + 1;
					set(x, y, z, borde ? muro : AIRE);
				}
			}
		}
		set(8, y0 + 1, 7, AIRE);
		set(8, y0 + 2, 7, AIRE);
	}

	/** Mostrador, archivo, raíz, urnas, velas y lámparas: tres puntos de interés y poca luz. */
	private void interior(int y0, SplittableRandom r, Tipo tipo) {
		int y = y0 + 1;
		// suelo con manchas de terreno infectado
		for (int x = 9; x <= 14; x++) {
			for (int z = 4; z <= 10; z++) {
				if (r.nextInt(100) < 18) {
					set(x, y0, z, tipo == Tipo.REFUGIO ? ex("netherrack_infectada")
							: tipo == Tipo.CASA ? ex("piedra_negra_infectada") : ex("piedra_infectada"));
				}
			}
		}
		set(9, y, 6, ex("piedra_registro"));
		for (int z = 6; z <= 8; z++) {
			set(12, y, z, ex("mesa_notario"));
			set(11, y, z, ex("losa_ceniza"));
		}
		set(12, y + 1, 6, ex("velas_notario"));
		set(12, y + 1, 8, ex("velas_notario"));
		for (int z = 5; z <= 9; z++) {
			set(14, y, z, ex("archivo_condenas"));
			set(14, y + 1, z, ex("archivo_condenas"));
		}
		set(14, y, 4, ex("urna_animas"));
		set(14, y, 10, ex("urna_animas"));
		set(10, y, 4, ex("lampara_animas"));
		set(13, y, 10, ex("lampara_animas"));
		// la raíz: núcleo visual del enclave, atraviesa el techo
		for (int yy = y; yy <= y0 + 5; yy++) {
			set(10, yy, 10, ex("raiz_ceniza"));
		}
		set(10, y0 + 3, 9, ex("raicillas_infectadas"));
		set(9, y0 + 3, 10, ex("raicillas_infectadas"));
		set(9, y, 10, ex("raicillas_infectadas"));
		set(11, y0 + 3, 10, ex("raicillas_infectadas"));
		if (tipo == Tipo.CASA) {
			for (int x = 9; x <= 11; x++) {
				set(x, y0, 9, Blocks.CRIMSON_NYLIUM.defaultBlockState());
				set(x, y, 9, Blocks.CRIMSON_ROOTS.defaultBlockState());
			}
			set(13, y0 + 3, 5, Blocks.HANGING_ROOTS.defaultBlockState());
			set(11, y0 + 3, 4, Blocks.HANGING_ROOTS.defaultBlockState());
		}
		// algo de ceniza en las esquinas
		set(9, y, 4, ex("losa_ceniza"));
		set(14, y, 7, ex("losa_ceniza"));
	}

	private void notario(int y) {
		ServerLevel nivel = sl;
		double x = ox + 13.5;
		double z = oz + 7.5;
		nivel.getServer().execute(() -> {
			var n = ModEntities.NOTARIO.create(nivel, EntitySpawnReason.STRUCTURE);
			if (n != null) {
				n.setPos(x, y, z);
				n.setYRot(90.0f);
				n.setYHeadRot(90.0f);
				nivel.addFreshEntity(n);
			}
		});
	}

	// ---------------------------------------------------------------- accesos


	/** Escalera de piedra que baja hacia el este desde (2, s-1) hasta el suelo de la sala. */
	private void escalera(int s, int y0) {
		BlockState peldano = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
		for (int i = 0; i <= 5; i++) {
			int x = 2 + i;
			int y = s - 1 - i;
			set(x, y, 7, peldano);
			set(x, y + 1, 7, AIRE);
			set(x, y + 2, 7, AIRE);
			set(x, y + 3, 7, AIRE);
			for (int z = 6; z <= 8; z += 2) {
				set(x, y + 1, z, get(x, y + 1, z).isAir() ? AIRE : ex("piedra_infectada"));
			}
		}
		for (int y = y0 + 1; y <= y0 + 3; y++) {
			for (int x = 7; x <= 8; x++) {
				set(x, y, 7, AIRE);
			}
		}
	}

	// ---------------------------------------------------------------- superficie

	/** Unas pocas manchas de terreno infectado cerca de la entrada; nada más y nada fuera de ese rincón. */
	private void marcasSuperficie(int s, SplittableRandom r) {
		for (int x = 0; x <= 6; x++) {
			for (int z = 4; z <= 10; z++) {
				if (r.nextInt(100) >= 30) {
					continue;
				}
				BlockState b = get(x, s - 1, z);
				if (b.is(Blocks.GRASS_BLOCK)) {
					set(x, s - 1, z, ex("cesped_infectado"));
				} else if (b.is(BlockTags.DIRT)) {
					set(x, s - 1, z, ex("tierra_infectada"));
				} else if (b.is(BlockTags.SAND)) {
					set(x, s - 1, z, ex("arena_infectada"));
				} else if (b.is(BlockTags.BASE_STONE_OVERWORLD)) {
					set(x, s - 1, z, ex("piedra_infectada"));
				}
			}
		}
		// una raíz ennegrecida y una fisura bajo las hojas, a la vista solo de cerca
		set(1, s, 7, ex("raicillas_infectadas"));
		set(3, s, 6, ex("raicillas_infectadas"));
	}

	private void ruina(int s, SplittableRandom r) {
		BlockState musgo = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		for (int[] p : new int[][] { { 0, 5 }, { 4, 5 }, { 0, 9 }, { 4, 9 } }) {
			int h = 1 + r.nextInt(3);
			for (int y = 0; y < h; y++) {
				set(p[0], s + y, p[1], y == h - 1 && r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : musgo);
			}
		}
		set(1, s, 5, Blocks.COBBLESTONE_SLAB.defaultBlockState());
		set(3, s, 9, Blocks.COBBLESTONE_SLAB.defaultBlockState());
	}

	/** Vieja carbonería hundida: tejado bajo de roble oscuro sobre la escalera, hojas y raíces ocultando el acceso. */
	private void casaSuperior(int s, SplittableRandom r) {
		BlockState tronco = Blocks.DARK_OAK_LOG.defaultBlockState();
		BlockState losa = Blocks.DARK_OAK_SLAB.defaultBlockState();
		BlockState hojas = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int[] p : new int[][] { { 1, 5 }, { 5, 5 }, { 1, 9 }, { 5, 9 } }) {
			set(p[0], s, p[1], tronco);
			set(p[0], s + 1, p[1], tronco);
		}
		for (int x = 1; x <= 5; x++) {
			for (int z = 5; z <= 9; z++) {
				set(x, s + 2, z, losa);
			}
		}
		// fachada parcialmente enterrada, con una ventana a oscuras
		for (int x = 2; x <= 4; x++) {
			set(x, s, 9, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		}
		set(3, s + 1, 9, Blocks.GLASS_PANE.defaultBlockState());
		for (int i = 0; i < 9; i++) {
			int x = r.nextInt(7);
			int z = 4 + r.nextInt(7);
			if (get(x, s, z).isAir() && x != 2 + 0) {
				set(x, s, z, r.nextBoolean() ? hojas : ex("raicillas_infectadas"));
			}
		}
	}

	// ---------------------------------------------------------------- Nether

	private void refugioNether(int fy) {
		// despejar: entrada exterior y espacio abierto alrededor
		caja(2, fy + 1, 2, 15, fy + 9, 12, AIRE);
		caja(2, fy - 2, 2, 15, fy - 1, 12, ex("netherrack_infectada"));
		caja(2, fy, 2, 7, fy, 12, ex("netherrack_infectada"));
		// suelo de la sala y camino hasta la puerta
		sala(fy, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
		caja(8, fy - 2, 3, 15, fy - 1, 11, ex("piedra_negra_infectada"));
		// roca baja que oculta la construcción desde el norte y el sur
		for (int x = 5; x <= 15; x++) {
			set(x, fy + 1, 2, ex("netherrack_infectada"));
			set(x, fy + 2, 2, ex("netherrack_infectada"));
			set(x, fy + 1, 12, ex("netherrack_infectada"));
			set(x, fy + 2, 12, ex("netherrack_infectada"));
		}
		set(7, fy + 1, 5, ex("piedra_negra_infectada"));
		set(7, fy + 1, 9, ex("piedra_negra_infectada"));
		set(7, fy + 2, 5, ex("piedra_negra_infectada"));
		set(7, fy + 2, 9, ex("piedra_negra_infectada"));
	}
}
