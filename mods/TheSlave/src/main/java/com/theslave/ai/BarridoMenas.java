package com.theslave.ai;

/** Cursor de cascaras cubicas: posiciones cercanas primero, sin almacenar el volumen buscado. */
public final class BarridoMenas {
    private final int radio;
    private int capa, indice, x, y, z;
    public BarridoMenas(int radio) {
        if(radio<1||radio>32)throw new IllegalArgumentException("Radio fuera del presupuesto de busqueda");
        this.radio=radio;
    }
    public boolean siguiente() {
        if(capa>radio)return false;
        if(capa==0) {x=y=z=0;capa=1;return true;}
        int ancho=2*capa+1,interior=ancho-2;
        int carasY=2*ancho*ancho,carasX=2*interior*ancho;
        if(indice<carasY) {
            int cara=indice/(ancho*ancho),p=indice%(ancho*ancho);
            x=p/ancho-capa;z=p%ancho-capa;y=cara==0?-capa:capa;
        } else if(indice<carasY+carasX) {
            int p=indice-carasY,cara=p/(interior*ancho);p%=interior*ancho;
            x=cara==0?-capa:capa;y=p/ancho-capa+1;z=p%ancho-capa;
        } else {
            int p=indice-carasY-carasX,cara=p/(interior*interior);p%=interior*interior;
            z=cara==0?-capa:capa;x=p/interior-capa+1;y=p%interior-capa+1;
        }
        if(++indice==24*capa*capa+2) {indice=0;capa++;}
        return true;
    }
    public int x(){return x;}
    public int y(){return y;}
    public int z(){return z;}
}
