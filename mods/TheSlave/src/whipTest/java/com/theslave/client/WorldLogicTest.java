package com.theslave.client;

import com.theslave.worldgen.Enclaves;
import net.minecraft.core.BlockPos;
import java.util.List;

/** Pruebas puras del indice de destinos: no inicia Minecraft ni crea mundos. */
public final class WorldLogicTest {
 private static Enclaves.Hallado sitio(String dimension,int x,int z,String tipo) {
  var h=new Enclaves.Hallado();h.dim=dimension;h.cx=x;h.cz=z;h.y=25;h.tipo=tipo;
  Enclaves.confirmarVerificacion(h,true);return h;
 }
 public static void run() {
  var cerca=sitio("minecraft:overworld",1,1,"CUEVA");
  var lejos=sitio("minecraft:overworld",100,100,"CASA");
  var otra=sitio("minecraft:the_nether",0,0,"REFUGIO");
  var invalido=sitio("minecraft:overworld",0,0,"desconocido");
  if(Enclaves.elegirHallado(List.of(lejos,otra,invalido,cerca),"minecraft:overworld",BlockPos.ZERO)!=cerca)
   throw new AssertionError("La brujula no elige la entrada real mas cercana de su dimension");
  if(Enclaves.elegirHallado(List.of(cerca,lejos),"minecraft:the_nether",BlockPos.ZERO)!=null)
   throw new AssertionError("La brujula apunta a un enclave de otra dimension");
  if(Enclaves.elegirHallado(List.of(),"minecraft:overworld",BlockPos.ZERO)!=null)
   throw new AssertionError("La brujula inventa un destino sin enclaves verificados");
  if(Enclaves.elegirHallado(List.of(cerca,lejos),"minecraft:overworld",new BlockPos(1600,0,1600))!=lejos)
   throw new AssertionError("La seleccion de destino no se actualiza al desplazarse");
  var pendiente=new Enclaves.Hallado();pendiente.dim="minecraft:overworld";pendiente.cx=0;pendiente.cz=0;pendiente.tipo="CUEVA";
  if(Enclaves.elegirHallado(List.of(pendiente),pendiente.dim,BlockPos.ZERO)!=null)
   throw new AssertionError("Un indice sin chunk verificado se convierte en destino");
  if(Enclaves.elegirPendiente(List.of(cerca,pendiente),pendiente.dim,BlockPos.ZERO)!=pendiente)
   throw new AssertionError("No se verifica primero el rastro anterior mas cercano");
  Enclaves.confirmarVerificacion(pendiente,false);
  if(pendiente.verificacion!=Enclaves.Verificacion.HUERFANO || Enclaves.elegirPendiente(List.of(pendiente),pendiente.dim,BlockPos.ZERO)!=null)
   throw new AssertionError("Se reutiliza un rastro huerfano");
  Enclaves.confirmarVerificacion(pendiente,true);
  if(Enclaves.elegirHallado(List.of(pendiente),pendiente.dim,BlockPos.ZERO)!=pendiente)
   throw new AssertionError("La comprobacion fisica no habilita el destino");
  var gson=new com.google.gson.Gson();String indice=gson.toJson(pendiente);
  if(indice.contains("verificacion") || gson.fromJson(indice,Enclaves.Hallado.class).verificacion!=Enclaves.Verificacion.PENDIENTE)
   throw new AssertionError("Una sesion nueva confia en un booleano del indice en disco");
  if(!Enclaves.coincideDestino(pendiente,pendiente.dim,new BlockPos(2,pendiente.y,7))
      || Enclaves.coincideDestino(pendiente,"minecraft:the_nether",new BlockPos(2,pendiente.y,7))
      || Enclaves.coincideDestino(pendiente,pendiente.dim,new BlockPos(2,pendiente.y+1,7)))throw new AssertionError("El tracker confia en otra dimension/altura");
  for(int rx:new int[]{-3,0,2}) {
   var muestras=Enclaves.muestrasCasa(rx,-2,140);
   if(muestras.size()!=64 || new java.util.HashSet<>(muestras).size()!=64)throw new AssertionError("Busqueda de bioma no acotada/duplicada");
   for(var p:muestras)if(Math.floorDiv(p.getX()>>4,140)!=rx || Math.floorDiv(p.getZ()>>4,140)!=-2 || p.getY()!=64)throw new AssertionError("Muestreo forestal sale de su region");
  }
  var cupo=new Enclaves.CupoBusqueda(-2,4);
  if(!cupo.mismaZona(-2,4) || cupo.mismaZona(-1,4))throw new AssertionError("Zona de rastreo incorrecta");
  if(cupo.reservar(0)!=0 || cupo.reservar(1000)!=-1)throw new AssertionError("Se permiten rastreos simultaneos");
  cupo.terminar();
  if(cupo.reservar(199)!=-1 || cupo.reservar(200)!=1)throw new AssertionError("No se respeta el enfriamiento");
  cupo.terminar();
  for(int i=2;i<9;i++) {
   if(cupo.reservar(i*200)!=i)throw new AssertionError("Candidatos repetidos u omitidos");
   cupo.terminar();
  }
  if(!cupo.agotada() || cupo.reservar(2000)!=-1)throw new AssertionError("El rastreo genera regiones sin limite");
  System.out.println("Enclaves: destino verificado, dimension, movimiento, cupo, concurrencia y enfriamiento correctos.");
 }
 public static void main(String[] args) { run(); }
}
