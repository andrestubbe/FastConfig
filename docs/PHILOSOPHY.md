# FastConfig Architectural Philosophy

`FastConfig` is designed according to the core principles of the **FastJava Ecosystem**:

## 1. Zero-Bloat and Zero Unnecessary Dependencies
Traditional Java applications pull in entire multi-megabyte transitive dependency trees (Jackson, SnakeYAML, Apache Commons, or Spring Framework) simply to load a 10-line application settings file. `FastConfig` uses pure Java 17+ and integrates natively with `FastFileFormat` and `FastKeychain`.

## 2. Hardened-by-Default Security
Sensitive user secrets (API keys, authentication tokens, credentials) should never sit as cleartext in local files.
- `FastConfig` encrypts secret properties through Windows Data Protection API (DPAPI) directly tied to the logged-in OS user session.
- If encryption fails, `FastConfig` aborts hard with an exception rather than compromising security by falling back to cleartext disk storage.

## 3. Crash-Proof Atomic Persistence
Corrupt configuration files are a common cause of application startup failures:
- Many libraries overwrite existing files in-place using truncating output streams. A crash or power loss during the write leaves a 0-byte or corrupted file.
- `FastConfig` writes to a randomly identified temporary file, forces filesystem buffers to physical storage (`fsync` / `FileChannel.force(true)`), and completes the write via atomic file moves (`ATOMIC_MOVE`).

## 4. Human-Readable and Section-Aware
Configurations should be effortless to read and edit manually without complex nesting or bracket balancing:
- Simple dot notation (`audio.volume`) seamlessly translates to readable INI-style sections:
  ```ini
  [Audio]
  volume = 0.85
  ```
