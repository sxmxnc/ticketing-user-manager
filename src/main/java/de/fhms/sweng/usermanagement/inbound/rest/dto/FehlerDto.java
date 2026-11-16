package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Einheitliche Fehler-Antwort für die REST-API.
 *
 * <p>Wird vom zentralen {@code ExceptionHandler} (@RestControllerAdvice) erzeugt und als
 * JSON an den Client zurückgegeben. Bietet eine strukturierte Fehlermeldung statt nur eines
 * HTTP-Statuscodes.</p>
 *
 * <p>Wichtig: Die {@code nachricht} darf keine sensiblen Daten enthalten (kein Passwort,
 * kein Token, keine PII).</p>
 *
 * @param status    der HTTP-Statuscode (z. B. 400, 404, 409)
 * @param error     die Kurzbezeichnung des Fehlers (z. B. "Bad Request")
 * @param message   eine erklärende Meldung für den Client
 * @param timestamp der Zeitpunkt des Fehlers
 */
public record FehlerDto(int status, String error, String message, Instant timestamp) {

    /**
     * Bequeme Factory: leitet Statuscode und Kurzbezeichnung aus dem {@link HttpStatus} ab
     * (eine einzige Quelle — keine magischen Zahlen, keine duplizierten Reason-Strings) und
     * setzt den Zeitpunkt automatisch auf jetzt.
     *
     * @param status    der HTTP-Status (liefert Code via {@code value()} und Kurzbezeichnung via {@code getReasonPhrase()})
     * @param nachricht die erklärende Meldung
     * @return das Fehler-DTO mit aktuellem Zeitstempel
     */
    public static FehlerDto von(HttpStatus status, String nachricht) {
        return new FehlerDto(status.value(), status.getReasonPhrase(), nachricht, Instant.now());
    }
}
