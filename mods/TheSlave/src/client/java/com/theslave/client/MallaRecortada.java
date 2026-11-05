package com.theslave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Recorte geometrico de quads: interpola UV, luz y normales en cada arista nueva. */
public final class MallaRecortada implements VertexConsumer {
    public record Plano(float x, float y, float z, float d) {
        float distancia(float[] v) { return x*v[0]+y*v[1]+z*v[2]+d; }
    }
    private final List<Plano> planos;
    private final List<float[]> quad = new ArrayList<>(4);
    private final List<float[]> vertices = new ArrayList<>();
    private float[] actual;

    public MallaRecortada(Plano... planos) { this.planos=List.of(planos); }

    @Override public VertexConsumer addVertex(float x,float y,float z) {
        cerrarVertice();
        actual=new float[]{x,y,z,0,0,255,255,255,255,0,0,0,0,0,1,0};
        return this;
    }
    private void cerrarVertice() {
        if(actual==null)return;
        quad.add(actual); actual=null;
        if(quad.size()!=4)return;
        List<float[]> poligono=quad;
        for(Plano plano:planos) poligono=recortar(poligono,plano);
        if(poligono.size()==4)vertices.addAll(poligono);
        else for(int i=1;i+1<poligono.size();i++) {
            vertices.add(poligono.get(0)); vertices.add(poligono.get(i));
            vertices.add(poligono.get(i+1)); vertices.add(poligono.get(i+1));
        }
        quad.clear();
    }
    static List<float[]> recortar(List<float[]> entrada,Plano plano) {
        if(entrada.isEmpty())return entrada;
        int dentro=0;for(float[] vertice:entrada)if(plano.distancia(vertice)>=0)dentro++;
        if(dentro==entrada.size())return entrada;
        if(dentro==0)return List.of();
        List<float[]> salida=new ArrayList<>(entrada.size()+1);
        float[] anterior=entrada.getLast(); float da=plano.distancia(anterior);
        for(float[] siguiente:entrada) {
            float ds=plano.distancia(siguiente);
            if((da>=0)!=(ds>=0)) {
                float t=da/(da-ds); float[] borde=new float[anterior.length];
                for(int k=0;k<borde.length;k++)borde[k]=anterior[k]+t*(siguiente[k]-anterior[k]);
                salida.add(borde);
            }
            if(ds>=0)salida.add(siguiente);
            anterior=siguiente; da=ds;
        }
        return salida;
    }
    public MallaRecortada terminar() {
        cerrarVertice();
        if(!quad.isEmpty())throw new IllegalStateException("Malla de modelo no formada por quads");
        return this;
    }
    public List<float[]> vertices() { return vertices.stream().map(float[]::clone).toList(); }
    public void dibujar(PoseStack.Pose pose,VertexConsumer out) {
        for(float[] v:vertices) {
            out.addVertex(pose,v[0],v[1],v[2]).setColor(Math.round(v[5]),Math.round(v[6]),Math.round(v[7]),Math.round(v[8]))
                .setUv(v[3],v[4]).setUv1(Math.round(v[9]),Math.round(v[10])).setUv2(Math.round(v[11]),Math.round(v[12]))
                .setNormal(pose,v[13],v[14],v[15]);
        }
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a) { actual[5]=r;actual[6]=g;actual[7]=b;actual[8]=a;return this; }
    @Override public VertexConsumer setColor(int argb) { return setColor(argb>>16&255,argb>>8&255,argb&255,argb>>>24); }
    @Override public VertexConsumer setUv(float u,float v) {actual[3]=u;actual[4]=v;return this;}
    @Override public VertexConsumer setUv1(int u,int v) {actual[9]=u;actual[10]=v;return this;}
    @Override public VertexConsumer setUv2(int u,int v) {actual[11]=u;actual[12]=v;return this;}
    @Override public VertexConsumer setUv3(float u,float v) {return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z) {actual[13]=x;actual[14]=y;actual[15]=z;return this;}
    @Override public VertexConsumer setLineWidth(float width) {return this;}
}
