# FastConfig 0.1.1 [ALPHA-2026-10] — Universal Application Configuration Engine for Java

[![Status](https://img.shields.io/badge/status-0.1.1-brightgreen.svg)](https://github.com/andrestubbe/FastConfig/releases/tag/0.1.1)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.1-green.svg)](https://jitpack.io/#andrestubbe/FastConfig)

---

**⚡ Universal high-performance, thread-safe application configuration engine for Java — section-aware dot keys, atomic file persistence, DPAPI secret encryption, and FastFileFormat integration.**

`FastConfig` provides lightweight, robust application configuration management for the FastJava ecosystem. Built on top of **[FastFileFormat](https://github.com/andrestubbe/FastFileFormat)** and **[FastKeychain](https://github.com/andrestubbe/FastKeychain)**, it eliminates heavy XML/YAML parsing overhead while delivering thread-safe read/write operations, section-aware dot navigation, atomic write replacements, and transparent Windows DPAPI secret protection.

---

## Quick Start — Example

```java
import fastconfig.FastConfig;
import java.nio.file.Path;

public class Demo {
    enum ThemeMode { LIGHT, DARK, AUTO }

    public static void main(String[] args) {
        // 1. Resolve application configuration (stored at ~/.myapp/config.format)
        FastConfig cfg = FastConfig.app("myapp").autoSave(true);

        // 2. Set typed values with section-aware dot keys
        cfg.set("app.name", "FastSuite");
        cfg.set("app.version", 1);
        cfg.set("audio.volume", 0.85);
        cfg.set("audio.enabled", true);
        cfg.set("ui.theme", ThemeMode.DARK);

        // 3. Store encrypted API secrets securely (encrypted on-disk via Windows DPAPI)
        cfg.setSecret("keys.elevenlabs", "sk_live_secret_token_12345");

        // 4. Retrieve values with guaranteed type-safe defaults
        String appName   = cfg.getString("app.name", "Unknown");
        int version      = cfg.getInt("app.version", 0);
        double volume    = cfg.getDouble("audio.volume", 1.0);
        boolean enabled  = cfg.getBoolean("audio.enabled", false);
        ThemeMode theme  = cfg.getEnum("ui.theme", ThemeMode.class, ThemeMode.AUTO);
        String apiKey    = cfg.getSecret("keys.elevenlabs", null);

        System.out.printf("Loaded %s v%d | Volume: %.2f | Theme: %s\n", appName, version, volume, theme);
    }
}
```

---

## Table of Contents

- [Why FastConfig?](#why-fastconfig)
- [Key Features](#key-features)
- [Configuration File Format](#configuration-file-format)
- [API Quick Reference](#api-quick-reference)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastConfig?

Most Java configuration libraries force developers into bloated dependencies (Jackson, SnakeYAML, Apache Commons Configuration, or heavy Spring context engines), messy scalar string maps, or unencrypted on-disk secrets. `FastConfig` provides:

- **100% Zero-Bloat Architecture** — Pure Java 17+ core powered by `FastFileFormat`, with no external third-party framework overhead.
- **Section-Aware Dot Key Notation** — Seamlessly navigate and store properties using natural dot notation (`audio.volume` automatically organizes into `[Audio] volume = 0.85`).
- **Hardware-Level DPAPI Secret Security** — Transparently encrypts sensitive API tokens and passwords directly on-disk via Windows DPAPI (`FastKeychain`), preventing plain-text token leaks in source trees and dotfiles.
- **Crash-Proof Atomic Persistence** — Replaces fragile direct file writes with atomic file renames (`ATOMIC_MOVE`) and fsync (`FileChannel.force(true)`), eliminating partial-write file corruption during sudden system shutdowns.
- **Thread-Safe High-Throughput Access** — Protected by fine-grained `ReentrantReadWriteLock` for zero lock contention during hot-path reads.

| Feature | Java Properties / Preferences | Apache Commons / Typesafe Config | FastConfig |
|:---|:---|:---|:---|
| **Secret Encryption** | None (Plaintext on disk) | Manual wrapper / plugin | **Native Windows DPAPI (`enc:<token>`)** |
| **Section Organization**| Flat scalar keys only | Heavy hierarchical trees | **Natural section-aware dot keys** |
| **File Format** | Clunky `.properties` / XML | Complex HOCON / YAML / JSON | **Clean human-readable `.format`** |
| **Write Reliability** | Truncating direct streams | Non-atomic stream writes | **Guaranteed `ATOMIC_MOVE` replacements + fsync** |
| **Dependencies** | JDK built-in | Heavy multi-JAR ecosystems | **Pure lightweight FastJava stack** |

---

## Key Features

* **📁 Automatic App Directory Discovery** — `FastConfig.app("name")` automatically targets `~/.<name>/config.format` across platforms.
* **🎯 Section-Aware Dot Navigation** — Setting `"audio.volume"` automatically maps into `[Audio]` section with key `volume = 0.85`.
* **🔒 Transparent Secret Encryption** — `setSecret` / `getSecret` encrypts sensitive API keys and tokens on-disk using Windows DPAPI (`FastKeychain`), storing them as `enc:<base64>` tokens. Fails hard on error to guarantee no plaintext leaks.
* **🛡️ Atomic File Writes** — Configuration files are saved using atomic temporary write-and-replace moves (`ATOMIC_MOVE`) with fsync to prevent data corruption during power cuts or crashes.
* **⚡ Zero Bloat & High Performance** — Directly powered by `FastFileFormat` text parsers and `ReentrantReadWriteLock` concurrency.
* **💾 Flexible Persistence** — Supports explicit `save()` / `load()` cycles as well as reactive `autoSave(true)`.

---

## Configuration File Format

Saved files utilize the clean, human-readable `.format` syntax from **[FastFileFormat](https://github.com/andrestubbe/FastFileFormat)**:

```ini
# FastConfig Auto-Generated Configuration

[App]
name = FastSuite
version = 1

[Audio]
volume = 0.85
enabled = true

[Ui]
theme = DARK

[Keys]
elevenlabs = enc:AQAAANCMnd8BFdERjHoAwE/Cl+sBAAAA...
```

---

## API Quick Reference

| Method | Return Type | Description |
|:---|:---|:---|
| `FastConfig.app(String appName)` | `FastConfig` | Resolves or creates app configuration at `~/.<appName>/config.format`. |
| `FastConfig.file(Path path)` | `FastConfig` | Loads or creates a shared instance for an arbitrary file path. |
| `getString(String key, String def)` | `String` | Retrieves string property with default fallback. |
| `getInt(String key, int def)` | `int` | Parses integer value with fallback. |
| `getLong(String key, long def)` | `long` | Parses long value with fallback. |
| `getDouble(String key, double def)` | `double` | Parses double value with fallback. |
| `getBoolean(String key, boolean def)` | `boolean` | Strict boolean parser (`true`/`false`, `yes`/`no`, `1`/`0`, `on`/`off`). |
| `getEnum(String key, Class<E> cls, E def)` | `<E extends Enum<E>> E` | Reads enum constant case-insensitively and hyphen-friendly. |
| `getSecret(String key, String def)` | `String` | Transparently decrypts DPAPI-encrypted secret tokens. |
| `setSecret(String key, String secret)` | `FastConfig` | Encrypts and writes secret using Windows DPAPI (hard fails on error). |
| `set(String key, Object val)` | `FastConfig` | Stores key-value pair, mapping dot-notation to sections. |
| `has(String key)` | `boolean` | Checks if key exists. |
| `remove(String key)` | `FastConfig` | Removes key from its section. |
| `clear()` | `FastConfig` | Clears all configuration keys across all sections. |
| `getAll(String section)` | `Map<String, String>` | Returns snapshot of all keys and values in a section. |
| `getSections()` | `Set<String>` | Returns set of all registered section names. |
| `autoSave(boolean enable)` | `FastConfig` | Toggles automatic disk flush on every `set` / `remove`. |
| `save()` | `FastConfig` | Flushes data to disk atomically with fsync. |
| `load()` | `FastConfig` | Re-reads configuration file from disk. |

---

## Installation

### Option 1: Maven (Recommended)

Add JitPack repository and `FastConfig` to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastConfig Core -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastConfig</artifactId>
        <version>0.1.1</version>
    </dependency>

    <!-- FastFileFormat Serialization Engine -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastFileFormat</artifactId>
        <version>0.1.1</version>
    </dependency>

    <!-- FastKeychain DPAPI Security Engine -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastKeychain</artifactId>
        <version>0.1.0</version>
    </dependency>

    <!-- FastCore JNI Bridge -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastConfig:0.1.1'
    implementation 'com.github.andrestubbe:FastFileFormat:0.1.1'
    implementation 'com.github.andrestubbe:FastKeychain:0.1.0'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

---

## Documentation

- **[REFERENCE.md](docs/REFERENCE.md)**: Exhaustive catalog of API contracts, persistence lifecycle, and security guarantees.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: Zero-bloat, hardened secret protection, and crash-proof atomic persistence rationale.
- **[ROADMAP.md](docs/ROADMAP.md)**: Planned milestone features, hot reload, and cross-platform security extensions.
- **[CHANGELOG.md](docs/CHANGELOG.md)**: Version history and release notes.
- **[COMPILE.md](docs/COMPILE.md)**: Full compilation guide (Maven build workflow and tests).

---

## Platform Support

| Platform | Architecture | Status | Notes |
|:---|:---:|:---:|:---|
| **Windows 10 / 11** | x64 | ✅ Fully Supported | Full DPAPI encryption via `FastKeychain` |
| **Linux (Ubuntu / RHEL)** | x64 / AArch64 | 🚧 Planned | Full config parsing active; DPAPI fallback to plaintext |
| **macOS (Sonoma+)** | Apple Silicon / x64 | 🚧 Planned | Full config parsing active; Keychain bridge planned |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastCore](https://github.com/andrestubbe/FastCore) — Native library loader, FFM gateway, and platform abstraction
- [FastFileFormat](https://github.com/andrestubbe/FastFileFormat) — High-performance `.format` and `.formatbin` serialization engine
- [FastKeychain](https://github.com/andrestubbe/FastKeychain) — Native Windows DPAPI cryptographic storage for Java
- [FastJSON](https://github.com/andrestubbe/FastJSON) — Ultra-fast zero-allocation JSON parser for Java

---

Part of the FastJava Ecosystem — Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀
