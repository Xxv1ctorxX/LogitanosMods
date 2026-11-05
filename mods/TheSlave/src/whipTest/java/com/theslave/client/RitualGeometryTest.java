package com.theslave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.theslave.ritual.CoreografiaFusion;
import com.theslave.ritual.AparicionRitual;
import com.theslave.ritual.GestosMuerte;
import java.util.List;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;

/** Geometria real del humanoide vanilla y su UV; no crea juego, mundo ni ventana. */
public final class RitualGeometryTest {
    private static int checks;
    private static void check(boolean b,String mensaje) {checks++;if(!b)throw new AssertionError(mensaje);}
    private static float area(List<float[]> vertices) {
        float suma=0;
        for(int i=0;i<vertices.size();i+=4) {
            float[] a=vertices.get(i),b=vertices.get(i+1),c=vertices.get(i+2),d=vertices.get(i+3);
            suma+=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(new Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2])).length()/2;
            suma+=new Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2]).cross(new Vector3f(d[0]-a[0],d[1]-a[1],d[2]-a[2])).length()/2;
        }
        return suma;
    }
    private static MallaRecortada cuadrado(MallaRecortada.Plano plano) {
        var mesh=new MallaRecortada(plano);
        for(float[] p:new float[][]{{-1,0},{1,0},{1,1},{-1,1}})
            mesh.addVertex(p[0],p[1],0).setUv((p[0]+1)/2,p[1]).setColor(255,100,20,255).setNormal(0,0,1).setUv1(3,5).setUv2(240,160);
        return mesh.terminar();
    }
    public static void run() {
        check(cuadrado(new MallaRecortada.Plano(1,0,0,2)).vertices().size()==4,"Un quad intacto duplica vertices innecesariamente");
        var tubo=new MallaRecortada();
        RitualMesh.beam(new PoseStack().last(),tubo,new Vector3f(0,0,0),new Vector3f(0,0,1),.1f,-1,0);tubo.terminar();
        check(tubo.vertices().size()==16,"El tubo incluye caras interiores redundantes");
        var caras=tubo.vertices();
        for(int i=0;i<caras.size();i+=4) {
            float x=0,y=0;for(int j=0;j<4;j++){x+=caras.get(i+j)[0];y+=caras.get(i+j)[1];}
            check(x*caras.get(i)[13]+y*caras.get(i)[14]>0,"Cara de tubo invertida: desaparece desde fuera");
        }
        for(int i=-20;i<=20;i++) {
            float borde=i/20f;var mesh=cuadrado(new MallaRecortada.Plano(1,0,0,-borde));
            check(Math.abs(area(mesh.vertices())-(1-borde))<1e-5,"Area del cuadrado recortado");
            for(float[] v:mesh.vertices()) {
                check(v[0]>=borde-1e-6,"Vertice detras del plano");
                check(Math.abs(v[3]-(v[0]+1)/2)<1e-6,"UV interpolada en la arista");
                check(v[9]==3&&v[10]==5&&v[11]==240&&v[12]==160,"Luz y overlay conservados");
            }
        }
        var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot();
        var pose=new PoseStack();pose.scale(-1,-1,1);pose.translate(0,-1.501f,0);
        var entera=new MallaRecortada();root.render(pose,entera,0x00f000f0,0,-1);entera.terminar();
        var izq=new MallaRecortada(new MallaRecortada.Plano(-1,0,0,0));root.render(pose,izq,0x00f000f0,0,-1);izq.terminar();
        var der=new MallaRecortada(new MallaRecortada.Plano(1,0,0,0));root.render(pose,der,0x00f000f0,0,-1);der.terminar();
        check(!izq.vertices().isEmpty()&&!der.vertices().isEmpty(),"Mitades reales del humanoide");
        check(Math.abs(area(entera.vertices())-area(izq.vertices())-area(der.vertices()))<0.0001,"Las mitades cubren la superficie original sin duplicar area");
        check(izq.vertices().stream().allMatch(v->v[0]<=1e-6),"Mitad izquierda no conserva geometria derecha");
        check(der.vertices().stream().allMatch(v->v[0]>=-1e-6),"Mitad derecha no conserva geometria izquierda");
        check(izq.vertices().stream().anyMatch(v->v[3]>32/64f&&v[3]<48/64f&&v[4]>48/64f),"Izquierda conserva UV del brazo izquierdo");
        check(izq.vertices().stream().noneMatch(v->v[3]>40/64f&&v[4]>20/64f&&v[4]<32/64f),"Izquierda descarta UV del brazo derecho");
        for(int orientacion=0;orientacion<4;orientacion++)for(int paso=-10;paso<=10;paso++) {
            var local=new PoseStack();local.rotateDegrees(com.mojang.math.Axis.YP,orientacion*90);
            float x=orientacion==1?1:orientacion==3?-1:0,z=orientacion==0?1:orientacion==2?-1:0,d=paso/10f;
            var mesh=new MallaRecortada(new MallaRecortada.Plano(x,0,z,d));root.render(local,mesh,0,0,-1);mesh.terminar();
            for(float[] v:mesh.vertices())check(x*v[0]+z*v[2]+d>=-1e-5,"Portal: vertice humanoide detras del plano");
        }
        float resto=1,convergencia=0,costura=0,descenso=0;
        for(int t=0;t<360;t++) {
            var f=CoreografiaFusion.en(t);
            check(f.resto()<=resto&&f.convergencia()>=convergencia&&f.costura()>=costura&&f.descenso()>=descenso,"Coreografia monotona");
            check(f.resto()>=0&&f.resto()<=1&&f.convergencia()>=0&&f.convergencia()<=1&&f.costura()>=0&&f.costura()<=1,"Progreso acotado");
            if(f.convergencia()>0)check(f.resto()==0,"Converger solo tras descartar la otra mitad");
            if(f.descenso()>0)check(f.convergencia()==1,"Descender solo tras unirse");
            resto=f.resto();convergencia=f.convergencia();costura=f.costura();descenso=f.descenso();
        }
        var fin=CoreografiaFusion.en(358);
        check(fin.resto()==0&&fin.convergencia()==1&&fin.costura()==1&&fin.descenso()==1,"El cuerpo llega unido al suelo antes del cobro");
        for(int tipo=0;tipo<4;tipo++) {
            int duracion=com.theslave.ritual.SecuenciaRitual.para(tipo).duracion();float altura=0,color=0;
            for(int tick=0;tick<=duracion;tick++) {
                var aparicion=AparicionRitual.en(tipo,tick);var gesto=GestosMuerte.en(tipo,tick);
                check(aparicion.altura()>=altura&&aparicion.color()>=color,"Reconstruccion corporal o color retrocede");
                check(aparicion.altura()>=0&&aparicion.altura()<=1&&aparicion.color()>=0&&aparicion.color()<=1,"Aparicion fuera de rango");
                check(aparicion.arrodillado()>=0&&aparicion.arrodillado()<=1&&aparicion.almas()>=0&&aparicion.almas()<=1&&aparicion.objetos()>=0&&aparicion.objetos()<=1,"Efecto fuera de rango");
                check(Float.isFinite(gesto.cabeza())&&Math.abs(gesto.cabeza())<1&&Float.isFinite(gesto.derechaX())&&Math.abs(gesto.derechaX())<3&&Float.isFinite(gesto.izquierdaX())&&Math.abs(gesto.izquierdaX())<3,"Gesto no finito o articulacion excesiva");
                if(tipo==0&&tick<160)check(aparicion.altura()==0,"Cuerpo antes del segundo ocho");
                if(tipo==1&&tick<320)check(aparicion.altura()==0,"Retorno normal antes de su ultima fase");
                if(tipo!=2&&aparicion.altura()>0) {
                    var modelo=new MallaRecortada(new MallaRecortada.Plano(0,-1,0,2.1f*aparicion.altura()),new MallaRecortada.Plano(0,1,0,0));
                    root.render(pose,modelo,0,0,-1);modelo.terminar();
                    for(float[] v:modelo.vertices())check(v[1]>=-1e-5&&v[1]<=2.1f*aparicion.altura()+1e-5,"Reconstruccion emite cuerpo por encima del corte desde pies");
                }
                altura=aparicion.altura();color=aparicion.color();
            }
            if(tipo!=2)check(altura==1&&color==1,"Aspecto incompleto al terminar la ceremonia");
        }
        System.out.println("Geometria ritual: "+checks+" comprobaciones de recorte, UV y fusion correctas.");
    }
}
