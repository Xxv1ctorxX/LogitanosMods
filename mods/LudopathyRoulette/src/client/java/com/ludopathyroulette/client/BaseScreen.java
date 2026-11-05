package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

abstract class BaseScreen extends Screen {
    static final int BG=0xf21a1c22,PANEL=0xff282b33,INK=0xfff5eedc,MUTED=0xffb3b7c3,GOLD=0xffffce66;
    public final BlockPos pos;
    JsonObject view;
    int sequence,left,top,panelWidth,panelHeight;
    String notice="";
    private int heartbeat;
    BaseScreen(BlockPos p,JsonObject v){super(Component.literal("Ludopathy Roulette"));pos=p;view=v;sequence=v.get("sequence").getAsInt();}
    public void update(JsonObject v){view=v;sequence=Math.max(sequence,v.get("sequence").getAsInt());if(v.has("message")&&!v.get("message").getAsString().isBlank())notice=v.get("message").getAsString();}
    void bounds(){panelWidth=Math.min(width-12,620);panelHeight=Math.min(height-12,360);left=(width-panelWidth)/2;top=(height-panelHeight)/2;}
    Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    EditBox field(String label,String value,int x,int y,int w,int max){EditBox e=new EditBox(font,x,y,w,20,Component.literal(label));e.setMaxLength(max);e.setValue(value);return addRenderableWidget(e);}
    void send(String verb,JsonObject data){ClientPlayNetworking.send(new Wire.Request(pos,view.get("session").getAsString(),++sequence,verb,data.toString()));}
    void send(String verb){send(verb,new JsonObject());}
    static JsonObject json(Object... values){JsonObject d=new JsonObject();for(int i=0;i<values.length;i+=2){Object value=values[i+1];if(value instanceof Number n)d.addProperty(values[i].toString(),n);else if(value instanceof Boolean b)d.addProperty(values[i].toString(),b);else d.addProperty(values[i].toString(),value.toString());}return d;}
    void frame(GuiGraphicsExtractor g,String subtitle){
        g.fill(0,0,width,height,0xb0000000);g.fill(left,top,left+panelWidth,top+panelHeight,BG);g.fill(left,top,left+panelWidth,top+2,GOLD);
        g.text(font,"LUDOPATHY / "+view.get("name").getAsString(),left+10,top+10,GOLD);
        g.text(font,font.plainSubstrByWidth(subtitle,panelWidth-20),left+10,top+25,MUTED);
        if(!notice.isBlank()){String shortText=font.plainSubstrByWidth(notice,panelWidth-20);g.text(font,shortText,left+10,top+panelHeight-13,INK);}
    }
    public void onClose(){send("close");super.onClose();}
    public void tick(){if(++heartbeat%200==0)send("sync");}
    public boolean isPauseScreen(){return false;}
}
