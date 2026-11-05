package com.ludopathyroulette.client;

import com.ludopathyroulette.Model;
import com.ludopathyroulette.core.AtomicStore;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientOptions {
    public int sound=0;
    public boolean reducedMotion;
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("ludopathyroulette-client.json");}
    public static ClientOptions load(){try{ClientOptions options=Files.exists(path())?Model.GSON.fromJson(Files.readString(path()),ClientOptions.class):new ClientOptions();if(options==null)return new ClientOptions();options.sound=Math.clamp(options.sound,0,2);return options;}catch(Exception e){return new ClientOptions();}}
    public void save(){try{AtomicStore.write(path(),Model.GSON.toJson(this));}catch(Exception e){System.err.println("No se pudieron guardar preferencias de ruleta: "+e.getMessage());}}
}
