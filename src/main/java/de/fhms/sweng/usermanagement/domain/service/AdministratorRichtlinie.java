package de.fhms.sweng.usermanagement.domain.service;

import de.fhms.sweng.usermanagement.domain.exception.LetzterAdministratorException;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.springframework.stereotype.Service;

/**
 * Domain-Service für die Administrator-Invariante: „Es muss immer mindestens einen aktiven
 * Administrator geben."
 *
 * <p>Kapselt die Regel an einer einzigen Stelle, damit alle Operationen, die einen aktiven
 * Administrator deaktivieren oder entfernen würden, sie wiederverwenden können:
 * {@code sperreKonto}, {@code entzieheRolle} und {@code entferneKontoEndgueltig} im
 * {@code AdminService} sowie der Soft-Delete {@code loescheEigenesKonto} im {@code NutzerService}.</p>
 *
 * <p>Liegt im Domain-Layer und hängt nur vom Port {@link NutzerRepository} ab (Dependency Inversion);
 * die Zählung erfolgt über {@link NutzerRepository#zaehleMitRolleUndStatus(Nutzerrolle, KontoStatus)}.</p>
 */
@Service
public class AdministratorRichtlinie {

    private final NutzerRepository nutzerRepository;

    /**
     * Konstruktor-Injektion des Persistenz-Ports.
     *
     * @param nutzerRepository der Port zum Zählen der Konten
     */
    public AdministratorRichtlinie(NutzerRepository nutzerRepository) {
        this.nutzerRepository = nutzerRepository;
    }

    /**
     * Prüft, ob das übergebene Konto der letzte aktive Administrator ist.
     *
     * <p>Nur ein selbst aktives Konto mit der Rolle ADMINISTRATOR kann „der letzte" sein. Ist das
     * Konto nicht aktiv oder kein Administrator, zählt es nicht mit — dann wird auch nicht gezählt.</p>
     *
     * @param konto das zu prüfende Konto
     * @return true, wenn es genau diesen einen aktiven Administrator gibt
     */
    public boolean istLetzterAktiverAdministrator(NutzerAccount konto) {
        boolean kontoIstAktiverAdmin = konto.getStatus() == KontoStatus.ACTIVE
                && konto.getRollen().contains(Nutzerrolle.ADMINISTRATOR);
        if (!kontoIstAktiverAdmin) {
            return false;
        }
        return nutzerRepository.zaehleMitRolleUndStatus(Nutzerrolle.ADMINISTRATOR, KontoStatus.ACTIVE) <= 1;
    }

    /**
     * Stellt sicher, dass das Deaktivieren/Entfernen dieses Kontos nicht den letzten aktiven
     * Administrator beseitigt. Andernfalls wird die Aktion mit einer Exception abgelehnt.
     *
     * @param konto das Konto, das deaktiviert oder entfernt werden soll
     * @throws LetzterAdministratorException wenn es der letzte aktive Administrator ist
     */
    public void stelleSicherNichtLetzterAdministrator(NutzerAccount konto) {
        if (istLetzterAktiverAdministrator(konto)) {
            throw new LetzterAdministratorException(
                    "Der letzte aktive Administrator kann nicht entfernt oder deaktiviert werden");
        }
    }
}
