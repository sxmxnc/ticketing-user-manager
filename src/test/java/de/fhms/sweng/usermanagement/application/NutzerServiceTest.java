package de.fhms.sweng.usermanagement.application;

import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import de.fhms.sweng.usermanagement.domain.event.AccountDeletedEvent;
import de.fhms.sweng.usermanagement.domain.event.CustomerRegisteredEvent;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.FalschesPasswortException;
import de.fhms.sweng.usermanagement.domain.exception.KontoNichtAktivException;
import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.domain.model.Adresse;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.Kundenprofil;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.model.Sparte;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import de.fhms.sweng.usermanagement.domain.service.AdministratorRichtlinie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link NutzerService}.
 * Das Repository und der EventPublisher werden mit Mockito gemockt; der {@code PasswordEncoder}
 * ist ein echter BCryptPasswordEncoder (kein Spring-Kontext nötig).
 */
@ExtendWith(MockitoExtension.class)
class NutzerServiceTest {

    /** Klartext-Passwort, mit dem {@link #neuesKonto(String)} jedes Testkonto anlegt. */
    private static final String PASSWORT = "GeheimesPasswort1!";

    /** Vor- und Nachname, mit denen die Testkonten angelegt werden. */
    private static final String VORNAME = "Max";
    private static final String NACHNAME = "Mustermann";

    @Mock
    private NutzerRepository nutzerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AdministratorRichtlinie administratorRichtlinie;

    // Echter Encoder statt Mock — realistisches Hashing, keine Stubs nötig
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private NutzerService nutzerService;

    @BeforeEach
    void setUp() {
        nutzerService = new NutzerService(nutzerRepository, encoder, eventPublisher, administratorRichtlinie);
    }

    /**
     * Hilfsmethode: erzeugt ein bereits freigeschaltetes (ACTIVE) Testkonto mit dem Passwort
     * {@link #PASSWORT}. Self-Service-Tests brauchen ein nutzbares Konto — die Saga-Freigabe
     * (PENDING → ACTIVE) wird hier simuliert.
     */
    private NutzerAccount neuesKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext(PASSWORT, encoder);
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName(VORNAME, NACHNAME));
        konto.bestaetigeRegistrierung();
        return konto;
    }

    /** Hilfsmethode: erzeugt ein noch ausstehendes (PENDING) Testkonto (Saga nicht abgeschlossen). */
    private NutzerAccount ausstehendesKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext(PASSWORT, encoder);
        return NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName(VORNAME, NACHNAME));
    }

    private Adresse testAdresse() {
        return new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
    }

    // ------------------------------------------------------------------ registriere

    @Test
    void registriere_neueEmail_speichertUndGibtKontoZurueck() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(nutzerRepository.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

        NutzerAccount ergebnis = nutzerService.registriere("neu@example.test", PASSWORT, VORNAME, NACHNAME);

        // Semantic Lock: ein frisch registriertes Konto startet PENDING
        assertEquals(KontoStatus.PENDING, ergebnis.getStatus());
        assertTrue(ergebnis.getRollen().contains(Nutzerrolle.PROSPECT));
        assertEquals("neu@example.test", ergebnis.getEmail().adresse());
        verify(nutzerRepository).save(any());
        // Event muss nach dem save() publiziert worden sein
        verify(eventPublisher).publiziere(any(CustomerRegisteredEvent.class));
    }

    @Test
    void registriere_vorhandeneEmail_wirftEmailBereitsVergeben() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(neuesKonto("schon@da.de")));

        assertThrows(EmailBereitsVergebenException.class,
                () -> nutzerService.registriere("schon@da.de", PASSWORT, VORNAME, NACHNAME));
        verify(nutzerRepository, never()).save(any());
        // Bei Ablehnung darf kein Event publiziert werden
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void registriere_ungueltigeEmail_wirftIllegalArgument() {
        // Die EmailAdresse-VO wirft schon vor dem Repository-Zugriff
        assertThrows(IllegalArgumentException.class,
                () -> nutzerService.registriere("keineEmail", PASSWORT, VORNAME, NACHNAME));
        verify(nutzerRepository, never()).save(any());
    }

    @Test
    void registriere_zuKurzesPasswort_wirftIllegalArgument() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> nutzerService.registriere("neu@example.test", "kurz", VORNAME, NACHNAME));
        verify(nutzerRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ findeEigenes

    @Test
    void findeEigenes_vorhanden_gibtKonto() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertEquals(konto, nutzerService.findeEigenes("simon@example.test"));
    }

    @Test
    void findeEigenes_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(NutzerNichtGefundenException.class,
                () -> nutzerService.findeEigenes("weg@example.test"));
    }

    // ------------------------------------------------------------------ aktualisiereEigenesProfil

    @Test
    void aktualisiereEigenesProfil_ohneBestehendesProfil_legtProfilAn() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        nutzerService.aktualisiereEigenesProfil("simon@example.test", testAdresse(), EnumSet.of(Sparte.MUSIC));

        assertNotNull(konto.getKundenprofil());
        assertEquals(testAdresse(), konto.getKundenprofil().getRechnungsAdresse());
        assertTrue(konto.getKundenprofil().getInteressen().contains(Sparte.MUSIC));
    }

    @Test
    void aktualisiereEigenesProfil_mitBestehendemProfil_aktualisiert() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        konto.setKundenprofil(new Kundenprofil(testAdresse(), EnumSet.of(Sparte.MUSIC)));
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        Adresse neueAdresse = new Adresse("Münsterstraße", "5", "48145", "Münster", "Deutschland");
        nutzerService.aktualisiereEigenesProfil("simon@example.test", neueAdresse, EnumSet.of(Sparte.COMEDY));

        assertEquals(neueAdresse, konto.getKundenprofil().getRechnungsAdresse());
        assertTrue(konto.getKundenprofil().getInteressen().contains(Sparte.COMEDY));
        assertFalse(konto.getKundenprofil().getInteressen().contains(Sparte.MUSIC));
    }

    @Test
    void aktualisiereEigenesProfil_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());
        Adresse adresse = testAdresse();
        Set<Sparte> interessen = EnumSet.of(Sparte.MUSIC);

        assertThrows(NutzerNichtGefundenException.class,
                () -> nutzerService.aktualisiereEigenesProfil("weg@example.test", adresse, interessen));
    }

    // ------------------------------------------------------------------ aendereEigeneEmail

    @Test
    void aendereEigeneEmail_korrektesPasswortUndFreieEmail_aendertEmail() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(new EmailAdresse("simon@example.test")))
                .thenReturn(Optional.of(konto));
        when(nutzerRepository.findByEmail(new EmailAdresse("neu@example.test")))
                .thenReturn(Optional.empty());

        NutzerAccount ergebnis = nutzerService.aendereEigeneEmail(
                "simon@example.test", "neu@example.test", PASSWORT);

        assertEquals("neu@example.test", ergebnis.getEmail().adresse());
    }

    @Test
    void aendereEigeneEmail_falschesPasswort_wirftFalschesPasswort() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(new EmailAdresse("simon@example.test")))
                .thenReturn(Optional.of(konto));

        assertThrows(FalschesPasswortException.class,
                () -> nutzerService.aendereEigeneEmail("simon@example.test", "neu@example.test", "falsch"));
        // E-Mail bleibt unverändert
        assertEquals("simon@example.test", konto.getEmail().adresse());
    }

    @Test
    void aendereEigeneEmail_neueEmailVergeben_wirftEmailBereitsVergeben() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        NutzerAccount andererNutzer = neuesKonto("belegt@example.test");
        when(nutzerRepository.findByEmail(new EmailAdresse("simon@example.test")))
                .thenReturn(Optional.of(konto));
        when(nutzerRepository.findByEmail(new EmailAdresse("belegt@example.test")))
                .thenReturn(Optional.of(andererNutzer));

        assertThrows(EmailBereitsVergebenException.class,
                () -> nutzerService.aendereEigeneEmail("simon@example.test", "belegt@example.test", PASSWORT));
        assertEquals("simon@example.test", konto.getEmail().adresse());
    }

    @Test
    void aendereEigeneEmail_aufEigeneAktuelleEmail_bleibtErlaubt() {
        // No-Op-Zweig: dieselbe (eigene) E-Mail erneut setzen ist erlaubt — der Eindeutigkeits-Check
        // findet zwar ein Konto, aber es ist das eigene (equals == true) → kein 409.
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(new EmailAdresse("simon@example.test")))
                .thenReturn(Optional.of(konto));

        NutzerAccount ergebnis = nutzerService.aendereEigeneEmail(
                "simon@example.test", "simon@example.test", PASSWORT);

        assertEquals("simon@example.test", ergebnis.getEmail().adresse());
    }

    // ------------------------------------------------------------------ aendereEigenesPasswort

    @Test
    void aendereEigenesPasswort_korrektesAltesPasswort_setztNeuenHash() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        NutzerAccount ergebnis = nutzerService.aendereEigenesPasswort(
                "simon@example.test", PASSWORT, "NeuesPasswort1!");

        assertTrue(ergebnis.getPasswortHash().stimmt("NeuesPasswort1!", encoder));
        assertFalse(ergebnis.getPasswortHash().stimmt(PASSWORT, encoder));
    }

    @Test
    void aendereEigenesPasswort_falschesAltesPasswort_wirftFalschesPasswort() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(FalschesPasswortException.class,
                () -> nutzerService.aendereEigenesPasswort("simon@example.test", "falsch", "NeuesPasswort1!"));
        // Passwort bleibt unverändert
        assertTrue(konto.getPasswortHash().stimmt(PASSWORT, encoder));
    }

    @Test
    void aendereEigenesPasswort_zuKurzesNeuesPasswort_wirftIllegalArgument() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(IllegalArgumentException.class,
                () -> nutzerService.aendereEigenesPasswort("simon@example.test", PASSWORT, "kurz"));
    }

    // ------------------------------------------------------------------ aendereEigenenNamen

    @Test
    void aendereEigenenNamen_aktivesKonto_setztNeuenNamen() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        NutzerAccount ergebnis = nutzerService.aendereEigenenNamen("simon@example.test", "Erika", "Musterfrau");

        assertEquals("Erika", ergebnis.getName().vorname());
        assertEquals("Musterfrau", ergebnis.getName().nachname());
    }

    @Test
    void aendereEigenenNamen_gesperrtesKonto_wirftKontoNichtAktiv() {
        NutzerAccount konto = neuesKonto("gesperrt@example.test");
        konto.sperren();
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(KontoNichtAktivException.class,
                () -> nutzerService.aendereEigenenNamen("gesperrt@example.test", "Erika", "Musterfrau"));
        // Name unverändert — der Status-Guard greift vor der Änderung
        assertEquals(VORNAME, konto.getName().vorname());
    }

    @Test
    void aendereEigenenNamen_leererName_wirftIllegalArgument() {
        // Das PersonenName-VO validiert die Eingabe → IllegalArgumentException (→ 400)
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(IllegalArgumentException.class,
                () -> nutzerService.aendereEigenenNamen("simon@example.test", "  ", "Musterfrau"));
    }

    @Test
    void aendereEigenenNamen_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(NutzerNichtGefundenException.class,
                () -> nutzerService.aendereEigenenNamen("weg@example.test", "Erika", "Musterfrau"));
    }

    // ------------------------------------------------------------------ Status-Guard (nicht aktives Konto)

    @Test
    void aktualisiereEigenesProfil_gesperrtesKonto_wirftKontoNichtAktiv() {
        NutzerAccount konto = neuesKonto("gesperrt@example.test");
        konto.sperren();
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));
        Adresse adresse = testAdresse();
        Set<Sparte> interessen = EnumSet.of(Sparte.MUSIC);

        assertThrows(KontoNichtAktivException.class,
                () -> nutzerService.aktualisiereEigenesProfil("gesperrt@example.test", adresse, interessen));
        // Kein Profil angelegt
        assertNull(konto.getKundenprofil());
    }

    @Test
    void aendereEigeneEmail_gesperrtesKonto_wirftKontoNichtAktiv() {
        NutzerAccount konto = neuesKonto("gesperrt@example.test");
        konto.sperren();
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(KontoNichtAktivException.class,
                () -> nutzerService.aendereEigeneEmail("gesperrt@example.test", "neu@example.test", PASSWORT));
        // E-Mail unverändert; der Guard greift vor Passwortprüfung und Eindeutigkeit
        assertEquals("gesperrt@example.test", konto.getEmail().adresse());
    }

    @Test
    void aendereEigenesPasswort_geloeschtesKonto_wirftKontoNichtAktiv() {
        NutzerAccount konto = neuesKonto("geloescht@example.test");
        konto.loeschen();
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        assertThrows(KontoNichtAktivException.class,
                () -> nutzerService.aendereEigenesPasswort("geloescht@example.test", PASSWORT, "NeuesPasswort1!"));
        // Passwort unverändert
        assertTrue(konto.getPasswortHash().stimmt(PASSWORT, encoder));
    }

    // ------------------------------------------------------------------ loescheEigenesKonto

    @Test
    void loescheEigenesKonto_vorhanden_setztStatusGeloescht() {
        NutzerAccount konto = neuesKonto("simon@example.test");
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));

        nutzerService.loescheEigenesKonto("simon@example.test");

        assertEquals(KontoStatus.DELETED, konto.getStatus());
        verify(eventPublisher).publiziere(any(AccountDeletedEvent.class));
    }

    @Test
    void loescheEigenesKonto_nichtVorhanden_wirftNutzerNichtGefunden() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(NutzerNichtGefundenException.class,
                () -> nutzerService.loescheEigenesKonto("weg@example.test"));
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void loescheEigenesKonto_letzterAktiverAdmin_wirftException_keinSoftDelete() {
        // Auch der Soft-Delete darf den letzten aktiven Admin nicht beseitigen — Richtlinie lehnt ab
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.of(konto));
        doThrow(new LetzterAdministratorException("letzter Admin"))
                .when(administratorRichtlinie).stelleSicherNichtLetzterAdministrator(konto);

        assertThrows(LetzterAdministratorException.class,
                () -> nutzerService.loescheEigenesKonto("admin@example.test"));
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        verify(eventPublisher, never()).publiziere(any());
    }

    // ------------------------------------------------------------------ aktiviereKonto (Saga-Erfolg)

    @Test
    void aktiviereKonto_ausstehend_setztAktivRolleBleibtProspect() {
        NutzerAccount konto = ausstehendesKonto("saga@example.test"); // PENDING + PROSPECT
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.aktiviereKonto(konto.getNutzerId());

        // Saga-Erfolg: Semantic Lock freigegeben (ACTIVE); Rolle bleibt PROSPECT (kein CUSTOMER)
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
        assertTrue(konto.getRollen().contains(Nutzerrolle.PROSPECT));
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    @Test
    void aktiviereKonto_zweimalAufgerufen_bleibtAktiv() {
        // Idempotenz: zweite Zustellung schaltet nicht erneut um
        NutzerAccount konto = ausstehendesKonto("saga@example.test");
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.aktiviereKonto(konto.getNutzerId());
        nutzerService.aktiviereKonto(konto.getNutzerId());

        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void aktiviereKonto_unbekannteNutzerId_keinFehler() {
        NutzerID id = NutzerID.neu();
        when(nutzerRepository.findByNutzerId(id)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> nutzerService.aktiviereKonto(id));
    }

    @Test
    void aktiviereKonto_gesperrtesKonto_bleibtUnveraendert() {
        // Out-of-order/verspätet: ein gesperrtes Konto wird durch eine Bestätigung nicht aktiviert
        NutzerAccount konto = neuesKonto("gesperrt@example.test");
        konto.sperren();
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.aktiviereKonto(konto.getNutzerId());

        assertEquals(KontoStatus.BLOCKED, konto.getStatus());
    }

    // ------------------------------------------------------------------ lehneRegistrierungAb (Kompensation)

    @Test
    void lehneRegistrierungAb_ausstehend_setztAbgelehnt() {
        NutzerAccount konto = ausstehendesKonto("saga@example.test");
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.lehneRegistrierungAb(konto.getNutzerId(), "Timeout");

        // Kompensation: ausstehendes Konto wird abgelehnt, keine CUSTOMER-Rolle, kein Event
        assertEquals(KontoStatus.REJECTED, konto.getStatus());
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
        verify(eventPublisher, never()).publiziere(any());
    }

    @Test
    void lehneRegistrierungAb_bereitsAktiv_keineKompensation() {
        // Out-of-order: gewann das Erfolgs-Event das Rennen, bleibt das Konto ACTIVE (kein Downgrade)
        NutzerAccount konto = neuesKonto("saga@example.test");
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.lehneRegistrierungAb(konto.getNutzerId(), "Timeout");

        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void lehneRegistrierungAb_unbekannteNutzerId_wirdIgnoriert() {
        NutzerID id = NutzerID.neu();
        when(nutzerRepository.findByNutzerId(id)).thenReturn(Optional.empty());

        // Kein Konto gefunden → kein Fehler, Event wird nur geloggt und ignoriert
        assertDoesNotThrow(() -> nutzerService.lehneRegistrierungAb(id, "Timeout"));
    }

    // ------------------------------------------------------------------ befoerdereZuKunde (Kauf, per NutzerID)

    @Test
    void befoerdereZuKundePerId_aktiverProspect_wirdCustomerOhneProspect() {
        NutzerAccount konto = neuesKonto("kaeufer@example.test"); // ACTIVE + PROSPECT
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.befoerdereZuKunde(konto.getNutzerId());

        // Konversion auf der Rollen-Achse: CUSTOMER zugewiesen, PROSPECT entzogen; Status unverändert
        assertTrue(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
        assertFalse(konto.getRollen().contains(Nutzerrolle.PROSPECT));
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void befoerdereZuKundePerId_ausstehendesKonto_wirdCustomerStatusBleibtPending() {
        // Belang-Trennung: ein PENDING-Käufer wird CUSTOMER (Rolle), der Status bleibt PENDING —
        // die Freigabe des Semantic Locks ist allein Sache der Registrierungs-Saga.
        NutzerAccount konto = ausstehendesKonto("pending@example.test"); // PENDING + PROSPECT
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.befoerdereZuKunde(konto.getNutzerId());

        assertTrue(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
        assertFalse(konto.getRollen().contains(Nutzerrolle.PROSPECT));
        assertEquals(KontoStatus.PENDING, konto.getStatus());
    }

    @Test
    void befoerdereZuKundePerId_gesperrtesKonto_keineBefoerderung() {
        NutzerAccount konto = neuesKonto("gesperrt@example.test");
        konto.sperren(); // BLOCKED — terminal/gesperrt, keine Beförderung
        when(nutzerRepository.findByNutzerId(konto.getNutzerId())).thenReturn(Optional.of(konto));

        nutzerService.befoerdereZuKunde(konto.getNutzerId());

        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    @Test
    void befoerdereZuKundePerId_unbekannteNutzerId_keinFehler() {
        NutzerID id = NutzerID.neu();
        when(nutzerRepository.findByNutzerId(id)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> nutzerService.befoerdereZuKunde(id));
    }
}
