package de.fhms.sweng.usermanagement.domain.repository;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;

import java.util.List;
import java.util.Optional;

/**
 * Port (Interface) für den Datenbankzugriff auf NutzerAccounts.
 * Liegt im Domain-Layer — die Domain definiert, was sie braucht.
 * Die JPA-Implementierung ({@code JpaNutzerRepository} im outbound-Paket) erfüllt diesen Vertrag.
 * Dependency Inversion: Domain hängt nicht von JPA ab — JPA hängt von der Domain ab.
 *
 * <p>Methodennamen bewusst an Spring-Data-Ableitungskonventionen angepasst:
 * {@code findByNutzerId} statt {@code findById} (Kollision mit JpaRepository.findById(Long)),
 * {@code suche} statt {@code findAll(String)} (Kollision mit JpaRepository.findAll()).</p>
 *
 * <p>Standard-Löschung ist der Soft-Delete über {@link NutzerAccount#loeschen()} (Status DELETED,
 * Zeile bleibt erhalten — Audit-Trail). Daneben gibt es {@link #loescheEndgueltig(NutzerAccount)} als
 * physischen Hard-Delete, der ausschließlich den Admin-Funktionen vorbehalten ist (DSGVO-„Recht auf
 * Vergessenwerden"/Purge) — die Zeile wird dabei tatsächlich aus der Datenbank entfernt.</p>
 */
public interface NutzerRepository {

    /**
     * Speichert einen neuen oder aktualisierten NutzerAccount.
     *
     * @param konto der zu speichernde NutzerAccount
     * @return der gespeicherte NutzerAccount (mit gesetztem Surrogatschlüssel nach dem ersten Persist)
     */
    NutzerAccount save(NutzerAccount konto);

    /**
     * Sucht einen NutzerAccount anhand seines fachlichen Schlüssels.
     *
     * @param id die NutzerID (fachlicher Schlüssel, nicht der Surrogatschlüssel)
     * @return Optional mit dem gefundenen Konto, oder leer wenn nicht vorhanden
     */
    Optional<NutzerAccount> findByNutzerId(NutzerID id);

    /**
     * Sucht einen NutzerAccount anhand der E-Mail-Adresse.
     *
     * @param email die E-Mail-Adresse
     * @return Optional mit dem gefundenen Konto, oder leer wenn nicht vorhanden
     */
    Optional<NutzerAccount> findByEmail(EmailAdresse email);

    /**
     * Gibt alle NutzerAccounts zurück, optional gefiltert nach E-Mail (Teilstring).
     * Leerer oder null-Filter liefert alle Konten.
     *
     * @param filter Suchbegriff (Teilstring der E-Mail), oder null für alle
     * @return Liste aller passenden Konten (niemals null)
     */
    List<NutzerAccount> suche(String filter);

    /**
     * Zählt die Konten mit einer bestimmten Rolle und einem bestimmten Status.
     *
     * <p>Wird genutzt, um die Invariante „es muss immer mindestens einen aktiven Administrator geben"
     * zu prüfen, bevor einem Konto die Rolle ADMINISTRATOR entzogen wird.</p>
     *
     * @param rolle  die gesuchte Rolle
     * @param status der gesuchte Kontostatus
     * @return Anzahl der Konten, die Rolle und Status erfüllen
     */
    long zaehleMitRolleUndStatus(Nutzerrolle rolle, KontoStatus status);

    /**
     * Entfernt einen NutzerAccount physisch aus der Datenbank (Hard-Delete, DSGVO-Purge).
     *
     * <p>Im Gegensatz zum fachlichen Soft-Delete ({@link NutzerAccount#loeschen()}) wird die Zeile
     * hier tatsächlich gelöscht — es bleibt kein Audit-Trail. Nur für Admin-Funktionen vorgesehen
     * (z. B. „Recht auf Vergessenwerden"). Es wird bewusst die bereits geladene Entity übergeben
     * (nicht nur die ID), damit ein evtl. vorhandenes {@code Kundenprofil} über den Cascade
     * mitgelöscht wird und das Optimistic Locking greift.</p>
     *
     * @param konto der endgültig zu entfernende NutzerAccount
     */
    void loescheEndgueltig(NutzerAccount konto);
}
