package com.theslave.client;

import com.google.gson.*;
import com.theslave.ritual.MuerteDurable;
import com.theslave.ritual.RitualDurable;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Pruebas de codigo real de muertes, sin instanciar ni arrancar un servidor/mundo. */
public final class DeathDurabilityTest {
    private static int checks;
    private static void require(boolean b,String mensaje) { checks++;if(!b)throw new AssertionError(mensaje); }
    private static void rechaza(Runnable tarea) { checks++;try { tarea.run();throw new AssertionError("Se acepto evidencia incompleta"); }catch(IllegalStateException expected) {} }
    private static CompoundTag chunk(CompoundTag entidad) {
        CompoundTag chunk=new CompoundTag();ListTag lista=new ListTag();lista.add(entidad);chunk.put("Entities",lista);return chunk;
    }
    private static ItemStack stack(int count) {
        // Los componentes vanilla proceden de datapacks desde 26.3. Usamos un mapa explicito
        // para probar ItemStack real sin arrancar una carga de recursos ni un mundo.
        try {
            var constructor=ItemStack.class.getDeclaredConstructor(net.minecraft.core.Holder.class,int.class,net.minecraft.core.component.PatchedDataComponentMap.class);
            constructor.setAccessible(true);
            var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
            return constructor.newInstance(Items.DIAMOND.builtInRegistryHolder(),count,new net.minecraft.core.component.PatchedDataComponentMap(components));
        }catch(ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    public static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        UUID vinculo=UUID.randomUUID();JsonObject vivo=new JsonObject();vivo.addProperty("id",vinculo.toString());vivo.addProperty("estado","VIVO");vivo.addProperty("muertes",2);vivo.addProperty("esclavo",UUID.randomUUID().toString());
        JsonArray antes=new JsonArray();antes.add(vivo);
        var muerto=MuerteDurable.cuentaDespues(antes,vinculo,MuerteDurable.Tipo.MUERTE,"Nombre",1);
        require(antes.get(0).getAsJsonObject().get("estado").getAsString().equals("VIVO"),"La preparacion muta la cuenta viva");
        var ficha=muerto.get(0).getAsJsonObject();
        require(ficha.get("estado").getAsString().equals("DIFUNTO") && ficha.get("muertes").getAsInt()==3 && !ficha.has("esclavo"),"La muerte no deja una generacion estable");
        rechaza(() -> MuerteDurable.cuentaDespues(muerto,vinculo,MuerteDurable.Tipo.MUERTE,"Nombre",1));
        require(MuerteDurable.cuentaDespues(muerto,vinculo,MuerteDurable.Tipo.ENTERRAR,"",1).get(0).getAsJsonObject().get("estado").getAsString().equals("LIBERADO"),"Enterrar no libera la plaza");
        require(MuerteDurable.cuentaDespues(muerto,vinculo,MuerteDurable.Tipo.RECUPERAR_CUSTODIA,"",1).equals(muerto),"Recuperar custodia cambia el vinculo");
        rechaza(() -> MuerteDurable.cuentaDespues(antes,vinculo,MuerteDurable.Tipo.ENTERRAR,"",1));
        var legado=new com.theslave.Registro.Vinculo();legado.estado=com.theslave.Registro.Estado.DIFUNTO;legado.muertes=1;
        require(com.theslave.block.TumbaBlockEntity.normalizarMuertesLegacy(0,legado)==1,"No repara lapida legado de primera muerte");
        require(com.theslave.block.TumbaBlockEntity.normalizarMuertesLegacy(3,legado)==3,"Altera una generacion no legada");
        legado.muertes=2;require(com.theslave.block.TumbaBlockEntity.normalizarMuertesLegacy(0,legado)==0,"Asigna la lapida antigua a una muerte posterior");
        legado.muertes=1;legado.estado=com.theslave.Registro.Estado.VIVO;
        require(com.theslave.block.TumbaBlockEntity.normalizarMuertesLegacy(0,legado)==0,"Modifica la lapida de un vivo");
        require(com.theslave.block.TumbaBlockEntity.normalizarMuertesLegacy(0,null)==0,"Inventa una generacion sin vinculo resuelto");

        var op=new MuerteDurable.Operacion();op.id=UUID.randomUUID();op.dueno=UUID.randomUUID();op.vinculo=vinculo;op.cuerpo=UUID.randomUUID();op.tipo=MuerteDurable.Tipo.MUERTE;op.dimension="minecraft:overworld";op.cuentaAntes=antes;op.cuentaDespues=muerto;op.muertes=3;
        CompoundTag drop=new CompoundTag();drop.store("UUID",UUIDUtil.CODEC,UUID.randomUUID());drop.putString("TheSlaveBotinOperacion",op.id.toString());drop.putBoolean("TheSlaveBotinCustodia",true);
        ListTag posicion=new ListTag();posicion.add(DoubleTag.valueOf(8));posicion.add(DoubleTag.valueOf(65));posicion.add(DoubleTag.valueOf(8));drop.put("Pos",posicion);
        CompoundTag item=new CompoundTag();item.putString("id","minecraft:diamond");item.putInt("count",7);drop.put("Item",item);
        op.botin.add(RitualDurable.codificar(drop));MuerteDurable.validar(op);checks++;
        var codec=new Gson();MuerteDurable.validar(codec.fromJson(codec.toJson(op),MuerteDurable.Operacion.class));checks++;
        MuerteDurable.verificarBotin(op,List.of(chunk(drop)));checks++;
        rechaza(() -> MuerteDurable.verificarBotin(op,List.of(new CompoundTag())));
        var perdido=drop.copy();perdido.getCompoundOrEmpty("Item").putInt("count",6);
        rechaza(() -> MuerteDurable.verificarBotin(op,List.of(chunk(perdido))));
        var sinCustodia=drop.copy();sinCustodia.putBoolean("TheSlaveBotinCustodia",false);
        rechaza(() -> MuerteDurable.verificarBotin(op,List.of(chunk(sinCustodia))));
        rechaza(() -> MuerteDurable.verificarBotin(op,List.of(chunk(drop),chunk(drop))));
        op.botin.add(op.botin.get(0));rechaza(() -> MuerteDurable.validar(op));op.botin.remove(1);
        MuerteDurable.compactar(op);require(!op.botin.isEmpty() && op.cuentaAntes!=null,"Compacta una muerte aun pendiente");
        op.confirmada=true;String walCompleto=codec.toJson(op);
        MuerteDurable.compactar(op);
        require(op.resumida && op.botin.isEmpty() && op.cuentaAntes==null && op.idsBotin.size()==1,"Conserva snapshots historicos en RAM");
        UUID identidad=op.idsBotin.get(0);MuerteDurable.compactar(op);
        require(op.idsBotin.size()==1 && identidad.equals(op.idsBotin.get(0)),"La compactacion cambia identidad de botin");
        var desdeDisco=codec.fromJson(walCompleto,MuerteDurable.Operacion.class);MuerteDurable.validar(desdeDisco);
        require(!desdeDisco.botin.isEmpty() && desdeDisco.cuentaAntes!=null && !desdeDisco.resumida,"El WAL completo no puede recuperarse tras compactar RAM");

        ItemStack[] mochila=new ItemStack[43];Arrays.fill(mochila,ItemStack.EMPTY);mochila[0]=stack(60);
        var resto=MuerteDurable.insertar(mochila,stack(7));
        require(resto.isEmpty() && mochila[0].getCount()==64 && mochila[1].getCount()==3,"La devolucion no apila correctamente");
        for(int i=0;i<36;i++)mochila[i]=stack(64);
        resto=MuerteDurable.insertar(mochila,stack(7));
        require(resto.getCount()==7 && mochila[36].isEmpty(),"El excedente desaparece o ocupa equipo del jugador");
        try {
            var itemType=net.minecraft.world.entity.item.ItemEntity.class;
            itemType.getDeclaredMethod("tick");itemType.getDeclaredMethod("isMergable");itemType.getDeclaredMethod("playerTouch",net.minecraft.world.entity.player.Player.class);
            itemType.getDeclaredMethod("hurtServer",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.damagesource.DamageSource.class,float.class);
            net.minecraft.world.level.block.entity.HopperBlockEntity.class.getDeclaredMethod("addItem",net.minecraft.world.Container.class,itemType);checks++;
        }catch(ReflectiveOperationException ex) { throw new AssertionError("Cambió la API de custodia de botin",ex); }
        System.out.println("MuerteDurable: "+checks+" comprobaciones puras correctas.");
    }
    public static void main(String[] args) { run(); }
}
