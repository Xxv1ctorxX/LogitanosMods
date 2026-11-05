package com.ludopathyroulette;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** JSON acotado para vistas. Las peticiones nunca contienen objetos ni resultados aceptados. */
public final class Wire {
    public record Request(BlockPos pos,String session,int sequence,String action,String data) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RouletteMod.id("request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,v)-> {
            b.writeBlockPos(v.pos); b.writeUtf(v.session,40); b.writeVarInt(v.sequence); b.writeUtf(v.action,24); b.writeUtf(v.data,4096);
        },b->new Request(b.readBlockPos(),b.readUtf(40),b.readVarInt(),b.readUtf(24),b.readUtf(4096)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record View(BlockPos pos,String json) implements CustomPacketPayload {
        public static final Type<View> TYPE=new Type<>(RouletteMod.id("view_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,v)->{b.writeBlockPos(v.pos);b.writeUtf(v.json,196608);},b->new View(b.readBlockPos(),b.readUtf(196608)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE,View.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(v,c)->c.server().execute(()->RouletteMod.service.request(c.player(),v)));
    }
}
