package de.fhms.sweng.usermanagement.inbound.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.Map;

/**
 * Konfiguration des eingehenden Messaging-Adapters (Inbound).
 *
 * <p>Definiert einen {@link MessageRoutingCallback}-Bean, der eingehende AMQP-Nachrichten
 * anhand des Headers {@code amqp_receivedRoutingKey} an den richtigen Consumer weiterleitet.
 * Damit wird ein einziger RabbitMQ-Consumer ({@code functionRouter-in-0}) für mehrere
 * Event-Typen genutzt — das Routing geschieht zur Laufzeit über den Header, nicht über
 * separate Queues pro Event-Typ.</p>
 *
 * <p>Mapping (Routing-Key → Consumer-Bean-Name):
 * <ul>
 *   <li>{@code user.provisioned} → {@link UserProvisionedEventConsumer}</li>
 *   <li>{@code user.provisioning.failed} → {@link UserProvisioningFailedEventConsumer}</li>
 * </ul>
 * Unbekannte Routing-Keys liefern {@code null} → die Nachricht wird ohne Verarbeitung verworfen
 * (kein Fehler, kein Retry).</p>
 */
@Configuration
public class MessagingConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingConfiguration.class);

    /**
     * Routing-Tabelle: AMQP-Routing-Key → Name der Consumer-Bean (Spring Cloud Function-Konvention).
     * Der Bean-Name ist der lowercase-Klassenname des Consumers (Spring-Default:
     * {@code userProvisionedEventConsumer}). Die Routing-Keys ({@code user.provisioned} /
     * {@code user.provisioning.failed}) sind teamübergreifend mit dem EventManager abgestimmt und
     * müssen auf beiden Seiten identisch sein.
     */
    private static final Map<String, String> ROUTING_KEY_ZU_BINDING = Map.of(
            "user.provisioned", "userProvisionedEventConsumer",
            "user.provisioning.failed", "userProvisioningFailedEventConsumer"
    );

    /**
     * Bean, der dem {@code functionRouter} von Spring Cloud Function mitteilt, an welchen
     * Consumer eine eingehende Nachricht delegiert werden soll.
     *
     * @return den {@link MessageRoutingCallback} mit der konfigurierten Routing-Tabelle
     */
    @Bean
    public MessageRoutingCallback messageRoutingCallback() {
        return new MessageRoutingCallback() {
            @Override
            public String routingResult(Message<?> message) {
                String routingKey = message.getHeaders().get("amqp_receivedRoutingKey", String.class);
                if (routingKey == null) {
                    LOGGER.warn("routingResult() — Header amqp_receivedRoutingKey fehlt, Nachricht wird ignoriert");
                    return null;
                }
                String zielBinding = ROUTING_KEY_ZU_BINDING.get(routingKey);
                if (zielBinding == null) {
                    LOGGER.warn("routingResult() — unbekannter Routing-Key: {}, Nachricht wird ignoriert",
                            routingKey);
                }
                return zielBinding;
            }
        };
    }
}
