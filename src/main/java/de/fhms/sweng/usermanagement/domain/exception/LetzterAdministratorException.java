package de.fhms.sweng.usermanagement.domain.exception;

/**
 * Wird geworfen, wenn dem letzten aktiven Administrator die Rolle ADMINISTRATOR entzogen werden soll.
 *
 * <p>Schützt die Invariante „es muss immer mindestens einen aktiven Administrator geben" — sonst
 * könnte niemand mehr auf {@code /rest/admin/**} zugreifen (Selbstaussperrung).</p>
 *
 * <p>RuntimeException (ungeprüft): löst Rollback aus. Bewusst <b>ohne</b> {@code @ResponseStatus} —
 * das Mapping auf HTTP 409 (Conflict) erfolgt zentral im ExceptionHandler.</p>
 */
public class LetzterAdministratorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param nachricht erklärende Meldung
     */
    public LetzterAdministratorException(String nachricht) {
        super(nachricht);
    }
}
