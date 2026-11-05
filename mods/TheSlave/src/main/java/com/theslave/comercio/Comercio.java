package com.theslave.comercio;

import com.theslave.Registro;
import com.theslave.Registro.Estado;
import com.theslave.Registro.Vinculo;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModComponents;
import com.theslave.registry.ModComponents.Almas;
import com.theslave.registry.ModComponents.Pacto;
import com.theslave.registry.ModItems;
import com.theslave.persistencia.CustodiaJugador;
import com.theslave.persistencia.TransaccionJugador;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lógica del Notario. Todo ocurre en el servidor: se calcula sobre una copia del inventario y solo si
 * todo cuadra (minerales, almas, hueco para recibir) se vuelca al inventario real. Esta validacion no sustituye una transaccion durable entre el registro y el guardado del jugador.
 */
public final class Comercio {
	private Comercio() {
	}

	public enum Oferta {
		ADQUISICION, TRIBUTO, SELLO, DOBLE, RELICARIO, ALTAR, DEVOLVER
	}

	/** Minerales (lingotes y piedras) y almas de cada tipo. */
	public record Precio(int hierro, int oro, int esmeralda, int diamante, int almasPorTipo) {
		public Almas almas() {
			return Almas.cada(almasPorTipo);
		}
	}

	public static Precio precioAdquisicion(int grado) {
		return switch (Math.max(0, Math.min(2, grado))) {
			case 0 -> new Precio(16, 4, 0, 0, 2);
			case 1 -> new Precio(32, 8, 0, 2, 8);
			default -> new Precio(64, 16, 0, 6, 14);
		};
	}

	public static final Precio TRIBUTO = new Precio(0, 4, 1, 0, 1);
	public static final Precio SELLO = new Precio(0, 16, 8, 3, 3);
	public static final Precio DOBLE = new Precio(0, 16, 6, 1, 2);
	public static final Precio RELICARIO = new Precio(0, 1, 0, 0, 0);
	public static final Precio ALTAR = new Precio(4, 0, 0, 0, 0);

	public static Precio precio(Oferta o, int grado) {
		return switch (o) {
			case ADQUISICION -> precioAdquisicion(grado);
			case TRIBUTO -> TRIBUTO;
			case SELLO -> SELLO;
			case DOBLE -> DOBLE;
			case RELICARIO -> RELICARIO;
			case ALTAR -> ALTAR;
			case DEVOLVER -> new Precio(0, 0, 0, 0, 0);
		};
	}

	// ------------------------------------------------------------ copia del inventario

	/** Las 36 casillas principales, copiadas. */
	private static ItemStack[] copia(Inventory inv) {
		ItemStack[] s = new ItemStack[Inventory.INVENTORY_SIZE];
		for (int i = 0; i < s.length; i++) {
			s[i] = inv.getItem(i).copy();
		}
		return s;
	}

	private static void volcar(Inventory inv, ItemStack[] sim) {
		for (int i = 0; i < sim.length; i++) {
			if (!ItemStack.matches(inv.getItem(i), sim[i])) {
				inv.setItem(i, sim[i]);
			}
		}
		inv.setChanged();
	}

	private static int contar(ItemStack[] sim, Item item) {
		int n = 0;
		for (ItemStack s : sim) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean quitar(ItemStack[] sim, Item item, int n) {
		if (n <= 0) {
			return true;
		}
		if (contar(sim, item) < n) {
			return false;
		}
		for (int i = 0; i < sim.length && n > 0; i++) {
			if (sim[i].is(item)) {
				int t = Math.min(n, sim[i].getCount());
				sim[i].shrink(t);
				n -= t;
				if (sim[i].isEmpty()) {
					sim[i] = ItemStack.EMPTY;
				}
			}
		}
		return true;
	}

	private static boolean anadir(ItemStack[] sim, ItemStack nuevo) {
		ItemStack resto = nuevo.copy();
		for (ItemStack s : sim) {
			if (!resto.isEmpty() && !s.isEmpty() && ItemStack.isSameItemSameComponents(s, resto)
					&& s.getCount() < s.getMaxStackSize()) {
				int t = Math.min(resto.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(t);
				resto.shrink(t);
			}
		}
		for (int i = 0; i < sim.length && !resto.isEmpty(); i++) {
			if (sim[i].isEmpty()) {
				int t = Math.min(resto.getCount(), resto.getMaxStackSize());
				sim[i] = resto.copyWithCount(t);
				resto.shrink(t);
			}
		}
		return resto.isEmpty();
	}

	// ------------------------------------------------------------ almas del relicario

	public static Almas almasDisponibles(Inventory inv) {
		Almas t = Almas.VACIO;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.RELICARIO)) {
				t = t.mas(s.getOrDefault(ModComponents.ALMAS, Almas.VACIO));
			}
		}
		return t;
	}

	/** Retira almas de los relicarios de la copia. false si no hay suficientes. */
	private static boolean sacarAlmas(ItemStack[] sim, Almas pedido) {
		Almas falta = pedido;
		for (ItemStack s : sim) {
			if (falta.total() == 0) {
				break;
			}
			if (s.is(ModItems.RELICARIO)) {
				Almas a = s.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
				Almas t = new Almas(Math.min(a.hambrienta(), falta.hambrienta()),
						Math.min(a.vigilante(), falta.vigilante()), Math.min(a.acechante(), falta.acechante()));
				s.set(ModComponents.ALMAS, a.menos(t));
				falta = falta.menos(t);
			}
		}
		return falta.total() == 0;
	}

	/** Devuelve almas a los relicarios de la copia. false si no caben. */
	private static boolean meterAlmas(ItemStack[] sim, Almas devolver) {
		Almas resto = devolver;
		for (ItemStack s : sim) {
			if (resto.total() == 0) {
				break;
			}
			if (s.is(ModItems.RELICARIO)) {
				Almas a = s.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
				int hueco = Math.max(0, Almas.CAPACIDAD - a.total());
				int h = Math.min(hueco, resto.hambrienta());
				hueco -= h;
				int v = Math.min(hueco, resto.vigilante());
				hueco -= v;
				int c = Math.min(hueco, resto.acechante());
				Almas t = new Almas(h, v, c);
				s.set(ModComponents.ALMAS, a.mas(t));
				resto = resto.menos(t);
			}
		}
		return resto.total() == 0;
	}

	/** Suma una captura (0 hambrienta, 1 vigilante, 2 acechante) al primer relicario con sitio. */
	public static boolean capturar(Inventory inv, int tipo) {
		if (tipo < 0 || tipo > 2) return false;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.RELICARIO)) {
				Almas a = s.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
				if (a.total() < Almas.CAPACIDAD) {
					s.set(ModComponents.ALMAS,
							a.mas(new Almas(tipo == 0 ? 1 : 0, tipo == 1 ? 1 : 0, tipo == 2 ? 1 : 0)));
					inv.setChanged();
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------ estado para la pantalla

	public record Situacion(int ocupadas, int libres, boolean reservaPendiente, Almas almas, boolean hayDifunto,
			boolean hayDifuntoFusionado, boolean puedeDevolver, boolean fusionables) {
	}

	public static Situacion situacion(ServerPlayer p) {
		UUID id = p.getUUID();
		return new Situacion(Registro.ocupadas(id), Registro.libres(id), Registro.reservaPendiente(id) != null,
				almasDisponibles(p.getInventory()), hayDifunto(id, false), hayDifunto(id, true), devolucionPosible(p),
				hayFusionables(id));
	}

	// ------------------------------------------------------------ compras

	private static boolean quitarPrecio(ItemStack[] sim, Precio pr) {
		return quitar(sim, Items.IRON_INGOT, pr.hierro()) && quitar(sim, Items.GOLD_INGOT, pr.oro())
				&& quitar(sim, Items.EMERALD, pr.esmeralda()) && quitar(sim, Items.DIAMOND, pr.diamante())
				&& sacarAlmas(sim, pr.almas());
	}

	private static ItemStack conAlmasYPacto(Item item, Almas a, Pacto pacto) {
		ItemStack s = new ItemStack(item);
		s.set(ModComponents.ALMAS, a);
		s.set(ModComponents.PACTO, pacto);
		return s;
	}

	/** Intenta una compra. Devuelve la clave de lang del resultado; empieza por "ok." si se cobró. */
	public static String comprar(ServerPlayer p, Oferta oferta) {
		if (CustodiaJugador.ocupada(p.getUUID())) return "theslave.altar.ocupado";
		UUID dueno = p.getUUID();
		Inventory inv = p.getInventory();
		if (oferta == Oferta.DEVOLVER) {
			return devolver(p);
		}
		int grado = Registro.ocupadas(dueno);
		switch (oferta) {
			case ADQUISICION -> {
				if (Registro.reservaPendiente(dueno) != null) {
					return "theslave.notario.ya_contrato";
				}
				if (Registro.libres(dueno) <= 0) {
					return "theslave.contrato.limite";
				}
			}
			case TRIBUTO -> {
				if (!hayDifunto(dueno, false)) {
					return "theslave.notario.sin_difunto";
				}
			}
			case DOBLE -> {
				if (!hayDifunto(dueno, true)) {
					return "theslave.notario.sin_fusionado";
				}
			}
			case SELLO -> {
				if (!hayFusionables(dueno)) {
					return "theslave.notario.sin_fusionables";
				}
			}
			default -> {
			}
		}
		ItemStack[] sim = copia(inv);
		Precio pr = precio(oferta, grado);
		if (!quitarPrecio(sim, pr)) {
			return "theslave.notario.falta";
		}
		Almas almas = pr.almas();
		List<ItemStack> entrega = new ArrayList<>();
		UUID contratoNuevo = null;
		switch (oferta) {
			case ADQUISICION -> {
				contratoNuevo = UUID.randomUUID();
				Pacto pacto = new Pacto(dueno, contratoNuevo, grado);
				ItemStack contrato = new ItemStack(ModItems.CONTRATO);
				contrato.set(ModComponents.PACTO, pacto);
				ItemStack latigo = new ItemStack(ModItems.LATIGO);
				latigo.set(ModComponents.PACTO, pacto);
				entrega.add(contrato);
				entrega.add(conAlmasYPacto(ModItems.OFRENDA, almas, pacto));
				entrega.add(latigo);
			}
			case TRIBUTO -> entrega.add(conAlmasYPacto(ModItems.TRIBUTO, almas, new Pacto(dueno, ModComponents.NINGUNO, 0)));
			case SELLO -> entrega.add(conAlmasYPacto(ModItems.SELLO, almas, new Pacto(dueno, ModComponents.NINGUNO, 0)));
            case DOBLE -> entrega.add(conAlmasYPacto(ModItems.DOBLE, almas,
                    new Pacto(dueno, ModComponents.NINGUNO, 0)));
			case RELICARIO -> entrega.add(new ItemStack(ModItems.RELICARIO));
			case ALTAR -> entrega.add(new ItemStack(ModBlocks.ALTAR));
			default -> {
			}
		}
		for (ItemStack e : entrega) {
			if (!anadir(sim, e)) {
				return "theslave.notario.sin_hueco";
			}
		}
		var cuentaAntes=Registro.cuentaOperacion(dueno);
        var cuentaDespues=cuentaAntes.deepCopy();
		if (contratoNuevo != null) {
            Vinculo reserva=new Vinculo();
            reserva.estado=Estado.RESERVADO; reserva.contrato=contratoNuevo; reserva.grado=grado;
            cuentaDespues.add(Registro.codificarVinculo(reserva));
		}
        confirmar(p,oferta.name(),sim,cuentaAntes,cuentaDespues);
		return "ok." + oferta.name().toLowerCase();
	}

    private static Vinculo primerDifunto(UUID dueno, boolean fusionado) {
        for (Vinculo v : Registro.vinculos(dueno)) {
            if (v.estado == Estado.DIFUNTO && v.fusionado == fusionado) return v;
        }
        return null;
    }

    private static boolean hayDifunto(UUID dueno, boolean fusionado) {
        return primerDifunto(dueno, fusionado) != null;
    }

	private static boolean hayFusionables(UUID dueno) {
		int n = 0;
		for (Vinculo v : Registro.vinculos(dueno)) {
			if (v.estado == Estado.VIVO && !v.fusionado) {
				n++;
			}
		}
		return n >= 2;
	}

	// ------------------------------------------------------------ devolución

	/** Posición de contrato, ofrenda y látigo de la compra pendiente, o null si falta alguno o no coincide. */
	private static int[] piezas(ServerPlayer p) {
		UUID dueno = p.getUUID();
		Vinculo reserva = Registro.reservaPendiente(dueno);
		if (reserva == null) {
			return null;
		}
		Inventory inv = p.getInventory();
		int c = -1;
		int o = -1;
		int l = -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			Pacto pa = s.get(ModComponents.PACTO);
			if (pa == null || !dueno.equals(pa.dueno()) || !reserva.contrato.equals(pa.referencia())
					|| pa.grado() != reserva.grado || s.getCount() != 1) {
				continue;
			}
			if (s.is(ModItems.CONTRATO)) {
				c = i;
			} else if (s.is(ModItems.OFRENDA)
                    && s.getOrDefault(ModComponents.ALMAS, Almas.VACIO).equals(precioAdquisicion(reserva.grado).almas())) {
				o = i;
			} else if (s.is(ModItems.LATIGO) && s.getDamageValue() == 0) {
				l = i;
			}
		}
		return c >= 0 && o >= 0 && l >= 0 ? new int[] { c, o, l } : null;
	}

	public static boolean devolucionPosible(ServerPlayer p) {
		return piezas(p) != null;
	}

	private static String devolver(ServerPlayer p) {
		int[] pz = piezas(p);
		if (pz == null) {
			return "theslave.notario.devolver_falta";
		}
		UUID dueno = p.getUUID();
		Vinculo reserva = Registro.reservaPendiente(dueno);
		Inventory inv = p.getInventory();
		ItemStack[] sim = copia(inv);
		Almas almas = sim[pz[1]].getOrDefault(ModComponents.ALMAS, Almas.VACIO);
		sim[pz[0]] = ItemStack.EMPTY;
		sim[pz[1]] = ItemStack.EMPTY;
		sim[pz[2]] = ItemStack.EMPTY;
		Precio pr = precioAdquisicion(reserva.grado);
		if (!meterAlmas(sim, almas)) {
			return "theslave.notario.devolver_sin_relicario";
		}
		if (!anadir(sim, new ItemStack(Items.IRON_INGOT, pr.hierro()))
				|| !anadir(sim, new ItemStack(Items.GOLD_INGOT, pr.oro()))
				|| (pr.diamante() > 0 && !anadir(sim, new ItemStack(Items.DIAMOND, pr.diamante())))) {
			return "theslave.notario.sin_hueco";
		}
        var antes=Registro.cuentaOperacion(dueno);
        var despues=new com.google.gson.JsonArray();
        for (var v:antes) if (!v.getAsJsonObject().get("id").getAsString().equals(reserva.id.toString())) despues.add(v.deepCopy());
        confirmar(p,"DEVOLVER",sim,antes,despues);
		return "ok.devolver";
	}

    private static void confirmar(ServerPlayer p,String tipo,ItemStack[] principal,
            com.google.gson.JsonArray antesCuenta,com.google.gson.JsonArray despuesCuenta) {
        var antes=CustodiaJugador.capturar(p);
        ItemStack[] completo=new ItemStack[p.getInventory().getContainerSize()];
        for (int i=0;i<completo.length;i++) completo[i]=i<principal.length?principal[i]:p.getInventory().getItem(i).copy();
        var despues=CustodiaJugador.codificar(p,completo);
        TransaccionJugador.ejecutar(p,tipo,UUID.randomUUID(),antes,despues,antesCuenta,despuesCuenta);
    }
}
