package de.fhms.sweng.usermanagement.inbound.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Test für SecurityConfig — kein Spring-Kontext nötig.
 * Testet das Verhalten des BCryptPasswordEncoders: korrektes Hashing und Verifikation.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void passwordEncoder_korrektesPaswortWirdVerifiziert() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String hash = encoder.encode("Geheim1234!");
        assertTrue(encoder.matches("Geheim1234!", hash));
    }

    @Test
    void passwordEncoder_falschesPaswortWirdAbgelehnt() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String hash = encoder.encode("Geheim1234!");
        assertFalse(encoder.matches("FalschesPasswort!", hash));
    }

    @Test
    void passwordEncoder_gleichesPasswortErgibtVerschiedeneHashes() {
        // BCrypt salzt automatisch — zwei Hashes desselben Passworts sind nie identisch
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String hash1 = encoder.encode("Geheim1234!");
        String hash2 = encoder.encode("Geheim1234!");
        assertNotEquals(hash1, hash2);
    }
}
