package com.theslave.client;

import com.theslave.ai.*;
import com.theslave.entity.Inv;
import java.util.*;
import java.util.function.ToIntFunction;
import net.minecraft.core.component.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Recetas vanilla reales y mochila real; no hay servidor ni mundo instanciados. */
public final class FabricacionTest {
    private static int checks;
    private static void require(boolean b,String m) { checks++;if(!b)throw new AssertionError(m); }
    private static void bind(Item item,int max) { item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,max).build()); }
    public static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(Item item:List.of(Items.STONE,Items.PAPER,Items.DIAMOND,Items.IRON_ORE,Items.IRON_INGOT,Items.COAL,Items.OAK_PLANKS,Items.BIRCH_PLANKS))bind(item,64);
        bind(Items.MILK_BUCKET,1);bind(Items.LAVA_BUCKET,1);bind(Items.BUCKET,16);
        ItemStack output=new ItemStack(Items.DIAMOND,2);output.set(DataComponents.CUSTOM_NAME,Component.literal("Pieza unica"));
        var shaped=new ShapedRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),
            ShapedRecipePattern.of(Map.of('M',Ingredient.of(Items.MILK_BUCKET),'P',Ingredient.of(Items.PAPER)),"M","P"),ItemStackTemplate.fromNonEmptyStack(output));
        var receta=new Conocimiento.Receta(Items.DIAMOND,2,List.of(List.of(Items.MILK_BUCKET),List.of(Items.PAPER)),false,output,shaped);
        var metodo=new Conocimiento.MetodoCraftear(receta,List.of(Items.MILK_BUCKET,Items.PAPER));
        var inv=new Inv();inv.set(0,new ItemStack(Items.MILK_BUCKET));inv.set(1,new ItemStack(Items.PAPER));
        require(Fabricacion.craftear(null,inv,metodo,1),"Falla receta shaped nativa");
        require(inv.count(Items.DIAMOND)==2 && inv.count(Items.BUCKET)==1 && inv.count(Items.MILK_BUCKET)==0,"Pierde resultado o recipiente nativo");
        require(Component.literal("Pieza unica").equals(inv.buscar(s -> s.is(Items.DIAMOND)).get(DataComponents.CUSTOM_NAME)),"Pierde componentes del resultado");
        var falta=new Inv();falta.set(0,new ItemStack(Items.MILK_BUCKET));
        require(!Fabricacion.craftear(null,falta,metodo,1) && falta.count(Items.MILK_BUCKET)==1 && falta.count(Items.BUCKET)==0,"Consume ingredientes de un crafteo fallido");
        var llena=new Inv();for(int i=0;i<Inv.SIZE;i++)llena.set(i,new ItemStack(Items.STONE,64));
        llena.set(0,new ItemStack(Items.MILK_BUCKET));llena.set(1,new ItemStack(Items.PAPER,64));
        require(!Fabricacion.craftear(null,llena,metodo,1) && llena.count(Items.MILK_BUCKET)==1 && llena.count(Items.PAPER)==64,"Pierde recipiente al faltar hueco");
        var copia=falta.copiar();copia.get(0).shrink(1);require(falta.count(Items.MILK_BUCKET)==1,"Inv.copiar comparte stacks");
        falta.reemplazar(copia);copia.set(0,new ItemStack(Items.DIAMOND));require(falta.isEmpty(),"Inv.reemplazar comparte array");
        var capacidad=new Inv();for(int i=0;i<Inv.SIZE;i++)capacidad.set(i,new ItemStack(Items.STONE,64));
        capacidad.set(0,ItemStack.EMPTY);
        require(!capacidad.cabe(new ItemStack(Items.DIAMOND,65)),"Un hueco admite mas de una pila");
        require(capacidad.cabe(new ItemStack(Items.DIAMOND,64)),"Un hueco libre no admite una pila");
        ItemStack[] muestras={new ItemStack(Items.DIAMOND,2)};
        require(com.theslave.entity.EsclavoEntity.tomarMuestra(muestras,Items.PAPER).isEmpty(),"Repeticion fabrica una muestra ausente");
        ItemStack muestra=com.theslave.entity.EsclavoEntity.tomarMuestra(muestras,Items.DIAMOND);
        require(muestra.getCount()==1 && muestras[0].getCount()==1,"Repeticion no debita una muestra real");
        muestras[0].set(DataComponents.CUSTOM_NAME,Component.literal("Reservado"));
        require(com.theslave.entity.EsclavoEntity.tomarMuestra(muestras,Items.DIAMOND).isEmpty() && muestras[0].getCount()==1,"Repeticion consume pieza protegida");
        var mezcla=new ShapelessRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),ItemStackTemplate.fromNonEmptyStack(output),List.of(Ingredient.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS),Ingredient.of(Items.OAK_PLANKS)));
        var m=new Conocimiento.MetodoCraftear(new Conocimiento.Receta(Items.DIAMOND,2,List.of(List.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS),List.of(Items.OAK_PLANKS)),false,output,mezcla),List.of(Items.OAK_PLANKS,Items.OAK_PLANKS));
        var alternativas=new Inv();alternativas.set(0,new ItemStack(Items.OAK_PLANKS));alternativas.set(1,new ItemStack(Items.BIRCH_PLANKS));
        require(Fabricacion.craftear(null,alternativas,m,1) && alternativas.count(Items.DIAMOND)==2,"La eleccion voraz bloquea ingredientes compatibles");
        require(!Fabricacion.craftear(null,alternativas,m,17),"Permite tandas sin presupuesto");

        ItemStack lingote=new ItemStack(Items.IRON_INGOT,2);lingote.set(DataComponents.CUSTOM_NAME,Component.literal("Metal marcado"));
        var smelting=new SmeltingRecipe(new Recipe.CommonInfo(true),new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC,""),Ingredient.of(Items.IRON_ORE),ItemStackTemplate.fromNonEmptyStack(lingote),0.7f,200);
        var horno=new Conocimiento.Fundicion(Items.IRON_ORE,Items.IRON_INGOT,200,lingote,smelting);
        var metales=new Inv();metales.set(0,new ItemStack(Items.IRON_ORE,4));
        var resultado=fundir(metales,horno,2,400,s -> 0);
        require(resultado.hechas()==2 && resultado.energia()==0 && metales.count(Items.IRON_INGOT)==4 && metales.count(Items.IRON_ORE)==2,"Funde mas tandas o pierde cantidad nativa");
        require(Component.literal("Metal marcado").equals(metales.buscar(s -> s.is(Items.IRON_INGOT)).get(DataComponents.CUSTOM_NAME)),"Fundicion pierde componentes");
        var lava=new Inv();lava.set(0,new ItemStack(Items.IRON_ORE,2));lava.set(1,new ItemStack(Items.LAVA_BUCKET));
        resultado=fundir(lava,horno,1,0,s -> s.is(Items.LAVA_BUCKET)?20000:0);
        require(resultado.hechas()==1 && resultado.energia()==19800 && lava.count(Items.BUCKET)==1,"Pierde credito o cubo del combustible");
        resultado=fundir(lava,horno,1,resultado.energia(),s -> 0);
        require(resultado.hechas()==1 && resultado.energia()==19600 && lava.count(Items.BUCKET)==1,"Vuelve a consumir combustible con credito pendiente");
        var saturada=new Inv();for(int i=0;i<Inv.SIZE;i++)saturada.set(i,new ItemStack(Items.STONE,64));
        saturada.set(0,new ItemStack(Items.IRON_ORE,64));saturada.set(1,new ItemStack(Items.COAL,64));
        resultado=fundir(saturada,horno,1,0,s -> s.is(Items.COAL)?1600:0);
        require(resultado.hechas()==0 && resultado.energia()==0 && saturada.count(Items.COAL)==64 && saturada.count(Items.IRON_ORE)==64,"Consume entrada/combustible sin espacio");
        System.out.println("Fabricacion: "+checks+" pruebas de recetas nativas, recipientes, componentes, espacio y combustible correctas.");
    }
    private static Fabricacion.Resultado fundir(Inv inv,Conocimiento.Fundicion receta,int veces,int energia,ToIntFunction<ItemStack> combustible) {
        try {
            var metodo=Fabricacion.class.getDeclaredMethod("fundirConCredito",net.minecraft.server.level.ServerLevel.class,Inv.class,Conocimiento.Fundicion.class,int.class,int.class,ToIntFunction.class);
            metodo.setAccessible(true);return (Fabricacion.Resultado)metodo.invoke(null,null,inv,receta,veces,energia,combustible);
        }catch(ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    public static void main(String[] args) { run(); }
}
