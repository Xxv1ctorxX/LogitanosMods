package com.theslave.client;

import com.theslave.Config;
import com.theslave.ai.BarridoMenas;
import com.theslave.ai.PresupuestoEscaneo;
import com.theslave.ai.ExtraccionSegura;
import java.util.*;

/** Verifica los mismos cursores y barreras usados por la entidad, sin arrancar Minecraft. */
public final class TrabajoSeguroTest {
    private static int checks;
    private static void require(boolean ok,String text) {checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args) {
        for(int radio:new int[]{1,2,8,24,32}) {
            BarridoMenas cursor=new BarridoMenas(radio);Set<Long> vistos=new HashSet<>();int anterior=0;
            while(cursor.siguiente()) {
                int r=Math.max(Math.abs(cursor.x()),Math.max(Math.abs(cursor.y()),Math.abs(cursor.z())));
                require(r>=anterior && r<=radio,"Cursor salta radio o retrocede");anterior=r;
                long clave=((long)(cursor.x()+32)*65+cursor.y()+32)*65+cursor.z()+32;
                require(vistos.add(clave),"Cursor visita un bloque dos veces");
            }
            require(vistos.size()==(2*radio+1)*(2*radio+1)*(2*radio+1),"Cursor deja huecos");
            require(!cursor.siguiente(),"Cursor agotado vuelve a empezar");
        }
        for(int total:new int[]{1,9,100}) {
            PresupuestoEscaneo cola=new PresupuestoEscaneo();UUID[] ids=new UUID[total];int[] atendidos=new int[total];
            Arrays.setAll(ids,i->new UUID(0,i));
            for(int t=0;t<200;t++) {
                int suma=0;
                for(int i=0;i<total;i++) {
                    int n=cola.tomar(t,ids[i],1024,128);suma+=n;if(n>0)atendidos[i]++;
                    require(n<=128,"Trabajador excede presupuesto");
                    require(cola.tomar(t,ids[i],1024,128)==0,"Replay del tick obtiene mas presupuesto");
                }
                require(suma<=1024,"Presupuesto global se multiplica por entidades");
            }
            int min=Arrays.stream(atendidos).min().orElseThrow(),max=Arrays.stream(atendidos).max().orElseThrow();
            require(min>0 && max-min<=1,"Turnos no equitativos");
            cola.tomar(300,new UUID(1,1),1024,128);
            require(cola.registrados()==1,"IDs inactivos quedan retenidos");
        }
        List<String> orden=new ArrayList<>();
        require(!ExtraccionSegura.<String>ejecutar(()->false,()->{throw new AssertionError("Loot sin permiso");},()->true,orden::add),"Permiso denegado ignorado");
        require(!ExtraccionSegura.ejecutar(()->true,()->List.of("diamante"),()->false,orden::add),"Rotura fallida ignorada");
        require(orden.isEmpty(),"Botin duplicable tras fallo");
        require(ExtraccionSegura.ejecutar(()->{orden.add("permiso");return true;},()->{orden.add("calculo");return List.of("botin");},()->{orden.add("rotura");return true;},orden::add),"Rotura valida falla");
        require(orden.equals(List.of("permiso","calculo","rotura","botin")),"Botin se entrega antes de romper");
        Config c=new Config();c.radioMenas=Integer.MAX_VALUE;c.escaneoGlobalPorTick=Integer.MAX_VALUE;c.escaneoPorEsclavo=-1;c.maxPorJugador=100;c.validar();
        require(c.radioMenas==32 && c.escaneoGlobalPorTick==4096 && c.escaneoPorEsclavo==16 && c.maxPorJugador==3,"Config permite saltar limites");
        System.out.println("TrabajoSeguro: "+checks+" comprobaciones correctas");
    }
}
