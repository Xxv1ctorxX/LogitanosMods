package com.ludopathyroulette.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Instantánea sincronizada con sustitución atómica. Si el sistema no la permite, se detiene el cobro. */
public final class AtomicStore {
    private AtomicStore() {}
    public static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName()+".tmp");
        try (FileChannel c = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer b = StandardCharsets.UTF_8.encode(value);
            while(b.hasRemaining()) c.write(b);
            c.force(true);
        }
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
