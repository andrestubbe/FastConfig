package fastconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FastConfigTest {

    enum DemoMode { SIMPLE, ADVANCED, HYPHEN_CASE }

    @Test
    void testBasicPropertiesAndAutoSave(@TempDir Path tempDir) {
        Path cfgPath = tempDir.resolve("test.format");
        FastConfig cfg = FastConfig.file(cfgPath);
        cfg.autoSave(true);

        cfg.set("app.name", "SpeakPlus");
        cfg.set("app.version", 1);
        cfg.set("engine", "windows");
        cfg.set("audio.volume", 0.85);
        cfg.set("audio.enabled", true);
        cfg.set("mode", DemoMode.ADVANCED);

        // Verification in memory
        assertEquals("SpeakPlus", cfg.getString("app.name", "def"));
        assertEquals(1, cfg.getInt("app.version", 0));
        assertEquals("windows", cfg.getString("engine", "def"));
        assertEquals(0.85, cfg.getDouble("audio.volume", 1.0), 0.001);
        assertTrue(cfg.getBoolean("audio.enabled", false));
        assertEquals(DemoMode.ADVANCED, cfg.getEnum("mode", DemoMode.class, DemoMode.SIMPLE));

        // Reload fresh from file to verify persistence
        FastConfig fresh = FastConfig.file(cfgPath).load();
        assertEquals("SpeakPlus", fresh.getString("app.name", "def"));
        assertEquals(1, fresh.getInt("app.version", 0));
        assertEquals("windows", fresh.getString("engine", "def"));
        assertEquals(0.85, fresh.getDouble("audio.volume", 1.0), 0.001);
        assertTrue(fresh.getBoolean("audio.enabled", false));
        assertEquals(DemoMode.ADVANCED, fresh.getEnum("mode", DemoMode.class, DemoMode.SIMPLE));
    }

    @Test
    void testSecretEncryption(@TempDir Path tempDir) {
        Path cfgPath = tempDir.resolve("secret.format");
        FastConfig cfg = FastConfig.file(cfgPath);
        cfg.autoSave(true);

        String secretKey = "sk_live_elevenlabs_secret_token_12345";
        cfg.setSecret("keys.elevenlabs", secretKey);

        // Verification in memory
        assertEquals(secretKey, cfg.getSecret("keys.elevenlabs", null));

        // Verify the file contains encrypted prefix, not the plaintext
        FastConfig fresh = FastConfig.file(cfgPath).load();
        String rawOnDisk = fresh.getString("keys.elevenlabs", null);
        assertNotNull(rawOnDisk);
        assertTrue(rawOnDisk.startsWith("enc:"));
        assertFalse(rawOnDisk.contains(secretKey));

        // Verify transparent unprotect
        assertEquals(secretKey, fresh.getSecret("keys.elevenlabs", null));
    }

    @Test
    void testBooleanAndEnumHardening(@TempDir Path tempDir) {
        Path cfgPath = tempDir.resolve("types.format");
        FastConfig cfg = FastConfig.file(cfgPath);

        cfg.set("bool.on", "on");
        cfg.set("bool.off", "off");
        cfg.set("bool.invalid", "maybe");

        assertTrue(cfg.getBoolean("bool.on", false));
        assertFalse(cfg.getBoolean("bool.off", true));
        assertTrue(cfg.getBoolean("bool.invalid", true)); // Returns default on invalid string

        cfg.set("mode.hyphen", "hyphen-case");
        assertEquals(DemoMode.HYPHEN_CASE, cfg.getEnum("mode.hyphen", DemoMode.class, DemoMode.SIMPLE));
    }
}
