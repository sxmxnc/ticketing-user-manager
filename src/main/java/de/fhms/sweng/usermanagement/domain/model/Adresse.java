package de.fhms.sweng.usermanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value Object: vollständige Postanschrift eines Nutzers.
 * Wird als Rechnungsadresse im Kundenprofil gespeichert.
 * Gleichheit basiert auf allen Feldern zusammen (Record-Semantik).
 *
 * <p>JPA-Hinweis: {@code @Embeddable} bedeutet, dass JPA die Felder dieses Records
 * direkt in die Tabelle der besitzenden Entity ({@link Kundenprofil}) einbettet — keine
 * eigene Tabelle, keine eigene ID. Hibernate 7 unterstützt {@code @Embeddable}-Records
 * mit kanonischem Konstruktor (kein No-Args-Konstruktor nötig).</p>
 */
@Embeddable
public record Adresse(

        @Column(name = "strasse", nullable = false)
        String strasse,

        @Column(name = "hausnummer", nullable = false)
        String hausnummer,

        @Column(name = "plz", nullable = false)
        String plz,

        @Column(name = "stadt", nullable = false)
        String stadt,

        @Column(name = "land", nullable = false)
        String land

) {

    /**
     * Kompakter Konstruktor: alle Felder müssen gesetzt und nicht leer sein.
     *
     * @param strasse     Straßenname
     * @param hausnummer  Hausnummer (inkl. Zusätze wie "12a")
     * @param plz         Postleitzahl
     * @param stadt       Stadt
     * @param land        Land
     * @throws IllegalArgumentException wenn ein Feld null oder leer ist
     */
    public Adresse {
        // null und leer einheitlich als ungültige Eingabe behandeln (→ IllegalArgumentException → 400),
        // statt bei null eine NullPointerException zu werfen (die würde fälschlich 500 erzeugen).
        if (istLeer(strasse) || istLeer(hausnummer) || istLeer(plz)
                || istLeer(stadt) || istLeer(land)) {
            throw new IllegalArgumentException("Kein Adressfeld darf null oder leer sein");
        }
    }

    /**
     * Prüft, ob ein Adressfeld fehlt — null oder nur aus Leerzeichen bestehend.
     *
     * @param wert das zu prüfende Feld
     * @return true wenn null oder leer
     */
    private static boolean istLeer(String wert) {
        return wert == null || wert.isBlank();
    }
}
