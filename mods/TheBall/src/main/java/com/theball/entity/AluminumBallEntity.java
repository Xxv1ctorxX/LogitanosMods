package com.theball.entity;

import com.theball.block.ThroneBlock;
import com.theball.item.AluminumBallItem;
import com.theball.power.Allies;
import com.theball.registry.ModItems;
import com.theball.throne.Contributor;
import com.theball.throne.ThroneLogic;
import com.theball.throne.Tribute;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * La bola de papel de aluminio. Es una entidad (no un bloque) para poder moverse y recogerse.
 * El tamaño se mide en "hojas" y se sincroniza con el cliente.
 *
 * Puede estar <b>sentada en un trono</b> (anclada): entonces no tiene física, crece hacia arriba desde el asiento,
 * paga XP al alimentarla y lleva corona (ver {@link ThroneLogic}).
 */
public class AluminumBallEntity extends Entity {
	public static final int MIN_SHEETS = 1;

	/** Tick de la última vez que marcó gol (para no contar el mismo gol varias veces). */
	public int lastGoalTick = -1000;
	private int lastBounceSoundTick = -1000;
	public static final int MAX_SHEETS = 2000;
	/** Hasta aquí la bola crece despacio (como en la vida real); a partir de aquí, de forma acelerada. */
	public static final int SLOW_PHASE_SHEETS = 500;

	private static final double MIN_DIAMETER = 0.1;
	private static final double MAX_DIAMETER = 8.0;
	private static final double DIAMETER_AT_SLOW_END = 0.12 * Math.cbrt(SLOW_PHASE_SHEETS / 9.0);
	/** Por debajo de este diámetro la bola no es sólida: se empuja al pasar por encima, sin atascarse. */
	private static final double SOLID_DIAMETER = 1.0;

	private static final EntityDataAccessor<Integer> DATA_SHEETS =
			SynchedEntityData.defineId(AluminumBallEntity.class, EntityDataSerializers.INT);
	/** Trono en el que está sentada (vacío = bola libre). */
	private static final EntityDataAccessor<Optional<BlockPos>> DATA_ANCHOR =
			SynchedEntityData.defineId(AluminumBallEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	/** Hacia dónde mira el trono (para inclinar la corona). */
	private static final EntityDataAccessor<Integer> DATA_FACING =
			SynchedEntityData.defineId(AluminumBallEntity.class, EntityDataSerializers.INT);
	/** Coronada: la corona es de oro con diamante. */
	private static final EntityDataAccessor<Boolean> DATA_CROWNED =
			SynchedEntityData.defineId(AluminumBallEntity.class, EntityDataSerializers.BOOLEAN);

	/** Cuánto levita sobre el asiento (bloques): la Reina sube durante el espectáculo del Coronamiento. */
	private static final EntityDataAccessor<Float> DATA_LIFT =
			SynchedEntityData.defineId(AluminumBallEntity.class, EntityDataSerializers.FLOAT);

	/** Solo cliente: posición del asiento en el tick anterior (para interpolar la subida). */
	private Vec3 clientSeatPrev;

	/** Rotación acumulada por rodar (solo se usa en el cliente, para el render). */
	public final Quaternionf rollRotation = new Quaternionf();
	public final Quaternionf rollRotationOld = new Quaternionf();

	// ---------------------------------------------------------------- estado de servidor

	/** Quién ha puesto cuánto papel y qué premios se han dado. */
	private Tribute tribute = new Tribute();
	/** Ticks de la coronación en marcha (-1 = ninguna). */
	public int coronationTick = -1;
	/** Estado del trono que no se guarda (barra de jefe, holograma...). Lo gestiona {@link ThroneLogic}. */
	public ThroneLogic.Runtime throneRuntime;

	/** Golazo: quién la chutó y cuántos ticks le quedan haciendo daño. */
	private UUID kickOwner;
	private int kickTicks;
	private final Set<UUID> kickHits = new HashSet<>();

	public AluminumBallEntity(EntityType<? extends AluminumBallEntity> type, Level level) {
		super(type, level);
	}

	// ---------------------------------------------------------------- tamaño

	/**
	 * Hasta 500 hojas: diámetro = 0.12 * cbrt(hojas/9) (volumen proporcional al papel): 9 = 0.12, 100 = 0.26, 500 = 0.46.
	 * De 500 a 2000 hojas: crece de forma lineal y acelerada hasta 8 bloques.
	 */
	public static double diameterFor(int sheets) {
		double d;
		if (sheets <= SLOW_PHASE_SHEETS) {
			d = 0.12 * Math.cbrt(sheets / 9.0);
		} else {
			double t = (sheets - SLOW_PHASE_SHEETS) / (double) (MAX_SHEETS - SLOW_PHASE_SHEETS);
			d = DIAMETER_AT_SLOW_END + (MAX_DIAMETER - DIAMETER_AT_SLOW_END) * t;
		}
		return Mth.clamp(d, MIN_DIAMETER, MAX_DIAMETER);
	}

	/** Papeles que cuesta añadir 1 hoja: siempre 1 (un papel de aluminio = una hoja). */
	public static int foilCostFor(int sheets) {
		return 1;
	}

	private boolean isSolidBall() {
		return getDiameter() >= SOLID_DIAMETER;
	}

	public int getSheets() {
		return this.entityData.get(DATA_SHEETS);
	}

	public void setSheets(int sheets) {
		this.entityData.set(DATA_SHEETS, Mth.clamp(sheets, MIN_SHEETS, MAX_SHEETS));
	}

	public double getDiameter() {
		return diameterFor(getSheets());
	}

	/** Cuanto más grande, más "pesa" (menos sale despedida). */
	public double getMass() {
		return Math.max(1.0, getDiameter() / 0.6);
	}

	public Tribute getTribute() {
		return tribute;
	}

	public void setTribute(Tribute tribute) {
		this.tribute = tribute;
	}

	/** El item que sale al recoger la bola: conserva hojas, aportes y premios. */
	public ItemStack toItem() {
		return AluminumBallItem.create(getSheets(), tribute);
	}

	// ---------------------------------------------------------------- trono

	public boolean isSeated() {
		return this.entityData.get(DATA_ANCHOR).isPresent();
	}

	/** Posición del trono en el que está sentada, o null. */
	public BlockPos getAnchor() {
		return this.entityData.get(DATA_ANCHOR).orElse(null);
	}

	public Direction getThroneFacing() {
		return Direction.from2DDataValue(this.entityData.get(DATA_FACING));
	}

	public boolean isCrowned() {
		return this.entityData.get(DATA_CROWNED);
	}

	public float getLift() {
		return this.entityData.get(DATA_LIFT);
	}

	public void setLift(float lift) {
		this.entityData.set(DATA_LIFT, lift);
	}

	public void setCrowned(boolean crowned) {
		this.entityData.set(DATA_CROWNED, crowned);
	}

	/** Centro de la base de la bola cuando está sentada: el asiento del trono. */
	public static Vec3 seatPosition(BlockPos throne) {
		return new Vec3(throne.getX() + 0.5, throne.getY() + ThroneBlock.SEAT_Y, throne.getZ() + 0.5);
	}

	/** Sienta la bola en un trono: deja de tener física y se coloca sobre el asiento. */
	public void seatOn(BlockPos throne, Direction facing) {
		this.entityData.set(DATA_ANCHOR, Optional.of(throne.immutable()));
		this.entityData.set(DATA_FACING, facing.get2DDataValue());
		this.noPhysics = true;
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
		Vec3 seat = seatPosition(throne);
		setPos(seat.x, seat.y, seat.z);
		clearKick();
	}

	/** La bola deja el trono (lo han roto o la han retirado) y vuelve a la física normal. */
	public void release() {
		if (!isSeated()) {
			return;
		}
		BlockPos throne = getAnchor();
		this.entityData.set(DATA_ANCHOR, Optional.empty());
		this.entityData.set(DATA_LIFT, 0.0f);
		this.noPhysics = false;
		setNoGravity(false);
		this.coronationTick = -1;
		if (this.level() instanceof ServerLevel serverLevel) {
			ThroneLogic.onLeftThrone(serverLevel, this, throne);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SHEETS, AluminumBallItem.DEFAULT_SHEETS);
		builder.define(DATA_ANCHOR, Optional.empty());
		builder.define(DATA_FACING, Direction.SOUTH.get2DDataValue());
		builder.define(DATA_CROWNED, false);
		builder.define(DATA_LIFT, 0.0f);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		// Puede llamarse desde el constructor de Entity, antes de que existan los datos sincronizados.
		int sheets = this.entityData == null ? AluminumBallItem.DEFAULT_SHEETS : getSheets();
		float d = (float) diameterFor(sheets);
		return EntityDimensions.fixed(d, d);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_SHEETS.equals(accessor)) {
			refreshDimensions();
		} else if (DATA_ANCHOR.equals(accessor)) {
			this.noPhysics = isSeated();
		}
	}

	// ---------------------------------------------------------------- persistencia

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setSheets(input.getIntOr("Sheets", AluminumBallItem.DEFAULT_SHEETS));
		this.tribute = new Tribute(input.read("Contributions", Contributor.LIST_CODEC).orElse(List.of()),
				input.getIntOr("RewardsGiven", 0));
		Optional<BlockPos> anchor = input.read("Throne", BlockPos.CODEC);
		this.entityData.set(DATA_ANCHOR, anchor);
		this.entityData.set(DATA_FACING, input.getIntOr("ThroneFacing", Direction.SOUTH.get2DDataValue()));
		this.entityData.set(DATA_CROWNED, input.getBooleanOr("Crowned", false));
		this.coronationTick = input.getIntOr("CoronationTick", -1);
		this.noPhysics = anchor.isPresent();
		if (anchor.isPresent()) {
			setNoGravity(true);
		}
		this.kickOwner = input.read("KickOwner", UUIDUtil.CODEC).orElse(null);
		this.kickTicks = input.getIntOr("KickTicks", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Sheets", getSheets());
		if (!this.tribute.isEmpty()) {
			output.store("Contributions", Contributor.LIST_CODEC, this.tribute.toList());
		}
		if (this.tribute.rewardsGiven() != 0) {
			output.putInt("RewardsGiven", this.tribute.rewardsGiven());
		}
		BlockPos anchor = getAnchor();
		if (anchor != null) {
			output.store("Throne", BlockPos.CODEC, anchor);
			output.putInt("ThroneFacing", this.entityData.get(DATA_FACING));
		}
		if (isCrowned()) {
			output.putBoolean("Crowned", true);
		}
		if (this.coronationTick >= 0) {
			output.putInt("CoronationTick", this.coronationTick);
		}
		if (this.kickTicks > 0 && this.kickOwner != null) {
			output.store("KickOwner", UUIDUtil.CODEC, this.kickOwner);
			output.putInt("KickTicks", this.kickTicks);
		}
	}

	/** Sentada, el cliente ignora los paquetes de posición (se coloca él mismo con la altura sincronizada). */
	@Override
	protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
		return new net.minecraft.world.entity.InterpolationHandler() {
			@Override
			public net.minecraft.core.PositionAndRotation target() {
				return net.minecraft.world.entity.InterpolationHandler.NO_OP.target();
			}

			@Override
			public boolean interpolateTo(net.minecraft.world.entity.PositionPath path, float yRot, float xRot, boolean rotate) {
				return AluminumBallEntity.this.isSeated(); // true = ya tratado (ignorado)
			}

			@Override
			public void interpolate() {
			}

			@Override
			public void applyPredictedMovement(Vec3 movement) {
			}

			@Override
			public boolean hasActiveInterpolation() {
				return false;
			}

			@Override
			public void cancel() {
			}
		};
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.level() instanceof ServerLevel serverLevel) {
			ThroneLogic.onRemoved(serverLevel, this, reason);
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------- solidez / puntería

	@Override
	public float getPickRadius() {
		return 0.08f; // margen para poder apuntar a la bola pequeña
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return isSolidBall();
	}

	@Override
	public ItemStack getPickResult() {
		return AluminumBallItem.create(getSheets());
	}

	// ---------------------------------------------------------------- física

	@Override
	public void tick() {
		super.tick();
		this.rollRotationOld.set(this.rollRotation);

		if (isSeated()) {
			tickSeated();
			return;
		}

		// Si ha quedado metida en bloques (al crecer, colocarla o lanzarla), la subimos poco a poco hasta sacarla.
		if (!this.level().noBlockCollision(this, getBoundingBox())) {
			setPos(getX(), getY() + 0.15, getZ());
			Vec3 stuck = getDeltaMovement();
			setDeltaMovement(stuck.x, Math.max(stuck.y, 0.0), stuck.z);
			return;
		}

		Vec3 motion = getDeltaMovement();
		boolean inWater = isInWater();

		// Gravedad (menos en el agua).
		if (!isNoGravity()) {
			motion = motion.add(0.0, inWater ? -0.01 : -0.04, 0.0);
		}

		// Si alguien camina contra ella, la empuja.
		motion = applyWalkingPush(motion);

		Vec3 before = position();
		setDeltaMovement(motion);
		move(MoverType.SELF, motion);
		Vec3 moved = position().subtract(before);

		// Rebotes suaves al chocar: comparamos lo que quería moverse con lo que se movió de verdad.
		double vx = motion.x;
		double vy = motion.y;
		double vz = motion.z;
		double impact = 0.0; // fuerza del choque más fuerte de este tick (para el sonido)
		if (Math.abs(moved.x - motion.x) > 1.0E-5) {
			vx = -motion.x * 0.45;
			impact = Math.max(impact, Math.abs(motion.x));
		}
		if (Math.abs(moved.z - motion.z) > 1.0E-5) {
			vz = -motion.z * 0.45;
			impact = Math.max(impact, Math.abs(motion.z));
		}
		if (Math.abs(moved.y - motion.y) > 1.0E-5) {
			if (motion.y < 0.0) {
				// Cae sobre algo: rebota si llega con algo de fuerza.
				vy = motion.y < -0.18 ? -motion.y * 0.4 : 0.0;
				impact = Math.max(impact, -motion.y);
			} else {
				vy = 0.0;
			}
		}
		playBounceSound(impact);

		// Rozamiento.
		double friction;
		if (inWater) {
			friction = 0.8;
			vy *= 0.8;
		} else if (onGround()) {
			float blockFriction = this.level().getBlockState(getOnPos()).getBlock().getFriction();
			friction = Mth.clamp(0.4 + blockFriction * 0.9, 0.5, 0.99);
		} else {
			friction = 0.99;
		}
		vx *= friction;
		vz *= friction;
		if (vx * vx + vz * vz < 1.0E-5) {
			vx = 0.0;
			vz = 0.0;
		}
		Vec3 velocity = new Vec3(vx, vy, vz);

		setDeltaMovement(velocity);

		if (this.level() instanceof ServerLevel serverLevel) {
			tickKick(serverLevel, velocity);
		}

		if (this.level().isClientSide()) {
			updateRoll(moved);
		}
	}

	/** Sentada: quieta sobre el asiento, sin física. */
	private void tickSeated() {
		BlockPos anchor = getAnchor();
		setDeltaMovement(Vec3.ZERO);
		Vec3 seat = seatPosition(anchor).add(0.0, getLift(), 0.0);
		if (this.level().isClientSide()) {
			// El cliente se coloca solo con la altura sincronizada y conserva la posición del tick anterior: así la subida
			// de la Reina se dibuja interpolada (fluida) y no a saltos de 20 por segundo.
			Vec3 prev = this.clientSeatPrev != null ? this.clientSeatPrev : seat;
			setPos(seat.x, seat.y, seat.z);
			this.xo = prev.x;
			this.yo = prev.y;
			this.zo = prev.z;
			this.xOld = prev.x;
			this.yOld = prev.y;
			this.zOld = prev.z;
			this.clientSeatPrev = seat;
		} else if (position().distanceToSqr(seat) > 1.0E-6) {
			setPos(seat.x, seat.y, seat.z);
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			ThroneLogic.tickSeated(serverLevel, this);
		}
	}

	/** Ruido al rebotar: más fuerte cuanto más fuerte es el golpe y más grave cuanto más grande es la bola. */
	private void playBounceSound(double impact) {
		if (this.level().isClientSide() || impact < 0.12 || this.tickCount - this.lastBounceSoundTick < 4) {
			return;
		}
		this.lastBounceSoundTick = this.tickCount;
		float volume = (float) Mth.clamp(impact * 1.6, 0.2, 1.0);
		float pitch = (float) Mth.clamp(1.5 / Math.sqrt(getMass()), 0.5, 1.5) + this.random.nextFloat() * 0.15f;
		this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, volume, pitch);
		this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.METAL_HIT, SoundSource.NEUTRAL, volume * 0.6f, pitch * 1.3f);
	}

	/** Entidades vivas que caminan contra la bola le transmiten velocidad (más cuanto más pequeña es). */
	private Vec3 applyWalkingPush(Vec3 motion) {
		AABB area = getBoundingBox().inflate(0.12, 0.0, 0.12);
		List<LivingEntity> touching = this.level().getEntitiesOfClass(LivingEntity.class, area,
				e -> e.isAlive() && !e.isSpectator());
		double pushMass = Math.sqrt(getMass()); // las bolas gigantes se empujan algo más lento, pero se pueden mover
		boolean solid = isSolidBall();
		boolean server = !this.level().isClientSide();

		for (LivingEntity e : touching) {
			double dx = getX() - e.getX();
			double dz = getZ() - e.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.0E-4) {
				// Justo en el centro: elegimos una dirección cualquiera para no quedarnos atascados.
				dx = 1.0;
				dz = 0.0;
				len = 1.0;
			}
			dx /= len;
			dz /= len;

			// Cuánto se solapan en horizontal (aproximando a círculos).
			double penetration = getDiameter() / 2.0 + e.getBbWidth() / 2.0 - len;

			if (!solid && penetration > 0.0) {
				// Bola pequeña: no bloquea a nadie, es ella la que sale despedida de debajo de los pies.
				double out = Math.min(penetration * 0.5, 0.2);
				motion = motion.add(dx * out, 0.0, dz * out);
			}

			// Velocidad con la que el personaje avanza hacia la bola.
			double along = Math.max(
					(e.getX() - e.xo) * dx + (e.getZ() - e.zo) * dz,
					e.getDeltaMovement().x * dx + e.getDeltaMovement().z * dz);
			// Si va hacia ella pero la colisión le impide moverse, usamos la tecla de avance.
			if (along <= 0.01 && e instanceof ServerPlayer sp && sp.getLastClientInput().forward()) {
				Vec3 look = e.getLookAngle();
				if (look.x * dx + look.z * dz > 0.5) {
					along = e.isSprinting() ? 0.13 : 0.1;
				}
			}
			if (along > 0.01) {
				double target = along * 1.5 / pushMass;
				double current = motion.x * dx + motion.z * dz;
				if (current < target) {
					double extra = target - current;
					motion = motion.add(dx * extra, 0.0, dz * extra);
				}
			}

			// Bola sólida con alguien metido dentro (p. ej. al crecer o al colocarla): lo sacamos para que no se quede pegado.
			if (solid && server && penetration > 0.05 && e.getY() < getY() + getBbHeight() * 0.6) {
				double strength = Math.min(penetration * 0.2, 0.25);
				e.setDeltaMovement(e.getDeltaMovement().add(-dx * strength, 0.0, -dz * strength));
				e.syncVelocity = true;
			}
		}
		return motion;
	}

	/** Acumula la rotación de rodar: eje = arriba x dirección de avance, ángulo = distancia / radio. */
	private void updateRoll(Vec3 moved) {
		double distance = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
		if (distance < 1.0E-4) {
			return;
		}
		double radius = getDiameter() / 2.0;
		float angle = (float) (distance / radius);
		float axisX = (float) (moved.z / distance);
		float axisZ = (float) (-moved.x / distance);
		Quaternionf delta = new Quaternionf().rotationAxis(angle, axisX, 0.0f, axisZ);
		this.rollRotation.premul(delta).normalize();
	}

	// ---------------------------------------------------------------- Golazo (chute con el cetro)

	/** El cetro le da un chute: durante unos segundos, si va rápida, arrolla lo que atraviesa. */
	public void kick(ServerPlayer owner, Vec3 impulse) {
		setDeltaMovement(getDeltaMovement().add(impulse));
		this.syncVelocity = true;
		this.kickOwner = owner.getUUID();
		this.kickTicks = 60;
		this.kickHits.clear();
	}

	public boolean isRoyalKick() {
		return this.kickTicks > 0 && this.kickOwner != null;
	}

	public UUID getKickOwner() {
		return this.kickOwner;
	}

	private void clearKick() {
		this.kickOwner = null;
		this.kickTicks = 0;
		this.kickHits.clear();
	}

	private void tickKick(ServerLevel level, Vec3 velocity) {
		if (this.kickTicks <= 0) {
			return;
		}
		this.kickTicks--;
		double speed = velocity.length();
		if (speed < 0.6) {
			return; // un toque suave no hace daño
		}
		Entity owner = this.kickOwner == null ? null : level.getEntity(this.kickOwner);
		float maxDamage = 40.0f;
		float damage = (float) Mth.clamp(getDiameter() * speed * 6.0, 2.0, maxDamage);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.2),
				e -> e.isAlive() && !e.isSpectator() && e != owner && !kickHits.contains(e.getUUID())
						&& (owner == null || !Allies.areAllies(owner, e)))) {
			this.kickHits.add(target.getUUID());
			DamageSource source = owner instanceof Player p
					? level.damageSources().playerAttack(p)
					: level.damageSources().generic();
			target.hurtServer(level, source, damage);
			Vec3 push = velocity.normalize().scale(0.6 + speed * 0.5);
			target.push(push.x, 0.35, push.z);
			target.syncVelocity = true;
		}
	}

	// ---------------------------------------------------------------- golpes

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved()) {
			return false;
		}
		Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		if (attacker == null) {
			return false; // lava, vacío, etc.: la bola es inmune
		}
		if (isSeated()) {
			// Sentada en el trono no se mueve: solo un "plof" de lana.
			playSound(SoundEvents.WOOL_HIT, 0.5f, 0.7f);
			return false;
		}

		double dx = getX() - attacker.getX();
		double dz = getZ() - attacker.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) {
			Vec3 look = attacker.getLookAngle();
			dx = look.x;
			dz = look.z;
			len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.0E-4) {
				return false;
			}
		}
		dx /= len;
		dz /= len;

		double mass = getMass();
		double strength = (0.55 + Mth.clamp(damage, 0.0f, 10.0f) * 0.03) / mass;
		double up = 0.12 / Math.sqrt(mass);
		setDeltaMovement(getDeltaMovement().add(dx * strength, up, dz * strength));
		this.syncVelocity = true;
		playSound(SoundEvents.WOOL_HIT, 0.8f, 1.4f / (float) Math.sqrt(mass));
		return true;
	}

	// ---------------------------------------------------------------- interacción

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		return onPlayerUse(player, hand);
	}

	/** Click derecho de un jugador. */
	public InteractionResult onPlayerUse(Player player, InteractionHand hand) {
		if (isSeated()) {
			return onSeatedUse(player, hand);
		}

		boolean clientSide = this.level().isClientSide();
		ItemStack held = player.getItemInHand(hand);

		// Crecer con papel de aluminio: cuanto más grande, más papel cuesta cada hoja nueva.
		if (held.is(ModItems.ALUMINUM_FOIL)) {
			if (getSheets() >= MAX_SHEETS) {
				if (!clientSide) {
					player.sendOverlayMessage(Component.translatable("message.theball.max_size", MAX_SHEETS));
				}
				return InteractionResult.SUCCESS;
			}
			int cost = foilCostFor(getSheets());
			boolean free = player.hasInfiniteMaterials();
			if (!free && held.getCount() < cost) {
				if (!clientSide) {
					player.sendOverlayMessage(Component.translatable("message.theball.need_foil", cost));
				}
				return InteractionResult.SUCCESS;
			}
			if (!clientSide) {
				setSheets(getSheets() + 1);
				if (!free) {
					held.shrink(cost);
					// Fuera del trono no se paga XP, pero el aporte sí cuenta para las recompensas.
					ThroneLogic.recordContribution(this, player, cost);
				}
				playSound(SoundEvents.WOOL_PLACE, 0.8f, 1.5f + this.random.nextFloat() * 0.5f);
			}
			return InteractionResult.SUCCESS;
		}

		// Recoger: click derecho (con o sin shift) sin papel de aluminio en ninguna mano.
		boolean foilInHand = player.getMainHandItem().is(ModItems.ALUMINUM_FOIL)
				|| player.getOffhandItem().is(ModItems.ALUMINUM_FOIL);
		if (!foilInHand) {
			if (!clientSide) {
				ItemStack stack = toItem();
				// Si tienes la mano libre se queda en ella: manteniendo el click derecho cargas y lanzas.
				if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
					player.setItemInHand(hand, stack);
				} else if (!player.getInventory().add(stack)) {
					player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1.0f);
				discard();
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	/**
	 * Sentada en el trono: papel = alimentar (paga XP), shift + mano vacía = retirarla, mano vacía = estado.
	 * Mantener el click derecho con papel alimenta en tanda (el cliente repite la interacción cada 4 ticks).
	 */
	private InteractionResult onSeatedUse(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		boolean emptyMain = hand == InteractionHand.MAIN_HAND && held.isEmpty();
		if (!held.is(ModItems.ALUMINUM_FOIL) && !emptyMain) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			if (held.is(ModItems.ALUMINUM_FOIL)) {
				ThroneLogic.feed(this, player, hand);
			} else if (player.isShiftKeyDown()) {
				ThroneLogic.unseat(this, player);
			} else {
				ThroneLogic.statusFor(serverLevel, getAnchor(), player);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
