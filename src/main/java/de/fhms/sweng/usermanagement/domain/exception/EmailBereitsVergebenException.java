package de.fhms.sweng.usermanagement.domain.exception;

/**
 * Wird geworfen, wenn eine Registrierung mit einer bereits vergebenen E-Mail-Adresse versucht wird.
 *
 * <p>RuntimeException (ungeprüft): löst Rollback aus. Bewusst <b>ohne</b> {@code @ResponseStatus} —
 * das Mapping auf HTTP 409 (Conflict) erfolgt zentral im ExceptionHandler.</p>
 */
public class EmailBereitsVergebenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param nachricht erklärende Meldung (ohne PII — keine konkrete E-Mail im Text)
     */
    public EmailBereitsVergebenException(String nachricht) {
        super(nachricht);
    }
}
