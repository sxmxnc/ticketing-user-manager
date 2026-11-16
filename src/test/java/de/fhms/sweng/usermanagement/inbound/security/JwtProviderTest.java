package de.fhms.sweng.usermanagement.inbound.security;

import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests für {@link JwtProvider}. Lädt die committed Schlüsseldateien vom Test-Classpath
 * (das Ressourcen-Verzeichnis liegt auch im Test-Classpath) und prüft den Roundtrip
 * Erstellen → Validieren/Auslesen ({@code leseAuthentifizierung}) sowie die Ablehnung
 * ungültiger/abgelaufener Tokens (leeres Optional).
 */
class JwtProviderTest {

    private static final String BENUTZER = "demo@example.test";
    private static final String USER_ID = "3f0d6a2e-1c4b-4e8a-9b1f-2a6c7d8e9f01";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        jwtProvider = new JwtProvider();
        // @Value wird ohne Spring-Kontext nicht injiziert → Gültigkeitsdauer manuell setzen (1 Stunde)
        ReflectionTestUtils.setField(jwtProvider, "gueltigkeitsdauerMs", 3_600_000L);
        // @PostConstruct läuft ohne Spring-Kontext nicht automatisch → Schlüssel manuell laden
        jwtProvider.schluesselLaden();
    }

    @Test
    void leseAuthentifizierung_frischesToken_liefertBenutzerUndRollen() {
        String token = jwtProvider.erstelleToken(BENUTZER, USER_ID,
                List.of(Nutzerrolle.ADMINISTRATOR, Nutzerrolle.CUSTOMER));

        Optional<Authentication> ergebnis = jwtProvider.leseAuthentifizierung(token);

        assertThat(ergebnis).isPresent();
        Authentication authentifizierung = ergebnis.get();
        assertThat(authentifizierung.getName()).isEqualTo(BENUTZER);
        assertThat(authentifizierung.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ADMINISTRATOR", "CUSTOMER");
    }

    @Test
    void leseAuthentifizierung_manipuliertesToken_istLeer() {
        String token = jwtProvider.erstelleToken(BENUTZER, USER_ID, List.of(Nutzerrolle.PROSPECT));
        // Signatur verfälschen: die letzten Zeichen ersetzen → Signaturprüfung schlägt fehl
        String manipuliert = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(jwtProvider.leseAuthentifizierung(manipuliert)).isEmpty();
    }

    @Test
    void leseAuthentifizierung_kaputterString_istLeer() {
        assertThat(jwtProvider.leseAuthentifizierung("kein.gueltiges.token")).isEmpty();
    }

    @Test
    void leseAuthentifizierung_abgelaufenesToken_istLeer() {
        // Negative Gültigkeitsdauer → Ablaufzeitpunkt liegt in der Vergangenheit
        ReflectionTestUtils.setField(jwtProvider, "gueltigkeitsdauerMs", -1_000L);
        String abgelaufen = jwtProvider.erstelleToken(BENUTZER, USER_ID, List.of(Nutzerrolle.PROSPECT));

        assertThat(jwtProvider.leseAuthentifizierung(abgelaufen)).isEmpty();
    }

    @Test
    void leseAuthentifizierung_leererString_istLeer() {
        // Leerer Token-String → jjwt wirft IllegalArgumentException (nicht JwtException) →
        // deckt den zweiten Zweig des Multi-Catch (JwtException | IllegalArgumentException) ab.
        assertThat(jwtProvider.leseAuthentifizierung("")).isEmpty();
    }

    @Test
    void leseAuthentifizierung_tokenOhneRollen_liefertLeereAuthorities() {
        // Token mit leerer Rollenliste → die for-Schleife in leseRollen läuft nicht (Schleifen-Zweig).
        String token = jwtProvider.erstelleToken(BENUTZER, USER_ID, List.of());

        Optional<Authentication> ergebnis = jwtProvider.leseAuthentifizierung(token);

        assertThat(ergebnis).isPresent();
        assertThat(ergebnis.get().getAuthorities()).isEmpty();
    }

    @Test
    void erstelleToken_enthaeltUserIdClaim() {
        // Der userId-Claim (UUID) ist die verbindliche, stabile Nutzer-Identität für andere Services
        // (Order, Payment, Eventmanager). Token mit dem öffentlichen Schlüssel verifizieren und den
        // Claim direkt auslesen.
        String token = jwtProvider.erstelleToken(BENUTZER, USER_ID, List.of(Nutzerrolle.CUSTOMER));

        PublicKey publicKey = (PublicKey) ReflectionTestUtils.getField(jwtProvider, "publicKey");
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("userId", String.class)).isEqualTo(USER_ID);
        assertThat(claims.getSubject()).isEqualTo(BENUTZER);
    }

    @Test
    void leseAuthentifizierung_tokenOhneRollenClaim_liefertLeereAuthorities() {
        // Token, dem der "roles"-Claim komplett fehlt → claims.get("roles") liefert null →
        // deckt den rohRollen != null -> false-Zweig in leseRollen ab. Mit dem privaten Schlüssel
        // des Providers signiert, damit die Signaturprüfung gelingt.
        PrivateKey privateKey = (PrivateKey) ReflectionTestUtils.getField(jwtProvider, "privateKey");
        String tokenOhneClaim = Jwts.builder()
                .subject(BENUTZER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();

        Optional<Authentication> ergebnis = jwtProvider.leseAuthentifizierung(tokenOhneClaim);

        assertThat(ergebnis).isPresent();
        assertThat(ergebnis.get().getAuthorities()).isEmpty();
    }
}
