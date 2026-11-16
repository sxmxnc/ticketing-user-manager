package de.fhms.sweng.usermanagement.outbound.messaging;

import de.fhms.sweng.usermanagement.domain.event.DomainEvent;
import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Ausgehender Adapter (outbound): veröffentlicht Domain-Events über Spring Cloud Stream / RabbitMQ.
 *
 * <p>Implementiert den Port {@link EventPublisher}. Sendet jedes Event über das Output-Binding
 * {@code nutzerEventPublisher-out-0} (→ Exchange {@code sweng.events.user}) mittels
 * {@link StreamBridge}.</p>
 *
 * <p>Transaktionalität (Eventual Consistency, §5.7): Die Methode ist {@code @Transactional} und nimmt
 * an der Transaktion des aufrufenden Service teil. Schlägt der Versand fehl ({@code send} liefert
 * {@code false}), wird die laufende Transaktion über {@code setRollbackOnly()} zum Rollback markiert —
 * dadurch wird z. B. eine Registrierung zurückgerollt und kein Konto ohne zugehöriges Event committet.</p>
 *
 * <p>Sicherheit: Es wird niemals der Event-Inhalt geloggt (kann E-Mail/PII enthalten), nur der
 * Event-Typ.</p>
 */
@Component
public class RabbitMqEventPublisher implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqEventPublisher.class);

    /** Name des Output-Bindings; zeigt laut application.yml auf die Destination sweng.events.user. */
    private static final String BINDING = "nutzerEventPublisher-out-0";

    private final StreamBridge streamBridge;

    /**
     * Konstruktor-Injektion der StreamBridge.
     *
     * @param streamBridge die Spring-Cloud-Stream-Brücke zum Senden an ein Binding
     */
    public RabbitMqEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Veröffentlicht ein Domain-Event. Schlägt der Versand fehl, wird die umgebende Transaktion
     * zum Rollback markiert (statt das Event „verloren" gehen zu lassen).
     *
     * @param event das zu veröffentlichende Event
     */
    @Override
    @Transactional
    public void publiziere(DomainEvent event) {
        // Kein Event-Inhalt loggen — nur der Typ (PII-Tabu)
        String eventTyp = event.getClass().getSimpleName();
        // Event-spezifischen Routing-Key als Header mitgeben; die Producer-Binding-Expression
        // routing-key-expression: headers['routingKey'] setzt ihn auf der Topic-Exchange. So kann der
        // EventManager gezielt nur user.registered konsumieren (nicht user.blocked/user.deleted).
        Message<DomainEvent> nachricht = MessageBuilder.withPayload(event)
                .setHeader("routingKey", event.routingKey())
                .build();
        boolean erfolgreich = streamBridge.send(BINDING, nachricht);
        if (erfolgreich) {
            LOGGER.info("publiziere() — {} veröffentlicht", eventTyp);
        } else {
            LOGGER.error("publiziere() — Veröffentlichung von {} fehlgeschlagen, Transaktion wird zurückgerollt",
                    eventTyp);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
