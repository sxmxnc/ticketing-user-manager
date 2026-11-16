package de.fhms.sweng.usermanagement.inbound.messaging;

/**
 * Eingehendes Saga-Reply-Event: Der EventManager hat den neu registrierten Nutzer erfolgreich
 * provisioniert (Routing-Key {@code user.provisioned} auf der Exchange
 * {@code sweng.events.user.provisioning}).
 *
 * <p>Wird vom {@link UserProvisionedEventConsumer} konsumiert. Als Reaktion gibt der UserManager den
 * Semantic Lock frei (PENDING → ACTIVE). Korreliert wird über {@code userId} — die fachliche NutzerID
 * (UUID), die der EventManager unverändert aus dem ursprünglichen {@code UserRegistered}-Event
 * zurückgibt.</p>
 *
 * @param userId die fachliche NutzerID des bestätigten Nutzers als UUID-String
 * @param email  die E-Mail-Adresse des Nutzers (nur zur Information)
 */
public record UserProvisionedEvent(String userId, String email) {
}
