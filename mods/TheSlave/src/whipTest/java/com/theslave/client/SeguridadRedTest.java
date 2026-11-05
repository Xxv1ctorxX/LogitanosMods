package com.theslave.client;

import com.theslave.net.*;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;

/** Ataques de repeticion y limites reales del codec, sin arrancar cliente ni servidor. */
public final class SeguridadRedTest {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void run() {
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        LimitePeticiones limite=new LimitePeticiones();int admitted=0;
        for(int i=0;i<10000;i++)if(limite.admitir(owner,LimitePeticiones.Accion.COMPRA,0))admitted++;
        require(admitted==1,"Spam compra ejecuta mas de una transaccion");
        require(!limite.admitir(owner,LimitePeticiones.Accion.COMPRA,999),"Compra ignora su intervalo");
        require(limite.admitir(owner,LimitePeticiones.Accion.COMPRA,1000),"Compra normal no vuelve a habilitarse");
        require(!limite.admitir(owner,LimitePeticiones.Accion.COMPRA,10),"Retroceso del reloj habilita spam");
        require(limite.admitir(other,LimitePeticiones.Accion.COMPRA,1000),"Un jugador agota el cupo individual de otro");
        limite.desconectar(owner);require(limite.conexiones()==1,"Desconexion deja historial en RAM");
        limite.limpiar();require(limite.conexiones()==0,"Cambio de mundo conserva cupos viejos");
        int total=0;
        for(int tick=0;tick<1000;tick++)for(var action:LimitePeticiones.Accion.values())if(limite.admitir(owner,action,tick))total++;
        require(total<=12,"Alternar tipos evade presupuesto global de peticiones");
        for(int n:new int[]{-1,9,Integer.MAX_VALUE}) {
            RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                buffer.writeBlockPos(net.minecraft.core.BlockPos.ZERO);buffer.writeVarInt(n);
                try{Payloads.AbrirFusion.CODEC.decode(buffer);throw new AssertionError("Lista fusion ilimitada aceptada");}
                catch(io.netty.handler.codec.DecoderException expected){checks++;}
            }finally{buffer.release();}
        }
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buffer.writeBlockPos(net.minecraft.core.BlockPos.ZERO);buffer.writeUtf("Nombre",64);buffer.writeVarInt(0);buffer.writeVarInt(1);buffer.writeVarInt(1000000);
            try{Payloads.TumbaInfo.CODEC.decode(buffer);throw new AssertionError("Lista tumba ilimitada aceptada");}
            catch(io.netty.handler.codec.DecoderException expected){checks++;}
        }finally{buffer.release();}
        try{new Payloads.Candidato(1,"Ejemplo",0,new float[]{0,0,0,0,Float.NaN});throw new AssertionError("NaN de aptitud aceptado");}
        catch(IllegalArgumentException expected){checks++;}
        float[] merits=new float[5];var candidate=new Payloads.Candidato(1,"Ejemplo",0,merits);merits[0]=Float.NaN;
        require(Float.isFinite(candidate.merito()[0]),"El paquete comparte el array mutable del productor");
        System.out.println("Seguridad de red: "+checks+" comprobaciones correctas, sin Minecraft.");
    }
    public static void main(String[] args){run();}
}
