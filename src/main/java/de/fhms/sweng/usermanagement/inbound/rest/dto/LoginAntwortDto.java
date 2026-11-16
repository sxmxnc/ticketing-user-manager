package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Response-DTO der Anmeldung: enthält das ausgestellte JWT plus die Profil-Basisdaten, die die App
 * nach dem Login lokal cachen kann (Anzeigename für UI/Support-Tickets, stabile userId, E-Mail, Rollen)
 * — so muss der Name nicht vor jedem API-Call erneut abgerufen werden.
 *
 * <p>Der Client schickt das Token anschließend bei jedem Request im Header
 * {@code Authorization: Bearer <token>} mit. {@code userId} ist die stabile fachliche Identität (UUID);
 * die E-Mail (= Login-Name) ist dagegen änderbar. {@code displayName} wird aus Vor- und Nachname
 * gebildet.</p>
 *
 * @param token       das signierte JSON Web Token
 * @param userId      die fachliche NutzerID (UUID)
 * @param email       die E-Mail-Adresse
 * @param displayName der Anzeigename ("Vorname Nachname")
 * @param roles       die zugewiesenen Rollen als Namen
 */
public record LoginAntwortDto(String token, UUID userId, String email, String displayName, Set<String> roles) {

    /**
     * Erstellt ein {@link LoginAntwortDto} aus dem ausgestellten Token und dem angemeldeten Konto
     * (Domain → DTO). Die Rollen-Enums werden auf ihre Namen abgebildet; der Anzeigename kommt aus
     * dem Value Object {@link de.fhms.sweng.usermanagement.domain.model.PersonenName}.
     *
     * @param token das bereits ausgestellte JWT
     * @param konto der angemeldete NutzerAccount
     * @return das Response-DTO
     */
    public static LoginAntwortDto von(String token, NutzerAccount konto) {
        // Rollen-Enums auf Strings abbilden — bewusst per for-Schleife (Lesbarkeit)
        Set<String> rollenNamen = new LinkedHashSet<>();
        for (Nutzerrolle rolle : konto.getRollen()) {
            rollenNamen.add(rolle.name());
        }
        return new LoginAntwortDto(
                token,
                konto.getNutzerId().wert(),
                konto.getEmail().adresse(),
                konto.getName().anzeigename(),
                rollenNamen);
    }

    /**
     * Überschriebene toString-Methode: maskiert das Token.
     * Ein JWT ist ein Credential — es darf nicht versehentlich in Logs landen.
     *
     * @return String-Repräsentation ohne das eigentliche Token
     */
    @Override
    public String toString() {
        return "LoginAntwortDto[token=***, userId=" + userId + ", email=" + email
                + ", displayName=" + displayName + ", roles=" + roles + "]";
    }
}
