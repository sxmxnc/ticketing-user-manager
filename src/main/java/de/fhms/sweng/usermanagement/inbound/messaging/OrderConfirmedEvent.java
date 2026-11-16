package de.fhms.sweng.usermanagement.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Eingehendes Event von Order/Ticketing: eine Bestellung wurde erfolgreich bezahlt und bestätigt
 * (Routing-Key {@code order.confirmed} auf der Exchange {@code order.events}).
 *
 * <p>Wird vom {@link OrderConfirmedEventConsumer} konsumiert. Als Reaktion befördert der UserManager
 * den Käufer von PROSPECT zu CUSTOMER. Korreliert wird über {@code customerId} — die fachliche
 * NutzerID (UUID) aus dem JWT-Claim {@code userId}, die Order in jedem Event mitführt.</p>
 *
 * <p>Das Order-Event trägt weitere Felder (orderId, status, totalPrice, seats), die wir hier nicht
 * brauchen. {@code @JsonIgnoreProperties(ignoreUnknown = true)} ignoriert sie beim Deserialisieren,
 * sodass wir nur das relevante Feld modellieren (lose Kopplung an den Order-Vertrag).</p>
 *
 * @param customerId die NutzerID des Käufers als UUID-String (JWT-Claim {@code userId})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(String customerId) {
}
