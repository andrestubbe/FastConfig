# FastConfig Changelog

All notable changes to `FastConfig` will be documented in this file.

## [0.1.1] - 2026-10-11

### Security & Hardening
- **P0 DPAPI Secret Protection Hardening**: `setSecret()` now throws `IllegalStateException` immediately upon DPAPI encryption failure instead of silently falling back to plaintext storage, eliminating secret token leakage in source trees and configuration files.
- **P1 Crash-Proof Atomic File Persistence**: 
  - File saving now invokes `FileChannel.force(true)` (`fsync`) prior to file moves to guarantee physical disk sync.
  - Implemented automatic fallback to safe `REPLACE_EXISTING` copy when `AtomicMoveNotSupportedException` occurs (e.g., across separate filesystem mount points).
  - Temporary files now use a per-write random UUID suffix (`.tmp.<uuid>`) to prevent concurrency collisions.
- **P1 Resilient `load()`**: Configuration state is only updated after the source file has been completely and successfully read and parsed into memory.

### Added
- **API Inspection Methods**:
  - `getAll(String section)`: Returns an unmodifiable `Map<String, String>` snapshot of all key-value pairs within a section.
  - `getSections()`: Returns an unmodifiable `Set<String>` of all configured section names.
- **Documentation**: Added comprehensive `/docs` directory including [REFERENCE.md](REFERENCE.md), [CHANGELOG.md](CHANGELOG.md), and [PHILOSOPHY.md](PHILOSOPHY.md).

### Changed & Improved
- **P2 Stricter `getBoolean()`**: Implemented strict switch expression mapping (`true`/`false`, `yes`/`no`, `1`/`0`, `on`/`off`) with `Locale.ROOT` normalization; invalid values (e.g. `"maybe"`) cleanly return the provided default value.
- **P2 Robust `getEnum()`**: Added case-insensitivity and automatic hyphen-case normalization (`-` and space mapped to `_`).
- **Tests**: Expanded test suite to 3 test suites verifying atomic auto-save, secret encryption/decryption, and hardened boolean/enum parsing.

---

## [0.1.0] - 2026-10-11

### Added
- Initial public release of `FastConfig`.
- Application configuration targeting (`FastConfig.app("name")` -> `~/.<name>/config.format`).
- Arbitrary file target loading via `FastConfig.file(path)`.
- Section-aware dot notation navigation (`audio.volume` -> `[Audio] volume = 0.85`).
- FastFileFormat text parser integration with thread-safe `ReentrantReadWriteLock`.
- Windows DPAPI encryption via FastKeychain (`enc:<base64>`).
- Auto-save option (`autoSave(true)`).
