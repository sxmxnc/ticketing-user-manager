package de.fhms.sweng.usermanagement.inbound.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit-Test für {@link MessagingConfiguration} — prüft die Routing-Tabelle des
 * {@link MessageRoutingCallback} ohne Spring-Kontext.
 */
class MessagingConfigurationTest {

    private MessageRoutingCallback callback;

    @BeforeEach
    void setUp() {
        MessagingConfiguration config = new MessagingConfiguration();
        callback = config.messageRoutingCallback();
    }

    @Test
    void routingResult_userProvisioned_gibtKorrektesBeanZurueck() {
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("amqp_receivedRoutingKey", "user.provisioned")
                .build();

        String ergebnis = callback.routingResult(message);

        assertEquals("userProvisionedEventConsumer", ergebnis);
    }

    @Test
    void routingResult_userProvisioningFailed_gibtKorrektesBeanZurueck() {
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("amqp_receivedRoutingKey", "user.provisioning.failed")
                .build();

        String ergebnis = callback.routingResult(message);

        assertEquals("userProvisioningFailedEventConsumer", ergebnis);
    }

    @Test
    void routingResult_unbekannterRoutingKey_gibtNullZurueck() {
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("amqp_receivedRoutingKey", "unbekannteEvent")
                .build();

        String ergebnis = callback.routingResult(message);

        assertNull(ergebnis);
    }

    @Test
    void routingResult_keinRoutingKeyHeader_gibtNullZurueck() {
        // Nachricht ohne amqp_receivedRoutingKey-Header (z. B. Test-Binder ohne AMQP-Kontext)
        Message<?> message = MessageBuilder.withPayload("payload").build();

        String ergebnis = callback.routingResult(message);

        assertNull(ergebnis);
    }
}
