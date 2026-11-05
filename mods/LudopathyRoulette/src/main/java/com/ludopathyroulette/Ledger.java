package com.ludopathyroulette;

import com.google.gson.*;
import com.ludopathyroulette.core.AtomicStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Diario mundial: una caída inesperada pone operaciones no liquidadas en revisión, sin repetir mutaciones. */
public final class Ledger {
    public static class Machine {
        public String id=UUID.randomUUID().toString(),dimension="",status="PLACED";
        public long position;
        public boolean casino;
        public Model.Config config=new Model.Config();
        public long total,grand,normal,punish;
        public String lastResult="";
        public JsonObject lastFrame;
    }
    public static class Operation {
        public String id=UUID.randomUUID().toString(),player,machine,state="PREPARED",note="";
        public Model.Config config;
        public int winner,duration,remaining,actionIndex,warning=100;
        public long seed,started;
        public boolean counted,notified;
        public List<Model.Action> actions=new ArrayList<>();
    }
    public static class Surcharge { public int factor=100,uses; }
    public static class State {
        public int schema=1;
        public boolean clean=true;
        public Map<String,Machine> machines=new LinkedHashMap<>();
        public Map<String,Operation> operations=new LinkedHashMap<>();
        public Map<String,Surcharge> surcharges=new HashMap<>();
    }
    public final Path path;
    public State state;
    public Ledger(Path path) {
        this.path=path;
        try {
            state=Files.exists(path)?Model.GSON.fromJson(Files.readString(path),State.class):new State();
            if(state==null || state.schema!=1 || state.machines==null || state.operations==null || state.surcharges==null)
                throw new IOException("Formato del diario incompatible");
            if(!state.clean) recoverUnclean(state);
            state.clean=false; flush();
        } catch(Exception e){throw new IllegalStateException("Diario bloqueado; conserva el original: "+path,e);}
    }
    public static void recoverUnclean(State s) {
        for(Operation o:s.operations.values()) if(!o.state.equals("SETTLED")) {o.state="REVIEW";o.note="Parada inesperada: verificar cobro y acciones antes de continuar";}
    }
    public void flush() {
        try {AtomicStore.write(path,Model.GSON.toJson(state));}
        catch(IOException e){throw new IllegalStateException("No se pudo sincronizar el diario; juego bloqueado",e);}
    }
    public void audit(Operation o,String message) {
        // La instantánea es la evidencia duradera; el log es solo una vista rotatoria auxiliar.
        try {
            Path file=path.resolveSibling("audit.log");
            if(Files.exists(file)&&Files.size(file)>8*1024*1024) Files.move(file,path.resolveSibling("audit.previous.log"),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file,java.time.Instant.now()+" "+o.id+" "+o.player+" "+o.machine+" "+o.state+" "+message.replace('\n',' ')+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException e){System.err.println("LudopathyRoulette auditoría: "+e.getMessage());}
    }
}
