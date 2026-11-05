package com.theslave.entity;

import com.theslave.Aptitudes;

/** Relojes de produccion y descanso, independientes del mundo para simular ciclos completos. */
public final class Mantenimiento {
    private int esfuerzo, descanso, hambreTicks;
    private boolean pausado;
    private double reposoFraccion;
    public Mantenimiento() {}
    public Mantenimiento(int esfuerzo,int descanso,int hambreTicks,boolean pausado) {
        this(esfuerzo,descanso,hambreTicks,pausado,0);
    }
    public Mantenimiento(int esfuerzo,int descanso,int hambreTicks,boolean pausado,double reposoFraccion) {
        this.esfuerzo=Math.max(0,esfuerzo);this.descanso=Math.max(0,Math.min(24000,descanso));
        this.hambreTicks=Math.max(0,hambreTicks);this.pausado=pausado||this.descanso>0;
        this.reposoFraccion=Double.isFinite(reposoFraccion)?Math.max(0,Math.min(Math.nextDown(1.0),reposoFraccion)):0;
    }
    public int esfuerzo() { return esfuerzo; }
    public int descanso() { return descanso; }
    public int hambreTicks() { return hambreTicks; }
    public boolean pausado() { return pausado; }
    public double reposoFraccion() { return reposoFraccion; }
    public static int limiteTrabajo(int minutos,float[] meritos) {
        return Math.max(1,(int)Math.round(Math.max(1,minutos)*1200.0*Aptitudes.factor(meritos,Aptitudes.RESISTENCIA)));
    }
    public static int duracionDescanso(float[] meritos) {
        return (int)Math.round(6000*Aptitudes.factor(meritos,Aptitudes.RECUPERACION));
    }
    public static int intervaloHambre(float[] meritos) {
        return Math.max(1,(int)Math.round(600/Aptitudes.factor(meritos,Aptitudes.APETITO)));
    }
    /** false conserva el encargo: solo impide ejecutar otro paso durante esta pausa. */
    public boolean trabajar(float hambre,int minutos,float[] meritos) {
        if (pausado) return false;
        if (esfuerzo>=limiteTrabajo(minutos,meritos)) {
            pausado=true;descanso=duracionDescanso(meritos);return false;
        }
        if (!(hambre>0)) { pausado=true;return false; }
        esfuerzo++;return true;
    }
    public boolean consumirHambre(float[] meritos) {
        if (++hambreTicks<intervaloHambre(meritos)) return false;
        hambreTicks=0;return true;
    }
    /** La falta de comida no regala resistencia; solo completar descanso restablece el esfuerzo. */
    public boolean descansar(float hambre) {
        if (descanso>0 && --descanso==0) { esfuerzo=0;reposoFraccion=0; }
        if (descanso==0 && hambre>0) { pausado=false;return true; }
        return false;
    }
    /** Un reposo real entre encargos recupera la capacidad completa en el mismo tiempo de descanso. */
    public void reposarLibre(int minutos,float[] meritos) {
        if(pausado)return;
        if(esfuerzo==0) {reposoFraccion=0;return;}
        reposoFraccion+=(double)limiteTrabajo(minutos,meritos)/duracionDescanso(meritos);
        int recuperado=(int)Math.floor(reposoFraccion+1e-9);
        esfuerzo=Math.max(0,esfuerzo-recuperado);
        reposoFraccion=esfuerzo==0?0:Math.max(0,reposoFraccion-recuperado);
    }
    public static boolean necesitaComer(float hambre,int nutricion) {
        return nutricion>0 && hambre<=Math.max(0,20-nutricion);
    }
    /** Agilidad solo afecta al desplazamiento: la extraccion usa condicion y destreza minera. */
    public static double condicion(float hambre,int moral,int aceleracion) {
        return (0.55+0.45*(Math.max(0,Math.min(20,hambre))/20.0))
                *(0.8+0.2*(Math.max(0,Math.min(100,moral))/100.0))*(1.0+0.3*Math.max(0,Math.min(3,aceleracion)));
    }
    public static int tiempoExtraccion(double base,float hambre,int moral,int aceleracion,float[] meritos) {
        return Math.max(1,(int)(Math.max(6,Math.min(300,base))/condicion(hambre,moral,aceleracion)*Aptitudes.factor(meritos,Aptitudes.MINERIA)));
    }
}
