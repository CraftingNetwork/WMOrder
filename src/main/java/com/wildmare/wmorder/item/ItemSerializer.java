package com.wildmare.wmorder.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;

public final class ItemSerializer {
    public byte[] serialize(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new ItemSerializationException("Could not serialize item", exception);
        }
    }

    public ItemStack deserialize(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream objectInput = new BukkitObjectInputStream(input)) {
            Object value = objectInput.readObject();
            if (!(value instanceof ItemStack stack)) throw new IOException("Serialized value is not an ItemStack");
            return stack;
        } catch (IOException | ClassNotFoundException exception) {
            throw new ItemSerializationException("Could not deserialize item", exception);
        }
    }

    public static final class ItemSerializationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ItemSerializationException(String message, Throwable cause) { super(message, cause); }
    }
}
