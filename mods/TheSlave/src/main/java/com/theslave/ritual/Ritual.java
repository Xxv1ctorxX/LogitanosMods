package com.theslave.ritual;

import com.theslave.Registro;
import com.theslave.block.TumbaBlockEntity;
import com.theslave.entity.EsclavoEntity;
import com.theslave.net.Payloads;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModEntities;
import com.theslave.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * La lápida y la resurrección. La ceremonia en sí vive en {@link Ceremonias}: la lápida solo informa de qué falta
 * (noche, un Altar de Ceniza cerca, el tributo adecuado) y levanta al esclavo cuando la ceremonia lo ordena.
 *
 * <p>Las tumbas anteriores a las ceremonias podían tener contrato y manzana en custodia: se devuelven al abrirlas.
 */
public final class Ritual {
	private Ritual() {
	}

	public static void registrar() {
	}

	// ---------------------------------------------------------------- lápida


	public static void abrirLapida(ServerPlayer p, BlockPos pos) {
		if (p.level() instanceof ServerLevel sl&&sl.hasChunkAt(pos)&&sl.getBlockEntity(pos) instanceof TumbaBlockEntity be
				&& be.dueno != null && be.dueno.equals(p.getUUID())) {
			devolverCustodia(sl, be, p);
		}
		enviarInfo(p, pos);
	}

	private static void enviarInfo(ServerPlayer p, BlockPos pos) {
		if (!(p.level() instanceof ServerLevel sl)||!sl.hasChunkAt(pos)||!(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be)
				|| be.dueno == null || !be.dueno.equals(p.getUUID())) {
			return;
		}
		UUID id = be.vinculoId();
		Registro.Vinculo v = id == null ? null : Registro.vinculo(p.getUUID(), id);
        boolean alma = v!=null && Hogares.almaEnTumba(p.getUUID(),v.id,be.muertes);
        List<Payloads.Linea> lineas=List.of(new Payloads.Linea("alma_personal",alma,""));
        boolean listo=alma&&!com.theslave.persistencia.CustodiaJugador.ocupada(p.getUUID());
        ServerPlayNetworking.send(p,new Payloads.TumbaInfo(pos,be.nombre,be.dia,be.muertes,lineas,listo,false,be.causa));
	}

	public static void accion(ServerPlayer p, BlockPos pos, int accion) {
		if (!(p.level() instanceof ServerLevel sl) || p.distanceToSqr(Vec3.atCenterOf(pos)) > 100.0
				||!sl.hasChunkAt(pos)||!(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be) || be.dueno == null || !be.dueno.equals(p.getUUID())) {
			return;
		}
		if (accion == 0) {
			enviarInfo(p, pos);
		} else if (accion == 1) {
			Hogares.extraerAlma(p, be);
		}
	}

	/** Un entierro o cualquier cambio en la tumba corta la ceremonia que la usara. */
	public static void cancelarEn(ServerLevel sl, BlockPos pos) {
		Ceremonias.cancelarEn(sl, pos);
	}

	// ---------------------------------------------------------------- migración de custodias antiguas

	/** Devuelve al inventario (o al suelo) las ofrendas que una versión anterior dejó en custodia en la tumba. */
    public static void devolverCustodia(ServerLevel sl, TumbaBlockEntity be, ServerPlayer p) {
        if (p != null && be.custodia != 0) MuerteDurable.recuperarCustodia(p, be);
    }

	// ---------------------------------------------------------------- resurrección

    /** Prepara el individuo original sin mutar tumba, inventario ni Registro. */
    public static EsclavoEntity prepararResurreccion(ServerLevel sl, TumbaBlockEntity be) {
        EsclavoEntity e = ModEntities.ESCLAVO.create(sl, EntitySpawnReason.TRIGGERED);
        if (e == null || be.dueno == null || be.vinculoId() == null) return null;
        var v = Registro.vinculo(be.dueno, be.vinculoId());
        if (v == null || v.estado != Registro.Estado.DIFUNTO) return null;
        e.restaurar(be.dueno, be.vinculoId(), be.nombre, be.variante);
        e.aplicarVinculo(v);
        BlockPos pos=be.getBlockPos();
        e.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        return e;
    }

    /** Operador: misma confirmacion durable, sin cobrar un tributo. Requiere al dueño conectado. */
    public static boolean resucitar(ServerLevel sl, BlockPos pos, TumbaBlockEntity be) {
        if(be.dueno==null)return false;
        ServerPlayer dueno=sl.getServer().getPlayerList().getPlayer(be.dueno);
        if(dueno==null || dueno.level()!=sl)return false;
        EsclavoEntity e=prepararResurreccion(sl,be);
        if(e==null)return false;
        var cuenta=Registro.cuentaOperacion(be.dueno);
        for(var fila:cuenta) {
            var ficha=fila.getAsJsonObject();
            if(be.vinculoId().toString().equals(ficha.get("id").getAsString())) {
                ficha.addProperty("estado","VIVO");ficha.addProperty("esclavo",e.getUUID().toString());
            }
        }
        return RitualDurable.iniciar(dueno,e,List.of(),be,cuenta,
                com.theslave.persistencia.CustodiaJugador.capturar(dueno));
    }

	/** Para operadores: resucita la tumba más cercana sin ceremonia. */
	public static boolean resucitarCercana(ServerPlayer p, BlockPos centro) {
		ServerLevel sl = (ServerLevel) p.level();
		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = -4; dy <= 4; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					BlockPos pos = centro.offset(dx, dy, dz);
					if(!sl.hasChunkAt(pos))continue;
					if (sl.getBlockState(pos).is(ModBlocks.TUMBA) && sl.getBlockEntity(pos) instanceof TumbaBlockEntity be) {
						cancelarEn(sl, pos);
						return resucitar(sl, pos, be);
					}
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- escena de prueba (comando de operador)

	public static void escenaDePrueba(ServerPlayer p) {
		ServerPlayNetworking.send(p, new Payloads.Escena(1, 0, "«Queda escrito»", 0.6f, true));
	}
}
