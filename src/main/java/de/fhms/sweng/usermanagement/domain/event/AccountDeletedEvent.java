package de.fhms.sweng.usermanagement.domain.event;

/**
 * Domain-Event: Ein Konto wurde gelöscht.
 *
 * <p>Wird sowohl nach dem Soft-Delete (Self-Service, Status DELETED — die Zeile bleibt als
 * Audit-Trail erhalten) als auch nach dem Hard-Delete (Admin-Funktion, DSGVO-Purge — die Zeile
 * wird physisch entfernt) publiziert. So können nachgelagerte Services (z. B. EventManager,
 * EmailService) zugehörige Daten aufräumen oder den Nutzer benachrichtigen.</p>
 *
 * @param userId die fachliche NutzerID als String
 * @param email  die E-Mail-Adresse des gelöschten Kontos
 */
public record AccountDeletedEvent(String userId, String email) implements DomainEvent {

    /** Löschung eines Kontos → Routing-Key {@code user.deleted}. */
    @Override
    public String routingKey() {
        return "user.deleted";
    }
}
