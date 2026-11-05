package com.theslave.ai;

import com.theslave.entity.Inv;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Convierte "tráeme N de X" en una lista ordenada de pasos (minar, craftear, fundir) restando lo que ya lleva
 * en la mochila. Las herramientas y la mesa/horno se fabrican una sola vez y no se consumen.
 */
public final class Planificador {
	public enum Tipo {
		MINAR, CRAFTEAR, FUNDIR
	}

	/** {@code cantidad} es la cantidad final de {@code item} que debe haber tras el paso; {@code veces} las tandas. */
	public record Paso(Tipo tipo, Item item, int cantidad, Conocimiento.Metodo metodo, int veces) {
	}

	public static final class Resultado {
		public final boolean ok;
		public final Item item;
		public final int cantidad;
		public final List<Paso> pasos;
		public final String motivo;

		Resultado(boolean ok, Item item, int cantidad, List<Paso> pasos, String motivo) {
			this.ok = ok;
			this.item = item;
			this.cantidad = cantidad;
			this.pasos = pasos;
			this.motivo = motivo;
		}

		public String describir() {
			if (!ok) {
				return "No se puede: " + motivo;
			}
			StringBuilder sb = new StringBuilder("Plan para " + cantidad + " x " + nombre(item) + " (" + pasos.size() + " pasos):");
			for (Paso p : pasos) {
				sb.append("\n  ").append(switch (p.tipo()) {
					case MINAR -> "Conseguir ";
					case CRAFTEAR -> "Craftear ";
					case FUNDIR -> "Fundir ";
				}).append(p.cantidad()).append(" x ").append(nombre(p.item()));
			}
			return sb.toString();
		}
	}

	private static final Item[][] FAMILIAS = {
			{Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE},
			{Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE},
			{Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL}};

	private Planificador() {
	}

	public static String nombre(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	private static final class Ctx {
		final Map<Item, Integer> stock = new HashMap<>();
		final Set<Item> herramientas = new HashSet<>();
		final List<Paso> pasos = new ArrayList<>();
        ServerLevel nivel;
        int energia;
		String error;
        int nodos;
	}

	public static Resultado planificar(ServerLevel level,Item item,int n,Inv inv) {return planificar(level,item,n,inv,0);}
    public static Resultado planificar(ServerLevel level,Item item,int n,Inv inv,int credito) {return planificar(level,item,n,inv,credito,List.of());}
    public static Resultado planificar(ServerLevel level, Item item, int n, Inv inv,int credito,java.util.Collection<Item> equipo) {
		if(n<1 || n>2304)return new Resultado(false,item,n,List.of(),"cantidad fuera de rango (1-2304)");
        if(!Conocimiento.asegurar(level))return new Resultado(false,item,n,List.of(),"preparando recetas; vuelve a intentarlo en unos segundos");
		if (!Conocimiento.sabeHacer(item) && inv.count(item) < n) {
			return new Resultado(false, item, n, List.of(), "no sé cómo conseguir " + nombre(item));
		}
        Ctx c = new Ctx();c.nivel=level;c.energia=Math.clamp(credito,0,512000);c.herramientas.addAll(equipo);
		for (ItemStack s : inv.stacks()) {
			if (!s.isEmpty()) {
				c.stock.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		expandir(c, item, n, 0);
		if (c.error != null) {
			return new Resultado(false, item, n, List.of(), c.error);
		}
		return new Resultado(true, item, n, fusionar(c.pasos), "");
	}

	private static List<Paso> fusionar(List<Paso> pasos) {
		List<Paso> res = new ArrayList<>();
		Map<Item, Integer> minados = new HashMap<>();
		for (Paso p : pasos) {
			if (p.tipo() == Tipo.MINAR) {
				Integer idx = minados.get(p.item());
				if (idx != null) {
					Paso a = res.get(idx);
					res.set(idx, new Paso(Tipo.MINAR, a.item(), a.cantidad() + p.cantidad(), a.metodo(), 1));
					continue;
				}
				minados.put(p.item(), res.size());
			}
			res.add(p);
		}
		return res;
	}

	private static void expandir(Ctx c, Item item, int qty, int prof) {
		if (c.error != null || qty <= 0) {
			return;
		}
		if (++c.nodos>4096 || c.pasos.size()>512 || qty>65536 || prof > 40) {
			c.error = "receta demasiado profunda para " + nombre(item);
			return;
		}
		int tiene = c.stock.getOrDefault(item, 0);
		int toma = Math.min(tiene, qty);
		if (toma > 0) {
			c.stock.put(item, tiene - toma);
			qty -= toma;
		}
		if (qty <= 0) {
			return;
		}
		Conocimiento.Metodo m = Conocimiento.metodo(item);
		if (m == null) {
			c.error = "no sé cómo conseguir " + nombre(item);
			return;
		}
		switch (m) {
			case Conocimiento.MetodoMinar mm -> {
				Item h = mm.fuente().herramienta();
				if (h != null) {
					asegurarHerramienta(c, h, prof + 1);
				}
				c.pasos.add(new Paso(Tipo.MINAR, item, qty, m, 1));
			}
			case Conocimiento.MetodoCraftear mc -> {
				Conocimiento.Receta r = mc.receta();
				int veces = (qty + r.cantidad() - 1) / r.cantidad();
				if (r.grande()) {
					asegurarHerramienta(c, Items.CRAFTING_TABLE, prof + 1);
				}
				for (Item ing : mc.eleccion()) {
					expandir(c, ing, veces, prof + 1);
				}
				c.pasos.add(new Paso(Tipo.CRAFTEAR, item, veces * r.cantidad(), m, veces));
				int sobra = veces * r.cantidad() - qty;
				if (sobra > 0) {
					c.stock.merge(item, sobra, Integer::sum);
				}
			}
            case Conocimiento.MetodoFundir mf -> {
                asegurarHerramienta(c,Items.FURNACE,prof+1);
                int produccion=Math.max(1,mf.fundicion().resultado().getCount());
                int tandas=(qty+produccion-1)/produccion;
                expandir(c,mf.fundicion().entrada(),tandas,prof+1);
                Item comb=combustible();
                int energia=com.theslave.ai.Fabricacion.energiaCombustible(c.nivel,net.minecraft.core.BlockPos.ZERO.above(64),new ItemStack(comb));
                if(energia<=0){c.error="combustible incompatible";return;}
                long necesario=(long)tandas*Math.max(1,mf.fundicion().ticks());
                int unidades=(int)((Math.max(0,necesario-c.energia)+energia-1)/energia);
                expandir(c,comb,unidades,prof+1);
                c.energia=(int)Math.max(0,c.energia+(long)unidades*energia-necesario);
                c.pasos.add(new Paso(Tipo.FUNDIR,item,tandas*produccion,m,tandas));
                int sobra=tandas*produccion-qty;if(sobra>0)c.stock.merge(item,sobra,Integer::sum);
            }
		}
	}

	private static Item combustible() {
		Item mejor = Items.COAL;
		int mc = Conocimiento.coste(Items.COAL);
		for (Item i : new Item[]{Items.CHARCOAL, Items.OAK_PLANKS}) {
			int ci = Conocimiento.coste(i);
			if (ci < mc) {
				mc = ci;
				mejor = i;
			}
		}
		return mejor;
	}

	/** Una herramienta (o mesa/horno) se necesita una vez; sirve cualquiera igual o mejor que ya se tenga. */
	private static void asegurarHerramienta(Ctx c, Item h, int prof) {
		for (Item t : equivalentes(h)) {
			if (c.herramientas.contains(t) || c.stock.getOrDefault(t, 0) > 0) {
				return;
			}
		}
		expandir(c, h, 1, prof);
		c.herramientas.add(h);
	}

	private static List<Item> equivalentes(Item h) {
		for (Item[] fam : FAMILIAS) {
			for (int i = 0; i < fam.length; i++) {
				if (fam[i] == h) {
					List<Item> l = new ArrayList<>();
					for (int j = i; j < fam.length; j++) {
						l.add(fam[j]);
					}
					return l;
				}
			}
		}
		return List.of(h);
	}
}
