-- Demo-Daten für lokale Entwicklung und H2-Konsole.
-- Wird nur geladen wenn spring.sql.init.mode=always gesetzt ist (nicht in @DataJpaTest).
-- Hibernate erstellt das Schema via ddl-auto=create BEVOR diese Datei ausgeführt wird
-- (gesichert durch defer-datasource-initialization=true in application.yml).
--
-- Demo-Passwort: DemoPasswort1!  (BCrypt-Hash, 10 Runden)
-- Hinweis: id wird von H2 per IDENTITY auto-generiert — nicht angeben.
-- kundenprofil_id bleibt NULL (wird erst beim ersten Pflegen des Profils via PUT /rest/users/me gesetzt).
--
-- WICHTIG: E-Mail-Adressen hier IMMER kleingeschrieben eintragen!
-- Die EmailAdresse-VO normalisiert auf Kleinbuchstaben; findByEmail vergleicht exakt.
-- Eine groß geschriebene Seed-E-Mail (Raw-SQL umgeht die VO) würde von findByEmail nie gefunden.

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'demo@example.test',
    'Demo',
    'Nutzer',
    '$2a$10$x4DZx6CI/3z6JUXHvMKP/OKZLCP.pdjjPvpN.fp/klMYNcHZdTEgy',
    'ACTIVE',
    0
);

-- Demo-Rolle: PROSPECT. Status ACTIVE: demo@ ist ein bereits freigeschaltetes Konto (stabiler
-- Login für Postman/Self-Service-Tests) — anders als ein frisch registriertes Konto, das im
-- Semantic Lock PENDING startet (siehe ausstehend@ unten).
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'PROSPECT'
FROM nutzer
WHERE nutzer_id = '11111111-1111-1111-1111-111111111111';

-- Admin-Konto für die Administration (/rest/admin/**) und die Postman-Systemtests.
-- Gleiches Demo-Passwort DemoPasswort1! — ein BCrypt-Hash verifiziert jeden gültigen Klartext,
-- daher kann derselbe Hash wiederverwendet werden.
INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'admin@example.test',
    'Anna',
    'Admin',
    '$2a$10$x4DZx6CI/3z6JUXHvMKP/OKZLCP.pdjjPvpN.fp/klMYNcHZdTEgy',
    'ACTIVE',
    0
);

-- Admin-Rolle: ADMINISTRATOR (Vollzugriff auf alle Konten)
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'ADMINISTRATOR'
FROM nutzer
WHERE nutzer_id = '22222222-2222-2222-2222-222222222222';

-- Ausstehendes Konto (Semantic Lock): zeigt den Startzustand der Registrierungs-Saga —
-- PENDING + PROSPECT (wie direkt nach registrieren()). Der EventManager bestätigt per
-- user.provisioned (PENDING -> ACTIVE); ersatzweise schaltet ein Administrator es über
-- POST /rest/admin/accounts/{id}/activation frei (PENDING -> ACTIVE, Rolle bleibt PROSPECT).
INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'ausstehend@example.test',
    'Paula',
    'Pending',
    '$2a$10$x4DZx6CI/3z6JUXHvMKP/OKZLCP.pdjjPvpN.fp/klMYNcHZdTEgy',
    'PENDING',
    0
);

INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'PROSPECT'
FROM nutzer
WHERE nutzer_id = '33333333-3333-3333-3333-333333333333';

-- Testkonten — ein Konto pro Rolle (für manuelle Rollen-/Berechtigungstests via Postman/Swagger).
-- Alle ACTIVE (nicht über die Saga registriert, sondern direkt einsatzbereit), Passwort jeweils
-- DemoSecure123! (BCrypt-Hash, 10 Runden, gleicher Hash für alle — ein Hash verifiziert jeden
-- gültigen Klartext erneut). E-Mail-Adressen bewusst klein geschrieben (siehe Hinweis oben):
-- die EmailAdresse-VO normalisiert auf Kleinbuchstaben, findByEmail vergleicht exakt — eine
-- groß geschriebene Seed-E-Mail würde der Login nie finden.

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('44444444-4444-4444-4444-444444444444', 'customer@example.test', 'Carla', 'Customer',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'CUSTOMER' FROM nutzer WHERE nutzer_id = '44444444-4444-4444-4444-444444444444';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('55555555-5555-5555-5555-555555555555', 'prospect@example.test', 'Paul', 'Prospect',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'PROSPECT' FROM nutzer WHERE nutzer_id = '55555555-5555-5555-5555-555555555555';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('66666666-6666-6666-6666-666666666666', 'service_staff@example.test', 'Sven', 'Service',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'SERVICE_STAFF' FROM nutzer WHERE nutzer_id = '66666666-6666-6666-6666-666666666666';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('77777777-7777-7777-7777-777777777777', 'customer_service@example.test', 'Clara', 'Service',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'CUSTOMER_SERVICE' FROM nutzer WHERE nutzer_id = '77777777-7777-7777-7777-777777777777';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('88888888-8888-8888-8888-888888888888', 'administrator@example.test', 'Alex', 'Admin',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'ADMINISTRATOR' FROM nutzer WHERE nutzer_id = '88888888-8888-8888-8888-888888888888';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('99999999-9999-9999-9999-999999999999', 'eventmanager@example.test', 'Eva', 'Event',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'EVENT_MANAGER' FROM nutzer WHERE nutzer_id = '99999999-9999-9999-9999-999999999999';

INSERT INTO nutzer (nutzer_id, email, vorname, nachname, passwort_hash, status, version)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'accountant@example.test', 'Adam', 'Accountant',
        '$2a$10$j1Adx8ukugPKLld5FjJVdeKak8XkF4Zk.IhDCOvP/RGA6mx5TtLmu', 'ACTIVE', 0);
INSERT INTO nutzer_rollen (account_id, rolle)
SELECT id, 'ACCOUNTANT' FROM nutzer WHERE nutzer_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
