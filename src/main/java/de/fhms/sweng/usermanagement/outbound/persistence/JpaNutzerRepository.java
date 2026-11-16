package de.fhms.sweng.usermanagement.outbound.persistence;

import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ausgehender Adapter (outbound): implementiert den Port {@link NutzerRepository} via Spring Data JPA.
 *
 * <p>Kombiniertes Interface-Muster (wie Professorenlösung): dieses Interface erbt von
 * {@link JpaRepository} (Spring-Data-Standard-CRUD) und von {@link NutzerRepository} (Domain-Port).
 * Spring Data generiert die Implementierung automatisch zur Laufzeit — keine eigene Klasse nötig.</p>
 *
 * <p>Ableitungsregeln für Spring Data:
 * <ul>
 *   <li>{@code save} — aus JpaRepository geerbt</li>
 *   <li>{@code findByNutzerId} — Spring Data leitet ab: {@code WHERE nutzer_id = ?}
 *       (Converter wandelt NutzerID → String automatisch)</li>
 *   <li>{@code findByEmail} — Spring Data leitet ab: {@code WHERE email = ?}
 *       (Converter wandelt EmailAdresse → String automatisch)</li>
 *   <li>{@code suche} — native Query nötig, weil LIKE auf konvertiertem Attribut in JPQL
 *       nicht zuverlässig funktioniert (Converter greift auf Rohwert-Ebene, nicht JPQL-Ebene)</li>
 * </ul>
 * </p>
 */
@Repository
public interface JpaNutzerRepository extends JpaRepository<NutzerAccount, Long>, NutzerRepository {

    /**
     * Sucht NutzerAccounts anhand eines Teilstrings der E-Mail-Adresse (Groß-/Kleinschreibung ignoriert).
     * Native Query auf der Roh-Spalte {@code email}, weil LIKE auf JPA-konvertierten Attributen
     * in JPQL nicht zuverlässig funktioniert.
     *
     * @param filter Teilstring der E-Mail (Groß-/Kleinschreibung egal), oder null für alle Konten
     * @return Liste aller passenden NutzerAccounts (niemals null)
     */
    @Query(value = "SELECT * FROM nutzer WHERE :filter IS NULL "
            + "OR LOWER(email) LIKE LOWER(CONCAT('%', :filter, '%'))",
            nativeQuery = true)
    List<NutzerAccount> suche(@Param("filter") String filter);

    /**
     * Zählt Konten mit Rolle und Status per JPQL. Anders als bei {@code suche} ist hier KEIN
     * Native-Query nötig: {@code status} und die Rollen-Elemente sind einfache {@code @Enumerated}-Enums
     * (keine Custom-Converter), daher funktioniert der JPQL-Join über die {@code @ElementCollection}
     * {@code rollen} zuverlässig.
     *
     * @param rolle  die gesuchte Rolle
     * @param status der gesuchte Kontostatus
     * @return Anzahl der passenden Konten
     */
    @Override
    @Query("SELECT COUNT(n) FROM NutzerAccount n JOIN n.rollen r WHERE r = :rolle AND n.status = :status")
    long zaehleMitRolleUndStatus(@Param("rolle") Nutzerrolle rolle, @Param("status") KontoStatus status);

    /**
     * Hard-Delete: delegiert an das von {@link JpaRepository} geerbte {@code delete(entity)}.
     * Entity-basiert (nicht als Bulk-Query), damit der Cascade auf ein evtl. vorhandenes
     * {@code Kundenprofil} und das Optimistic Locking korrekt angewendet werden.
     *
     * @param konto der endgültig zu entfernende NutzerAccount
     */
    @Override
    default void loescheEndgueltig(NutzerAccount konto) {
        delete(konto);
    }
}
