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
 * Inbound-Adapter (Messaging): konsumiert das {@link OrderConfirmedEvent} von Order/Ticketing.
 *
 * <p>Gebunden an die Exchange {@code order.events} mit Routing-Key {@code order.confirmed} (nur
 * bestätigte, bezahlte Bestellungen — nicht {@code order.created}/{@code order.cancelled}). Als
 * Reaktion befördert der UserManager den Käufer von PROSPECT zu CUSTOMER
 * ({@link NutzerService#befoerdereZuKunde(NutzerID)}).</p>
 *
 * <p>Dies ist <b>keine</b> Saga, sondern ein einfaches Event-getriebenes Domain-Event-Abo: Order hat
 * den Kauf bereits abgeschlossen, der UserManager zieht eventually konsistent nach. Es gibt nichts zu
 * kompensieren.</p>
 *
 * <p>Defensive Verarbeitung: Eine fehlende oder ungültige {@code customerId} (kein UUID-Format) wird
 * als Warnung geloggt und verworfen — kein Fehler, kein Retry. Ein unbekannter Nutzer wird vom
 * {@link NutzerService} idempotent ignoriert. Es werden keine personenbezogenen Daten geloggt.</p>
 */
@Component
public class OrderConfirmedEventConsumer implements Consumer<Message<OrderConfirmedEvent>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderConfirmedEventConsumer.class);

    private final NutzerService nutzerService;

    /**
     * Konstruktor-Injektion des NutzerService.
     *
     * @param nutzerService der Application Service für Nutzer-Operationen
     */
    public OrderConfirmedEventConsumer(NutzerService nutzerService) {
        this.nutzerService = nutzerService;
    }

    /**
     * Verarbeitet ein {@link OrderConfirmedEvent}: befördert den Käufer (sofern ACTIVE) zu CUSTOMER.
     *
     * @param message die eingehende Nachricht mit dem Event als Payload
     */
    @Override
    @Transactional
    public void accept(Message<OrderConfirmedEvent> message) {
        LOGGER.info("accept() — OrderConfirmedEvent empfangen, Käufer wird ggf. zu CUSTOMER befördert");
        OrderConfirmedEvent event = message.getPayload();
        String customerId = event.customerId();
        if (customerId == null || customerId.isBlank()) {
            LOGGER.warn("accept() — order.confirmed ohne customerId, Event wird ignoriert");
            return;
        }
        // Ungültiges UUID-Format ist kein Programmierfehler, sondern ein verwaistes/fehlerhaftes Event:
        // sauber verwerfen (kein Rethrow → keine Redelivery), statt die Verarbeitung abzubrechen.
        NutzerID nutzerId;
        try {
            nutzerId = NutzerID.ausText(customerId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("accept() — customerId ist keine gültige UUID, Event wird ignoriert");
            return;
        }
        nutzerService.befoerdereZuKunde(nutzerId);
    }
}
