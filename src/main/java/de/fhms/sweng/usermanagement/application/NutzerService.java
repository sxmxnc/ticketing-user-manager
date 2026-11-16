package de.fhms.sweng.usermanagement.application;

import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import de.fhms.sweng.usermanagement.domain.event.AccountDeletedEvent;
import de.fhms.sweng.usermanagement.domain.event.CustomerRegisteredEvent;
import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.FalschesPasswortException;
import de.fhms.sweng.usermanagement.domain.exception.KontoNichtAktivException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
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
import de.fhms.sweng.usermanagement.domain.service.AdministratorRichtlinie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Application Service (Use-Case-Schicht) für die Nutzer-Verwaltung.
 *
 * <p>Orchestriert die fachlichen Abläufe rund um das <b>eigene</b> Konto eines Nutzers
 * (Registrierung, Profil lesen/aktualisieren, E-Mail/Passwort ändern, Löschen) sowie die
 * Saga-Handler. Die Self-Service-Methoden arbeiten bewusst <b>über die E-Mail aus dem Token</b>
 * (nicht über eine ID aus dem Pfad): So kann ein Nutzer strukturell nur sein eigenes Konto
 * bearbeiten — fremde Konten verwaltet ausschließlich der {@link AdminService}.</p>
 *
 * <p>Dependency Injection per Konstruktor: final-Felder, ein Konstruktor, daher kein
 * {@code @Autowired} nötig. Schreibende Methoden sind {@code @Transactional}, rein lesende
 * {@code @Transactional(readOnly = true)}. {@code @Retryable} auf Klassenebene macht die
 * Schreiboperationen gegen Versionskonflikte (Optimistic Locking) resilient.</p>
 *
 * <p>Validierung: Die rohen Strings aus dem Controller werden hier in Value Objects
 * ({@link EmailAdresse}, {@link PasswortHash}) umgewandelt — deren Konstruktoren validieren
 * und werfen bei Ungültigkeit eine {@code IllegalArgumentException}, die der ExceptionHandler
 * auf HTTP 400 abbildet.</p>
 *
 * <p>Messaging: Nach jeder zustandsändernden Operation wird ein Domain-Event über den Port
 * {@link EventPublisher} veröffentlicht. Das Event wird innerhalb derselben {@code @Transactional}
 * Methode publiziert — schlägt der Versand fehl, wird die gesamte Transaktion zurückgerollt
 * (kein Konto ohne zugehöriges Event).</p>
 */
@Service
// Optimistic Locking: Bei konkurrierendem Schreiben wirft JPA beim Commit eine OptimisticLockException,
// die Spring in eine OptimisticLockingFailureException übersetzt. @Retryable wiederholt die Operation
// dann automatisch (erneutes Lesen mit frischer Version), bevor auf dem REST-Pfad der 409-Fallback greift.
// Der Retry-Advisor liegt per Default außerhalb der Transaktion und umschließt den Commit damit korrekt.
@Retryable(includes = OptimisticLockingFailureException.class,
        maxRetries = 3, delay = 100, jitter = 10, multiplier = 2)
public class NutzerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NutzerService.class);

    private final NutzerRepository nutzerRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    private final AdministratorRichtlinie administratorRichtlinie;

    /**
     * Konstruktor-Injektion der benötigten Beans.
     *
     * @param nutzerRepository        der Persistenz-Port
     * @param passwordEncoder         der BCrypt-Encoder (Bean aus SecurityConfig) zum Hashen/Prüfen
     * @param eventPublisher          der Port zum Veröffentlichen von Domain-Events (RabbitMQ)
     * @param administratorRichtlinie der Domain-Service für die „mindestens ein aktiver Admin"-Regel
     */
    public NutzerService(NutzerRepository nutzerRepository, PasswordEncoder passwordEncoder,
                         EventPublisher eventPublisher, AdministratorRichtlinie administratorRichtlinie) {
        this.nutzerRepository = nutzerRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.administratorRichtlinie = administratorRichtlinie;
    }

    /**
     * Registriert einen neuen Nutzer.
     *
     * @param email            die E-Mail-Adresse (wird zu {@link EmailAdresse} validiert)
     * @param klartextPasswort das Klartext-Passwort (wird sofort gehasht, nie gespeichert)
     * @param vorname          der Vorname (Pflichtangabe, wird zu {@link PersonenName} validiert)
     * @param nachname         der Nachname (Pflichtangabe, wird zu {@link PersonenName} validiert)
     * @return der gespeicherte NutzerAccount
     * @throws IllegalArgumentException      bei ungültiger E-Mail, zu kurzem Passwort oder leerem Namen
     * @throws EmailBereitsVergebenException wenn die E-Mail bereits registriert ist
     */
    @Transactional
    public NutzerAccount registriere(String email, String klartextPasswort, String vorname, String nachname) {
        // Erst validieren/prüfen, dann loggen — sonst meldet das Log einen Erfolg, obwohl die Methode
        // gleich mit einer Exception abbricht. E-Mail und Name werden zuerst gebaut: ihre Konstruktoren
        // validieren die Nutzereingabe (→ IllegalArgumentException → 400), noch bevor wir die DB anfassen.
        EmailAdresse emailAdresse = new EmailAdresse(email);
        PersonenName name = new PersonenName(vorname, nachname);

        // Eindeutigkeit der E-Mail prüfen, bevor wir hashen und speichern
        if (nutzerRepository.findByEmail(emailAdresse).isPresent()) {
            LOGGER.warn("registriere() — E-Mail bereits vergeben, Registrierung abgelehnt");
            throw new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert");
        }

        PasswortHash passwortHash = PasswortHash.vonKlartext(klartextPasswort, passwordEncoder);
        NutzerAccount konto = NutzerAccount.registrieren(emailAdresse, passwortHash, name);
        NutzerAccount gespeichert = nutzerRepository.save(konto);
        LOGGER.info("registriere() — neues Konto wurde angelegt");
        // Event innerhalb derselben Transaktion publizieren — schlägt der Versand fehl,
        // wird auch das save() zurückgerollt (kein Konto ohne zugehöriges Event)
        eventPublisher.publiziere(new CustomerRegisteredEvent(
                gespeichert.getNutzerId().wert().toString(), gespeichert.getEmail().adresse()));
        return gespeichert;
    }

    /**
     * Lädt das eigene Profil anhand der E-Mail des angemeldeten Nutzers (aus dem Token).
     *
     * @param email die E-Mail des angemeldeten Nutzers
     * @return der gefundene NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser E-Mail existiert
     */
    @Transactional(readOnly = true)
    public NutzerAccount findeEigenes(String email) {
        // Erst die Existenz prüfen (orElseThrow), dann den Erfolg loggen
        NutzerAccount konto = ladeKontoPerEmail(email);
        LOGGER.info("findeEigenes() — eigenes Konto geladen");
        return konto;
    }

    /**
     * Aktualisiert das eigene Kundenprofil (Rechnungsadresse + Interessen). Existiert noch kein
     * Kundenprofil, wird eines angelegt; andernfalls wird das vorhandene aktualisiert.
     *
     * @param email      die E-Mail des angemeldeten Nutzers
     * @param adresse    die neue Rechnungsadresse
     * @param interessen die neuen Interessengebiete (Sparten)
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser E-Mail existiert
     */
    @Transactional
    public NutzerAccount aktualisiereEigenesProfil(String email, Adresse adresse, Set<Sparte> interessen) {
        // Erst die Existenz prüfen, dann ändern und den Erfolg loggen
        NutzerAccount konto = ladeKontoPerEmail(email);
        stelleSicherKontoAktiv(konto);
        Kundenprofil profil = konto.getKundenprofil();
        if (profil == null) {
            // Noch kein Profil → neues anlegen; die Cascade speichert es beim Commit mit
            konto.setKundenprofil(new Kundenprofil(adresse, interessen));
        } else {
            // Vorhandenes Profil ist eine managed Entity — JPA synchronisiert die Änderung
            // beim Commit automatisch, ein expliziter save()-Aufruf ist daher nicht nötig
            profil.aktualisieren(adresse, interessen);
        }
        LOGGER.info("aktualisiereEigenesProfil() — eigenes Profil aktualisiert");
        return konto;
    }

    /**
     * Ändert die eigene E-Mail-Adresse (zugleich Login-Name). Sensible Operation: das aktuelle
     * Passwort muss zur Bestätigung mitgeschickt werden.
     *
     * <p>Hinweis: Da die E-Mail das Token-Subject ist, wird ein bereits ausgestelltes JWT nach der
     * Änderung „stale" — der Client muss sich mit der neuen E-Mail neu anmelden.</p>
     *
     * @param aktuelleEmail     die bisherige E-Mail des angemeldeten Nutzers (aus dem Token)
     * @param neueEmail         die gewünschte neue E-Mail (wird validiert)
     * @param aktuellesPasswort das aktuelle Klartext-Passwort zur Bestätigung
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException  wenn kein Konto mit dieser E-Mail existiert
     * @throws FalschesPasswortException     wenn das aktuelle Passwort nicht stimmt
     * @throws EmailBereitsVergebenException wenn die neue E-Mail bereits vergeben ist
     * @throws IllegalArgumentException      wenn die neue E-Mail ein ungültiges Format hat
     */
    @Transactional
    public NutzerAccount aendereEigeneEmail(String aktuelleEmail, String neueEmail, String aktuellesPasswort) {
        NutzerAccount konto = ladeKontoPerEmail(aktuelleEmail);
        stelleSicherKontoAktiv(konto);
        // Sensible Operation: aktuelles Passwort bestätigen, bevor die Login-Identität geändert wird
        if (!konto.getPasswortHash().stimmt(aktuellesPasswort, passwordEncoder)) {
            LOGGER.warn("aendereEigeneEmail() — aktuelles Passwort falsch, Änderung abgelehnt");
            throw new FalschesPasswortException("Aktuelles Passwort ist falsch");
        }
        EmailAdresse neueEmailAdresse = new EmailAdresse(neueEmail);
        // Eindeutigkeit prüfen — nur ablehnen, wenn die E-Mail einem ANDEREN Konto gehört
        // (die unveränderte eigene E-Mail erneut zu setzen ist erlaubt, ein No-Op)
        Optional<NutzerAccount> vorhanden = nutzerRepository.findByEmail(neueEmailAdresse);
        if (vorhanden.isPresent() && !vorhanden.get().equals(konto)) {
            LOGGER.warn("aendereEigeneEmail() — neue E-Mail bereits vergeben, Änderung abgelehnt");
            throw new EmailBereitsVergebenException("Diese E-Mail-Adresse ist bereits registriert");
        }
        konto.emailAendern(neueEmailAdresse);
        LOGGER.info("aendereEigeneEmail() — eigene E-Mail geändert");
        return konto;
    }

    /**
     * Ändert das eigene Passwort. Das alte Passwort muss zur Bestätigung stimmen; das neue Passwort
     * wird über {@link PasswortHash#vonKlartext} auf die Mindestlänge geprüft und gehasht.
     *
     * @param email         die E-Mail des angemeldeten Nutzers (aus dem Token)
     * @param altesPasswort das aktuelle Klartext-Passwort zur Bestätigung
     * @param neuesPasswort das neue Klartext-Passwort (mind. 10 Zeichen)
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser E-Mail existiert
     * @throws FalschesPasswortException    wenn das alte Passwort nicht stimmt
     * @throws IllegalArgumentException     wenn das neue Passwort zu kurz ist
     */
    @Transactional
    public NutzerAccount aendereEigenesPasswort(String email, String altesPasswort, String neuesPasswort) {
        NutzerAccount konto = ladeKontoPerEmail(email);
        stelleSicherKontoAktiv(konto);
        if (!konto.getPasswortHash().stimmt(altesPasswort, passwordEncoder)) {
            LOGGER.warn("aendereEigenesPasswort() — altes Passwort falsch, Änderung abgelehnt");
            throw new FalschesPasswortException("Aktuelles Passwort ist falsch");
        }
        // vonKlartext validiert die Mindestlänge des neuen Passworts (→ IllegalArgumentException → 400)
        konto.passwortAendern(PasswortHash.vonKlartext(neuesPasswort, passwordEncoder));
        LOGGER.info("aendereEigenesPasswort() — eigenes Passwort geändert");
        return konto;
    }

    /**
     * Ändert den eigenen Namen (Vor- und Nachname). Anders als E-Mail- oder Passwortänderung ist der
     * Name nicht sicherheitskritisch — es ist daher kein Passwort zur Bestätigung nötig. Wie bei jeder
     * schreibenden Self-Service-Operation muss das Konto aktiv sein.
     *
     * @param email    die E-Mail des angemeldeten Nutzers (aus dem Token)
     * @param vorname  der neue Vorname
     * @param nachname der neue Nachname
     * @return der aktualisierte NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser E-Mail existiert
     * @throws KontoNichtAktivException     wenn das Konto gesperrt oder gelöscht ist
     * @throws IllegalArgumentException     wenn Vor- oder Nachname leer ist
     */
    @Transactional
    public NutzerAccount aendereEigenenNamen(String email, String vorname, String nachname) {
        NutzerAccount konto = ladeKontoPerEmail(email);
        stelleSicherKontoAktiv(konto);
        // managed Entity: nameAendern() setzt das Feld, JPA schreibt die Änderung beim Commit automatisch.
        // new PersonenName(...) validiert die Eingabe (→ IllegalArgumentException → 400).
        konto.nameAendern(new PersonenName(vorname, nachname));
        LOGGER.info("aendereEigenenNamen() — eigener Name geändert");
        return konto;
    }

    /**
     * Löscht das eigene Konto als Soft-Delete: der Status wird auf DELETED gesetzt,
     * die Zeile bleibt als Audit-Trail in der Datenbank erhalten.
     *
     * @param email die E-Mail des angemeldeten Nutzers (aus dem Token)
     * @throws NutzerNichtGefundenException  wenn kein Konto mit dieser E-Mail existiert
     * @throws de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException
     *         wenn das Konto der letzte aktive Administrator ist
     */
    @Transactional
    public void loescheEigenesKonto(String email) {
        // Erst die Existenz prüfen, dann löschen und den Erfolg loggen
        NutzerAccount konto = ladeKontoPerEmail(email);
        // Auch der Soft-Delete darf den letzten aktiven Administrator nicht beseitigen
        administratorRichtlinie.stelleSicherNichtLetzterAdministrator(konto);
        // managed Entity: loeschen() setzt den Status, JPA schreibt die Änderung beim Commit
        konto.loeschen();
        LOGGER.info("loescheEigenesKonto() — eigenes Konto als gelöscht markiert");
        eventPublisher.publiziere(new AccountDeletedEvent(
                konto.getNutzerId().wert().toString(), konto.getEmail().adresse()));
    }

    /**
     * Saga-Handler (Erfolg): gibt den Semantic Lock frei, nachdem der EventManager die Provisionierung
     * des neuen Nutzers bestätigt hat (Reaktion auf das {@code user.provisioned}-Event).
     *
     * <p>Wirkung: PENDING → ACTIVE (über {@link NutzerAccount#bestaetigeRegistrierung}). Die Rolle
     * bleibt bewusst <b>PROSPECT</b> — ein bestätigtes Konto ist ein freigeschalteter Interessent, aber
     * noch kein Kunde. Die Rolle CUSTOMER kommt ausschließlich über die Kauf-Choreographie
     * ({@code order.confirmed} → {@link #befoerdereZuKunde(NutzerID)}).</p>
     *
     * <p>Idempotent und out-of-order-fest: {@code bestaetigeRegistrierung} schaltet nur ein noch
     * ausstehendes Konto um — eine doppelte oder verspätete Bestätigung lässt ein bereits aktives Konto
     * unverändert und belebt ein terminales (gelöscht/abgelehnt) nicht wieder. Ein Event zu einer
     * unbekannten NutzerID wird ohne Fehler ignoriert.</p>
     *
     * @param id die fachliche NutzerID (aus dem {@code userId}-Feld des Reply-Events)
     */
    @Transactional
    public void aktiviereKonto(NutzerID id) {
        Optional<NutzerAccount> gefunden = nutzerRepository.findByNutzerId(id);
        if (gefunden.isEmpty()) {
            LOGGER.warn("aktiviereKonto() — kein Konto mit dieser NutzerID, Event wird ignoriert");
            return;
        }
        // Semantic Lock freigeben (PENDING → ACTIVE; Guard im Aggregat: jeder andere Status ein No-Op).
        // Die Rolle (PROSPECT) wird bewusst NICHT verändert — Kunde wird man erst durch einen Kauf.
        gefunden.get().bestaetigeRegistrierung();
        LOGGER.info("aktiviereKonto() — Registrierung bestätigt, Semantic Lock freigegeben (ACTIVE)");
    }

    /**
     * Befördert einen Nutzer zur Rolle CUSTOMER, nachdem er eine Bestellung erfolgreich bezahlt hat
     * (Reaktion auf das {@code order.confirmed}-Event von Order/Ticketing). Aus dem Interessenten
     * (PROSPECT) wird beim ersten bestätigten Kauf ein Kunde (CUSTOMER).
     *
     * <p>Wirkung: <b>Konversion</b> statt Anhäufung — PROSPECT wird entzogen und CUSTOMER zugewiesen.
     * Etwaige Sonderrollen (z. B. ADMINISTRATOR, EVENT_MANAGER) bleiben unangetastet. Idempotent: Beide
     * Mengen-Operationen sind ein No-Op, wenn das Konto bereits CUSTOMER (und nicht mehr PROSPECT) ist —
     * ein mehrfach zugestelltes Kauf-Event befördert genau einmal.</p>
     *
     * <p>Belang-Trennung (zwei orthogonale Achsen): Der Kauf ändert <b>nur die Rolle</b> (PROSPECT →
     * CUSTOMER) und fasst den <b>Status bewusst nicht an</b>. Die Freigabe des Semantic Locks
     * (PENDING → ACTIVE) bleibt allein Sache der Registrierungs-Saga (EventManager) bzw. des Admins —
     * ein kommerzielles Event hebt keinen Lifecycle-Lock auf. Ein PENDING-Käufer wird daher zu
     * PENDING + CUSTOMER; sein Status wird erst durch die Saga ACTIVE.</p>
     *
     * <p>Status-Guard: Nur terminale oder gesperrte Konten ({@code BLOCKED}, {@code DELETED},
     * {@code REJECTED}) werden nicht befördert — ein gelöschtes/abgelehntes Konto wird nicht
     * wiederbelebt, ein gesperrtes nicht heimlich aufgewertet. Ein Kauf-Event zu einer unbekannten
     * NutzerID wird ohne Fehler ignoriert.</p>
     *
     * @param id die fachliche NutzerID (aus dem {@code customerId}-Feld des Kauf-Events)
     */
    @Transactional
    public void befoerdereZuKunde(NutzerID id) {
        Optional<NutzerAccount> gefunden = nutzerRepository.findByNutzerId(id);
        if (gefunden.isEmpty()) {
            LOGGER.warn("befoerdereZuKunde() — kein Konto mit dieser NutzerID, Kauf-Event wird ignoriert");
            return;
        }
        NutzerAccount konto = gefunden.get();
        KontoStatus status = konto.getStatus();
        // Terminale/gesperrte Konten werden nicht befördert: ein gesperrtes (BLOCKED), gelöschtes
        // (DELETED) oder abgelehntes (REJECTED) Konto wird durch ein Kauf-Event nicht (wieder) zum Kunden.
        if (status == KontoStatus.BLOCKED || status == KontoStatus.DELETED || status == KontoStatus.REJECTED) {
            LOGGER.warn("befoerdereZuKunde() — Konto in terminalem/gesperrtem Status {}, keine Beförderung",
                    status);
            return;
        }
        // Konversion ausschließlich auf der Rollen-Achse: PROSPECT → CUSTOMER (Sonderrollen bleiben
        // erhalten). Der Status (Semantic Lock) wird bewusst NICHT angefasst. Idempotent: beide
        // Mengen-Operationen sind ein No-Op, wenn das Konto bereits CUSTOMER (und nicht mehr PROSPECT) ist.
        konto.rolleEntziehen(Nutzerrolle.PROSPECT);
        konto.rolleZuweisen(Nutzerrolle.CUSTOMER);
        LOGGER.info("befoerdereZuKunde() — Nutzer nach bestätigtem Kauf zur Rolle CUSTOMER befördert");
    }

    /**
     * Saga-Handler (Kompensation): lehnt die Registrierung ab, nachdem der EventManager die
     * Provisionierung endgültig (nach seinen Retries) als fehlgeschlagen gemeldet hat (Reaktion auf
     * das {@code user.provisioning.failed}-Event).
     *
     * <p>Wirkung: PENDING → REJECTED (über {@link NutzerAccount#registrierungAblehnen}). Die
     * Kompensation ist idempotent und stuft ein bereits aktives Konto nicht herab: Hat ein
     * Erfolgs-Event das Rennen gewonnen (out-of-order), bleibt das Konto ACTIVE. Ein bereits
     * abgelehntes Konto bleibt REJECTED (doppelte Zustellung). Ein Event zu einer unbekannten NutzerID
     * wird ohne Fehler ignoriert.</p>
     *
     * @param id    die fachliche NutzerID (aus dem {@code userId}-Feld des Reply-Events)
     * @param grund der Ablehnungsgrund aus dem {@code user.provisioning.failed}-Event
     */
    @Transactional
    public void lehneRegistrierungAb(NutzerID id, String grund) {
        // Existenz prüfen, damit ein verwaistes Event (unbekannte NutzerID) sauber geloggt wird
        Optional<NutzerAccount> gefunden = nutzerRepository.findByNutzerId(id);
        if (gefunden.isEmpty()) {
            LOGGER.warn("lehneRegistrierungAb() — kein Konto mit dieser NutzerID, Event wird ignoriert");
            return;
        }
        NutzerAccount konto = gefunden.get();
        KontoStatus vorher = konto.getStatus();
        // Kompensation als Domain-Transition (Guard im Aggregate): nur PENDING → REJECTED
        konto.registrierungAblehnen();
        if (vorher == KontoStatus.PENDING) {
            LOGGER.warn("lehneRegistrierungAb() — Registrierung kompensiert, Konto REJECTED. Grund: {}", grund);
        } else {
            LOGGER.warn("lehneRegistrierungAb() — Konto nicht ausstehend (Status {}), keine Kompensation. Grund: {}",
                    vorher, grund);
        }
    }

    /**
     * Lädt ein Nutzerkonto anhand seiner E-Mail — wirft eine Exception, wenn es nicht existiert.
     * Wandelt den rohen String in das {@link EmailAdresse}-Value-Object um (das dabei auf
     * Kleinbuchstaben normalisiert) und zentralisiert das orElseThrow-Muster für die Self-Service-
     * Methoden, die den Nutzer über die E-Mail aus dem Token auflösen.
     *
     * @param email die E-Mail-Adresse als String
     * @return der gefundene NutzerAccount
     * @throws NutzerNichtGefundenException wenn kein Konto mit dieser E-Mail existiert
     */
    private NutzerAccount ladeKontoPerEmail(String email) {
        return nutzerRepository.findByEmail(new EmailAdresse(email))
                .orElseThrow(() -> new NutzerNichtGefundenException("Kein Nutzer mit dieser E-Mail gefunden"));
    }

    /**
     * Stellt sicher, dass das Konto {@code ACTIVE} ist, bevor eine schreibende Self-Service-Operation
     * (Profil, E-Mail, Passwort) ausgeführt wird. Ein gesperrtes oder gelöschtes Konto darf nicht mehr
     * per Self-Service geändert werden — auch nicht mit einem noch gültigen, vor der Sperrung/Löschung
     * ausgestellten Token (Stateless-JWT-Zeitfenster). Das Löschen des eigenen Kontos bleibt bewusst
     * ohne diese Prüfung (Kontoschließung soll auch im Ausnahmezustand möglich sein).
     *
     * @param konto das zu prüfende Konto
     * @throws KontoNichtAktivException wenn das Konto nicht ACTIVE ist
     */
    private void stelleSicherKontoAktiv(NutzerAccount konto) {
        if (konto.getStatus() != KontoStatus.ACTIVE) {
            LOGGER.warn("stelleSicherKontoAktiv() — Konto nicht aktiv (Status {}), Self-Service abgelehnt",
                    konto.getStatus());
            throw new KontoNichtAktivException("Konto ist gesperrt oder gelöscht — keine Änderung möglich");
        }
    }
}
