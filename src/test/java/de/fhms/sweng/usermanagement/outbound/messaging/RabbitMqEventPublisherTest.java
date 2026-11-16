package de.fhms.sweng.usermanagement.outbound.messaging;

import de.fhms.sweng.usermanagement.domain.event.CustomerRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Test für {@link RabbitMqEventPublisher} — {@link StreamBridge} als Mock, kein Spring-Kontext.
 *
 * <p>Geprüft wird der <b>Erfolgspfad</b>: Das Event wird an das korrekte Binding weitergeleitet.
 * Der Fehlerpfad ({@code send} liefert {@code false} → {@code setRollbackOnly}) erfordert einen
 * aktiven {@code @Transactional}-AOP-Proxy und wird deshalb im {@code MessagingIntegrationTest}
 * über seine Wirkung (Rollback der DB-Änderung) abgesichert.</p>
 */
@ExtendWith(MockitoExtension.class)
class RabbitMqEventPublisherTest {

    /** Name des Output-Bindings, identisch mit dem Wert in {@link RabbitMqEventPublisher}. */
    private static final String BINDING = "nutzerEventPublisher-out-0";

    @Mock
    private StreamBridge streamBridge;

    private RabbitMqEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RabbitMqEventPublisher(streamBridge);
    }

    /**
     * Erfolgspfad: {@code StreamBridge.send} wird mit dem korrekten Binding-Namen und einer Nachricht
     * aufgerufen, deren Payload das unveränderte Event ist und deren Header {@code routingKey} den
     * event-spezifischen Routing-Key trägt ({@code user.registered}).
     */
    @Test
    void publiziere_erfolgreich_sendetEventMitRoutingKeyAnBinding() {
        when(streamBridge.send(eq(BINDING), any())).thenReturn(true);
        CustomerRegisteredEvent event =
                new CustomerRegisteredEvent("11111111-1111-1111-1111-111111111111", "demo@example.test");

        publisher.publiziere(event);

        verify(streamBridge).send(eq(BINDING),
                argThat((Message<?> nachricht) ->
                        event.equals(nachricht.getPayload())
                                && "user.registered".equals(nachricht.getHeaders().get("routingKey"))));
    }
}
