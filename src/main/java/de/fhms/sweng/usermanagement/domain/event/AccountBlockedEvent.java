package de.fhms.sweng.usermanagement.domain.event;

/**
 * Domain-Event: Ein Konto wurde gesperrt (Status BLOCKED).
 *
 * <p>Wird publiziert, wenn ein Administrator ein Konto sperrt, damit nachgelagerte Services
 * reagieren können (z. B. laufende Sitzungen invalidieren, Benachrichtigung).</p>
 *
 * @param userId die fachliche NutzerID als String
 * @param email  die E-Mail-Adresse des gesperrten Kontos
 */
public record AccountBlockedEvent(String userId, String email) implements DomainEvent {

    /** Sperrung eines Kontos → Routing-Key {@code user.blocked}. */
    @Override
    public String routingKey() {
        return "user.blocked";
    }
}
