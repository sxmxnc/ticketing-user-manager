package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.domain.exception.FalschesPasswortException;
import de.fhms.sweng.usermanagement.domain.exception.KontoNichtAktivException;
import de.fhms.sweng.usermanagement.inbound.rest.dto.FehlerDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit-Test für den {@link ExceptionHandler} — direkter Aufruf der Handler-Methode.
 *
 * <p>Der AccessDenied-Pfad wird hier <b>direkt</b> getestet, weil er über die Controller-Slice nicht
 * auslösbar ist: {@code @WebMvcTest} erzwingt keine Methoden-Security, und in Produktion beschattet
 * die URL-Regel der SecurityFilterChain das {@code @PreAuthorize} (sie liefert 403 bereits auf
 * Filter-Ebene). Der End-to-End-Deny-Fall (CUSTOMER → 403) ist im {@code SecurityIntegrationTest}
 * abgedeckt; dieser Test sichert die einheitliche {@link FehlerDto}-Antwort des Handlers ab.</p>
 */
class ExceptionHandlerTest {

    private final ExceptionHandler handler = new ExceptionHandler();

    @Test
    void handleAccessDenied_gibt403FehlerDto() {
        FehlerDto fehler = handler.handleAccessDenied(new AccessDeniedException("verweigert"));

        assertEquals(HttpStatus.FORBIDDEN.value(), fehler.status());
        assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), fehler.error());
        assertEquals("Zugriff verweigert", fehler.message());
        assertNotNull(fehler.timestamp());
    }

    @Test
    void handleFalschesPasswort_gibt403FehlerDto() {
        FehlerDto fehler = handler.handleFalschesPasswort(
                new FalschesPasswortException("Aktuelles Passwort ist falsch"));

        assertEquals(HttpStatus.FORBIDDEN.value(), fehler.status());
        assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), fehler.error());
        assertEquals("Aktuelles Passwort ist falsch", fehler.message());
        assertNotNull(fehler.timestamp());
    }

    @Test
    void handleKontoNichtAktiv_gibt403FehlerDto() {
        FehlerDto fehler = handler.handleKontoNichtAktiv(
                new KontoNichtAktivException("Konto ist gesperrt oder gelöscht — keine Änderung möglich"));

        assertEquals(HttpStatus.FORBIDDEN.value(), fehler.status());
        assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), fehler.error());
        assertEquals("Konto ist gesperrt oder gelöscht — keine Änderung möglich", fehler.message());
        assertNotNull(fehler.timestamp());
    }
}
