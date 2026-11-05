package com.theslave.client;

import com.google.gson.Gson;
import com.theslave.Registro;
import com.theslave.registry.ModComponents;
import com.theslave.ritual.Hogares;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Valida las reglas usadas por el hogar real y el alma, sin crear mundos ni servidores. */
public final class HomeDurabilityTest {
    private static int checks;
    private static void require(boolean ok,String text) { checks++;if(!ok)throw new AssertionError(text); }
    private static void rechaza(Runnable r) { checks++;try { r.run();throw new AssertionError("Acepta hogar corrupto"); }catch(IllegalStateException expected) {} }
    public static void run() {
        UUID owner=UUID.randomUUID(),link=UUID.randomUUID(),token=UUID.randomUUID();
        var ficha=new Registro.Vinculo();ficha.id=link;ficha.estado=Registro.Estado.DIFUNTO;ficha.muertes=3;
        var alma=new ModComponents.AlmaVinculada(owner,link,3,token,false);
        require(Hogares.validarAlma(alma,owner,ficha,token,true),"Rechaza alma propia de generacion actual");
        require(!Hogares.validarAlma(alma,owner,ficha,token,false),"Acepta alma sin recibo de extraccion");
        require(!Hogares.validarAlma(alma,UUID.randomUUID(),ficha,token,true),"Acepta alma de otro dueno");
        require(!Hogares.validarAlma(alma,owner,ficha,UUID.randomUUID(),true),"Acepta token de otra muerte");
        ficha.muertes=4;require(!Hogares.validarAlma(alma,owner,ficha,token,true),"Reutiliza alma de una muerte previa");ficha.muertes=3;
        ficha.estado=Registro.Estado.VIVO;require(!Hogares.validarAlma(alma,owner,ficha,token,true),"Resucita un vivo con alma vieja");ficha.estado=Registro.Estado.DIFUNTO;
        ficha.fusionado=true;require(!Hogares.validarAlma(alma,owner,ficha,token,true),"Usa alma normal para fusionado");
        var doble=new ModComponents.AlmaVinculada(owner,link,3,token,true);require(Hogares.validarAlma(doble,owner,ficha,token,true),"Rechaza alma fusionada valida");
        ficha.id=UUID.randomUUID();require(!Hogares.validarAlma(doble,owner,ficha,token,true),"Acepta otro vinculo con generacion igual");
        var h=new Hogares.Hogar();h.dueno=owner;h.vinculo=link;Hogares.validar(h);checks++;
        h.dimension="minecraft:overworld";h.pie=new BlockPos(10,64,10).asLong();h.frente=0;h.extracciones.put(3,UUID.randomUUID());
        var gson=new Gson();var restaurado=gson.fromJson(gson.toJson(h),Hogares.Hogar.class);Hogares.validar(restaurado);
        require(restaurado.pie.equals(h.pie) && restaurado.extracciones.equals(h.extracciones),"Pierde ubicacion o recibo al guardar");
        Hogares.validarColeccion(List.of(h));checks++;
        var otro=gson.fromJson(gson.toJson(h),Hogares.Hogar.class);otro.vinculo=UUID.randomUUID();rechaza(() -> Hogares.validarColeccion(List.of(h,otro)));
        otro.pie=new BlockPos(100,64,100).asLong();Hogares.validarColeccion(List.of(h,otro));checks++;
        otro.frente=8;rechaza(() -> Hogares.validar(otro));otro.frente=0;
        otro.extracciones.put(0,UUID.randomUUID());rechaza(() -> Hogares.validar(otro));
        ficha.estado=Registro.Estado.DIFUNTO;ficha.muertes=3;
        require(Hogares.validarRenuncia(ficha,3,false),"No permite renunciar al difunto actual");
        require(!Hogares.validarRenuncia(ficha,2,false),"Una lapida antigua libera una muerte posterior");
        ficha.estado=Registro.Estado.LIBERADO;
        require(Hogares.validarRenuncia(ficha,3,true),"El reintento rechaza una cuenta ya aplicada");
        require(!Hogares.validarRenuncia(ficha,3,false),"Repite renuncia sin recibo");
        UUID confirmacion=UUID.randomUUID();
        require(!Hogares.confirmarRenuncia(owner,confirmacion,0),"El primer clic renuncia");
        require(Hogares.confirmarRenuncia(owner,confirmacion,100),"No acepta segundo clic dentro del limite");
        require(!Hogares.confirmarRenuncia(owner,confirmacion,101),"Reutiliza confirmacion consumida");
        require(!Hogares.confirmarRenuncia(owner,confirmacion,202),"Acepta confirmacion caducada");
        require(Hogares.confirmarRenuncia(owner,confirmacion,250),"No reinicia ventana despues de caducar");
        var legado=new Registro.Vinculo();legado.estado=Registro.Estado.VIVO;
        require(Hogares.necesitaLechoLegado(legado,null,false),"El legado sin hogar nunca recibe lecho");
        legado.contrato=UUID.randomUUID();
        require(Hogares.necesitaLechoLegado(legado,null,false),"Confunde contrato viejo con entrega de lecho");
        require(!Hogares.necesitaLechoLegado(legado,null,true),"Repite el lecho de una invocacion nueva confirmada");
        var portatil=new Hogares.Hogar();portatil.dueno=owner;portatil.vinculo=legado.id;portatil.extracciones.put(1,UUID.randomUUID());
        require(Hogares.debeEmitirLechoLegado(legado,portatil,false),"La extraccion antigua sin lecho impide su entrega");
        portatil.pie=new BlockPos(20,64,20).asLong();portatil.dimension="minecraft:overworld";
        require(Hogares.necesitaLechoLegado(legado,portatil,false) && !Hogares.debeEmitirLechoLegado(legado,portatil,false),"Un hogar colocado necesita otro item");
        portatil.lechoEntregado=true;portatil.reciboLecho=UUID.randomUUID();
        require(!Hogares.necesitaLechoLegado(legado,portatil,false),"Reemite un lecho con recibo persistido");
        var recibo=new Gson().fromJson(new Gson().toJson(portatil),Hogares.Hogar.class);
        require(!Hogares.necesitaLechoLegado(legado,recibo,false),"El recibo se pierde al recargar el hogar");
        legado.estado=Registro.Estado.LIBERADO;require(!Hogares.necesitaLechoLegado(legado,null,false),"Entrega lecho a un vinculo liberado");
        legado.estado=Registro.Estado.RESERVADO;require(!Hogares.necesitaLechoLegado(legado,null,false),"Entrega lecho antes de invocar");
        legado.estado=Registro.Estado.DIFUNTO;require(Hogares.necesitaLechoLegado(legado,null,false),"Deja sin lecho al difunto legado");
        var indice=new Hogares.Indice();
        for(int i=0;i<4096;i++) {
            var casa=new Hogares.Hogar();casa.dueno=owner;casa.vinculo=new UUID(0,i+1);casa.dimension="minecraft:overworld";
            casa.pie=new BlockPos(i*32,64,0).asLong();indice.poner(casa);
        }
        for(int i=0;i<4096;i++) {
            var pie=new BlockPos(i*32,64,0);String id=owner+":"+new UUID(0,i+1);
            require(id.equals(indice.en("minecraft:overworld",pie)),"No indexa el pie de la casa");
            require(indice.enChunk("minecraft:overworld",net.minecraft.world.level.ChunkPos.containing(pie)).size()==1,"Consulta hogares de chunks ajenos");
        }
        require(indice.en("minecraft:the_nether",new BlockPos(0,64,0))==null,"Mezcla posiciones de dimensiones distintas");
        var colision=new Hogares.Hogar();colision.dueno=owner;colision.vinculo=UUID.randomUUID();colision.dimension="minecraft:overworld";
        colision.pie=new BlockPos(0,64,-1).asLong();colision.frente=0;
        rechaza(() -> indice.poner(colision));require(indice.en(colision.dimension,BlockPos.of(colision.pie))==null,"Un alta rechazada deja media cama indexada");
        var retirada=new Hogares.Hogar();retirada.dueno=owner;retirada.vinculo=new UUID(0,1);retirada.dimension="minecraft:overworld";retirada.pie=BlockPos.containing(0,64,0).asLong();
        indice.quitar(retirada);require(indice.enChunk(retirada.dimension,new net.minecraft.world.level.ChunkPos(0,0)).isEmpty(),"Retirar conserva un indice fantasma");
        System.out.println("Hogares: "+checks+" comprobaciones puras correctas.");
    }
    public static void main(String[] args) { run(); }
}
