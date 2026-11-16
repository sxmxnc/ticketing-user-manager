package de.fhms.sweng.usermanagement.inbound.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Inbound-Adapter (Security): erstellt und validiert JSON Web Tokens (JWT).
 *
 * <p>Asymmetrisches RSA-Verfahren: Der <b>private</b> Schlüssel signiert ausgehende Tokens
 * (nur dieser Service kann gültige Tokens ausstellen), der <b>öffentliche</b> Schlüssel prüft die
 * Signatur eingehender Tokens (kann an andere Microservices verteilt werden).</p>
 *
 * <p>Das Laden der Schlüssel ist bewusst an einer einzigen Stelle gekapselt
 * ({@link #schluesselLaden()}): Würde die Quelle später von Ressourcen-Dateien auf
 * Umgebungsvariablen umgestellt, betrifft das nur diese Methode — Token-Erstellung und
 * -Validierung bleiben unverändert.</p>
 *
 * <p>Sicherheit: Es werden niemals Tokens, Benutzernamen oder andere PII geloggt.</p>
 */
@Component
public class JwtProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtProvider.class);

    /** Name des Claims, der die Rollen (Authorities) des Nutzers trägt. */
    private static final String CLAIM_ROLES = "roles";

    /** Name des Claims, der die fachliche NutzerID (UUID als String) trägt. */
    private static final String CLAIM_USER_ID = "userId";

    /** Gültigkeitsdauer eines Tokens in Millisekunden (Default: 1 Stunde). */
    @Value("${security.jwt.token.expire-length:3600000}")
    private long gueltigkeitsdauerMs;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * Lädt nach der Bean-Initialisierung das RSA-Schlüsselpaar aus dem Classpath.
     * Einzige Stelle, die die Herkunft der Schlüssel kennt (Resource-Dateien {@code privateKey}
     * im PKCS8-Format und {@code publicKey} im X509-Format).
     *
     * @throws IOException             wenn eine Schlüsseldatei nicht gelesen werden kann
     * @throws NoSuchAlgorithmException wenn RSA nicht verfügbar ist (sollte nie passieren)
     * @throws InvalidKeySpecException  wenn die Schlüsseldatei kein gültiges Schlüsselformat enthält
     */
    @PostConstruct
    void schluesselLaden() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = ladeRessource("privateKey");
        this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        byte[] publicBytes = ladeRessource("publicKey");
        this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));

        LOGGER.info("schluesselLaden() — RSA-Schlüsselpaar erfolgreich geladen");
    }

    /**
     * Liest eine Ressourcen-Datei vom Classpath als Byte-Array.
     *
     * @param name der Dateiname im Classpath (z. B. "privateKey")
     * @return der Dateiinhalt als Byte-Array
     * @throws IOException wenn die Datei nicht gelesen werden kann
     */
    private byte[] ladeRessource(String name) throws IOException {
        try (InputStream inputStream = new ClassPathResource(name).getInputStream()) {
            return IOUtils.toByteArray(inputStream);
        }
    }

    /**
     * Erstellt ein signiertes JWT für einen authentifizierten Nutzer.
     *
     * <p>Neben dem Subject (E-Mail) und den Rollen wird die fachliche NutzerID als eigener Claim
     * {@code userId} (UUID als String) abgelegt. Andere Microservices (Order, Payment, Eventmanager)
     * lesen diesen Claim als verbindliche, stabile Nutzer-Identität — die E-Mail im Subject ist
     * änderbar, die UUID nicht. Dadurch bleibt die fachliche Korrelation (z. B. Sitzplatz-Zuordnung)
     * über Service-Grenzen hinweg eindeutig.</p>
     *
     * @param benutzername die E-Mail des Nutzers (wird als Subject gespeichert)
     * @param userId       die fachliche NutzerID als UUID-String (Claim "userId")
     * @param rollen       die Rollen des Nutzers (werden als Liste im Claim "roles" abgelegt)
     * @return das kompakte, signierte JWT als String
     */
    public String erstelleToken(String benutzername, String userId,
                                Collection<? extends GrantedAuthority> rollen) {
        // Rollennamen als einfache String-Liste in den Claim schreiben (mehrere Rollen möglich)
        List<String> rollenNamen = new ArrayList<>();
        for (GrantedAuthority rolle : rollen) {
            rollenNamen.add(rolle.getAuthority());
        }

        Date jetzt = new Date();
        Date ablauf = new Date(jetzt.getTime() + gueltigkeitsdauerMs);

        LOGGER.info("erstelleToken() — JWT wird ausgestellt");
        return Jwts.builder()
                .subject(benutzername)
                // Stabile fachliche Identität für konsumierende Services (nicht die änderbare E-Mail)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, rollenNamen)
                .issuedAt(jetzt)
                .expiration(ablauf)
                // Explizit RS512 — passend zum erzeugten Schlüsselpaar
                .signWith(privateKey, Jwts.SIG.RS512)
                .compact();
    }

    /**
     * Validiert ein Token und baut daraus die Spring-Authentication.
     *
     * <p>Parst und verifiziert das Token <b>genau einmal</b> (Signatur + Ablauf) und liest in einem
     * Durchgang Benutzername und Rollen aus. Bei ungültigem, manipuliertem oder abgelaufenem Token
     * wird die von jjwt geworfene {@link JwtException} hier abgefangen und ein leeres Optional
     * zurückgegeben — der Aufrufer (Filter) muss sich nicht um Exceptions kümmern.</p>
     *
     * @param token das zu prüfende JWT
     * @return die Authentication (Benutzername + Rollen) bei gültigem Token, sonst ein leeres Optional
     */
    public Optional<Authentication> leseAuthentifizierung(String token) {
        try {
            Claims claims = leseClaims(token);
            String benutzername = claims.getSubject();
            List<SimpleGrantedAuthority> rollen = leseRollen(claims);
            // Credentials = null: das Passwort ist nach erfolgreicher Token-Prüfung nicht mehr relevant
            Authentication authentifizierung =
                    new UsernamePasswordAuthenticationToken(benutzername, null, rollen);
            return Optional.of(authentifizierung);
        } catch (JwtException | IllegalArgumentException ex) {
            // Kein Token loggen — nur der Fehlertyp ist diagnostisch relevant
            LOGGER.warn("leseAuthentifizierung() — Token ungültig oder abgelaufen: {}",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Wandelt die Rollen aus den Claims in Spring-Authorities um.
     *
     * @param claims die bereits verifizierten Claims
     * @return die Rollen als Liste von Authorities (niemals null)
     */
    private List<SimpleGrantedAuthority> leseRollen(Claims claims) {
        List<?> rohRollen = claims.get(CLAIM_ROLES, List.class);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (rohRollen != null) {
            for (Object rolle : rohRollen) {
                authorities.add(new SimpleGrantedAuthority(rolle.toString()));
            }
        }
        return authorities;
    }

    /**
     * Parst und verifiziert ein Token gegen den öffentlichen Schlüssel und gibt seine Claims zurück.
     *
     * <p>Diese Methode enthält selbst kein {@code throw}: Der Aufruf
     * {@code parseSignedClaims(token)} prüft Signatur und Ablaufzeit und wirft <b>innerhalb von
     * jjwt</b> eine {@link JwtException} (eine unchecked RuntimeException), sobald das Token
     * ungültig, manipuliert oder abgelaufen ist. Diese Methode reicht sie nur an den Aufrufer
     * weiter — {@link #leseAuthentifizierung(String)} fängt sie dort ab.</p>
     *
     * @param token das JWT
     * @return die verifizierten Claims (Payload)
     * @throws JwtException von jjwt ausgelöst bei ungültiger Signatur oder abgelaufenem Token
     */
    private Claims leseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
