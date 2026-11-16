package de.fhms.sweng.usermanagement.inbound.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link JwtAuthenticationFilter} — JwtProvider als Mock, ohne Spring-Kontext.
 * Prüft, ob bei gültigem Token die Authentication gesetzt wird, bei fehlendem/ungültigem Token
 * nicht — und dass die Filterkette in allen Fällen fortgesetzt wird.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "abc.def.ghi";
    private static final String BENUTZER = "demo@example.test";

    @Mock
    private JwtProvider jwtProvider;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        // SecurityContext ist thread-gebunden — nach jedem Test leeren, um Seiteneffekte zu vermeiden
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_gueltigesToken_setztAuthentication() throws Exception {
        Authentication gemockteAuth = new UsernamePasswordAuthenticationToken(
                BENUTZER, null, List.of(new SimpleGrantedAuthority("ADMINISTRATOR")));
        when(jwtProvider.leseAuthentifizierung(TOKEN)).thenReturn(Optional.of(gemockteAuth));
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(BENUTZER);
        assertThat(auth.getAuthorities()).extracting("authority").contains("ADMINISTRATOR");
        // Kette wurde fortgesetzt (MockFilterChain merkt sich den durchgereichten Request)
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilter_keinHeader_setztKeineAuthentication() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilter_headerOhneBearerPraefix_setztKeineAuthentication() throws Exception {
        // Header vorhanden, aber kein "Bearer "-Präfix → leseToken liefert null (startsWith-Zweig)
        request.addHeader("Authorization", "Basic abc123");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilter_ungueltigesToken_setztKeineAuthentication() throws Exception {
        when(jwtProvider.leseAuthentifizierung(TOKEN)).thenReturn(Optional.empty());
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }
}
