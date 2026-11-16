package de.fhms.sweng.usermanagement.domain.model;

import org.springframework.security.core.GrantedAuthority;

/**
 * Rollen im Ticketshop-System. Implementiert GrantedAuthority, damit Spring Security
 * die Rollen direkt für @PreAuthorize-Prüfungen verwenden kann.
 * Ein NutzerAccount kann mehrere Rollen gleichzeitig haben ({@code Set<Nutzerrolle>}).
 *
 * PROSPECT: Neu registrierter Interessent, hat noch nichts gekauft (Ausgangszustand nach Registrierung).
 * CUSTOMER:       Hat mindestens eine Bestellung bezahlt (Kauf-Choreographie: order.confirmed).
 * ADMINISTRATOR: Vollzugriff auf alle Konten (Sperren, Rollen vergeben).
 * EVENT_MANAGER: Kann Veranstaltungen anlegen und verwalten.
 * ACCOUNTANT:  Lesezugriff auf Transaktionen und Abrechnungen.
 * SERVICE_STAFF: Support-Zugang für Kundenanfragen.
 * CUSTOMER_SERVICE: Erweiterte Support-Rechte (z. B. Rückerstattungen).
 */
public enum Nutzerrolle implements GrantedAuthority {
    PROSPECT,
    CUSTOMER,
    ADMINISTRATOR,
    EVENT_MANAGER,
    ACCOUNTANT,
    SERVICE_STAFF,
    CUSTOMER_SERVICE;

    /**
     * Gibt den Rollennamen als String zurück — wird von Spring Security für
     * Berechtigungsprüfungen (z. B. hasAuthority("ADMINISTRATOR")) verwendet.
     *
     * @return Name der Rolle als String
     */
    @Override
    public String getAuthority() {
        return name();
    }
}
