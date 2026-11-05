package com.theslave.ritual;

/** Gestos lentos de entrada, inspeccion, cobro, sutura y despedida. Valores en radianes. */
public record GestosMuerte(float cabeza,float derechaX,float derechaZ,float izquierdaX,float izquierdaZ) {
    public static GestosMuerte en(int tipo,float tick) {
        var s=SecuenciaRitual.para(tipo);
        float entra=1-AparicionRitual.suave(tick,s.entra()+8,s.entra()+24);
        float actua=AparicionRitual.suave(tick,s.llega(),s.llega()+18)*(1-AparicionRitual.suave(tick,s.sale()-16,s.sale()));
        float cobro=AparicionRitual.en(tipo,tick).almas();
        float costura=tipo==2?actua*(float)Math.sin((tick-s.llega())*.08f):0;
        float dos=tipo==3?cobro:0;
        return new GestosMuerte(.38f*actua,-1.3f*entra-.9f*cobro-.65f*actua+.16f*costura,
            .15f*entra+.22f*actua,-.2f*actua-1.0f*dos-.15f*costura,-.25f*dos-.12f*actua);
    }
}
