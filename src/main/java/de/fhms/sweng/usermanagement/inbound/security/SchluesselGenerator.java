package de.fhms.sweng.usermanagement.inbound.security;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Einmaliges Hilfsprogramm (Dev-Tool) zum Erzeugen des RSA-Schlüsselpaars für die JWT-Signatur.
 *
 * <p>Schreibt zwei Dateien nach {@code src/main/resources}:
 * <ul>
 *   <li>{@code privateKey} — PKCS8-kodiert (DER), wird vom {@link JwtProvider} zum Signieren geladen.</li>
 *   <li>{@code publicKey}  — X509-kodiert (DER), wird vom {@link JwtProvider} zum Validieren geladen
 *       und kann an andere Microservices verteilt werden.</li>
 * </ul>
 *
 * <p>Asymmetrisches Verfahren: Nur der Inhaber des privaten Schlüssels kann gültige Tokens ausstellen;
 * jeder mit dem öffentlichen Schlüssel kann die Signatur prüfen, ohne selbst Tokens ausstellen zu können.
 *
 * <p>Dieses Programm wird nicht zur Laufzeit der Anwendung ausgeführt, sondern einmalig manuell,
 * um lokale Schlüsseldateien zu erzeugen.</p>
 */
public final class SchluesselGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchluesselGenerator.class);

    /** Zielverzeichnis für die Schlüsseldateien. */
    private static final Path ZIEL_VERZEICHNIS = Path.of("src", "main", "resources");

    private SchluesselGenerator() {
        // Utility-Klasse: keine Instanzen
    }

    /**
     * Erzeugt ein RSA-Schlüsselpaar (RS512) und schreibt privaten und öffentlichen Schlüssel
     * als DER-Bytes in das Ressourcen-Verzeichnis.
     *
     * @param args werden nicht verwendet
     * @throws IOException wenn das Schreiben einer Schlüsseldatei fehlschlägt
     */
    public static void main(String[] args) throws IOException {
        LOGGER.info("main() — RSA-Schlüsselpaar wird erzeugt");

        KeyPair schluesselpaar = Jwts.SIG.RS512.keyPair().build();
        PrivateKey privaterSchluessel = schluesselpaar.getPrivate();
        PublicKey oeffentlicherSchluessel = schluesselpaar.getPublic();

        // Privater Schlüssel im PKCS8-Format (Standard für PrivateKey.getEncoded())
        PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privaterSchluessel.getEncoded());
        Files.write(ZIEL_VERZEICHNIS.resolve("privateKey"), privateSpec.getEncoded());

        // Öffentlicher Schlüssel im X509-Format (Standard für PublicKey.getEncoded())
        X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(oeffentlicherSchluessel.getEncoded());
        Files.write(ZIEL_VERZEICHNIS.resolve("publicKey"), publicSpec.getEncoded());

        LOGGER.info("main() — Schlüsseldateien privateKey und publicKey wurden geschrieben");
    }
}
