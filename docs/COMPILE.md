# Building FastConfig from Source

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or higher
- **Apache Maven**: Version 3.8+ (or higher)

## Build

To compile and package `FastConfig` locally:

```bash
mvn clean package
```

To run the full unit test suite:

```bash
mvn test
```

To install the resulting artifact into your local `~/.m2` repository:

```bash
mvn clean install
```

---

## Dependency Verification

`FastConfig` depends on the following components from the **FastJava** ecosystem:
- **FastCore** (Unified native substrate & loader)
- **FastFileFormat** (Serialization & text parser)
- **FastKeychain** (Windows DPAPI cryptographic bridge)

All dependencies are resolved automatically via JitPack.
