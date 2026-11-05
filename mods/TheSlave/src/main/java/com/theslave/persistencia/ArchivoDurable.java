package com.theslave.persistencia;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Escritura de un JSON individual: temporal sincronizado y reemplazo atomico obligatorio. */
public final class ArchivoDurable {
    private ArchivoDurable() {}
    public static JsonElement leer(Path file) {
        try { return JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)); }
        catch (IOException | JsonParseException ex) { throw new IllegalStateException("No se puede leer "+file,ex); }
    }
    public static void escribir(Path file,JsonElement json) {
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(json),StandardCharsets.UTF_8);
            try (FileChannel channel=FileChannel.open(tmp,StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) { throw new IllegalStateException("No se puede persistir "+file,ex); }
    }
}
