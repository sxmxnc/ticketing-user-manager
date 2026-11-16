# Ticketing User Manager

Simon Cebulas Microservice für Identität und Kontoverwaltung aus einem
Ticketing-Teamprojekt. Die eigenständige Demo enthält den User Manager mit
REST-API, H2 und RabbitMQ. Die anderen Services des Teams werden im folgenden
Ablauf durch die administrative Freischaltung ersetzt.

Bei der Registrierung bleibt ein Konto im Zustand `PENDING`, bis die
Registrierung bestätigt oder bei einem endgültigen Fehler kompensiert wird.
Der Dienst verarbeitet die entsprechenden RabbitMQ-Ereignisse. Den Status
eines Kontos und seine Rollen modelliert die Anwendung getrennt. Bei einem
bestätigten Kauf kann aus der Rolle `PROSPECT` die Rolle `CUSTOMER` werden.

## Lokal starten

Voraussetzungen sind JDK 25, OpenSSL und Docker. Der Webserver und der
AMQP-Port sind im folgenden Aufbau nur über Loopback erreichbar.

```sh
./generate-demo-keys.sh
export RABBITMQ_USERNAME=ticketing_demo
export RABBITMQ_PASSWORD="$(openssl rand -hex 24)"
docker run --rm -d --name ticketing-demo-rabbit \
  -e RABBITMQ_DEFAULT_USER="$RABBITMQ_USERNAME" \
  -e RABBITMQ_DEFAULT_PASS="$RABBITMQ_PASSWORD" \
  -p 127.0.0.1:5672:5672 rabbitmq:4
docker exec ticketing-demo-rabbit rabbitmq-diagnostics -q await_startup
./mvnw spring-boot:run
```

Die Swagger-Oberfläche ist unter http://localhost:8080/swagger-ui.html
erreichbar. Die H2-Datenbank wird bei jedem Start neu angelegt.

1. Mit einer fiktiven Adresse wie `person@example.test` ein Konto über
   `POST /rest/users/register` anlegen. Konto-ID und Status stehen in der Antwort.
2. Über `POST /rest/users/login` als `admin@example.test` mit dem lokalen
   Demo-Passwort `DemoPasswort1!` anmelden. Den zurückgegebenen Token in Swagger
   unter **Authorize** eintragen.
3. Das Konto mit `POST /rest/admin/accounts/{userId}/activation` freischalten.
4. Als neue Person anmelden und das eigene Profil über `GET /rest/users/me` abrufen.

Zum Beenden die Anwendung stoppen und `docker stop ticketing-demo-rabbit`
ausführen.

## Tests

Nach der Schlüsselerzeugung mit `./mvnw test` ausführen. Das RSA-Schlüsselpaar
wird für die lokale Kopie erzeugt und von Git ignoriert.
