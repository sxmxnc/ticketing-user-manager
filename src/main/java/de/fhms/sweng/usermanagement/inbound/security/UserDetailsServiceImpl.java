package de.fhms.sweng.usermanagement.inbound.security;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.KontoStatus;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.repository.NutzerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Inbound-Adapter (Security): verbindet Spring Security mit unserem Domänenmodell.
 *
 * <p>Spring Security kennt unsere {@link NutzerAccount}-Entität nicht. Beim Login ruft der
 * {@code AuthenticationManager} {@link #loadUserByUsername(String)} auf, um zur E-Mail das
 * gespeicherte Passwort und die Rollen zu laden. Den Passwortvergleich übernimmt danach der
 * {@code DaoAuthenticationProvider} mit dem {@code PasswordEncoder} — nicht diese Klasse.</p>
 *
 * <p>Anmeldbar sind {@code ACTIVE}- und {@code PENDING}-Konten: Letztere sind registriert, aber
 * noch in der Saga — sie dürfen sich anmelden und ihr eigenes Profil lesen, schreibende
 * Self-Service-Zugriffe bleiben über den Status-Guard im {@code NutzerService} gesperrt (Semantic
 * Lock). Gesperrte Konten werden mit {@link LockedException} abgelehnt; gelöschte und
 * (Saga-)abgelehnte Konten werden wie nicht vorhanden behandelt (ihre Existenz wird nicht
 * preisgegeben).</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    /**
     * Einheitliche Meldung für „kein anmeldbares Konto" — bewusst unspezifisch, damit nicht
     * preisgegeben wird, ob eine E-Mail existiert oder ein Konto gelöscht wurde.
     */
    private static final String NICHT_GEFUNDEN = "Kein anmeldbares Konto mit dieser E-Mail gefunden";

    private final NutzerRepository nutzerRepository;

    /**
     * Konstruktor-Injektion des Persistenz-Ports.
     *
     * @param nutzerRepository der Port zum Laden von NutzerAccounts
     */
    public UserDetailsServiceImpl(NutzerRepository nutzerRepository) {
        this.nutzerRepository = nutzerRepository;
    }

    /**
     * Lädt die Anmeldedaten (Passwort-Hash + Rollen) eines Nutzers anhand seiner E-Mail.
     *
     * @param email die E-Mail-Adresse als Benutzername (vom AuthenticationManager übergeben)
     * @return die UserDetails (Benutzername, Passwort-Hash, Rollen als Authorities)
     * @throws UsernameNotFoundException wenn die E-Mail ungültig ist, kein Konto existiert
     *                                   oder das Konto gelöscht bzw. abgelehnt wurde
     * @throws LockedException           wenn das Konto gesperrt ist
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        // Erst Format prüfen: ein ungültiges E-Mail-Format ist kein anmeldbarer Nutzer.
        // Ohne diese Prüfung würde der EmailAdresse-Konstruktor eine IllegalArgumentException
        // werfen — im Authentifizierungsfluss ist UsernameNotFoundException der saubere Typ.
        if (!EmailAdresse.istGueltig(email)) {
            LOGGER.warn("loadUserByUsername() — ungültiges E-Mail-Format, Anmeldung abgelehnt");
            throw new UsernameNotFoundException(NICHT_GEFUNDEN);
        }

        NutzerAccount konto = nutzerRepository.findByEmail(new EmailAdresse(email))
                .orElseThrow(() -> new UsernameNotFoundException(NICHT_GEFUNDEN));

        KontoStatus status = konto.getStatus();
        if (status == KontoStatus.DELETED || status == KontoStatus.REJECTED) {
            // Gelöschtes oder (Saga-)abgelehntes Konto wie nicht vorhanden behandeln — terminale
            // Endzustände, deren Existenz nicht preisgegeben wird.
            LOGGER.warn("loadUserByUsername() — Konto ist gelöscht oder abgelehnt, Anmeldung abgelehnt");
            throw new UsernameNotFoundException(NICHT_GEFUNDEN);
        }
        if (status == KontoStatus.BLOCKED) {
            LOGGER.warn("loadUserByUsername() — Konto ist gesperrt, Anmeldung abgelehnt");
            throw new LockedException("Das Konto ist gesperrt");
        }

        LOGGER.info("loadUserByUsername() — Nutzerdetails erfolgreich geladen");
        // Spring-eigenes UserDetails-Objekt aufbauen: Benutzername, gespeicherter Hash, Rollen.
        // Nutzerrolle implementiert GrantedAuthority, daher direkt als Authorities verwendbar.
        return User.withUsername(konto.getEmail().adresse())
                .password(konto.getPasswortHash().hash())
                .authorities(konto.getRollen())
                .build();
    }
}
