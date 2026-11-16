package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.application.NutzerService;
import de.fhms.sweng.usermanagement.domain.model.Adresse;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Sparte;
import de.fhms.sweng.usermanagement.inbound.rest.dto.AdresseDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.EmailAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.NameAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.NutzerprofilDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.PasswortAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.ProfilAktualisierungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.LoginAntwortDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.LoginDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.RegistrierungsDto;
import de.fhms.sweng.usermanagement.inbound.security.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * REST-Controller für nutzerbezogene Endpunkte (Inbound-Adapter).
 *
 * <p>Empfängt HTTP-Anfragen, wandelt DTOs in Domain-Objekte um und delegiert die Fachlogik an den
 * {@link NutzerService}. Das Ergebnis wird zurück in ein Response-DTO ({@link NutzerprofilDto})
 * übersetzt.</p>
 *
 * <p><b>Self-Service über {@code /me}:</b> Alle Operationen auf dem eigenen Konto (Profil lesen,
 * aktualisieren, E-Mail/Passwort ändern, löschen) beziehen sich strukturell auf den
 * <b>angemeldeten Nutzer</b> — das Konto wird aus dem {@code Authentication}-Principal abgeleitet
 * ({@code authentication.getName()} = E-Mail aus dem Token), nicht aus einer ID im Pfad. Dadurch
 * kann niemand über eine fremde ID ein fremdes Konto bearbeiten (kein IDOR); fremde Konten verwaltet
 * ausschließlich der {@code AdminController} unter {@code /rest/admin/**}.</p>
 *
 * <p>Fehlerbehandlung (ungültige E-Mail, unbekannte Sparte, falsches Passwort) übernimmt der zentrale
 * {@link ExceptionHandler} — kein try/catch im Controller.</p>
 */
@RestController
@RequestMapping("/rest/users")
@Tag(name = "User management", description = "Registration, login/logout, manage own profile/email/password, delete account")
public class NutzerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NutzerController.class);

    private final NutzerService nutzerService;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    /**
     * Konstruktor-Injektion der benötigten Dienste.
     *
     * <p>{@code AuthenticationManager} und {@code JwtProvider} werden hier im Inbound-Layer
     * verwendet (Login = Authentifizierung + Token-Ausstellung) — der Application Service bleibt
     * dadurch frei von Spring-Security-Infrastruktur.</p>
     *
     * @param nutzerService        der Application Service für Nutzer-Use-Cases
     * @param authenticationManager prüft beim Login E-Mail und Passwort
     * @param jwtProvider          stellt nach erfolgreicher Anmeldung das JWT aus
     */
    public NutzerController(NutzerService nutzerService,
                            AuthenticationManager authenticationManager,
                            JwtProvider jwtProvider) {
        this.nutzerService = nutzerService;
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
    }

    /**
     * Registriert einen neuen Nutzer.
     *
     * @param dto enthält E-Mail und Klartext-Passwort
     * @return das angelegte Nutzerprofil als DTO
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "Account created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid email or password too short")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    public NutzerprofilDto registrieren(@RequestBody RegistrierungsDto dto) {
        LOGGER.info("registrieren() — Registrierungsanfrage für E-Mail eingegangen");
        NutzerAccount konto = nutzerService.registriere(
                dto.email(), dto.password(), dto.firstName(), dto.lastName());
        return NutzerprofilDto.von(konto);
    }

    /**
     * Meldet einen Nutzer an und stellt bei Erfolg ein JWT aus.
     *
     * <p>Nach erfolgreicher Authentifizierung wird die fachliche NutzerID (UUID) geladen und als Claim
     * {@code userId} ins Token gelegt. Sie ist die stabile Identität, über die nachgelagerte Services
     * den Nutzer korrelieren — das Subject (E-Mail) ist dagegen änderbar.</p>
     *
     * @param dto enthält E-Mail und Klartext-Passwort
     * @return das ausgestellte JWT im Antwort-DTO
     */
    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    @ApiResponse(responseCode = "200", description = "Login successful — token plus profile basics (userId, email, displayName, roles) in body")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public LoginAntwortDto login(@RequestBody LoginDto dto) {
        LOGGER.info("login() — Anmeldeanfrage eingegangen");
        // AuthenticationManager prüft E-Mail + Passwort; bei Fehlschlag wirft er eine
        // AuthenticationException (→ 401 über den ExceptionHandler).
        Authentication authentifizierung = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password()));
        // Fachliche NutzerID (UUID) für den userId-Claim laden — nach erfolgreicher Authentifizierung
        // existiert das Konto sicher. Konsumierende Services korrelieren über diese stabile ID, nicht
        // über die (änderbare) E-Mail im Token-Subject.
        NutzerAccount konto = nutzerService.findeEigenes(authentifizierung.getName());
        // Rollen stehen nach erfolgreicher Authentifizierung als Authorities bereit
        String token = jwtProvider.erstelleToken(authentifizierung.getName(),
                konto.getNutzerId().wert().toString(), authentifizierung.getAuthorities());
        // Neben dem Token auch die Profil-Basisdaten (userId, E-Mail, Anzeigename, Rollen) zurückgeben,
        // damit die App sie lokal cachen kann und den Namen nicht vor jedem API-Call neu abrufen muss.
        return LoginAntwortDto.von(token, konto);
    }

    /**
     * Meldet einen Nutzer ab.
     *
     * <p>Bei STATELESS-JWT hält der Server keinen Session-State: Das eigentliche Abmelden ist, dass
     * der Client sein kurzlebiges Token verwirft. Dieser Endpoint gibt der App einen sauberen
     * Aufrufpunkt und erzeugt ein Audit-Log, führt aber bewusst keinen Server-State ein (eine
     * Token-Sperrliste würde der zustandslosen Architektur widersprechen).</p>
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out (stateless)")
    @ApiResponse(responseCode = "204", description = "Logout processed")
    @ApiResponse(responseCode = "401", description = "No valid token")
    public void logout() {
        // Kein Identifier im Log: Die E-Mail (= Principal-Name) ist PII und wird nicht protokolliert.
        LOGGER.info("logout() — Abmeldung eines Nutzers");
    }

    /**
     * Lädt das Profil des angemeldeten Nutzers (eigenes Konto).
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = E-Mail)
     * @return das eigene Nutzerprofil als DTO
     */
    @GetMapping("/me")
    @Operation(summary = "Load own profile")
    @ApiResponse(responseCode = "200", description = "Profile found")
    @ApiResponse(responseCode = "401", description = "No valid token")
    public NutzerprofilDto findeEigenesProfil(Authentication authentication) {
        LOGGER.info("findeEigenesProfil() — Anfrage: eigenes Profil");
        NutzerAccount konto = nutzerService.findeEigenes(authentication.getName());
        return NutzerprofilDto.von(konto);
    }

    /**
     * Aktualisiert das eigene Kundenprofil (Rechnungsadresse + Interessen).
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = E-Mail)
     * @param dto            neue Rechnungsadresse und Interessen
     * @return das aktualisierte Nutzerprofil als DTO
     */
    @PutMapping("/me")
    @Operation(summary = "Update own customer profile")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "400", description = "Invalid address or unknown category")
    @ApiResponse(responseCode = "401", description = "No valid token")
    public NutzerprofilDto aktualisiereEigenesProfil(Authentication authentication,
                                                     @RequestBody ProfilAktualisierungDto dto) {
        LOGGER.info("aktualisiereEigenesProfil() — Aktualisierungsanfrage für eigenes Profil");

        // Rechnungsadresse ist Pflicht — fehlt sie im JSON, ist das ungültige Eingabe (→ 400),
        // kein Programmierfehler (sonst gäbe es beim zuAdresse()-Aufruf eine NullPointerException).
        AdresseDto adresseDto = dto.billingAddress();
        if (adresseDto == null) {
            throw new IllegalArgumentException("Rechnungsadresse darf nicht fehlen");
        }
        Adresse adresse = adresseDto.zuAdresse();

        Set<Sparte> interessen = leseInteressen(dto);

        NutzerAccount konto = nutzerService.aktualisiereEigenesProfil(
                authentication.getName(), adresse, interessen);
        return NutzerprofilDto.von(konto);
    }

    /**
     * Ändert die eigene E-Mail-Adresse (zugleich Login-Name). Erfordert das aktuelle Passwort.
     *
     * <p>Da die E-Mail das Token-Subject ist, wird ein bereits ausgestelltes JWT nach der Änderung
     * „stale" — der Client muss sich danach mit der neuen E-Mail neu anmelden.</p>
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = bisherige E-Mail)
     * @param dto            neue E-Mail und aktuelles Passwort zur Bestätigung
     * @return das aktualisierte Nutzerprofil als DTO
     */
    @PutMapping("/me/email")
    @Operation(summary = "Change own email (with current password)")
    @ApiResponse(responseCode = "200", description = "Email changed (request a new token afterwards)")
    @ApiResponse(responseCode = "400", description = "Invalid email format")
    @ApiResponse(responseCode = "401", description = "No valid token")
    @ApiResponse(responseCode = "403", description = "Current password is incorrect")
    @ApiResponse(responseCode = "409", description = "New email already in use")
    public NutzerprofilDto aendereEigeneEmail(Authentication authentication,
                                              @RequestBody EmailAenderungDto dto) {
        LOGGER.info("aendereEigeneEmail() — Anfrage: eigene E-Mail ändern");
        NutzerAccount konto = nutzerService.aendereEigeneEmail(
                authentication.getName(), dto.newEmail(), dto.currentPassword());
        return NutzerprofilDto.von(konto);
    }

    /**
     * Ändert das eigene Passwort. Erfordert das alte Passwort zur Bestätigung.
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = E-Mail)
     * @param dto            altes und neues Passwort
     * @return das aktualisierte Nutzerprofil als DTO
     */
    @PutMapping("/me/password")
    @Operation(summary = "Change own password (with old password)")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "New password too short")
    @ApiResponse(responseCode = "401", description = "No valid token")
    @ApiResponse(responseCode = "403", description = "Old password is incorrect")
    public NutzerprofilDto aendereEigenesPasswort(Authentication authentication,
                                                  @RequestBody PasswortAenderungDto dto) {
        LOGGER.info("aendereEigenesPasswort() — Anfrage: eigenes Passwort ändern");
        NutzerAccount konto = nutzerService.aendereEigenesPasswort(
                authentication.getName(), dto.oldPassword(), dto.newPassword());
        return NutzerprofilDto.von(konto);
    }

    /**
     * Ändert den eigenen Namen (Vor- und Nachname). Anders als E-Mail-/Passwortänderung ist keine
     * Passwortbestätigung nötig, da der Name nicht sicherheitskritisch ist. Das Konto muss aktiv sein.
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = E-Mail)
     * @param dto            neuer Vor- und Nachname
     * @return das aktualisierte Nutzerprofil als DTO
     */
    @PutMapping("/me/name")
    @Operation(summary = "Change own name")
    @ApiResponse(responseCode = "200", description = "Name changed")
    @ApiResponse(responseCode = "400", description = "First or last name is empty")
    @ApiResponse(responseCode = "401", description = "No valid token")
    @ApiResponse(responseCode = "403", description = "Account is blocked or deleted")
    public NutzerprofilDto aendereEigenenNamen(Authentication authentication,
                                               @RequestBody NameAenderungDto dto) {
        LOGGER.info("aendereEigenenNamen() — Anfrage: eigenen Namen ändern");
        NutzerAccount konto = nutzerService.aendereEigenenNamen(
                authentication.getName(), dto.firstName(), dto.lastName());
        return NutzerprofilDto.von(konto);
    }

    /**
     * Löscht das eigene Konto (Soft-Delete: Status wird auf DELETED gesetzt).
     *
     * @param authentication die Authentifizierung des angemeldeten Nutzers (Principal = E-Mail)
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own account (soft delete)")
    @ApiResponse(responseCode = "204", description = "Account marked as deleted")
    @ApiResponse(responseCode = "401", description = "No valid token")
    public void loescheEigenesKonto(Authentication authentication) {
        LOGGER.info("loescheEigenesKonto() — Löschanfrage für eigenes Konto");
        nutzerService.loescheEigenesKonto(authentication.getName());
    }

    /**
     * Wandelt die Sparten-Strings aus dem DTO in ein {@code Set<Sparte>} um. Fehlende Interessen
     * (null als ganze Liste) werden als leere Menge behandelt — ein Kundenprofil darf interessenlos
     * sein. Ein unbekannter ODER fehlender (null) Einzelwert ist ungültige Eingabe → 400. Der explizite
     * null-Check ist nötig, weil {@code Sparte.valueOf(null)} sonst eine NullPointerException (→ 500) wirft.
     *
     * @param dto das Aktualisierungs-DTO
     * @return die Interessen als Set (niemals null)
     */
    private Set<Sparte> leseInteressen(ProfilAktualisierungDto dto) {
        Set<Sparte> interessen = new LinkedHashSet<>();
        if (dto.interests() != null) {
            for (String sparteText : dto.interests()) {
                if (sparteText == null) {
                    throw new IllegalArgumentException("Eine Sparte darf nicht null sein");
                }
                interessen.add(Sparte.valueOf(sparteText));
            }
        }
        return interessen;
    }
}
