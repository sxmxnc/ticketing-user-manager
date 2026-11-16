package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Aggregate Root NutzerAccount.
 * Kein Spring-Kontext nötig — reiner Java-Test der Domänenlogik.
 */
class NutzerAccountTest {

    private NutzerAccount konto;

    // Gültiger Name für jeden Test — als Konstante, damit er auch in assertThrows-Lambdas referenziert
    // werden kann, ohne dort einen zweiten Methodenaufruf einzuführen (SonarQube S5778).
    private static final PersonenName NAME = new PersonenName("Max", "Mustermann");

    @BeforeEach
    void setUp() {
        // Vorbedingung: gültiger NutzerAccount für jeden Test
        EmailAdresse email = new EmailAdresse("simon@example.test");
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        konto = NutzerAccount.registrieren(email, hash, NAME);
    }

    @Test
    void registrieren_erstelltKontoMitStatusAusstehend() {
        // Semantic Lock: ein neues Konto startet PENDING, bis die Saga es freigibt
        assertEquals(KontoStatus.PENDING, konto.getStatus());
    }

    @Test
    void bestaetigeRegistrierung_ausAusstehend_setztAktiv() {
        konto.bestaetigeRegistrierung();
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void bestaetigeRegistrierung_ausAktiv_bleibtAktiv() {
        // Idempotenz: ein doppelt/verspätet eintreffendes Erfolgs-Event schaltet nicht erneut um
        konto.bestaetigeRegistrierung();
        konto.bestaetigeRegistrierung();
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void bestaetigeRegistrierung_ausGeloescht_belaesstGeloescht() {
        // Keine Resurrection: ein verspätetes Erfolgs-Event belebt ein terminales Konto nicht wieder
        konto.loeschen();
        konto.bestaetigeRegistrierung();
        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void registrierungAblehnen_ausAusstehend_setztAbgelehnt() {
        konto.registrierungAblehnen();
        assertEquals(KontoStatus.REJECTED, konto.getStatus());
    }

    @Test
    void registrierungAblehnen_ausAktiv_belaesstAktiv() {
        // Kein Downgrade: gewann das Erfolgs-Event das Rennen, bleibt das Konto ACTIVE
        konto.bestaetigeRegistrierung();
        konto.registrierungAblehnen();
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void registrierungAblehnen_ausGeloescht_belaesstGeloescht() {
        konto.loeschen();
        konto.registrierungAblehnen();
        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void registrieren_erstelltKontoMitRolleInteressent() {
        assertTrue(konto.getRollen().contains(Nutzerrolle.PROSPECT));
        assertEquals(1, konto.getRollen().size());
    }

    @Test
    void registrieren_erstelltKontoMitEindeutigerNutzerID() {
        EmailAdresse email2 = new EmailAdresse("andere@example.test");
        PasswortHash hash2 = PasswortHash.vonKlartext("AnderesPasswort1!", new BCryptPasswordEncoder());
        NutzerAccount konto2 = NutzerAccount.registrieren(email2, hash2, NAME);

        assertNotEquals(konto.getNutzerId(), konto2.getNutzerId());
    }

    @Test
    void registrieren_mitNullEmail_wirftNullPointerException() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        assertThrows(NullPointerException.class, () -> NutzerAccount.registrieren(null, hash, NAME));
    }

    @Test
    void registrieren_mitNullPasswort_wirftNullPointerException() {
        EmailAdresse email = new EmailAdresse("simon@example.test");
        assertThrows(NullPointerException.class, () -> NutzerAccount.registrieren(email, null, NAME));
    }

    @Test
    void registrieren_mitNullName_wirftNullPointerException() {
        // Name ist Pflichtfeld der Factory — fehlt er, ist das ein Programmierfehler → NPE
        EmailAdresse email = new EmailAdresse("simon@example.test");
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        assertThrows(NullPointerException.class, () -> NutzerAccount.registrieren(email, hash, null));
    }

    @Test
    void sperren_setztStatusGesperrt() {
        konto.sperren();
        assertEquals(KontoStatus.BLOCKED, konto.getStatus());
    }

    @Test
    void sperren_aufGeloeschtesKonto_aendertStatusNicht() {
        // Gelöschte Konten dürfen nicht gesperrt werden
        konto.loeschen();
        konto.sperren();
        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void reaktivieren_setztStatusAktiv() {
        konto.sperren();
        konto.reaktivieren();
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void reaktivieren_aufGeloeschtesKonto_aendertStatusNicht() {
        konto.loeschen();
        konto.reaktivieren();
        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void sperren_aufAbgelehntesKonto_aendertStatusNicht() {
        // REJECTED ist terminal (Saga-Kompensation endgültig) — nicht sperrbar
        konto.registrierungAblehnen();
        konto.sperren();
        assertEquals(KontoStatus.REJECTED, konto.getStatus());
    }

    @Test
    void reaktivieren_aufAbgelehntesKonto_aendertStatusNicht() {
        konto.registrierungAblehnen();
        konto.reaktivieren();
        assertEquals(KontoStatus.REJECTED, konto.getStatus());
    }

    @Test
    void loeschen_setztStatusGeloescht() {
        konto.loeschen();
        assertEquals(KontoStatus.DELETED, konto.getStatus());
    }

    @Test
    void rolleZuweisen_fuegtRolleHinzu() {
        konto.rolleZuweisen(Nutzerrolle.CUSTOMER);
        assertTrue(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    @Test
    void rolleEntziehen_entferntRolle() {
        konto.rolleZuweisen(Nutzerrolle.CUSTOMER);
        konto.rolleEntziehen(Nutzerrolle.CUSTOMER);
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER));
    }

    @Test
    void rolleZuweisen_mitNull_wirftNullPointerException() {
        assertThrows(NullPointerException.class, () -> konto.rolleZuweisen(null));
    }

    @Test
    void emailAendern_setztNeueEmail() {
        EmailAdresse neueEmail = new EmailAdresse("neu@example.test");
        konto.emailAendern(neueEmail);
        assertEquals(neueEmail, konto.getEmail());
    }

    @Test
    void emailAendern_mitNull_wirftNullPointerException() {
        assertThrows(NullPointerException.class, () -> konto.emailAendern(null));
    }

    @Test
    void getName_liefertNamen() {
        // Der bei der Registrierung gesetzte Name muss unverändert abrufbar sein
        assertEquals(NAME, konto.getName());
    }

    @Test
    void nameAendern_setztNeuenNamen() {
        PersonenName neuerName = new PersonenName("Erika", "Musterfrau");
        konto.nameAendern(neuerName);
        assertEquals(neuerName, konto.getName());
    }

    @Test
    void nameAendern_mitNull_wirftNullPointerException() {
        assertThrows(NullPointerException.class, () -> konto.nameAendern(null));
    }

    @Test
    void passwortAendern_setztNeuenHash() {
        PasswortHash neuerHash = PasswortHash.vonKlartext("NeuesPasswort1!", new BCryptPasswordEncoder());
        konto.passwortAendern(neuerHash);
        assertEquals(neuerHash, konto.getPasswortHash());
    }

    @Test
    void passwortAendern_mitNull_wirftNullPointerException() {
        assertThrows(NullPointerException.class, () -> konto.passwortAendern(null));
    }

    @Test
    void getRollen_gibtUnveraenderlichesSetZurueck() {
        // Defensiver Copy beim Rausgeben: externe Änderungen müssen blockiert werden
        // Erst holen (außerhalb Lambda), dann nur .add() testen
        Set<Nutzerrolle> rollen = konto.getRollen();
        assertThrows(UnsupportedOperationException.class,
                () -> rollen.add(Nutzerrolle.ADMINISTRATOR));
    }

    @Test
    void equals_gleichesObjekt_istGleich() {
        assertEquals(konto, konto);
    }

    @Test
    void equals_unterschiedlicheKonten_sindNichtGleich() {
        EmailAdresse email2 = new EmailAdresse("andere@example.test");
        PasswortHash hash2 = PasswortHash.vonKlartext("AnderesPasswort1!", new BCryptPasswordEncoder());
        NutzerAccount konto2 = NutzerAccount.registrieren(email2, hash2, NAME);

        assertNotEquals(konto, konto2);
    }

    @Test
    void equals_mitNull_istNichtGleich() {
        // konto MUSS der Receiver sein, sonst kurzschließt Objects.equals und ruft konto.equals(null)
        // nie auf — dann bliebe der instanceof-Branch ungetestet.
        assertNotEquals(konto, null);
    }

    @Test
    void equals_mitAnderemTyp_istNichtGleich() {
        // instanceof-Branch: konto.equals(String) → String ist kein NutzerAccount → false
        assertNotEquals(konto, "keinNutzerAccount");
    }

    @Test
    void getKundenprofil_neuesKonto_istNull() {
        // 0..1-Beziehung: ein frisch registriertes Konto hat noch kein Kundenprofil
        assertNull(konto.getKundenprofil());
    }

    @Test
    void setKundenprofil_haengtProfilAnUndGibtEsZurueck() {
        // Wird beim ersten Pflegen des eigenen Profils (PUT /rest/users/me) aufgerufen
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        Kundenprofil profil = new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC));

        konto.setKundenprofil(profil);

        assertEquals(profil, konto.getKundenprofil());
    }
}
