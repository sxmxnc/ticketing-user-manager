package de.fhms.sweng.usermanagement.inbound.security;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link UserDetailsServiceImpl} — kein Spring-Kontext, Repository als Mock.
 * Prüft das Abbilden eines NutzerAccounts auf Spring-UserDetails sowie die Status-Regeln
 * (gesperrt → LockedException, gelöscht/unbekannt/ungültig → UsernameNotFoundException).
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    private static final String EMAIL = "demo@example.test";
    private static final String HASH = "$2a$10$x4DZx6CI3z6JUXHvMKPOKZLCPpdjjPvpNfpklMYNcHZdTEgy";

    @Mock
    private NutzerRepository nutzerRepository;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(nutzerRepository);
    }

    /** Hilfsmethode: legt ein bereits freigeschaltetes ACTIVE-Konto mit Rolle PROSPECT an. */
    private NutzerAccount neuesKonto() {
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(EMAIL), new PasswortHash(HASH), new PersonenName("Max", "Mustermann"));
        konto.bestaetigeRegistrierung();
        return konto;
    }

    /** Hilfsmethode: legt ein noch ausstehendes (PENDING) Konto an (Saga nicht abgeschlossen). */
    private NutzerAccount ausstehendesKonto() {
        return NutzerAccount.registrieren(
                new EmailAdresse(EMAIL), new PasswortHash(HASH), new PersonenName("Max", "Mustermann"));
    }

    @Test
    void loadUserByUsername_aktivesKonto_liefertUserDetailsMitRollen() {
        NutzerAccount konto = neuesKonto();
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.of(konto));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.getUsername()).isEqualTo(EMAIL);
        assertThat(details.getPassword()).isEqualTo(HASH);
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("PROSPECT");
    }

    @Test
    void loadUserByUsername_unbekannteEmail_wirftUsernameNotFound() {
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_gesperrtesKonto_wirftLockedException() {
        NutzerAccount konto = neuesKonto();
        konto.sperren();
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.of(konto));

        assertThrows(LockedException.class, () -> service.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_geloeschtesKonto_wirftUsernameNotFound() {
        NutzerAccount konto = neuesKonto();
        konto.loeschen();
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.of(konto));

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_ausstehendesKonto_liefertUserDetails() {
        // Semantic Lock: ein ausstehendes Konto ist registriert und darf sich anmelden und lesen.
        // Schreibende Self-Service-Aktionen sperrt separat der Status-Guard im NutzerService.
        NutzerAccount konto = ausstehendesKonto();
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.of(konto));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.getUsername()).isEqualTo(EMAIL);
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("PROSPECT");
    }

    @Test
    void loadUserByUsername_abgelehntesKonto_wirftUsernameNotFound() {
        // Kompensierter (REJECTED) Endzustand wird wie nicht vorhanden behandelt — kein Login
        NutzerAccount konto = ausstehendesKonto();
        konto.registrierungAblehnen();
        when(nutzerRepository.findByEmail(any(EmailAdresse.class))).thenReturn(Optional.of(konto));

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(EMAIL));
    }

    @Test
    void loadUserByUsername_ungueltigesFormat_wirftUsernameNotFound() {
        String ungueltig = "kein-email-format";

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(ungueltig));
    }
}
