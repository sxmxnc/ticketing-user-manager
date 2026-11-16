package de.fhms.sweng.usermanagement.domain.event;

/**
 * Domain-Event: Ein neuer Nutzer hat sich registriert.
 *
 * <p>Wird nach erfolgreicher Registrierung publiziert (Routing-Key {@code user.registered}) und ist
 * der <b>Auslöser der Registrierungs-Saga</b>: Der EventManager konsumiert dieses Event,
 * provisioniert den Nutzer in seinem Kontext und antwortet mit {@code user.provisioned} bzw.
 * {@code user.provisioning.failed}. Andere Services können es ebenfalls nutzen (z. B. Willkommens-Mail).</p>
 *
 * @param userId die fachliche NutzerID als String
 * @param email  die E-Mail-Adresse des neuen Nutzers
 */
public record CustomerRegisteredEvent(String userId, String email) implements DomainEvent {

    /** Registrierung eines neuen Nutzers → Routing-Key {@code user.registered}. */
    @Override
    public String routingKey() {
        return "user.registered";
    }
}
