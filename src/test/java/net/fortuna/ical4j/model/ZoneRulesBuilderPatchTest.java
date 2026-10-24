package net.fortuna.ical4j.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the patched {@link ZoneRulesBuilder} copy (ical4j#903)
 */
class ZoneRulesBuilderPatchTest {

    @Test
    void ical4jVersion_isTheOneThePatchIsBasedOn() throws IOException, URISyntaxException {
        Path ical4jJar = Path.of(Calendar.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile jar = new JarFile(ical4jJar.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Bundle-Version"))
                    .as("ical4j was upgraded: if the release fixes https://github.com/ical4j/ical4j/issues/903, "
                            + "delete the patched src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java and this test; "
                            + "otherwise re-apply the patch to the new version's source")
                    .isEqualTo("4.4.0");
        }
    }

    @Test
    void patchedClass_shadowsTheJar() {
        String patched = ZoneRulesBuilder.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String jar = Calendar.class.getProtectionDomain().getCodeSource().getLocation().toString();

        assertThat(patched).isNotEqualTo(jar);
    }

    @Test
    void moscowAfterAbolishedDst_isPlusThree() {
        var rules = TimeZoneRegistry.getGlobalZoneId("Europe/Moscow").getRules();

        assertThat(rules.getOffset(Instant.parse("2026-07-01T12:00:00Z"))).isEqualTo(ZoneOffset.ofHours(3));
        assertThat(rules.getOffset(Instant.parse("2026-10-25T12:00:00Z"))).isEqualTo(ZoneOffset.ofHours(3));
    }
}
