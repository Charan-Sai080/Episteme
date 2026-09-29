package com.episteme.indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for computing cryptographic hashes of files.
 * <p>
 * This is used to determine if a Java source file has changed since the last
 * successful AST ingestion.
 */
public final class FileHashUtil {

    private FileHashUtil() {
        // Prevent instantiation
    }

    /**
     * Computes the SHA-256 hash of the specified file and returns it as a Hex string.
     * <p>
     * @implNote This method READS from the filesystem. It has no side-effects.
     * 
     * @param file The file to hash.
     * @return A hexadecimal string representing the SHA-256 hash of the file.
     * @throws IOException If the file cannot be read.
     */
    public static String computeHash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] byteArray = new byte[8192];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }
            byte[] bytes = digest.digest();
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
