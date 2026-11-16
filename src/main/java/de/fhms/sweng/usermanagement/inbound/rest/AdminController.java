package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.application.AdminService;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.inbound.rest.dto.AdminEmailAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.NutzerprofilDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.PasswortRuecksetzungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.RollenZuweisungDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST-Controller für die Administration von Nutzerkonten (Inbound-Adapter, {@code /rest/admin/**}).
 *
 * <p>Bietet Endpunkte zum Auflisten/Suchen von Konten, Sperren/Reaktivieren, Rollen
 * zuweisen/entziehen und endgültigem Löschen (Hard-Delete, DSGVO-Purge). Die Fachlogik liegt im
 * {@link AdminService}; dieser Controller übersetzt nur HTTP ↔ Domain.</p>
 *
 * <p>Logging: Der Controller protokolliert nur den <b>Eingang der Anfrage</b> (das ist beim Loggen
 * immer wahr und behauptet keinen Erfolg). Das tatsächliche Ergebnis loggt der {@link AdminService}
 * erst <b>nach</b> der Existenzprüfung/Aktion — so meldet kein Log einen Erfolg, der gleich mit einer
 * Exception (z. B. fehlendes Konto) abbricht.</p>
 *
 * <p>Autorisierung doppelt abgesichert: Die {@code SecurityConfig} beschränkt {@code /rest/admin/**}
 * bereits auf die Rolle ADMINISTRATOR (URL-Ebene). Zusätzlich erzwingt
 * {@code @PreAuthorize("hasAuthority('ADMINISTRATOR')")} die Rolle auf Methodenebene
 * (Defense-in-Depth). {@code hasAuthority} statt {@code hasRole}, da {@code Nutzerrolle} ohne
 * {@code ROLE_}-Präfix arbeitet.</p>
 *
 * <p>Fehlerbehandlung (ungültige UUID, unbekannte Rolle, fehlendes Konto) übernimmt der zentrale
 * {@link ExceptionHandler} — kein try/catch im Controller.</p>
 */
@RestController
@RequestMapping("/rest/admin")
@PreAuthorize("hasAuthority('ADMINISTRATOR')")
@Tag(name = "Administration", description = "Manage accounts: list, lock, roles, hard delete")
public class AdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;

    /**
     * Konstruktor-Injektion des Admin-Service.
     *
     * @param adminService der Application Service für die Admin-Use-Cases
     */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Listet alle Konten auf, optional gefiltert nach einem E-Mail-Teilstring.
     *
     * @param filter optionaler Suchbegriff (Teilstring der E-Mail), oder null für alle
     * @return List of accounts als DTOs
     */
    @GetMapping("/accounts")
    @Operation(summary = "List all accounts (optionally filtered)")
    @ApiResponse(responseCode = "200", description = "List of accounts")
    public List<NutzerprofilDto> findeAlleKonten(@RequestParam(required = false) String filter) {
        LOGGER.info("findeAlleKonten() — Anfrage: Kontoliste");
        List<NutzerAccount> konten = adminService.findeAlleKonten(filter);
        // Bewusst per for-Schleife auf DTOs abbilden (Lesbarkeit statt Stream)
        List<NutzerprofilDto> dtos = new ArrayList<>();
        for (NutzerAccount konto : konten) {
            dtos.add(NutzerprofilDto.von(konto));
        }
        return dtos;
    }

    /**
     * Lädt ein einzelnes Konto anhand seiner ID.
     *
     * @param id die NutzerID als UUID-String im Pfad
     * @return das Konto als DTO
     */
    @GetMapping("/accounts/{id}")
    @Operation(summary = "Load a single account")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto findeKonto(@PathVariable String id) {
        LOGGER.info("findeKonto() — Kontoanfrage für ID {}", id);
        return NutzerprofilDto.von(adminService.findeKonto(NutzerID.ausText(id)));
    }

    /**
     * Sperrt ein Konto.
     *
     * @param id die NutzerID als UUID-String im Pfad
     * @return das aktualisierte Konto als DTO
     */
    @PostMapping("/accounts/{id}/lock")
    @Operation(summary = "Lock account")
    @ApiResponse(responseCode = "200", description = "Account locked (or unchanged if deleted)")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto sperreKonto(@PathVariable String id) {
        LOGGER.info("sperreKonto() — Sperr-Anfrage für Konto {}", id);
        return NutzerprofilDto.von(adminService.sperreKonto(NutzerID.ausText(id)));
    }

    /**
     * Reaktiviert ein gesperrtes Konto.
     *
     * @param id die NutzerID als UUID-String im Pfad
     * @return das aktualisierte Konto als DTO
     */
    @PostMapping("/accounts/{id}/reactivate")
    @Operation(summary = "Reactivate account")
    @ApiResponse(responseCode = "200", description = "Account reactivated (or unchanged if deleted)")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto reaktiviereKonto(@PathVariable String id) {
        LOGGER.info("reaktiviereKonto() — Reaktivierungsanfrage für Konto {}", id);
        return NutzerprofilDto.von(adminService.reaktiviereKonto(NutzerID.ausText(id)));
    }

    /**
     * Schaltet ein ausstehendes Konto frei (Admin-Genehmigung der Registrierung): PENDING → ACTIVE.
     * Bildet den Erfolgspfad der Registrierungs-Saga administrativ ab. Die Rolle bleibt PROSPECT
     * (CUSTOMER kommt über die Kauf-Choreographie, nicht über die Freischaltung).
     *
     * @param id die NutzerID als UUID-String im Pfad
     * @return das aktualisierte Konto als DTO
     */
    @PostMapping("/accounts/{id}/activation")
    @Operation(summary = "Activate a pending account (PENDING -> ACTIVE)")
    @ApiResponse(responseCode = "200", description = "Account activated (or unchanged if not pending)")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto schalteKontoFrei(@PathVariable String id) {
        LOGGER.info("schalteKontoFrei() — Freischaltungsanfrage für Konto {}", id);
        return NutzerprofilDto.von(adminService.schalteKontoFrei(NutzerID.ausText(id)));
    }

    /**
     * Weist einem Konto eine Rolle zu.
     *
     * @param id  die NutzerID als UUID-String im Pfad
     * @param dto enthält den Namen der zuzuweisenden Rolle
     * @return das aktualisierte Konto als DTO
     */
    @PostMapping("/accounts/{id}/roles")
    @Operation(summary = "Assign role")
    @ApiResponse(responseCode = "200", description = "Role assigned")
    @ApiResponse(responseCode = "400", description = "Unknown role")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto weiseRolleZu(@PathVariable String id, @RequestBody RollenZuweisungDto dto) {
        LOGGER.info("weiseRolleZu() — Rollenzuweisungsanfrage für Konto {}", id);
        Nutzerrolle rolle = parseRolle(dto.role());
        return NutzerprofilDto.von(adminService.weiseRolleZu(NutzerID.ausText(id), rolle));
    }

    /**
     * Entzieht einem Konto eine Rolle.
     *
     * @param id    die NutzerID als UUID-String im Pfad
     * @param rolle der Name der zu entziehenden Rolle im Pfad
     * @return das aktualisierte Konto als DTO
     */
    @DeleteMapping("/accounts/{id}/roles/{role}")
    @Operation(summary = "Revoke role")
    @ApiResponse(responseCode = "200", description = "Role revoked")
    @ApiResponse(responseCode = "400", description = "Unknown role")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto entzieheRolle(@PathVariable String id, @PathVariable("role") String rolle) {
        LOGGER.info("entzieheRolle() — Rollenentzugsanfrage für Konto {}", id);
        Nutzerrolle nutzerrolle = parseRolle(rolle);
        return NutzerprofilDto.von(adminService.entzieheRolle(NutzerID.ausText(id), nutzerrolle));
    }

    /**
     * Ändert die E-Mail-Adresse eines Kontos (Admin-Pflege, kein Passwort nötig).
     *
     * @param id  die NutzerID als UUID-String im Pfad
     * @param dto enthält die neue E-Mail-Adresse
     * @return das aktualisierte Konto als DTO
     */
    @PutMapping("/accounts/{id}/email")
    @Operation(summary = "Change account email")
    @ApiResponse(responseCode = "200", description = "Email changed")
    @ApiResponse(responseCode = "400", description = "Invalid email format")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    public NutzerprofilDto aendereEmail(@PathVariable String id, @RequestBody AdminEmailAenderungDto dto) {
        LOGGER.info("aendereEmail() — E-Mail-Änderungsanfrage für Konto {}", id);
        return NutzerprofilDto.von(adminService.aendereEmail(NutzerID.ausText(id), dto.newEmail()));
    }

    /**
     * Setzt das Passwort eines Kontos zurück (Admin-Pflege, kein altes Passwort nötig).
     *
     * @param id  die NutzerID als UUID-String im Pfad
     * @param dto enthält das neue Passwort
     * @return das aktualisierte Konto als DTO
     */
    @PutMapping("/accounts/{id}/password")
    @Operation(summary = "Reset account password")
    @ApiResponse(responseCode = "200", description = "Password reset")
    @ApiResponse(responseCode = "400", description = "New password too short")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public NutzerprofilDto setzePasswortZurueck(@PathVariable String id,
                                                @RequestBody PasswortRuecksetzungDto dto) {
        LOGGER.info("setzePasswortZurueck() — Passwort-Reset-Anfrage für Konto {}", id);
        return NutzerprofilDto.von(adminService.setzePasswortZurueck(NutzerID.ausText(id), dto.newPassword()));
    }

    /**
     * Entfernt ein Konto endgültig (Hard-Delete, DSGVO-Purge).
     *
     * @param id die NutzerID als UUID-String im Pfad
     */
    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete account (hard delete / GDPR purge)")
    @ApiResponse(responseCode = "204", description = "Account physically deleted")
    @ApiResponse(responseCode = "404", description = "No account with this ID")
    public void entferneKontoEndgueltig(@PathVariable String id) {
        LOGGER.info("entferneKontoEndgueltig() — Hard-Delete-Anfrage für Konto {}", id);
        adminService.entferneKontoEndgueltig(NutzerID.ausText(id));
    }

    /**
     * Wandelt einen Rollennamen in das {@link Nutzerrolle}-Enum um. Ein fehlender (null) oder
     * unbekannter Name ist ungültige Eingabe und führt zu einer IllegalArgumentException (→ 400).
     * Der explizite null-Check ist nötig, weil {@code Nutzerrolle.valueOf(null)} sonst eine
     * NullPointerException (→ 500) werfen würde.
     *
     * @param rolle der Rollenname
     * @return die passende Nutzerrolle
     */
    private Nutzerrolle parseRolle(String rolle) {
        if (rolle == null) {
            throw new IllegalArgumentException("Rolle darf nicht fehlen");
        }
        return Nutzerrolle.valueOf(rolle);
    }
}
