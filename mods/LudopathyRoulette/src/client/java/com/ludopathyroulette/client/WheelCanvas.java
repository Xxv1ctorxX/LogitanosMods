package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.core.WheelMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Disco por bandas horizontales: color proporcional, misma geometría angular que el mundo. */
final class WheelCanvas {
    static int color(String category){return switch(category){case "GRAND"->0xfff0c04a;case "PUNISH"->0xffc0404a;default->0xff3f9a78;};}
    static int[] weights(JsonObject view){JsonArray rows=view.getAsJsonArray("rows");int[] w=new int[rows.size()];for(int i=0;i<w.length;i++)w[i]=rows.get(i).getAsJsonObject().get("weight").getAsInt();return w;}
    static int sector(double angle,int[] w){double point=WheelMath.mod(angle)/WheelMath.TAU*WheelMath.sum(w);long cumulative=0;for(int i=0;i<w.length;i++){cumulative+=w[i];if(point<cumulative)return i;}return w.length-1;}
    static void draw(GuiGraphicsExtractor g,JsonObject v,int cx,int cy,int radius,double elapsed,boolean reduced){
        JsonArray rows=v.getAsJsonArray("rows");if(rows==null||rows.size()<2)return;
        int[] w=weights(v);boolean casino=v.get("casino").getAsBoolean();double flatten=casino?.62:1;
        JsonObject spin=v.has("spin")?v.getAsJsonObject("spin"):null;
        double rotation=0,ballAngle=0,ballRadius=.94,ballHeight=0;
        if(spin!=null){int winner=spin.get("winner").getAsInt(),duration=spin.get("duration").getAsInt();long seed=spin.get("seed").getAsLong();
            if(casino){var b=WheelMath.casino(w,winner,seed,reduced?duration:elapsed,duration);rotation=b.rotor();ballAngle=b.angle();ballRadius=b.radius();ballHeight=b.height();}
            else rotation=WheelMath.fortuna(w,winner,seed,reduced?duration:elapsed,duration);
        }
        g.fill(cx-radius-12,cy-(int)(radius*flatten)-12,cx+radius+12,cy+(int)(radius*flatten)+14,0xff0d0f14);
        disc(g,cx,cy,radius+9,flatten,0xff7a5518);disc(g,cx,cy,radius+7,flatten,0xffe3b64f);disc(g,cx,cy,radius+4,flatten,0xffa8782a);disc(g,cx,cy,radius+2,flatten,0xff10151a);
        // Bandas de dos píxeles agrupadas por sector: sin textura dinámica ni render a textura.
        for(int y=-(int)(radius*flatten);y<radius*flatten;y+=2){
            double localY=y/flatten;int extent=(int)Math.sqrt(Math.max(0,radius*radius-localY*localY));
            int run=-extent,last=-1;
            for(int x=-extent;x<=extent;x+=2){
                int s=sector(Math.atan2(localY,x)-rotation,w);
                if(last!=-1&&s!=last){g.fill(cx+run,cy+y,cx+x,cy+y+2,color(rows.get(last).getAsJsonObject().get("category").getAsString()));g.fill(cx+x-1,cy+y,cx+x+1,cy+y+2,0xff10151a);run=x;}
                last=s;
            }
            if(last!=-1)g.fill(cx+run,cy+y,cx+extent+1,cy+y+2,color(rows.get(last).getAsJsonObject().get("category").getAsString()));
        }
        long cumulative=0,total=WheelMath.sum(w);
        for(int i=0;i<w.length;i++){
            double a=WheelMath.TAU*(cumulative+w[i]*.5)/total+rotation;cumulative+=w[i];
            int x=cx+(int)(Math.cos(a)*radius*.76),y=cy+(int)(Math.sin(a)*radius*.76*flatten);
            // Los números exteriores siguen siendo legibles cuando el arco es diminuto.
            if(w[i]/(double)total>.03)g.centeredText(net.minecraft.client.Minecraft.getInstance().font,Integer.toString(i+1),x,y-4,0xff10151a);
        }
        for(int i=0;i<w.length;i++){
            double a=WheelMath.TAU*Math.max(0,0)+rotation;long before=0;for(int k=0;k<i;k++)before+=w[k];a=WheelMath.TAU*before/total+rotation;
            int px=cx+(int)(Math.cos(a)*(radius+5.5)),py=cy+(int)(Math.sin(a)*(radius+5.5)*flatten);
            g.fill(px-1,py-1,px+2,py+2,0xfffff2b8);
        }
        disc(g,cx,cy,9,1,0xff10151a);disc(g,cx,cy,7,1,0xffe3b64f);disc(g,cx,cy,4,1,0xfffff0c0);disc(g,cx,cy,2,1,0xffa8782a);
        if(casino){int bx=cx+(int)(Math.cos(ballAngle)*radius*ballRadius),by=cy+(int)(Math.sin(ballAngle)*radius*ballRadius*flatten-ballHeight*radius);
            g.fill(bx-3,by+3,bx+5,by+6,0x99000000);g.fill(bx-3,by-3,bx+4,by+4,0xffeee9d6);g.fill(bx-2,by-2,bx+1,by+1,0xffffffff);
        }else {for(int i=0;i<9;i++)g.fill(cx-i,cy-radius-13+i,cx+i+1,cy-radius-12+i,i<2?0xffc0404a:0xffffefd0);}
    }
    static void disc(GuiGraphicsExtractor g,int cx,int cy,int r,double flatten,int color){
        int h=(int)(r*flatten);
        for(int y=-h;y<=h;y+=1){double ly=y/flatten;int e=(int)Math.sqrt(Math.max(0,(double)r*r-ly*ly));g.fill(cx-e,cy+y,cx+e+1,cy+y+1,color);}
    }
}
