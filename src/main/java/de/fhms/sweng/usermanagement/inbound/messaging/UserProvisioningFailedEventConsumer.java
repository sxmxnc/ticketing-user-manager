package de.fhms.sweng.usermanagement.inbound.messaging;

import de.fhms.sweng.usermanagement.application.NutzerService;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Inbound-Adapter (Messaging): konsumiert das {@link UserProvisioningFailedEvent} des EventManagers.
 *
 * <p>Wird vom {@code functionRouter} aufgerufen, wenn der Header
 * {@code amqp_receivedRoutingKey = "user.provisioning.failed"} vorliegt (Routing über
 * {@link MessagingConfiguration}). Als Reaktion kompensiert der Service die Registrierung — ein noch
 * ausstehendes Konto wird auf REJECTED gesetzt (Saga-Kompensation, Semantic Lock terminal).</p>
 *
 * <p>Sicherheit: Die E-Mail-Adresse wird nicht geloggt (PII-Tabu). Der {@code reason} aus dem Event
 * wird als Warnung ausgegeben — er sollte keine personenbezogenen Daten enthalten. Eine fehlende oder
 * ungültige {@code userId} wird verworfen (kein Rethrow → keine Redelivery).</p>
 */
@Component
public class UserProvisioningFailedEventConsumer implements Consumer<Message<UserProvisioningFailedEvent>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserProvisioningFailedEventConsumer.class);

    private final NutzerService nutzerService;

    /**
     * Konstruktor-Injektion des NutzerService.
     *
     * @param nutzerService der Application Service für Nutzer-Operationen
     */
    public UserProvisioningFailedEventConsumer(NutzerService nutzerService) {
        this.nutzerService = nutzerService;
    }

    /**
     * Verarbeitet ein {@link UserProvisioningFailedEvent}: kompensiert die Registrierung
     * (ausstehendes Konto → REJECTED).
     *
     * @param message die eingehende Nachricht mit dem Event als Payload
     */
    @Override
    @Transactional
    public void accept(Message<UserProvisioningFailedEvent> message) {
        LOGGER.info("accept() — UserProvisioningFailedEvent empfangen");
        UserProvisioningFailedEvent event = message.getPayload();
        String userId = event.userId();
        if (userId == null || userId.isBlank()) {
            LOGGER.warn("accept() — Reply ohne userId, Event wird ignoriert");
            return;
        }
        NutzerID id;
        try {
            id = NutzerID.ausText(userId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("accept() — userId ist keine gültige UUID, Event wird ignoriert");
            return;
        }
        nutzerService.lehneRegistrierungAb(id, event.reason());
    }
}
