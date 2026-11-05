package com.ludopathyroulette;

import com.google.gson.*;
import com.ludopathyroulette.core.WheelMath;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;

public final class Service {
    private final MinecraftServer server;
    public final Ledger ledger;
    private final SecureRandom outcomeRandom=new SecureRandom(),visualRandom=new SecureRandom();
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Deque<Long>> accepted=new HashMap<>(),requests=new HashMap<>();
    private long clock;
    private boolean failed;
    private static class Session {
        String id=UUID.randomUUID().toString(),machine,dimension;
        BlockPos pos;
        boolean editor;
        Model.Config draft;
        int baseRevision,sequence;
        long touched;
    }
    public Service(MinecraftServer server) {
        this.server=server;
        ledger=new Ledger(server.getWorldPath(LevelResource.ROOT).resolve("data/ludopathyroulette/ledger.json"));
        for(Ledger.Machine m:ledger.state.machines.values())refresh(m);
    }
    public static boolean op(ServerPlayer p){return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
    public static boolean staff(ServerPlayer p){return op(p)||p.entityTags().contains("ludopathyroulette:admin")||(RouletteMod.policy.allowCreative&&p.isCreative());}
    private void save(){try{ledger.flush();}catch(RuntimeException e){failed=true;throw e;}}
    public void ensure(RouletteBlockEntity be) {
        if(be.getLevel()==null || be.getLevel().isClientSide())return;
        String dim=be.getLevel().dimension().identifier().toString();
        Ledger.Machine existing=ledger.state.machines.get(be.machineId);
        if(existing!=null && (!existing.dimension.equals(dim)||existing.position!=be.getBlockPos().asLong()||!existing.status.equals("PLACED"))) be.machineId=UUID.randomUUID().toString();
        Ledger.Machine m=ledger.state.machines.get(be.machineId);
        if(m==null){
            if(ledger.state.machines.size()>=4096){be.refresh("{\"name\":\"Límite de máquinas\",\"paused\":true}");return;}
            m=new Ledger.Machine();m.id=be.machineId;m.dimension=dim;m.position=be.getBlockPos().asLong();m.casino=((RouletteBlock)be.getBlockState().getBlock()).casino;
            m.config=defaults();ledger.state.machines.put(m.id,m);save();
        }
        refresh(m);
    }
    private Model.Config defaults() {
        Model.Config c=new Model.Config();c.price=Model.stack(new ItemStack(Items.GOLD_INGOT),server.registryAccess());
        for(int i=0;i<4;i++){
            Model.Segment s=new Model.Segment();s.weight=new int[]{1,24,25,50}[i];
            s.category=i==0?Model.Category.GRAND:i==3?Model.Category.PUNISH:Model.Category.NORMAL;
            if(i<3)s.item=Model.stack(new ItemStack(i==0?Items.DIAMOND:i==1?Items.BREAD:Items.IRON_NUGGET,i==1?3:1),server.registryAccess());
            else s.actions.add(new Model.Action(Model.Kind.NOTHING));c.segments.add(s);
        }return c;
    }
    public void place(ServerPlayer p,RouletteBlockEntity be,String id) {
        Ledger.Machine m=ledger.state.machines.get(id);
        if(m==null||!staff(p))return;
        // BLOCK_ENTITY_LOAD ya registró una máquina por defecto para este bloque: se descarta para no dejar huérfanos.
        Ledger.Machine placeholder=ledger.state.machines.get(be.machineId);
        if(placeholder!=null&&placeholder!=m&&placeholder.total==0&&placeholder.position==be.getBlockPos().asLong())ledger.state.machines.remove(placeholder.id);
        if(m.status.equals("PACKED")) {
            m.status="PLACED";m.dimension=p.level().dimension().identifier().toString();m.position=be.getBlockPos().asLong();be.machineId=m.id;save();
        } else { // Una copia de creativo hereda configuración, jamás identidad ni estadísticas.
            Ledger.Machine copy=new Ledger.Machine();copy.config=m.config.copy();copy.dimension=p.level().dimension().identifier().toString();copy.position=be.getBlockPos().asLong();copy.casino=((RouletteBlock)be.getBlockState().getBlock()).casino;
            if(ledger.state.machines.size()<4096){ledger.state.machines.put(copy.id,copy);be.machineId=copy.id;save();}
        }
    }
    private RouletteBlockEntity block(ServerPlayer p,BlockPos pos) {
        if(!p.level().hasChunkAt(pos)||p.distanceToSqr(Vec3.atCenterOf(pos))>64)return null;
        return p.level().getBlockEntity(pos) instanceof RouletteBlockEntity be?be:null;
    }
    public void open(ServerPlayer p,BlockPos pos,boolean editor) {
        if(failed){message(p,"Diario fuera de servicio. Consulta al operador.");return;}
        RouletteBlockEntity be=block(p,pos);if(be==null)return;ensure(be);
        if(editor&&(!staff(p)||(RouletteMod.policy.requireSneak&&!p.isShiftKeyDown()))){message(p,"Puerta cerrada: sin permiso o falta agacharse.");return;}
        Ledger.Machine m=ledger.state.machines.get(be.machineId);if(m==null)return;
        Session old=sessions.get(p.getUUID());
        Session s=new Session();s.machine=m.id;s.pos=pos.immutable();s.dimension=m.dimension;s.editor=editor;s.baseRevision=m.config.revision;s.draft=editor?m.config.copy():null;s.touched=clock;
        sessions.put(p.getUUID(),s); if(old!=null)refresh(ledger.state.machines.get(old.machine));
        sendSnapshot(p,s,true,"");refresh(m);
    }
    public void leave(ServerPlayer p){Session s=sessions.remove(p.getUUID());if(s!=null)refresh(ledger.state.machines.get(s.machine));requests.remove(p.getUUID());accepted.remove(p.getUUID());}
    public void join(ServerPlayer p) {
        long pending=ledger.state.operations.values().stream().filter(o->o.player.equals(p.getStringUUID())&&!o.state.equals("SETTLED")).count();
        if(pending>0)message(p,"Tienes "+pending+" resultado(s) pendiente(s). /ludopathyroulette pendientes");
    }
    private boolean rate(Map<UUID,Deque<Long>> map,UUID id,int limit,long window) {
        Deque<Long> q=map.computeIfAbsent(id,k->new ArrayDeque<>());while(!q.isEmpty()&&clock-q.peekFirst()>=window)q.removeFirst();
        if(q.size()>=limit)return false;q.addLast(clock);return true;
    }
    public void request(ServerPlayer p,Wire.Request r) {
        if(!rate(requests,p.getUUID(),30,20))return;
        Session s=sessions.get(p.getUUID());
        if(s==null||!s.id.equals(r.session())||r.sequence()<=s.sequence||r.sequence()>s.sequence+1000)return;
        s.sequence=r.sequence();s.touched=clock;
        try {
            if(failed)throw new IllegalArgumentException("Diario bloqueado");
            RouletteBlockEntity be=block(p,r.pos());
            if(be==null||!r.pos().equals(s.pos)||!be.machineId.equals(s.machine)||!p.level().dimension().identifier().toString().equals(s.dimension))throw new IllegalArgumentException("La sesión ya no es válida");
            if(s.editor&&!staff(p)){leave(p);throw new IllegalArgumentException("Permiso revocado");}
            Ledger.Machine m=ledger.state.machines.get(s.machine);
            if(r.action().equals("close")){leave(p);return;}
            if(r.action().equals("sync")){sendSnapshot(p,s,false,"");return;}
            JsonObject data=JsonParser.parseString(r.data()).getAsJsonObject();
            if(r.action().equals("spin")&&!s.editor){spin(p,s,m,data);return;}
            if(r.action().equals("claim")){claim(p);sendSnapshot(p,s,false,"Recogida comprobada");return;}
            if(!s.editor)throw new IllegalArgumentException("Acción no permitida");
            Model.Config previous=s.draft.copy();
            try {
                edit(p,s,m,r.action(),data);
                if(Model.GSON.toJson(s.draft).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>131072)throw new IllegalArgumentException("El borrador supera 128 KiB");
            }catch(RuntimeException error){s.draft=previous;throw error;}
            sendSnapshot(p,s,false,r.action().equals("save")?"Configuración publicada correctamente":"");
        }catch(Exception e){message(p,e.getMessage()==null?"Petición inválida":e.getMessage());if(sessions.containsKey(p.getUUID()))sendSnapshot(p,s,false,"Error: "+e.getMessage());}
    }
    private void edit(ServerPlayer p,Session s,Ledger.Machine m,String verb,JsonObject d) {
        Model.Config c=s.draft;
        switch(verb) {
            case "reload" -> {s.draft=m.config.copy();s.baseRevision=m.config.revision;}
            case "save" -> {
                if(s.baseRevision!=m.config.revision)throw new IllegalArgumentException("Conflicto: otra persona ha guardado. Recarga o conserva tu borrador.");
                Model.validate(c,server.registryAccess(),RouletteMod.policy,op(p));
                boolean lethal=c.segments.stream().flatMap(x->x.actions.stream()).anyMatch(x->x.kind==Model.Kind.KILL);
                if(lethal&&(!d.has("confirm")||!d.get("confirm").getAsString().equals(c.name)))throw new IllegalArgumentException("Escribe el nombre de la máquina para confirmar daño letal");
                Model.Config next=c.copy();next.revision=m.config.revision+1;next.published=true;m.config=next;save();s.baseRevision=next.revision;s.draft=next.copy();refresh(m);
                message(p,"Configuración publicada. Premios ilimitados, cobro consumido.");
            }
            case "general" -> {
                String name=d.get("name").getAsString();if(name.isBlank()||name.length()>32)throw new IllegalArgumentException("Nombre: 1–32 caracteres");
                int xp=d.get("xp").getAsInt();if(xp<0||xp>1000)throw new IllegalArgumentException("XP: 0–1000");
                boolean free=d.get("free").getAsBoolean();if(free&&!op(p))throw new IllegalArgumentException("Gratis: solo operador");
                c.name=name;c.xp=xp;c.free=free;c.paused=d.get("paused").getAsBoolean();c.announce=d.get("announce").getAsBoolean();if(free)c.price=JsonNull.INSTANCE;
            }
            case "add" -> {if(c.segments.size()>=12)throw new IllegalArgumentException("Máximo 12 casillas");Model.Segment row=new Model.Segment();row.item=Model.stack(new ItemStack(Items.BREAD),server.registryAccess());c.segments.add(row);}
            case "remove" -> {if(c.segments.size()<=2)throw new IllegalArgumentException("Mínimo 2 casillas");c.segments.remove(index(d,c.segments.size()));}
            case "row" -> {
                Model.Segment row=c.segments.get(index(d,c.segments.size()));int weight=d.get("weight").getAsInt();
                if(weight<1||weight>1000000)throw new IllegalArgumentException("Peso 1–1000000");row.weight=weight;
                Model.Category cat=Model.Category.valueOf(d.get("category").getAsString());
                if(cat!=row.category){row.category=cat;row.actions.clear();if(cat==Model.Category.PUNISH)row.actions.add(new Model.Action(Model.Kind.NOTHING));else if(row.item.isJsonNull())row.item=Model.stack(new ItemStack(Items.BREAD),server.registryAccess());}
            }
            case "equal" -> c.segments.forEach(row->row.weight=1);
            case "percent" -> {
                List<String> values=new ArrayList<>();for(JsonElement e:d.getAsJsonArray("values"))values.add(e.getAsString());
                if(values.size()!=c.segments.size())throw new IllegalArgumentException("Faltan porcentajes");int[] weights=WheelMath.percentages(values);for(int i=0;i<weights.length;i++)c.segments.get(i).weight=weights[i];
            }
            case "action" -> {
                Model.Segment row=c.segments.get(index(d,c.segments.size()));if(row.category!=Model.Category.PUNISH)throw new IllegalArgumentException("Selecciona un castigo");
                int a=d.get("slot").getAsInt();if(a<0||a>2)throw new IllegalArgumentException("Acción inválida");
                if(d.has("remove")&&d.get("remove").getAsBoolean()){if(a<row.actions.size())row.actions.remove(a);return;}
                Model.Kind kind=Model.Kind.valueOf(d.get("kind").getAsString());
                Model.Action action=a<row.actions.size()?row.actions.get(a):new Model.Action();action.kind=kind;action.factor=d.get("factor").getAsInt();action.uses=d.get("uses").getAsInt();action.count=d.get("count").getAsInt();
                if(action.factor<150||action.factor>1000||action.uses<1||action.uses>5||action.count<1||action.count>3)throw new IllegalArgumentException("Parámetros fuera de límites");
                if(a<row.actions.size())row.actions.set(a,action);else if(a==row.actions.size())row.actions.add(action);else throw new IllegalArgumentException("Añade acciones en orden");
            }
            case "sample" -> {
                int slot=d.get("inventory").getAsInt();if(slot<0||slot>=36)throw new IllegalArgumentException("Slot inválido");
                ItemStack stack=p.getInventory().getItem(slot).copy();if(stack.isEmpty())throw new IllegalArgumentException("Ese slot está vacío");
                JsonElement json=Model.stack(stack,server.registryAccess());if(json.toString().length()>8192)throw new IllegalArgumentException("Objeto demasiado grande");
                int target=d.get("target").getAsInt();
                if(target==-1){c.price=json;c.free=false;}
                else {Model.Segment row=c.segments.get(index(d,c.segments.size()));if(target==-2)row.item=json;else {if(target<0||target>=row.actions.size())throw new IllegalArgumentException("Elige acción primero");row.actions.get(target).source=json;}}
            }
            case "clearprice" -> c.price=JsonNull.INSTANCE;
            default -> throw new IllegalArgumentException("Acción desconocida");
        }
    }
    private int index(JsonObject d,int size){int i=d.get("row").getAsInt();if(i<0||i>=size)throw new IllegalArgumentException("Fila inválida");return i;}
    private Ledger.Operation activeMachine(String id){return ledger.state.operations.values().stream().filter(o->o.machine.equals(id)&&(o.state.equals("SPINNING")||o.state.equals("PREPARED"))).findFirst().orElse(null);}
    private boolean blocked(ServerPlayer p){return ledger.state.operations.values().stream().anyMatch(o->o.player.equals(p.getStringUUID())&&!o.state.equals("SETTLED")&&!(o.state.equals("PENDING")&&onlyItems(o)));}
    private boolean onlyItems(Ledger.Operation o){return o.actions.subList(Math.min(o.actionIndex,o.actions.size()),o.actions.size()).stream().allMatch(a->a.kind==Model.Kind.GIVE_ITEM||a.kind==Model.Kind.NOTHING);}
    private Ledger.Surcharge surcharge(ServerPlayer p,String machine){return ledger.state.surcharges.getOrDefault(p.getStringUUID()+"/"+machine,new Ledger.Surcharge());}
    private void spin(ServerPlayer p,Session s,Ledger.Machine m,JsonObject d) {
        if(p.isSpectator()||!p.isAlive())throw new IllegalArgumentException("No puedes jugar en este estado");
        Model.Config c=m.config;
        if(!c.published||c.paused)throw new IllegalArgumentException("Máquina sin configurar o en mantenimiento");
        Model.validate(c,server.registryAccess(),RouletteMod.policy,true);
        int revision=d.get("revision").getAsInt(),factor=d.get("factor").getAsInt();
        Ledger.Surcharge sc=surcharge(p,m.id);
        if(revision!=c.revision||factor!=sc.factor){sendSnapshot(p,s,false,"El precio cambió. Revisa y vuelve a girar.");return;}
        if(activeMachine(m.id)!=null)throw new IllegalArgumentException("Máquina ocupada");
        if(blocked(p))throw new IllegalArgumentException("Tienes una tirada o castigo pendiente");
        if(ledger.state.operations.values().stream().filter(o->!o.state.equals("SETTLED")).count()>=RouletteMod.policy.maxOperations)throw new IllegalArgumentException("Servidor ocupado: demasiadas obligaciones pendientes");
        if(ledger.state.operations.values().stream().filter(o->o.player.equals(p.getStringUUID())&&!o.state.equals("SETTLED")).count()>=RouletteMod.policy.maxPending)throw new IllegalArgumentException("Recoge tus premios pendientes");
        boolean lethal=c.segments.stream().flatMap(row->row.actions.stream()).anyMatch(a->a.kind==Model.Kind.KILL);
        if(lethal&&(!d.has("lethal")||!d.get("lethal").getAsBoolean()))throw new IllegalArgumentException("Confirma el aviso de daño letal antes de pagar");
        float impulse=d.has("impulse")?d.get("impulse").getAsFloat():.5f;
        if(!Float.isFinite(impulse)||impulse<0||impulse>1)throw new IllegalArgumentException("Impulso inválido");
        Deque<Long> recent=accepted.computeIfAbsent(p.getUUID(),k->new ArrayDeque<>());
        while(!recent.isEmpty()&&clock-recent.peekFirst()>=1200)recent.removeFirst();
        if(recent.size()>=20||(!recent.isEmpty()&&clock-recent.peekLast()<60))throw new IllegalArgumentException("Espera antes de volver a girar");
        ItemStack price=Model.stack(c.price,server.registryAccess());int quantity=WheelMath.price(price.getCount(),sc.factor),xp=WheelMath.price(c.xp,sc.factor);
        int available=0;for(int i=0;i<36;i++)if(ItemStack.isSameItemSameComponents(price,p.getInventory().getItem(i)))available+=p.getInventory().getItem(i).getCount();
        if((!price.isEmpty()&&available<quantity)||p.experienceLevel<xp)throw new IllegalArgumentException("No tienes suficientes objetos o niveles");
        Ledger.Operation o=new Ledger.Operation();o.player=p.getStringUUID();o.machine=m.id;o.config=c.copy();o.winner=WheelMath.select(c.weights(),outcomeRandom.nextLong(WheelMath.sum(c.weights())));o.seed=visualRandom.nextLong();o.duration=m.casino?140+visualRandom.nextInt(61):90+Math.round(impulse*90);o.remaining=o.duration;o.started=clock;
        Model.Segment winner=c.segments.get(o.winner);
        if(winner.category==Model.Category.PUNISH)o.actions=Model.GSON.fromJson(Model.GSON.toJson(winner.actions),new com.google.gson.reflect.TypeToken<List<Model.Action>>(){}.getType());
        else {Model.Action a=new Model.Action(Model.Kind.GIVE_ITEM);a.source=winner.item.deepCopy();o.actions.add(a);}
        o.actions.sort(Comparator.comparingInt(a->switch(a.kind){case SPAWN->0;case GIVE_ITEM->1;case EFFECT->2;case SURCHARGE->3;default->4;}));
        ledger.state.operations.put(o.id,o);save(); // PREPARED implica revisión tras caída, nunca cobro/reembolso automático.
        int left=quantity;
        for(int i=0;i<36&&left>0&&!price.isEmpty();i++){ItemStack stack=p.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(price,stack)){int n=Math.min(left,stack.getCount());stack.shrink(n);left-=n;}}
        if(xp>0)p.giveExperienceLevels(-xp);
        if(sc.uses>0){sc.uses--;if(sc.uses==0)ledger.state.surcharges.remove(p.getStringUUID()+"/"+m.id);}
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        o.note="Cobrado: "+quantity+" objetos, "+xp+" niveles";o.state="SPINNING";save();ledger.audit(o,o.note);recent.addLast(clock);
        publish(m);sendSnapshot(p,s,false,"");
    }
    public void tick() {
        if(!server.tickRateManager().runsNormally())return;
        clock++;
        if(failed)return;
        try {
            for(Ledger.Operation o:List.copyOf(ledger.state.operations.values())) {
                if(o.state.equals("SPINNING")) {
                    if(--o.remaining<=0){o.state="PENDING";count(o);save();}
                    else if(clock%20==0){Ledger.Machine m=ledger.state.machines.get(o.machine);if(m!=null)publish(m);}
                }
                if(o.state.equals("PENDING")) {
                    ServerPlayer p=server.getPlayerList().getPlayer(UUID.fromString(o.player));
                    if(p==null||!p.isAlive()||p.isSpectator())continue;
                    if(!onlyItems(o)&&o.warning>0) {
                        if(o.warning==100)message(p,"Resultado pendiente: "+Model.describe(o.config.segments.get(o.winner),server.registryAccess())+". Se aplicará en 5 s.");
                        o.warning--;continue;
                    }
                    if(clock%20==0)executeNext(o,p);
                }
            }
            if(clock%20==0) {
                for(var entry:List.copyOf(sessions.entrySet())) {
                    ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());Session s=entry.getValue();
                    if(p==null||clock-s.touched>1200||block(p,s.pos)==null||(s.editor&&!staff(p))) {sessions.remove(entry.getKey());refresh(ledger.state.machines.get(s.machine));continue;}
                    if(!s.editor)sendSnapshot(p,s,false,"");
                }
            }
            if(clock%100==0)save();
        }catch(Exception e){failed=true;System.err.println("LudopathyRoulette bloqueado: "+e);}
    }
    private void count(Ledger.Operation o) {
        if(o.counted)return;o.counted=true;
        Ledger.Machine m=ledger.state.machines.get(o.machine);if(m==null)return;
        m.total++;Model.Category category=o.config.segments.get(o.winner).category;
        switch(category){case GRAND->m.grand++;case NORMAL->m.normal++;case PUNISH->m.punish++;}
        String description=Model.describe(o.config.segments.get(o.winner),server.registryAccess());
        m.lastResult=description.substring(0,description.offsetByCodePoints(0,Math.min(96,description.codePointCount(0,description.length()))));
        JsonObject frame=new JsonObject();frame.addProperty("casino",m.casino);frame.addProperty("revision",o.config.revision);
        JsonArray rows=new JsonArray();for(Model.Segment segment:o.config.segments){JsonObject row=new JsonObject();row.addProperty("weight",segment.weight);row.addProperty("category",segment.category.name());rows.add(row);}frame.add("rows",rows);
        JsonObject spin=new JsonObject();spin.addProperty("id",o.id);spin.addProperty("winner",o.winner);spin.addProperty("seed",o.seed);spin.addProperty("duration",o.duration);spin.addProperty("elapsed",o.duration);frame.add("spin",spin);m.lastFrame=frame;
        if(category==Model.Category.GRAND&&o.config.announce)server.getPlayerList().broadcastSystemMessage(Component.literal("★ Gran premio en "+o.config.name+": "+Model.describe(o.config.segments.get(o.winner),server.registryAccess())),false);
        publish(m);
    }
    private void executeNext(Ledger.Operation o,ServerPlayer p) {
        if(o.actionIndex>=o.actions.size()){settle(o,p);return;}
        Model.Action a=o.actions.get(o.actionIndex);
        Ledger.Machine m=ledger.state.machines.get(o.machine);
        if(a.kind==Model.Kind.SPAWN&&RouletteMod.policy.enableSpawn) {
            ServerLevel l=machineLevel(m);
            if(m!=null&&m.status.equals("PLACED")&&l!=null) {
                if(!p.level().dimension().identifier().toString().equals(m.dimension)||p.distanceToSqr(Vec3.atCenterOf(BlockPos.of(m.position)))>64) {
                    if(!o.notified){message(p,"Vuelve junto a "+o.config.name+" para resolver la invocación pendiente.");o.notified=true;save();}return;
                }
            }
        }
        if(a.kind==Model.Kind.GIVE_ITEM) {
            ItemStack remaining=Model.stack(a.source,server.registryAccess());
            if(remaining.isEmpty()){advance(o);return;}
            // No mutar si no hay espacio: el buzón permanece estable.
            int capacity=0;
            for(int i=0;i<36;i++){
                ItemStack s=p.getInventory().getItem(i);
                if(s.isEmpty())capacity+=remaining.getMaxStackSize();
                else if(ItemStack.isSameItemSameComponents(s,remaining))capacity+=Math.max(0,s.getMaxStackSize()-s.getCount());
            }
            if(capacity==0){if(!o.notified){message(p,"Premio guardado: libera espacio y usa /ludopathyroulette recoger.");o.notified=true;save();}return;}
            o.state="MUTATING";save();
            p.getInventory().add(remaining);p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
            a.source=Model.stack(remaining,server.registryAccess());
            if(remaining.isEmpty())o.actionIndex++;
            o.state="PENDING";o.notified=false;save();
            ledger.audit(o,"Entrega parcial o completa, acción "+o.actionIndex);
            if(o.actionIndex>=o.actions.size())settle(o,p);
            return;
        }
        o.state="MUTATING";save();
        switch(a.kind) {
            case NOTHING -> {}
            case EFFECT -> {
                PotionContents potion=Model.stack(a.source,server.registryAccess()).get(DataComponents.POTION_CONTENTS);
                if(potion==null)throw new IllegalStateException("Fuente de efectos ausente");
                for(MobEffectInstance e:potion.getAllEffects()) {
                    String id=e.getEffect().unwrapKey().orElseThrow().identifier().toString();
                    if(RouletteMod.policy.allowedEffects.contains(id)&&!e.getEffect().value().isInstantaneous())p.addEffect(new MobEffectInstance(e));
                    else o.note+="; efecto omitido por política: "+id;
                }
            }
            case SURCHARGE -> {
                String key=o.player+"/"+o.machine;Ledger.Surcharge sc=ledger.state.surcharges.computeIfAbsent(key,k->new Ledger.Surcharge());
                sc.factor=Math.min(1000,sc.factor*a.factor/100);sc.uses=Math.max(sc.uses,a.uses);
            }
            case KILL -> {
                if(RouletteMod.policy.enableKill) {
                    var holder=server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,RouletteMod.id("ruleta")));
                    p.hurtServer((ServerLevel)p.level(),new DamageSource(holder),1000000f);
                }else o.note+="; daño letal omitido por política";
            }
            case SPAWN -> spawn(o,p,a,m);
            case GIVE_ITEM -> throw new AssertionError();
        }
        advance(o);if(o.actionIndex>=o.actions.size())settle(o,p);
    }
    private void spawn(Ledger.Operation o,ServerPlayer p,Model.Action a,Ledger.Machine m) {
        ServerLevel l=machineLevel(m);
        if(!RouletteMod.policy.enableSpawn||m==null||!m.status.equals("PLACED")||l==null){o.note+="; invocación omitida: origen o capacidad no disponibles";return;}
        ItemStack source=Model.stack(a.source,server.registryAccess());
        if(!(source.getItem() instanceof SpawnEggItem))throw new IllegalArgumentException("Huevo incompatible");
        EntityType<?> type=SpawnEggItem.getType(source);
        if(!RouletteMod.policy.allowedEntities.contains(EntityType.getKey(type).toString())){o.note+="; entidad no permitida";return;}
        int generated=0;
        for(int n=0;n<a.count;n++) {
            for(int attempt=0;attempt<32;attempt++) {
                double angle=attempt*2.399963229728653;double radius=2+(attempt%3);
                BlockPos pos=BlockPos.containing(p.getX()+Math.cos(angle)*radius,p.getY(),p.getZ()+Math.sin(angle)*radius);
                if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos)||!l.getBlockState(pos.below()).isSolidRender()||!p.mayInteract(l,pos))continue;
                Entity entity=type.create(l,EntitySpawnReason.TRIGGERED);
                if(entity==null)break;
                entity.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                if(!l.noCollision(entity)||l.containsAnyLiquid(entity.getBoundingBox())){entity.discard();continue;}
                // No se copia NBT del huevo; caducidad persistente en tags y sin recompensas por muerte.
                entity.addTag("ludopathyroulette:spawn");entity.addTag("lr_expire:"+(l.getGameTime()+2400));
                if(entity instanceof LivingEntity living)for(EquipmentSlot slot:EquipmentSlot.values())living.setItemSlot(slot,ItemStack.EMPTY);
                if(entity instanceof Mob mob)for(EquipmentSlot slot:EquipmentSlot.values())mob.setDropChance(slot,0);
                if(l.addFreshEntity(entity))generated++;else entity.discard();break;
            }
        }
        o.note+="; criaturas creadas "+generated+"/"+a.count;
    }
    private void advance(Ledger.Operation o){o.actionIndex++;o.state="PENDING";o.notified=false;save();ledger.audit(o,"Acción confirmada "+o.actionIndex);}
    private void settle(Ledger.Operation o,ServerPlayer p) {
        o.state="SETTLED";count(o);save();ledger.audit(o,"Resultado liquidado");
        message(p,"Resultado de "+o.config.name+": "+Model.describe(o.config.segments.get(o.winner),server.registryAccess())+" · resuelto. "+o.note);
        Session s=sessions.get(p.getUUID());if(s!=null)sendSnapshot(p,s,false,"Resultado confirmado · "+Model.describe(o.config.segments.get(o.winner),server.registryAccess()));
        refresh(ledger.state.machines.get(o.machine));
        // Solo se eliminan operaciones liquidadas: ningún mensaje cliente permite reejecutarlas por id.
        if(ledger.state.operations.size()>2048){Iterator<Ledger.Operation> it=ledger.state.operations.values().iterator();while(ledger.state.operations.size()>2048&&it.hasNext())if(it.next().state.equals("SETTLED"))it.remove();save();}
    }
    public void claim(ServerPlayer p) {
        if(failed)return;
        for(Ledger.Operation o:List.copyOf(ledger.state.operations.values()))if(o.player.equals(p.getStringUUID())&&o.state.equals("PENDING")&&onlyItems(o))executeNext(o,p);
    }
    public void pending(ServerPlayer p) {
        int n=0;for(Ledger.Operation o:ledger.state.operations.values())if(o.player.equals(p.getStringUUID())&&!o.state.equals("SETTLED")){n++;message(p,o.id+" · "+o.state+" · "+Model.describe(o.config.segments.get(o.winner),server.registryAccess()));}
        if(n==0)message(p,"No tienes resultados pendientes.");
    }
    private ServerLevel machineLevel(Ledger.Machine m){return m==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(m.dimension)));}
    private JsonObject publicView(Ledger.Machine m) {
        JsonObject out=new JsonObject();Model.Config c=m.config;
        out.addProperty("machine",m.id);out.addProperty("name",c.name);out.addProperty("casino",m.casino);out.addProperty("revision",c.revision);out.addProperty("published",c.published);out.addProperty("paused",c.paused);out.addProperty("total",m.total);out.addProperty("grand",m.grand);out.addProperty("normal",m.normal);out.addProperty("punish",m.punish);out.addProperty("serverTick",clock);
        JsonArray rows=new JsonArray();
        Ledger.Operation active=activeMachine(m.id);Model.Config visual=active==null?c:active.config;
        for(Model.Segment row:visual.segments){JsonObject r=new JsonObject();r.addProperty("category",row.category.name());r.addProperty("weight",row.weight);try{r.addProperty("description",Model.describe(row,server.registryAccess()));}catch(Exception e){r.addProperty("description","Datos incompatibles");}rows.add(r);}
        out.add("rows",rows);
        out.addProperty("lastResult",m.lastResult);if(m.lastFrame!=null)out.add("lastFrame",m.lastFrame);
        if(active!=null){JsonObject spin=new JsonObject();spin.addProperty("id",active.id);spin.addProperty("winner",active.winner);spin.addProperty("seed",active.seed);spin.addProperty("duration",active.duration);spin.addProperty("elapsed",active.duration-active.remaining);spin.addProperty("started",clock-(active.duration-active.remaining));out.add("spin",spin);}
        out.addProperty("hatch",sessions.values().stream().anyMatch(s->s.editor&&s.machine.equals(m.id)));
        return out;
    }
    private void sendSnapshot(ServerPlayer p,Session s,boolean open,String message) {
        Ledger.Machine m=ledger.state.machines.get(s.machine);if(m==null)return;
        JsonObject out=publicView(m);out.addProperty("mode",s.editor?"editor":"play");out.addProperty("open",open);out.addProperty("session",s.id);out.addProperty("sequence",s.sequence);out.addProperty("message",message);
        if(s.editor){out.add("draft",Model.GSON.toJsonTree(s.draft));out.addProperty("conflict",m.config.revision!=s.baseRevision);}
        Model.Config c=m.config;Ledger.Surcharge sc=surcharge(p,m.id);out.addProperty("factor",sc.factor);out.addProperty("uses",sc.uses);
        ItemStack price=Model.stack(c.price,server.registryAccess());int quantity=WheelMath.price(price.getCount(),sc.factor),xp=WheelMath.price(c.xp,sc.factor);
        out.addProperty("price",c.free?"GRATIS":(price.isEmpty()?"":quantity+" × "+price.getHoverName().getString())+(xp>0?" + "+xp+" niveles":""));
        int available=0;for(int i=0;i<36;i++)if(ItemStack.isSameItemSameComponents(price,p.getInventory().getItem(i)))available+=p.getInventory().getItem(i).getCount();
        out.add("priceItem",c.price);out.addProperty("canPay",(price.isEmpty()||available>=quantity)&&p.experienceLevel>=xp);
        Ledger.Operation current=activeMachine(m.id);out.addProperty("ownSpin",current!=null&&current.player.equals(p.getStringUUID()));
        out.addProperty("blocked",blocked(p)||failed);out.addProperty("lethal",c.segments.stream().flatMap(row->row.actions.stream()).anyMatch(a->a.kind==Model.Kind.KILL));
        // Detalles del premio solo al participante; el paquete público conserva textos cortos.
        if(!s.editor){JsonArray items=new JsonArray();Ledger.Operation active=activeMachine(m.id);Model.Config visual=active==null?c:active.config;for(Model.Segment row:visual.segments)items.add(row.category==Model.Category.PUNISH?JsonNull.INSTANCE:row.item);out.add("items",items);}
        ServerPlayNetworking.send(p,new Wire.View(s.pos,out.toString()));
    }
    private void refresh(Ledger.Machine m) {
        if(m==null)return;ServerLevel l=machineLevel(m);BlockPos pos=BlockPos.of(m.position);
        if(l!=null&&l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof RouletteBlockEntity be&&be.machineId.equals(m.id))be.refresh(publicView(m).toString());
    }
    private void publish(Ledger.Machine m) {
        if(m==null)return;refresh(m);JsonObject view=publicView(m);view.addProperty("mode","world");
        for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.level().dimension().identifier().toString().equals(m.dimension)&&p.distanceToSqr(Vec3.atCenterOf(BlockPos.of(m.position)))<1024)ServerPlayNetworking.send(p,new Wire.View(BlockPos.of(m.position),view.toString()));
    }
    public void pause(ServerPlayer p,BlockPos pos,boolean paused) {
        if(!staff(p))throw new IllegalArgumentException("Sin permiso");RouletteBlockEntity be=block(p,pos);if(be==null)throw new IllegalArgumentException("Mira una máquina a menos de 8 bloques");
        Ledger.Machine m=ledger.state.machines.get(be.machineId);m.config.paused=paused;m.config.revision++;save();publish(m);
    }
    public void stats(ServerPlayer p,BlockPos pos){RouletteBlockEntity be=block(p,pos);if(be==null)return;Ledger.Machine m=ledger.state.machines.get(be.machineId);message(p,m.config.name+": "+m.total+" resultados · GRAND "+m.grand+" · NORMAL "+m.normal+" · PUNISH "+m.punish);}
    public void retire(ServerPlayer p,BlockPos pos) {
        if(!staff(p))throw new IllegalArgumentException("Sin permiso");RouletteBlockEntity be=block(p,pos);if(be==null)throw new IllegalArgumentException("Mira una máquina");
        Ledger.Machine m=ledger.state.machines.get(be.machineId);
        if(activeMachine(m.id)!=null||ledger.state.operations.values().stream().anyMatch(o->o.machine.equals(m.id)&&!o.state.equals("SETTLED"))||sessions.values().stream().anyMatch(s->s.machine.equals(m.id)&&s.editor))throw new IllegalArgumentException("Cierra editores y resuelve obligaciones antes de retirar");
        if(p.getInventory().getFreeSlot()<0)throw new IllegalArgumentException("Necesitas un hueco libre");
        ItemStack item=new ItemStack(m.casino?RouletteMod.BOLA:RouletteMod.FORTUNA);CompoundTag tag=new CompoundTag();tag.putString("RouletteId",m.id);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        m.status="PACKED";save();p.level().removeBlock(pos,false);p.getInventory().add(item);p.inventoryMenu.broadcastChanges();message(p,"Máquina retirada con configuración e identidad. Las muestras no son existencias.");
    }
    public void diagnose(ServerPlayer p,String id){Ledger.Operation o=ledger.state.operations.get(id);message(p,o==null?"Operación desconocida":Model.GSON.toJson(o));}
    public void resolve(ServerPlayer p,String id,String resolution) {
        if(!op(p))throw new IllegalArgumentException("Solo operador");Ledger.Operation o=ledger.state.operations.get(id);
        if(o==null||!o.state.equals("REVIEW"))throw new IllegalArgumentException("No está en revisión");
        if(resolution.equals("confirmar")){o.state="SETTLED";count(o);}
        else if(resolution.equals("continuar")){o.state="PENDING";o.warning=100;}
        else throw new IllegalArgumentException("Usa confirmar (no repetir) o continuar (acciones pendientes verificadas)");
        o.note+="; resuelto por "+p.getStringUUID()+": "+resolution;save();ledger.audit(o,o.note);message(p,"Resolución registrada. Reembolsos, si corresponden, se realizan manualmente después de verificar cobro.");
    }
    public void close(){if(!failed){server.getPlayerList().saveAll();ledger.state.clean=true;save();}sessions.clear();}
    public static void message(ServerPlayer p,String text){p.sendSystemMessage(Component.literal(text==null?"Petición inválida":text));}
}
