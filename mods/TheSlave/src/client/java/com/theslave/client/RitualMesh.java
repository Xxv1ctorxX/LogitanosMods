package com.theslave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/** Geometria de las manifestaciones del altar, independiente de particulas. */
public final class RitualMesh {
 public static final net.minecraft.resources.Identifier SUPERFICIE=com.theslave.TheSlaveMod.id("textures/entity/ritual_surface.png");
 private RitualMesh() { }
 public static void vertex(PoseStack.Pose pose, VertexConsumer out, Vector3f p, Vector3f n, int color, int light) {
  out.addVertex(pose, p).setColor(color).setUv(0.5f,0.5f).setOverlay(0).setLight(light).setNormal(pose,n);
 }
 public static void quad(PoseStack.Pose pose, VertexConsumer out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color, int light) {
  Vector3f n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
  if(n.lengthSquared()<1e-12f) return;
  n.normalize();
  vertex(pose,out,a,n,color,light); vertex(pose,out,b,n,color,light); vertex(pose,out,c,n,color,light); vertex(pose,out,d,n,color,light);
  n.negate();
  vertex(pose,out,d,n,color,light); vertex(pose,out,c,n,color,light); vertex(pose,out,b,n,color,light); vertex(pose,out,a,n,color,light);
 }
 private static void cara(PoseStack.Pose pose,VertexConsumer out,Vector3f a,Vector3f b,Vector3f c,Vector3f d,int color,int light) {
  Vector3f n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
  if(n.lengthSquared()<1e-12f)return;n.normalize();
  vertex(pose,out,a,n,color,light);vertex(pose,out,b,n,color,light);vertex(pose,out,c,n,color,light);vertex(pose,out,d,n,color,light);
 }
 public static void beam(PoseStack.Pose pose, VertexConsumer out, Vector3f a, Vector3f b, float radius, int color, int light) {
  Vector3f axis=new Vector3f(b).sub(a);
  if(axis.lengthSquared()<1e-10f)return;
  axis.normalize();
  Vector3f normal=axis.cross(Math.abs(axis.y)<0.9f?new Vector3f(0,1,0):new Vector3f(1,0,0),new Vector3f()).normalize().mul(radius);
  Vector3f bin=axis.cross(normal,new Vector3f());
  Vector3f[] p=new Vector3f[4],q=new Vector3f[4];
  for(int i=0;i<4;i++){
   float x=(i==0||i==3)?-1:1,y=i<2?-1:1;
   p[i]=new Vector3f(a).fma(x,normal).fma(y,bin); q[i]=new Vector3f(b).fma(x,normal).fma(y,bin);
  }
  for(int i=0;i<4;i++)cara(pose,out,p[i],p[(i+1)%4],q[(i+1)%4],q[i],color,light);
 }
 public static void chain(PoseStack.Pose pose, VertexConsumer out, Vector3f a, Vector3f b, int light){
  Vector3f axis=new Vector3f(b).sub(a);float length=axis.length();if(length<0.01)return;axis.normalize();
  Vector3f side=axis.cross(Math.abs(axis.y)<0.9?new Vector3f(0,1,0):new Vector3f(1,0,0),new Vector3f()).normalize();
  Vector3f other=axis.cross(side,new Vector3f());int count=Math.min(72,Math.max(2,(int)(length/0.09f)));
  for(int i=0;i<=count;i++){
   Vector3f center=new Vector3f(a).lerp(b,i/(float)count);Vector3f width=(i%2==0?side:other);
   for(int j=0;j<8;j++){
    float t=(float)(j*Math.PI/4),u=(float)((j+1)*Math.PI/4);
    Vector3f p=new Vector3f(center).fma((float)Math.cos(t)*0.037f,width).fma((float)Math.sin(t)*0.064f,axis);
    Vector3f q=new Vector3f(center).fma((float)Math.cos(u)*0.037f,width).fma((float)Math.sin(u)*0.064f,axis);
    beam(pose,out,p,q,0.009f,j%3==0?0xff493153:0xff3d3b43,light);
   }
  }
 }
}
