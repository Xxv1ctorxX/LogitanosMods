package com.theslave.ai;

import com.theslave.entity.Inv;
import java.util.*;
import java.util.function.ToIntFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

/** Ejecuta las recetas nativas sobre una copia: ni ingredientes ni componentes se pierden al fallar. */
public final class Fabricacion {
    private Fabricacion() {}
    public record Resultado(int hechas,int energia,String fallo) {}
    private static final int MAX_TANDAS=16;

    public static boolean craftear(ServerLevel nivel,Inv inventario,Conocimiento.MetodoCraftear metodo,int veces) {
        if(veces<1 || veces>MAX_TANDAS || metodo==null || metodo.receta().receta()==null)return false;
        var nativa=metodo.receta().receta();
        if(nativa.isSpecial() || !(nativa instanceof ShapedRecipe || nativa instanceof ShapelessRecipe))return false;
        if(nivel!=null && !Conocimiento.vigente(nivel,nativa))return false;
        Inv copia=inventario.copiar();
        try {
            for(int tanda=0;tanda<veces;tanda++) {
                CraftingInput input=preparar(copia,metodo);
                if(input==null || !nativa.matches(input,nivel))return false;
                ItemStack resultado=nativa.assemble(input);
                if(resultado.isEmpty() || !resultado.is(metodo.receta().salida()))return false;
                var restos=nativa.getRemainingItems(input);
                if(!copia.insertar(resultado,Inv.Fuente.CRAFTEO).isEmpty())return false;
                for(ItemStack resto:restos)if(!copia.insertar(resto,Inv.Fuente.CRAFTEO).isEmpty())return false;
            }
        }catch(RuntimeException recetaIncompatible) { return false; }
        inventario.reemplazar(copia);return true;
    }
    private static CraftingInput preparar(Inv copia,Conocimiento.MetodoCraftear metodo) {
        CraftingRecipe receta=metodo.receta().receta();
        List<Optional<Ingredient>> celdas;int ancho,alto;
        if(receta instanceof ShapedRecipe forma) { ancho=forma.getWidth();alto=forma.getHeight();celdas=forma.getIngredients(); }
        else {
            var ingredientes=receta.placementInfo().ingredients();ancho=Math.min(3,ingredientes.size());
            if(ancho==0)return null;alto=(ingredientes.size()+ancho-1)/ancho;
            celdas=new ArrayList<>();ingredientes.forEach(i -> celdas.add(Optional.of(i)));
            while(celdas.size()<ancho*alto)celdas.add(Optional.empty());
        }
        if(ancho<1 || alto<1 || ancho>3 || alto>3)return null;
        List<Necesidad> pendientes=new ArrayList<>();int ingrediente=0;
        for(int i=0;i<celdas.size();i++)if(celdas.get(i).isPresent()) {
            Ingredient requisito=celdas.get(i).orElseThrow();List<Integer> slots=new ArrayList<>();
            Item preferido=ingrediente<metodo.eleccion().size()?metodo.eleccion().get(ingrediente):null;ingrediente++;
            for(int slot=0;slot<Inv.SIZE;slot++)if(!copia.get(slot).isEmpty() && requisito.test(copia.get(slot)))slots.add(slot);
            slots.sort(Comparator.comparingInt(slot -> preferido!=null && copia.get(slot).is(preferido)?0:1));
            if(slots.isEmpty())return null;pendientes.add(new Necesidad(i,slots));
        }
        pendientes.sort(Comparator.comparingInt(n -> n.slots.size()));
        int[] reservas=new int[Inv.SIZE],asignacion=new int[celdas.size()];Arrays.fill(asignacion,-1);
        if(!asignar(copia,pendientes,0,reservas,asignacion,new int[]{2048}))return null;
        List<ItemStack> grid=new ArrayList<>(celdas.size());
        for(int i=0;i<celdas.size();i++)grid.add(asignacion[i]<0?ItemStack.EMPTY:copia.get(asignacion[i]).copyWithCount(1));
        for(int i=0;i<Inv.SIZE;i++)if(reservas[i]>0)copia.get(i).shrink(reservas[i]);
        return CraftingInput.of(ancho,alto,grid);
    }
    private record Necesidad(int celda,List<Integer> slots) {}
    private static boolean asignar(Inv inv,List<Necesidad> necesidades,int indice,int[] usados,int[] grid,int[] presupuesto) {
        if(indice==necesidades.size())return true;
        Necesidad n=necesidades.get(indice);
        for(int slot:n.slots) {
            if(--presupuesto[0]<0)return false;
            if(usados[slot]>=inv.get(slot).getCount())continue;
            usados[slot]++;grid[n.celda]=slot;
            if(asignar(inv,necesidades,indice+1,usados,grid,presupuesto))return true;
            usados[slot]--;grid[n.celda]=-1;
        }
        return false;
    }
    private static int elegir(Inv inv,Ingredient ingrediente,Item preferido) {
        for(int ronda=0;ronda<2;ronda++)for(int i=0;i<Inv.SIZE;i++) {
            ItemStack stack=inv.get(i);
            if(!stack.isEmpty() && ingrediente.test(stack) && (ronda==1 || (preferido!=null && stack.is(preferido))))return i;
        }
        return -1;
    }

    public static Resultado fundir(ServerLevel nivel,Inv inventario,Conocimiento.Fundicion receta,int veces,int energia) {
        return fundir(nivel,BlockPos.ZERO.above(64),inventario,receta,veces,energia);
    }
    /** Energia conserva el combustible sobrante en unidades de trabajo (ticks de receta). */
    public static Resultado fundir(ServerLevel nivel,BlockPos posicion,Inv inventario,Conocimiento.Fundicion receta,int veces,int energia) {
        Horno horno=new Horno(posicion);
        return fundirConCredito(nivel,inventario,receta,veces,energia,stack -> energiaCombustible(nivel,horno,stack));
    }
    public static int energiaCombustible(ServerLevel nivel,BlockPos posicion,ItemStack stack) {
        return energiaCombustible(nivel,new Horno(posicion),stack);
    }
    private static int energiaCombustible(ServerLevel nivel,Horno horno,ItemStack stack) {
        if(stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL))return 0;
        int tiempo=horno.duracion(nivel,stack);float velocidad=horno.velocidad(nivel,stack);
        double trabajo=tiempo*(double)velocidad;
        return tiempo>0 && velocidad>0 && Double.isFinite(trabajo) && trabajo>=1 && trabajo<256000?(int)trabajo:0;
    }
    private static Resultado fundirConCredito(ServerLevel nivel,Inv inventario,Conocimiento.Fundicion receta,int veces,int energia,ToIntFunction<ItemStack> combustible) {
        if(veces<1 || veces>MAX_TANDAS || energia<0 || receta==null || receta.receta()==null)return new Resultado(0,Math.max(0,energia),"receta invalida");
        if(nivel!=null && !Conocimiento.vigente(nivel,receta.receta()))return new Resultado(0,energia,"recetas pendientes de actualizar");
        int hechas=0;
        for(int tanda=0;tanda<veces;tanda++) {
            Inv copia=inventario.copiar();int restante=energia;
            try {
                int slot=elegir(copia,receta.receta().input(),receta.entrada());
                if(slot<0)return new Resultado(hechas,energia,"faltan materiales");
                ItemStack input=copia.get(slot).copyWithCount(1);
                var entrada=new SingleRecipeInput(input);
                if(!receta.receta().matches(entrada,nivel))return new Resultado(hechas,energia,"entrada incompatible");
                ItemStack salida=receta.receta().assemble(entrada);
                if(salida.isEmpty() || !salida.is(receta.salida()))return new Resultado(hechas,energia,"resultado incompatible");
                copia.get(slot).shrink(1);int costo=Math.max(1,receta.receta().cookingTime());
                while(restante<costo) {
                    int slotCombustible=-1,aporte=0;
                    for(int i=0;i<Inv.SIZE;i++) {
                        ItemStack stack=copia.get(i);if(stack.isEmpty() || stack.is(receta.entrada()))continue;
                        int trabajo=combustible.applyAsInt(stack);
                        if(trabajo>0 && trabajo<Integer.MAX_VALUE-costo) { slotCombustible=i;aporte=trabajo;break; }
                    }
                    if(slotCombustible<0)return new Resultado(hechas,energia,"falta combustible");
                    ItemStack fuel=copia.get(slotCombustible);var resto=fuel.getItem().getCraftingRemainder();fuel.shrink(1);
                    if(resto!=null && !copia.insertar(resto.create(),Inv.Fuente.FUNDIDO).isEmpty())return new Resultado(hechas,energia,"sin espacio para el recipiente");
                    restante=Math.addExact(restante,aporte);
                }
                if(!copia.insertar(salida,Inv.Fuente.FUNDIDO).isEmpty())return new Resultado(hechas,energia,"sin espacio");
                restante-=costo;inventario.reemplazar(copia);energia=restante;hechas++;
            }catch(RuntimeException recetaIncompatible) { return new Resultado(hechas,energia,"receta incompatible"); }
        }
        return new Resultado(hechas,energia,"");
    }
    /** Solo contexto de horno: no se coloca un bloque ni se carga terreno para leer el combustible. */
    private static final class Horno extends FurnaceBlockEntity {
        Horno(BlockPos pos) { super(pos,Blocks.FURNACE.defaultBlockState()); }
        int duracion(ServerLevel nivel,ItemStack fuel) { return getBurnDuration(nivel,fuel); }
        float velocidad(ServerLevel nivel,ItemStack fuel) { return getSpeedMultiplier(nivel,fuel); }
    }
}
