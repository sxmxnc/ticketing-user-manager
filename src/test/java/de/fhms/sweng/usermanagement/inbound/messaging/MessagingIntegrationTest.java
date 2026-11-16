package de.fhms.sweng.usermanagement.inbound.messaging;

import de.fhms.sweng.usermanagement.application.NutzerService;
import de.fhms.sweng.usermanagement.domain.event.CustomerRegisteredEvent;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrationstest für das Messaging (Publish + Consume) über den In-Memory-Test-Binder.
 *
 * <p>{@code @Import(TestChannelBinderConfiguration.class)} ersetzt den RabbitMQ-Binder durch einen
 * Test-Binder: Statt echter CloudAMQP-Queues schreibt/liest der Test in eine {@link InputDestination}
 * (eingehende Nachrichten simulieren) und eine {@link OutputDestination} (ausgehende Nachrichten
 * abgreifen). So lässt sich der gesamte Stream-Pfad testen, ohne einen echten Broker zu kontaktieren.</p>
 *
 * <p>Profil {@code test}: liefert die Test-Destinations mit {@code .test}-Suffix
 * ({@code sweng.events.user.test}, {@code sweng.events.user.provisioning.test},
 * {@code order.events.test}) und deaktiviert den RabbitMQ-Health-Check.</p>
 *
 * <p>Abgedeckte Fälle:
 * <ul>
 *   <li><b>Publish:</b> {@code registriere()} veröffentlicht ein {@link CustomerRegisteredEvent}.</li>
 *   <li><b>Voll-Saga Happy Path:</b> Registrierung (PENDING) + {@code user.provisioned} → Konto ACTIVE,
 *       Rolle bleibt PROSPECT (Semantic Lock freigegeben, aber noch kein Kunde).</li>
 *   <li><b>Idempotenz:</b> Zweimaliges Zustellen von {@code user.provisioned} lässt das Konto genau
 *       einmal ACTIVE werden.</li>
 *   <li><b>Voll-Saga Kompensation:</b> Registrierung (PENDING) + {@code user.provisioning.failed}
 *       → Konto REJECTED.</li>
 *   <li><b>Kauf-Choreographie:</b> {@code order.confirmed} befördert einen ACTIVE+PROSPECT-Seed zu
 *       CUSTOMER (nur Rolle, Status bleibt ACTIVE).</li>
 * </ul></p>
 *
 * <p><b>Hinweis — geteilter, veränderlicher Seed-Zustand:</b> Die Saga-Consume-Tests registrieren
 * jeweils ihren <i>eigenen</i> Nutzer (PENDING) und fassen die Seed-Konten nicht an; nur der Kauf-Test
 * verändert den Seed {@code prospect@example.test} → CUSTOMER. Da kein {@code @DirtiesContext} gesetzt ist,
 * „leakt" der DB-Zustand zwischen Methoden — hier unkritisch, weil jeder Test einen eindeutigen Nutzer
 * prüft und idempotente Endzustände erwartet.</p>
 */
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
@ActiveProfiles("test")
class MessagingIntegrationTest {

    /** Test-Destination für ausgehende User-Events (mit .test-Suffix aus application-test.yml). */
    private static final String OUT_DESTINATION = "sweng.events.user.test";

    /** Test-Destination für eingehende Saga-Reply-Events des EventManagers. */
    private static final String IN_DESTINATION = "sweng.events.user.provisioning.test";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private NutzerService nutzerService;

    @Autowired
    private NutzerRepository nutzerRepository;

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Test
    void registriere_veroeffentlichtCustomerRegisteredEvent() {
        nutzerService.registriere("publish-test@example.test", "GeheimesPasswort1!", "Max", "Mustermann");

        // Die ausgehende Nachricht vom Out-Binding abgreifen (max. 2 s warten)
        Message<byte[]> nachricht = outputDestination.receive(2000, OUT_DESTINATION);
        assertNotNull(nachricht, "Es sollte ein CustomerRegisteredEvent veröffentlicht worden sein");

        CustomerRegisteredEvent event =
                objectMapper.readValue(nachricht.getPayload(), CustomerRegisteredEvent.class);
        assertEquals("publish-test@example.test", event.email());
        assertNotNull(event.userId());
    }

    @Test
    void registrierung_dannUserProvisioned_machtKontoAktiv() {
        // Voll-Saga (Happy Path): Registrierung → PENDING, dann EventManager-Bestätigung → ACTIVE.
        // Belang-Trennung: die Rolle bleibt PROSPECT (CUSTOMER kommt erst über order.confirmed).
        NutzerAccount neu = nutzerService.registriere(
                "saga-ok@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String userId = neu.getNutzerId().wert().toString();
        // Das eigene CustomerRegisteredEvent abräumen (geteilte Out-Queue)
        outputDestination.receive(2000, OUT_DESTINATION);

        Message<UserProvisionedEvent> nachricht = MessageBuilder
                .withPayload(new UserProvisionedEvent(userId, "saga-ok@example.test"))
                .setHeader("amqp_receivedRoutingKey", "user.provisioned")
                .build();
        inputDestination.send(nachricht, IN_DESTINATION);

        NutzerAccount konto = ladeKonto("saga-ok@example.test");
        assertEquals(KontoStatus.ACTIVE, konto.getStatus(),
                "Nach user.provisioned sollte das Konto ACTIVE sein (Semantic Lock freigegeben)");
        assertTrue(konto.getRollen().contains(Nutzerrolle.PROSPECT),
                "Die Rolle bleibt PROSPECT — Kunde wird man erst durch einen Kauf");
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER),
                "user.provisioned darf NICHT zu CUSTOMER befördern");
    }

    @Test
    void userProvisioned_zweimal_bleibtAktiv() {
        // Idempotenz: dieselbe Bestätigung zweimal zustellen lässt das Konto genau einmal ACTIVE werden
        NutzerAccount neu = nutzerService.registriere(
                "saga-idem@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String userId = neu.getNutzerId().wert().toString();
        outputDestination.receive(2000, OUT_DESTINATION);

        Message<UserProvisionedEvent> nachricht = MessageBuilder
                .withPayload(new UserProvisionedEvent(userId, "saga-idem@example.test"))
                .setHeader("amqp_receivedRoutingKey", "user.provisioned")
                .build();
        inputDestination.send(nachricht, IN_DESTINATION);
        inputDestination.send(nachricht, IN_DESTINATION);

        NutzerAccount konto = ladeKonto("saga-idem@example.test");
        assertEquals(KontoStatus.ACTIVE, konto.getStatus());
    }

    @Test
    void registrierung_dannUserProvisioningFailed_kompensiertZuAbgelehnt() {
        // Voll-Saga (Kompensation): Registrierung → PENDING, dann Fehler-Event → REJECTED
        NutzerAccount neu = nutzerService.registriere(
                "saga-fail@example.test", "GeheimesPasswort1!", "Max", "Mustermann");
        String userId = neu.getNutzerId().wert().toString();
        outputDestination.receive(2000, OUT_DESTINATION);

        Message<UserProvisioningFailedEvent> nachricht = MessageBuilder
                .withPayload(new UserProvisioningFailedEvent(userId, "saga-fail@example.test", "Timeout"))
                .setHeader("amqp_receivedRoutingKey", "user.provisioning.failed")
                .build();
        inputDestination.send(nachricht, IN_DESTINATION);

        NutzerAccount konto = ladeKonto("saga-fail@example.test");
        assertEquals(KontoStatus.REJECTED, konto.getStatus(),
                "Nach user.provisioning.failed sollte das Konto REJECTED sein (Kompensation)");
        assertFalse(konto.getRollen().contains(Nutzerrolle.CUSTOMER),
                "Ein abgelehntes Konto darf nicht zu CUSTOMER geworden sein");
    }

    @Test
    void orderConfirmedEvent_befoerdertProspektSeedZuKunde() {
        // Kauf-Choreographie (Weg 1): order.confirmed befördert den ACTIVE+PROSPECT-Seed zu CUSTOMER.
        // Belang-Trennung: nur die Rolle ändert sich, der Status bleibt ACTIVE.
        Message<OrderConfirmedEvent> nachricht = MessageBuilder
                .withPayload(new OrderConfirmedEvent("55555555-5555-5555-5555-555555555555"))
                .setHeader("amqp_receivedRoutingKey", "order.confirmed")
                .build();

        inputDestination.send(nachricht, "order.events.test");

        NutzerAccount konto = ladeKonto("prospect@example.test");
        assertTrue(konto.getRollen().contains(Nutzerrolle.CUSTOMER),
                "Nach order.confirmed sollte der Käufer CUSTOMER sein");
        assertFalse(konto.getRollen().contains(Nutzerrolle.PROSPECT),
                "Nach der Konversion sollte PROSPECT entfernt sein");
        assertEquals(KontoStatus.ACTIVE, konto.getStatus(),
                "Der Kauf darf den Status nicht ändern (bleibt ACTIVE)");
    }

    /** Lädt ein Konto frisch aus der DB anhand seiner E-Mail (nach der Consumer-Verarbeitung). */
    private NutzerAccount ladeKonto(String email) {
        Optional<NutzerAccount> konto = nutzerRepository.findByEmail(new EmailAdresse(email));
        assertTrue(konto.isPresent(), "Konto muss vorhanden sein");
        return konto.get();
    }
}
