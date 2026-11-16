package de.fhms.sweng.usermanagement.application;

import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import de.fhms.sweng.usermanagement.domain.event.AccountDeletedEvent;
import de.fhms.sweng.usermanagement.domain.event.AccountBlockedEvent;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import de.fhms.sweng.usermanagement.domain.service.AdministratorRichtlinie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application Service (Use-Case-Schicht) für die Administration von Nutzerkonten.
 *
 * <p>Bündelt die Admin-Use-Cases hinter {@code /rest/admin/**}: alle Konten auflisten/suchen,
 * ein Konto sperren/reaktivieren, Rollen zuweisen/entziehen und ein Konto physisch entfernen
 * (Hard-Delete, DSGVO-Purge). Die Zugriffsbeschränkung auf die Rolle ADMINISTRATOR übernimmt die
 * Security-Schicht ({@code SecurityConfig}); dieser Service enthält die reine Fachlogik.</p>
 *
 * <p>Transaktionen: schreibende Methoden sind {@code @Transactional}, rein lesende
 * {@code @Transactional(readOnly = true)}. Managed Entities werden innerhalb der Transaktion
 * geändert — JPA schreibt die Änderung beim Commit, ein expliziter {@code save()} entfällt.
 * {@code @Retryable} auf Klassenebene macht die Schreiboperationen gegen Versionskonflikte
 * (Optimistic Locking) resilient.</p>
 *
 * <p>Events (Eventual Consistency): {@code sperreKonto} veröffentlicht ein {@link AccountBlockedEvent},
 * der Hard-Delete ein {@link AccountDeletedEvent}. Reaktivierung und Rollenänderungen lösen bewusst
 * kein Event aus (kein definierter Downstream-Consumer).</p>
 *
 * <p>No-Op-Guards: Ein bereits gelöschtes Konto (DELETED) wird nicht gesperrt oder reaktiviert —
 * Soft-Delete ist endgültig. Der übersprungene Fall wird hier in der Service-Schicht als Warnung
 * protokolliert (das Domänenmodell bleibt log-frei).</p>
 */
@Service
// Optimistic Locking: Bei konkurrierendem Schreiben wirft JPA beim Commit eine OptimisticLockException,
// die Spring in eine OptimisticLockingFailureException übersetzt. @Retryable wiederholt die Operation
// dann automatisch (erneutes Lesen mit frischer Version), bevor auf dem REST-Pfad der 409-Fallback greift.
// Der Retry-Advisor liegt per Default außerhalb der Transaktion und umschließt den Commit damit korrekt.
@Retryable(includes = OptimisticLockingFailureException.class,
        maxRetries = 3, delay = 100, jitter = 10, multiplier = 2)
public class AdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    private final NutzerRepository nutzerRepository;
    private final EventPublisher eventPublisher;
    private final AdministratorRichtlinie administratorRichtlinie;
    private final PasswordEncoder passwordEncoder;

    /**
     * Konstruktor-Injektion der benötigten Beans.
     *
     * @param nutzerRepository        der Persistenz-Port
     * @param eventPublisher          der Port zum Veröffentlichen von Domain-Events (RabbitMQ)
     * @param administratorRichtlinie der Domain-Service für die „mindestens ein aktiver Admin"-Regel
     * @param passwordEncoder         der BCrypt-Encoder zum Hashen beim Zurücksetzen eines Passworts
     */
    public AdminService(NutzerRepository nutzerRepository, EventPublisher eventPublisher,
                        AdministratorRichtlinie administratorRichtlinie, PasswordEncoder passwordEncoder) {
        this.nutzerRepository = nutzerRepository;
        this.eventPublisher = eventPublisher;
        this.administratorRichtlinie = administratorRichtlinie;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Listet alle Konten auf, optional gefiltert nach einem E-Mail-Teilstring.
     *
     * <p>Liefert bewusst <b>auch</b> Konten mit Status DELETED — ein Administrator soll den
     * vollständigen Bestand (inkl. soft-gelöschter Konten) für Audit und Purge sehen.</p>
     *
     * @param filter Suchbegriff (Teilstring der E-Mail), oder null für alle Konten
     * @return Liste der passenden Konten (niemals null)
     */
    @Transactional(readOnly = true)
    public List<NutzerAccount> findeAlleKonten(String filter) {
        LOGGER.info("findeAlleKonten() — Kontoliste wird geladen");
        return nutzerRepository.suche(filter);
    }

    /**
     * Lädt ein einzelnes Konto anhand seiner fachlichen ID.
     *
     * @param id die NutzerID
     * @return der gefundene NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     */
    @Transactional(readOnly = true)
    public NutzerAccount findeKonto(NutzerID id) {
        NutzerAccount konto = ladeKonto(id);
        LOGGER.info("findeKonto() — Konto {} geladen", id);
        return konto;
    }

    /**
     * Sperrt ein Konto (Status BLOCKED). Ein bereits gelöschtes Konto bleibt unverändert (No-Op).
     *
     * @param id die NutzerID
     * @return der (ggf. gesperrte) NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     * @throws de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException
     *         wenn das Konto der letzte aktive Administrator ist
     */
    @Transactional
    public NutzerAccount sperreKonto(NutzerID id) {
        NutzerAccount konto = ladeKonto(id);
        // Nur ein aktives Konto kann gesperrt werden. Bereits gesperrte oder gelöschte Konten bleiben
        // unverändert (No-Op) — und es wird kein Event publiziert, sonst entstünden bei wiederholtem
        // Sperren redundante AccountBlockedEvents. Es wird also nur beim echten Zustandswechsel gemeldet.
        if (konto.getStatus() != KontoStatus.ACTIVE) {
            LOGGER.warn("sperreKonto() — Konto {} ist nicht aktiv (Status {}), keine Aktion",
                    id, konto.getStatus());
            return konto;
        }
        // Sperren würde den Admin inaktiv machen — der letzte aktive Administrator darf das nicht sein
        administratorRichtlinie.stelleSicherNichtLetzterAdministrator(konto);
        konto.sperren();
        LOGGER.info("sperreKonto() — Konto {} gesperrt", id);
        eventPublisher.publiziere(new AccountBlockedEvent(
                konto.getNutzerId().wert().toString(), konto.getEmail().adresse()));
        return konto;
    }

    /**
     * Reaktiviert ein gesperrtes Konto (Status ACTIVE). Ein gelöschtes Konto bleibt unverändert (No-Op).
     *
     * @param id die NutzerID
     * @return der (ggf. reaktivierte) NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     */
    @Transactional
    public NutzerAccount reaktiviereKonto(NutzerID id) {
        NutzerAccount konto = ladeKonto(id);
        // Nur ein gesperrtes Konto kann reaktiviert werden. Aktive bleiben aktiv, gelöschte bleiben
        // gelöscht (No-Op) — Soft-Delete ist endgültig. Symmetrisch zum Vorbedingungs-Guard in sperreKonto.
        if (konto.getStatus() != KontoStatus.BLOCKED) {
            LOGGER.warn("reaktiviereKonto() — Konto {} ist nicht gesperrt (Status {}), keine Aktion",
                    id, konto.getStatus());
            return konto;
        }
        konto.reaktivieren();
        LOGGER.info("reaktiviereKonto() — Konto {} reaktiviert", id);
        return konto;
    }

    /**
     * Schaltet ein ausstehendes Konto frei (Admin-Genehmigung der Registrierung): PENDING → ACTIVE.
     * Bildet den Erfolgspfad der Registrierungs-Saga administrativ ab — nützlich, wenn (z. B. im
     * Systemtest oder bei Ausfall des EventManagers) kein {@code user.provisioned}-Event eintrifft.
     * Nutzt dieselbe Domain-Transition wie der Saga-Handler {@code aktiviereKonto}.
     *
     * <p>Die Rolle bleibt bewusst <b>PROSPECT</b> — ein freigeschaltetes Konto ist ein aktiver
     * Interessent, aber noch kein Kunde. CUSTOMER kommt ausschließlich über die Kauf-Choreographie
     * ({@code order.confirmed}); ein Admin kann die Rolle bei Bedarf manuell per {@link #weiseRolleZu}
     * setzen.</p>
     *
     * <p>Nur ein ausstehendes Konto wird freigeschaltet; jeder andere Status bleibt unverändert
     * (No-Op), symmetrisch zu den Vorbedingungs-Guards in {@link #sperreKonto}/{@link #reaktiviereKonto}.</p>
     *
     * @param id die NutzerID
     * @return der (ggf. freigeschaltete) NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     */
    @Transactional
    public NutzerAccount schalteKontoFrei(NutzerID id) {
        NutzerAccount konto = ladeKonto(id);
        // Nur ein ausstehendes Konto wird freigeschaltet. Bereits aktive, gesperrte, gelöschte oder
        // abgelehnte Konten bleiben unverändert (No-Op) — kein erneutes Umschalten terminaler Zustände.
        if (konto.getStatus() != KontoStatus.PENDING) {
            LOGGER.warn("schalteKontoFrei() — Konto {} ist nicht ausstehend (Status {}), keine Aktion",
                    id, konto.getStatus());
            return konto;
        }
        // Semantic Lock freigeben (PENDING → ACTIVE); Rolle bleibt PROSPECT
        konto.bestaetigeRegistrierung();
        LOGGER.info("schalteKontoFrei() — Konto {} freigeschaltet (ACTIVE)", id);
        return konto;
    }

    /**
     * Weist einem Konto eine zusätzliche Rolle zu (idempotent über das Rollen-Set).
     *
     * @param id    die NutzerID
     * @param rolle die zuzuweisende Rolle
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     */
    @Transactional
    public NutzerAccount weiseRolleZu(NutzerID id, Nutzerrolle rolle) {
        NutzerAccount konto = ladeKonto(id);
        konto.rolleZuweisen(rolle);
        LOGGER.info("weiseRolleZu() — Rolle {} dem Konto {} zugewiesen", rolle, id);
        return konto;
    }

    /**
     * Entzieht einem Konto eine Rolle (No-Op, falls die Rolle nicht vorhanden ist).
     *
     * <p>Schutz vor Selbstaussperrung: Dem <b>letzten aktiven</b> Administrator kann die Rolle
     * ADMINISTRATOR nicht entzogen werden — sonst hätte niemand mehr Zugriff auf {@code /rest/admin/**}.</p>
     *
     * @param id    die NutzerID
     * @param rolle die zu entziehende Rolle
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     * @throws de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException
     *         wenn dem letzten aktiven Administrator ADMINISTRATOR entzogen würde
     */
    @Transactional
    public NutzerAccount entzieheRolle(NutzerID id, Nutzerrolle rolle) {
        NutzerAccount konto = ladeKonto(id);
        // Nur das Entziehen von ADMINISTRATOR kann zur Selbstaussperrung führen
        if (rolle == Nutzerrolle.ADMINISTRATOR) {
            administratorRichtlinie.stelleSicherNichtLetzterAdministrator(konto);
        }
        konto.rolleEntziehen(rolle);
        LOGGER.info("entzieheRolle() — Rolle {} dem Konto {} entzogen", rolle, id);
        return konto;
    }

    /**
     * Ändert die E-Mail-Adresse eines fremden Kontos (Admin-Pflege). Anders als beim Self-Service
     * ist <b>kein</b> Passwort nötig — der Administrator handelt kraft seiner Rolle. Die neue Adresse
     * muss eindeutig sein.
     *
     * @param id        die NutzerID des Zielkontos
     * @param neueEmail die gewünschte neue E-Mail (wird validiert)
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException  wenn kein Konto mit dieser ID existiert
     * @throws EmailBereitsVergebenException wenn die neue E-Mail bereits einem anderen Konto gehört
     * @throws IllegalArgumentException      wenn die neue E-Mail ein ungültiges Format hat
     */
    @Transactional
    public NutzerAccount aendereEmail(NutzerID id, String neueEmail) {
        NutzerAccount konto = ladeKonto(id);
        EmailAdresse neueEmailAdresse = new EmailAdresse(neueEmail);
        // Eindeutigkeit prüfen — nur ablehnen, wenn die E-Mail einem ANDEREN Konto gehört
        Optional<NutzerAccount> vorhanden = nutzerRepository.findByEmail(neueEmailAdresse);
        if (vorhanden.isPresent() && !vorhanden.get().equals(konto)) {
            LOGGER.warn("aendereEmail() — neue E-Mail bereits vergeben, Änderung abgelehnt");
            throw new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert");
        }
        konto.emailAendern(neueEmailAdresse);
        LOGGER.info("aendereEmail() — E-Mail des Kontos {} geändert", id);
        return konto;
    }

    /**
     * Setzt das Passwort eines fremden Kontos zurück (Admin-Pflege). Anders als beim Self-Service ist
     * <b>kein</b> altes Passwort nötig — der Administrator handelt kraft seiner Rolle. Die Mindestlänge
     * des neuen Passworts prüft {@link PasswortHash#vonKlartext}.
     *
     * @param id            die NutzerID des Zielkontos
     * @param neuesPasswort das neue Klartext-Passwort (mind. 10 Zeichen)
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     * @throws IllegalArgumentException     wenn das neue Passwort zu kurz ist
     */
    @Transactional
    public NutzerAccount setzePasswortZurueck(NutzerID id, String neuesPasswort) {
        NutzerAccount konto = ladeKonto(id);
        // vonKlartext validiert die Mindestlänge (→ IllegalArgumentException → 400) und hasht
        konto.passwortAendern(PasswortHash.vonKlartext(neuesPasswort, passwordEncoder));
        LOGGER.info("setzePasswortZurueck() — Passwort des Kontos {} zurückgesetzt", id);
        return konto;
    }

    /**
     * Entfernt ein Konto physisch aus der Datenbank (Hard-Delete, DSGVO-Purge). Anders als der
     * fachliche Soft-Delete bleibt kein Audit-Trail. Danach wird ein {@link AccountDeletedEvent}
     * veröffentlicht, damit nachgelagerte Services zugehörige Daten aufräumen.
     *
     * @param id die NutzerID
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     * @throws de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException
     *         wenn das Konto der letzte aktive Administrator ist
     */
    @Transactional
    public void entferneKontoEndgueltig(NutzerID id) {
        NutzerAccount konto = ladeKonto(id);
        // Auch der Hard-Delete darf den letzten aktiven Administrator nicht beseitigen
        administratorRichtlinie.stelleSicherNichtLetzterAdministrator(konto);
        // ID und E-Mail vor dem physischen Löschen sichern — danach ist die Entity entfernt
        String nutzerIdWert = konto.getNutzerId().wert().toString();
        String email = konto.getEmail().adresse();
        nutzerRepository.loescheEndgueltig(konto);
        LOGGER.info("entferneKontoEndgueltig() — Konto {} physisch gelöscht (DSGVO-Purge)", id);
        eventPublisher.publiziere(new AccountDeletedEvent(nutzerIdWert, email));
    }

    /**
     * Lädt ein Nutzerkonto anhand seiner ID — wirft eine Exception, wenn es nicht existiert.
     * Zentralisiert das in mehreren Methoden benötigte orElseThrow-Muster.
     *
     * @param id die NutzerID
     * @return der gefundene NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser ID existiert
     */
    private NutzerAccount ladeKonto(NutzerID id) {
        return nutzerRepository.findByNutzerId(id)
                .orElseThrow(() -> new NutzerNichtGefundenException("Kein Nutzer mit dieser ID gefunden"));
    }
}
