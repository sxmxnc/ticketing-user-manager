package de.fhms.sweng.usermanagement.domain.event;

/**
 * Basistyp für alle Domain-Events des UserManagers.
 *
 * <p>Dient als gemeinsamer Obertyp, damit der {@link EventPublisher}-Port eine einzige generische
 * {@code publiziere(DomainEvent)}-Methode anbieten kann, statt je Event-Typ eine eigene Methode.</p>
 *
 * <p>Jedes Event liefert über {@link #routingKey()} seinen AMQP-Routing-Key. So kann der
 * {@code RabbitMqEventPublisher} alle Events auf der gemeinsamen Exchange {@code sweng.events.user}
 * mit unterschiedlichem Routing-Key veröffentlichen, und nachgelagerte Consumer filtern gezielt
 * (z. B. der EventManager reagiert nur auf {@code user.registered}, nicht auf Sperrung/Löschung).</p>
 */
public interface DomainEvent {

    /**
     * Liefert den AMQP-Routing-Key, unter dem dieses Event veröffentlicht wird.
     *
     * @return der Routing-Key (z. B. {@code user.registered})
     */
    String routingKey();
}
