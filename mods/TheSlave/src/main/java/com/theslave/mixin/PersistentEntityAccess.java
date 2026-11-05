package com.theslave.mixin;

import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntityAccess {
    @Invoker("ensureChunkQueuedForLoad") void theslave$ensureLoaded(long chunk);
    @Invoker("storeChunkSections") boolean theslave$store(long chunk,Consumer<Entity> consumer);
    @Accessor("permanentStorage") EntityPersistentStorage<Entity> theslave$storage();
    @Accessor("sectionStorage") EntitySectionStorage<Entity> theslave$sections();
}
