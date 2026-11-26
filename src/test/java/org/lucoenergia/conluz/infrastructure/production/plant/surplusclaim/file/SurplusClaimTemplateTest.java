package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Endesa's template is bundled as #633 pins it, and filling it never writes it back.
 */
class SurplusClaimTemplateTest {

    /**
     * The template with its last save location and last author removed, everything else as Endesa publishes it.
     */
    private static final String SHA_256 = "1c1cd91fee566b6c97e68ca7284f8a4528e9ffcff5cfa297cf43692831c89bc8";
    private static final Path SOURCE = Path.of("src/main/resources", EndesaSurplusClaimFileFormat.TEMPLATE);

    @Test
    @DisplayName("SCL-017 AC12 the bundled template is the pinned one, in the sources and on the classpath, before and after files are written")
    void theTemplateIsNeverRewritten() throws Exception {
        assertEquals(SHA_256, sha256(Files.readAllBytes(SOURCE)));
        assertEquals(SHA_256, classpathSha256());

        EndesaSurplusClaimFileFormat format = new EndesaSurplusClaimFileFormat();
        format.write(SurplusClaimFileTestClaims.month(YearMonth.of(2026, 3)).build());
        format.write(SurplusClaimFileTestClaims.month(YearMonth.of(2026, 9)).build());

        assertEquals(SHA_256, sha256(Files.readAllBytes(SOURCE)));
        assertEquals(SHA_256, classpathSha256());
    }

    private static String classpathSha256() throws IOException, NoSuchAlgorithmException {
        try (InputStream template = new ClassPathResource(EndesaSurplusClaimFileFormat.TEMPLATE).getInputStream()) {
            return sha256(template.readAllBytes());
        }
    }

    private static String sha256(byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
