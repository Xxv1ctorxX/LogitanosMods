package com.theslave.ai;

import com.theslave.TheSlaveMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lo que el esclavo sabe hacer: recetas de crafteo y de horno, qué bloques sueltan qué y el coste de cada item
 * (relajación tipo Bellman-Ford sobre todo el grafo de recetas, así el plan nunca entra en ciclos).
 * Se prepara por lotes compartidos entre todos los esclavos, sin bloquear el primer encargo.
 */
public final class Conocimiento {
	public static final int INF = 1_000_000;

	public record Receta(Item salida,int cantidad,List<List<Item>> grupos,boolean grande,ItemStack resultado,CraftingRecipe receta) {
        public Receta { resultado=resultado.copy(); }
        public Receta(Item salida,int cantidad,List<List<Item>> grupos,boolean grande) { this(salida,cantidad,grupos,grande,ItemStack.EMPTY,null); }
        @Override public ItemStack resultado() { return resultado.copy(); }
	}

	public record Fundicion(Item entrada,Item salida,int ticks,ItemStack resultado,SmeltingRecipe receta) {
        public Fundicion { resultado=resultado.copy(); }
        public Fundicion(Item entrada,Item salida,int ticks) { this(entrada,salida,ticks,ItemStack.EMPTY,null); }
        @Override public ItemStack resultado() { return resultado.copy(); }
	}

	/** Bloques naturales que sueltan un item, con la herramienta mínima (o null) que hace falta. */
	public record Fuente(Item item, Set<Block> bloques, Item herramienta) {
	}

	/** Cómo se consigue un item: el método de menor coste. */
	public sealed interface Metodo permits MetodoMinar, MetodoCraftear, MetodoFundir {
	}

	public record MetodoMinar(Fuente fuente) implements Metodo {
	}

	public record MetodoCraftear(Receta receta, List<Item> eleccion) implements Metodo {
	}

	public record MetodoFundir(Fundicion fundicion) implements Metodo {
	}

	private static Object clave;
	private static final Map<Item, List<Receta>> CRAFTEO = new HashMap<>();
	private static final Map<Item, List<Fundicion>> FUNDICION = new HashMap<>();
	private static final Map<Item, Fuente> FUENTES = new HashMap<>();
	private static final Map<Item, Integer> COSTE = new HashMap<>();
	private static final Map<Item, Metodo> METODO = new HashMap<>();
    private static final Set<Recipe<?>> RECETAS_VIGENTES=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private static final Item[] PICOS = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE};
	private static final Item[] HACHAS = {Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE};
	private static final Item[] PALAS = {Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL};
	private static final Item[][] HERRAMIENTAS = {PICOS, HACHAS, PALAS};

	private Conocimiento() {
	}

	/** Items que se consideran combustible y cuántas fundiciones da cada unidad (x10 para evitar decimales). */
	public static int fundicionesPorDiez(ItemStack s) {
		if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) {
			return 80;
		}
		if (s.is(Items.COAL_BLOCK)) {
			return 800;
		}
		if (s.is(ItemTags.LOGS_THAT_BURN) || s.is(ItemTags.PLANKS)) {
			return 15;
		}
		if (s.is(Items.STICK)) {
			return 5;
		}
		return 0;
	}

    private static Trabajo trabajo;
    private static boolean listo,recargando;

    public static void registrar() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { reiniciar();recargando=false; });
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server,resources) -> server.execute(() -> { recargando=true;reiniciar(); }));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,ok) -> server.execute(() -> { reiniciar();recargando=false;asegurar(server.overworld()); }));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> asegurar(server.overworld()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(trabajo==null || recargando)return;
            if(server.getRecipeManager()!=clave) { reiniciar();asegurar(server.overworld());return; }
            trabajo.avanzar();
            if(trabajo.fase==4) {
                listo=true;trabajo=null;
                TheSlaveMod.LOGGER.info("Conocimiento del esclavo preparado: {} recetas, {} fundiciones, {} fuentes",CRAFTEO.size(),FUNDICION.size(),FUENTES.size());
            }
        });
    }
    private static void reiniciar() {
        clave=null;trabajo=null;listo=false;
        CRAFTEO.clear();FUNDICION.clear();FUENTES.clear();COSTE.clear();METODO.clear();RECETAS_VIGENTES.clear();
    }
    /** Nunca consume presupuesto desde cada esclavo: todos comparten el unico lote del servidor. */
    public static boolean asegurar(ServerLevel level) {
        if(recargando)return false;
        Object manager=level.getServer().getRecipeManager();
        if(manager!=clave) { reiniciar();clave=manager;trabajo=new Trabajo(level); }
        return listo;
    }
    public static int fundicionesPorDiez(ServerLevel nivel,BlockPos pos,ItemStack stack) {
        return Fabricacion.energiaCombustible(nivel,pos,stack)/20;
    }
    public static boolean vigente(ServerLevel nivel,Recipe<?> receta) { return asegurar(nivel) && RECETAS_VIGENTES.contains(receta); }
    /** Lote de produccion probado sin servidor: limita tanto unidades como tiempo entre unidades. */
    public static <T> int lote(Iterator<T> entradas,int maximo,long limite,LongSupplier reloj,Consumer<T> procesar) {
        int hechas=0;
        while(hechas<maximo && entradas.hasNext() && reloj.getAsLong()<limite) { procesar.accept(entradas.next());hechas++; }
        return hechas;
    }
    private static final class Trabajo {
        final ServerLevel nivel;
        final Iterator<RecipeHolder<?>> recetas;
        final Iterator<Block> bloques=BuiltInRegistries.BLOCK.iterator();
        final Map<Item,Set<Block>> porItem=new HashMap<>();
        final ItemStack[] simuladores={new ItemStack(Items.DIAMOND_PICKAXE),new ItemStack(Items.DIAMOND_AXE),new ItemStack(Items.DIAMOND_SHOVEL),new ItemStack(Items.DIAMOND_HOE),new ItemStack(Items.SHEARS)};
        final ItemStack[][] herramientas=new ItemStack[HERRAMIENTAS.length][];
        Iterator<Map.Entry<Item,Set<Block>>> fuentes;
        List<Item> nodos;
        Iterator<Item> costes;
        int fase,pasadas;
        boolean cambio;
        Trabajo(ServerLevel nivel) {
            this.nivel=nivel;
            // La copia se captura una sola vez en el hilo de servidor, no entre lotes de un reload.
            recetas=List.copyOf(nivel.getServer().getRecipeManager().getRecipes()).iterator();
            for(int i=0;i<HERRAMIENTAS.length;i++) {
                herramientas[i]=new ItemStack[HERRAMIENTAS[i].length];
                for(int j=0;j<herramientas[i].length;j++)herramientas[i][j]=new ItemStack(HERRAMIENTAS[i][j]);
            }
        }
        void avanzar() {
            long limite=System.nanoTime()+2_000_000L;
            switch(fase) {
                case 0 -> {
                    lote(recetas,32,limite,System::nanoTime,h -> indexarReceta(nivel,h));
                    if(!recetas.hasNext())fase=1;
                }
                case 1 -> {
                    lote(bloques,8,limite,System::nanoTime,b -> {
                        try { indexarBloque(nivel,b,simuladores,porItem); }
                        catch(RuntimeException e) { /* Un bloque de otro mod sin contexto no invalida el indice completo. */ }
                    });
                    if(!bloques.hasNext()) { fuentes=porItem.entrySet().iterator();fase=2; }
                }
                case 2 -> {
                    lote(fuentes,8,limite,System::nanoTime,e -> FUENTES.put(e.getKey(),new Fuente(e.getKey(),e.getValue(),herramientaMinima(nivel,e.getValue(),herramientas))));
                    if(!fuentes.hasNext()) {
                        var todos=new LinkedHashSet<Item>();todos.addAll(FUENTES.keySet());todos.addAll(CRAFTEO.keySet());todos.addAll(FUNDICION.keySet());
                        nodos=List.copyOf(todos);costes=nodos.iterator();porItem.clear();fase=3;
                    }
                }
                case 3 -> {
                    lote(costes,128,limite,System::nanoTime,item -> cambio|=relajar(item));
                    if(!costes.hasNext()) {
                        if(!cambio || ++pasadas>=40)fase=4;
                        else { cambio=false;costes=nodos.iterator(); }
                    }
                }
                default -> {}
            }
        }
    }

	// ---------------------------------------------------------------- recetas

	private static void indexarReceta(ServerLevel level,RecipeHolder<?> h) {
			Recipe<?> r = h.value();
			try {
				if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe) {
					CraftingRecipe cr = (CraftingRecipe) r;
					if (cr.isSpecial()) {
						return;
					}
					ItemStack out = cr.assemble(CraftingInput.EMPTY);
					if (out.isEmpty()) {
						return;
					}
					List<List<Item>> grupos = new ArrayList<>();
                    var ingredientes=r instanceof ShapedRecipe forma?forma.getIngredients().stream().flatMap(java.util.Optional::stream).toList():cr.placementInfo().ingredients();
					for (Ingredient ing : ingredientes) {
						List<Item> alt = new ArrayList<>();
						ing.items().forEach(hold -> alt.add(hold.value()));
						if (alt.isEmpty()) {
							throw new IllegalStateException("ingrediente vacío");
						}
						grupos.add(alt);
					}
					boolean grande;
					if (r instanceof ShapedRecipe sr) {
						grande = sr.getWidth() > 2 || sr.getHeight() > 2;
					} else {
						grande = grupos.size() > 4;
					}
					CRAFTEO.computeIfAbsent(out.getItem(), x -> new ArrayList<>())
							.add(new Receta(out.getItem(),out.getCount(),grupos,grande,out,cr));
                    RECETAS_VIGENTES.add(cr);
				} else if (r instanceof SmeltingRecipe sm) {
					Ingredient ing = sm.input();
					List<Item> ins = new ArrayList<>();
					ing.items().forEach(hold -> ins.add(hold.value()));
					for (Item in : ins) {
						ItemStack out = sm.assemble(new SingleRecipeInput(new ItemStack(in)));
						if (!out.isEmpty()) {
							FUNDICION.computeIfAbsent(out.getItem(), x -> new ArrayList<>())
									.add(new Fundicion(in,out.getItem(),sm.cookingTime(),out,sm));
                            RECETAS_VIGENTES.add(sm);
						}
					}
				}
			} catch (RuntimeException e) {
				// receta rara que no sabemos leer: simplemente no la usamos
			}
	}

	// ---------------------------------------------------------------- bloques

	private static boolean natural(Block b, Item drop) {
		BlockState s = b.defaultBlockState();
		if (s.is(BlockTags.LOGS) || s.is(BlockTags.SAND) || s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD)
				|| s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW)) {
			return true;
		}
		return b.asItem() != drop;
	}

	private static void indexarBloque(ServerLevel level,Block b,ItemStack[] simuladores,Map<Item,Set<Block>> porItem) {
        BlockPos origen=BlockPos.ZERO.above(64);
			if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) {
				return;
			}
			BlockState s = b.defaultBlockState();
			if (s.getDestroySpeed(level, origen) < 0 || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
				return;
			}
			if (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER || b == Blocks.BUDDING_AMETHYST) {
				return;
			}
			Set<Item> vistos = new HashSet<>();
			for (ItemStack tool : simuladores) {
				for (int i = 0; i < 4; i++) {
					try {
						for (ItemStack d : Block.getDrops(s, level, origen, null, null, tool)) {
							if (!d.isEmpty()) {
								vistos.add(d.getItem());
							}
						}
					} catch (RuntimeException e) {
						break;
					}
				}
			}
			for (Item it : vistos) {
				if (natural(b, it)) {
					porItem.computeIfAbsent(it, x -> new LinkedHashSet<>()).add(b);
				}
			}

	}

	/** Herramienta más barata que sirve para recoger alguno de los bloques; null si basta la mano. */
	private static Item herramientaMinima(ServerLevel level, Set<Block> bloques,ItemStack[][] herramientas) {
		boolean algunoSinHerramienta = false;
		Item mejor = null;
		int mejorTier = 99;
		for (Block b : bloques) {
			BlockState s = b.defaultBlockState();
			if (!s.requiresCorrectToolForDrops()) {
				algunoSinHerramienta = true;
				continue;
			}
			for (int familiaIndice=0;familiaIndice<HERRAMIENTAS.length;familiaIndice++) {
                Item[] familia=HERRAMIENTAS[familiaIndice];
				for (int t = 0; t < familia.length; t++) {
					if (herramientas[familiaIndice][t].isCorrectToolForDrops(s)) {
						if (t < mejorTier) {
							mejorTier = t;
							mejor = familia[t];
						}
						break;
					}
				}
			}
		}
		return algunoSinHerramienta ? null : mejor;
	}

	// ---------------------------------------------------------------- costes

    private static boolean relajar(Item item) {
        boolean cambio=false;
        Fuente fuente=FUENTES.get(item);
        if(fuente!=null) {
            int c=2;
            if(fuente.herramienta()!=null) {
                int herramienta=coste(fuente.herramienta());
                if(herramienta<INF)c+=herramienta;else c=INF;
            }
            if(c<INF)cambio|=mejorar(item,c,new MetodoMinar(fuente));
        }
        for(Receta r:CRAFTEO.getOrDefault(item,List.of())) {
            int total=1;List<Item> eleccion=new ArrayList<>();boolean posible=true;
            for(List<Item> grupo:r.grupos()) {
                Item mejor=null;int mc=INF;
                for(Item it:grupo)if(coste(it)<mc) { mc=coste(it);mejor=it; }
                if(mejor==null) { posible=false;break; }
                total+=mc;eleccion.add(mejor);
            }
            if(!posible)continue;
            if(r.grande()) { int mesa=coste(Items.CRAFTING_TABLE);if(mesa>=INF)continue;total+=mesa; }
            cambio|=mejorar(item,total,new MetodoCraftear(r,eleccion));
        }
        for(Fundicion f:FUNDICION.getOrDefault(item,List.of())) {
            int ci=coste(f.entrada()),ch=coste(Items.FURNACE),cc=Math.min(coste(Items.COAL),Math.min(coste(Items.CHARCOAL),coste(Items.OAK_PLANKS)));
            if(ci<INF && ch<INF && cc<INF)cambio|=mejorar(item,ci+ch+cc+2,new MetodoFundir(f));
        }
        return cambio;
    }

	private static boolean mejorar(Item item, int coste, Metodo m) {
		Integer actual = COSTE.get(item);
		if (actual == null || coste < actual) {
			COSTE.put(item, coste);
			METODO.put(item, m);
			return true;
		}
		return false;
	}

	public static int coste(Item item) {
		Integer c = COSTE.get(item);
		return c == null ? INF : c;
	}

	public static Metodo metodo(Item item) {
		return METODO.get(item);
	}

	public static Fuente fuente(Item item) {
		return FUENTES.get(item);
	}

	public static int fuentes() {
		return FUENTES.size();
	}

	/** Items que se pueden pedir: los que tienen método de obtención. */
	public static boolean sabeHacer(Item item) {
		return COSTE.containsKey(item);
	}
}
