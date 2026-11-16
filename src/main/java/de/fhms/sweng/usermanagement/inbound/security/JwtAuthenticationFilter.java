package de.fhms.sweng.usermanagement.inbound.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Inbound-Adapter (Security): prüft bei jedem Request das mitgelieferte JWT.
 *
 * <p>Läuft als {@link OncePerRequestFilter} genau einmal pro Anfrage und wird in der
 * {@code SecurityFilterChain} vor dem {@code UsernamePasswordAuthenticationFilter} eingehängt.
 * Liegt ein gültiges Token im {@code Authorization: Bearer ...}-Header, wird die Authentifizierung
 * (Benutzername + Rollen) in den {@link SecurityContextHolder} gesetzt, sodass die nachfolgenden
 * Autorisierungsregeln greifen.</p>
 *
 * <p>Fehlt das Token oder ist es ungültig, setzt der Filter keine Authentication und reicht die
 * Anfrage unverändert weiter — die Zugriffsentscheidung (permitAll vs. authenticated) trifft dann
 * die {@code SecurityFilterChain}. Der Filter blockt also nicht selbst.</p>
 *
 * <p>Bewusst <b>keine</b> Spring-Bean ({@code @Component}): Spring Boot würde eine Filter-Bean
 * automatisch als äußersten Servlet-Filter registrieren — dort würde die gesetzte Authentication
 * vom {@code SecurityContextHolderFilter} der Security-Kette wieder überschrieben. Stattdessen
 * instanziiert die {@code SecurityConfig} den Filter und hängt ihn per {@code addFilterBefore}
 * an der korrekten Position innerhalb der Kette ein.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Name des HTTP-Headers, der das Token trägt. */
    private static final String JWT_HEADER = "Authorization";

    /** Präfix vor dem eigentlichen Token im Header-Wert. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    /**
     * Konstruktor-Injektion des JwtProviders.
     *
     * @param jwtProvider der Dienst zum Validieren und Auslesen von Tokens
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Prüft das Token der aktuellen Anfrage und setzt bei Gültigkeit die Authentication.
     *
     * @param request     die eingehende HTTP-Anfrage
     * @param response    die HTTP-Antwort
     * @param filterChain die Filterkette (wird immer fortgesetzt)
     * @throws ServletException bei Filter-Fehlern
     * @throws IOException      bei E/A-Fehlern in der Filterkette
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = leseToken(request);
        if (token != null) {
            // Token genau einmal prüfen und auslesen; bei Ungültigkeit kommt ein leeres Optional zurück
            Optional<Authentication> authentifizierung = jwtProvider.leseAuthentifizierung(token);
            if (authentifizierung.isPresent()) {
                // Neuen Context erzeugen statt bestehenden mutieren — threadsicher auch mit Virtual Threads
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentifizierung.get());
                SecurityContextHolder.setContext(context);
                LOGGER.info("doFilterInternal() — Anfrage mit gültigem Token authentifiziert");
            }
        }
        // Die Kette wird immer fortgesetzt. Ein fehlendes oder ungueltiges Token bricht die Anfrage
        // nicht ab. Ueber den Zugriff entscheidet die SecurityFilterChain (permitAll vs. authenticated).
        filterChain.doFilter(request, response);
    }

    /**
     * Extrahiert das reine Token aus dem Authorization-Header.
     *
     * @param request die HTTP-Anfrage
     * @return das Token ohne "Bearer "-Präfix, oder null wenn kein Bearer-Header vorhanden ist
     */
    private String leseToken(HttpServletRequest request) {
        String header = request.getHeader(JWT_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
