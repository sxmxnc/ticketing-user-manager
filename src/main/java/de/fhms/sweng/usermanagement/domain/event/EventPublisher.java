package de.fhms.sweng.usermanagement.domain.event;

/**
 * Port (Interface) für das Veröffentlichen von Domain-Events.
 *
 * <p>Liegt im Domain-Layer: Die Domain definiert, dass sie Events veröffentlichen können muss, ohne
 * die Messaging-Technik zu kennen (Dependency Inversion). Die konkrete Umsetzung liefert der
 * Outbound-Adapter {@code RabbitMqEventPublisher} (Spring Cloud Stream / RabbitMQ).</p>
 */
public interface EventPublisher {

    /**
     * Veröffentlicht ein Domain-Event über den Messaging-Adapter.
     *
     * @param event das zu veröffentlichende Event
     */
    void publiziere(DomainEvent event);
}
