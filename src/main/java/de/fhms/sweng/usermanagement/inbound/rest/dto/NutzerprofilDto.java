package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.Kundenprofil;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.Sparte;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Response-DTO für die Nutzerprofil-Daten (Profil lesen, Registrierung, Profil aktualisieren).
 *
 * <p>Enthält bewusst <b>kein</b> Passwort und keinen Hash. Die {@code version} wird mitgegeben,
 * damit der Client bei einer späteren Aktualisierung seine bekannte Version mitschicken kann
 * (Optimistic-Locking-Konflikterkennung)</p>
 *
 * <p>Profilfelder ({@code billingAddress}, {@code interests}) sind null/leer, solange noch
 * kein Kundenprofil angelegt wurde.</p>
 *
 * @param userId         die fachliche NutzerID (UUID)
 * @param email          die E-Mail-Adresse
 * @param displayName    der Anzeigename ("Vorname Nachname")
 * @param firstName      der Vorname
 * @param lastName       der Nachname
 * @param status         der Kontostatus (PENDING, ACTIVE, BLOCKED, DELETED, REJECTED)
 * @param roles          die zugewiesenen Rollen als Namen
 * @param version        der Versionszähler (Optimistic Locking)
 * @param billingAddress die Rechnungsadresse, oder null wenn kein Kundenprofil existiert
 * @param interests      die Interessengebiete als Sparten-Namen (leer wenn kein Profil)
 */
public record NutzerprofilDto(
        UUID userId,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String status,
        Set<String> roles,
        long version,
        AdresseDto billingAddress,
        Set<String> interests) {

    /**
     * Erstellt ein {@link NutzerprofilDto} aus dem Aggregate Root {@link NutzerAccount}
     * (Domain → DTO). Enums werden auf ihre Namen abgebildet; ein evtl. vorhandenes
     * Kundenprofil wird mit übernommen.
     *
     * @param konto der NutzerAccount
     * @return das Response-DTO
     */
    public static NutzerprofilDto von(NutzerAccount konto) {
        // Rollen-Enums auf Strings abbilden — bewusst per for-Schleife (Lesbarkeit)
        Set<String> rollenNamen = new LinkedHashSet<>();
        for (Nutzerrolle rolle : konto.getRollen()) {
            rollenNamen.add(rolle.name());
        }

        // Kundenprofil ist optional (0..1) — nur abbilden wenn vorhanden
        AdresseDto adresseDto = null;
        Set<String> interessenNamen = new LinkedHashSet<>();
        Kundenprofil profil = konto.getKundenprofil();
        if (profil != null) {
            adresseDto = AdresseDto.von(profil.getRechnungsAdresse());
            for (Sparte sparte : profil.getInteressen()) {
                interessenNamen.add(sparte.name());
            }
        }

        return new NutzerprofilDto(
                konto.getNutzerId().wert(),
                konto.getEmail().adresse(),
                konto.getName().anzeigename(),
                konto.getName().vorname(),
                konto.getName().nachname(),
                konto.getStatus().name(),
                rollenNamen,
                konto.getVersion(),
                adresseDto,
                interessenNamen);
    }
}
