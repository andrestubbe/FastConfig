package fastconfig;

import fastfileformat.FastFileFormat;
import fastfileformat.TextFormatParser;
import fastfileformat.TextFormatWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.file.StandardOpenOption.*;

/**
 * Universal High-Performance Application Configuration Engine for FastJava.
 * Supports:
 * - Direct app home directories (e.g. {@code ~/.speak/config.format})
 * - Section-aware dot keys (e.g. {@code "chunking.maxTokens"} -> {@code [Chunking] maxTokens = 150})
 * - Hardware DPAPI encryption for secrets without plaintext fallbacks
 * - Atomic fsynced saves & optional auto-save
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
        String cleanName = appName.trim().toLowerCase(Locale.ROOT);
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
     * Reloads configuration safely from disk without clearing existing state on read failure.
     */
    public FastConfig load() {
        lock.writeLock().lock();
        try {
            if (Files.exists(configPath)) {
                String text = Files.readString(configPath, StandardCharsets.UTF_8);
                TextFormatParser parser = FastFileFormat.parseText(text);

                Map<String, Map<String, String>> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : parser.getAll().entrySet()) {
                    String fullKey = entry.getKey();
                    KeyRef ref = resolveKey(fullKey);
                    parsed.computeIfAbsent(ref.section, k -> new LinkedHashMap<>()).put(ref.subKey, entry.getValue());
                }

                data.clear();
                data.putAll(parsed);
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
     * Writes configuration to disk atomically with fsync and cross-filesystem fallback.
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

            byte[] bytes = writer.toText().getBytes(StandardCharsets.UTF_8);
            Path temp = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp." + UUID.randomUUID().toString().substring(0, 8));

            // Write and sync to physical media
            try (FileChannel channel = FileChannel.open(temp, CREATE, WRITE, TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }

            // Atomic move with fallback if ATOMIC_MOVE is unsupported on target filesystem
            try {
                Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return this;
        } catch (IOException e) {
            throw new RuntimeException("[FastConfig] Failed to persist " + configPath + ": " + e.getMessage(), e);
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
        String trimmed = val.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> defaultValue;
        };
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> enumClass, E defaultValue) {
        if (enumClass == null) return defaultValue;
        String val = getString(key, null);
        if (val == null || val.isBlank()) return defaultValue;

        String clean = val.trim().replace('-', '_').replace(' ', '_');
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(clean)) {
                return constant;
            }
        }
        return defaultValue;
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
        return defaultValue;
    }

    /**
     * Stores a secret value securely by encrypting it via Windows DPAPI / FastKeychain before writing.
     * Hard fails on encryption failure to guarantee no plaintext leakage.
     *
     * @param key   Configuration key.
     * @param value Plaintext secret to encrypt.
     */
    public FastConfig setSecret(String key, String value) {
        if (value == null || value.isBlank()) {
            return set(key, "");
        }
        byte[] cipher = fastkeychain.FastKeychain.protectString(value);
        if (cipher == null || cipher.length == 0) {
            throw new IllegalStateException("DPAPI secret encryption failed for key '" + key + "'. Aborting to prevent plaintext leakage.");
        }
        String encoded = "enc:" + Base64.getEncoder().encodeToString(cipher);
        return set(key, encoded);
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

    public Map<String, String> getAll(String section) {
        lock.readLock().lock();
        try {
            String secName = capitalize(section);
            Map<String, String> sec = data.get(secName);
            return sec != null ? Collections.unmodifiableMap(new LinkedHashMap<>(sec)) : Collections.emptyMap();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getSections() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(data.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private String getRaw(String key) {
        KeyRef ref = resolveKey(key);
        Map<String, String> sec = data.get(ref.section);
        if (sec != null && sec.containsKey(ref.subKey)) {
            return sec.get(ref.subKey);
        }
        // Fallback: search in default section
        Map<String, String> gen = data.get("General");
        if (gen != null && gen.containsKey(ref.subKey)) {
            return gen.get(ref.subKey);
        }
        return null;
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
        if (str == null || str.isEmpty()) return "General";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private record KeyRef(String section, String subKey) {}
}
