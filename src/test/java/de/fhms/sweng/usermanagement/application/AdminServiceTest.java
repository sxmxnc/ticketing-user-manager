package de.fhms.sweng.usermanagement.application;

import de.fhms.sweng.usermanagement.domain.event.AccountDeletedEvent;
import de.fhms.sweng.usermanagement.domain.event.AccountBlockedEvent;
import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import de.fhms.sweng.usermanagement.domain.service.AdministratorRichtlinie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link AdminService}. Repository und EventPublisher sind gemockt;
 * der {@code PasswordEncoder} ist ein echter BCryptPasswordEncoder (kein Spring-Kontext nötig).
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private NutzerRepository nutzerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AdministratorRichtlinie administratorRichtlinie;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(nutzerRepository, eventPublisher, administratorRichtlinie, encoder);
    }

    /** Hilfsmethode: erzeugt ein bereits freigeschaltetes Testkonto (ACTIVE, PROSPECT). */
    private NutzerAccount neuesKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName("Max", "Mustermann"));
        // Saga-Freigabe simulieren — Admin-Operationen setzen ein aktives Konto voraus
        konto.bestaetigeRegistrierung();
        return konto;
    }

    /** Hilfsmethode: erzeugt ein noch ausstehendes (PENDING) Testkonto (Saga nicht abgeschlossen). */
    private NutzerAccount ausstehendesKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        return NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName("Max", "Mustermann"));
    }

    // ------------------------------------------------------------------ findeAlleKonten / findeKonto

    @Test
    void findeAlleKonten_gibtListeZurueck() {
        List<NutzerAccount> konten = List.of(neuesKonto("a@example.test"), neuesKonto("b@example.test"));
        when(nutzerRepository.suche(null)).thenReturn(konten);

        List<NutzerAccount> ergebnis = adminService.findeAlleKonten(null);

        assertEquals(2, ergebnis.size());
        verify(nutzerRepository).suche(null);
    }

    @Test
    void findeKonto_vorhanden_gibtKonto() {
        NutzerAccount konto = neuesKonto("admin-ziel@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        assertEquals(konto, adminService.findeKonto(konto.getNutzerId()));
    }

    @Test
    void findeKonto_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.empty());
        NutzerID id = NutzerID.neu();

        assertThrows(NutzerNichtGefundenException.class, () -> adminService.findeKonto(id));
    }

    // ------------------------------------------------------------------ sperreKonto

    @Test
    void sperreKonto_aktivesKonto_setztGesperrt_undPubliziert() {
        NutzerAccount konto = neuesKonto("sperr@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.sperreKonto(konto.getNutzerId());

        assertEquals(KontoStatus.BLOCKED, konto.getStatus());
        verify(eventPublisher).publiziere(any(AccountBlockedEvent.class));
    }

    @Test
    void sperreKonto_geloeschtesKonto_bleibtUnveraendert_keinEvent() {
        NutzerAccount konto = neuesKonto("geloescht@example.test");
        konto.loeschen();
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.sperreKonto(konto.getNutzerId());

        assertEquals(KontoStatus.DELETED, konto.getStatus());
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void sperreKonto_bereitsGesperrt_keinEvent() {
        // Vorbedingungs-Guard: ein bereits gesperrtes Konto wird nicht erneut gesperrt → kein Event
        NutzerAccount konto = neuesKonto("schon-gesperrt@example.test");
        konto.sperren();
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.sperreKonto(konto.getNutzerId());

        assertEquals(KontoStatus.BLOCKED, konto.getStatus());
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void sperreKonto_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.empty());
        NutzerID id = NutzerID.neu();

        assertThrows(NutzerNichtGefundenException.class, () -> adminService.sperreKonto(id));
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void sperreKonto_letzterAktiverAdmin_wirftException_keinEvent() {
        // Sperren würde den letzten aktiven Admin deaktivieren → Richtlinie lehnt ab
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        doThrow(new LetzterAdministratorException("letzter Admin"))
                .when(administratorRichtlinie).stelleSicherNichtLetzterAdministrator(konto);
        NutzerID id = konto.getNutzerId();

        assertThrows(LetzterAdministratorException.class, () -> adminService.sperreKonto(id));
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        verify(eventPublisher, never()).publiziere(any());
    }

    // ------------------------------------------------------------------ reaktiviereKonto

    @Test
    void reaktiviereKonto_gesperrtesKonto_setztAktiv_keinEvent() {
        NutzerAccount konto = neuesKonto("reaktiv@example.test");
        konto.sperren();
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.reaktiviereKonto(konto.getNutzerId());

        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void reaktiviereKonto_geloeschtesKonto_bleibtUnveraendert() {
        NutzerAccount konto = neuesKonto("tot@example.test");
        konto.loeschen();
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.reaktiviereKonto(konto.getNutzerId());

        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void reaktiviereKonto_bereitsAktiv_bleibtAktiv() {
        // Vorbedingungs-Guard: nur ein gesperrtes Konto wird reaktiviert; ein aktives bleibt No-Op
        NutzerAccount konto = neuesKonto("schon-aktiv@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.reaktiviereKonto(konto.getNutzerId());

        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    // ------------------------------------------------------------------ schalteKontoFrei

    @Test
    void schalteKontoFrei_ausstehendesKonto_setztAktivRolleBleibtProspect() {
        NutzerAccount konto = ausstehendesKonto("ausstehend@example.test"); // PENDING + PROSPECT
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.schalteKontoFrei(konto.getNutzerId());

        // Admin-Freischaltung aktiviert nur (kein CUSTOMER) — die Rolle bleibt PROSPECT
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        assertTrue(konto.getRollen().contains(Nutzerrolle.PROSPECT));
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    @Test
    void schalteKontoFrei_bereitsAktiv_bleibtUnveraendert() {
        // Vorbedingungs-Guard: nur ein ausstehendes Konto wird freigeschaltet (No-Op bei ACTIVE)
        NutzerAccount konto = neuesKonto("schon-aktiv@example.test"); // ACTIVE + PROSPECT
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.schalteKontoFrei(konto.getNutzerId());

        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    // ------------------------------------------------------------------ Rollen

    @Test
    void weiseRolleZu_fuegtRolleHinzu() {
        NutzerAccount konto = neuesKonto("rolle@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.weiseRolleZu(konto.getNutzerId(), Nutzerrolle.EVENT_MANAGER);

        assertTrue(konto.getRollen().contains(Nutzerrolle.EVENT_MANAGER));
    }

    @Test
    void entzieheRolle_letzterAktiverAdmin_wirftException() {
        // Schutz vor Selbstaussperrung: die Richtlinie lehnt den Entzug beim letzten Admin ab
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        doThrow(new LetzterAdministratorException("letzter Admin"))
                .when(administratorRichtlinie).stelleSicherNichtLetzterAdministrator(konto);
        NutzerID id = konto.getNutzerId();

        assertThrows(LetzterAdministratorException.class,
                () -> adminService.entzieheRolle(id, Nutzerrolle.ADMINISTRATOR));
        // Rolle bleibt erhalten — kein Entzug erfolgt
        assertTrue(konto.getRollen().contains(Nutzerrolle.ADMINISTRATOR));
    }

    @Test
    void entzieheRolle_keinLetzterAdmin_entferntRolle() {
        // Richtlinie erlaubt (kein letzter Admin) → Entzug erfolgt
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.entzieheRolle(konto.getNutzerId(), Nutzerrolle.ADMINISTRATOR);

        assertFalse(konto.getRollen().contains(Nutzerrolle.ADMINISTRATOR));
        verify(administratorRichtlinie).stelleSicherNichtLetzterAdministrator(konto);
    }

    @Test
    void entzieheRolle_nichtAdminRolle_prueftRichtlinieNicht() {
        // Bei einer anderen Rolle als ADMINISTRATOR ist keine Selbstaussperrung möglich → kein Check
        NutzerAccount konto = neuesKonto("rolle@example.test");
        konto.rolleZuweisen(Nutzerrolle.ACCOUNTANT);
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.entzieheRolle(konto.getNutzerId(), Nutzerrolle.ACCOUNTANT);

        assertFalse(konto.getRollen().contains(Nutzerrolle.ACCOUNTANT));
        verify(administratorRichtlinie, never()).stelleSicherNichtLetzterAdministrator(any());
    }

    // ------------------------------------------------------------------ aendereEmail / setzePasswortZurueck

    @Test
    void aendereEmail_freieEmail_aendert() {
        NutzerAccount konto = neuesKonto("ziel@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        when(nutzerRepository.findByEmail(new EmailAdresse("neu@example.test")))
                .thenReturn(Optional.empty());

        NutzerAccount ergebnis = adminService.aendereEmail(konto.getNutzerId(), "neu@example.test");

        assertEquals("neu@example.test", ergebnis.getEmail().adresse());
    }

    @Test
    void aendereEmail_vergebeneEmail_wirftEmailBereitsVergeben() {
        NutzerAccount konto = neuesKonto("ziel@example.test");
        NutzerAccount andererNutzer = neuesKonto("belegt@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        when(nutzerRepository.findByEmail(new EmailAdresse("belegt@example.test")))
                .thenReturn(Optional.of(andererNutzer));
        NutzerID id = konto.getNutzerId();

        assertThrows(EmailBereitsVergebenException.class,
                () -> adminService.aendereEmail(id, "belegt@example.test"));
        assertEquals("ziel@example.test", konto.getEmail().adresse());
    }

    @Test
    void aendereEmail_aufEigeneAktuelleEmail_bleibtErlaubt() {
        // No-Op-Zweig: die unveränderte eigene E-Mail des Kontos erneut setzen ist kein Konflikt
        // (Eindeutigkeits-Check findet das Konto selbst → equals == true → kein 409).
        NutzerAccount konto = neuesKonto("ziel@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        when(nutzerRepository.findByEmail(new EmailAdresse("ziel@example.test")))
                .thenReturn(Optional.of(konto));

        NutzerAccount ergebnis = adminService.aendereEmail(konto.getNutzerId(), "ziel@example.test");

        assertEquals("ziel@example.test", ergebnis.getEmail().adresse());
    }

    @Test
    void setzePasswortZurueck_setztNeuenHash() {
        NutzerAccount konto = neuesKonto("ziel@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.setzePasswortZurueck(konto.getNutzerId(), "AdminReset1!");

        assertTrue(konto.getPasswortHash().stimmt("AdminReset1!", encoder));
    }

    // ------------------------------------------------------------------ entferneKontoEndgueltig

    @Test
    void entferneKontoEndgueltig_loeschtPhysisch_undPubliziert() {
        NutzerAccount konto = neuesKonto("purge@example.test");
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));

        adminService.entferneKontoEndgueltig(konto.getNutzerId());

        verify(nutzerRepository).loescheEndgueltig(konto);
        verify(eventPublisher).publiziere(any(AccountDeletedEvent.class));
    }

    @Test
    void entferneKontoEndgueltig_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.empty());
        NutzerID id = NutzerID.neu();

        assertThrows(NutzerNichtGefundenException.class, () -> adminService.entferneKontoEndgueltig(id));
        verify(nutzerRepository, never()).loescheEndgueltig(any());
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void entferneKontoEndgueltig_letzterAktiverAdmin_wirftException_keinPurge() {
        // Hard-Delete des letzten aktiven Admins → Richtlinie lehnt ab
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.findByNutzerId(any())).thenReturn(Optional.of(konto));
        doThrow(new LetzterAdministratorException("letzter Admin"))
                .when(administratorRichtlinie).stelleSicherNichtLetzterAdministrator(konto);
        NutzerID id = konto.getNutzerId();

        assertThrows(LetzterAdministratorException.class, () -> adminService.entferneKontoEndgueltig(id));
        verify(nutzerRepository, never()).loescheEndgueltig(any());
        verify(eventPublisher, never()).publiziere(any());
    }
}
