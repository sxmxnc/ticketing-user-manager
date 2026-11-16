package de.fhms.sweng.usermanagement.inbound.security;

import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integrationstest der Zugriffsregeln aus {@link SecurityConfig} mit der echten Security-Filterkette.
 *
 * <p>{@code @SpringBootTest} lädt den vollen Kontext, {@code @AutoConfigureMockMvc} bindet MockMvc
 * mit aktiven Filtern. Statt {@code @WithMockUser} werden hier <b>echte JWTs</b> über den realen
 * {@link JwtProvider} erzeugt und im {@code Authorization}-Header mitgeschickt — so läuft der
 * komplette Pfad Token → {@code JwtAuthenticationFilter} → Autorisierung end-to-end.</p>
 *
 * <p>Grund gegen {@code @WithMockUser}: Bei {@code SessionCreationPolicy.STATELESS} lädt der
 * {@code SecurityContextHolderFilter} zu Beginn einen leeren Context und überschreibt die vom
 * Test gesetzte Authentication — die Anfrage gälte als anonym. Ein echtes Token umgeht das, weil
 * unser Filter die Authentication erst <i>nach</i> dem Context-Filter setzt.</p>
 *
 * <p>Geprüft wird die Zugriffsregel für {@code /rest/admin/**}: ohne Token 401, mit falscher Rolle
 * (CUSTOMER) 403, als ADMINISTRATOR 200 (der AdminController liefert die Kontoliste). Zusätzlich wird
 * {@code /rest/users/logout} abgedeckt (authentifizierungspflichtig: ohne Token 401, mit Token 204).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    /** Erzeugt ein gültiges JWT für einen Tester mit der angegebenen Rolle. */
    private String tokenMit(Nutzerrolle rolle) {
        return jwtProvider.erstelleToken("tester@example.test",
                "00000000-0000-0000-0000-000000000001", List.of(rolle));
    }

    /** Erzeugt ein gültiges JWT für eine konkrete E-Mail (= Token-Subject) mit der angegebenen Rolle. */
    private String tokenFuer(String email, Nutzerrolle rolle) {
        return jwtProvider.erstelleToken(email,
                "00000000-0000-0000-0000-000000000002", List.of(rolle));
    }

    @Test
    void actuatorHealth_istOhneAuthErreichbar() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void adminBereich_ohneToken_gibt401() throws Exception {
        mockMvc.perform(get("/rest/admin/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminBereich_falscheRolle_gibt403() throws Exception {
        mockMvc.perform(get("/rest/admin/accounts")
                        .header("Authorization", "Bearer " + tokenMit(Nutzerrolle.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBereich_alsAdministrator_wirdDurchgelassen() throws Exception {
        // Mit ADMINISTRATOR-Token greift sowohl die URL-Regel als auch @PreAuthorize — der
        // AdminController liefert die Kontoliste (200). Damit ist der End-to-End-Pfad
        // Token → Filter → @PreAuthorize → Controller abgedeckt.
        mockMvc.perform(get("/rest/admin/accounts")
                        .header("Authorization", "Bearer " + tokenMit(Nutzerrolle.ADMINISTRATOR)))
                .andExpect(status().isOk());
    }

    @Test
    void logout_ohneToken_gibt401() throws Exception {
        // Logout steht nicht in der permitAll-Liste → greift anyRequest().authenticated():
        // ohne Token meldet der HttpStatusEntryPoint 401.
        mockMvc.perform(post("/rest/users/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_mitToken_gibt204() throws Exception {
        // Mit gültigem Token läuft der End-to-End-Pfad Token → Filter → Controller durch (204).
        mockMvc.perform(post("/rest/users/logout")
                        .header("Authorization", "Bearer " + tokenMit(Nutzerrolle.CUSTOMER)))
                .andExpect(status().isNoContent());
    }

    @Test
    void me_ohneToken_gibt401() throws Exception {
        // /rest/users/me steht nicht in der permitAll-Liste → greift anyRequest().authenticated().
        mockMvc.perform(get("/rest/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_mitToken_liefertEigenesKonto() throws Exception {
        // Das Konto wird strukturell aus dem Token-Subject abgeleitet (kein {id} im Pfad → kein IDOR).
        // Das Seed-Konto demo@example.test (data.sql) muss exakt zurückkommen.
        mockMvc.perform(get("/rest/users/me")
                        .header("Authorization", "Bearer " + tokenFuer("demo@example.test", Nutzerrolle.PROSPECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@example.test"));
    }
}
