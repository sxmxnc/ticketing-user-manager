package de.fhms.sweng.usermanagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Einstiegspunkt des UserManager-Microservice (Ticketshop SS26).
 *
 * Rolle in der Architektur: Startet den Spring-ApplicationContext und
 * aktiviert alle benötigten Spring-Funktionen via Annotationen.
 */
// @SpringBootApplication aktiviert Auto-Konfiguration, Component-Scan und Konfigurationsklassen
@SpringBootApplication
// @EnableResilientMethods aktiviert Springs Core-Resilience (@Retryable, @ConcurrencyLimit)
// für ausgehende Calls (z.B. RabbitMQ) — Spring-7-Variante statt des älteren spring-retry @EnableRetry
@EnableResilientMethods
public class UserManagerApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserManagerApplication.class);

    public static void main(String[] args) {
        LOGGER.info("UserManager-Microservice wird gestartet");
        SpringApplication.run(UserManagerApplication.class, args);
    }
}
