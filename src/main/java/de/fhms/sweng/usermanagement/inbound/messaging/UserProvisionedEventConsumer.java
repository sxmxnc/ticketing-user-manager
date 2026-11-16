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
 * Inbound-Adapter (Messaging): konsumiert das {@link UserProvisionedEvent} des EventManagers.
 *
 * <p>Wird vom {@code functionRouter} aufgerufen, wenn der Header
 * {@code amqp_receivedRoutingKey = "user.provisioned"} vorliegt (Routing über
 * {@link MessagingConfiguration}). Als Reaktion gibt der Service den Semantic Lock frei
 * (PENDING → ACTIVE) — die Rolle bleibt PROSPECT (Kunde wird man erst durch einen Kauf).</p>
 *
 * <p>Idempotenz: {@link NutzerService#aktiviereKonto(NutzerID)} ist idempotent (Guard im Aggregat) —
 * eine doppelte Zustellung hat keinen weiteren Effekt. Sicherheit: Es wird kein PII geloggt; eine
 * fehlende oder ungültige {@code userId} wird verworfen (kein Rethrow → keine Redelivery).</p>
 */
@Component
public class UserProvisionedEventConsumer implements Consumer<Message<UserProvisionedEvent>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserProvisionedEventConsumer.class);

    private final NutzerService nutzerService;

    /**
     * Konstruktor-Injektion des NutzerService.
     *
     * @param nutzerService der Application Service für Nutzer-Operationen
     */
    public UserProvisionedEventConsumer(NutzerService nutzerService) {
        this.nutzerService = nutzerService;
    }

    /**
     * Verarbeitet ein {@link UserProvisionedEvent}: gibt den Semantic Lock frei (PENDING → ACTIVE).
     *
     * @param message die eingehende Nachricht mit dem Event als Payload
     */
    @Override
    @Transactional
    public void accept(Message<UserProvisionedEvent> message) {
        LOGGER.info("accept() — UserProvisionedEvent empfangen, Konto wird freigeschaltet (ACTIVE)");
        UserProvisionedEvent event = message.getPayload();
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
        nutzerService.aktiviereKonto(id);
    }
}
