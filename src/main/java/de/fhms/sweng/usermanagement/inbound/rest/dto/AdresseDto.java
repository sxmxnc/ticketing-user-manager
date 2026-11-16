package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.Adresse;

/**
 * DTO für eine Postanschrift — wird sowohl im Request (Profil aktualisieren) als auch
 * in der Response (Profil lesen) verwendet.
 *
 * <p>Trennt die REST-Repräsentation vom domänennahen Value Object {@code Adresse}.
 * Das Mapping in beide Richtungen findet in den statischen Factory-Methoden statt.</p>
 *
 * @param street      Straßenname
 * @param houseNumber Hausnummer (inkl. Zusätze wie "12a")
 * @param zipCode     Postleitzahl
 * @param city        Stadt
 * @param country     Land
 */
public record AdresseDto(String street, String houseNumber, String zipCode, String city, String country) {

    /**
     * Erstellt ein {@link AdresseDto} aus dem Value Object {@link Adresse} (Domain → DTO).
     *
     * @param adresse das Adress-Value-Object
     * @return das passende DTO
     */
    public static AdresseDto von(Adresse adresse) {
        return new AdresseDto(
                adresse.strasse(),
                adresse.hausnummer(),
                adresse.plz(),
                adresse.stadt(),
                adresse.land());
    }

    /**
     * Wandelt dieses DTO in das Value Object {@link Adresse} um (DTO → Domain).
     * Die Validierung (keine leeren Felder) übernimmt der Adresse-Konstruktor.
     *
     * @return das Adress-Value-Object
     * @throws IllegalArgumentException wenn ein Feld leer ist
     * @throws NullPointerException     wenn ein Feld null ist
     */
    public Adresse zuAdresse() {
        return new Adresse(street, houseNumber, zipCode, city, country);
    }
}
