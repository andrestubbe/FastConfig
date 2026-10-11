package fastconfig;

import fastfileformat.FastFileFormat;
import fastfileformat.TextFormatParser;
import fastfileformat.TextFormatWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Universal High-Performance Application Configuration Engine for FastJava.
 * Supports:
 * - Direct app home directories (e.g. {@code ~/.speak/config.format})
 * - Section-aware dot keys (e.g. {@code "chunking.maxTokens"} -> {@code [Chunking] maxTokens = 150})
 * - Atomic saves & optional auto-save
 * - Thread-safety via {@link ReentrantReadWriteLock}
 * - Typed getters with guaranteed defaults
 */
public final class FastConfig {

    private static final Map<Path, FastConfig> INSTANCES = new ConcurrentHashMap<>();

    private final Path configPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Map<String, String>> data = new LinkedHashMap<>();
    private volatile boolean autoSave = false;

    private FastConfig(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath must not be null");
        load();
    }

    /**
     * Resolves or creates an application config at {@code ~/.<appName>/config.format}.
     *
     * @param appName Name of the application (e.g. "speak", "clip", "candy").
     * @return Shared {@link FastConfig} instance.
     */
    public static FastConfig app(String appName) {
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("appName must not be blank");
        }
        String cleanName = appName.trim().toLowerCase();
        if (cleanName.startsWith(".")) {
            cleanName = cleanName.substring(1);
        }
        Path path = Paths.get(System.getProperty("user.home"), "." + cleanName, "config.format");
        return file(path);
    }

    /**
     * Resolves or creates a config at an arbitrary file path.
     *
     * @param path Target configuration file.
     * @return Shared {@link FastConfig} instance.
     */
    public static FastConfig file(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        return INSTANCES.computeIfAbsent(abs, FastConfig::new);
    }

    /**
     * Reloads configuration from disk.
     */
    public FastConfig load() {
        lock.writeLock().lock();
        try {
            data.clear();
            if (Files.exists(configPath)) {
                String text = Files.readString(configPath);
                TextFormatParser parser = FastFileFormat.parseText(text);

                // Populate from all parsed entries
                for (Map.Entry<String, String> entry : parser.getAll().entrySet()) {
                    String fullKey = entry.getKey();
                    KeyRef ref = resolveKey(fullKey);
                    data.computeIfAbsent(ref.section, k -> new LinkedHashMap<>()).put(ref.subKey, entry.getValue());
                }
            }
            return this;
        } catch (IOException e) {
            System.err.println("[FastConfig] Failed to read " + configPath + ": " + e.getMessage());
            return this;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Writes configuration to disk safely.
     */
    public FastConfig save() {
        lock.writeLock().lock();
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }

            TextFormatWriter writer = FastFileFormat.textWriter();
            writer.comment("FastConfig Auto-Generated Configuration");

            for (Map.Entry<String, Map<String, String>> secEntry : data.entrySet()) {
                String secName = secEntry.getKey();
                Map<String, String> kvs = secEntry.getValue();
                if (kvs == null || kvs.isEmpty()) continue;

                writer.section(secName);
                for (Map.Entry<String, String> kv : kvs.entrySet()) {
                    writer.set(kv.getKey(), kv.getValue());
                }
            }

            // Atomic file write using temporary file
            Path temp = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp");
            Files.writeString(temp, writer.toText());
            Files.move(temp, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return this;
        } catch (IOException e) {
            System.err.println("[FastConfig] Failed to save " + configPath + ": " + e.getMessage());
            return this;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public FastConfig autoSave(boolean enable) {
        this.autoSave = enable;
        return this;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public Path getPath() {
        return configPath;
    }

    // --- Typed Getters ---

    public String getString(String key, String defaultValue) {
        lock.readLock().lock();
        try {
            String val = getRaw(key);
            return val != null ? val : defaultValue;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getInt(String key, int defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        String trimmed = val.trim().toLowerCase();
        return trimmed.equals("true") || trimmed.equals("yes") || trimmed.equals("1") || trimmed.equals("on");
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> enumClass, E defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        try {
            return Enum.valueOf(enumClass, val.trim().toUpperCase());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Retrieves an encrypted secret value (e.g. API key) and decrypts it via Windows DPAPI / FastKeychain.
     *
     * @param key          Configuration key.
     * @param defaultValue Fallback if key absent or decryption fails.
     * @return Decrypted plaintext secret.
     */
    public String getSecret(String key, String defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        if (raw.startsWith("enc:")) {
            try {
                byte[] cipher = Base64.getDecoder().decode(raw.substring(4).trim());
                String decrypted = fastkeychain.FastKeychain.unprotectString(cipher);
                return (decrypted != null) ? decrypted : defaultValue;
            } catch (Exception e) {
                System.err.println("[FastConfig] Failed to decrypt secret '" + key + "': " + e.getMessage());
                return defaultValue;
            }
        }
        // Fallback: plaintext token
        return raw;
    }

    /**
     * Stores a secret value securely by encrypting it via Windows DPAPI / FastKeychain before writing.
     *
     * @param key   Configuration key.
     * @param value Plaintext secret to encrypt.
     */
    public FastConfig setSecret(String key, String value) {
        if (value == null || value.isBlank()) {
            return set(key, "");
        }
        try {
            byte[] cipher = fastkeychain.FastKeychain.protectString(value);
            if (cipher != null && cipher.length > 0) {
                String encoded = "enc:" + Base64.getEncoder().encodeToString(cipher);
                return set(key, encoded);
            }
        } catch (Exception e) {
            System.err.println("[FastConfig] Failed to encrypt secret '" + key + "': " + e.getMessage());
        }
        return set(key, value);
    }

    // --- Setters ---

    public FastConfig set(String key, Object value) {
        if (key == null || key.isBlank()) return this;
        String valStr = value != null ? String.valueOf(value) : "";

        lock.writeLock().lock();
        try {
            KeyRef ref = resolveKey(key);
            data.computeIfAbsent(ref.section, k -> new LinkedHashMap<>()).put(ref.subKey, valStr);
        } finally {
            lock.writeLock().unlock();
        }

        if (autoSave) {
            save();
        }
        return this;
    }

    public boolean has(String key) {
        lock.readLock().lock();
        try {
            return getRaw(key) != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public FastConfig remove(String key) {
        lock.writeLock().lock();
        try {
            KeyRef ref = resolveKey(key);
            Map<String, String> sec = data.get(ref.section);
            if (sec != null) {
                sec.remove(ref.subKey);
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (autoSave) {
            save();
        }
        return this;
    }

    private String getRaw(String key) {
        KeyRef ref = resolveKey(key);
        Map<String, String> sec = data.get(ref.section);
        if (sec != null && sec.containsKey(ref.subKey)) {
            return sec.get(ref.subKey);
        }
        // Fallback: search in default section
        Map<String, String> gen = data.get("General");
        if (gen != null && gen.containsKey(key)) {
            return gen.get(key);
        }
        return null;
    }

    private static String normalizeSection(String sec) {
        if (sec == null || sec.isBlank()) return "General";
        return sec.trim();
    }

    private static KeyRef resolveKey(String key) {
        String trimmed = key.trim();
        int dot = trimmed.indexOf('.');
        if (dot != -1 && dot < trimmed.length() - 1) {
            String sec = trimmed.substring(0, dot).trim();
            String sub = trimmed.substring(dot + 1).trim();
            return new KeyRef(capitalize(sec), sub);
        }
        return new KeyRef("General", trimmed);
    }

    private static String capitalize(String str) {
        if (str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private record KeyRef(String section, String subKey) {}
}
