package com.theslave.client;

import com.theslave.Aptitudes;
import com.theslave.comercio.Comercio;
import com.theslave.registry.ModComponents.Almas;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.io.*;
import net.minecraft.util.RandomSource;
import org.joml.Vector3fc;

/** Checks de economia, herencia, codecs de modelos y vertices de cadenas. */
public final class LoreTest {
 private static int checks;
 private static void require(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  int[] souls={2,8,14},iron={16,32,64},gold={4,8,16},diamonds={0,2,6};
  for(int i=0;i<3;i++){
   var p=Comercio.precioAdquisicion(i);
   require(p.almasPorTipo()==souls[i]&&p.hierro()==iron[i]&&p.oro()==gold[i]&&p.diamante()==diamonds[i]&&p.esmeralda()==0,"Precio incorrecto");
   require(p.almas().total()==souls[i]*3,"Total almas incorrecto");
  }
  require(Almas.CAPACIDAD>=42,"No cabe la tercera ofrenda");
  try { new Almas(-1,0,0);throw new AssertionError("Almas negativas admitidas"); } catch(IllegalArgumentException expected){}
  var random=RandomSource.create(7331);
  for(int i=0;i<1000;i++){
   float[] a=Aptitudes.generar(random),b=Aptitudes.generar(random),f=Aptitudes.fusion(a,b);
   require(java.util.Arrays.equals(f,Aptitudes.fusion(a,b)),"Herencia no determinista");
   for(float v:a)require(Float.isFinite(v)&&v>=-.151f&&v<=.201f,"Normal fuera de limites");
   for(float v:f)require(Float.isFinite(v)&&v>=-.301f&&v<=.601f,"Fusion fuera de limites");
  }
  try(var files=Files.walk(Path.of("src/main/resources/assets/theslave/models"))){
   for(Path path:files.filter(p->p.toString().endsWith(".json")).toList())
    try(var reader=Files.newBufferedReader(path)){require(net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(reader)!=null,"Modelo invalido "+path);}
  }
  final int[] vertices={0};
  VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,m,a)->{
   if(m.getName().equals("addVertex")){
    Vector3fc p=(Vector3fc)a[1];require(Float.isFinite(p.x())&&Float.isFinite(p.y())&&Float.isFinite(p.z()),"Vertice invalido");vertices[0]++;
   }return proxy;
  });
  RitualMesh.chain(new PoseStack().last(),out,new org.joml.Vector3f(0,0,0),new org.joml.Vector3f(2,2,1),15728880);
  require(vertices[0]>100&&vertices[0]%4==0,"Cadena sin caras");
  System.out.println("Lore: "+checks+" comprobaciones correctas.");
 }
}
