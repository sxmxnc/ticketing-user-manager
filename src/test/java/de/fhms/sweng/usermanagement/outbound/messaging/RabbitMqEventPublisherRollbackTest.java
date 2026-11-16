package de.fhms.sweng.usermanagement.outbound.messaging;

import de.fhms.sweng.usermanagement.domain.event.CustomerRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integrationstest für den Fehlerpfad von {@link RabbitMqEventPublisher}: Schlägt der Versand fehl
 * ({@code StreamBridge.send} liefert {@code false}), muss die umgebende Transaktion zum Rollback
 * markiert werden ({@code setRollbackOnly}).
 *
 * <p>Dieser Pfad braucht einen echten {@code @Transactional}-AOP-Proxy und eine laufende Transaktion —
 * deshalb ein {@code @SpringBootTest} statt eines reinen Unit-Tests. Die {@link StreamBridge} wird
 * gemockt und auf Fehlschlag gestellt; der Versand läuft in einer programmatischen Transaktion
 * ({@link TransactionTemplate}). Beim Commit erkennt Spring das rollback-only-Flag und wirft eine
 * {@link UnexpectedRollbackException} — der Nachweis, dass {@code setRollbackOnly} gegriffen hat.</p>
 */
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
@ActiveProfiles("test")
class RabbitMqEventPublisherRollbackTest {

    @MockitoBean
    private StreamBridge streamBridge;

    @Autowired
    private RabbitMqEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void publiziere_versandFehlgeschlagen_markiertTransaktionFuerRollback() {
        when(streamBridge.send(anyString(), any())).thenReturn(false);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CustomerRegisteredEvent event = new CustomerRegisteredEvent("id-1", "test@example.test");

        // publiziere() tritt der Template-Transaktion bei, setzt sie auf rollback-only →
        // der anschließende Commit wirft UnexpectedRollbackException.
        assertThrows(UnexpectedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(status -> publisher.publiziere(event)));
    }
}
