package com.ludopathyroulette;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.sounds.SoundEvent;

public final class RouletteMod implements ModInitializer {
    public static final String ID="ludopathyroulette";
    public static Service service;
    public static Policy policy;
    public static Block FORTUNA,BOLA;
    public static SoundEvent CLICK,ROLL,BOUNCE,WIN;
    public static BlockEntityType<RouletteBlockEntity> MACHINE_BE;
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath(ID,path);}
    public void onInitialize() {
        policy=Policy.load();
        FORTUNA=block("fortuna",false); BOLA=block("bola",true);
        CLICK=sound("click");ROLL=sound("roll");BOUNCE=sound("bounce");WIN=sound("win");
        MACHINE_BE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id("machine"),FabricBlockEntityTypeBuilder.create(RouletteBlockEntity::new,FORTUNA,BOLA).build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("main"),FabricCreativeModeTab.builder().title(Component.literal("Ludopathy Roulette")).icon(()->new ItemStack(FORTUNA)).displayItems((parameters,output)->{output.accept(FORTUNA);output.accept(BOLA);}).build());
        Wire.register(); RouletteCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(s->service=new Service(s));
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(service!=null)service.close();});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->service=null);
        ServerTickEvents.END_SERVER_TICK.register(s->{if(service!=null)service.tick();});
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be,l)->{if(be instanceof RouletteBlockEntity machine && service!=null)service.ensure(machine);});
        PlayerBlockBreakEvents.BEFORE.register((l,p,pos,state,be)->{
            if(be instanceof RouletteBlockEntity){p.sendOverlayMessage(Component.literal("Usa /ludopathyroulette retirar (staff)."));return false;}return true;
        });
        ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{
            if(!ServerPlayNetworking.canSend(h.player,Wire.View.TYPE)) h.disconnect(Component.literal("Instala LudopathyRoulette 1.0.0 para Minecraft 26.3."));
            else if(service!=null)service.join(h.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{if(service!=null)service.leave(h.player);});
    }
    private static Block block(String name,boolean casino) {
        ResourceKey<Block> key=ResourceKey.create(Registries.BLOCK,id(name));
        Block b=Registry.register(BuiltInRegistries.BLOCK,key,new RouletteBlock(BlockBehaviour.Properties.of().setId(key).strength(-1,3600000).noLootTable().noOcclusion().pushReaction(PushReaction.IMMOVEABLE).sound(SoundType.METAL),casino));
        ResourceKey<Item> itemKey=ResourceKey.create(Registries.ITEM,id(name));
        Registry.register(BuiltInRegistries.ITEM,itemKey,new BlockItem(b,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));return b;
    }
    private static SoundEvent sound(String name){return Registry.register(BuiltInRegistries.SOUND_EVENT,id(name),SoundEvent.createVariableRangeEvent(id(name)));}
}
