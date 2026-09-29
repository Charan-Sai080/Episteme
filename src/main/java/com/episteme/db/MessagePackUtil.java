package com.episteme.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import java.io.IOException;

/**
 * Utility class for serializing and deserializing complex Java Records to/from MessagePack BLOBs.
 * <p>
 * This is used to store unstructured or nested data (like method parameters or execution traces)
 * efficiently within the SQLite database.
 */
public final class MessagePackUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    static {
        objectMapper.findAndRegisterModules();
    }

    private MessagePackUtil() {
        // Prevent instantiation
    }

    /**
     * Serializes a Java object (typically a Record) into a MessagePack byte array.
     * <p>
     * @implNote This method performs CPU-bound serialization. No side-effects.
     * 
     * @param object The Java object to serialize.
     * @return A byte array containing the MessagePack-encoded data.
     * @throws RuntimeException If serialization fails (e.g., due to Jackson mapping errors).
     */
    public static byte[] serialize(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object to MessagePack", e);
        }
    }

    /**
     * Deserializes a MessagePack byte array back into a Java object of the specified class.
     * <p>
     * @implNote This method performs CPU-bound deserialization. No side-effects.
     * 
     * @param data The MessagePack byte array.
     * @param clazz The target Java class (or Record) type.
     * @param <T> The type parameter of the target class.
     * @return The deserialized Java object.
     * @throws RuntimeException If deserialization fails.
     */
    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) return null;
        try {
            return objectMapper.readValue(data, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize MessagePack to " + clazz.getSimpleName(), e);
        }
    }
}
