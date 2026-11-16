package de.fhms.sweng.usermanagement.inbound.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Sicherheitskonfiguration des UserManagers (Inbound-Adapter: Security).
 * Legt fest, welche Endpunkte ohne Authentifizierung erreichbar sind, hängt den
 * {@link JwtAuthenticationFilter} in die Filterkette ein und konfiguriert die
 * Session-Strategie (STATELESS für JWT-basierte REST-APIs) sowie den
 * AuthenticationManager für den Login.
 */
@Configuration
@EnableMethodSecurity  // aktiviert @PreAuthorize auf Controller-Methoden
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Definiert die HTTP-Sicherheitsregeln des Microservice.
     * STATELESS: Kein Server-seitiger Session-State — jeder Request trägt sein JWT selbst.
     * CSRF deaktiviert: REST-APIs mit JWT brauchen keinen CSRF-Schutz (kein Browser-Cookie-Auth).
     *
     * <p>Der {@link JwtAuthenticationFilter} wird hier instanziiert (bewusst keine Bean) und vor dem
     * {@code UsernamePasswordAuthenticationFilter} eingehängt — so läuft er innerhalb der
     * Security-Kette an der richtigen Stelle.</p>
     *
     * @param http        der HttpSecurity-Builder von Spring Security
     * @param jwtProvider der Dienst zum Validieren von Tokens (für den Filter)
     * @return fertig konfigurierte SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider) {
        LOGGER.info("filterChain() — Sicherheitskonfiguration wird aufgebaut");

        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtProvider);

        try {
            return http
                    // CORS deaktiviert — das API-Gateway übernimmt Cross-Origin-Handling
                    .cors(cors -> cors.disable())
                    // CSRF deaktiviert: REST-API mit JWT-Auth, kein Cookie-basiertes Login
                    .csrf(csrf -> csrf.disable())
                    // STATELESS: Spring Security legt keine HttpSession an
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(registry -> registry
                            // Healthcheck-Endpunkt muss ohne Auth erreichbar sein (Kubernetes Readiness-Probe)
                            .requestMatchers("/actuator/**").permitAll()
                            // H2-Konsole freigeben: PathRequest.toH2Console() liest den konfigurierten
                            // H2-Pfad aus. PathRequest wurde in SB4 nur verschoben (nicht entfernt) nach
                            // org.springframework.boot.security.autoconfigure.web.servlet.
                            .requestMatchers(PathRequest.toH2Console()).permitAll()
                            // Swagger-UI und OpenAPI-Docs öffentlich (Entwicklung und Doku)
                            .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            // Fehlerseite von Tomcat muss erreichbar sein
                            .requestMatchers("/error").permitAll()
                            // Registrierung und Login sind öffentlich — der Nutzer hat noch kein JWT
                            .requestMatchers("/rest/users/register", "/rest/users/login").permitAll()
                            // Admin-Bereich nur für die Rolle ADMINISTRATOR (hasAuthority, da ohne ROLE_-Präfix)
                            .requestMatchers("/rest/admin/**").hasAuthority("ADMINISTRATOR")
                            // Alle anderen Anfragen erfordern Authentifizierung
                            .anyRequest().authenticated()
                    )
                    // Ohne eigenen EntryPoint antwortet Spring Security bei fehlender
                    // Authentifizierung standardmaessig mit Forbidden. Fuer eine JWT-API ist
                    // Unauthorized korrekt, wenn gar kein gueltiges Token vorliegt. Der
                    // Forbidden-Fall bleibt der unzureichenden Rolle vorbehalten.
                    .exceptionHandling(eh -> eh.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    // JWT-Filter vor dem Standard-Login-Filter ausführen: liest Token, setzt Authentication
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    // H2-Konsole nutzt iFrames — frameOptions muss deaktiviert sein
                    .headers(headers -> headers.frameOptions(header -> header.disable()))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Fehler beim Aufbau der SecurityFilterChain", ex);
        }
    }

    /**
     * Stellt den AuthenticationManager bereit, der beim Login die Anmeldedaten prüft.
     *
     * <p>Der {@link DaoAuthenticationProvider} lädt die Nutzerdaten über den
     * {@link UserDetailsService} und vergleicht das eingegebene Passwort mit dem gespeicherten
     * Hash über den {@link PasswordEncoder}. Der Encoder wird hier explizit gesetzt, weil unsere
     * gespeicherten Hashes reine BCrypt-Hashes ohne Algorithmus-Präfix sind.</p>
     *
     * @param userDetailsService lädt Nutzer anhand der E-Mail
     * @param passwordEncoder    vergleicht Klartext-Passwort und gespeicherten Hash
     * @return der konfigurierte AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        LOGGER.info("authenticationManager() — DaoAuthenticationProvider wird registriert");
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }

    /**
     * BCrypt als Passwort-Hashing-Algorithmus.
     * BCrypt salzt automatisch (kein separates Salt-Management nötig) und ist
     * bewusst langsam (Work-Factor), um Brute-Force-Angriffe zu erschweren.
     *
     * @return BCryptPasswordEncoder-Instanz
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        LOGGER.info("passwordEncoder() — BCryptPasswordEncoder wird registriert");
        // BCryptPasswordEncoder gewählt, weil er automatisch salzt
        return new BCryptPasswordEncoder();
    }
}
