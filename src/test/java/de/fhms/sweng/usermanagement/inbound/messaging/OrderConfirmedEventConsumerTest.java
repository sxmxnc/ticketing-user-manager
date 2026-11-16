package de.fhms.sweng.usermanagement.inbound.messaging;

import de.fhms.sweng.usermanagement.application.NutzerService;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit-Test für {@link OrderConfirmedEventConsumer} — kein Spring-Kontext, {@link NutzerService}
 * als Mock. Geprüft wird die Delegation an den Service sowie die defensive Behandlung fehlender
 * oder ungültiger {@code customerId}-Werte (kein Aufruf, kein Fehler).
 */
@ExtendWith(MockitoExtension.class)
class OrderConfirmedEventConsumerTest {

    private static final String GUELTIGE_ID = "55555555-5555-5555-5555-555555555555";

    @Mock
    private NutzerService nutzerService;

    private OrderConfirmedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderConfirmedEventConsumer(nutzerService);
    }

    @Test
    void accept_gueltigeCustomerId_befoerdertZuKunde() {
        Message<OrderConfirmedEvent> message =
                MessageBuilder.withPayload(new OrderConfirmedEvent(GUELTIGE_ID)).build();

        consumer.accept(message);

        verify(nutzerService).befoerdereZuKunde(NutzerID.ausText(GUELTIGE_ID));
    }

    @Test
    void accept_ungueltigeUuid_wirdIgnoriert() {
        Message<OrderConfirmedEvent> message =
                MessageBuilder.withPayload(new OrderConfirmedEvent("kein-uuid")).build();

        consumer.accept(message);

        verifyNoInteractions(nutzerService);
    }

    @Test
    void accept_leereCustomerId_wirdIgnoriert() {
        Message<OrderConfirmedEvent> message =
                MessageBuilder.withPayload(new OrderConfirmedEvent("   ")).build();

        consumer.accept(message);

        verifyNoInteractions(nutzerService);
    }
}
