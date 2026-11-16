package de.fhms.sweng.usermanagement.inbound.rest;

import de.fhms.sweng.usermanagement.domain.exception.EmailBereitsVergebenException;
import de.fhms.sweng.usermanagement.domain.exception.FalschesPasswortException;
import de.fhms.sweng.usermanagement.domain.exception.KontoNichtAktivException;
import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.exception.NutzerNichtGefundenException;
import de.fhms.sweng.usermanagement.inbound.rest.dto.FehlerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Zentraler Ausnahmebehandler für alle REST-Controller (Inbound-Adapter).
 *
 * <p>{@code @RestControllerAdvice} fängt Exceptions, die aus einem Controller fliegen,
 * ab und wandelt sie in eine einheitliche JSON-Fehlerantwort ({@link FehlerDto}) um.
 * Die Controller selbst benötigen dadurch keine try/catch-Blöcke.</p>
 *
 * <p>Logging: fachliche Fehler (400, 404, 409) als WARN; unerwartete Datenbankfehler als ERROR.
 * Niemals Passwörter, Tokens oder PII in Log-Nachrichten.</p>
 */
@RestControllerAdvice
public class ExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler.class);

    /**
     * Fängt ungültige Eingaben ab: nicht-parsbare E-Mail, zu kurzes Passwort,
     * ungültiges UUID-Format im Pfad, unbekannte Sparte im Request-Body.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 400
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public FehlerDto handleIllegalArgument(IllegalArgumentException ex) {
        LOGGER.warn("handleIllegalArgument() — ungültige Eingabe: {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Kein Konto mit der angegebenen ID gefunden.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 404
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(NutzerNichtGefundenException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public FehlerDto handleNutzerNichtGefunden(NutzerNichtGefundenException ex) {
        LOGGER.warn("handleNutzerNichtGefunden() — {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Die E-Mail-Adresse ist bereits registriert.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 409
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(EmailBereitsVergebenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public FehlerDto handleEmailBereitsVergeben(EmailBereitsVergebenException ex) {
        LOGGER.warn("handleEmailBereitsVergeben() — {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Fehlgeschlagene Anmeldung: falsche Anmeldedaten, unbekanntes oder gesperrtes Konto.
     * Die Antwort ist bewusst unspezifisch ("Ungültige Anmeldedaten") und enthält keine
     * Originalmeldung — so wird nicht verraten, ob eine E-Mail existiert oder gesperrt ist.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 401
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public FehlerDto handleAuthentication(AuthenticationException ex) {
        // Nur den Fehlertyp loggen — keine PII und keine Originalmeldung (kann die E-Mail enthalten)
        LOGGER.warn("handleAuthentication() — Anmeldung fehlgeschlagen: {}", ex.getClass().getSimpleName());
        return FehlerDto.von(HttpStatus.UNAUTHORIZED, "Ungültige Anmeldedaten");
    }

    /**
     * Zugriff verweigert: authentifiziert, aber unzureichende Rolle. Wird von der Methoden-Security
     * ({@code @PreAuthorize}) ausgelöst, wenn eine Prüfung innerhalb des Controller-Aufrufs fehlschlägt.
     * (Die URL-basierte Regel der SecurityFilterChain liefert 403 bereits auf Filter-Ebene; dieser
     * Handler fängt den verbleibenden Methoden-Security-Pfad ab und liefert eine einheitliche FehlerDto.)
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 403
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public FehlerDto handleAccessDenied(AccessDeniedException ex) {
        LOGGER.warn("handleAccessDenied() — Zugriff verweigert: {}", ex.getClass().getSimpleName());
        return FehlerDto.von(HttpStatus.FORBIDDEN, "Zugriff verweigert");
    }

    /**
     * Bei einer sensiblen Self-Service-Aktion (E-Mail- oder Passwortänderung) stimmt das mitgeschickte
     * aktuelle/alte Passwort nicht. 403 statt 401, weil der Nutzer bereits authentifiziert ist (gültiges
     * Token), aber die für die Aktion nötige Eigentümerschaft (Passwortkenntnis) nicht nachweisen kann.
     * Die Meldung ist bewusst generisch und enthält weder PII noch das Passwort.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 403
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(FalschesPasswortException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public FehlerDto handleFalschesPasswort(FalschesPasswortException ex) {
        LOGGER.warn("handleFalschesPasswort() — {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Schreibende Self-Service-Aktion auf einem nicht aktiven Konto (gesperrt oder gelöscht). 403, weil
     * der Nutzer zwar authentifiziert ist (ggf. mit einem vor der Sperrung ausgestellten Token), die
     * Aktion auf einem nicht-aktiven Konto aber nicht erlaubt ist. Meldung ohne PII.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 403
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(KontoNichtAktivException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public FehlerDto handleKontoNichtAktiv(KontoNichtAktivException ex) {
        LOGGER.warn("handleKontoNichtAktiv() — {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Dem letzten aktiven Administrator soll die Rolle ADMINISTRATOR entzogen werden — abgelehnt,
     * damit nicht der gesamte Admin-Zugang verloren geht (Selbstaussperrung).
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 409
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(LetzterAdministratorException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public FehlerDto handleLetzterAdministrator(LetzterAdministratorException ex) {
        LOGGER.warn("handleLetzterAdministrator() — {}", ex.getMessage());
        return FehlerDto.von(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Gleichzeitiger Schreibzugriff zweier Anfragen auf dasselbe Konto.
     * Bei einem Versionskonflikt (@Version) wirft Hibernate intern eine JPA-OptimisticLockException;
     * Springs Transaktionsmanagement übersetzt sie beim Commit in eine OptimisticLockingFailureException
     * der Spring-Hierarchie. Daher wird hier bewusst die Spring-Oberklasse gefangen — sie deckt auch
     * die konkrete ObjectOptimisticLockingFailureException ab, die beim Commit tatsächlich ankommt.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 409
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public FehlerDto handleOptimisticLock(OptimisticLockingFailureException ex) {
        LOGGER.error("handleOptimisticLock() — Schreibkonflikt aufgetreten", ex);
        return FehlerDto.von(HttpStatus.CONFLICT, "Das Konto wurde zwischenzeitlich geändert — bitte erneut versuchen");
    }

    /**
     * Datenbankintegritätsverletzung — tritt auf, wenn zwei gleichzeitige Registrierungsanfragen
     * dieselbe E-Mail verwenden: die erste Anfrage gewinnt den DB-Unique-Constraint auf email,
     * die zweite landet hier. Ohne diesen Handler würde Spring einen 500-Fehler liefern.
     *
     * @param ex die aufgetretene Exception
     * @return Fehlerantwort mit HTTP 409
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public FehlerDto handleDataIntegrity(DataIntegrityViolationException ex) {
        LOGGER.error("handleDataIntegrity() — Datenbankkonflikt (Unique-Constraint verletzt)", ex);
        return FehlerDto.von(HttpStatus.CONFLICT, "Die E-Mail-Adresse ist bereits vergeben");
    }
}
