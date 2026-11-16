package de.fhms.sweng.usermanagement.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate Root des UserManager-Domänenmodells.
 * Kapselt Identität (inkl. Vor-/Nachname), Authentifizierungsdaten, Kontostatus und Rollen eines Nutzers.
 * Alle Zustandsänderungen (sperren, löschen, Rolle zuweisen) laufen ausschließlich
 * über die Methoden dieser Klasse — nie direkt von außen auf Felder zugreifen.
 *
 * <p>JPA-Hinweis: {@code id} ist ein technischer Surrogatschlüssel (auto-generiert).
 * {@code nutzerId} ist der fachliche Schlüssel und die Basis für {@code equals}/{@code hashCode}.
 * Die Konverter für Value Objects ({@link NutzerID}, {@link EmailAdresse}, {@link PasswortHash})
 * liegen in {@code outbound/persistence/converter} und werden via {@code autoApply=true} automatisch
 * angewendet — die Domain-Klasse selbst importiert kein JPA außer den Mapping-Annotationen.</p>
 */
@Entity
@Table(name = "nutzer")
public class NutzerAccount {

    /**
     * Technischer Primärschlüssel — von der Datenbank generiert (IDENTITY-Strategie).
     * Wird nicht im Domänencode verwendet; fachliche Identität ist {@link #nutzerId}.
     * Surrogat-@Id gewählt, weil JPA AttributeConverter auf @Id-Felder nicht erlaubt.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fachlicher Schlüssel — eindeutig in der Tabelle, Basis für equals/hashCode.
     * Mapping: NutzerIDConverter (autoApply=true) wandelt NutzerID ↔ String-Spalte.
     */
    @Column(name = "nutzer_id", unique = true, nullable = false)
    private NutzerID nutzerId;

    /**
     * Validierte E-Mail-Adresse — dient auch als Login-Name.
     * Mapping: EmailAdresseConverter (autoApply=true) wandelt EmailAdresse ↔ String-Spalte.
     */
    @Column(name = "email", unique = true, nullable = false)
    private EmailAdresse email;

    /**
     * Vor- und Nachname des Nutzers — Pflichtangabe bei der Registrierung.
     * Mapping: {@code @Embedded} bettet die Felder des Value Objects {@link PersonenName} direkt
     * als Spalten (vorname, nachname) in die nutzer-Tabelle ein — keine eigene Tabelle, kein Join.
     * Dient als Basis für den Anzeigenamen (UI, Support-Tickets).
     */
    @Embedded
    private PersonenName name;

    /**
     * BCrypt-gehashtes Passwort — kein Klartext wird jemals gespeichert.
     * Mapping: PasswortHashConverter (autoApply=true) wandelt PasswortHash ↔ String-Spalte.
     */
    @Column(name = "passwort_hash", nullable = false)
    private PasswortHash passwortHash;

    /**
     * Aktueller Kontostatus (PENDING, ACTIVE, BLOCKED, DELETED, REJECTED).
     * STRING statt ORDINAL: lesbar in der DB, robust bei Enum-Erweiterungen.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private KontoStatus status;

    /**
     * Rollen des Nutzers — in eigener Tabelle nutzer_rollen gespeichert.
     * @ElementCollection: Rollen gehören zum NutzerAccount und existieren nicht eigenständig.
     * EAGER: Rollen werden immer zusammen mit dem Account geladen (klein, immer benötigt).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutzer_rollen", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rolle")
    private Set<Nutzerrolle> rollen;

    /**
     * Optionales Kundenprofil (0..1). Bleibt zunächst null und wird erst angelegt, wenn der Nutzer
     * sein Profil zum ersten Mal pflegt (Rechnungsadresse + Interessen über {@code PUT /rest/users/me}).
     * Weder die Saga-Freigabe (PENDING → ACTIVE) noch die Kunden-Beförderung (→ Rolle CUSTOMER) erzeugt
     * das Profil. CASCADE ALL + orphanRemoval: Profil wird mit dem Account gelöscht.
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "kundenprofil_id")
    private Kundenprofil kundenprofil;

    /**
     * Versionszähler für Optimistic Locking.
     * JPA erhöht diesen Wert automatisch bei jedem Update — nie selbst ändern!
     * Bei konkurrierendem Schreibzugriff wirft JPA eine OptimisticLockException → Rollback.
     */
    @Version
    private long version;

    /**
     * Standardkonstruktor für JPA — JPA braucht einen No-Args-Konstruktor,
     * um Entities per Reflection aus der Datenbank zu rekonstruieren.
     * Protected: Anwendungscode soll immer die Factory-Methode {@link #registrieren} nutzen.
     */
    protected NutzerAccount() {
    }

    /**
     * Factory-Methode: legt ein neues Nutzerkonto an.
     * Initialer Status: PENDING (Semantic Lock). Initiale Rolle: PROSPECT (noch kein Kaufrecht).
     * Das Konto bleibt für schreibende Self-Service-Zugriffe gesperrt, bis die Registrierungs-Saga
     * es freigibt: Bei Erfolg (EventManager-Reply user.provisioned) → {@link #bestaetigeRegistrierung}
     * (ACTIVE; die Rolle bleibt PROSPECT), bei Fehler (user.provisioning.failed) →
     * {@link #registrierungAblehnen} (REJECTED, Kompensation). Die Rolle CUSTOMER wird erst durch einen
     * bestätigten Kauf (order.confirmed) vergeben.
     *
     * @param email        validierte E-Mail-Adresse des neuen Nutzers
     * @param passwortHash bereits gehashtes Passwort
     * @param name         Vor- und Nachname des Nutzers (Pflichtangabe)
     * @return neu angelegter NutzerAccount
     * @throws NullPointerException wenn email, passwortHash oder name null sind
     */
    public static NutzerAccount registrieren(EmailAdresse email, PasswortHash passwortHash, PersonenName name) {
        // Pflichtfelder als Invariante absichern: fehlt hier etwas, ist das ein Programmierfehler
        // im aufrufenden Service (nicht ungültige Nutzereingabe) — daher NullPointerException.
        Objects.requireNonNull(email, "E-Mail darf nicht null sein");
        Objects.requireNonNull(passwortHash, "PasswortHash darf nicht null sein");
        Objects.requireNonNull(name, "Name darf nicht null sein");

        NutzerAccount konto = new NutzerAccount();
        konto.nutzerId = NutzerID.neu();
        konto.email = email;
        konto.name = name;
        konto.passwortHash = passwortHash;
        // Semantic Lock: neues Konto startet als PENDING — die Saga gibt es erst frei (ACTIVE)
        // oder kompensiert es (REJECTED). Bis dahin sind schreibende Self-Service-Zugriffe gesperrt.
        konto.status = KontoStatus.PENDING;
        konto.rollen = EnumSet.of(Nutzerrolle.PROSPECT);
        // kundenprofil bleibt null (Default) — wird erst beim ersten Profil-Pflegen (PUT /me) angelegt
        return konto;
    }

    /**
     * Bestätigt die Registrierung nach erfolgreicher Saga (Freigabe des Semantic Locks):
     * PENDING → ACTIVE. Für jeden anderen Ausgangszustand ein No-Op — so kann ein verspätet oder
     * doppelt eintreffendes Erfolgs-Event ein bereits aktives Konto nicht erneut umschalten und ein
     * zwischenzeitlich gesperrtes/gelöschtes/abgelehntes Konto nicht wiederbeleben (idempotent).
     */
    public void bestaetigeRegistrierung() {
        // Nur ein noch ausstehendes Konto wird durch die Saga freigegeben.
        if (status == KontoStatus.PENDING) {
            this.status = KontoStatus.ACTIVE;
        }
    }

    /**
     * Kompensiert die Registrierung nach fehlgeschlagener Saga: PENDING → REJECTED.
     * Für jeden anderen Ausgangszustand ein No-Op — insbesondere wird ein bereits aktives Konto
     * (das Erfolgs-Event gewann das Rennen) nicht herabgestuft und ein terminaler Zustand nicht
     * verändert (idempotent, schützt vor out-of-order/doppelten Events).
     */
    public void registrierungAblehnen() {
        // Nur ein noch ausstehendes Konto wird kompensiert — kein Downgrade eines aktiven Kontos.
        if (status == KontoStatus.PENDING) {
            this.status = KontoStatus.REJECTED;
        }
    }

    /**
     * Sperrt das Konto (z. B. durch Administrator).
     * Bereits gelöschte Konten werden nicht erneut gesperrt.
     */
    public void sperren() {
        // Terminale Zustände werden nicht gesperrt (No-Op): gelöschte Konten (Soft-Delete ist
        // endgültig) und abgelehnte Konten (Saga-Kompensation ist endgültig).
        if (status == KontoStatus.DELETED || status == KontoStatus.REJECTED) {
            return;
        }
        this.status = KontoStatus.BLOCKED;
    }

    /**
     * Reaktiviert ein gesperrtes Konto.
     * Gelöschte Konten können nicht reaktiviert werden.
     */
    public void reaktivieren() {
        // Terminale Zustände können nicht reaktiviert werden (No-Op): gelöschte Konten (Soft-Delete
        // ist endgültig) und abgelehnte Konten (Saga-Kompensation ist endgültig).
        if (status == KontoStatus.DELETED || status == KontoStatus.REJECTED) {
            return;
        }
        this.status = KontoStatus.ACTIVE;
    }

    /**
     * Markiert das Konto als gelöscht (Soft-Delete).
     * Die Daten bleiben in der Datenbank erhalten (Audit-Trail).
     */
    public void loeschen() {
        this.status = KontoStatus.DELETED;
    }

    /**
     * Weist dem Konto eine zusätzliche Rolle zu.
     *
     * @param rolle die zuzuweisende Rolle
     * @throws NullPointerException wenn rolle null ist
     */
    public void rolleZuweisen(Nutzerrolle rolle) {
        Objects.requireNonNull(rolle, "Rolle darf nicht null sein");
        this.rollen.add(rolle);
    }

    /**
     * Entzieht dem Konto eine Rolle.
     *
     * @param rolle die zu entziehende Rolle
     * @throws NullPointerException wenn rolle null ist
     */
    public void rolleEntziehen(Nutzerrolle rolle) {
        Objects.requireNonNull(rolle, "Rolle darf nicht null sein");
        this.rollen.remove(rolle);
    }

    /**
     * Ändert die E-Mail-Adresse (zugleich Login-Name) des Kontos.
     * Die Validierung des Formats erledigt bereits das Value Object {@link EmailAdresse};
     * die Eindeutigkeit (keine doppelte E-Mail) prüft der aufrufende Service vorher.
     *
     * @param neueEmail die neue, bereits validierte E-Mail-Adresse
     * @throws NullPointerException wenn neueEmail null ist
     */
    public void emailAendern(EmailAdresse neueEmail) {
        Objects.requireNonNull(neueEmail, "E-Mail darf nicht null sein");
        this.email = neueEmail;
    }

    /**
     * Ändert den Namen (Vor- und Nachname) des Kontos.
     * Die Validierung (nicht leer) erledigt bereits das Value Object {@link PersonenName};
     * hier kommt nur ein fertiges, gültiges Objekt an.
     *
     * @param neuerName der neue, bereits validierte Name
     * @throws NullPointerException wenn neuerName null ist
     */
    public void nameAendern(PersonenName neuerName) {
        Objects.requireNonNull(neuerName, "Name darf nicht null sein");
        this.name = neuerName;
    }

    /**
     * Ändert das gehashte Passwort des Kontos.
     * Die Mindestlänge-Prüfung des Klartexts erledigt bereits die Factory
     * {@link PasswortHash#vonKlartext}; hier kommt nur der fertige Hash an.
     *
     * @param neuerHash der neue, bereits gehashte Passwort-Wert
     * @throws NullPointerException wenn neuerHash null ist
     */
    public void passwortAendern(PasswortHash neuerHash) {
        Objects.requireNonNull(neuerHash, "PasswortHash darf nicht null sein");
        this.passwortHash = neuerHash;
    }

    /**
     * Zwei NutzerAccounts sind gleich, wenn sie dieselbe NutzerID haben —
     * unabhängig von anderen Feldern (Entity-Gleichheit über fachliche Identität).
     * Der Surrogatschlüssel {@code id} wird bewusst nicht verwendet, da er vor
     * dem ersten Persist noch null ist.
     *
     * @param o das zu vergleichende Objekt
     * @return true wenn die NutzerIDs übereinstimmen
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NutzerAccount other)) return false;
        return Objects.equals(nutzerId, other.nutzerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nutzerId);
    }

    // --- Getter ---

    /**
     * @return der technische Surrogatschlüssel (kann vor dem ersten persist null sein)
     */
    public Long getId() {
        return id;
    }

    /**
     * @return die eindeutige fachliche NutzerID
     */
    public NutzerID getNutzerId() {
        return nutzerId;
    }

    /**
     * @return die E-Mail-Adresse
     */
    public EmailAdresse getEmail() {
        return email;
    }

    /**
     * @return der Name (Vor- und Nachname) des Nutzers
     */
    public PersonenName getName() {
        return name;
    }

    /**
     * @return das gehashte Passwort
     */
    public PasswortHash getPasswortHash() {
        return passwortHash;
    }

    /**
     * @return der aktuelle Kontostatus
     */
    public KontoStatus getStatus() {
        return status;
    }

    /**
     * Gibt eine unveränderliche Sicht (View) auf die Rollen zurück — kein Copy.
     * Der Aufrufer kann lesen, aber nicht schreiben; ein Schreibversuch wirft
     * UnsupportedOperationException und schützt so die interne Collection.
     *
     * @return unveränderliches Set der Rollen (niemals null)
     */
    public Set<Nutzerrolle> getRollen() {
        // unmodifiableSet liefert eine View, keinen Copy: lesen ja, schreiben nein
        return Collections.unmodifiableSet(rollen);
    }

    /**
     * @return das Kundenprofil, oder null wenn noch keines angelegt wurde
     */
    public Kundenprofil getKundenprofil() {
        return kundenprofil;
    }

    /**
     * Setzt das Kundenprofil (wird beim ersten Pflegen des eigenen Profils über
     * {@code PUT /rest/users/me} vom NutzerService aufgerufen).
     *
     * @param kundenprofil das neu angelegte Kundenprofil
     */
    public void setKundenprofil(Kundenprofil kundenprofil) {
        this.kundenprofil = kundenprofil;
    }

    /**
     * @return der aktuelle Versionszähler (für Optimistic Locking / Client-seitige Konflikterkennung)
     */
    public long getVersion() {
        return version;
    }
}
