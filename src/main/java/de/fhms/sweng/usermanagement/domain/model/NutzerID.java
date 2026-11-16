package de.fhms.sweng.usermanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object: eindeutige Identität eines NutzerAccounts.
 * Kapselt einen UUID-Wert — Gleichheit wird über den UUID-Wert definiert,
 * nicht über die Java-Objektidentität (typisch für Value Objects).
 * Records in Java sind automatisch immutable und erzeugen equals/hashCode/toString.
 */
public record NutzerID(UUID wert) {

    /**
     * Kompakter Konstruktor: prüft, dass kein null-Wert übergeben wird.
     * Wird vor der Zuweisung der Record-Felder aufgerufen.
     *
     * @param wert der UUID-Wert (darf nicht null sein)
     * @throws NullPointerException wenn wert null ist
     */
    public NutzerID {
        // Kompakter Konstruktor (kein 'this.wert = wert' nötig — macht Java automatisch)
        Objects.requireNonNull(wert, "NutzerID-Wert darf nicht null sein");
    }

    /**
     * Erzeugt eine neue, zufällige NutzerID.
     * Wird bei der Registrierung eines neuen Nutzerkontos verwendet.
     *
     * @return frische NutzerID mit zufälligem UUID
     */
    public static NutzerID neu() {
        return new NutzerID(UUID.randomUUID());
    }

    /**
     * Erzeugt eine NutzerID aus ihrer String-Darstellung (z. B. einem Pfad-Parameter).
     * Zentralisiert das Parsen, das sonst in jedem Controller dupliziert würde.
     *
     * @param text die UUID als String
     * @return die typisierte NutzerID
     * @throws IllegalArgumentException wenn der String kein gültiges UUID-Format hat
     */
    public static NutzerID ausText(String text) {
        return new NutzerID(UUID.fromString(text));
    }
}
