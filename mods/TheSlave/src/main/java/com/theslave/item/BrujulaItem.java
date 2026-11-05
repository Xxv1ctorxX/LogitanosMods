package com.theslave.item;

import com.theslave.worldgen.Enclaves;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * Brújula de Ceniza: la aguja apunta al acceso del enclave más cercano de la dimensión actual. No da coordenadas ni
 * dibuja rutas: solo la aguja y una indicación breve al usarla. El objetivo se calcula en el servidor.
 */
public class BrujulaItem extends Item {
    private record Destino(String dimension,long tick,long revision,BlockPos desde,Enclaves.Sitio sitio) {}
    // Solo valores/UUIDs; nunca retiene jugadores, niveles o servidores cerrados.
    private static final Map<UUID,Destino> DESTINOS=new java.util.LinkedHashMap<>(128,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,Destino> e) { return size()>128; }
    };

    private static Enclaves.Sitio cercano(ServerLevel level,ServerPlayer p) {
        String dim=level.dimension().identifier().toString();long tick=level.getGameTime(),revision=Enclaves.versionIndice();BlockPos pos=p.blockPosition();
        Destino anterior=DESTINOS.get(p.getUUID());
        if(anterior!=null && anterior.dimension.equals(dim) && anterior.tick==tick && anterior.revision==revision && anterior.desde.equals(pos))return anterior.sitio;
        var sitio=Enclaves.masCercano(level,pos);DESTINOS.put(p.getUUID(),new Destino(dim,tick,revision,pos.immutable(),sitio));return sitio;
    }
	public BrujulaItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayer p)) {
			return;
		}
        var guardado=stack.get(DataComponents.LODESTONE_TRACKER);
        if(guardado!=null && (guardado.target().isEmpty() || !Enclaves.esDestinoVerificado(level,guardado.target().orElseThrow())))stack.remove(DataComponents.LODESTONE_TRACKER);
        if((level.getGameTime()+p.getId())%20!=0)return;
		Enclaves.Sitio s = cercano(level,p);
		if (s == null) {
			stack.remove(DataComponents.LODESTONE_TRACKER);
			return;
		}
		Enclaves.Hallado h = Enclaves.hallado(level, s);
        if (h == null) { stack.remove(DataComponents.LODESTONE_TRACKER); return; }
		int y = h.y;
        var tracker=new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(),new BlockPos(s.x(),y,s.z()))),false);
        // Tambien retira inmediatamente un destino persistido de otra sesion: no hay ventana
        // de veinte ticks durante la que la aguja pueda seguir un indice aun no verificado.
        if(!tracker.equals(stack.get(DataComponents.LODESTONE_TRACKER)))stack.set(DataComponents.LODESTONE_TRACKER,tracker);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}
		if (p.getCooldowns().isOnCooldown(p.getItemInHand(hand))) {
			return InteractionResult.FAIL;
		}
		p.getCooldowns().addCooldown(p.getItemInHand(hand), 20);
		Enclaves.Sitio s = cercano(sl,p);
		String clave = "theslave.brujula.sin_rastro";
		float tono = 0.6f;
        if (s == null || Enclaves.necesitaVerificar(sl,p.blockPosition())) {
            var estado=Enclaves.buscar(sl,p.blockPosition(),encontrado -> {
                if(sl.getServer().getPlayerList().getPlayer(p.getUUID())!=p || p.level()!=sl)return;
                p.sendSystemMessage(Component.translatable(encontrado ? "theslave.brujula.rastro" : "theslave.brujula.sin_rastro"),true);
            });
            clave=switch(estado) {
                case BUSCANDO -> "theslave.brujula.buscando";
                case ESPERA -> "theslave.brujula.espera";
                case SIN_RASTRO -> "theslave.brujula.sin_rastro";
            };
            // Mientras se contrasta el indice anterior solo se conserva un destino ya verificado.
            if(estado==Enclaves.Rastreo.BUSCANDO || estado==Enclaves.Rastreo.ESPERA)s=null;
        }
		if (s != null) {
			double dx = s.x() - p.getX();
			double dz = s.z() - p.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			Enclaves.Hallado h = Enclaves.hallado(sl, s);
			if (h != null && Math.sqrt(d*d+Math.pow(h.y-p.getY(),2)) < 24) {
				clave = "theslave.brujula.cerca";
				tono = 1.6f;
			} else if (h != null && h.y - p.getY() <= -10) {
				clave = "theslave.brujula.desciende";
				tono = 0.9f;
			} else if (h != null && h.y - p.getY() >= 10) {
				clave = "theslave.brujula.asciende";
				tono = 0.9f;
			} else if (d < 2500) {
				clave = "theslave.brujula.rastro";
				tono = 0.8f;
			}
		}
		p.sendSystemMessage(Component.translatable(clave), true);
		sl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5f, tono);
		return InteractionResult.SUCCESS;
	}
}
