package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.*;
import com.ludopathyroulette.core.WheelMath;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Editor de borrador servidor. Nunca manda ItemStack ni NBT procedentes del cliente. */
final class EditorScreen extends BaseScreen {
    private int tab,row,actionIndex,sampleTarget=-2;
    private boolean small;
    private boolean dirty,discardArmed,demo;
    private long demoStart;
    private EditBox name,xp,weight,factor,uses,count,confirm,percent;
    EditorScreen(BlockPos p,JsonObject v){super(p,v);}
    private Model.Config config(){return Model.GSON.fromJson(view.get("draft"),Model.Config.class);}
    private Model.Segment segment(){Model.Config c=config();row=Math.clamp(row,0,c.segments.size()-1);return c.segments.get(row);}
    public void update(JsonObject v){String previous=view.get("draft").toString();int revision=config().revision;super.update(v);if(config().revision>revision)dirty=false;if(!previous.equals(v.get("draft").toString()))rebuildWidgets();}
    protected void init(){
        bounds();small=panelWidth<400||panelHeight<228;
        if(small){notice="Reduce la escala GUI o amplía la ventana para editar.";button("Cerrar",left+10,top+panelHeight-41,90,this::onClose);return;}
        int y=top+43;
        button("General",left+8,y,58,()->switchTab(0));button("Casillas",left+70,y,58,()->switchTab(1));button("Acciones",left+132,y,58,()->switchTab(2));button("Muestras",left+194,y,58,()->switchTab(3));button("Reparto",left+256,y,58,()->switchTab(4));
        button("Probar",left+panelWidth-70,y,62,()->{demo=true;demoStart=System.nanoTime();});
        int x=left+10,content=top+78;
        switch(tab){case 0->general(x,content);case 1->rows(x,content);case 2->actions(x,content);case 3->samples(x,content);case 4->percentages(x,content);}
        int footer=top+panelHeight-41;
        button("Guardar y publicar",x,footer,126,this::publish);
        button("Recargar",x+130,footer,76,()->{if(!discardArmed){discardArmed=true;notice="Recargar pierde el borrador. Pulsa otra vez para confirmar.";}else{send("reload");dirty=false;discardArmed=false;}});
        button("Cerrar",left+panelWidth-76,footer,66,this::onClose);
    }
    private void switchTab(int n){tab=n;demo=false;rebuildWidgets();}
    private void change(String action,JsonObject data){dirty=true;discardArmed=false;send(action,data);}
    private void general(int x,int y){
        Model.Config c=config();int col=Math.max(165,panelWidth/2);
        name=field("Nombre",c.name,x,y,Math.min(220,panelWidth-28),32);
        xp=field("Niveles de XP",Integer.toString(c.xp),x,y+40,90,4);
        button(c.free?"Gratis: SÍ":"Gratis: NO",x+96,y+40,112,()->generalSave(!c.free,c.paused,c.announce));
        button(c.paused?"Reanudar":"Pausar",x,y+64,90,()->generalSave(c.free,!c.paused,c.announce));
        button(c.announce?"Anuncio: SÍ":"Anuncio: NO",x+96,y+64,112,()->generalSave(c.free,c.paused,!c.announce));
        button("Aplicar nombre / XP",x,y+88,150,()->generalSave(c.free,c.paused,c.announce));
        button("Precio: muestra",x+col+24,y,138,()->{sampleTarget=-1;switchTab(3);});
        button("Quitar objeto precio",x+col+24,y+24,138,()->change("clearprice",new JsonObject()));
        confirm=field("Confirmación letal: nombre exacto", "",x+col+24,y+88,Math.min(145,panelWidth-col-44),32);
    }
    private void generalSave(boolean free,boolean paused,boolean announce){
        try{change("general",json("name",name.getValue(),"xp",Integer.parseInt(xp.getValue()),"free",free,"paused",paused,"announce",announce));}
        catch(NumberFormatException e){notice="XP debe ser un entero entre 0 y 1000.";}
    }
    private void rows(int x,int y){
        Model.Config c=config();int col=Math.max(150,panelWidth/2);
        row=Math.clamp(row,0,c.segments.size()-1);Model.Segment s=c.segments.get(row);
        button("◀",x,y,26,()->{row=Math.floorMod(row-1,c.segments.size());rebuildWidgets();});
        button("▶",x+108,y,26,()->{row=(row+1)%c.segments.size();rebuildWidgets();});
        button("Añadir casilla",x,y+28,134,()->{change("add",new JsonObject());});
        button("Quitar casilla",x,y+52,134,()->change("remove",json("row",row)));
        button("Igualar pesos",x,y+76,134,()->change("equal",new JsonObject()));
        weight=field("Peso",Integer.toString(s.weight),x+col,y,100,7);
        button(s.category.name(),x+col,y+24,134,()->{
            Model.Category[] values=Model.Category.values();Model.Category next=values[(s.category.ordinal()+1)%values.length];
            change("row",json("row",row,"weight",s.weight,"category",next.name()));
        });
        button("Aplicar peso",x+col,y+48,134,()->{try{change("row",json("row",row,"weight",Integer.parseInt(weight.getValue()),"category",s.category.name()));}catch(NumberFormatException e){notice="Peso entero entre 1 y 1000000.";}});
        button("Elegir premio",x+col,y+72,134,()->{sampleTarget=-2;switchTab(3);});
    }
    private void percentages(int x,int y){
        percent=field("Porcentajes separados por ;","",x,y+26,Math.min(280,panelWidth-120),160);
        button("Aplicar %",x,y+54,94,()->{
            JsonObject d=new JsonObject();JsonArray values=new JsonArray();for(String v:percent.getValue().split(";"))values.add(v.trim());d.add("values",values);change("percent",d);
        });
    }
    private void actions(int x,int y){
        Model.Segment s=segment();int col=Math.max(160,panelWidth/2);
        if(s.category!=Model.Category.PUNISH){button("Ir a casillas",x,y+25,144,()->switchTab(1));return;}
        actionIndex=Math.clamp(actionIndex,0,Math.min(2,s.actions.size()));
        Model.Action a=actionIndex<s.actions.size()?s.actions.get(actionIndex):new Model.Action();
        button("◀",x,y,62,()->{actionIndex=Math.max(0,actionIndex-1);rebuildWidgets();});
        button("▶",x+66,y,66,()->{actionIndex=Math.min(2,Math.min(s.actions.size(),actionIndex+1));rebuildWidgets();});
        button(a.kind.name(),x,y+28,132,()->{
            Model.Kind[] kinds=Model.Kind.values();saveAction(kinds[(a.kind.ordinal()+1)%kinds.length]);
        });
        button("Quitar acción",x,y+56,132,()->change("action",json("row",row,"slot",actionIndex,"remove",true)));
        button("Muestra de acción",x,y+84,132,()->{sampleTarget=actionIndex;switchTab(3);});
        factor=field("Factor ×100",Integer.toString(a.factor),x+col,y+16,90,4);
        uses=field("Usos",Integer.toString(a.uses),x+col,y+54,90,1);
        count=field("Criaturas",Integer.toString(a.count),x+col,y+88,90,1);
        button("Aplicar",x+col+96,y+88,70,()->saveAction(a.kind));
    }
    private void saveAction(Model.Kind kind){try{change("action",json("row",row,"slot",actionIndex,"kind",kind.name(),"factor",Integer.parseInt(factor.getValue()),"uses",Integer.parseInt(uses.getValue()),"count",Integer.parseInt(count.getValue())));}catch(NumberFormatException e){notice="Los parámetros deben ser enteros.";}}
    private void samples(int x,int y){
        button("Precio",x,y,76,()->{sampleTarget=-1;rebuildWidgets();});
        button("Premio",x+80,y,76,()->{sampleTarget=-2;rebuildWidgets();});
        if(segment().category==Model.Category.PUNISH)for(int i=0;i<segment().actions.size();i++){int a=i;button("Acción "+(i+1),x+160+i*70,y,66,()->{sampleTarget=a;rebuildWidgets();});}
        int slotSize=17;
        for(int i=0;i<36;i++){final int slot=i;int bx=x+(i%9)*slotSize,by=y+36+(i/9)*slotSize;
            var stack=minecraft.player.getInventory().getItem(slot);
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(Integer.toString(slot+1)),b->change("sample",json("row",row,"target",sampleTarget,"inventory",slot))).bounds(bx,by,16,16).build()).active=!stack.isEmpty();
        }
    }
    private void publish(){
        Model.Config c=config();
        if(tab==0)generalSave(c.free,c.paused,c.announce);
        String confirmation=confirm==null?"":confirm.getValue();
        send("save",json("confirm",confirmation));
    }
    public void onClose(){if(dirty&&!discardArmed){discardArmed=true;notice="Hay cambios en el borrador. Pulsa Cerrar otra vez para descartarlos.";return;}super.onClose();}
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        frame(g,"STAFF · plantillas ilimitadas / cobro consumido · revisión "+config().revision+(view.get("conflict").getAsBoolean()?" · CONFLICTO":""));
        if(small){super.extractRenderState(g,mx,my,partial);return;}
        int x=left+10,y=top+78,col=Math.max(165,panelWidth/2);Model.Config c=config();
        if(tab==0){g.text(font,"Nombre",x,y-11,MUTED);g.text(font,"Niveles de XP",x,y+29,MUTED);g.text(font,"Confirmar daño letal:",x+col+24,y+76,GOLD);
            String price;try{price=Model.itemName(c.price,minecraft.level.registryAccess());}catch(Exception e){price="Objeto incompatible";}
            g.text(font,font.plainSubstrByWidth("Precio: "+price,panelWidth-col-44),x+col+24,y+52,INK);
        }
        if(tab==1){Model.Segment s=segment();g.centeredText(font,"Casilla "+(row+1)+" / "+c.segments.size(),x+67,y+6,GOLD);g.text(font,"Peso entero",x+Math.max(150,panelWidth/2),y-11,MUTED);}
        if(tab==2){if(segment().category!=Model.Category.PUNISH)g.text(font,"Selecciona una casilla PUNISH para editar acciones.",x,y,INK);else {g.text(font,"Acción "+(actionIndex+1)+" · casilla "+(row+1),x,y-11,GOLD);g.text(font,"Factor ×100 (200 = ×2)",x+col,y+3,MUTED);g.text(font,"Usos futuros (1–5)",x+col,y+41,MUTED);g.text(font,"Criaturas (1–3)",x+col,y+79,MUTED);}}
        if(tab==3){g.text(font,"Destino: "+(sampleTarget==-1?"precio":sampleTarget==-2?"premio casilla "+(row+1):"acción "+(sampleTarget+1)),x,y+24,GOLD);int slotSize=17;
            for(int i=0;i<36;i++){var stack=minecraft.player.getInventory().getItem(i);int bx=x+(i%9)*slotSize,by=y+36+(i/9)*slotSize;
                if(mx>=bx&&mx<bx+slotSize&&my>=by&&my<by+slotSize&&!stack.isEmpty())g.setTooltipForNextFrame(font,stack,mx,my);
            }
        }
        if(tab==4){g.text(font,"Todos los porcentajes, separados por ; (suma 100).",x,y+4,INK);g.text(font,"Ejemplo de 4 casillas: 1;24;25;50",x,y+14,MUTED);}
        super.extractRenderState(g,mx,my,partial);
        if(tab==3){int slotSize=17;for(int i=0;i<36;i++){var stack=minecraft.player.getInventory().getItem(i);if(!stack.isEmpty())g.item(stack,x+(i%9)*slotSize,y+36+(i/9)*slotSize);}}
        if(demo){
            g.fill(left+5,top+67,left+panelWidth-5,top+panelHeight-46,0xfd1a1c22);g.centeredText(font,"DEMOSTRACIÓN · sin coste ni consecuencias",left+panelWidth/2,top+72,GOLD);
            JsonObject v=new JsonObject();v.addProperty("casino",view.get("casino").getAsBoolean());JsonArray rows=new JsonArray();for(Model.Segment s:c.segments){JsonObject r=new JsonObject();r.addProperty("weight",s.weight);r.addProperty("category",s.category.name());rows.add(r);}v.add("rows",rows);
            JsonObject spin=json("winner",0,"seed",42,"duration",120);v.add("spin",spin);double elapsed=(System.nanoTime()-demoStart)/50_000_000.0;
            WheelCanvas.draw(g,v,left+panelWidth/2,top+panelHeight/2,Math.max(25,(panelHeight-145)/2),elapsed,RouletteClient.options.reducedMotion);if(elapsed>140)demo=false;
        }
    }
}
