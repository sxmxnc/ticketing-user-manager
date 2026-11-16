package de.fhms.sweng.usermanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value Object: der Name eines Nutzers (Vor- und Nachname).
 * Gehört zur Identität des Nutzers und wird daher direkt am Aggregate Root
 * {@link NutzerAccount} geführt — nicht am optionalen Kundenprofil, das bei einem
 * frisch registrierten (PENDING) Konto noch gar nicht existiert.
 *
 * <p>Die Validierung findet im kompakten Konstruktor statt — ein leerer oder fehlender
 * Name kann nie instanziiert werden (Invariante des Value Objects). Gleichheit basiert
 * auf Vor- und Nachname zusammen (Record-Semantik, automatisch).</p>
 *
 * <p>Normalisierung: führende/abschließende Leerzeichen werden entfernt (getrimmt), damit
 * der zusammengesetzte {@link #anzeigename()} sauber "Vorname Nachname" ergibt. Dieselbe Idee
 * wie bei {@link EmailAdresse} (Kleinschreibung): die Normalisierung passiert genau einmal an
 * der Quelle und gilt dann überall konsistent.</p>
 *
 * <p>JPA-Hinweis: {@code @Embeddable} bedeutet, dass JPA die Felder dieses Records direkt
 * in die Tabelle der besitzenden Entity ({@code nutzer}) einbettet — keine eigene Tabelle,
 * keine eigene ID. Die Spalten {@code vorname}/{@code nachname} sind {@code nullable = false}:
 * Der Name ist eine Pflichtangabe bei der Registrierung und damit für jedes Konto gesetzt.</p>
 */
@Embeddable
public record PersonenName(

        @Column(name = "vorname", nullable = false)
        String vorname,

        @Column(name = "nachname", nullable = false)
        String nachname

) {

    /**
     * Kompakter Konstruktor: Vor- und Nachname müssen gesetzt und nicht leer sein;
     * anschließend werden beide getrimmt.
     *
     * @param vorname  der Vorname
     * @param nachname der Nachname
     * @throws IllegalArgumentException wenn Vor- oder Nachname null oder leer ist
     */
    public PersonenName {
        // null und leer einheitlich als ungültige Eingabe behandeln (→ IllegalArgumentException → 400),
        // statt bei null eine NullPointerException zu werfen (die würde fälschlich 500 erzeugen).
        if (istLeer(vorname) || istLeer(nachname)) {
            throw new IllegalArgumentException("Vor- und Nachname dürfen nicht null oder leer sein");
        }
        // Randleerzeichen entfernen, damit anzeigename() ein sauberes "Vorname Nachname" ergibt.
        vorname = vorname.trim();
        nachname = nachname.trim();
    }

    /**
     * Prüft, ob ein Namensfeld fehlt — null oder nur aus Leerzeichen bestehend.
     *
     * @param wert das zu prüfende Feld
     * @return true wenn null oder leer
     */
    private static boolean istLeer(String wert) {
        return wert == null || wert.isBlank();
    }

    /**
     * Liefert den Anzeigenamen für UI und Support-Tickets: "Vorname Nachname".
     * Da Vor- und Nachname Pflichtfelder sind, ist das Ergebnis nie leer.
     *
     * @return der zusammengesetzte Anzeigename
     */
    public String anzeigename() {
        return vorname + " " + nachname;
    }
}
