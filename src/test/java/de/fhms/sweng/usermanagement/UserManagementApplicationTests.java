package de.fhms.sweng.usermanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-Test: Prüft ob der Spring-ApplicationContext fehlerfrei startet
 * und alle Beans korrekt geladen werden.
 */
@SpringBootTest
class UserManagementApplicationTests {

    // ApplicationContext wird von Spring injiziert — enthält alle registrierten Beans
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void kontextStartetFehlerfreiHoch() {
        // Prüft dass der Context nicht null ist und erfolgreich gestartet wurde
        assertThat(applicationContext).isNotNull();
    }
}
