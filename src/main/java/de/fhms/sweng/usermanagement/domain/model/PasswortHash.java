package de.fhms.sweng.usermanagement.domain.model;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Value Object: gehashtes Passwort eines Nutzers.
 * Der Konstruktor akzeptiert ausschließlich bereits gehashte Strings —
 * Klartextpasswörter werden niemals gespeichert oder geloggt.
 * Das Hashing selbst übernimmt die Factory-Methode {@link #vonKlartext(String, PasswordEncoder)},
 * die den PasswordEncoder von außen erhält (hexagonale Architektur: kein Spring-Import im Aufruf).
 */
public record PasswortHash(String hash) {

    /** Mindestlänge für Klartextpasswörter (Sicherheitsvorgabe: 10). */
    private static final int MINDESTLAENGE = 10;

    /**
     * Kompakter Konstruktor: akzeptiert nur nicht-null Hashes.
     * Wird direkt nur mit bereits geHashten Werten aufgerufen (z. B. aus der DB).
     *
     * @param hash der BCrypt-Hash-String
     * @throws NullPointerException wenn hash null ist
     */
    public PasswortHash {
        Objects.requireNonNull(hash, "Passwort-Hash darf nicht null sein");
    }

    /**
     * Factory-Methode: erzeugt einen PasswortHash aus einem Klartext-Passwort.
     * Validiert die Mindestlänge und delegiert das Hashing an den übergebenen PasswordEncoder.
     * Der PasswordEncoder kommt als Spring-Bean von außen — die Domain bleibt Spring-frei.
     *
     * @param klartext        das Klartextpasswort (mind. 10 Zeichen)
     * @param passwordEncoder der zu verwendende Encoder (z.B. BCryptPasswordEncoder)
     * @return neuer PasswortHash mit dem erzeugten BCrypt-Hash
     * @throws IllegalArgumentException wenn das Passwort zu kurz oder null ist
     */
    public static PasswortHash vonKlartext(String klartext, PasswordEncoder passwordEncoder) {
        if (klartext == null || klartext.length() < MINDESTLAENGE) {
            throw new IllegalArgumentException(
                    "Passwort muss mindestens " + MINDESTLAENGE + " Zeichen haben");
        }
        // Kein Logging des Klartexts — Sicherheitstabu
        return new PasswortHash(passwordEncoder.encode(klartext));
    }

    /**
     * Prüft, ob ein Klartext-Passwort mit diesem Hash übereinstimmt.
     * Wird beim Login verwendet — der PasswordEncoder kennt das Hashing-Verfahren.
     *
     * @param klartext        das zu prüfende Klartextpasswort
     * @param passwordEncoder der zum Hashen verwendete Encoder
     * @return true wenn Klartext und Hash übereinstimmen, sonst false
     */
    public boolean stimmt(String klartext, PasswordEncoder passwordEncoder) {
        if (klartext == null) {
            return false;
        }
        return passwordEncoder.matches(klartext, this.hash);
    }
}
