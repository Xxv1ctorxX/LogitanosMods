package com.theslave.client;

import com.theslave.net.Payloads;
import com.theslave.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Estado visual recibido del servidor, tambien para jugadores remotos. */
public final class LatigoAnimacion {
	public record Ataque(long inicio, InteractionHand mano, int ranura, Vec3 destino) { }
	private static final Map<Integer, Ataque> ACTIVOS = new HashMap<>();
	private static net.minecraft.client.multiplayer.ClientLevel ultimoNivel;
	private LatigoAnimacion() { }

	public static void recibir(Payloads.LatigoAnimado p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		if (ultimoNivel != mc.level) { reiniciar(); ultimoNivel = mc.level; }
		if (p.cancelar()) { ACTIVOS.remove(p.entidad()); return; }
		var entidad = mc.level.getEntity(p.entidad());
		int ranura = entidad instanceof net.minecraft.world.entity.player.Player jugador
				? jugador.getInventory().getSelectedSlot() : -1;
		ACTIVOS.put(p.entidad(), new Ataque(mc.level.getGameTime(), p.mano(), ranura, p.destino()));
	}

	public static Ataque obtener(LivingEntity entidad, InteractionHand mano) {
		Ataque a = ACTIVOS.get(entidad.getId());
		return a != null && a.mano == mano ? a : null;
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) { reiniciar(); return; }
		if (ultimoNivel != mc.level) { reiniciar(); ultimoNivel = mc.level; }
		ACTIVOS.entrySet().removeIf(e -> {
			Ataque a = e.getValue();
			var entidad = mc.level.getEntity(e.getKey());
			return !(entidad instanceof LivingEntity vivo) || !vivo.isAlive()
					|| !vivo.getItemInHand(a.mano).is(ModItems.LATIGO)
					|| (vivo instanceof net.minecraft.world.entity.player.Player jugador
						&& a.mano == InteractionHand.MAIN_HAND && jugador.getInventory().getSelectedSlot() != a.ranura)
					|| mc.level.getGameTime() - a.inicio >= 18;
		});
	}

	public static void reiniciar() { ACTIVOS.clear(); ultimoNivel = null; }
}
