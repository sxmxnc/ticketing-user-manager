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
 * Unit-Test für {@link UserProvisioningFailedEventConsumer} — {@link NutzerService} als Mock. Geprüft
 * wird die Delegation an {@code lehneRegistrierungAb} (inkl. reason) sowie die defensive Behandlung
 * ungültiger userId-Werte.
 */
@ExtendWith(MockitoExtension.class)
class UserProvisioningFailedEventConsumerTest {

    private static final String GUELTIGE_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private NutzerService nutzerService;

    private UserProvisioningFailedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserProvisioningFailedEventConsumer(nutzerService);
    }

    @Test
    void accept_gueltigeUserId_lehntRegistrierungAb() {
        Message<UserProvisioningFailedEvent> message = MessageBuilder
                .withPayload(new UserProvisioningFailedEvent(GUELTIGE_ID, "demo@example.test", "Timeout"))
                .build();

        consumer.accept(message);

        verify(nutzerService).lehneRegistrierungAb(NutzerID.ausText(GUELTIGE_ID), "Timeout");
    }

    @Test
    void accept_ungueltigeUuid_wirdIgnoriert() {
        Message<UserProvisioningFailedEvent> message = MessageBuilder
                .withPayload(new UserProvisioningFailedEvent("kein-uuid", "demo@example.test", "Timeout"))
                .build();

        consumer.accept(message);

        verifyNoInteractions(nutzerService);
    }
}
