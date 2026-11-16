package de.fhms.sweng.usermanagement.domain.model;

/**
 * Veranstaltungssparten des Ticketshops, für die ein Kunde Interesse bekunden kann.
 * Wird im Kundenprofil als {@code Set<Sparte>} gespeichert (Mehrfachauswahl möglich).
 */
public enum Sparte {
    MUSIC,
    MUSICAL,
    THEATER,
    CLASSICAL,
    COMEDY
}
