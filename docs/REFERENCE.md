# FastConfig API Reference

Comprehensive reference guide for all classes, methods, and configurations in `FastConfig`.

## Package: `fastconfig`

### Class: `FastConfig`

Thread-safe configuration manager providing section-aware key access, atomic persistence, and DPAPI cryptographic secret protection.

```java
public final class FastConfig
```

---

### Factory Methods

#### `app(String appName)`
```java
public static FastConfig app(String appName)
```
Resolves and returns the configuration instance located in the user's home directory under `~/.<appName>/config.format`. Automatically creates parent directories if necessary.
- **Parameters:** `appName` - Simple application name (e.g., `"myassistant"`).
- **Returns:** Active `FastConfig` instance.

#### `file(Path configPath)`
```java
public static FastConfig file(Path configPath)
```
Returns a `FastConfig` instance bound to the specified file path. Shared across calls with the exact same normalized path.
- **Parameters:** `configPath` - Target path for the configuration file.
- **Returns:** Active `FastConfig` instance.

---

### Persistence & Lifecycle Methods

#### `autoSave(boolean autoSave)`
```java
public FastConfig autoSave(boolean autoSave)
```
Enables or disables automatic disk persistence on every mutating operation (`set`, `remove`, `setSecret`).
- **Parameters:** `autoSave` - `true` to persist immediately on writes; `false` to require manual `save()`.
- **Returns:** `this` for fluent chaining.

#### `save()`
```java
public FastConfig save()
```
Flushes configuration data to disk atomically:
1. Writes text representation to a temporary file (`<filename>.tmp.<uuid>`).
2. Syncs metadata and contents to physical media via `FileChannel.force(true)` (`fsync`).
3. Executes atomic move (`StandardCopyOption.ATOMIC_MOVE`), with fallback to `REPLACE_EXISTING` if atomic rename across filesystems is unsupported.
- **Throws:** `RuntimeException` on IO failure.
- **Returns:** `this` for fluent chaining.

#### `load()`
```java
public FastConfig load()
```
Reads and parses the configuration file from disk into memory. Only updates internal memory state after parsing completes successfully.
- **Returns:** `this` for fluent chaining.

---

### Retrieval Methods (Getters)

All getters accept a `key` formatted in dot notation (`section.property`) or flat name (`property` -> mapped to `General` section).

#### `getString(String key, String defaultValue)`
```java
public String getString(String key, String defaultValue)
```
Retrieves a string property.

#### `getInt(String key, int defaultValue)`
```java
public int getInt(String key, int defaultValue)
```
Parses and returns an integer property. Returns `defaultValue` if the key is missing or cannot be parsed.

#### `getLong(String key, long defaultValue)`
```java
public long getLong(String key, long defaultValue)
```
Parses and returns a 64-bit integer property.

#### `getDouble(String key, double defaultValue)`
```java
public double getDouble(String key, double defaultValue)
```
Parses and returns a double-precision floating point value.

#### `getBoolean(String key, boolean defaultValue)`
```java
public boolean getBoolean(String key, boolean defaultValue)
```
Strict boolean parser using `Locale.ROOT` case-insensitivity:
- `true`, `yes`, `1`, `on` $\rightarrow$ `true`
- `false`, `no`, `0`, `off` $\rightarrow$ `false`
- All other values return `defaultValue`.

#### `getEnum(String key, Class<E> enumClass, E defaultValue)`
```java
public <E extends Enum<E>> E getEnum(String key, Class<E> enumClass, E defaultValue)
```
Reads an enum constant. Case-insensitive and normalizes hyphens (`-`) and whitespace to underscores (`_`).

#### `getSecret(String key, String defaultValue)`
```java
public String getSecret(String key, String defaultValue)
```
Retrieves and decrypts a DPAPI-protected secret string. If stored as `enc:<base64>`, decrypts via `FastKeychain.unprotectString()`. Returns `defaultValue` if unprotection fails.

---

### Mutation Methods (Setters)

#### `set(String key, Object value)`
```java
public FastConfig set(String key, Object value)
```
Sets a property value. Dot notation splits into section and key (e.g., `audio.volume` becomes section `Audio`, key `volume`). Automatically triggers `save()` if `autoSave` is active.

#### `setSecret(String key, String value)`
```java
public FastConfig setSecret(String key, String value)
```
Encrypts a sensitive plaintext secret using Windows DPAPI via `FastKeychain` and stores it as an `enc:<base64>` string.
- **Security Guarantee:** Throws `IllegalStateException` immediately if encryption fails to prevent accidental plaintext leakage on disk.

#### `remove(String key)`
```java
public FastConfig remove(String key)
```
Removes a property. Automatically triggers `save()` if `autoSave` is active.

---

### Inspection Methods

#### `has(String key)`
```java
public boolean has(String key)
```
Returns `true` if the property exists in its section.

#### `getAll(String section)`
```java
public Map<String, String> getAll(String section)
```
Returns an unmodifiable copy of all key-value entries in the specified section (capitalized). Returns an empty map if the section does not exist.

#### `getSections()`
```java
public Set<String> getSections()
```
Returns an unmodifiable set of all section names currently registered.
