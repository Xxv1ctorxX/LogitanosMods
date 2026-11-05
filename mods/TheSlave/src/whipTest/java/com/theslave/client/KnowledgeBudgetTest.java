package com.theslave.client;

import com.theslave.ai.Conocimiento;
import java.util.*;
import java.util.stream.IntStream;

/** Usa el planificador de lotes de produccion, sin instanciar servidor, mundo o loot context. */
public final class KnowledgeBudgetTest {
    public static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var entradas=IntStream.range(0,10000).boxed().toList().iterator();List<Integer> hechas=new ArrayList<>();
        if(Conocimiento.lote(entradas,8,Long.MAX_VALUE,() -> 0,hechas::add)!=8 || hechas.size()!=8)throw new AssertionError("Excede el presupuesto de bloques");
        if(Conocimiento.lote(entradas,0,Long.MAX_VALUE,() -> 0,hechas::add)!=0 || hechas.size()!=8)throw new AssertionError("Ejecuta con presupuesto agotado");
        long[] reloj={0};
        if(Conocimiento.lote(entradas,128,3,() -> reloj[0]++,hechas::add)!=3)throw new AssertionError("Ignora el presupuesto temporal");
        if(Conocimiento.lote(entradas,128,0,() -> 0,hechas::add)!=0)throw new AssertionError("Procesa despues del limite temporal");
        while(entradas.hasNext())Conocimiento.lote(entradas,32,Long.MAX_VALUE,() -> 0,hechas::add);
        if(hechas.size()!=10000 || new HashSet<>(hechas).size()!=10000)throw new AssertionError("Omite o duplica entradas entre ticks");
        for(int i=0;i<hechas.size();i++)if(hechas.get(i)!=i)throw new AssertionError("Cambia el orden al reanudar");
        probarCostes();
        System.out.println("Conocimiento: presupuesto de unidades/tiempo y reanudacion de 10000 nodos correctos.");
    }
    @SuppressWarnings("unchecked")
    private static void probarCostes() {
        try {
            var fuentesCampo=Conocimiento.class.getDeclaredField("FUENTES");fuentesCampo.setAccessible(true);
            var recetasCampo=Conocimiento.class.getDeclaredField("CRAFTEO");recetasCampo.setAccessible(true);
            var costeCampo=Conocimiento.class.getDeclaredField("COSTE");costeCampo.setAccessible(true);
            var metodoCampo=Conocimiento.class.getDeclaredField("METODO");metodoCampo.setAccessible(true);
            var fuentes=(Map<net.minecraft.world.item.Item,Conocimiento.Fuente>)fuentesCampo.get(null);
            var recetas=(Map<net.minecraft.world.item.Item,List<Conocimiento.Receta>>)recetasCampo.get(null);
            var costes=(Map<?,?>)costeCampo.get(null);var metodos=(Map<?,?>)metodoCampo.get(null);
            var stone=net.minecraft.world.item.Items.STONE;var plank=net.minecraft.world.item.Items.OAK_PLANKS;var stick=net.minecraft.world.item.Items.STICK;
            fuentes.put(stone,new Conocimiento.Fuente(stone,Set.of(net.minecraft.world.level.block.Blocks.STONE),null));
            recetas.put(plank,List.of(new Conocimiento.Receta(plank,1,List.of(List.of(stone)),false)));
            recetas.put(stick,List.of(new Conocimiento.Receta(stick,1,List.of(List.of(plank)),false)));
            var diamante=net.minecraft.world.item.Items.DIAMOND;var esmeralda=net.minecraft.world.item.Items.EMERALD;
            recetas.put(diamante,List.of(new Conocimiento.Receta(diamante,1,List.of(List.of(esmeralda)),false)));
            recetas.put(esmeralda,List.of(new Conocimiento.Receta(esmeralda,1,List.of(List.of(diamante)),false)));
            var relajar=Conocimiento.class.getDeclaredMethod("relajar",net.minecraft.world.item.Item.class);relajar.setAccessible(true);
            for(int pasada=0;pasada<5;pasada++)for(var item:List.of(stick,plank,stone,diamante,esmeralda))relajar.invoke(null,item);
            if(Conocimiento.coste(stone)!=2 || Conocimiento.coste(plank)!=3 || Conocimiento.coste(stick)!=4)throw new AssertionError("Los lotes alteran la convergencia de recetas");
            if(Conocimiento.coste(diamante)<Conocimiento.INF || Conocimiento.coste(esmeralda)<Conocimiento.INF)throw new AssertionError("Un ciclo sin fuente produce recursos");
            fuentes.clear();recetas.clear();costes.clear();metodos.clear();
        }catch(ReflectiveOperationException ex) { throw new AssertionError("No se pudo probar el calculo real de costes",ex); }
    }
    public static void main(String[] args) { run(); }
}
