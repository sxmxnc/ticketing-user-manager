package de.fhms.sweng.usermanagement.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Erweitertes Kundenprofil eines NutzerAccounts (0..1-Beziehung).
 * Enthält Rechnungsadresse und Interessengebiete (Sparten).
 * Wird erst angelegt, wenn der Nutzer sein Profil zum ersten Mal pflegt (PUT /rest/users/me) —
 * unabhängig von Status (Saga) und Rolle (Kauf).
 *
 * <p>JPA-Hinweis: {@code profilId} ist ein app-vergebener UUID-Primärschlüssel — kein
 * Surrogatschlüssel nötig, weil {@code profilId} ein einfaches Feld (kein Value Object)
 * ist und JPA UUID direkt als @Id unterstützt. {@link Adresse} wird via {@code @Embedded}
 * direkt in diese Tabelle eingebettet.</p>
 */
@Entity
@Table(name = "kundenprofil")
public class Kundenprofil {

    /**
     * Primärschlüssel — app-vergeben (UUID.randomUUID() im Konstruktor).
     * Kein @GeneratedValue nötig: UUID wird vor dem Persist im Konstruktor gesetzt.
     */
    @Id
    @Column(name = "profil_id")
    private UUID profilId;

    /**
     * Rechnungsadresse — direkt in die kundenprofil-Tabelle eingebettet (@Embeddable-Record).
     * Keine eigene Tabelle, keine eigene ID — die Spalten erscheinen direkt in der Profil-Zeile.
     */
    @Embedded
    private Adresse rechnungsAdresse;

    /**
     * Veranstaltungssparten, für die der Kunde Interesse bekundet hat.
     * @ElementCollection: Sparten existieren nicht eigenständig — nur als Teil des Profils.
     * Eigene Tabelle kundenprofil_interessen mit FK profil_id auf kundenprofil.profil_id.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kundenprofil_interessen", joinColumns = @JoinColumn(name = "profil_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sparte")
    private Set<Sparte> interessen;

    /**
     * Versionszähler für Optimistic Locking.
     * JPA erhöht diesen Wert automatisch bei jedem Update — nie selbst ändern!
     */
    @Version
    private long version;

    /**
     * Erstellt ein neues Kundenprofil mit Adresse und Interessen.
     *
     * @param rechnungsAdresse die Rechnungsadresse des Kunden
     * @param interessen       die Interessengebiete des Kunden (darf null sein)
     * @throws NullPointerException wenn rechnungsAdresse null ist
     */
    public Kundenprofil(Adresse rechnungsAdresse, Set<Sparte> interessen) {
        // Rechnungsadresse ist Pflicht-Invariante: fehlt sie, ist das ein Programmierfehler
        // im aufrufenden Service (nicht ungültige Nutzereingabe) — daher NullPointerException.
        Objects.requireNonNull(rechnungsAdresse, "Rechnungsadresse darf nicht null sein");
        this.profilId = UUID.randomUUID();
        this.rechnungsAdresse = rechnungsAdresse;
        this.interessen = kopiereInteressen(interessen);
    }

    /**
     * Standardkonstruktor für JPA — JPA braucht einen No-Args-Konstruktor,
     * um Entities per Reflection aus der Datenbank zu rekonstruieren.
     * Protected: Anwendungscode soll immer den parametrisierten Konstruktor nutzen.
     */
    protected Kundenprofil() {
    }

    /**
     * Aktualisiert Rechnungsadresse und Interessen des Kundenprofils.
     *
     * @param neueAdresse    die neue Rechnungsadresse
     * @param neueInteressen die neuen Interessengebiete
     * @throws NullPointerException wenn neueAdresse null ist
     */
    public void aktualisieren(Adresse neueAdresse, Set<Sparte> neueInteressen) {
        Objects.requireNonNull(neueAdresse, "Rechnungsadresse darf nicht null sein");
        this.rechnungsAdresse = neueAdresse;
        this.interessen = kopiereInteressen(neueInteressen);
    }

    /**
     * Erstellt einen defensiven Copy der übergebenen Interessen (eine eigene Instanz).
     * So beeinflussen spätere externe Änderungen am übergebenen Set den internen
     * Zustand des Profils nicht. Null oder leeres Set ergibt ein leeres Set.
     * Hinweis: {@code EnumSet.copyOf} wirft bei einer leeren Nicht-EnumSet-Collection
     * eine IllegalArgumentException — deshalb die vorgeschaltete isEmpty()-Prüfung.
     *
     * @param quelle die zu kopierenden Interessen (darf null oder leer sein)
     * @return neues, eigenständiges Set der Sparten (niemals null)
     */
    private static Set<Sparte> kopiereInteressen(Set<Sparte> quelle) {
        if (quelle != null && !quelle.isEmpty()) {
            return EnumSet.copyOf(quelle);
        } else {
            return EnumSet.noneOf(Sparte.class);
        }
    }

    /**
     * @return die Profil-ID
     */
    public UUID getProfilId() {
        return profilId;
    }

    /**
     * @return die Rechnungsadresse
     */
    public Adresse getRechnungsAdresse() {
        return rechnungsAdresse;
    }

    /**
     * Gibt eine unveränderliche Sicht (View) auf die Interessen zurück — kein Copy.
     * Der Aufrufer kann lesen, aber nicht schreiben; ein Schreibversuch wirft
     * UnsupportedOperationException. Defensiv kopiert wird stattdessen beim Reinnehmen
     * (Konstruktor / aktualisieren), siehe {@link #kopiereInteressen(Set)}.
     *
     * @return unveränderliches Set der Sparten (niemals null)
     */
    public Set<Sparte> getInteressen() {
        // unmodifiableSet liefert eine View, keinen Copy: lesen ja, schreiben nein
        return Collections.unmodifiableSet(interessen);
    }

    /**
     * @return der aktuelle Versionszähler (für Optimistic Locking)
     */
    public long getVersion() {
        return version;
    }
}
