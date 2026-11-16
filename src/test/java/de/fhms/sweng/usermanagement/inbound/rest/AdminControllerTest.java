package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.application.AdminService;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.inbound.rest.dto.AdminEmailAenderungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.PasswortRuecksetzungDto;
import de.fhms.sweng.usermanagement.inbound.rest.dto.RollenZuweisungDto;
import de.fhms.sweng.usermanagement.inbound.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test-Slice für {@link AdminController} (Inbound-Adapter REST).
 *
 * <p>{@code @WebMvcTest} lädt nur die Web-Schicht inkl. {@link ExceptionHandler}; der
 * {@link AdminService} ist gemockt. {@code addFilters = false} schaltet die Servlet-Filterkette ab —
 * die URL-basierte Rollenprüfung wird separat im {@code SecurityIntegrationTest} (echte Kette)
 * getestet. Die Methoden-Autorisierung ({@code @PreAuthorize} auf dem Controller) ist hier aber aktiv,
 * deshalb laufen die Tests als {@code @WithMockUser(authorities = "ADMINISTRATOR")}.</p>
 *
 * <p>{@code AuthenticationManager} und {@code JwtProvider} werden gemockt, weil die mitgeladene
 * {@code SecurityConfig} sie als Bean-Abhängigkeiten erwartet.</p>
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ADMINISTRATOR")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    // Von der mitgeladenen SecurityConfig benötigt (filterChain + authenticationManager-Bean)
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtProvider jwtProvider;

    /** Hilfsmethode: erzeugt ein bereits freigeschaltetes Testkonto (ACTIVE, PROSPECT). */
    private NutzerAccount testKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName("Max", "Mustermann"));
        konto.bestaetigeRegistrierung();
        return konto;
    }

    @Test
    void findeAlleKonten_gibt200_mitListe() throws Exception {
        List<NutzerAccount> konten = List.of(testKonto("a@example.test"), testKonto("b@example.test"));
        when(adminService.findeAlleKonten(nullable(String.class))).thenReturn(konten);

        mockMvc.perform(get("/rest/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("a@example.test"))
                .andExpect(jsonPath("$[1].email").value("b@example.test"));
    }

    @Test
    void findeKonto_vorhanden_gibt200() throws Exception {
        NutzerAccount konto = testKonto("einzel@example.test");
        when(adminService.findeKonto(any())).thenReturn(konto);

        mockMvc.perform(get("/rest/admin/accounts/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("einzel@example.test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void findeKonto_nichtVorhanden_gibt404() throws Exception {
        when(adminService.findeKonto(any()))
                .thenThrow(new NutzerNichtGefundenException("Kein Nutzer mit dieser ID gefunden"));

        mockMvc.perform(get("/rest/admin/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void findeKonto_ungueltigeId_gibt400() throws Exception {
        // parseId(UUID.fromString) wirft IllegalArgumentException → 400, Service wird nicht aufgerufen
        mockMvc.perform(get("/rest/admin/accounts/keine-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sperreKonto_gibt200_mitStatusGesperrt() throws Exception {
        NutzerAccount konto = testKonto("sperr@example.test");
        konto.sperren();
        when(adminService.sperreKonto(any())).thenReturn(konto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void reaktiviereKonto_gibt200_mitStatusAktiv() throws Exception {
        NutzerAccount konto = testKonto("reaktiv@example.test");
        when(adminService.reaktiviereKonto(any())).thenReturn(konto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void schalteKontoFrei_gibt200_mitStatusAktiv() throws Exception {
        // Saga-Erfolg administrativ: ein freigeschaltetes Konto ist ACTIVE (Rolle bleibt PROSPECT)
        NutzerAccount konto = testKonto("ausstehend@example.test");
        when(adminService.schalteKontoFrei(any())).thenReturn(konto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/activation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void weiseRolleZu_gueltigeRolle_gibt200() throws Exception {
        NutzerAccount konto = testKonto("rolle@example.test");
        when(adminService.weiseRolleZu(any(), any())).thenReturn(konto);

        RollenZuweisungDto dto = new RollenZuweisungDto("EVENT_MANAGER");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void weiseRolleZu_unbekannteRolle_gibt400() throws Exception {
        // parseRolle(Nutzerrolle.valueOf) wirft IllegalArgumentException → 400, Service wird nicht aufgerufen
        RollenZuweisungDto dto = new RollenZuweisungDto("GIBTSNICHT");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weiseRolleZu_fehlendeRolle_gibt400() throws Exception {
        // rolle == null im Body → parseRolle wirft IllegalArgumentException → 400 (null-Zweig)
        RollenZuweisungDto dto = new RollenZuweisungDto(null);
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/rest/admin/accounts/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entzieheRolle_gibt200() throws Exception {
        NutzerAccount konto = testKonto("rolle@example.test");
        when(adminService.entzieheRolle(any(), any())).thenReturn(konto);

        mockMvc.perform(delete("/rest/admin/accounts/" + UUID.randomUUID() + "/roles/ACCOUNTANT"))
                .andExpect(status().isOk());
    }

    @Test
    void entzieheRolle_letzterAdmin_gibt409() throws Exception {
        // Service wirft LetzterAdministratorException → ExceptionHandler bildet auf 409 ab
        when(adminService.entzieheRolle(any(), any()))
                .thenThrow(new LetzterAdministratorException("Dem letzten aktiven Administrator kann die Rolle nicht entzogen werden"));

        mockMvc.perform(delete("/rest/admin/accounts/" + UUID.randomUUID() + "/roles/ADMINISTRATOR"))
                .andExpect(status().isConflict());
    }

    @Test
    void aendereEmail_gueltig_gibt200() throws Exception {
        NutzerAccount konto = testKonto("neu@example.test");
        when(adminService.aendereEmail(any(), any())).thenReturn(konto);

        AdminEmailAenderungDto dto = new AdminEmailAenderungDto("neu@example.test");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/admin/accounts/" + UUID.randomUUID() + "/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("neu@example.test"));
    }

    @Test
    void aendereEmail_emailVergeben_gibt409() throws Exception {
        when(adminService.aendereEmail(any(), any()))
                .thenThrow(new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert"));

        AdminEmailAenderungDto dto = new AdminEmailAenderungDto("belegt@example.test");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/admin/accounts/" + UUID.randomUUID() + "/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void setzePasswortZurueck_gueltig_gibt200() throws Exception {
        NutzerAccount konto = testKonto("reset@example.test");
        when(adminService.setzePasswortZurueck(any(), any())).thenReturn(konto);

        PasswortRuecksetzungDto dto = new PasswortRuecksetzungDto("AdminReset1!");
        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/rest/admin/accounts/" + UUID.randomUUID() + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void entferneKontoEndgueltig_gibt204() throws Exception {
        // void-Methode: nichts stubben, nur prüfen dass der Endpunkt 204 liefert
        mockMvc.perform(delete("/rest/admin/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
