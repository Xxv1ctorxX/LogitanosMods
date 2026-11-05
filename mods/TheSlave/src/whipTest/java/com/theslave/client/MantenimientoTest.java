package com.theslave.client;

import com.theslave.Aptitudes;
import com.theslave.entity.Mantenimiento;

/** Ciclos del motor de produccion, con pausa/reanudacion y reconstruccion del estado persistido. */
public final class MantenimientoTest {
    private static int checks;
    private static void require(boolean ok,String text) { checks++;if(!ok)throw new AssertionError(text); }
    public static void run() {
        float[] normal=new float[5];
        var clock=new Mantenimiento();
        for(int i=0;i<1200;i++)require(clock.trabajar(20,1,normal),"Descansa antes de agotar presupuesto");
        require(!clock.trabajar(20,1,normal),"Produce sin resistencia");
        require(clock.pausado()&&clock.descanso()==6000,"Descanso base no son cinco minutos");
        for(int i=0;i<5999;i++)require(!clock.descansar(20),"Reanuda antes del cooldown");
        require(clock.descansar(20)&&clock.esfuerzo()==0,"No recupera automaticamente resistencia");
        require(clock.trabajar(20,1,normal),"No reanuda siguiente paso");
        require(!clock.trabajar(0,1,normal),"Trabaja sin comida");
        int esfuerzo=clock.esfuerzo();
        for(int i=0;i<100;i++)require(!clock.descansar(0),"Reanuda con hambre0");
        require(clock.descansar(5)&&clock.esfuerzo()==esfuerzo,"Comer borra cansancio acumulado");
        require(Mantenimiento.necesitaComer(15,5)&&!Mantenimiento.necesitaComer(20,5),"Consume comida innecesariamente");
        require(!Mantenimiento.necesitaComer(0,0),"Come objetos sin nutricion");
        float[] especialista={-.05f,-.05f,-.05f,-.05f,.20f};
        float[] fused=Aptitudes.fusion(especialista,especialista);
        require(Math.abs(fused[Aptitudes.RECUPERACION]-.6)<.0001,"Fusion misma especialidad no alcanza ventaja extraordinaria");
        require(Mantenimiento.duracionDescanso(fused)==2400,"Fusion no reduce cinco a dos minutos");
        float sum=0;for(float value:fused){sum+=value;require(value>=-.3001f&&value<=.6001f,"Fusion fuera de limites");}
        require(sum<=.0201f,"Especialista sin compensacion presupuestaria");
        var loaded=new Mantenimiento(1200,2400,173,true);
        var restored=new Mantenimiento(loaded.esfuerzo(),loaded.descanso(),loaded.hambreTicks(),loaded.pausado());
        for(int i=0;i<2399;i++)require(!restored.descansar(20),"Reload acorta descanso");
        require(restored.descansar(20)&&restored.esfuerzo()==0,"Reload no termina descanso");
        var appetiteClock=new Mantenimiento();int losses=0;
        for(int i=0;i<1200;i++)if(appetiteClock.consumirHambre(normal))losses++;
        require(losses==2,"Apetito base incoherente");
        float[] frugal={.2f,0,0,0,0};
        require(Mantenimiento.intervaloHambre(frugal)>Mantenimiento.intervaloHambre(normal),"Menor apetito consume mas");
        float[] agile={0,0,.6f,0,0};
        require(Mantenimiento.tiempoExtraccion(100,20,100,0,agile)==Mantenimiento.tiempoExtraccion(100,20,100,0,normal),"Agilidad altera mineria");
        float[] miner={0,0,0,.2f,0};
        require(Mantenimiento.tiempoExtraccion(100,20,100,0,miner)<Mantenimiento.tiempoExtraccion(100,20,100,0,normal),"Mineria no afecta extraccion");
        float[] resistant={0,.2f,0,0,0};
        require(Mantenimiento.limiteTrabajo(1,resistant)>1200,"Resistencia no amplía expedicion");
        var passive=new Mantenimiento(1200,0,0,false);
        for(int i=0;i<7;i++)passive.reposarLibre(1,normal);
        require(passive.esfuerzo()==1199&&passive.reposoFraccion()>0,"Reposo pasivo pierde la fraccion");
        passive=new Mantenimiento(passive.esfuerzo(),passive.descanso(),passive.hambreTicks(),passive.pausado(),passive.reposoFraccion());
        for(int i=7;i<6000;i++)passive.reposarLibre(1,normal);
        require(passive.esfuerzo()==0&&passive.reposoFraccion()==0,"Reposo pasivo no recupera toda la capacidad tras recargar");
        var blocked=new Mantenimiento(1200,6000,0,true);
        for(int i=0;i<6000;i++)blocked.reposarLibre(1,normal);
        require(blocked.descanso()==6000&&blocked.esfuerzo()==1200,"Reposo libre se salta una pausa activa");
        var fastRest=new Mantenimiento(Mantenimiento.limiteTrabajo(1,fused),0,0,false);
        for(int i=0;i<2400;i++)fastRest.reposarLibre(1,fused);
        require(fastRest.esfuerzo()==0,"Recuperacion especializada no afecta al reposo entre encargos");
        System.out.println("Mantenimiento real: "+checks+" comprobaciones correctas, sin Minecraft.");
    }
    public static void main(String[] args) {run();}
}
