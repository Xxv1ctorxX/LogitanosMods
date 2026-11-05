package com.theslave.client;

import com.google.gson.*;
import com.theslave.ritual.RitualDurable;
import com.theslave.ritual.RitualDurable.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;

/** Ejecuta confirmar(), el mismo orquestador de produccion, con puertos y ACK controlados. */
public final class RitualDurableTest {
    private static final Gson JSON=new Gson();
    private static int checks;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static void rechazar(Runnable action,String message) {
        try {action.run();throw new AssertionError("Se admitio "+message);}
        catch(RuntimeException expected) {checks++;}
    }
    private static Operacion copia(Operacion op) {return JSON.fromJson(JSON.toJson(op),Operacion.class);}
    private static Operacion operacion() {
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=UUID.randomUUID();op.resultado=UUID.randomUUID();
        op.dimension="minecraft:overworld";op.cx=-1;op.cz=2;
        op.inventarioAntes=new JsonArray();op.inventarioAntes.add(JsonNull.INSTANCE);op.inventarioDespues=op.inventarioAntes.deepCopy();
        op.cuentaAntes=new JsonArray();op.cuentaDespues=new JsonArray();
        UUID vinculo=UUID.randomUUID();JsonObject v=new JsonObject();v.addProperty("id",vinculo.toString());v.addProperty("estado","VIVO");v.addProperty("esclavo",op.resultado.toString());op.cuentaDespues.add(v);
        CompoundTag nbt=new CompoundTag();nbt.store("UUID",UUIDUtil.CODEC,op.resultado);
        nbt.putString("id","theslave:esclavo");nbt.putString("Dueno",op.dueno.toString());nbt.putString("Vinculo",vinculo.toString());
        nbt.putString("TheSlaveRitualOperacion",op.id.toString());nbt.putBoolean("TheSlaveRitualCustodia",true);nbt.putBoolean("TheSlaveRitualOriginal",false);
        CompoundTag item=new CompoundTag();item.putString("id","minecraft:diamond");item.putInt("count",2);
        ListTag mochila=new ListTag();mochila.add(item.copy());nbt.put("Mochila",mochila);nbt.put("Ref",item.copy());
        CompoundTag equipment=new CompoundTag();equipment.put("mainhand",item.copy());nbt.put("equipment",equipment);
        nbt.putInt("Variante",1);nbt.putInt("Variante2",-1);nbt.putString("custom_name","Individuo de prueba");
        nbt.putInt("EnergiaFundicion",2400);
        ListTag pos=new ListTag();pos.add(DoubleTag.valueOf(-0.5));pos.add(DoubleTag.valueOf(65));pos.add(DoubleTag.valueOf(32.5));nbt.put("Pos",pos);
        op.snapshot=RitualDurable.codificar(nbt);return op;
    }
    private static CompoundTag chunk(Operacion op) {
        CompoundTag chunk=new CompoundTag();ListTag entities=new ListTag();entities.add(RitualDurable.decodificar(op.snapshot));chunk.put("Entities",entities);return chunk;
    }
    private static void cuerpo(Operacion op,Consumer<CompoundTag> change) {var nbt=RitualDurable.decodificar(op.snapshot);change.accept(nbt);op.snapshot=RitualDurable.codificar(nbt);}
    private static void validacion() {
        Operacion ok=operacion();RitualDurable.validar(ok);check(RitualDurable.decodificar(ok.snapshot).equals(RitualDurable.decodificar(RitualDurable.codificar(RitualDurable.decodificar(ok.snapshot)))),"Roundtrip NBT");
        Operacion suplica=copia(ok);suplica.tipo=Tipo.SUPLICA;
        suplica.difunto=UUID.fromString(suplica.cuentaDespues.get(0).getAsJsonObject().get("id").getAsString());suplica.muertes=2;
        RitualDurable.validar(suplica);check(suplica.tumba==null,"El alma permite suplicar sin tumba junto al altar");
        Operacion fusionada=copia(suplica);fusionada.tipo=null;cuerpo(fusionada,n->n.putInt("Variante2",2));
        RitualDurable.validar(fusionada);check(fusionada.tipo==Tipo.SUPLICA_FUSION,"Migracion clasifica un alma fusionada como normal");
        Operacion tipoFalso=copia(fusionada);tipoFalso.tipo=Tipo.SUPLICA;
        rechazar(()->RitualDurable.validar(tipoFalso),"retorno normal de un cuerpo fusionado");
        Huella huella=RitualDurable.resumir(ok);
        String metadata=JSON.toJson(huella);
        check(huella.id().equals(ok.id)&&huella.resultado().equals(ok.resultado),"Resumen pierde identidad");
        check(!metadata.contains("snapshot")&&!metadata.contains("inventarioAntes")&&!metadata.contains("cuentaDespues"),"El indice retiene los payloads historicos");
        Operacion almaAjena=copia(suplica);almaAjena.difunto=UUID.randomUUID();
        rechazar(()->RitualDurable.validar(almaAjena),"cuerpo de otro vinculo que el alma");
        Operacion entregaIncorrecta=copia(suplica);entregaIncorrecta.lechoEntregado=true;
        rechazar(()->RitualDurable.validar(entregaIncorrecta),"segundo lecho al resucitar");
        List<Consumer<Operacion>> invalidos=List.of(
            op->op.esquema=2,op->op.id=null,op->op.dueno=null,op->op.resultado=null,op->op.dimension="INVALID DIMENSION",
            op->op.snapshot="invalid base64",op->op.estado=null,op->op.originales=null,op->op.originales.add(op.resultado),
            op->op.originales.add(UUID.randomUUID()),op->{UUID id=UUID.randomUUID();op.originales.add(id);op.originales.add(id);},
            op->op.inventarioAntes=new JsonArray(),op->op.inventarioDespues.add(JsonNull.INSTANCE),op->op.tumba=1L,
            op->op.difunto=UUID.randomUUID(),op->op.muertes=-1,op->op.cx++,op->op.cz++,
            op->cuerpo(op,n->n.store("UUID",UUIDUtil.CODEC,UUID.randomUUID())),
            op->cuerpo(op,n->n.putString("id","minecraft:pig")),op->cuerpo(op,n->n.putString("Dueno",UUID.randomUUID().toString())),
            op->cuerpo(op,n->n.putString("Vinculo","broken")),op->cuerpo(op,n->n.putString("TheSlaveRitualOperacion",UUID.randomUUID().toString())),
            op->cuerpo(op,n->n.putBoolean("TheSlaveRitualCustodia",false)),op->cuerpo(op,n->n.putBoolean("TheSlaveRitualOriginal",true)),
            op->cuerpo(op,n->n.remove("Pos")),op->cuerpo(op,n->{ListTag pos=new ListTag();pos.add(DoubleTag.valueOf(Double.NaN));pos.add(DoubleTag.valueOf(64));pos.add(DoubleTag.valueOf(32));n.put("Pos",pos);}),
            op->op.cuentaDespues.get(0).getAsJsonObject().addProperty("esclavo",UUID.randomUUID().toString()));
        for(var change:invalidos) {Operacion op=copia(ok);change.accept(op);rechazar(()->RitualDurable.validar(op),"WAL corrupto");}
        rechazar(()->RitualDurable.decodificar(Base64.getEncoder().encodeToString(new byte[]{1,2,3,4})),"NBT comprimido corrupto");
        RitualDurable.verificarResultado(chunk(ok),ok);checks++;
        rechazar(()->RitualDurable.verificarResultado(new CompoundTag(),ok),"ACK sin resultado");
        var duplicado=chunk(ok);duplicado.getListOrEmpty("Entities").add(RitualDurable.decodificar(ok.snapshot));
        rechazar(()->RitualDurable.verificarResultado(duplicado,ok),"ACK con UUID duplicado");
        for(String key:List.of("UUID","id","Dueno","Vinculo","TheSlaveRitualOperacion","TheSlaveRitualCustodia","TheSlaveRitualOriginal","Pos")) {
            var malo=chunk(ok);malo.getListOrEmpty("Entities").getCompoundOrEmpty(0).remove(key);
            rechazar(()->RitualDurable.verificarResultado(malo,ok),"ACK sin "+key);
        }
        for(String key:List.of("Mochila","Ref","equipment","EnergiaFundicion","Variante","Variante2","custom_name")) {
            var malo=chunk(ok);malo.getListOrEmpty("Entities").getCompoundOrEmpty(0).remove(key);
            rechazar(()->RitualDurable.verificarResultado(malo,ok),"ACK con perdida de "+key);
        }
        var incompleto=chunk(ok);incompleto.getListOrEmpty("Entities").getCompoundOrEmpty(0).getListOrEmpty("Mochila").getCompoundOrEmpty(0).putInt("count",1);
        rechazar(()->RitualDurable.verificarResultado(incompleto,ok),"ACK con objetos perdidos");
        var anterior=copia(ok);anterior.id=UUID.randomUUID();
        rechazar(()->RitualDurable.validarPendientes(List.of(ok,anterior)),"dos PREP del mismo jugador");
        anterior.estado=Estado.CONFIRMADA;RitualDurable.validarPendientes(List.of(ok,anterior));checks++;
        anterior.estado=Estado.CANCELADA;RitualDurable.validarPendientes(List.of(ok,anterior));checks++;
    }
    private static final class Puertos implements Participantes {
        Operacion op;String fallo;boolean falloUsado;Estado disco=Estado.PREPARADA;
        CompletableFuture<Void> carga=CompletableFuture.completedFuture(null);
        CompletableFuture<CompoundTag> escritura;
        final List<String> orden=new ArrayList<>();final Set<UUID> registro,jugador,cuerpos;
        java.util.function.BooleanSupplier actual=()->true;
        Consumer<String> notificar=nombre->{};
        int cobros,adopciones,creaciones;boolean liberado,sesion=true;
        Puertos(Operacion op) {this(op,null);}
        Puertos(Operacion op,Puertos anterior) {
            this.op=op;registro=anterior==null?new HashSet<>():anterior.registro;
            jugador=anterior==null?new HashSet<>():anterior.jugador;cuerpos=anterior==null?new HashSet<>():anterior.cuerpos;
            if(anterior!=null)disco=anterior.disco;
        }
        void frontera(String nombre) {orden.add(nombre);notificar.accept(nombre);if(nombre.equals(fallo)&&!falloUsado){falloUsado=true;throw new IllegalStateException("Caida: "+nombre);}}
        public void comprobarSesion() {if(!sesion||!actual.getAsBoolean())throw new IllegalStateException("Sesion o intento cerrado");}
        public CompletableFuture<Void> cargarRestaurar() {
            frontera("cargar");return carga.thenRun(()->{comprobarSesion();if(cuerpos.add(op.resultado))creaciones++;frontera("restaurar");});
        }
        public void aplicarRegistro() {if(registro.add(op.id))adopciones++;frontera("registro");}
        public void aplicarJugador() {if(jugador.add(op.id))cobros++;frontera("jugador");}
        public void guardarJugador() {frontera("ackJugador");}
        public CompletableFuture<CompoundTag> guardarEntidad() {frontera("guardarEntidad");return escritura==null?CompletableFuture.completedFuture(chunk(op)):escritura;}
        public void persistirEstado(Estado estado) {
            check(op.estado==Estado.PREPARADA,"Estado COMMIT expuesto antes de escribir WAL");frontera("antesCommit");disco=estado;frontera("despuesCommit");
        }
        public void liberar() {check(op.estado==Estado.CONFIRMADA&&disco==Estado.CONFIRMADA,"Liberacion sin COMMIT durable");frontera("antesLiberar");liberado=true;frontera("despuesLiberar");}
    }
    private static void motor() {
        for(String punto:List.of("cargar","restaurar","registro","jugador","ackJugador","guardarEntidad","antesCommit","despuesCommit","antesLiberar","despuesLiberar")) {
            var op=operacion();var p=new Puertos(op);p.fallo=punto;
            rechazar(()->RitualDurable.confirmar(p.op,p).join(),"caida en "+punto);
            check(!p.liberado||p.disco==Estado.CONFIRMADA,"Caida libera un PREP");
            check(op.estado==(punto.equals("antesLiberar")||punto.equals("despuesLiberar")?Estado.CONFIRMADA:Estado.PREPARADA),"Estado de memoria tras caida "+punto);
            // Recarga del estado que SI alcanzo disco; los recibos de los participantes sobreviven al reintento.
            op=copia(op);op.estado=p.disco;p.op=op;
            RitualDurable.confirmar(op,p).join();check(p.liberado&&op.estado==Estado.CONFIRMADA,"Recuperacion incompleta en "+punto);
            int previos=p.orden.size();RitualDurable.confirmar(op,p).join();
            check(p.orden.subList(previos,p.orden.size()).equals(List.of("antesLiberar","despuesLiberar")),"Doble recuperacion repite pagos o escrituras");
            check(p.cobros==1&&p.adopciones==1&&p.creaciones==1,"Reintento duplica cobro/Registro/cuerpo en "+punto);
        }
        var op=operacion();var p=new Puertos(op);p.carga=new CompletableFuture<>();p.escritura=new CompletableFuture<>();
        var futuro=RitualDurable.confirmar(op,p);
        check(!futuro.isDone()&&p.orden.equals(List.of("cargar")),"Mutacion antes de cargar entidades");p.carga.complete(null);
        check(!futuro.isDone()&&op.estado==Estado.PREPARADA&&!p.liberado,"COMMIT anterior al ACK de entidades");
        check(p.orden.equals(List.of("cargar","restaurar","registro","jugador","ackJugador","guardarEntidad")),"Orden de participantes distinto al protocolo");
        p.escritura.complete(new CompoundTag());rechazar(futuro::join,"ACK falso");check(op.estado==Estado.PREPARADA&&!p.liberado,"ACK falso libera resultado");
        var cancelada=operacion();cancelada.estado=Estado.CANCELADA;var pc=new Puertos(cancelada);
        rechazar(()->RitualDurable.confirmar(cancelada,pc).join(),"confirmacion CANCELADA");check(pc.orden.isEmpty(),"CANCELADA modifica participantes");
        var vencida=operacion();var pv=new Puertos(vencida);pv.escritura=new CompletableFuture<>();var pendiente=RitualDurable.confirmar(vencida,pv);
        pv.sesion=false;pv.escritura.complete(chunk(vencida));rechazar(pendiente::join,"ACK de otra sesion");check(vencida.estado==Estado.PREPARADA&&!pv.liberado,"ACK de sesion cerrada libera custodia");
        for(boolean antesDeCargar:List.of(true,false)) {
            var vigente=operacion();int[] token={1};Object viejo=new Object(),nuevo=new Object();Object[] online={viejo};
            var antiguo=new Puertos(vigente);antiguo.actual=()->RitualDurable.intentoVigente(1,1,1,(long)token[0],viejo,online[0]);
            if(antesDeCargar)antiguo.carga=new CompletableFuture<>();else antiguo.escritura=new CompletableFuture<>();
            var obsoleto=RitualDurable.confirmar(vigente,antiguo);token[0]=2;online[0]=nuevo;
            var reciente=new Puertos(vigente,antiguo);reciente.actual=()->RitualDurable.intentoVigente(1,1,2,(long)token[0],nuevo,online[0]);
            RitualDurable.confirmar(vigente,reciente).join();
            int pasos=antiguo.orden.size();
            if(antesDeCargar)antiguo.carga.complete(null);else antiguo.escritura.complete(chunk(vigente));
            rechazar(obsoleto::join,"callback de un jugador anterior");
            check(antiguo.orden.size()==pasos&&!antiguo.liberado,"Callback viejo modifica participantes o libera el nuevo intento");
            check(vigente.estado==Estado.CONFIRMADA&&reciente.liberado,"Reconexion no completa el nuevo intento");
            check(antiguo.cobros+reciente.cobros==1&&antiguo.adopciones+reciente.adopciones==1&&antiguo.creaciones+reciente.creaciones==1,"Reconexion duplica pago o entidad");
        }
        for(String frontera:List.of("restaurar","registro","jugador","ackJugador","despuesCommit")) {
            var vigente=operacion();int[] token={1};var antiguo=new Puertos(vigente);antiguo.actual=()->token[0]==1;
            antiguo.notificar=nombre->{if(nombre.equals(frontera))token[0]=2;};
            rechazar(()->RitualDurable.confirmar(vigente,antiguo).join(),"jugador sustituido en "+frontera);
            check(!antiguo.liberado,"Un intento obsoleto libera al jugador nuevo");
            var reciente=new Puertos(vigente,antiguo);reciente.actual=()->token[0]==2;
            RitualDurable.confirmar(vigente,reciente).join();
            check(reciente.liberado&&antiguo.cobros+reciente.cobros==1&&antiguo.adopciones+reciente.adopciones==1&&antiguo.creaciones+reciente.creaciones==1,"Reintento de fase duplica resultados");
        }
    }
    private static void filtros() {
        UUID nacimiento=UUID.randomUUID(),transferencia=UUID.randomUUID();
        check(RitualDurable.custodiaPropia(nacimiento,nacimiento),"COMMIT no libera su propia custodia");
        check(!RitualDurable.custodiaPropia(nacimiento,transferencia),"Nacimiento COMMIT libera transferencia posterior PREP");
        check(!RitualDurable.custodiaPropia(nacimiento,null),"COMMIT historico libera una entidad sin su marcador");
        check(com.theslave.ritual.MuerteDurable.custodiaAjena(true,nacimiento,transferencia),"Retiro historico reescribe cuerpo de transferencia PREP");
        check(!com.theslave.ritual.MuerteDurable.custodiaAjena(true,nacimiento,nacimiento),"Se bloquea limpieza de la propia custodia");
        check(!com.theslave.ritual.MuerteDurable.custodiaAjena(false,nacimiento,transferencia),"Se bloquea reparacion de copia legacy fuera de custodia");
        check(!com.theslave.ritual.MuerteDurable.custodiaAjena(false,nacimiento,null),"Se bloquea reparacion de copia legacy sin marcador");
        Object player=new String("mismoUUID"),reconexion=new String("mismoUUID");
        check(RitualDurable.intentoVigente(1,1,1,1L,player,player),"Intento actual rechazado");
        check(!RitualDurable.intentoVigente(1,1,1,1L,player,reconexion),"Igual UUID permite usar ServerPlayer anterior");
        check(!RitualDurable.intentoVigente(1,2,1,1L,player,player),"Epoca anterior admitida");
        check(!RitualDurable.intentoVigente(1,1,1,2L,player,player),"Token anterior admitido");
        check(!RitualDurable.intentoVigente(1,1,1,null,player,player),"Intento sin cerrojo admitido");
        check(!RitualDurable.intentoVigente(1,1,1,1L,player,null),"Jugador desconectado admitido");
        check(RitualDurable.decidirCarga(Estado.PREPARADA,true)==DecisionCarga.RETENER_ORIGINAL,"Padre PREP libre");
        check(RitualDurable.decidirCarga(Estado.PREPARADA,false)==DecisionCarga.RETENER_RESULTADO,"Resultado PREP libre");
        check(RitualDurable.decidirCarga(Estado.CONFIRMADA,true)==DecisionCarga.DESCARTAR,"Padre antiguo reaparece");
        check(RitualDurable.decidirCarga(Estado.CONFIRMADA,false)==DecisionCarga.LIBERAR,"Resultado COMMIT congelado");
        check(RitualDurable.decidirCarga(Estado.CANCELADA,true)==DecisionCarga.NINGUNA&&RitualDurable.decidirCarga(Estado.CANCELADA,false)==DecisionCarga.NINGUNA,"CANCELADA filtra entidades ajenas");
        var op=operacion();op.tumba=BlockPos.ZERO.asLong();op.difunto=UUID.randomUUID();op.muertes=2;
        check(!RitualDurable.coincideTumba(op,op.dueno,op.difunto,2),"PREP retira tumba");op.estado=Estado.CONFIRMADA;
        check(RitualDurable.coincideTumba(op,op.dueno,op.difunto,2),"Tumba de esta generacion no reconocida");
        check(!RitualDurable.coincideTumba(op,op.dueno,op.difunto,3),"COMMIT antiguo borra tumba nueva");
        check(!RitualDurable.coincideTumba(op,UUID.randomUUID(),op.difunto,2),"Se retira tumba ajena");
        check(!RitualDurable.coincideTumba(op,op.dueno,UUID.randomUUID(),2),"Se retira otro difunto");
    }
    public static void main(String[] args) {validacion();motor();filtros();System.out.println("RitualDurable: "+checks+" comprobaciones reales de WAL, ACK, recuperacion y generacion correctas.");}
}
