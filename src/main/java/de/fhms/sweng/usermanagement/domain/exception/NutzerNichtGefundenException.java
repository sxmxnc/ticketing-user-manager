package de.fhms.sweng.usermanagement.domain.exception;

/**
 * Wird geworfen, wenn ein NutzerAccount zu einer gesuchten ID/E-Mail nicht existiert.
 *
 * <p>RuntimeException (ungeprüft): löst in einer transaktionalen Methode automatisch ein Rollback
 * aus und muss nicht überall deklariert werden. Bewusst <b>ohne</b> {@code @ResponseStatus} —
 * das Mapping auf HTTP 404 erfolgt zentral im ExceptionHandler, damit alle Fehler an einer
 * Stelle einheitlich behandelt werden.</p>
 */
public class NutzerNichtGefundenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param nachricht erklärende Meldung (ohne PII — keine konkrete E-Mail/ID im Text)
     */
    public NutzerNichtGefundenException(String nachricht) {
        super(nachricht);
    }
}
