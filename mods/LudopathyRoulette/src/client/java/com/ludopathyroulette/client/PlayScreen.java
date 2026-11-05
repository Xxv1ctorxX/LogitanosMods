package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.*;
import com.ludopathyroulette.core.WheelMath;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

final class PlayScreen extends BaseScreen {
    private JsonObject wheel;
    private JsonObject lastSpin;
    private String recordedSpin="";
    private boolean ownSpin;
    private String paidQuote="";
    private double receivedAt,lastTick,dragAngle,dragTravel;
    private long receivedNanos;
    private boolean waiting,dragging,lethalAccepted,history;
    private record GestureSample(long nanos,double travel) {}
    private final Deque<GestureSample> gesture=new ArrayDeque<>();
    private int cx,cy,radius,page,soundStep=-1;
    private Button spin;
    PlayScreen(BlockPos p,JsonObject v){super(p,v);ownSpin=v.get("ownSpin").getAsBoolean();world(v);}
    protected void init(){
        bounds();cx=left+panelWidth/3;cy=top+panelHeight/2;radius=Math.max(32,Math.min(panelWidth/3-36,panelHeight/2-76));
        spin=button("Girar",left+10,top+panelHeight-62,panelWidth/2-15,()->requestSpin(.5f));
        button("Recoger",left+panelWidth/2+3,top+panelHeight-62,74,()->send("claim"));
        button("Cerrar",left+panelWidth-83,top+panelHeight-62,73,this::onClose);
        button("Audio",left+10,top+panelHeight-38,66,()->{RouletteClient.options.sound=(RouletteClient.options.sound+1)%3;RouletteClient.options.save();notice="Audio: "+new String[]{"normal","reducido","silenciado"}[RouletteClient.options.sound];});
        button("Movimiento",left+80,top+panelHeight-38,90,()->{RouletteClient.options.reducedMotion=!RouletteClient.options.reducedMotion;RouletteClient.options.save();notice="Movimiento reducido: "+(RouletteClient.options.reducedMotion?"activado":"desactivado");});
        button("Historial",left+174,top+panelHeight-38,68,()->history=!history);
        button("◀",left+panelWidth-65,top+panelHeight-38,24,()->page=Math.max(0,page-1));
        button("▶",left+panelWidth-37,top+panelHeight-38,24,()->page++);
    }
    public void world(JsonObject v){
        if(v.has("spin"))lastSpin=v.deepCopy();
        else if(lastSpin!=null&&lastSpin.get("revision").getAsInt()!=v.get("revision").getAsInt())lastSpin=null;
        if(!v.has("spin")&&lastSpin==null&&v.has("lastFrame")&&v.getAsJsonObject("lastFrame").get("revision").getAsInt()==v.get("revision").getAsInt()){
            lastSpin=v.deepCopy();lastSpin.add("spin",v.getAsJsonObject("lastFrame").get("spin").deepCopy());
        }
        wheel=v.has("spin")||lastSpin==null?v.deepCopy():lastSpin.deepCopy();receivedNanos=System.nanoTime();
        receivedAt=v.has("spin")?v.getAsJsonObject("spin").get("elapsed").getAsDouble():lastSpin==null?0:lastSpin.getAsJsonObject("spin").get("duration").getAsDouble();
    }
    public void update(JsonObject v){
        if(view.get("revision").getAsInt()!=v.get("revision").getAsInt())lethalAccepted=false;
        JsonObject previous=lastSpin;boolean wasOwn=ownSpin;ownSpin=v.get("ownSpin").getAsBoolean();super.update(v);world(v);waiting=false;
        if(wasOwn&&previous!=null&&!v.has("spin")&&!recordedSpin.equals(previous.getAsJsonObject("spin").get("id").getAsString())){
            recordedSpin=previous.getAsJsonObject("spin").get("id").getAsString();
            int winner=previous.getAsJsonObject("spin").get("winner").getAsInt();String description=previous.getAsJsonArray("rows").get(winner).getAsJsonObject().get("description").getAsString();
            RouletteClient.record(v.get("machine").getAsString(),description+" · "+paidQuote+" · consulta pendientes");
            if(RouletteClient.options.sound<2)minecraft.getSoundManager().play(SimpleSoundInstance.forUI(RouletteMod.WIN,1f));
        }
    }
    private double elapsed(){return receivedAt+Math.min(30,(System.nanoTime()-receivedNanos)/50_000_000.0);}
    private void requestSpin(float impulse){
        if(waiting||view.has("spin")||view.get("blocked").getAsBoolean())return;
        if(view.get("lethal").getAsBoolean()&&!lethalAccepted){lethalAccepted=true;notice="AVISO: puede causar daño letal. Pulsa otra vez para aceptar y pagar.";return;}
        waiting=true;paidQuote=view.get("price").getAsString();send("spin",json("revision",view.get("revision").getAsInt(),"factor",view.get("factor").getAsInt(),"impulse",impulse,"lethal",lethalAccepted));
    }
    public void tick(){
        super.tick();
        if(minecraft.level!=null&&minecraft.level.getGameTime()!=lastTick){lastTick=minecraft.level.getGameTime();}
        spin.active=!waiting&&!view.has("spin")&&!view.get("blocked").getAsBoolean()&&view.get("published").getAsBoolean()&&!view.get("paused").getAsBoolean()&&view.get("canPay").getAsBoolean();
        spin.setMessage(Component.literal(waiting?"Esperando servidor…":view.has("spin")?"Girando…":!view.get("canPay").getAsBoolean()?"Faltan objetos o niveles":"Girar · "+view.get("price").getAsString()));
        if(wheel.has("spin")&&RouletteClient.options.sound<2&&!RouletteClient.options.reducedMotion){
            int[] weights=WheelCanvas.weights(wheel);JsonObject state=wheel.getAsJsonObject("spin");int duration=state.get("duration").getAsInt();
            double angle=WheelMath.fortuna(weights,state.get("winner").getAsInt(),state.get("seed").getAsLong(),elapsed(),duration);
            int step=(int)(angle/(Math.PI/12));if(step!=soundStep&&elapsed()<duration){soundStep=step;minecraft.getSoundManager().play(SimpleSoundInstance.forUI(wheel.get("casino").getAsBoolean()?RouletteMod.BOUNCE:RouletteMod.CLICK,1.2f,RouletteClient.options.sound==1?.12f:.3f));}
        }
    }
    public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(e.button()==0&&!view.get("casino").getAsBoolean()&&Math.hypot(e.x()-cx,e.y()-cy)<radius&&spin.active){dragging=true;dragAngle=Math.atan2(e.y()-cy,e.x()-cx);dragTravel=0;gesture.clear();gesture.add(new GestureSample(System.nanoTime(),0));return true;}return super.mouseClicked(e,doubleClick);
    }
    public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragging){double angle=Math.atan2(e.y()-cy,e.x()-cx),delta=Math.atan2(Math.sin(angle-dragAngle),Math.cos(angle-dragAngle));if(Math.hypot(e.x()-cx,e.y()-cy)>radius*.2)dragTravel+=Math.abs(delta);dragAngle=angle;long now=System.nanoTime();gesture.addLast(new GestureSample(now,dragTravel));while(gesture.size()>2&&now-gesture.peekFirst().nanos()>120000000)gesture.removeFirst();return true;}return super.mouseDragged(e,dx,dy);
    }
    public boolean mouseReleased(MouseButtonEvent e){if(dragging){dragging=false;if(dragTravel>.2){GestureSample first=gesture.peekFirst();double speed=first==null?6:(dragTravel-first.travel())/Math.max(.001,(System.nanoTime()-first.nanos())/1e9);requestSpin((float)Math.clamp((speed-6)/8,0,1));}return true;}return super.mouseReleased(e);}
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        frame(g,"Precio publicado · "+view.get("price").getAsString()+" · resultados: "+view.get("total").getAsLong());
        try{if(view.has("priceItem")&&!view.get("priceItem").isJsonNull()){var priceStack=Model.stack(view.get("priceItem"),minecraft.level.registryAccess());if(!priceStack.isEmpty()){g.item(priceStack,left+panelWidth-28,top+6);if(mx>=left+panelWidth-28&&mx<left+panelWidth-12&&my>=top+6&&my<top+22)g.setTooltipForNextFrame(font,priceStack,mx,my);}}}catch(Exception ignored){}
        WheelCanvas.draw(g,wheel,cx,cy,radius,elapsed(),RouletteClient.options.reducedMotion);
        int x=left+panelWidth/2+10,y=top+47,w=panelWidth/2-20;
        if(history){Deque<String> list=RouletteClient.history.getOrDefault(view.get("machine").getAsString(),new ArrayDeque<>());g.text(font,"HISTORIAL LOCAL",x,y,GOLD);y+=14;for(String text:list){if(y>top+panelHeight-85)break;g.text(font,font.plainSubstrByWidth(text,w),x,y,INK);y+=13;}}
        else {JsonArray rows=wheel.getAsJsonArray("rows");int count=Math.max(1,(panelHeight-135)/29),pages=Math.max(1,(rows.size()+count-1)/count);page=Math.min(page,pages-1);long total=WheelMath.sum(WheelCanvas.weights(wheel));
            for(int i=page*count;i<Math.min(rows.size(),(page+1)*count);i++){
                JsonObject row=rows.get(i).getAsJsonObject();double chance=100.0*row.get("weight").getAsInt()/total;String percent=chance<.01?"<0,01 %":String.format(Locale.ROOT,"%.2f %%",chance);
                String desc=row.get("description").getAsString();g.text(font,(i+1)+" · "+row.get("category").getAsString()+" · "+percent,x,y,WheelCanvas.color(row.get("category").getAsString()));
                net.minecraft.world.item.ItemStack item=net.minecraft.world.item.ItemStack.EMPTY;
                try{if(view.has("items")&&i<view.getAsJsonArray("items").size())item=Model.stack(view.getAsJsonArray("items").get(i),minecraft.level.registryAccess());}catch(Exception ignored){}
                int offset=item.isEmpty()?0:18;if(!item.isEmpty())g.item(item,x,y+10);g.text(font,font.plainSubstrByWidth(desc,w-offset),x+offset,y+11,INK);
                if(mx>=x&&mx<x+w&&my>=y&&my<y+26){if(!item.isEmpty())g.setTooltipForNextFrame(font,item,mx,my);else g.setTooltipForNextFrame(Component.literal(desc),mx,my);}y+=29;
            }
        }
        if(view.get("uses").getAsInt()>0)g.text(font,"Recargo: "+view.get("uses").getAsInt()+" usos",left+10,top+panelHeight-79,GOLD);
        if(view.has("spin")&&elapsed()>=view.getAsJsonObject("spin").get("duration").getAsInt())g.text(font,"Esperando confirmación…",left+10,top+panelHeight-79,MUTED);
        super.extractRenderState(g,mx,my,partial);
    }
}
