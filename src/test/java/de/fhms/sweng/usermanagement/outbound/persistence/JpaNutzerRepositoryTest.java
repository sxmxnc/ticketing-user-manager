package de.fhms.sweng.usermanagement.outbound.persistence;

import de.fhms.sweng.usermanagement.domain.model.Adresse;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.Kundenprofil;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.model.Sparte;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstest für {@link JpaNutzerRepository} als JPA-Test-Slice.
 *
 * <p>{@code @DataJpaTest} lädt nur den JPA-Kontext (Entities, Repositories, Konverter, H2) —
 * kein vollständiger Spring-Kontext, kein Web-Layer, kein Security-Layer.
 * Damit ist der Test schnell und fokussiert auf das DB-Mapping.</p>
 *
 * <p>{@code spring.sql.init.mode=never} verhindert das Laden von data.sql im Test —
 * sonst würden Seed-Zeilen die Assertions verfälschen.</p>
 *
 * <p>Repository wird als {@link NutzerRepository} (Port-Typ) injiziert, nicht als
 * {@link JpaNutzerRepository} — vermeidet Mehrdeutigkeit bei {@code save()} und testet
 * gegen die Domain-Schnittstelle (hexagonales Prinzip).</p>
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class JpaNutzerRepositoryTest {

    // Port-Typ statt Implementierungstyp: Spring injiziert JpaNutzerRepository,
    // aber der Test kennt nur den Domain-Port (kein Compile-Leak auf JPA-Details)
    @Autowired
    private NutzerRepository repository;

    @Autowired
    private TestEntityManager em;

    // BCryptPasswordEncoder direkt instanziiert — kein Spring-Context nötig für den Encoder
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private NutzerAccount testKonto;

    @BeforeEach
    void setUp() {
        // Vorbedingung: ein gespeicherter NutzerAccount für Tests, die ihn voraussetzen
        EmailAdresse email = new EmailAdresse("simon@example.test");
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        testKonto = repository.save(
                NutzerAccount.registrieren(email, hash, new PersonenName("Max", "Mustermann")));
        // flush + clear: erzwingt echten DB-Roundtrip bei nachfolgenden Abfragen
        em.flush();
        em.clear();
    }

    @Test
    void save_und_findByNutzerId_speichertUndLaedt() {
        // Überprüft den vollständigen Round-Trip aller Value Objects durch die Konverter
        Optional<NutzerAccount> ergebnis = repository.findByNutzerId(testKonto.getNutzerId());

        assertTrue(ergebnis.isPresent());
        NutzerAccount geladen = ergebnis.get();
        assertEquals(testKonto.getNutzerId(), geladen.getNutzerId());
        assertEquals(testKonto.getEmail(), geladen.getEmail());
        assertNotNull(geladen.getPasswortHash());
        assertNotNull(geladen.getId());
    }

    @Test
    void save_und_findByNutzerId_laedtNamen() {
        // Round-Trip des eingebetteten PersonenName: vorname/nachname werden als Spalten
        // der nutzer-Tabelle (via @Embedded) persistiert und korrekt zurückgeladen
        Optional<NutzerAccount> ergebnis = repository.findByNutzerId(testKonto.getNutzerId());

        assertTrue(ergebnis.isPresent());
        assertEquals("Max", ergebnis.get().getName().vorname());
        assertEquals("Mustermann", ergebnis.get().getName().nachname());
    }

    @Test
    void findByNutzerId_nichtVorhanden_gibtLeerOptional() {
        Optional<NutzerAccount> ergebnis = repository.findByNutzerId(NutzerID.neu());

        assertFalse(ergebnis.isPresent());
    }

    @Test
    void findByEmail_findetKonto() {
        Optional<NutzerAccount> ergebnis = repository.findByEmail(new EmailAdresse("simon@example.test"));

        assertTrue(ergebnis.isPresent());
        assertEquals(testKonto.getNutzerId(), ergebnis.get().getNutzerId());
    }

    @Test
    void findByEmail_nichtVorhanden_gibtLeerOptional() {
        Optional<NutzerAccount> ergebnis = repository.findByEmail(new EmailAdresse("unbekannt@example.test"));

        assertFalse(ergebnis.isPresent());
    }

    @Test
    void suche_mitPassendemFilter_findetKonto() {
        List<NutzerAccount> ergebnis = repository.suche("example.test");

        assertFalse(ergebnis.isEmpty());
        assertEquals(testKonto.getNutzerId(), ergebnis.get(0).getNutzerId());
    }

    @Test
    void suche_mitNullFilter_gibtAlleKontenZurueck() {
        // Zweites Konto anlegen — null-Filter muss beide zurückgeben
        EmailAdresse email2 = new EmailAdresse("zweiter@example.com");
        PasswortHash hash2 = PasswortHash.vonKlartext("ZweitesPasswort1!", encoder);
        repository.save(NutzerAccount.registrieren(email2, hash2, new PersonenName("Erika", "Musterfrau")));
        em.flush();
        em.clear();

        List<NutzerAccount> ergebnis = repository.suche(null);

        assertEquals(2, ergebnis.size());
    }

    @Test
    void suche_ohnePassendenTreffer_gibtLeereListeZurueck() {
        List<NutzerAccount> ergebnis = repository.suche("gibtsNicht");

        assertTrue(ergebnis.isEmpty());
    }

    @Test
    void loeschen_setztStatusGeloescht_undZeileBleibtErhalten() {
        // Soft-Delete-Politik: loeschen() setzt nur den Status,
        // die Zeile bleibt für den Audit-Trail in der DB — kein physischer Hard-Delete.
        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.loeschen();
        em.flush();
        em.clear();

        Optional<NutzerAccount> nachLoeschen = repository.findByNutzerId(testKonto.getNutzerId());
        assertTrue(nachLoeschen.isPresent());
        assertEquals(KontoStatus.DELETED, nachLoeschen.get().getStatus());
    }

    @Test
    void loescheEndgueltig_entferntZeilePhysisch() {
        // Hard-Delete (DSGVO-Purge): die Zeile wird tatsächlich aus der DB entfernt —
        // anders als beim Soft-Delete bleibt kein Audit-Trail.
        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        repository.loescheEndgueltig(geladen);
        em.flush();
        em.clear();

        Optional<NutzerAccount> nachLoeschen = repository.findByNutzerId(testKonto.getNutzerId());
        assertFalse(nachLoeschen.isPresent());
    }

    @Test
    void loescheEndgueltig_entferntKundenprofilPerCascade() {
        // Entity-basierter Hard-Delete muss das zugehörige Kundenprofil über den Cascade mitlöschen
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        Kundenprofil profil = new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC));
        UUID profilId = profil.getProfilId();

        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.setKundenprofil(profil);
        em.flush();
        em.clear();

        NutzerAccount mitProfil = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        repository.loescheEndgueltig(mitProfil);
        em.flush();
        em.clear();

        assertFalse(repository.findByNutzerId(testKonto.getNutzerId()).isPresent());
        // Das Kundenprofil darf durch den Cascade ebenfalls nicht mehr existieren
        assertNull(em.find(Kundenprofil.class, profilId));
    }

    @Test
    void rollen_ElementCollection_wirdPersistiert() {
        // Prüft, dass die nutzer_rollen-Tabelle korrekt befüllt wird
        Optional<NutzerAccount> geladen = repository.findByNutzerId(testKonto.getNutzerId());

        assertTrue(geladen.isPresent());
        assertTrue(geladen.get().getRollen().contains(Nutzerrolle.PROSPECT));
        assertEquals(1, geladen.get().getRollen().size());
    }

    @Test
    void rolleZuweisen_wirdDurchJpaErkannt() {
        // Managed Entity: Änderung innerhalb @Transactional wird automatisch gespeichert
        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.rolleZuweisen(Nutzerrolle.CUSTOMER);
        em.flush();
        em.clear();

        NutzerAccount nachUpdate = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        assertTrue(nachUpdate.getRollen().contains(Nutzerrolle.CUSTOMER));
        assertTrue(nachUpdate.getRollen().contains(Nutzerrolle.PROSPECT));
    }

    @Test
    void version_erhoehtSichBeiUpdate() {
        // Prüft Optimistic Locking: @Version-Zähler steigt nach jedem Update
        long versionVorher = testKonto.getVersion();

        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.sperren();
        em.flush();
        em.clear();

        NutzerAccount nachUpdate = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        assertTrue(nachUpdate.getVersion() > versionVorher);
    }

    @Test
    void kundenprofil_wirdMitKaskadePersistiert() {
        // Prüft CascadeType.ALL: Kundenprofil + eingebettete Adresse + Interessen werden mitgespeichert
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        Kundenprofil profil = new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC, Sparte.THEATER));

        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.setKundenprofil(profil);
        em.flush();
        em.clear();

        NutzerAccount nachUpdate = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        Adresse geladeneAdresse = nachUpdate.getKundenprofil().getRechnungsAdresse();
        assertNotNull(nachUpdate.getKundenprofil());
        assertEquals("Hauptstraße", geladeneAdresse.strasse());
        assertEquals("12", geladeneAdresse.hausnummer());
        assertEquals("48149", geladeneAdresse.plz());
        assertEquals("Münster", geladeneAdresse.stadt());
        assertEquals("Deutschland", geladeneAdresse.land());
        assertTrue(nachUpdate.getKundenprofil().getInteressen().contains(Sparte.MUSIC));
        assertTrue(nachUpdate.getKundenprofil().getInteressen().contains(Sparte.THEATER));
    }

    @Test
    void setKundenprofil_aufNull_entferntProfilDurchOrphanRemoval() {
        // Profil anlegen und persistieren
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        Kundenprofil profil = new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC));
        UUID profilId = profil.getProfilId();

        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.setKundenprofil(profil);
        em.flush();
        em.clear();

        // Profil wieder entfernen → orphanRemoval=true muss die Zeile physisch löschen
        NutzerAccount mitProfil = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        mitProfil.setKundenprofil(null);
        em.flush();
        em.clear();

        NutzerAccount ohneProfil = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        assertNull(ohneProfil.getKundenprofil());
        // Direkt in der kundenprofil-Tabelle nachsehen: die Zeile darf nicht mehr existieren
        assertNull(em.find(Kundenprofil.class, profilId));
    }

    @Test
    void aktualisieren_aufPersistiertemProfil_wirdGespeichert() {
        // Profil mit Erstdaten persistieren
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        Kundenprofil profil = new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC));

        NutzerAccount geladen = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        geladen.setKundenprofil(profil);
        em.flush();
        em.clear();

        // Profil aktualisieren (managed Entity → Dirty Checking speichert beim flush)
        NutzerAccount mitProfil = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        Adresse neueAdresse = new Adresse("Münsterstraße", "5", "48145", "Münster", "Deutschland");
        mitProfil.getKundenprofil().aktualisieren(neueAdresse, EnumSet.of(Sparte.COMEDY, Sparte.THEATER));
        em.flush();
        em.clear();

        NutzerAccount nachUpdate = repository.findByNutzerId(testKonto.getNutzerId()).orElseThrow();
        Kundenprofil aktualisiert = nachUpdate.getKundenprofil();
        assertEquals("Münsterstraße", aktualisiert.getRechnungsAdresse().strasse());
        assertEquals("5", aktualisiert.getRechnungsAdresse().hausnummer());
        assertTrue(aktualisiert.getInteressen().contains(Sparte.COMEDY));
        assertTrue(aktualisiert.getInteressen().contains(Sparte.THEATER));
        assertFalse(aktualisiert.getInteressen().contains(Sparte.MUSIC));
    }

    @Test
    void zaehleMitRolleUndStatus_zaehltNurAktiveMitRolle() {
        // Zwei aktive Admins, ein gesperrter Admin, plus das PROSPECT-Seed-Konto aus setUp().
        // Erwartung: zaehleMitRolleUndStatus(ADMINISTRATOR, ACTIVE) == 2.
        adminKonto("admin1@example.test", false);
        adminKonto("admin2@example.test", false);
        adminKonto("gesperrt-admin@example.test", true);
        em.flush();
        em.clear();

        long anzahl = repository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE);

        assertEquals(2, anzahl);
    }

    /** Hilfsmethode: legt ein aktives Konto mit ADMINISTRATOR-Rolle an, optional gesperrt. */
    private void adminKonto(String email, boolean gesperrt) {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        NutzerAccount konto = NutzerAccount.registrieren(
                new EmailAdresse(email), hash, new PersonenName("Admin", "Tester"));
        // Saga-Freigabe simulieren: nur aktive Admins werden von zaehleMitRolleUndStatus gezählt
        konto.bestaetigeRegistrierung();
        konto.rolleZuweisen(Nutzerrolle.ADMINISTRATOR);
        if (gesperrt) {
            konto.sperren();
        }
        repository.save(konto);
    }
}
