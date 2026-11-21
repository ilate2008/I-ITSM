package com.iitsm.repository;

import com.iitsm.exception.PersistenceException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Durable repository: the whole state is written to a file (atomic replace) after every change. */
public class FileRepository extends InMemoryRepository {
    private final Path file;

    public FileRepository(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
                this.data = (Data) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new PersistenceException("Could not load data file " + file, e);
            }
        }
    }

    @Override protected void onChange() {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            out.writeObject(data);
        } catch (IOException e) {
            throw new PersistenceException("Could not write data file " + file, e);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new PersistenceException("Could not replace data file " + file, e);
        }
    }
}
