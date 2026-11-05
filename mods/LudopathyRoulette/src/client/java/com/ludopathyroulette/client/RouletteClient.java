package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class RouletteClient implements ClientModInitializer {
    public static ClientOptions options;
    public static final Map<String,Deque<String>> history=new LinkedHashMap<>();
    public void onInitializeClient(){
        options=ClientOptions.load();
        BlockEntityRendererRegistry.register(RouletteMod.MACHINE_BE,MachineRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Wire.View.TYPE,(packet,context)->context.client().execute(()->{
            try{
                JsonObject v=JsonParser.parseString(packet.json()).getAsJsonObject();
                if(v.get("mode").getAsString().equals("world")) {
                    if(context.client().level!=null&&context.client().level.getBlockEntity(packet.pos()) instanceof RouletteBlockEntity be){be.publicJson=packet.json();be.snapshotTick=context.client().level.getGameTime();}
                    boolean ownUi=context.client().gui.screen() instanceof PlayScreen screen&&screen.pos.equals(packet.pos());
                    if(ownUi)((PlayScreen)context.client().gui.screen()).world(v);
                    else if(v.has("spin")&&options.sound<2)context.client().getSoundManager().play(new SimpleSoundInstance(v.get("casino").getAsBoolean()?RouletteMod.BOUNCE:RouletteMod.CLICK,SoundSource.BLOCKS,options.sound==1?.1f:.25f,1f,RandomSource.create(),packet.pos()));
                    return;
                }
                if(v.get("open").getAsBoolean()) {
                    if(v.get("mode").getAsString().equals("editor"))context.client().gui.setScreen(new EditorScreen(packet.pos(),v));
                    else context.client().gui.setScreen(new PlayScreen(packet.pos(),v));
                }else if(context.client().gui.screen() instanceof BaseScreen screen&&screen.pos.equals(packet.pos()))screen.update(v);
            }catch(Exception e){System.err.println("Vista de LudopathyRoulette rechazada: "+e);}
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->history.clear());
    }
    public static void record(String machine,String text){
        Deque<String> list=history.computeIfAbsent(machine,k->new ArrayDeque<>());list.addFirst(text);while(list.size()>20)list.removeLast();
        while(history.size()>128)history.remove(history.keySet().iterator().next());
    }
}
