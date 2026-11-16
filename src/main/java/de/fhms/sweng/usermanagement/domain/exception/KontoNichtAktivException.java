package de.fhms.sweng.usermanagement.domain.exception;

/**
 * Wird geworfen, wenn eine schreibende Self-Service-Operation (Profil, E-Mail, Passwort) auf einem
 * Konto versucht wird, das nicht {@code ACTIVE} ist (gesperrt oder gelöscht).
 *
 * <p>Hintergrund: Bei zustandslosem JWT bleibt ein vor der Sperrung/Löschung ausgestelltes Token bis
 * zum Ablauf gültig. Ohne diese Prüfung könnte der Inhaber in diesem Zeitfenster sein gesperrtes/
 * gelöschtes Konto noch ändern. Der Login selbst lehnt nicht-aktive Konten bereits ab — dieser Guard
 * schließt zusätzlich das Token-Zeitfenster für die schreibenden Self-Service-Pfade.</p>
 *
 * <p>RuntimeException (ungeprüft): löst Rollback aus. Bewusst <b>ohne</b> {@code @ResponseStatus} —
 * das Mapping auf HTTP 403 (Forbidden) erfolgt zentral im ExceptionHandler.</p>
 */
public class KontoNichtAktivException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param nachricht erklärende Meldung (ohne PII)
     */
    public KontoNichtAktivException(String nachricht) {
        super(nachricht);
    }
}
