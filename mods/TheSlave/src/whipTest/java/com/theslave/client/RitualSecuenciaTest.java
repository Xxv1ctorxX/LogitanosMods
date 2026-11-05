package com.theslave.client;

import com.theslave.ritual.SecuenciaRitual;

/** Prueba de los guiones sin abrir Minecraft ni crear un mundo. */
public final class RitualSecuenciaTest {
 public static void main(String[] args) {
  int[] duraciones={240,400,360,480};
  int tests=0;
  java.util.Set<Integer> entradas=new java.util.HashSet<>();
  for(int tipo=0;tipo<4;tipo++) {
   var s=SecuenciaRitual.para(tipo);
   if(s.duracion()!=duraciones[tipo] || !(0<s.abre()&&s.abre()<s.entra()&&s.entra()<s.llega()&&s.llega()<s.sale()&&s.sale()<s.vuelve()&&s.vuelve()<s.duracion()))
    throw new AssertionError("Orden del guion "+tipo);
   entradas.add(s.entra());
   for(int tick=-1;tick<=s.duracion()+1;tick++) {
    int abierto=s.apertura(tick);
    if(abierto<0||abierto>100)throw new AssertionError("Apertura fuera de rango");
    if(tick<s.abre()||tick>=s.duracion()) {if(abierto!=0)throw new AssertionError("Portal fuera de su vida");}
    if(tick>=s.entra()&&tick<=s.vuelve()) {if(abierto!=100)throw new AssertionError("La Muerte cruza un portal cerrado");}
    int presencia=s.presencia(tick);
    if(presencia<0||presencia>100)throw new AssertionError("Presencia fuera de rango");
    if(tick<=s.entra()||tick>=s.vuelve()) {if(presencia!=0)throw new AssertionError("Actor visible fuera de su vida");}
    if(tick>=s.entra()+14&&tick<=s.vuelve()-12) {if(presencia!=100)throw new AssertionError("Actor no materializado durante el ritual");}
    if(presencia>0&&abierto!=100)throw new AssertionError("Actor visible antes de abrirse el portal");
    tests+=2;
   }
  }
  if(entradas.size()!=4)throw new AssertionError("Cuatro rituales con una sola entrada");
  System.out.println("Rituales: "+tests+" comprobaciones de guion correctas.");
  RitualGeometryTest.run();
  if(!com.theslave.ritual.PrioridadEscena.preferir(false,25,10,false,25,20))throw new AssertionError("Empate de observadores inestable");
  if(com.theslave.ritual.PrioridadEscena.preferir(false,25,20,false,25,10))throw new AssertionError("El empate depende del orden de iteracion");
  if(!com.theslave.ritual.PrioridadEscena.preferir(true,200,20,false,1,10))throw new AssertionError("La escena propia pierde prioridad");
  if(com.theslave.ritual.PrioridadEscena.preferir(false,1,10,true,200,20))throw new AssertionError("La escena de otro reemplaza la propia");
  if(com.theslave.ritual.PrioridadEscena.preferir(true,577,1,false,1000,2))throw new AssertionError("HUD fuera de alcance");
 }
}
