package com.theslave.ritual;

/** Guion de las proyecciones: altura desde los pies, color, postura y almas. Sin entidades ni inventarios. */
public record AparicionRitual(float altura,float color,float arrodillado,float separacion,float almas,float objetos) {
    public static AparicionRitual en(int tipo,float tick) {
        return switch(tipo) {
            case 0 -> new AparicionRitual(suave(tick,160,200),suave(tick,200,236),0,0,ventana(tick,100,160),1-suave(tick,220,240));
            case 1 -> new AparicionRitual(suave(tick,320,360),suave(tick,360,398),1-suave(tick,360,398),0,ventana(tick,240,320),1-suave(tick,375,400));
            case 2 -> new AparicionRitual(0,0,0,0,ventana(tick,180,240),1-suave(tick,330,360));
            case 3 -> new AparicionRitual(suave(tick,80,180),suave(tick,400,476),0,.55f*(1-suave(tick,400,460)),ventana(tick,280,400),1-suave(tick,450,480));
            default -> throw new IllegalArgumentException("Ritual desconocido");
        };
    }
    public static float suave(float tick,float inicio,float fin) {
        float t=Math.clamp((tick-inicio)/(fin-inicio),0,1);return t*t*(3-2*t);
    }
    private static float ventana(float tick,float inicio,float fin) {
        return suave(tick,inicio,inicio+12)*(1-suave(tick,fin-12,fin));
    }
}
