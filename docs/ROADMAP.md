# FastConfig Roadmap

Future milestones, enhancements, and planned features for `FastConfig`.

## Milestones

### Version 0.1.0 & 0.1.1 (Current)
- [x] Application-level configuration targeting (`FastConfig.app("name")` -> `~/.<name>/config.format`).
- [x] Arbitrary file target binding (`FastConfig.file(path)`).
- [x] Section-aware dot navigation (`audio.volume` -> `[Audio] volume = 0.85`).
- [x] Hardware-level Windows DPAPI secret protection via `FastKeychain` (`enc:<base64>`).
- [x] Hard fail on secret encryption failure to guarantee zero plaintext leaks.
- [x] Crash-proof atomic persistence (`ATOMIC_MOVE` + `FileChannel.force(true)` fsync).
- [x] Thread-safe concurrency via `ReentrantReadWriteLock`.
- [x] Section inspection APIs (`getAll(section)`, `getSections()`).
- [x] Comprehensive documentation (`REFERENCE.md`, `CHANGELOG.md`, `PHILOSOPHY.md`, `COMPILE.md`).

### Version 0.2.0 (Planned)
- [ ] Reactive file watching integration via `FastFileWatch` for hot reload on disk edits.
- [ ] Cross-platform secret storage bridges (Linux Secret Service / macOS Keychain).
- [ ] Direct Memory-Mapped zero-copy configuration deserializer for ultra-large config files.
- [ ] Schema validation and strongly-typed POJO / Record mapping.
