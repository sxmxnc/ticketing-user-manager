package de.fhms.sweng.usermanagement.application;

import de.fhms.sweng.usermanagement.domain.event.EventPublisher;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integrationstest für die {@code @Retryable}-Resilienz des {@link NutzerService}.
 *
 * <p>{@code @SpringBootTest} lädt den echten Kontext, damit der Retry-Advisor (Spring-7-Core,
 * via {@code @EnableResilientMethods}) tatsächlich greift — in reinen Unit-Tests mit gemocktem
 * Service gibt es keinen Advisor und der Retry wäre nicht testbar.</p>
 *
 * <p>Das Repository ist gemockt: ein simulierter Versionskonflikt
 * ({@link OptimisticLockingFailureException}) beim {@code save()} steht stellvertretend für die
 * {@code ObjectOptimisticLockingFailureException}, die JPA real beim Commit wirft. Geprüft wird,
 * dass die Operation automatisch wiederholt wird und nach erschöpften Versuchen propagiert
 * (dann greift auf dem REST-Pfad der 409-Fallback im ExceptionHandler).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class NutzerServiceRetryTest {

    @Autowired
    private NutzerService nutzerService;

    @MockitoBean
    private NutzerRepository nutzerRepository;

    // EventPublisher gemockt: kein echter Nachrichtenversand im Test.
    @MockitoBean
    private EventPublisher eventPublisher;

    @Test
    void registriere_versionskonfliktBeimErstenVersuch_wirdWiederholtUndGelingt() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());
        // Erster save() wirft einen Versionskonflikt, der zweite gelingt → @Retryable muss greifen.
        when(nutzerRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("Versionskonflikt simuliert"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NutzerAccount ergebnis =
                nutzerService.registriere("retry@example.test", "GeheimesPasswort1!", "Max", "Mustermann");

        assertThat(ergebnis).isNotNull();
        // save() zweimal: ein Fehlschlag + ein erfolgreicher Retry → belegt die Wiederholung.
        verify(nutzerRepository, times(2)).save(any());
        // Event erst beim erfolgreichen Versuch — der Fehlversuch publiziert nichts (kein Doppel-Event).
        verify(eventPublisher, times(1)).publiziere(any());
    }

    @Test
    void registriere_dauerhafterVersionskonflikt_propagiertNachErschoepftenVersuchen() {
        when(nutzerRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(nutzerRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("Versionskonflikt simuliert"));

        assertThrows(OptimisticLockingFailureException.class,
                () -> nutzerService.registriere("dauerkonflikt@example.test", "GeheimesPasswort1!", "Max", "Mustermann"));

        // maxRetries = 3 → ein Erstversuch + drei Retries = vier save()-Aufrufe, dann Aufgabe.
        verify(nutzerRepository, times(4)).save(any());
        // Bei durchgehendem Fehlschlag wird nie ein Event publiziert.
        verify(eventPublisher, never()).publiziere(any());
    }
}
