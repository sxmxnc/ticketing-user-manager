package de.fhms.sweng.usermanagement.domain.exception;

/**
 * Wird geworfen, wenn bei einer sensiblen Self-Service-Operation (E-Mail- oder Passwortänderung)
 * das mitgeschickte aktuelle/alte Passwort nicht zum gespeicherten Hash passt.
 *
 * <p>RuntimeException (ungeprüft): löst Rollback aus. Bewusst <b>ohne</b> {@code @ResponseStatus} —
 * das Mapping auf HTTP 403 (Forbidden) erfolgt zentral im ExceptionHandler. 403 statt 401, weil der
 * Nutzer bereits authentifiziert ist (gültiges Token), aber die für die Aktion nötige Eigentümerschaft
 * (Kenntnis des aktuellen Passworts) nicht nachweisen kann.</p>
 */
public class FalschesPasswortException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param nachricht erklärende Meldung (ohne PII, ohne Passwort im Text)
     */
    public FalschesPasswortException(String nachricht) {
        super(nachricht);
    }
}
