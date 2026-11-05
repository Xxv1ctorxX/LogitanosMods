package com.ludopathyroulette;

import com.ludopathyroulette.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import com.google.gson.*;

/** No inicia Minecraft ni un servidor. Ejecuta solamente matemáticas, diario y contratos de recursos. */
public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void rejects(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException|ArithmeticException e){return;}throw new AssertionError("Se aceptó un dato inválido");}
    public static void main(String[] args)throws Exception{
        int[] w={1,24,25,50};
        check(WheelMath.sum(w)==100,"Suma");check(WheelMath.select(w,0)==0,"Borde 0");check(WheelMath.select(w,1)==1,"Borde 1");check(WheelMath.select(w,24)==1,"Borde 24");check(WheelMath.select(w,25)==2,"Borde 25");check(WheelMath.select(w,49)==2,"Borde 49");check(WheelMath.select(w,50)==3,"Borde 50");check(WheelMath.select(w,99)==3,"Borde 99");
        rejects(()->WheelMath.select(w,-1));rejects(()->WheelMath.select(w,100));rejects(()->WheelMath.sum(new int[]{1}));rejects(()->WheelMath.sum(new int[]{0,1}));rejects(()->WheelMath.sum(new int[]{1000001,1}));
        int[] maximum=new int[12];Arrays.fill(maximum,1000000);check(WheelMath.sum(maximum)==12000000,"Acumulado máximo");
        int[] percent=WheelMath.percentages(List.of("33,3333","33.3333","33.3334"));check(Arrays.stream(percent).sum()==1000000,"Reparto exacto");check(percent[2]>=percent[0],"Restos mayores");
        check(Arrays.equals(WheelMath.percentages(List.of("50","50")),new int[]{500000,500000}),"Partes iguales");
        rejects(()->WheelMath.percentages(List.of("50","49")));rejects(()->WheelMath.percentages(List.of("0","100")));rejects(()->WheelMath.percentages(List.of("0.00000001","99.99999999")));
        check(WheelMath.price(1,150)==2,"Redondeo objetos");check(WheelMath.price(3,150)==5,"Redondeo niveles");check(WheelMath.price(0,1000)==0,"Componente gratuito");check(WheelMath.price(64,1000)==640,"Tope multiplicador");rejects(()->WheelMath.price(-1,100));
        Random random=new Random(0xC0FFEE);int[] counts=new int[4];int samples=1000000;
        for(int i=0;i<samples;i++)counts[WheelMath.select(w,random.nextLong(100))]++;
        double chi=0;for(int i=0;i<w.length;i++){double expected=samples*w[i]/100.0;chi+=(counts[i]-expected)*(counts[i]-expected)/expected;}
        check(chi<20,"Chi cuadrado con semilla fija: "+chi);
        for(int n=0;n<10000;n++){
            int[] weights=n%2==0?w:new int[]{1,1000000,1,3};int winner=n%4;long seed=random.nextLong();int duration=90+random.nextInt(111);
            double target=WheelMath.target(weights,winner,seed),center=WheelMath.center(weights,winner),span=WheelMath.TAU*weights[winner]/WheelMath.sum(weights);
            check(Math.abs(target-center)<span*.31,"Jitter interior");
            double finalAngle=WheelMath.fortuna(weights,winner,seed,duration,duration);
            double local=WheelMath.mod(-Math.PI/2-finalAngle);
            check(Math.abs(Math.atan2(Math.sin(local-target),Math.cos(local-target)))<1e-10,"Destino Fortuna");
            var ball=WheelMath.casino(weights,winner,seed,duration,duration);
            check(Math.abs(Math.atan2(Math.sin(ball.angle()-ball.rotor()-target),Math.cos(ball.angle()-ball.rotor()-target)))<1e-10,"Destino Casino relativo");
            check(Math.abs(ball.radius()-.64)<1e-9&&Math.abs(ball.height())<1e-9,"Asentamiento de bola");
            double last=0;
            for(int t=0;t<=duration;t+=7){double angle=WheelMath.fortuna(weights,winner,seed,t,duration);check(angle>=last,"Fortuna monotónica");last=angle;var b=WheelMath.casino(weights,winner,seed,t,duration);check(b.radius()>=.639&&b.radius()<=.941&&b.height()>=0&&b.height()<=.081,"Bola dentro del cuenco");}
            check(WheelMath.fortuna(weights,winner,seed,duration+1000,duration)==finalAngle,"Salto de frames no modifica destino");
        }
        Path temp=Files.createTempDirectory("roulette-core-");
        try {
            Path file=temp.resolve("ledger.json");AtomicStore.write(file,"anterior");AtomicStore.write(file,"nuevo ✓");check(Files.readString(file).equals("nuevo ✓"),"Sustitución UTF-8");check(!Files.exists(file.resolveSibling("ledger.json.tmp")),"Temporal consumido");
            for(String phase:List.of("PREPARED","SPINNING","MUTATING","PENDING","REVIEW","SETTLED")){
                Ledger.State state=new Ledger.State();state.clean=false;Ledger.Operation operation=new Ledger.Operation();operation.state=phase;state.operations.put(operation.id,operation);
                Ledger.recoverUnclean(state);check(operation.state.equals(phase.equals("SETTLED")?"SETTLED":"REVIEW"),"Recuperación de fase "+phase);
                Ledger.recoverUnclean(state);check(operation.state.equals(phase.equals("SETTLED")?"SETTLED":"REVIEW"),"Recuperación idempotente");
            }
            Path store=temp.resolve("real.json");Ledger first=new Ledger(store);check(!first.state.clean,"Arranque marca sesión no limpia");Ledger.Operation op=new Ledger.Operation();op.player=UUID.randomUUID().toString();op.state="SPINNING";first.state.operations.put(op.id,op);first.flush();Ledger afterCrash=new Ledger(store);check(afterCrash.state.operations.get(op.id).state.equals("REVIEW"),"Caída real del diario no vuelve a resolver");
            afterCrash.state.operations.get(op.id).state="PENDING";afterCrash.state.clean=true;afterCrash.flush();Ledger afterStop=new Ledger(store);check(afterStop.state.operations.get(op.id).state.equals("PENDING"),"Parada normal conserva obligaciones");
        }finally{try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        Policy defaults=new Policy();defaults.validate();
        Policy configured=defaults.withOption("enableSpawn","true").withOption("maxPending","64");
        check(configured.enableSpawn&&configured.maxPending==64,"Opciones de comando aplicadas");
        check(!defaults.enableSpawn&&defaults.maxPending==128,"Editar no muta política vigente");
        check(defaults.withOption("allowedEntities","minecraft:zombie, minecraft:zombie,minecraft:spider").allowedEntities.size()==2,"Listas normalizadas sin duplicados");
        check(defaults.withOption("allowedEffects","ninguno").allowedEffects.isEmpty(),"Vaciar lista explícitamente");
        Policy restored=Model.GSON.fromJson(Model.GSON.toJson(configured),Policy.class);restored.validate();
        check(restored.enableSpawn&&restored.maxPending==64,"Opciones sobreviven serialización");
        rejects(()->defaults.withOption("enableSpawn","sí"));rejects(()->defaults.withOption("maxOperations","0"));rejects(()->defaults.withOption("maxPending","1025"));rejects(()->defaults.withOption("allowedEntities","minecraft:zombie,"));rejects(()->defaults.withOption("allowedEffects","id inválido"));rejects(()->defaults.withOption("desconocida","true"));
        Path resources=Path.of("src/main/resources");int jsons=0;
        try(var paths=Files.walk(resources)){for(Path file:paths.filter(p->p.toString().endsWith(".json")).toList()){JsonParser.parseString(Files.readString(file));jsons++;}}
        check(jsons>=14,"Recursos JSON presentes");
        for(String name:List.of("fortuna","bola")){
            JsonObject recipe=JsonParser.parseString(Files.readString(resources.resolve("data/ludopathyroulette/recipe/"+name+".json"))).getAsJsonObject();
            check(recipe.getAsJsonObject("result").get("id").getAsString().equals("ludopathyroulette:"+name),"Resultado receta "+name);
            check(Files.size(Path.of("docs/recipes/"+name+".png"))>100,"Imagen de receta "+name);
        }
        String report="LudopathyRoulette — verificación sin iniciar Minecraft\nComprobaciones: "+checks+"\nSorteos estadísticos: "+samples+"\nChi cuadrado (3 grados de libertad, umbral 20): "+chi+"\nTrayectorias: 10000 por rueda, con pasos y sectores extremos\nDiario: sustitución atómica, fases de caída, reapertura y parada limpia\nJSON inspeccionados: "+jsons+"\nNo se han ejecutado Minecraft, runClient, runServer ni pruebas visuales en juego.\n";
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/core-tests.txt"),report);System.out.print(report);
    }
}
