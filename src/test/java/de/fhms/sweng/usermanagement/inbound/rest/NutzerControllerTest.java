package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.application.NutzerService;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.FalschesPasswortException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.inbound.rest.dto.AdresseDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.EmailAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.NameAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.LoginDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.PasswortAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.ProfilAktualisierungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.RegistrierungsDto;
import de.fhms.sweng.usermanagement.inbound.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test-Slice für {@link NutzerController} (Inbound-Adapter REST).
 *
 * <p>{@code @WebMvcTest} lädt nur die Web-Schicht: Controller, DispatcherServlet, Jackson
 * und alle {@code @RestControllerAdvice}-Klassen (also auch den {@link ExceptionHandler}).
 * JPA, RabbitMQ und andere Beans werden nicht gestartet — der Test ist dadurch sehr schnell.</p>
 *
 * <p>{@code addFilters = false}: Die Spring-Security-Filterkette wird deaktiviert, damit die
 * Endpunkte ohne JWT erreichbar sind. Den für {@code /me} nötigen angemeldeten Nutzer liefert der
 * Test über {@code .principal(...)} — das setzt {@code request.getUserPrincipal()}, woraus Spring
 * MVC den {@code Authentication}-Parameter auflöst. Die echte Token-/Rollenprüfung deckt der
 * {@code SecurityIntegrationTest} ab.</p>
 *
 * <p>{@code @MockitoBean}: In Spring Boot 4 ersetzt diese Annotation das ältere {@code @MockBean}.</p>
 */
@WebMvcTest(NutzerController.class)
@AutoConfigureMockMvc(addFilters = false)
class NutzerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper aus dem Spring-Context — derselbe Jackson-3-Mapper, den die App zur Laufzeit nutzt
    @Autowired
    private ObjectMapper objectMapper;

    // NutzerService wird gemockt — kein Spring-Kontext, kein echtes Repository
    @MockitoBean
    private NutzerService nutzerService;

    // Login nutzt AuthenticationManager (Prüfung) und JwtProvider (Token-Ausstellung) — beide gemockt
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtProvider jwtProvider;

    /** Hilfsmethode: erzeugt ein gültiges Testkonto ohne Spring-Kontext. */
    private NutzerAccount testKonto() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        return NutzerAccount.registrieren(
                new EmailAdresse("simon@example.test"), hash, new PersonenName("Max", "Mustermann"));
    }

    /** Hilfsmethode: simuliert den angemeldeten Nutzer (Principal = E-Mail) für die /me-Endpunkte. */
    private Authentication angemeldet(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    // ------------------------------------------------------------------ registrieren

    @Test
    void registrieren_gueltigeDaten_gibt201() throws Exception {
        // Service gibt ein gültiges Konto zurück
        NutzerAccount konto = testKonto();
        when(nutzerService.registriere(any(), any(), any(), any())).thenReturn(konto);

        RegistrierungsDto dto = new RegistrierungsDto("simon@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("simon@example.test"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void registrieren_ungueltigeEmail_gibt400() throws Exception {
        // Service wirft IllegalArgumentException (EmailAdresse-VO validiert)
        when(nutzerService.registriere(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Ungültige E-Mail-Adresse"));

        RegistrierungsDto dto = new RegistrierungsDto("keineEmail", "GeheimesPasswort1!", "Max", "Mustermann");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrieren_emailBereitsVergeben_gibt409() throws Exception {
        // Service wirft EmailBereitsVergebenException → ExceptionHandler → 409
        when(nutzerService.registriere(any(), any(), any(), any()))
                .thenThrow(new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert"));

        RegistrierungsDto dto = new RegistrierungsDto("simon@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String body = objectMapper.writeValueAsString(dto);

        // Body prüfen: sichert die HttpStatus-Ableitung in FehlerDto.von() ab —
        // status aus value(), fehler aus getReasonPhrase(), nachricht durchgereicht, zeitpunkt gesetzt.
        mockMvc.perform(post("/rest/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Diese E-Mail-Adresse ist bereits registriert"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registrieren_datenbankKonflikt_gibt409() throws Exception {
        // Race Condition: Bei gleichzeitiger Doppel-Registrierung greift der Unique-Constraint auf
        // DB-Ebene → DataIntegrityViolationException → ExceptionHandler → 409 (nicht 500).
        when(nutzerService.registriere(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint verletzt"));

        RegistrierungsDto dto = new RegistrierungsDto("test@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ GET /me

    @Test
    void findeEigenesProfil_vorhanden_gibt200() throws Exception {
        NutzerAccount konto = testKonto();
        when(nutzerService.findeEigenes(any())).thenReturn(konto);

        mockMvc.perform(get("/rest/users/me").principal(angemeldet("simon@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("simon@example.test"));
    }

    @Test
    void findeEigenesProfil_nichtVorhanden_gibt404() throws Exception {
        // Service wirft NutzerNichtGefundenException → ExceptionHandler → 404
        when(nutzerService.findeEigenes(any()))
                .thenThrow(new NutzerNichtGefundenException("Kein Nutzer mit dieser E-Mail gefunden"));

        mockMvc.perform(get("/rest/users/me").principal(angemeldet("weg@example.test")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ PUT /me (Profil)

    @Test
    void aktualisiereEigenesProfil_vorhanden_gibt200() throws Exception {
        NutzerAccount konto = testKonto();
        when(nutzerService.aktualisiereEigenesProfil(any(), any(), any())).thenReturn(konto);

        AdresseDto adresseDto = new AdresseDto("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        ProfilAktualisierungDto dto = new ProfilAktualisierungDto(adresseDto, Set.of("MUSIC"));
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aktualisiereEigenesProfil_interessenNull_gibt200() throws Exception {
        // interessen ganz weggelassen (null) ist erlaubt — ein Kundenprofil darf interessenlos sein.
        // Deckt den interessen != null -> false-Zweig im Controller ab (leere Menge an den Service).
        NutzerAccount konto = testKonto();
        when(nutzerService.aktualisiereEigenesProfil(any(), any(), any())).thenReturn(konto);

        AdresseDto adresseDto = new AdresseDto("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        ProfilAktualisierungDto dto = new ProfilAktualisierungDto(adresseDto, null);
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aktualisiereEigenesProfil_schreibkonflikt_gibt409() throws Exception {
        // Spring übersetzt einen JPA-Versionskonflikt beim Commit in eine
        // ObjectOptimisticLockingFailureException (Unterklasse von OptimisticLockingFailureException).
        // Der ExceptionHandler fängt die Spring-Oberklasse → 409. Dieser Test sichert ab, dass NICHT
        // die rohe jakarta-OptimisticLockException erwartet wird (die hier nie ankäme → sonst 500).
        when(nutzerService.aktualisiereEigenesProfil(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(NutzerAccount.class, "nutzerId"));

        AdresseDto adresseDto = new AdresseDto("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        ProfilAktualisierungDto dto = new ProfilAktualisierungDto(adresseDto, Set.of("MUSIC"));
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void aktualisiereEigenesProfil_ohneRechnungsadresse_gibt400() throws Exception {
        // Fehlende Pflichtangabe: Der Controller wirft selbst eine IllegalArgumentException (→ 400),
        // bevor der Service aufgerufen wird. Sichert den Null-Check ab (kein NullPointerException → 500).
        ProfilAktualisierungDto dto = new ProfilAktualisierungDto(null, Set.of("MUSIC"));
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aktualisiereEigenesProfil_interesseMitNull_gibt400() throws Exception {
        // Ein null-Element in der Interessen-Liste ist ungültige Eingabe → 400 (nicht 500).
        // Set.of erlaubt kein null, daher ein HashSet mit explizitem null-Element.
        Set<String> interessenMitNull = new HashSet<>();
        interessenMitNull.add("MUSIC");
        interessenMitNull.add(null);
        AdresseDto adresseDto = new AdresseDto("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        ProfilAktualisierungDto dto = new ProfilAktualisierungDto(adresseDto, interessenMitNull);
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ PUT /me/email

    @Test
    void aendereEigeneEmail_gueltig_gibt200() throws Exception {
        NutzerAccount konto = testKonto();
        when(nutzerService.aendereEigeneEmail(any(), any(), any())).thenReturn(konto);

        EmailAenderungDto dto = new EmailAenderungDto("neu@example.test", "GeheimesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/email").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aendereEigeneEmail_falschesPasswort_gibt403() throws Exception {
        when(nutzerService.aendereEigeneEmail(any(), any(), any()))
                .thenThrow(new FalschesPasswortException("Aktuelles Passwort ist falsch"));

        EmailAenderungDto dto = new EmailAenderungDto("neu@example.test", "falsch");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/email").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void aendereEigeneEmail_emailVergeben_gibt409() throws Exception {
        when(nutzerService.aendereEigeneEmail(any(), any(), any()))
                .thenThrow(new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert"));

        EmailAenderungDto dto = new EmailAenderungDto("belegt@example.test", "GeheimesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/email").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ PUT /me/password

    @Test
    void aendereEigenesPasswort_gueltig_gibt200() throws Exception {
        NutzerAccount konto = testKonto();
        when(nutzerService.aendereEigenesPasswort(any(), any(), any())).thenReturn(konto);

        PasswortAenderungDto dto = new PasswortAenderungDto("GeheimesPasswort1!", "NeuesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/password").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aendereEigenesPasswort_falschesAltesPasswort_gibt403() throws Exception {
        when(nutzerService.aendereEigenesPasswort(any(), any(), any()))
                .thenThrow(new FalschesPasswortException("Aktuelles Passwort ist falsch"));

        PasswortAenderungDto dto = new PasswortAenderungDto("falsch", "NeuesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/password").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void aendereEigenesPasswort_zuKurzesNeuesPasswort_gibt400() throws Exception {
        when(nutzerService.aendereEigenesPasswort(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Passwort muss mindestens 10 Zeichen haben"));

        PasswortAenderungDto dto = new PasswortAenderungDto("GeheimesPasswort1!", "kurz");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/password").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ PUT /me/name

    @Test
    void aendereEigenenNamen_gueltig_gibt200() throws Exception {
        NutzerAccount konto = testKonto();
        when(nutzerService.aendereEigenenNamen(any(), any(), any())).thenReturn(konto);

        NameAenderungDto dto = new NameAenderungDto("Erika", "Musterfrau");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/name").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").exists());
    }

    @Test
    void aendereEigenenNamen_leererName_gibt400() throws Exception {
        // Service wirft IllegalArgumentException (PersonenName-VO validiert) → ExceptionHandler → 400
        when(nutzerService.aendereEigenenNamen(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Vor- und Nachname dürfen nicht null oder leer sein"));

        NameAenderungDto dto = new NameAenderungDto("  ", "Musterfrau");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/users/me/name").principal(angemeldet("simon@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ DELETE /me

    @Test
    void loescheEigenesKonto_vorhanden_gibt204() throws Exception {
        // loescheEigenesKonto() gibt void zurück — kein when() nötig, kein Fehler = Erfolg
        mockMvc.perform(delete("/rest/users/me").principal(angemeldet("simon@example.test")))
                .andExpect(status().isNoContent());
    }

    @Test
    void loescheEigenesKonto_nichtVorhanden_gibt404() throws Exception {
        // doThrow() statt when() — wird für void-Methoden verwendet
        doThrow(new NutzerNichtGefundenException("Kein Nutzer mit dieser E-Mail gefunden"))
                .when(nutzerService).loescheEigenesKonto(any());

        mockMvc.perform(delete("/rest/users/me").principal(angemeldet("weg@example.test")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ login / logout

    @Test
    void login_gueltigeDaten_gibt200MitToken() throws Exception {
        // AuthenticationManager bestätigt die Anmeldung (gibt eine Authentication mit Rollen zurück),
        // JwtProvider stellt das Token aus — beide gemockt.
        NutzerAccount konto = testKonto();
        Authentication authentifizierung = new UsernamePasswordAuthenticationToken(
                "simon@example.test", null, List.of(new SimpleGrantedAuthority("PROSPECT")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifizierung);
        // Login lädt nach erfolgreicher Authentifizierung das eigene Konto, um die NutzerID (UUID) für
        // den userId-Claim mitzugeben
        when(nutzerService.findeEigenes(any())).thenReturn(konto);
        when(jwtProvider.erstelleToken(any(), any(), any())).thenReturn("tok.en.wert");

        LoginDto dto = new LoginDto("simon@example.test", "GeheimesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok.en.wert"))
                // Profil-Basisdaten für den App-Cache: userId, E-Mail, Anzeigename, Rollen
                .andExpect(jsonPath("$.userId").value(konto.getNutzerId().wert().toString()))
                .andExpect(jsonPath("$.email").value("simon@example.test"))
                .andExpect(jsonPath("$.displayName").value("Max Mustermann"))
                .andExpect(jsonPath("$.roles[0]").value("PROSPECT"));

        // Sicherstellen, dass die UUID (nicht etwa die E-Mail) als userId-Claim durchgereicht wird
        verify(jwtProvider).erstelleToken(eq("simon@example.test"),
                eq(konto.getNutzerId().wert().toString()), any());
    }

    @Test
    void login_falscheDaten_gibt401() throws Exception {
        // AuthenticationManager lehnt ab → BadCredentialsException (AuthenticationException) → 401.
        // Body prüfen: sichert die HttpStatus-Ableitung (401/Unauthorized) und die generische Meldung.
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("ungültige Anmeldedaten"));

        LoginDto dto = new LoginDto("simon@example.test", "FalschesPasswort1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Ungültige Anmeldedaten"));
    }

    @Test
    void logout_gibt204() throws Exception {
        // Stateless Logout: kein Service-Aufruf, keine Eingabe — der Endpoint loggt nur und gibt 204.
        // (Im Slice sind die Security-Filter aus, daher ist kein Token nötig.)
        mockMvc.perform(post("/rest/users/logout"))
                .andExpect(status().isNoContent());
    }
}
