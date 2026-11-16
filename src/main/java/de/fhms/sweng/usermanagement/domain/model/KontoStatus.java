package de.fhms.sweng.usermanagement.domain.model;

/**
 * Zustand eines NutzerAccounts im Ticketshop.
 *
 * <p>Die Zustände PENDING → ACTIVE bzw. PENDING → REJECTED bilden den Semantic Lock und die
 * Kompensation der Registrierungs-Saga ab: Ein neues Konto startet als PENDING (Semantic Lock —
 * der Datensatz wird gerade von der Saga bearbeitet, schreibende Self-Service-Zugriffe sind
 * blockiert). Erst der EventManager gibt das Konto frei (user.provisioned → ACTIVE) oder löst die
 * Kompensation aus (user.provisioning.failed → REJECTED).</p>
 *
 * PENDING: Registrierung committed, aber Saga noch nicht abgeschlossen (Semantic Lock).
 * Login und Lesen des eigenen Profils sind möglich, schreibende Self-Service-Zugriffe nicht.
 * ACTIVE: Konto ist vollständig freigeschaltet und nutzbar.
 * BLOCKED: Konto wurde durch einen Administrator gesperrt (kein Login möglich).
 * DELETED: Konto ist weich gelöscht (Daten bleiben für Audit erhalten).
 * REJECTED: Kompensation der Registrierung — die Provisionierung beim EventManager ist endgültig
 * fehlgeschlagen. Terminaler Zustand, kein Login möglich.
 */
public enum KontoStatus {
    PENDING,
    ACTIVE,
    BLOCKED,
    DELETED,
    REJECTED
}
