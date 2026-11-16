package de.fhms.sweng.usermanagement.domain.model;

import java.util.Locale;

/**
 * Value Object: validierte E-Mail-Adresse eines Nutzers.
 * Die Validierung findet im kompakten Konstruktor statt — eine ungültige
 * E-Mail-Adresse kann nie instanziiert werden (Invariante des Value Objects).
 * Gleichheit basiert auf dem String-Wert (Value Object-Semantik, automatisch durch Record).
 *
 * <p>Normalisierung: Die Adresse wird immer in Kleinbuchstaben gespeichert. E-Mail-Adressen
 * werden in der Praxis von allen Providern case-insensitiv behandelt — {@code Simon@fh.de} und
 * {@code simon@fh.de} sind derselbe Nutzer. Durch die Normalisierung in der VO gilt das überall
 * automatisch: Gleichheit, Unique-Constraint und Suche werden konsistent.</p>
 */
public record EmailAdresse(String adresse) {

    /**
     * Kompakter Konstruktor: prüft Null und Format, normalisiert dann auf Kleinbuchstaben.
     *
     * @param adresse die E-Mail-Adresse als String
     * @throws IllegalArgumentException wenn das Format ungültig ist (auch bei null)
     */
    public EmailAdresse {
        // null oder leer fällt durch istGueltig() → IllegalArgumentException (kein NPE).
        // So wird ungültige Eingabe einheitlich auf HTTP 400 abgebildet, nicht auf 500.
        if (!istGueltig(adresse)) {
            // Keine PII im Exception-Text — keine konkrete Adresse loggen
            throw new IllegalArgumentException("Ungültiges E-Mail-Format");
        }
        // Normalisierung auf Kleinbuchstaben. Locale.ROOT vermeidet locale-abhängige Sonderfälle
        // (z. B. das türkische 'i' → 'İ'), die sonst zu unterschiedlichen Ergebnissen führen können.
        adresse = adresse.toLowerCase(Locale.ROOT);
    }

    /**
     * Prüft, ob ein String eine gültige E-Mail-Adresse darstellt.
     * Kriterien: genau ein @-Zeichen, nicht-leere lokale Parte und Domain,
     * Domain enthält mindestens einen Punkt (nicht am Anfang/Ende).
     *
     * @param adresse zu prüfender String (darf null sein — gibt dann false zurück)
     * @return true wenn das Format gültig erscheint, sonst false
     */
    public static boolean istGueltig(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            return false;
        }
        // Genau ein @ erzwingen: split("@") verschluckt ein abschließendes @,
        // dadurch würde "a@b.de@" sonst fälschlich als gültig durchrutschen.
        // indexOf == lastIndexOf bedeutet: es gibt höchstens ein @.
        if (adresse.indexOf('@') != adresse.lastIndexOf('@')) {
            return false;
        }
        // Aufteilen bei '@': genau zwei Teile erwartet
        String[] teile = adresse.split("@");
        if (teile.length != 2) {
            return false;
        }
        String lokalTeil = teile[0];  // Teil vor dem @, z. B. "simon"
        String domain = teile[1];     // Teil nach dem @, z. B. "fh-muenster.de"
        // Lokaler Teil darf nicht leer sein; Domain braucht mind. einen Punkt (kein am Rand)
        return !lokalTeil.isBlank()
                && domain.contains(".")
                && !domain.startsWith(".")
                && !domain.endsWith(".");
    }
}
