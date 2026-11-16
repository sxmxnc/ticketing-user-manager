package de.fhms.sweng.usermanagement.inbound.messaging;

/**
 * Eingehendes Saga-Reply-Event (Kompensation): Der EventManager konnte den neu registrierten Nutzer
 * nicht provisionieren (Routing-Key {@code user.provisioning.failed} auf der Exchange
 * {@code sweng.events.user.provisioning}).
 *
 * <p>Wird vom {@link UserProvisioningFailedEventConsumer} konsumiert. Als Reaktion kompensiert der
 * UserManager die Registrierung — ein noch ausstehendes Konto wird auf REJECTED gesetzt. Korreliert
 * wird über {@code userId}.</p>
 *
 * @param userId die fachliche NutzerID des betroffenen Nutzers als UUID-String
 * @param email  die E-Mail-Adresse des Nutzers (nur zur Information)
 * @param reason Beschreibung des Fehlschlags (sollte keine personenbezogenen Daten enthalten)
 */
public record UserProvisioningFailedEvent(String userId, String email, String reason) {
}
