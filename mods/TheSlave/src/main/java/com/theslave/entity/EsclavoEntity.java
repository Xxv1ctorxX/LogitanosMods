package com.theslave.entity;

import com.theslave.Aptitudes;
import com.theslave.Config;
import com.theslave.Registro;
import com.theslave.ai.Conocimiento;
import com.theslave.ai.Planificador;
import com.theslave.net.Payloads;
import com.theslave.persistencia.TransferenciasDurables;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModItems;
import com.theslave.block.TumbaBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;
import com.theslave.ai.BarridoMenas;
import com.theslave.ai.PresupuestoEscaneo;
import com.theslave.ai.ExtraccionSegura;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * El esclavo: recibe un item como encargo, lo planifica con {@link Planificador}, lo ejecuta (minar, craftear,
 * fundir) con su mochila y se planta junto a su dueño con la carga hasta que un latigazo la cobra.
 */
public class EsclavoEntity extends PathfinderMob {
	public enum Estado {
		OCIOSO, RECIBIENDO, PLANIFICANDO, TRABAJANDO, VOLVIENDO, INFORMANDO, ESPERANDO, DESCANSANDO
	}

	private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ESTADO = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<ItemStack> REF = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> TIENE_DUENO = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> VARIANTE2 = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);

	private static final EntityDataAccessor<Boolean> CADENA = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CUERPO_RETIRADO = SynchedEntityData.defineId(EsclavoEntity.class,EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<BlockPos> ANCLA = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FUSION_LADO = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUSION_TICK = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUSION_FRENTE = SynchedEntityData.defineId(EsclavoEntity.class, EntityDataSerializers.INT);
    public void fusionVisual(int lado,int tick,Direction frente) {
        entityData.set(FUSION_LADO,lado); entityData.set(FUSION_TICK,tick); entityData.set(FUSION_FRENTE,frente.get2DDataValue());
    }
    public int fusionLado() {return entityData.get(FUSION_LADO);}
    public int fusionTick() {return entityData.get(FUSION_TICK);}
    public Direction fusionFrente() {return Direction.from2DDataValue(entityData.get(FUSION_FRENTE));}
	public void cadenaRitual(BlockPos ancla) { entityData.set(ANCLA, ancla.immutable()); entityData.set(CADENA, true); }
	public boolean tieneCadena() { return entityData.get(CADENA); }
	public BlockPos anclaCadena() { return entityData.get(ANCLA); }

	public static final int VARIANTES = 3;

	private UUID dueno;
	/** Id del vínculo en el {@link Registro}; sobrevive a la resurrección aunque cambie el UUID de la entidad. */
	private UUID vinculo;
    private UUID operacionRitual;
    private boolean originalRitual;
    private boolean custodiaRitual;
    private Vec3 anclaCustodia;
    private BlockPos ultimoSueloSeguro;
    private String dimensionSueloSeguro;
    public BlockPos ultimoSueloSeguro() { return ultimoSueloSeguro; }
    public String dimensionSueloSeguro() { return dimensionSueloSeguro; }
	private final Inv inv = new Inv();
	private float hambre = 20f;
	private int moral = 100;

	// orden
	private int techo;
	private int latigazos;
	private int ventana;
	private int temporizador;
	private String ultimoItem = "";
	private int ultimoTecho;
	private boolean exito;
	private String motivoFallo = "";
	private boolean muertoRegistrado;

	// ejecución
	private List<Planificador.Paso> pasos = new ArrayList<>();
	private int paso;
	private int pasoBase = -1;
    private Mantenimiento mantenimiento=new Mantenimiento();
    private boolean encargoActivo;
    private boolean replanificarTrasCarga;
    private int regresoAtasco;
    private double distanciaCasa=Double.MAX_VALUE;
	private int replans;
	private int demora;
    private int energiaFundicion;
    public int energiaFundicion() {return energiaFundicion;}
    public void energiaFundicion(int energia) {energiaFundicion=Math.clamp(energia,0,512000);}
	private BlockPos objetivo;
	private BlockPos minando;
	private int minaTick;
	private int minaTotal;
	private final Set<Long> vetados = new LinkedHashSet<>();
	private List<BlockPos> candidatos = new ArrayList<>();
	private BarridoMenas barrido;
    private BlockState estadoMinando;
    private int grieta=-1;
    private static final WeakHashMap<net.minecraft.server.MinecraftServer,PresupuestoEscaneo> ESCANEOS=new WeakHashMap<>();
	private boolean escFin;
	private BlockPos escCentro;
	private int atasco;
	private double ultimaDist;
	private int aceleracion;
	private int aceleraTicks;
	private int cooldownGesto;

	// órdenes de acompañamiento y ceremonias
	private boolean sigue;
	private boolean enRito;
	private Vec3 posicionAntesRito;
	private boolean seguiaAntesRito;
	private int ordenTick;
	private int recupTick;
	private float[] merito;

	public EsclavoEntity(EntityType<? extends EsclavoEntity> type, Level level) {
		super(type, level);
	}

    /** El WAL conserva la propiedad de cuerpo e inventario hasta el ACK de almacenamiento. */
    public void marcarOperacion(UUID operacion,boolean original) {
        if(operacion==null)throw new IllegalArgumentException("Falta la operacion ritual");
        if(level() instanceof ServerLevel sl)cancelarMina(sl);
        operacionRitual=operacion;originalRitual=original;custodiaRitual=true;
        entityData.set(CUERPO_RETIRADO,original);
        anclaCustodia=position();sigue=false;getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);setNoGravity(true);
    }
    public UUID operacionRitual() { return operacionRitual; }
    public boolean originalRitual() { return originalRitual; }
    public boolean enCustodiaRitual() { return custodiaRitual; }
    public boolean ocultoPorCustodia() {return entityData.get(CUERPO_RETIRADO);}
    public void liberarCustodiaRitual() {
        entityData.set(CUERPO_RETIRADO,false);
        custodiaRitual=false;anclaCustodia=null;setNoGravity(false);setDeltaMovement(Vec3.ZERO);
        enRito(false);
    }
    private boolean duenoEnCustodia() { return com.theslave.persistencia.CerrojosJugador.ocupada(dueno); }

    @Override public void tick() {
        if(level() instanceof ServerLevel nivel && tickCount%20==0 && !custodiaRitual && !duenoEnCustodia()
                && onGround() && nivel.hasChunkAt(blockPosition()) && nivel.getFluidState(blockPosition()).isEmpty()
                && nivel.getBlockState(blockPosition().below()).isFaceSturdy(nivel,blockPosition().below(),Direction.UP)) {
            ultimoSueloSeguro=blockPosition().immutable();dimensionSueloSeguro=nivel.dimension().identifier().toString();
        }
        if(!level().isClientSide() && custodiaRitual) {
            if(anclaCustodia!=null)setPos(anclaCustodia);
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);setNoGravity(true);
            return;
        }
        if(!level().isClientSide() && duenoEnCustodia()) {
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.tick();
    }
    @Override public boolean isPushable() { return !custodiaRitual && !duenoEnCustodia() && super.isPushable(); }
    @Override public boolean hurtServer(ServerLevel nivel,DamageSource fuente,float cantidad) {
        return !custodiaRitual && !duenoEnCustodia() && super.hurtServer(nivel,fuente,cantidad);
    }

	public static AttributeSupplier.Builder atributos() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANTE, 0);
		builder.define(ESTADO, 0);
		builder.define(REF, ItemStack.EMPTY);
		builder.define(TIENE_DUENO, false);
		builder.define(CADENA, false);
        builder.define(CUERPO_RETIRADO,false);
		builder.define(ANCLA, BlockPos.ZERO);
        builder.define(FUSION_LADO,0); builder.define(FUSION_TICK,0); builder.define(FUSION_FRENTE,0);
		builder.define(VARIANTE2, -1);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- accesores

	public int variante() {
		return entityData.get(VARIANTE);
	}

	/** Vuelve de la tumba: mismo dueño, nombre y aspecto, con la mochila vacía y algo de hambre. */
	public void restaurar(UUID dueno, UUID vinculo, String nombre, int variante) {
		this.dueno = dueno;
		this.vinculo = vinculo;
		entityData.set(TIENE_DUENO, true);
		entityData.set(VARIANTE, variante);
		setCustomName(Component.literal(nombre));
		setCustomNameVisible(true);
		hambre = 12f;
		moral = 70;
		estado(Estado.OCIOSO);
		setPersistenceRequired();
	}

	/** Aspecto de la segunda mitad si es un fusionado; -1 si no. */
	public int variante2() {
		return entityData.get(VARIANTE2);
	}

	public boolean fusionado() {
		return variante2() >= 0;
	}

	public boolean enRito() {
		return enRito;
	}

	public void enRito(boolean v) {
        if (v && !enRito) {
            posicionAntesRito = position();
            seguiaAntesRito = sigue;
        }
		enRito = v;
		if (!v) { entityData.set(CADENA, false); entityData.set(FUSION_LADO,0); }
		if (v) {
			sigue = false;
			getNavigation().stop();
		}
	}

	public void sigue(boolean v) {
		sigue = v;
	}

	public boolean sigue() {
		return sigue;
	}

	public float[] meritos() {
		if (merito == null && dueno != null) {
			merito = Registro.meritos(dueno, vinculo(), getRandom());
		}
		return merito;
	}

	/** Nace de una invocación: vinculado de inicio, con aptitudes recién repartidas y sin hambre. */
	public void nacer(UUID dueno, UUID vinculo, int variante) {
		this.dueno = dueno;
		this.vinculo = vinculo;
		entityData.set(TIENE_DUENO, true);
		entityData.set(VARIANTE, variante);
		merito = Registro.meritos(dueno, vinculo, getRandom());
		estado(Estado.OCIOSO);
		setPersistenceRequired();
	}

    /** Preparacion del resultado antes del WAL, sin crear ni mutar un vinculo en Registro. */
    public void nacer(UUID dueno,UUID vinculo,int variante,float[] meritos) {
        this.dueno=dueno;this.vinculo=vinculo;
        entityData.set(TIENE_DUENO,true);entityData.set(VARIANTE,variante);
        this.merito=meritos.clone();
        estado(Estado.OCIOSO);setPersistenceRequired();
    }

	/** Aplica lo que el registro recuerda del vínculo: fusión y aptitudes. */
	public void aplicarVinculo(Registro.Vinculo v) {
		if (v == null) {
			return;
		}
		entityData.set(VARIANTE2, v.variante2);
		merito = v.merito;
	}

	/** Para el fusionado: aspecto de las dos mitades. */
	public void fusionar(UUID dueno, UUID vinculo, String nombre, int varianteIzq, int varianteDer, float[] m) {
		this.dueno = dueno;
		this.vinculo = vinculo;
		entityData.set(TIENE_DUENO, true);
		entityData.set(VARIANTE, varianteIzq);
		entityData.set(VARIANTE2, varianteDer);
		merito = m;
		setCustomName(Component.literal(nombre));
		setCustomNameVisible(true);
		estado(Estado.OCIOSO);
		setPersistenceRequired();
	}

	public void tirarVariante(RandomSource random) {
		entityData.set(VARIANTE, random.nextInt(VARIANTES));
	}

	public Estado estado() {
		Estado[] v = Estado.values();
		return v[Math.max(0, Math.min(v.length - 1, entityData.get(ESTADO)))];
	}

	private void estado(Estado e) {
		entityData.set(ESTADO, e.ordinal());
	}

	public ItemStack ref() {
		return entityData.get(REF);
	}

	private void ref(ItemStack s) {
		entityData.set(REF, s);
	}

	public Inv inv() {
		return inv;
	}

	public boolean tieneDueno() {
		return entityData.get(TIENE_DUENO);
	}

	public String nombre() {
		return hasCustomName() ? getCustomName().getString() : "";
	}

	public boolean esDe(Player p) {
		return tieneDueno() && dueno != null && dueno.equals(p.getUUID());
	}

	/** Id de vínculo; en esclavos anteriores a los vínculos se localiza por el UUID de la entidad. */
	public UUID vinculo() {
		if (vinculo == null && dueno != null) {
			Registro.Vinculo v = Registro.porEsclavo(dueno, getUUID());
			vinculo = v == null ? null : v.id;
		}
		return vinculo;
	}

	public UUID dueno() {
		return dueno;
	}

	public boolean puedeCancelar() {
		Estado e = estado();
		return e == Estado.RECIBIENDO || e == Estado.PLANIFICANDO || e == Estado.TRABAJANDO || (e==Estado.DESCANSANDO && encargoActivo);
	}

	private ServerPlayer duenoOnline() {
		if (dueno == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getServer().getPlayerList().getPlayer(dueno);
	}

	// ---------------------------------------------------------------- habla

	private void habla(String clave, Object... args) {
		ServerPlayer o = duenoOnline();
		if (o == null) {
			return;
		}
		Component linea = Component.empty()
				.append(Component.literal(nombre().isEmpty() ? "El esclavo" : nombre()).withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
				.append(Component.translatable("theslave.dice." + clave, args).withStyle(net.minecraft.ChatFormatting.ITALIC, net.minecraft.ChatFormatting.GRAY));
		o.sendSystemMessage(linea);
	}

	private static Component nombreItem(Item i) {
		return new ItemStack(i).getHoverName();
	}

	// ---------------------------------------------------------------- gestos

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		return gesto(sp, hand, player.getItemInHand(hand));
	}

	public InteractionResult gesto(ServerPlayer p, InteractionHand hand, ItemStack s) {
        if(custodiaRitual || enRito || duenoEnCustodia())return InteractionResult.SUCCESS;
		boolean sneak = p.isShiftKeyDown();
		boolean latigo = s.is(ModItems.LATIGO);
		if (latigo) {
			com.theslave.item.Latigazo.lanzar(p, position().add(0, getBbHeight() * 0.6, 0), hand);
		}
		if (!tieneDueno()) {
            // Solo un pacto del altar concede una vida; el latigo no permite saltarse su precio.
            if (latigo) p.sendSystemMessage(Component.translatable("theslave.contrato.usa_altar"), true);
            return InteractionResult.SUCCESS;
        }
		if (!esDe(p)) {
			p.sendSystemMessage(Component.translatable("theslave.dice.ajeno"), true);
			return InteractionResult.SUCCESS;
		}
		if (!latigo) {
			if (cooldownGesto > 0) return InteractionResult.SUCCESS;
			cooldownGesto = 5;
		}
		if (latigo) {
			return latigazo(p, hand, s, sneak);
		}
		if (s.isEmpty()) {
			if (sneak) {
				mostrarMochila(p);
			} else {
				abrirOrdenes(p);
			}
			return InteractionResult.SUCCESS;
		}
		if (s.is(ModItems.CONTRATO) && !sneak) {
			if (s.has(com.theslave.registry.ModComponents.PACTO)) {
				p.sendSystemMessage(Component.translatable("theslave.contrato.usa_altar"), true);
			} else if (estado() == Estado.OCIOSO || estado() == Estado.ESPERANDO) {
				liberar(p, s);
			}
			return InteractionResult.SUCCESS;
		}
		if (sneak) {
			darCosa(p, s);
			return InteractionResult.SUCCESS;
		}
		switch (estado()) {
			case OCIOSO, RECIBIENDO -> entregarReferencia(p, s);
			default -> habla("sigo");
		}
		return InteractionResult.SUCCESS;
	}

	/** Llamado desde el paquete del NombreScreen. */
	public void bautizar(ServerPlayer p, String nombre) {
		if (!esDe(p) || nombre == null || custodiaRitual || enRito || duenoEnCustodia()) {
			return;
		}
		String limpio = nombre.strip().replaceAll("[\\p{Cntrl}§]", "");
		if (limpio.length() > 24) {
			limpio = limpio.substring(0, 24);
		}
		if (limpio.isEmpty()) {
			return;
		}
		setCustomName(Component.literal(limpio));
		setCustomNameVisible(true);
		Registro.nombrado(dueno, vinculo(), limpio);
		level().playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 0.7f);
		habla("bautizado", limpio);
	}

	private void liberar(ServerPlayer p, ItemStack contrato) {
        com.theslave.ritual.MuerteDurable.liberar(p,this,contrato);
	}

    private int ranuraJugador(ServerPlayer p,ItemStack stack) {
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i)==stack)return i;
        return -1;
    }
    private void sinEspacio(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Haz espacio en tu inventario; los objetos siguen con tu esclavo."),true);
    }
    private void darCosa(ServerPlayer p,ItemStack s) {
        int slot=ranuraJugador(p,s);if(slot<0)return;
        var plan=TransferenciasDurables.copiar(p,this);ItemStack entrada=plan.jugador[slot];
        if(entrada.has(DataComponents.FOOD)) {
            ItemStack resto=plan.mochila.insertar(entrada,Inv.Fuente.DUENO);
            int recibidos=entrada.getCount()-resto.getCount();if(recibidos<=0)return;
            entrada.shrink(recibidos);
            TransferenciasDurables.ejecutar(p,this,plan,()->habla("provisiones"));return;
        }
        boolean libre=estado()==Estado.OCIOSO || estado()==Estado.ESPERANDO;
        boolean equipable=entrada.has(DataComponents.EQUIPPABLE)||entrada.has(DataComponents.TOOL)||entrada.is(ItemTags.SWORDS);
        if(equipable && libre) {
            EquipmentSlot destino=getEquipmentSlotForItem(entrada);ItemStack previo=plan.equipo.getOrDefault(destino,ItemStack.EMPTY);
            plan.equipo.put(destino,entrada.copyWithCount(1));if(!p.isCreative())entrada.shrink(1);
            ItemStack resto=TransferenciasDurables.insertarJugador(plan,previo);
            if(!resto.isEmpty() && !plan.mochila.insertar(resto,Inv.Fuente.DUENO).isEmpty()){sinEspacio(p);return;}
            TransferenciasDurables.ejecutar(p,this,plan,()->level().playSound(null,blockPosition(),SoundEvents.ARMOR_EQUIP_GENERIC.value(),SoundSource.NEUTRAL,1,1));return;
        }
        if(entrada.is(Items.BUCKET)||entrada.is(Items.WATER_BUCKET)||entrada.is(Items.TORCH)||entrada.is(Items.SOUL_TORCH)) {
            ItemStack resto=plan.mochila.insertar(entrada,Inv.Fuente.DUENO);
            if(resto.getCount()==entrada.getCount())return;entrada.setCount(resto.getCount());
            TransferenciasDurables.ejecutar(p,this,plan,()->{});return;
        }
        habla("no_mochila");
    }
    private void entregarReferencia(ServerPlayer p,ItemStack s) {
        if(s.isEnchanted()||s.has(DataComponents.CUSTOM_NAME)){habla("demasiado_bueno");return;}
        int slot=ranuraJugador(p,s);if(slot<0)return;
        var plan=TransferenciasDurables.copiar(p,this);
        ItemStack nueva=plan.jugador[slot].copyWithCount(1);plan.jugador[slot].shrink(1);
        if(!TransferenciasDurables.insertarJugador(plan,plan.referencia).isEmpty()){sinEspacio(p);return;}
        plan.referencia=nueva;plan.cuerpo.putInt("Estado",Estado.RECIBIENDO.ordinal());
        plan.cuerpo.putInt("Latigazos",0);plan.cuerpo.putInt("Ventana",100);plan.cuerpo.putInt("Temporizador",0);
        plan.cuerpo.putBoolean("EncargoActivo",false);plan.cuerpo.putBoolean("Siguiendo",false);
        TransferenciasDurables.ejecutar(p,this,plan,()->habla("apunta",nombreItem(nueva.getItem())));
    }
    private void repetirEncargo(ServerPlayer p,InteractionHand mano) {
        Item it=BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(ultimoItem)).orElse(null);if(it==null)return;
        var plan=TransferenciasDurables.copiar(p,this);
        if(!plan.referencia.isEmpty()){sinEspacio(p);return;}
        plan.referencia=tomarMuestra(plan.jugador,it);
        if(plan.referencia.isEmpty()){p.sendSystemMessage(Component.literal("Necesito una muestra del objeto para repetir el encargo."),true);return;}
        int latigo=ranuraJugador(p,p.getItemInHand(mano));
        if(latigo>=0 && !p.isCreative()) {
            ItemStack herramienta=plan.jugador[latigo];herramienta.setDamageValue(herramienta.getDamageValue()+1);
            if(herramienta.getDamageValue()>=herramienta.getMaxDamage())herramienta.shrink(1);
        }
        plan.cuerpo.putInt("Estado",Estado.PLANIFICANDO.ordinal());plan.cuerpo.putInt("Techo",ultimoTecho);
        plan.cuerpo.putBoolean("EncargoActivo",true);plan.cuerpo.putBoolean("Exito",false);plan.cuerpo.putString("Motivo","");
        plan.cuerpo.putBoolean("Siguiendo",false);
        TransferenciasDurables.ejecutar(p,this,plan,this::chasquido);
    }
    /** Se usa la misma muestra fisica en la repeticion que en el primer encargo. */
    public static ItemStack tomarMuestra(ItemStack[] inventario,Item item) {
        for(ItemStack stack:inventario) {
            if(stack.is(item) && !stack.isEmpty() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME)) {
                ItemStack muestra=stack.copyWithCount(1);stack.shrink(1);return muestra;
            }
        }
        return ItemStack.EMPTY;
    }

	private InteractionResult latigazo(ServerPlayer p, InteractionHand hand, ItemStack latigo, boolean sneak) {
		if (cooldownGesto > 0 || p.getCooldowns().isOnCooldown(latigo)) {
			return InteractionResult.SUCCESS;
		}
		p.getCooldowns().addCooldown(latigo, 20);
		Estado e = estado();
		switch (e) {
			case RECIBIENDO -> {
				if (!ref().isEmpty()) {
					if (latigazos < 3) {
						latigo.hurtAndBreak(1, p, hand);
					}
					latigazos = Math.min(3, latigazos + 1);
					temporizador = 30;
					chasquido();
				}
			}
			case OCIOSO -> {
				if (sneak && !ultimoItem.isEmpty()) {
					repetirEncargo(p,hand);
				} else {
					habla("que");
				}
			}
			case TRABAJANDO, VOLVIENDO -> {
				latigo.hurtAndBreak(1, p, hand);
				chasquido();
				aceleracion = Math.min(3, aceleracion + 1);
				aceleraTicks = 300;
				moral = Math.max(0, moral - 4);
			}
			case INFORMANDO -> {
				chasquido();
				cobrar(p);
			}
			case ESPERANDO -> {
				chasquido();
				if (inv.isEmpty() && ref().isEmpty()) {
					habla("que");
				} else {
					cobrar(p);
				}
			}
			default -> {
			}
		}
		return InteractionResult.SUCCESS;
	}

	private void chasquido() {
		level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 1.0f, 0.5f);
		cooldownGesto = 4;
	}

	private void abrirOrdenes(ServerPlayer p) {
		ServerPlayNetworking.send(p, new Payloads.AbrirOrdenes(getId(), nombre(), fusionado(), sigue, estado().ordinal(), meritos()));
		estadoEnBarra(p);
	}

	private void estadoEnBarra(ServerPlayer p) {
		int resta = Math.max(0, (Mantenimiento.limiteTrabajo(Config.get().presupuestoMinutos,meritos()) - mantenimiento.esfuerzo()) / 1200);
		Component linea = Component.translatable("theslave.estado.linea", nombre().isEmpty() ? "?" : nombre(),
				Component.translatable("theslave.estado." + estado().name().toLowerCase()),
				Math.round(hambre), moral, resta);
		p.sendSystemMessage(linea, true);
	}

	private void mostrarMochila(ServerPlayer p) {
		if (inv.isEmpty()) {
			p.sendSystemMessage(Component.translatable("theslave.dice.mochila_vacia"), true);
			return;
		}
		Component c = Component.translatable("theslave.dice.mochila");
		for (ItemStack s : inv.stacks()) {
			c = c.copy().append(Component.literal(" ")).append(Component.literal(s.getCount() + " x ")).append(s.getHoverName()).append(Component.literal(";"));
		}
		p.sendSystemMessage(c);
	}

	public void cancelar(ServerPlayer p) {
		if (!puedeCancelar() || custodiaRitual || enRito || duenoEnCustodia()) {
			return;
		}
        if(estado()==Estado.RECIBIENDO){devolverReferencia(p,false);return;}
		exito = false;
		motivoFallo = "cancelado";
        encargoActivo=false;replanificarTrasCarga=false;
		habla("cancelado");
		reiniciarEjecucion();
		estado(mantenimiento.pausado()?Estado.DESCANSANDO:Estado.VOLVIENDO);
	}

	// ---------------------------------------------------------------- entregas

	private static boolean esHerramienta(ItemStack s) {
		return s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES) || s.is(ItemTags.SHOVELS)
				|| s.is(Items.CRAFTING_TABLE) || s.is(Items.FURNACE);
	}

    private void devolverReferencia(ServerPlayer p,boolean agotado) {
        var plan=TransferenciasDurables.copiar(p,this);
        ItemStack resto=TransferenciasDurables.insertarJugador(plan,plan.referencia);
        if(resto.getCount()==plan.referencia.getCount() && !resto.isEmpty()) {
            sinEspacio(p);if(agotado)estado(Estado.ESPERANDO);return;
        }
        plan.referencia=resto;plan.cuerpo.putInt("Estado",resto.isEmpty()?Estado.OCIOSO.ordinal():Estado.ESPERANDO.ordinal());
        plan.cuerpo.putBoolean("EncargoActivo",false);
        TransferenciasDurables.ejecutar(p,this,plan,()->{if(agotado)habla("esperaba");});
    }
    private void cobrar(ServerPlayer p) {
        var plan=TransferenciasDurables.copiar(p,this);Item itemReferencia=plan.referencia.getItem();
        boolean cambio=false,teniaCarga=!plan.referencia.isEmpty();
        if(!plan.referencia.isEmpty()) {
            ItemStack resto=TransferenciasDurables.insertarJugador(plan,plan.referencia);
            cambio=resto.getCount()!=plan.referencia.getCount();plan.referencia=resto;
        }
        for(int i=0;i<Inv.SIZE;i++) {
            ItemStack stack=plan.mochila.get(i);
            if(stack.isEmpty() || ((esHerramienta(stack)||stack.has(DataComponents.FOOD))&&!stack.is(itemReferencia)))continue;
            teniaCarga=true;
            ItemStack resto=TransferenciasDurables.insertarJugador(plan,stack);
            cambio|=resto.getCount()!=stack.getCount();plan.mochila.set(i,resto);
        }
        if(!cambio){if(teniaCarga)sinEspacio(p);else {estado(Estado.OCIOSO);habla("nada");}return;}
        boolean carga=!plan.referencia.isEmpty();
        for(ItemStack stack:plan.mochila.stacks())if((!esHerramienta(stack)&&!stack.has(DataComponents.FOOD))||stack.is(itemReferencia))carga=true;
        plan.cuerpo.putInt("Estado",carga?Estado.ESPERANDO.ordinal():Estado.OCIOSO.ordinal());
        plan.cuerpo.putBoolean("EncargoActivo",false);
        if(exito)plan.cuerpo.putInt("Moral",Math.min(100,moral+5));
        TransferenciasDurables.ejecutar(p,this,plan,()->{if(exito)habla("toma");else habla("falla",Component.literal(motivoFallo));});
    }

	// ---------------------------------------------------------------- órdenes

	private void tickRecibiendo() {
		if (latigazos == 0) {
            if(--ventana<=0) {
                ServerPlayer o=duenoOnline();
                if(o!=null && o.level()==level())devolverReferencia(o,true);
                else estado(Estado.ESPERANDO);
            }
		} else if (--temporizador <= 0) {
			ItemStack r = ref();
			int max = r.getMaxStackSize();
			techo = switch (latigazos) {
				case 1 -> max == 1 ? 1 : Math.min(10, max);
				case 2 -> max == 1 ? 4 : max;
				default -> -1;
			};
			arrancar();
		}
	}

	private void arrancar() {
		ItemStack r = ref();
		ultimoItem = BuiltInRegistries.ITEM.getKey(r.getItem()).toString();
		ultimoTecho = techo;
		estado(Estado.PLANIFICANDO);
        encargoActivo=true;replanificarTrasCarga=false;
		replans = 0;
		exito = false;
		motivoFallo = "";
		aceleracion = 0;
        if(mantenimiento.pausado()) estado(Estado.DESCANSANDO);
        else planificar();
	}

	private void planificar() {
		ServerLevel sl = (ServerLevel) level();
        if(!Conocimiento.asegurar(sl)) { estado(Estado.PLANIFICANDO);return; }
		Item item = ref().getItem();
		int n = techo < 0 ? 2304 : techo;
		List<Item> herramientasEquipadas=new ArrayList<>();
        for(EquipmentSlot slot:EquipmentSlot.values())if(!getItemBySlot(slot).isEmpty())herramientasEquipadas.add(getItemBySlot(slot).getItem());
        Planificador.Resultado res = Planificador.planificar(sl,item,n,inv,energiaFundicion,herramientasEquipadas);
		if (!res.ok) {
			fallar(res.motivo);
			return;
		}
		pasos = res.pasos;
		paso = 0;
		pasoBase = -1;
		objetivo = null;
		minando = null;
		vetados.clear();
		candidatos.clear();
		barrido = null;escCentro=null;
		escFin = false;
		estado(Estado.TRABAJANDO);
		if (!pasos.isEmpty()) {
			habla("voy", nombreItem(pasos.get(0).item()));
		} else {
			terminar();
		}
	}

	private void fallar(String motivo) {
        encargoActivo=false;replanificarTrasCarga=false;
		exito = false;
		motivoFallo = motivo;
		reiniciarEjecucion();
		estado(Estado.VOLVIENDO);
	}

	private void terminar() {
        encargoActivo=false;replanificarTrasCarga=false;
		exito = inv.count(ref().getItem()) > 0;
		if (!exito) {
			motivoFallo = "no he conseguido nada";
		}
		reiniciarEjecucion();
		estado(Estado.VOLVIENDO);
	}

	private void reiniciarEjecucion() {
        if(minando!=null) level().destroyBlockProgress(getId(),minando,-1);
		pasos = new ArrayList<>();
		paso = 0;
		pasoBase = -1;
		objetivo = null;
		minando = null;
		minaTick = 0;
		getNavigation().stop();
	}

	private double factorVelocidad() {
		return Mantenimiento.condicion(hambre,moral,aceleracion)*Aptitudes.factor(meritos(),Aptitudes.AGILIDAD);
	}

    /** Provisiones normales de la mochila: se consume solo cuando su nutricion cabe. */
    private void alimentarse() {
        for(int i=0;i<Inv.SIZE;i++) {
            ItemStack comida=inv.get(i);
            FoodProperties alimento=comida.get(DataComponents.FOOD);
            if(alimento==null || !Mantenimiento.necesitaComer(hambre,alimento.nutrition()))continue;
            var recipiente=comida.get(DataComponents.USE_REMAINDER);
            comida.shrink(1);
            hambre=Math.min(20,hambre+alimento.nutrition());moral=Math.min(100,moral+3);
            if(recipiente!=null)dar(recipiente.convertInto().create(),Inv.Fuente.DUENO);
            return;
        }
    }
    private void pausarTrabajo() {
        if(minando!=null)level().destroyBlockProgress(getId(),minando,-1);
        minando=null;minaTick=0;objetivo=null;candidatos.clear();barrido=null;escFin=false;escCentro=null;
        getNavigation().stop();sigue=false;regresoAtasco=0;distanciaCasa=Double.MAX_VALUE;
        estado(Estado.DESCANSANDO);
        habla(hambre<=0?"sin_comida":"descanso");
    }
    private void tickDescanso(ServerLevel sl) {
        if(volverAlHogar(sl))return;
        if(!mantenimiento.descansar(hambre))return;
        regresoAtasco=0;distanciaCasa=Double.MAX_VALUE;
        if(encargoActivo && !ref().isEmpty()) {
            habla("reanudo");
            if(replanificarTrasCarga || pasos.isEmpty()) { replanificarTrasCarga=false;planificar(); }
            else estado(Estado.TRABAJANDO);
        } else estado(ref().isEmpty()?Estado.OCIOSO:Estado.VOLVIENDO);
    }
    /** Camina primero. Solo una pausa permite recuperar un cuerpo atascado junto a su lecho. */
    private boolean volverAlHogar(ServerLevel sl) {
        if(tickCount%20!=0)return false;
        var hogar=com.theslave.ritual.Hogares.hogar(dueno,vinculo());
        if(hogar==null)return false;
        ServerLevel destino=sl.getServer().getLevel(hogar.dimension());
        if(destino==null || !destino.hasChunkAt(hogar.pos()))return false;
        BlockPos junto=null;
        for(int radio=2;radio<=4 && junto==null;radio++)for(int dx=-radio;dx<=radio && junto==null;dx++)
            for(int dz=-radio;dz<=radio && junto==null;dz++)for(int dy=-1;dy<=1;dy++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radio)continue;
                BlockPos pos=hogar.pos().offset(dx,dy,dz);
                if(!destino.hasChunkAt(pos)||!destino.hasChunkAt(pos.above())||!destino.hasChunkAt(pos.below())
                        || !destino.getWorldBorder().isWithinBounds(pos))continue;
                BlockState suelo=destino.getBlockState(pos.below());
                if(!suelo.isFaceSturdy(destino,pos.below(),Direction.UP)||suelo.is(Blocks.MAGMA_BLOCK)
                        || suelo.is(Blocks.CACTUS)||suelo.is(Blocks.CAMPFIRE)||suelo.is(Blocks.SOUL_CAMPFIRE))continue;
                if(!destino.getBlockState(pos).isAir()||!destino.getBlockState(pos.above()).isAir()
                        || !destino.getFluidState(pos).isEmpty()||!destino.getFluidState(pos.above()).isEmpty())continue;
                Vec3 at=Vec3.atBottomCenterOf(pos);
                if(destino.noCollision(this,getBoundingBox().move(at.subtract(position())))) { junto=pos;break; }
            }
        if(junto==null)return false;
        if(destino==sl) {
            double distancia=distanceToSqr(Vec3.atBottomCenterOf(junto));
            if(distancia<=4) { getNavigation().stop();regresoAtasco=0;return false; }
            if(distancia<distanciaCasa-0.25)regresoAtasco=0;else regresoAtasco+=20;
            distanciaCasa=distancia;
            getNavigation().moveTo(junto.getX()+0.5,junto.getY(),junto.getZ()+0.5,factorVelocidad());
        } else regresoAtasco+=20;
        if(regresoAtasco<400)return false;
        regresoAtasco=0;
        // El cambio de dimension copia el NBT; el encargo se replanifica una sola vez desde su carga.
        replanificarTrasCarga=encargoActivo;
        return teleportTo(destino,junto.getX()+0.5,junto.getY(),junto.getZ()+0.5,Set.of(),getYRot(),getXRot(),false);
    }

	// ---------------------------------------------------------------- ejecución

	private void tickTrabajo(ServerLevel sl) {
        if(!Conocimiento.asegurar(sl)) {cancelarMina(sl);pasos=List.of();paso=0;getNavigation().stop();estado(Estado.PLANIFICANDO);return;}
        ServerPlayer propietario=duenoOnline();
        if(paso< pasos.size() && pasos.get(paso).tipo()==Planificador.Tipo.MINAR && Config.get().respetarProtecciones
                && (propietario==null || propietario.level()!=sl || propietario.isSpectator() || !propietario.mayBuild())) {
            cancelarMina(sl);getNavigation().stop();return;
        }
        if(!mantenimiento.trabajar(hambre,Config.get().presupuestoMinutos,meritos())) {
            pausarTrabajo();
			return;
		}
		if (mantenimiento.consumirHambre(meritos())) {
			hambre = Math.max(0f, hambre - 1f);
            alimentarse();
		}
        if(hambre<=0) {
            mantenimiento.trabajar(0,Config.get().presupuestoMinutos,meritos());
            pausarTrabajo();return;
        }
		if (aceleraTicks > 0 && --aceleraTicks == 0 && aceleracion > 0) {
			aceleracion--;
			aceleraTicks = aceleracion > 0 ? 300 : 0;
		}
		if (paso >= pasos.size()) {
			terminar();
			return;
		}
		Planificador.Paso p = pasos.get(paso);
		switch (p.tipo()) {
			case MINAR -> tickMinar(sl, p);
			case CRAFTEAR -> tickCraftear(p);
			case FUNDIR -> tickFundir(p);
		}
	}

	private void siguientePaso() {
		paso++;
        escCentro=null;barrido=null;escFin=false;candidatos.clear();
		pasoBase = -1;
		objetivo = null;
		demora = 0;
		if (paso < pasos.size()) {
			Planificador.Paso n = pasos.get(paso);
			if (n.tipo() == Planificador.Tipo.MINAR) {
				habla("voy", nombreItem(n.item()));
			}
		}
	}

    private void tickCraftear(Planificador.Paso p) {
        if(pasoBase<0)pasoBase=0;
        getNavigation().stop();
        int tandas=Math.min(16,p.veces()-pasoBase);
        if(tandas<=0){siguientePaso();return;}
        if(demora==0)demora=15+tandas*4;
        if(--demora>0) {
            if(tickCount%8==0)swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT);
            return;
        }
        var mc=(Conocimiento.MetodoCraftear)p.metodo();
        if(mc.receta().grande() && inv.count(Items.CRAFTING_TABLE)<1) {replanificarOFallar("me falta la mesa de crafteo");return;}
        if(!com.theslave.ai.Fabricacion.craftear((ServerLevel)level(),inv,mc,tandas)) {
            replanificarOFallar("faltan materiales o espacio para fabricar "+Planificador.nombre(p.item()));return;
        }
        pasoBase+=tandas;if(pasoBase>=p.veces())siguientePaso();
    }
    private void tickFundir(Planificador.Paso p) {
        if(pasoBase<0)pasoBase=0;
        getNavigation().stop();
        int tandas=Math.min(16,p.veces()-pasoBase);
        if(tandas<=0){siguientePaso();return;}
        if(demora==0)demora=Math.min(400,40+tandas*8);
        if(--demora>0)return;
        if(inv.count(Items.FURNACE)<1){replanificarOFallar("me falta un horno");return;}
        var mf=(Conocimiento.MetodoFundir)p.metodo();
        var resultado=com.theslave.ai.Fabricacion.fundir((ServerLevel)level(),blockPosition(),inv,mf.fundicion(),tandas,energiaFundicion);
        energiaFundicion(resultado.energia());pasoBase+=resultado.hechas();
        if(!resultado.fallo().isEmpty()) {replanificarOFallar(resultado.fallo());return;}
        if(pasoBase>=p.veces())siguientePaso();
    }

	private void replanificarOFallar(String motivo) {
		if (replans++ < 3) {
			planificar();
		} else {
			fallar(motivo);
		}
	}

	private void dar(ItemStack s, Inv.Fuente f) {
		ItemStack resto = inv.insertar(s, f);
		if (!resto.isEmpty()) {
			level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.5, getZ(), resto));
		}
	}

	private ItemStack mejorHerramienta(BlockState st) {
		ItemStack mejor = ItemStack.EMPTY;
		float mv = 1f;
		List<ItemStack> herramientas=new ArrayList<>(inv.stacks());
        for(EquipmentSlot slot:EquipmentSlot.values())if(!getItemBySlot(slot).isEmpty())herramientas.add(getItemBySlot(slot));
        for (ItemStack s : herramientas) {
			if (s.isCorrectToolForDrops(st) || (!st.requiresCorrectToolForDrops() && s.getDestroySpeed(st) > 1f)) {
				float v = s.getDestroySpeed(st);
				if (mejor.isEmpty() || v > mv) {
					mv = v;
					mejor = s;
				}
			}
		}
		return mejor;
	}

	private boolean cercaDeLava(ServerLevel sl, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (!sl.isLoaded(pos.relative(d)) || sl.getFluidState(pos.relative(d)).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private void tickMinar(ServerLevel sl, Planificador.Paso p) {
		Item item = p.item();
		if (pasoBase < 0) {
			pasoBase = inv.count(item);
		}
		if (inv.count(item) >= pasoBase + p.cantidad()) {
			siguientePaso();
			return;
		}
		Conocimiento.Fuente f = ((Conocimiento.MetodoMinar) p.metodo()).fuente();
		if (minando != null) {
			continuarMina(sl, f);
			return;
		}
		if (objetivo != null) {
			if(!sl.isLoaded(objetivo)) {objetivo=null;return;}
            BlockState st = sl.getBlockState(objetivo);
			if (!f.bloques().contains(st.getBlock())) {
				objetivo = null;
			}
		}
		if (objetivo == null) {
			buscarObjetivo(sl, f);
			return;
		}
		double d = distanceToSqr(Vec3.atCenterOf(objetivo));
		if (d <= 16.0 && puedeAlcanzar(sl, objetivo)) {
			getNavigation().stop();
			empezarMina(sl, objetivo);
			return;
		}
		if (tickCount % 10 == 0) {
			getNavigation().moveTo(objetivo.getX() + 0.5, objetivo.getY(), objetivo.getZ() + 0.5, 1.0 * factorVelocidad());
		}
		if (d < ultimaDist - 0.05) {
			atasco = 0;
		} else {
			atasco++;
		}
		ultimaDist = d;
		if (atasco > 60) {
			atasco = 0;
			if (!excavarHacia(sl, objetivo)) {
				vetar(objetivo.asLong());
				objetivo = null;
			}
		}
	}

	private boolean puedeAlcanzar(ServerLevel sl, BlockPos pos) {
		Vec3 ojos = getEyePosition();
		Vec3 fin = Vec3.atCenterOf(pos);
		Vec3 dir = fin.subtract(ojos);
		double len = dir.length();
		if (len < 1.0) {
			return true;
		}
		dir = dir.scale(1.0 / len);
		for (double t = 0.5; t < len - 0.6; t += 0.5) {
			BlockPos b = BlockPos.containing(ojos.add(dir.scale(t)));
			if(!sl.isLoaded(b))return false;
            BlockState s = sl.getBlockState(b);
			if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(sl, b).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Si hay un bloque sólido entre él y el objetivo, lo pica (túnel). Devuelve false si no puede. */
	private boolean excavarHacia(ServerLevel sl, BlockPos meta) {
		Vec3 ojos = getEyePosition();
		Vec3 fin = Vec3.atCenterOf(meta);
		Vec3 dir = fin.subtract(ojos);
		double len = dir.length();
		if (len < 1.0) {
			return false;
		}
		dir = dir.scale(1.0 / len);
		for (double t = 0.5; t < Math.min(len, 5.0); t += 0.5) {
			BlockPos b = BlockPos.containing(ojos.add(dir.scale(t)));
			if (b.equals(meta)) {
				break;
			}
			if(!sl.isLoaded(b))return false;
            BlockState s = sl.getBlockState(b);
			if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(sl, b).isEmpty()) {
				if (s.getDestroySpeed(sl, b) < 0 || s.hasBlockEntity() || cercaDeLava(sl, b)) {
					return false;
				}
				empezarMina(sl, b);
				return true;
			}
		}
		return false;
	}


    private void vetar(long pos) {
        if(vetados.size()>=512)vetados.remove(vetados.iterator().next());
        vetados.add(pos);
    }
    private boolean puedeMinar(ServerLevel sl,BlockPos pos,BlockState st) {
        if(!sl.isLoaded(pos) || !sl.getWorldBorder().isWithinBounds(pos) || st.isAir() || st.hasBlockEntity()
                || !st.getFluidState().isEmpty() || st.getDestroySpeed(sl,pos)<0 || cercaDeLava(sl,pos))return false;
        if(Config.get().respetarProtecciones) {
            ServerPlayer o=duenoOnline();
            if(o==null || o.level()!=sl || o.isSpectator() || !sl.mayInteract(o,pos) || sl.getServer().isUnderSpawnProtection(sl,pos,o))return false;
            if(!o.mayBuild())return false;
        }
        return true;
    }
    private void empezarMina(ServerLevel sl, BlockPos pos) {
        if(!sl.isLoaded(pos))return;
        BlockState st=sl.getBlockState(pos);
        if(!puedeMinar(sl,pos,st)) {vetar(pos.asLong());objetivo=null;return;}
        ItemStack tool=mejorHerramienta(st);
        boolean ok=!st.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(st));
        if(!ok) {replanificarOFallar("me falta la herramienta adecuada");return;}
        float vel=tool.isEmpty()?1f:Math.max(1f,tool.getDestroySpeed(st));
        int t=(int)Math.ceil(st.getDestroySpeed(sl,pos)*30f/vel*1.3f);
        minaTotal=Math.max(4,Mantenimiento.tiempoExtraccion(t,hambre,moral,aceleracion,meritos()));
        minando=pos.immutable();estadoMinando=st;minaTick=0;grieta=-1;
    }
    private void cancelarMina(ServerLevel sl) {
        if(minando!=null)sl.destroyBlockProgress(getId(),minando,-1);
        minando=null;estadoMinando=null;minaTick=0;grieta=-1;
    }
    private void continuarMina(ServerLevel sl,Conocimiento.Fuente f) {
        BlockPos pos=minando;
        if(!sl.isLoaded(pos)) {cancelarMina(sl);objetivo=null;return;}
        BlockState st=sl.getBlockState(pos);
        if(st!=estadoMinando || !puedeMinar(sl,pos,st) || distanceToSqr(Vec3.atCenterOf(pos))>20.25 || !puedeAlcanzar(sl,pos)) {
            cancelarMina(sl);objetivo=null;return;
        }
        getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        if(minaTick%6==0)swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT);
        int etapa=Math.min(9,++minaTick*10/minaTotal);
        if(etapa!=grieta){grieta=etapa;sl.destroyBlockProgress(getId(),pos,etapa);}
        if(minaTick<minaTotal)return;
        ItemStack tool=mejorHerramienta(st);
        ServerPlayer owner=duenoOnline();
        boolean roto=ExtraccionSegura.ejecutar(() -> {
            if(st.requiresCorrectToolForDrops() && (tool.isEmpty() || !tool.isCorrectToolForDrops(st)))return false;
            if(Config.get().respetarProtecciones) {
                if(owner==null)return false;
                if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(sl,owner,pos,st,null)) {
                    PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(sl,owner,pos,st,null);return false;
                }
            }
            // Los callbacks de proteccion pueden haber alterado el mundo.
            return sl.isLoaded(pos) && sl.getBlockState(pos)==st && puedeMinar(sl,pos,st);
        }, () -> Block.getDrops(st,sl,pos,null,this,tool),
           () -> sl.destroyBlock(pos,false,this), d -> dar(d,Inv.Fuente.MINADO));
        if(roto) {
            if(Config.get().respetarProtecciones && owner!=null)PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(sl,owner,pos,st,null);
            if(!tool.isEmpty() && tool.isDamageableItem()) {
                tool.setDamageValue(tool.getDamageValue()+1);
                if(tool.getDamageValue()>=tool.getMaxDamage()) {
                    tool.shrink(1);sl.playSound(null,blockPosition(),SoundEvents.ITEM_BREAK.value(),SoundSource.NEUTRAL,.8f,1f);
                }
            }
        } else vetar(pos.asLong());
        if(pos.equals(objetivo))objetivo=null;
        cancelarMina(sl);
        if(inv.stacks().size()>=Inv.SIZE){habla("lleno");terminar();}
    }
    private void buscarObjetivo(ServerLevel sl,Conocimiento.Fuente f) {
        Config cfg=Config.get();int radio=cfg.radioMenas;
        if(escCentro==null) {escCentro=blockPosition();barrido=new BarridoMenas(radio);escFin=false;candidatos.clear();}
        int limite=ESCANEOS.computeIfAbsent(sl.getServer(),s -> new PresupuestoEscaneo())
            .tomar(sl.getServer().getTickCount(),getUUID(),cfg.escaneoGlobalPorTick,cfg.escaneoPorEsclavo);
        BlockPos.MutableBlockPos m=new BlockPos.MutableBlockPos();
        while(limite-->0 && !escFin && candidatos.size()<64) {
            if(!barrido.siguiente()){escFin=true;break;}
            m.set(escCentro.getX()+barrido.x(),escCentro.getY()+barrido.y(),escCentro.getZ()+barrido.z());
            if(m.getY()<sl.getMinY() || m.getY()>=sl.getMaxY() || !sl.isLoaded(m) || vetados.contains(m.asLong()))continue;
            if(f.bloques().contains(sl.getBlockState(m).getBlock()))candidatos.add(m.immutable());
        }
        BlockPos mejor=null;double md=Double.MAX_VALUE;
        for(BlockPos c:candidatos) {
            if(vetados.contains(c.asLong()) || !sl.isLoaded(c) || !f.bloques().contains(sl.getBlockState(c).getBlock()))continue;
            double d=distanceToSqr(Vec3.atCenterOf(c));if(d<md){md=d;mejor=c;}
        }
        if(mejor!=null){candidatos.remove(mejor);objetivo=mejor;atasco=0;ultimaDist=Double.MAX_VALUE;return;}
        candidatos.clear();
        if(escFin) {
            if(escCentro.distSqr(blockPosition())>64 && replans<6){replans++;escCentro=null;return;}
            escCentro=null;fallar("no encuentro "+Planificador.nombre(f.item())+" en "+radio+" bloques");
        }
    }

	// ---------------------------------------------------------------- vuelta

	private void tickVolviendo(ServerLevel sl) {
		ServerPlayer o = duenoOnline();
		if (o == null || o.level() != sl || distanceTo(o) > 128) {
			getNavigation().stop();
			estado(Estado.ESPERANDO);
			return;
		}
		double d = distanceToSqr(o);
		if (d <= 9.0) {
			getNavigation().stop();
			estado(Estado.INFORMANDO);
			habla("ya_aqui");
			return;
		}
		if (tickCount % 10 == 0) {
			getNavigation().moveTo(o, 1.0 * (0.8 + 0.3 * aceleracion));
		}
		if (aceleraTicks > 0 && --aceleraTicks == 0 && aceleracion > 0) {
			aceleracion--;
		}
	}

	private void tickInformando(ServerLevel sl) {
		ServerPlayer o = duenoOnline();
		if (o == null || o.level() != sl) {
			estado(Estado.ESPERANDO);
			return;
		}
		getLookControl().setLookAt(o);
		if (distanceToSqr(o) > 36.0) {
			estado(Estado.VOLVIENDO);
		}
	}

	private void tickEsperando(ServerLevel sl) {
		if (tickCount % 40 != 0) {
			return;
		}
		ServerPlayer o = duenoOnline();
		if (o != null && o.level() == sl && distanceTo(o) <= 128 && (!inv.isEmpty() || !ref().isEmpty())) {
			estado(Estado.VOLVIENDO);
		}
	}

	/** Seguirme: camina detrás del dueño con una distancia cómoda; se detiene si el dueño se va o cambia de dimensión. */
	private void tickOrdenes(ServerLevel sl) {
		Estado e = estado();
		if (e == Estado.OCIOSO || e == Estado.ESPERANDO) {
            if(!sigue && !encargoActivo)mantenimiento.reposarLibre(Config.get().presupuestoMinutos,meritos());
			if (++recupTick >= 200) {
				recupTick = 0;
				if (moral < 100) {
					moral = Math.min(100, moral + (int) Math.max(1, Math.round(2.0 / Aptitudes.factor(meritos(), Aptitudes.RECUPERACION))));
				}
			}
		}
		if (!sigue) {
			return;
		}
		if (++ordenTick < 10) {
			return;
		}
		ordenTick = 0;
		ServerPlayer o = duenoOnline();
		if (o == null || o.level() != sl || e != Estado.OCIOSO) {
			sigue = false;
			getNavigation().stop();
			return;
		}
		double d = distanceTo(o);
		if (d > 56) {
			getNavigation().stop();
			return;
		}
		if (d > 3.5) {
			// Cada seguidor ocupa un punto distinto detrás del dueño para no apilarse.
			int hueco = Math.floorMod(getUUID().hashCode(), 5) - 2;
			double ang = Math.toRadians(o.getYRot() + 180 + hueco * 28);
			double r = 2.2 + Math.floorMod(getUUID().hashCode() >> 3, 3) * 0.6;
			getNavigation().moveTo(o.getX() - Math.sin(ang) * r, o.getY(), o.getZ() + Math.cos(ang) * r, 1.1 * factorVelocidad());
		} else if (d < 2.0) {
			getNavigation().stop();
		}
	}

	/** Orden del dueño: 0 seguirme, 1 esperar aquí. Devuelve la clave de lang del resultado. */
	public String ordenar(ServerPlayer p, int orden) {
		if (!esDe(p) || enRito || custodiaRitual || duenoEnCustodia() || (orden != 0 && orden != 1)) {
			return "theslave.orden.no";
		}
		if (estado() != Estado.OCIOSO) {
			return puedeCancelar() ? "theslave.orden.ocupado" : "theslave.orden.cargado";
		}
		if (orden == 0) {
			sigue = true;
			ordenTick = 10;
			return "theslave.orden.sigo";
		}
		sigue = false;
		getNavigation().stop();
		return "theslave.orden.espero";
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (cooldownGesto > 0) {
			cooldownGesto--;
		}
		if (!tieneDueno() || enRito) {
			return;
		}
        alimentarse();
        if(replanificarTrasCarga && encargoActivo && !mantenimiento.pausado() && estado()!=Estado.DESCANSANDO) {
            replanificarTrasCarga=false;planificar();
        }
		if (isNoGravity()) {
			// Seguro ante reinicios a media fusión: sin ceremonia en curso nadie debe quedar flotando.
			setNoGravity(false);
		}
		tickOrdenes(level);
		switch (estado()) {
			case PLANIFICANDO -> {if(tickCount%20==0 && Conocimiento.asegurar(level))planificar();}
            case RECIBIENDO -> tickRecibiendo();
			case TRABAJANDO -> tickTrabajo(level);
			case VOLVIENDO -> tickVolviendo(level);
			case INFORMANDO -> tickInformando(level);
			case ESPERANDO -> tickEsperando(level);
            case DESCANSANDO -> tickDescanso(level);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- muerte

	@Override
	public void die(DamageSource source) {
        if(custodiaRitual || duenoEnCustodia()) { if(getHealth()<=0)setHealth(1.0f);return; }
		if (level() instanceof ServerLevel sl && tieneDueno() && dueno != null && !muertoRegistrado) {
            try { crearTumba(sl,source);muertoRegistrado=true; }
            catch(RuntimeException ex) {
                // Sin WAL no se permite vaciar equipo ni dejar que vanilla lo tire por segunda vez.
                setHealth(1.0f);
                com.theslave.TheSlaveMod.LOGGER.error("No se pudo preparar la muerte; se conserva el cuerpo y todo su inventario",ex);
            }
            return;
		}
		super.die(source);
	}

	private BlockPos sitioTumba(ServerLevel sl, BlockPos base) {
		for (int dy = 0; dy >= -3; dy--) {
			for (int r = 0; r <= 3; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						BlockPos p = base.offset(dx, dy, dz);
                        if(!sl.hasChunkAt(p) || !sl.hasChunkAt(p.below()))continue;
						BlockState s = sl.getBlockState(p);
						if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty()
								&& sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}

	private void crearTumba(ServerLevel sl, DamageSource source) {
        com.theslave.ritual.MuerteDurable.morir(sl,this,sitioTumba(sl,blockPosition()),source.getMsgId());
    }

    // ---------------------------------------------------------------- persistencia

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
        if(ultimoSueloSeguro!=null && dimensionSueloSeguro!=null) {
            out.putLong("UltimoSueloSeguro",ultimoSueloSeguro.asLong());out.putString("DimensionSueloSeguro",dimensionSueloSeguro);
        }
        if(operacionRitual!=null)out.putString("TheSlaveRitualOperacion",operacionRitual.toString());
        out.putBoolean("TheSlaveRitualOriginal",originalRitual);
        out.putBoolean("TheSlaveRitualCustodia",custodiaRitual);
        if(custodiaRitual && anclaCustodia!=null) {
            out.putDouble("TheSlaveCustodiaX",anclaCustodia.x);
            out.putDouble("TheSlaveCustodiaY",anclaCustodia.y);
            out.putDouble("TheSlaveCustodiaZ",anclaCustodia.z);
        }
		out.putInt("Variante", variante());
		out.putInt("Variante2", variante2());
		out.putBoolean("TieneDueno", tieneDueno());
        out.putBoolean("Siguiendo", sigue);
        out.putBoolean("RitualSuspendido", enRito);
        if (enRito && posicionAntesRito != null) {
            out.putDouble("RitualX", posicionAntesRito.x);
            out.putDouble("RitualY", posicionAntesRito.y);
            out.putDouble("RitualZ", posicionAntesRito.z);
            out.putBoolean("RitualSeguia", seguiaAntesRito);
        }
		if (dueno != null) {
			out.putString("Dueno", dueno.toString());
		}
		if (vinculo != null) {
			out.putString("Vinculo", vinculo.toString());
		}
		out.putFloat("Hambre", hambre);
		out.putInt("Moral", moral);
		out.putString("UltimoItem", ultimoItem);
		out.putInt("UltimoTecho", ultimoTecho);
		out.putBoolean("Exito", exito);
		out.putString("Motivo", motivoFallo);
		out.putInt("Estado", estado().ordinal());
        out.putBoolean("EncargoActivo",encargoActivo);
        out.putInt("Techo",techo);
        out.putInt("Latigazos",latigazos);out.putInt("Ventana",ventana);out.putInt("Temporizador",temporizador);
        out.putInt("EnergiaFundicion",energiaFundicion);
        out.putInt("TrabajoTicks",mantenimiento.esfuerzo());
        out.putInt("HambreTicks",mantenimiento.hambreTicks());
        out.putInt("DescansoTicks",mantenimiento.descanso());
        out.putBoolean("PausaMantenimiento",mantenimiento.pausado());
        out.putDouble("ReposoFraccion",mantenimiento.reposoFraccion());
		if (!ref().isEmpty()) {
			out.store("Ref", ItemStack.CODEC, ref());
		}
		ValueOutput.TypedOutputList<ItemStack> lista = out.list("Mochila", ItemStack.CODEC);
		for (ItemStack s : inv.stacks()) {
			lista.add(s);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
        ultimoSueloSeguro=in.getLong("UltimoSueloSeguro").map(BlockPos::of).orElse(null);
        dimensionSueloSeguro=in.getStringOr("DimensionSueloSeguro","");
        sigue = in.getBooleanOr("Siguiendo", false);
        if (in.getBooleanOr("RitualSuspendido", false)) {
            // Las sesiones visuales no sobreviven a la descarga: recuperar el lugar seguro anterior.
            double x = in.getDoubleOr("RitualX", getX());
            double y = in.getDoubleOr("RitualY", getY());
            double z = in.getDoubleOr("RitualZ", getZ());
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) setPos(x, y, z);
            sigue = in.getBooleanOr("RitualSeguia", false);
            setNoGravity(false);
            setDeltaMovement(Vec3.ZERO);
        }
        enRito = false;
        entityData.set(CADENA, false);
		entityData.set(VARIANTE, in.getIntOr("Variante", 0));
		entityData.set(VARIANTE2, in.getIntOr("Variante2", -1));
		entityData.set(TIENE_DUENO, in.getBooleanOr("TieneDueno", false));
		String d = in.getStringOr("Dueno", "");
		try {
			dueno = d.isEmpty() ? null : UUID.fromString(d);
		} catch (IllegalArgumentException e) {
			dueno = null;
		}
		String vi = in.getStringOr("Vinculo", "");
		try {
			vinculo = vi.isEmpty() ? null : UUID.fromString(vi);
		} catch (IllegalArgumentException ex) {
			vinculo = null;
		}
		hambre = in.getFloatOr("Hambre", 20f);
        if(!Float.isFinite(hambre))hambre=20;
        hambre=Math.max(0,Math.min(20,hambre));
		moral = Math.max(0,Math.min(100,in.getIntOr("Moral", 100)));
        mantenimiento=new Mantenimiento(in.getIntOr("TrabajoTicks",0),in.getIntOr("DescansoTicks",0),
                in.getIntOr("HambreTicks",0),in.getBooleanOr("PausaMantenimiento",false),in.getDoubleOr("ReposoFraccion",0));
        techo=in.getIntOr("Techo",in.getIntOr("UltimoTecho",0));
        latigazos=Math.clamp(in.getIntOr("Latigazos",0),0,3);
        ventana=Math.clamp(in.getIntOr("Ventana",100),0,100);temporizador=Math.clamp(in.getIntOr("Temporizador",30),0,100);
        energiaFundicion(in.getIntOr("EnergiaFundicion",0));
		ultimoItem = in.getStringOr("UltimoItem", "");
		ultimoTecho = in.getIntOr("UltimoTecho", 0);
		exito = in.getBooleanOr("Exito", false);
		motivoFallo = in.getStringOr("Motivo", "");
		Optional<ItemStack> r = in.read("Ref", ItemStack.CODEC);
		ref(r.orElse(ItemStack.EMPTY));
		inv.clear();
		for (ItemStack s : in.listOrEmpty("Mochila", ItemStack.CODEC)) {
			inv.insertar(s, Inv.Fuente.DUENO);
		}
		int e = in.getIntOr("Estado", 0);
        encargoActivo=in.getBooleanOr("EncargoActivo",e==Estado.TRABAJANDO.ordinal()||e==Estado.PLANIFICANDO.ordinal())&&!ref().isEmpty();
        replanificarTrasCarga=encargoActivo;
		boolean conCarga = !ref().isEmpty() || !inv.isEmpty();
        if(e==Estado.RECIBIENDO.ordinal() && !ref().isEmpty() && !encargoActivo) {
            estado(Estado.RECIBIENDO);
        } else if(mantenimiento.pausado() || e==Estado.DESCANSANDO.ordinal()) {
            estado(Estado.DESCANSANDO);
        } else if(encargoActivo) {
            estado(Estado.PLANIFICANDO);
        } else if (e == Estado.INFORMANDO.ordinal() || e == Estado.ESPERANDO.ordinal() || (conCarga && e != 0)) {
			estado(Estado.ESPERANDO);
			if (!ref().isEmpty() && !exito && motivoFallo.isEmpty()) {
				motivoFallo = "se interrumpió el encargo";
			}
		} else {
			estado(Estado.OCIOSO);
		}
        String op=in.getStringOr("TheSlaveRitualOperacion","");
        try { operacionRitual=op.isEmpty()?null:UUID.fromString(op); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("Operacion ritual corrupta",ex); }
        originalRitual=in.getBooleanOr("TheSlaveRitualOriginal",false);
        custodiaRitual=in.getBooleanOr("TheSlaveRitualCustodia",false);
        entityData.set(CUERPO_RETIRADO,custodiaRitual&&originalRitual);
        if(custodiaRitual) {
            if(operacionRitual==null)throw new IllegalStateException("Custodia sin operacion ritual");
            double x=in.getDoubleOr("TheSlaveCustodiaX",getX());
            double y=in.getDoubleOr("TheSlaveCustodiaY",getY());
            double z=in.getDoubleOr("TheSlaveCustodiaZ",getZ());
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalStateException("Ancla de custodia corrupta");
            anclaCustodia=new Vec3(x,y,z);setPos(anclaCustodia);sigue=false;
            setNoGravity(true);setDeltaMovement(Vec3.ZERO);
        }
	}
}
