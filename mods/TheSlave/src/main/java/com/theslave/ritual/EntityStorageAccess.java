package com.theslave.ritual;

import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

/** Implementada por EntityStorageMixin, sin reflexión ni segundo escritor de ficheros MCA. */
public interface EntityStorageAccess {
    SimpleRegionStorage theslave$regionStorage();
}
