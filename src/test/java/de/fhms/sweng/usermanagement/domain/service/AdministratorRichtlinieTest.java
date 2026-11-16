package de.fhms.sweng.usermanagement.domain.service;

import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link AdministratorRichtlinie} — Repository gemockt, kein Spring-Kontext.
 * Deckt die Invariante „mindestens ein aktiver Administrator" in allen Verzweigungen ab.
 */
@ExtendWith(MockitoExtension.class)
class AdministratorRichtlinieTest {

    @Mock
    private NutzerRepository nutzerRepository;

    private AdministratorRichtlinie richtlinie;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        richtlinie = new AdministratorRichtlinie(nutzerRepository);
    }

    /** Hilfsmethode: erzeugt ein bereits freigeschaltetes Konto (ACTIVE, PROSPECT). */
    private NutzerAccount neuesKonto(String email) {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName("Max", "Mustermann"));
        // Die Admin-Invariante zählt nur aktive Admins — Saga-Freigabe simulieren
        konto.bestaetigeRegistrierung();
        return konto;
    }

    @Test
    void istLetzterAktiverAdministrator_einzigerAktiverAdmin_true() {
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE))
                .thenReturn(1L);

        assertTrue(richtlinie.istLetzterAktiverAdministrator(konto));
    }

    @Test
    void istLetzterAktiverAdministrator_mehrereAktiveAdmins_false() {
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE))
                .thenReturn(2L);

        assertFalse(richtlinie.istLetzterAktiverAdministrator(konto));
    }

    @Test
    void istLetzterAktiverAdministrator_gesperrterAdmin_false() {
        // Nicht aktiv → zählt nicht; die Count-Abfrage darf gar nicht erst nötig sein
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        konto.sperren();

        assertFalse(richtlinie.istLetzterAktiverAdministrator(konto));
    }

    @Test
    void istLetzterAktiverAdministrator_aktivOhneAdminRolle_false() {
        NutzerAccount konto = neuesKonto("kunde@example.test");

        assertFalse(richtlinie.istLetzterAktiverAdministrator(konto));
    }

    @Test
    void stelleSicherNichtLetzterAdministrator_letzterAdmin_wirft() {
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE))
                .thenReturn(1L);

        assertThrows(LetzterAdministratorException.class,
                () -> richtlinie.stelleSicherNichtLetzterAdministrator(konto));
    }

    @Test
    void stelleSicherNichtLetzterAdministrator_keinLetzterAdmin_okay() {
        NutzerAccount konto = neuesKonto("admin@example.test");
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        when(nutzerRepository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE))
                .thenReturn(3L);

        assertDoesNotThrow(() -> richtlinie.stelleSicherNichtLetzterAdministrator(konto));
    }
}
