# Calculateur de sous-réseaux — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** construire une application Spring Boot (pages web Thymeleaf + API REST JSON documentée par Swagger) qui calcule un réseau CIDR, fait un découpage VLSM ou un plan par VLAN, et exporte ce plan en CSV.

**Architecture :** un MVC classique.
- `model/` est du Java pur : adresses IPv4 en `int` 32 bits et calculs binaires, sans aucune dépendance à Spring.
- `service/` orchestre les calculs.
- `controller/` contient un `@Controller` pour les vues Thymeleaf et un `@RestController` pour le JSON. Les deux appellent les mêmes services.
- Les erreurs passent toutes par une seule exception, `InvalidInputException`, qui porte une liste d'`InputError`. Chaque `InputError` a un code stable.

**Tech Stack :** Java 21, Spring Boot 3.5.x (Web, Validation, Thymeleaf), springdoc-openapi 2.8.x, JUnit 5 + AssertJ + MockMvc, JaCoCo, Maven Wrapper.

**Spec :** [`docs/superpowers/specs/2026-10-12-calculateur-sous-reseaux-design.md`](../specs/2026-10-12-calculateur-sous-reseaux-design.md)

**Mode de travail (choix de l'apprenant) :** on construit pas à pas, ensemble. Les étapes marquées **🎓 À toi** se déroulent ainsi :
1. Claude montre le test.
2. L'apprenant écrit l'implémentation.
3. On compare avec l'**implémentation de référence** donnée dans le plan.
4. On garde la version qui fait passer les tests et qui est la plus claire.

Chaque tâche commence par une ligne **Notion**, qui résume ce qu'on y apprend.

## Global Constraints

- Java 21 ; Spring Boot **3.5.x** (dernière version 3.5 publiée sur Maven Central) ; springdoc-openapi-starter-webmvc-ui **2.8.x** ; JaCoCo 0.8.13.
- Package racine `com.coco.subnet` ; groupId `com.coco`, artifactId `subnet-calculator`.
- Aucune librairie réseau : tous les calculs IPv4 se font avec `&`, `|`, `~`, `<<` sur des `int`. Tous les compteurs sont des `long`.
- `model/` n'importe rien de Spring. `service/` dépend uniquement de `model/` (en plus de l'annotation `@Service`).
- Messages utilisateur en **français**, codes d'erreur en **anglais** (`ErrorCode`). Ces codes sont stables, car ils sont utilisés par le JSON et par les tests.
- API : toutes les erreurs sont au format `ProblemDetail` (`application/problem+json`), avec les propriétés `code` et `errors` (toujours présente). Une seule erreur d'entrée donne son code ; plusieurs erreurs donnent `VALIDATION_FAILED`.
- Toutes les erreurs dues à une saisie donnent un **400**, y compris `NETWORK_TOO_SMALL`.
- Jackson strict : `accept-float-as-int=false`, `fail-on-unknown-properties=true`.
- Corps de requête de l'API limité à **64 Ko** (65 536 octets) ; au-delà, 413.
- Noms valides : `^[\p{L}\p{N}][\p{L}\p{N}_-]{0,31}$`.
- ID de VLAN de 1 à 4094 ; 100 lignes au maximum ; 100 VLAN au maximum en mode raccourci ; ID générés : `i × 10`.
- Mode VLAN : un bloc doit contenir `hôtes + 1 + 2` adresses (la passerelle est la première adresse utilisable). Sans VLAN : `hôtes + 2`.
- Hôtes max : 4 294 967 294 sans VLAN, 4 294 967 293 en mode VLAN.
- CSV : séparateur `;`, UTF-8 avec BOM, fins de ligne `\r\n`, fichier `plan-adressage-<réseau>-<préfixe>.csv`.
- Couverture JaCoCo de **100 % des lignes et des branches** sur `com.coco.subnet.model` et `com.coco.subnet.service`, vérifiée par `./mvnw verify`.
- Messages des commits en anglais, sans ligne d'attribution.

## Review Focus

Cinq cas que la spec implique sans les tester explicitement, et qui risquent le plus de gêner un vrai utilisateur. Chacun a son test dans la tâche indiquée :

1. **Élément `null` dans une liste JSON** (`"vlans": [null]`) : on attend un 400 `MISSING_FIELD` sur `vlans[0]`, pas un 500. → test dans la tâche 13.
2. **Chiffres Unicode collés depuis un traitement de texte** (`１９２.168.1.1`, avec des chiffres pleine chasse) : on attend `INVALID_CHARACTERS`, sans conversion silencieuse. → test dans la tâche 3.
3. **Adresses ≥ 128.0.0.0 dans le découpage VLSM** (`200.1.0.0/16`) : le curseur d'allocation ne doit jamais devenir négatif, et les adresses doivent être exactes. → test dans la tâche 7.
4. **Index `removeRow` falsifié ou périmé** (`removeRow-99`, `removeRow-abc`) : la page se réaffiche sans changement, sans 500. → test dans la tâche 18.
5. **Saisie copiée-collée avec un retour à la ligne ou une tabulation** (`"192.168.1.0/24\n"`) : acceptée ; une tabulation **au milieu** est refusée. → test dans la tâche 5.

---

## Structure des fichiers

```
pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/, .gitattributes
src/main/java/com/coco/subnet/
├── SubnetCalculatorApplication.java
├── model/
│   ├── ErrorCode.java             tous les codes d'erreur
│   ├── InputError.java            (code, field, message)
│   ├── InvalidInputException.java liste d'InputError
│   ├── Ipv4Address.java           parse / format / opérations binaires
│   ├── Cidr.java                  parse "a.b.c.d/n", réseau, taille, avertissement de correction
│   ├── SubnetInfo.java            résultat du calcul d'un sous-réseau
│   ├── CidrResult.java            SubnetInfo + avertissements
│   ├── PlanMode.java              VLAN | REQUIREMENTS
│   ├── Requirement.java           (vlanId, name, hosts)
│   ├── AllocatedSubnet.java       besoin + sous-réseau + passerelle + libres
│   └── VlsmPlan.java              plan complet
├── service/
│   ├── SubnetService.java         calcul CIDR
│   ├── VlsmService.java           validation + algorithme VLSM + génération des VLAN
│   └── CsvExporter.java           plan → CSV
├── dto/
│   ├── SubnetResponse.java, SubnetEntry.java, VlsmResponse.java
│   ├── VlsmRequest.java, RequirementRequest.java, VlanRequest.java
│   └── CidrForm.java, VlsmForm.java, RowForm.java
├── controller/
│   ├── ApiController.java         /api/**
│   ├── ApiExceptionHandler.java   erreurs survenues dans ApiController
│   ├── RoutingErrorHandler.java   404/405/406/415 (avant le choix du controller)
│   ├── ApiProblems.java           fabrique de ProblemDetail + écriture brute
│   ├── ValidationErrors.java      erreurs @Valid → InputError
│   ├── PayloadTooLargeException.java
│   └── WebController.java         pages Thymeleaf
└── config/
    ├── OpenApiConfig.java
    ├── RequestSizeFilter.java
    └── LimitedServletInputStream.java
src/main/resources/
├── application.properties, messages.properties
├── templates/fragments/layout.html, index.html, vlsm.html, error/404.html, error/500.html
└── static/css/style.css
src/test/java/com/coco/subnet/
├── TestErrors.java                aide de test : extraire les InputError d'un appel
├── SubnetCalculatorApplicationTests.java, ApplicationSmokeTest.java, WebErrorPageTest.java
├── model/      InvalidInputExceptionTest, Ipv4AddressParseTest, Ipv4AddressBitsTest, CidrTest, SubnetInfoTest
├── service/    SubnetServiceTest, VlsmServiceAllocationTest, VlsmServiceValidationTest,
│               VlsmServiceGlobalChecksTest, VlsmServiceGenerationTest, CsvExporterTest
├── controller/ ApiSubnetEndpointTest, ApiVlsmEndpointTest, ApiCsvEndpointTest, ApiRoutingErrorsTest,
│               ApiUnexpectedErrorTest, ApiExceptionHandlerTest, WebCidrPageTest, WebVlsmPageTest, WebCsvAndRoutingTest
└── config/     RequestSizeFilterTest
```

> **Écart avec la spec :** la spec nommait `FieldError` ce qui s'appelle ici `InputError`, pour éviter le conflit avec `org.springframework.validation.FieldError`. La spec a été mise à jour en conséquence.

---

### Task 1 : Squelette du projet Spring Boot

**Notion :** structure d'un projet Maven/Spring Boot, rôle du wrapper `mvnw`, du `pom.xml` et de `application.properties`.

**Files :**
- Create : `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitattributes`
- Create : `src/main/java/com/coco/subnet/SubnetCalculatorApplication.java`
- Create : `src/main/resources/application.properties`
- Test : `src/test/java/com/coco/subnet/SubnetCalculatorApplicationTests.java`

**Interfaces :**
- Produces : le projet compile et `./mvnw verify` passe ; la classe `com.coco.subnet.SubnetCalculatorApplication` existe.

- [ ] **Step 1 : Générer le squelette sur start.spring.io** (PowerShell, depuis la racine du projet)

```powershell
$tmp = Join-Path $env:TEMP "subnet-starter"
New-Item -ItemType Directory -Force $tmp | Out-Null
Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?type=maven-project&language=java&groupId=com.coco&artifactId=subnet-calculator&name=subnet-calculator&packageName=com.coco.subnet&javaVersion=21&baseDir=subnet-calculator&dependencies=web,validation,thymeleaf" -OutFile "$tmp\starter.zip"
Expand-Archive "$tmp\starter.zip" -DestinationPath "$tmp\out" -Force
$src = "$tmp\out\subnet-calculator"
Copy-Item "$src\mvnw", "$src\mvnw.cmd", "$src\.gitattributes" -Destination .
Copy-Item "$src\.mvn" -Destination . -Recurse
Copy-Item "$src\src" -Destination . -Recurse
```

Le `pom.xml` généré n'est **pas** copié : on écrit le nôtre à l'étape 3. Le fichier `.gitattributes` généré force les fins de ligne LF pour `mvnw`, ce qui évite de casser le script sous Windows.

- [ ] **Step 2 : Trouver les versions exactes**

```bash
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml | grep -o '<version>3\.5\.[0-9]*</version>' | tail -1
curl -s https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml | grep -o '<version>2\.8\.[0-9]*</version>' | tail -1
```

Résultat attendu : deux lignes, par exemple `<version>3.5.7</version>` et `<version>2.8.13</version>`. Reporte ces deux versions dans le `pom.xml` de l'étape 3, à la place de `3.5.7` et `2.8.9`.

- [ ] **Step 3 : Écrire `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.coco</groupId>
    <artifactId>subnet-calculator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>subnet-calculator</name>
    <description>Calculateur de sous-réseaux CIDR / VLSM / VLAN</description>

    <properties>
        <java.version>21</java.version>
        <springdoc.version>2.8.9</springdoc.version>
        <jacoco.version>0.8.13</jacoco.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals><goal>check</goal></goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>PACKAGE</element>
                                    <includes>
                                        <include>com.coco.subnet.model</include>
                                        <include>com.coco.subnet.service</include>
                                    </includes>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>1.00</minimum>
                                        </limit>
                                        <limit>
                                            <counter>BRANCH</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>1.00</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4 : Écrire `src/main/resources/application.properties`** (remplace le fichier généré)

```properties
spring.application.name=subnet-calculator

# JSON strict (spec §5.1) : 2.5 n'est pas un entier, un champ inconnu est une erreur
spring.jackson.deserialization.accept-float-as-int=false
spring.jackson.deserialization.fail-on-unknown-properties=true

# Swagger ne documente que l'API
springdoc.paths-to-match=/api/**

# Jamais de stack trace dans une réponse
server.error.include-stacktrace=never
```

- [ ] **Step 5 : Vérifier les fichiers générés**

`src/main/java/com/coco/subnet/SubnetCalculatorApplication.java` doit contenir :

```java
package com.coco.subnet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SubnetCalculatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubnetCalculatorApplication.class, args);
    }
}
```

`src/test/java/com/coco/subnet/SubnetCalculatorApplicationTests.java` doit contenir :

```java
package com.coco.subnet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SubnetCalculatorApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6 : Lancer le build**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. Le premier lancement télécharge Maven et les dépendances, il peut donc durer quelques minutes.

Si Git Bash échoue à cause des espaces dans le chemin du projet, lance la même commande avec `cmd //c mvnw.cmd verify`.

- [ ] **Step 7 : Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn .gitattributes src
git commit -m "chore: scaffold Spring Boot project with springdoc and JaCoCo"
```

---

### Task 2 : Codes d'erreur et exception métier

**Notion :** `enum`, `record`, exception non vérifiée (`RuntimeException`), copie défensive avec `List.copyOf`.

**Files :**
- Create : `src/main/java/com/coco/subnet/model/ErrorCode.java`
- Create : `src/main/java/com/coco/subnet/model/InputError.java`
- Create : `src/main/java/com/coco/subnet/model/InvalidInputException.java`
- Create : `src/test/java/com/coco/subnet/TestErrors.java`
- Test : `src/test/java/com/coco/subnet/model/InvalidInputExceptionTest.java`

**Interfaces :**
- Produces :
  - `enum ErrorCode { ... }` (liste complète ci-dessous) ;
  - `record InputError(ErrorCode code, String field, String message)` ;
  - `class InvalidInputException extends RuntimeException` avec :
    - `InvalidInputException(List<InputError> errors)` ;
    - `static InvalidInputException of(ErrorCode, String field, String message)` ;
    - `List<InputError> errors()` ;
  - aide de test `TestErrors.firstError(Executable) : InputError` et `TestErrors.errors(Executable) : List<InputError>`.

- [ ] **Step 1 : Écrire l'aide de test** `src/test/java/com/coco/subnet/TestErrors.java`

```java
package com.coco.subnet;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exécute un appel qui doit échouer et renvoie les erreurs portées par l'exception. */
public final class TestErrors {

    private TestErrors() {
    }

    public static List<InputError> errors(Executable call) {
        return assertThrows(InvalidInputException.class, call).errors();
    }

    public static InputError firstError(Executable call) {
        return errors(call).get(0);
    }
}
```

- [ ] **Step 2 : Écrire le test qui échoue** `src/test/java/com/coco/subnet/model/InvalidInputExceptionTest.java`

```java
package com.coco.subnet.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvalidInputExceptionTest {

    @Test
    void ofCreatesASingleError() {
        InvalidInputException ex = InvalidInputException.of(ErrorCode.EMPTY_INPUT, "cidr", "L'adresse est obligatoire");

        assertThat(ex.errors()).containsExactly(new InputError(ErrorCode.EMPTY_INPUT, "cidr", "L'adresse est obligatoire"));
        assertThat(ex.getMessage()).isEqualTo("L'adresse est obligatoire");
    }

    @Test
    void keepsAnImmutableCopyOfTheErrors() {
        List<InputError> errors = new ArrayList<>(List.of(
                new InputError(ErrorCode.EMPTY_NAME, "requirements[0].name", "premier"),
                new InputError(ErrorCode.HOSTS_NOT_POSITIVE, "requirements[0].hosts", "second")));

        InvalidInputException ex = new InvalidInputException(errors);
        errors.clear();

        assertThat(ex.errors()).hasSize(2);
        assertThat(ex.getMessage()).isEqualTo("premier");
        assertThatThrownBy(() -> ex.errors().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesAnEmptyOrNullList() {
        assertThatThrownBy(() -> new InvalidInputException(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvalidInputException(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=InvalidInputExceptionTest`
Expected : échec de compilation, `cannot find symbol: class InvalidInputException`.

- [ ] **Step 4 : Écrire `ErrorCode.java`**

```java
package com.coco.subnet.model;

/** Codes d'erreur stables, utilisés dans le JSON de l'API et dans les tests. */
public enum ErrorCode {
    // Adresse IPv4
    EMPTY_INPUT, INVALID_CHARACTERS, EMPTY_OCTET, WRONG_OCTET_COUNT, LEADING_ZERO, OCTET_OUT_OF_RANGE,
    // Notation CIDR
    MISSING_PREFIX, MULTIPLE_SLASHES, INVALID_PREFIX, PREFIX_OUT_OF_RANGE,
    // Plan VLSM / VLAN
    NO_REQUIREMENTS, TOO_MANY_REQUIREMENTS, HOSTS_NOT_POSITIVE, HOSTS_TOO_LARGE, VLAN_ID_OUT_OF_RANGE,
    DUPLICATE_VLAN_ID, DUPLICATE_NAME, EMPTY_NAME, INVALID_NAME, VLAN_COUNT_OUT_OF_RANGE,
    REQUIREMENT_TOO_LARGE, NETWORK_TOO_SMALL, PARENT_NOT_SPLITTABLE, MISSING_FIELD,
    // Requête HTTP (API)
    AMBIGUOUS_REQUEST, VALIDATION_FAILED, MALFORMED_JSON, TYPE_MISMATCH, UNKNOWN_FIELD,
    MISSING_PARAMETER, MISSING_BODY, NOT_FOUND, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE,
    NOT_ACCEPTABLE, PAYLOAD_TOO_LARGE, INTERNAL_ERROR
}
```

- [ ] **Step 5 : Écrire `InputError.java`**

```java
package com.coco.subnet.model;

/** Une erreur de saisie : quel code, sur quel champ, avec quel message pour l'utilisateur. */
public record InputError(ErrorCode code, String field, String message) {
}
```

- [ ] **Step 6 : Écrire `InvalidInputException.java`**

```java
package com.coco.subnet.model;

import java.util.List;

/** Seule exception métier du projet : elle porte une ou plusieurs erreurs de saisie. */
public class InvalidInputException extends RuntimeException {

    private final List<InputError> errors;

    public InvalidInputException(List<InputError> errors) {
        super(firstMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public static InvalidInputException of(ErrorCode code, String field, String message) {
        return new InvalidInputException(List.of(new InputError(code, field, message)));
    }

    public List<InputError> errors() {
        return errors;
    }

    private static String firstMessage(List<InputError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Au moins une erreur est requise");
        }
        return errors.get(0).message();
    }
}
```

- [ ] **Step 7 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`, 4 tests au vert, aucune alerte JaCoCo.

- [ ] **Step 8 : Commit**

```bash
git add src
git commit -m "feat: add error codes and InvalidInputException"
```

---

### Task 3 : Lire et afficher une adresse IPv4

**Notion :** une adresse IPv4 n'est qu'un nombre de 32 bits, on construit sa valeur octet par octet avec `(value << 8) | octet`. On apprend aussi à valider une saisie dans un ordre précis, et on voit le piège de `split` en Java.

**Files :**
- Create : `src/main/java/com/coco/subnet/model/Ipv4Address.java`
- Test : `src/test/java/com/coco/subnet/model/Ipv4AddressParseTest.java`

**Interfaces :**
- Consumes : `ErrorCode`, `InvalidInputException.of(...)` (tâche 2).
- Produces : `record Ipv4Address(int value)` avec :
  - `static Ipv4Address parse(String input, String field)`, qui retire les espaces autour de la saisie ;
  - `static Ipv4Address parseStrict(String text, String field)` (visibilité paquet), qui ne retire rien. `Cidr` l'utilise, pour qu'un espace avant le `/` reste une erreur ;
  - `String toString()` (format `a.b.c.d`).

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class Ipv4AddressParseTest {

    private static Ipv4Address parse(String text) {
        return Ipv4Address.parse(text, "address");
    }

    @Test
    void parsesDottedDecimalIntoA32BitValue() {
        Ipv4Address address = parse("192.168.10.1");

        assertThat(address.value()).isEqualTo(0xC0A80A01);
        assertThat(address).hasToString("192.168.10.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.0.0.1", "127.0.0.1", "200.1.1.1", "255.255.255.255"})
    void formatsBackToTheSameText(String text) {
        assertThat(parse(text)).hasToString(text);
    }

    @Test
    void highAddressesAreNegativeAsSignedInt() {
        assertThat(parse("255.255.255.255").value()).isEqualTo(-1);
        assertThat(parse("200.1.1.1").value()).isNegative();
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(parse("  10.0.0.1 \n")).hasToString("10.0.0.1");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT",
            "'   '|EMPTY_INPUT",
            "192.168.a.1|INVALID_CHARACTERS",
            "1,2,3,4|INVALID_CHARACTERS",
            "1.2.3.-4|INVALID_CHARACTERS",
            "192.168. 1.1|INVALID_CHARACTERS",
            "192.168.1.\t1|INVALID_CHARACTERS",
            "１９２.168.1.1|INVALID_CHARACTERS",
            "192..1.1|EMPTY_OCTET",
            ".1.2.3|EMPTY_OCTET",
            "1.2.3.4.|EMPTY_OCTET",
            "192.168.1|WRONG_OCTET_COUNT",
            "1.2.3.4.5|WRONG_OCTET_COUNT",
            "01.2.3.4|LEADING_ZERO",
            "1.2.3.00|LEADING_ZERO",
            "300.01.1.1|LEADING_ZERO",
            "300.1.1.1|OCTET_OUT_OF_RANGE",
            "1.2.3.256|OCTET_OUT_OF_RANGE",
            "1.2.3.99999999999|OCTET_OUT_OF_RANGE"
    })
    void rejectsInvalidInputWithTheExpectedCode(String input, ErrorCode expected) {
        assertThat(firstError(() -> parse(input)).code()).isEqualTo(expected);
    }

    @Test
    void nullIsAnEmptyInput() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }

    @Test
    void messagesAreInFrench() {
        assertThat(firstError(() -> parse("")).message()).isEqualTo("L'adresse est obligatoire");
        assertThat(firstError(() -> parse("192.168.a.1")).message()).isEqualTo("Caractère non autorisé : 'a'");
        assertThat(firstError(() -> parse("192..1.1")).message()).isEqualTo("Octet vide en position 2");
        assertThat(firstError(() -> parse("1.2.3.4.")).message()).isEqualTo("Octet vide en position 5");
        assertThat(firstError(() -> parse("192.168.1")).message()).isEqualTo("Une adresse IPv4 contient 4 octets (3 trouvés)");
        assertThat(firstError(() -> parse("01.2.3.4")).message()).isEqualTo("Zéro initial interdit dans '01' (ambigu : octal)");
        assertThat(firstError(() -> parse("300.1.1.1")).message()).isEqualTo("L'octet 300 est hors limites (0–255)");
    }

    @Test
    void errorCarriesTheFieldName() {
        assertThat(firstError(() -> Ipv4Address.parse("x", "network")).field()).isEqualTo("network");
    }

    @Test
    void strictParsingDoesNotTrim() {
        assertThat(firstError(() -> Ipv4Address.parseStrict(" 10.0.0.1", "address")).code()).isEqualTo(ErrorCode.INVALID_CHARACTERS);
        assertThat(firstError(() -> Ipv4Address.parseStrict("", "address")).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
        assertThat(Ipv4Address.parseStrict("10.0.0.1", "address")).hasToString("10.0.0.1");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=Ipv4AddressParseTest`
Expected : échec de compilation, `cannot find symbol: class Ipv4Address`.

- [ ] **Step 3 : 🎓 À toi — écrire `Ipv4Address.parse` et `toString`**

Indices :
- Ordre des vérifications : vide → caractères (uniquement `0`-`9` ASCII et `.`) → octet vide → nombre d'octets → zéro initial → valeur.
- Découpe avec `split("\\.", -1)`, sans oublier le `-1`.
- Ne jamais appeler `Integer.parseInt` sur plus de 3 chiffres.
- Pour l'affichage, utilise `>>>` (et non `>>`) suivi d'un masque `& 0xFF`.

Implémentation de référence :

```java
package com.coco.subnet.model;

import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.EMPTY_OCTET;
import static com.coco.subnet.model.ErrorCode.INVALID_CHARACTERS;
import static com.coco.subnet.model.ErrorCode.LEADING_ZERO;
import static com.coco.subnet.model.ErrorCode.OCTET_OUT_OF_RANGE;
import static com.coco.subnet.model.ErrorCode.WRONG_OCTET_COUNT;

/** Une adresse IPv4 stockée dans un int de 32 bits (signé : 128.0.0.0 et plus sont négatifs). */
public record Ipv4Address(int value) {

    public static Ipv4Address parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        return parseStrict(input.strip(), field);
    }

    /** Sans retirer les espaces : utilisé par Cidr, qui a déjà nettoyé la saisie complète. */
    static Ipv4Address parseStrict(String text, String field) {
        if (text.isEmpty()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        for (char c : text.toCharArray()) {
            if ((c < '0' || c > '9') && c != '.') {
                throw InvalidInputException.of(INVALID_CHARACTERS, field, "Caractère non autorisé : '" + c + "'");
            }
        }
        // -1 : garder les morceaux vides en fin de chaîne ("1.2.3.4." → 5 morceaux)
        String[] parts = text.split("\\.", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                throw InvalidInputException.of(EMPTY_OCTET, field, "Octet vide en position " + (i + 1));
            }
        }
        if (parts.length != 4) {
            throw InvalidInputException.of(WRONG_OCTET_COUNT, field,
                    "Une adresse IPv4 contient 4 octets (" + parts.length + " trouvés)");
        }
        for (String part : parts) {
            if (part.length() > 1 && part.charAt(0) == '0') {
                throw InvalidInputException.of(LEADING_ZERO, field,
                        "Zéro initial interdit dans '" + part + "' (ambigu : octal)");
            }
        }
        int value = 0;
        for (String part : parts) {
            // plus de 3 chiffres : hors limites, sans risquer un dépassement dans parseInt
            if (part.length() > 3 || Integer.parseInt(part) > 255) {
                throw InvalidInputException.of(OCTET_OUT_OF_RANGE, field,
                        "L'octet " + part + " est hors limites (0–255)");
            }
            value = (value << 8) | Integer.parseInt(part);
        }
        return new Ipv4Address(value);
    }

    @Override
    public String toString() {
        return ((value >>> 24) & 0xFF) + "." + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "." + (value & 0xFF);
    }
}
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`, tous les tests de `Ipv4AddressParseTest` au vert, couverture à 100 %.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: parse and format IPv4 addresses"
```

---

### Task 4 : Opérations binaires sur les adresses

**Notion :** le masque `-1 << (32 - n)`, et le piège `-1 << 32 == -1` (en Java, le décalage se fait modulo 32). Ensuite le ET (`&`), le OU (`|`) et le NON (`~`), et enfin `Integer.toUnsignedLong` pour compter des adresses sans tomber dans les nombres négatifs.

**Files :**
- Modify : `src/main/java/com/coco/subnet/model/Ipv4Address.java`
- Test : `src/test/java/com/coco/subnet/model/Ipv4AddressBitsTest.java`

**Interfaces :**
- Produces (dans `Ipv4Address`) :
  - `static Ipv4Address maskOf(int prefix)` (`IllegalArgumentException` si `prefix` est hors de 0..32) ;
  - `Ipv4Address and(Ipv4Address)`, `Ipv4Address or(Ipv4Address)`, `Ipv4Address not()` ;
  - `long toUnsignedLong()` ;
  - `Ipv4Address plus(long offset)`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv4AddressBitsTest {

    private static Ipv4Address parse(String text) {
        return Ipv4Address.parse(text, "address");
    }

    @ParameterizedTest(name = "/{0} -> {1}")
    @CsvSource({
            "0,0.0.0.0", "1,128.0.0.0", "8,255.0.0.0", "16,255.255.0.0", "24,255.255.255.0",
            "25,255.255.255.128", "30,255.255.255.252", "31,255.255.255.254", "32,255.255.255.255"
    })
    void maskOfPrefix(int prefix, String mask) {
        assertThat(Ipv4Address.maskOf(prefix)).hasToString(mask);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 33})
    void maskOfRejectsAnInvalidPrefix(int prefix) {
        assertThatThrownBy(() -> Ipv4Address.maskOf(prefix)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void andOrNotComputeNetworkAndBroadcast() {
        Ipv4Address ip = parse("192.168.10.37");
        Ipv4Address mask = parse("255.255.255.0");

        assertThat(ip.and(mask)).hasToString("192.168.10.0");
        assertThat(mask.not()).hasToString("0.0.0.255");
        assertThat(ip.and(mask).or(mask.not())).hasToString("192.168.10.255");
    }

    @Test
    void toUnsignedLongIsNeverNegative() {
        assertThat(parse("200.1.1.1").toUnsignedLong()).isEqualTo(3_355_508_993L);
        assertThat(parse("255.255.255.255").toUnsignedLong()).isEqualTo(4_294_967_295L);
    }

    @Test
    void plusMovesAcrossOctetsAndTheSignBit() {
        assertThat(parse("192.168.10.255").plus(1)).hasToString("192.168.11.0");
        assertThat(parse("127.255.255.255").plus(1)).hasToString("128.0.0.0");
        assertThat(parse("255.255.255.255").plus(-1)).hasToString("255.255.255.254");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=Ipv4AddressBitsTest`
Expected : échec de compilation, `cannot find symbol: method maskOf(int)`.

- [ ] **Step 3 : 🎓 À toi — ajouter ces méthodes dans `Ipv4Address`** (après `parse`, avant `toString`)

Implémentation de référence :

```java
    /** Masque de sous-réseau pour un préfixe /0 à /32. */
    public static Ipv4Address maskOf(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Préfixe hors limites : " + prefix);
        }
        // Java décale modulo 32 : -1 << 32 vaut -1, d'où le cas particulier du /0
        return new Ipv4Address(prefix == 0 ? 0 : -1 << (32 - prefix));
    }

    public Ipv4Address and(Ipv4Address other) {
        return new Ipv4Address(value & other.value);
    }

    public Ipv4Address or(Ipv4Address other) {
        return new Ipv4Address(value | other.value);
    }

    public Ipv4Address not() {
        return new Ipv4Address(~value);
    }

    /** Valeur entre 0 et 4 294 967 295, pour compter et comparer sans nombres négatifs. */
    public long toUnsignedLong() {
        return Integer.toUnsignedLong(value);
    }

    public Ipv4Address plus(long offset) {
        return new Ipv4Address((int) (toUnsignedLong() + offset));
    }
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: add bitwise operations on IPv4 addresses"
```

---

### Task 5 : La notation CIDR

**Notion :** découper `a.b.c.d/n`, valider le préfixe avec une expression régulière, puis vérifier dans un constructeur compact de record que l'objet reste toujours valide.

**Files :**
- Create : `src/main/java/com/coco/subnet/model/Cidr.java`
- Test : `src/test/java/com/coco/subnet/model/CidrTest.java`

**Interfaces :**
- Consumes : `Ipv4Address.parseStrict`, `maskOf`, `and` (tâches 3 et 4).
- Produces : `record Cidr(Ipv4Address address, int prefix)` avec :
  - `static Cidr parse(String input, String field)` ;
  - `Ipv4Address mask()`, `Ipv4Address networkAddress()` ;
  - `boolean isNetworkAddress()`, `Cidr toNetwork()` ;
  - `long size()` ;
  - `Optional<String> correctionWarning()` ;
  - `String toString()` (format `a.b.c.d/n`).

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CidrTest {

    private static Cidr parse(String text) {
        return Cidr.parse(text, "cidr");
    }

    @Test
    void parsesAddressAndPrefix() {
        Cidr cidr = parse("192.168.10.0/24");

        assertThat(cidr.address()).hasToString("192.168.10.0");
        assertThat(cidr.prefix()).isEqualTo(24);
        assertThat(cidr).hasToString("192.168.10.0/24");
    }

    @Test
    void acceptsCopyPastedTrailingWhitespace() {
        assertThat(parse("192.168.1.0/24\n")).hasToString("192.168.1.0/24");
        assertThat(parse("\t10.0.0.0/8  ")).hasToString("10.0.0.0/8");
    }

    @Test
    void toleratesALeadingZeroInThePrefix() {
        assertThat(parse("10.0.0.0/024").prefix()).isEqualTo(24);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT",
            "/24|EMPTY_INPUT",
            "192.168.1.0|MISSING_PREFIX",
            "abc|MISSING_PREFIX",
            "1.2.3.4/24/8|MULTIPLE_SLASHES",
            "1.2.3.4/abc|INVALID_PREFIX",
            "1.2.3.4/|INVALID_PREFIX",
            "1.2.3.4/2.5|INVALID_PREFIX",
            "1.2.3.4/-1|INVALID_PREFIX",
            "1.2.3.4/+8|INVALID_PREFIX",
            "1.2.3.4/0024|INVALID_PREFIX",
            "192.168.1.0/2 4|INVALID_PREFIX",
            "1.2.3.4/33|PREFIX_OUT_OF_RANGE",
            "1.2.3.4/999|PREFIX_OUT_OF_RANGE",
            "300.1.1.1/24|OCTET_OUT_OF_RANGE",
            "1.2.3.4 /24|INVALID_CHARACTERS",
            "192.168.1.0\t/24|INVALID_CHARACTERS"
    })
    void rejectsInvalidCidr(String input, ErrorCode expected) {
        assertThat(firstError(() -> parse(input)).code()).isEqualTo(expected);
    }

    @Test
    void nullIsAnEmptyInput() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }

    @Test
    void messagesAreInFrench() {
        assertThat(firstError(() -> parse("192.168.1.0")).message()).isEqualTo("Préfixe manquant : format attendu a.b.c.d/n");
        assertThat(firstError(() -> parse("1.2.3.4/24/8")).message()).isEqualTo("Un seul '/' autorisé");
        assertThat(firstError(() -> parse("1.2.3.4/abc")).message()).isEqualTo("Le préfixe doit être un nombre entier positif");
        assertThat(firstError(() -> parse("1.2.3.4/33")).message()).isEqualTo("Le préfixe doit être compris entre 0 et 32");
    }

    @Test
    void computesTheNetworkOfAHostAddress() {
        Cidr cidr = parse("192.168.10.37/24");

        assertThat(cidr.mask()).hasToString("255.255.255.0");
        assertThat(cidr.networkAddress()).hasToString("192.168.10.0");
        assertThat(cidr.isNetworkAddress()).isFalse();
        assertThat(cidr.toNetwork()).hasToString("192.168.10.0/24");
        assertThat(cidr.correctionWarning()).contains("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void aNetworkAddressNeedsNoCorrection() {
        Cidr cidr = parse("192.168.10.0/24");

        assertThat(cidr.isNetworkAddress()).isTrue();
        assertThat(cidr.correctionWarning()).isEmpty();
    }

    @Test
    void sizeIsALong() {
        assertThat(parse("0.0.0.0/0").size()).isEqualTo(4_294_967_296L);
        assertThat(parse("10.0.0.0/8").size()).isEqualTo(16_777_216L);
        assertThat(parse("10.0.0.1/32").size()).isEqualTo(1L);
    }

    @Test
    void constructorRejectsAnImpossiblePrefix() {
        Ipv4Address address = Ipv4Address.parse("10.0.0.0", "a");
        assertThatThrownBy(() -> new Cidr(address, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cidr(address, 33)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cidr(null, 24)).isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=CidrTest`
Expected : échec de compilation, `cannot find symbol: class Cidr`.

- [ ] **Step 3 : Écrire `Cidr.java`**

```java
package com.coco.subnet.model;

import java.util.Objects;
import java.util.Optional;

import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.INVALID_PREFIX;
import static com.coco.subnet.model.ErrorCode.MISSING_PREFIX;
import static com.coco.subnet.model.ErrorCode.MULTIPLE_SLASHES;
import static com.coco.subnet.model.ErrorCode.PREFIX_OUT_OF_RANGE;

/** Une adresse et un préfixe, par exemple 192.168.10.0/24. */
public record Cidr(Ipv4Address address, int prefix) {

    public Cidr {
        Objects.requireNonNull(address, "address");
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Préfixe hors limites : " + prefix);
        }
    }

    /** Ordre des vérifications : vide → nombre de '/' → adresse → préfixe. */
    public static Cidr parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        String text = input.strip();
        int slash = text.indexOf('/');
        if (slash < 0) {
            throw InvalidInputException.of(MISSING_PREFIX, field, "Préfixe manquant : format attendu a.b.c.d/n");
        }
        if (text.indexOf('/', slash + 1) >= 0) {
            throw InvalidInputException.of(MULTIPLE_SLASHES, field, "Un seul '/' autorisé");
        }
        // parseStrict : un espace avant le '/' ("1.2.3.4 /24") reste interdit
        Ipv4Address address = Ipv4Address.parseStrict(text.substring(0, slash), field);
        String prefixText = text.substring(slash + 1);
        if (!prefixText.matches("[0-9]{1,3}")) {
            throw InvalidInputException.of(INVALID_PREFIX, field, "Le préfixe doit être un nombre entier positif");
        }
        int prefix = Integer.parseInt(prefixText);
        if (prefix > 32) {
            throw InvalidInputException.of(PREFIX_OUT_OF_RANGE, field, "Le préfixe doit être compris entre 0 et 32");
        }
        return new Cidr(address, prefix);
    }

    public Ipv4Address mask() {
        return Ipv4Address.maskOf(prefix);
    }

    public Ipv4Address networkAddress() {
        return address.and(mask());
    }

    public boolean isNetworkAddress() {
        return address.equals(networkAddress());
    }

    public Cidr toNetwork() {
        return new Cidr(networkAddress(), prefix);
    }

    /** Nombre total d'adresses du bloc (un /0 en contient 2^32, d'où le long). */
    public long size() {
        return 1L << (32 - prefix);
    }

    public Optional<String> correctionWarning() {
        return isNetworkAddress()
                ? Optional.empty()
                : Optional.of("Adresse corrigée vers le réseau " + toNetwork());
    }

    @Override
    public String toString() {
        return address + "/" + prefix;
    }
}
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: parse CIDR notation and compute network address"
```

---

### Task 6 : Calcul d'un sous-réseau et `SubnetService`

**Notion :** à partir du réseau et du masque, on obtient le wildcard (`~masque`) puis le broadcast (`réseau | wildcard`). On traite aussi les cas particuliers /31 et /32. Côté Spring, on voit un premier `@Service`.

**Files :**
- Create : `src/main/java/com/coco/subnet/model/SubnetInfo.java`
- Create : `src/main/java/com/coco/subnet/model/CidrResult.java`
- Create : `src/main/java/com/coco/subnet/service/SubnetService.java`
- Test : `src/test/java/com/coco/subnet/model/SubnetInfoTest.java`
- Test : `src/test/java/com/coco/subnet/service/SubnetServiceTest.java`

**Interfaces :**
- Consumes : `Cidr` (tâche 5).
- Produces :
  - `record SubnetInfo(Cidr cidr, Ipv4Address networkAddress, Ipv4Address subnetMask, Ipv4Address wildcardMask, Ipv4Address broadcastAddress, Ipv4Address firstUsable, Ipv4Address lastUsable, long usableHosts, long totalAddresses)` avec `static SubnetInfo of(Cidr)`. Le `cidr` renvoyé est toujours celui du réseau.
  - `record CidrResult(SubnetInfo subnet, List<String> warnings)`.
  - `@Service SubnetService` avec `CidrResult calculate(String cidrText)`, qui utilise le champ `"cidr"` dans ses erreurs.

- [ ] **Step 1 : Écrire le test `SubnetInfoTest` qui échoue**

```java
package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubnetInfoTest {

    private static SubnetInfo of(String cidr) {
        return SubnetInfo.of(Cidr.parse(cidr, "cidr"));
    }

    @Test
    void referenceExample() {
        SubnetInfo info = of("192.168.10.0/24");

        assertThat(info.cidr()).hasToString("192.168.10.0/24");
        assertThat(info.networkAddress()).hasToString("192.168.10.0");
        assertThat(info.subnetMask()).hasToString("255.255.255.0");
        assertThat(info.wildcardMask()).hasToString("0.0.0.255");
        assertThat(info.broadcastAddress()).hasToString("192.168.10.255");
        assertThat(info.firstUsable()).hasToString("192.168.10.1");
        assertThat(info.lastUsable()).hasToString("192.168.10.254");
        assertThat(info.usableHosts()).isEqualTo(254);
        assertThat(info.totalAddresses()).isEqualTo(256);
    }

    @ParameterizedTest(name = "/{0} -> {1}, {2} hôtes")
    @CsvSource({
            "0,0.0.0.0,4294967294", "1,128.0.0.0,2147483646", "2,192.0.0.0,1073741822",
            "3,224.0.0.0,536870910", "4,240.0.0.0,268435454", "5,248.0.0.0,134217726",
            "6,252.0.0.0,67108862", "7,254.0.0.0,33554430", "8,255.0.0.0,16777214",
            "9,255.128.0.0,8388606", "10,255.192.0.0,4194302", "11,255.224.0.0,2097150",
            "12,255.240.0.0,1048574", "13,255.248.0.0,524286", "14,255.252.0.0,262142",
            "15,255.254.0.0,131070", "16,255.255.0.0,65534", "17,255.255.128.0,32766",
            "18,255.255.192.0,16382", "19,255.255.224.0,8190", "20,255.255.240.0,4094",
            "21,255.255.248.0,2046", "22,255.255.252.0,1022", "23,255.255.254.0,510",
            "24,255.255.255.0,254", "25,255.255.255.128,126", "26,255.255.255.192,62",
            "27,255.255.255.224,30", "28,255.255.255.240,14", "29,255.255.255.248,6",
            "30,255.255.255.252,2", "31,255.255.255.254,2", "32,255.255.255.255,1"
    })
    void everyPrefix(int prefix, String mask, long usableHosts) {
        SubnetInfo info = of("10.0.0.0/" + prefix);

        assertThat(info.subnetMask()).hasToString(mask);
        assertThat(info.usableHosts()).isEqualTo(usableHosts);
        assertThat(info.totalAddresses()).isEqualTo(1L << (32 - prefix));
    }

    @Test
    void wholeInternet() {
        SubnetInfo info = of("0.0.0.0/0");

        assertThat(info.broadcastAddress()).hasToString("255.255.255.255");
        assertThat(info.firstUsable()).hasToString("0.0.0.1");
        assertThat(info.lastUsable()).hasToString("255.255.255.254");
        assertThat(info.totalAddresses()).isEqualTo(4_294_967_296L);
    }

    @Test
    void slash32IsASingleHost() {
        SubnetInfo info = of("255.255.255.255/32");

        assertThat(info.networkAddress()).hasToString("255.255.255.255");
        assertThat(info.broadcastAddress()).hasToString("255.255.255.255");
        assertThat(info.firstUsable()).hasToString("255.255.255.255");
        assertThat(info.lastUsable()).hasToString("255.255.255.255");
        assertThat(info.usableHosts()).isEqualTo(1);
    }

    @Test
    void slash31IsAPointToPointLink() {
        SubnetInfo info = of("10.0.0.0/31");

        assertThat(info.firstUsable()).hasToString("10.0.0.0");
        assertThat(info.lastUsable()).hasToString("10.0.0.1");
        assertThat(info.usableHosts()).isEqualTo(2);
    }

    @Test
    void hostAddressIsCorrectedToItsNetwork() {
        SubnetInfo info = of("192.168.10.37/24");

        assertThat(info.cidr()).hasToString("192.168.10.0/24");
        assertThat(info.networkAddress()).hasToString("192.168.10.0");
    }

    @Test
    void highAddressesSurviveTheSignedIntTrap() {
        SubnetInfo info = of("200.1.1.1/16");

        assertThat(info.networkAddress()).hasToString("200.1.0.0");
        assertThat(info.broadcastAddress()).hasToString("200.1.255.255");
        assertThat(info.lastUsable()).hasToString("200.1.255.254");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=SubnetInfoTest`
Expected : échec de compilation, `cannot find symbol: class SubnetInfo`.

- [ ] **Step 3 : 🎓 À toi — écrire `SubnetInfo.of`**

Implémentation de référence :

```java
package com.coco.subnet.model;

/** Toutes les caractéristiques d'un sous-réseau. */
public record SubnetInfo(Cidr cidr,
                         Ipv4Address networkAddress,
                         Ipv4Address subnetMask,
                         Ipv4Address wildcardMask,
                         Ipv4Address broadcastAddress,
                         Ipv4Address firstUsable,
                         Ipv4Address lastUsable,
                         long usableHosts,
                         long totalAddresses) {

    public static SubnetInfo of(Cidr input) {
        Cidr cidr = input.toNetwork();
        Ipv4Address network = cidr.address();
        Ipv4Address mask = cidr.mask();
        Ipv4Address wildcard = mask.not();
        Ipv4Address broadcast = network.or(wildcard);
        long total = cidr.size();
        return switch (cidr.prefix()) {
            // /32 : une seule adresse, qui est l'hôte lui-même
            case 32 -> new SubnetInfo(cidr, network, mask, wildcard, broadcast, network, network, 1, total);
            // /31 : lien point à point (RFC 3021), les 2 adresses sont utilisables
            case 31 -> new SubnetInfo(cidr, network, mask, wildcard, broadcast, network, broadcast, 2, total);
            default -> new SubnetInfo(cidr, network, mask, wildcard, broadcast,
                    network.plus(1), broadcast.plus(-1), total - 2, total);
        };
    }
}
```

- [ ] **Step 4 : Écrire `CidrResult.java`**

```java
package com.coco.subnet.model;

import java.util.List;

/** Résultat d'un calcul CIDR, avec les avertissements éventuels (adresse corrigée). */
public record CidrResult(SubnetInfo subnet, List<String> warnings) {
}
```

- [ ] **Step 5 : Écrire le test `SubnetServiceTest` qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import org.junit.jupiter.api.Test;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class SubnetServiceTest {

    private final SubnetService service = new SubnetService();

    @Test
    void networkAddressHasNoWarning() {
        CidrResult result = service.calculate("192.168.10.0/24");

        assertThat(result.subnet().broadcastAddress()).hasToString("192.168.10.255");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void hostAddressIsCorrectedWithAWarning() {
        CidrResult result = service.calculate("192.168.10.37/24");

        assertThat(result.subnet().cidr()).hasToString("192.168.10.0/24");
        assertThat(result.warnings()).containsExactly("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void errorsUseTheCidrField() {
        InputError error = firstError(() -> service.calculate("300.1.1.1/24"));

        assertThat(error.code()).isEqualTo(ErrorCode.OCTET_OUT_OF_RANGE);
        assertThat(error.field()).isEqualTo("cidr");
    }
}
```

- [ ] **Step 6 : Écrire `SubnetService.java`**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.SubnetInfo;
import org.springframework.stereotype.Service;

@Service
public class SubnetService {

    public CidrResult calculate(String cidrText) {
        Cidr cidr = Cidr.parse(cidrText, "cidr");
        return new CidrResult(SubnetInfo.of(cidr), cidr.correctionWarning().stream().toList());
    }
}
```

- [ ] **Step 7 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`, 100 % de couverture sur `model` et `service`.

- [ ] **Step 8 : Commit**

```bash
git add src
git commit -m "feat: compute subnet details and add SubnetService"
```

---

### Task 7 : Algorithme VLSM (cas valides)

**Notion :** trouver la plus petite puissance de 2 qui contient le besoin (`Long.highestOneBit`), trier de façon stable, puis allouer les blocs à la suite. Comme les blocs sont triés du plus grand au plus petit, chacun tombe automatiquement sur une adresse alignée.

**Files :**
- Create : `src/main/java/com/coco/subnet/model/PlanMode.java`
- Create : `src/main/java/com/coco/subnet/model/Requirement.java`
- Create : `src/main/java/com/coco/subnet/model/AllocatedSubnet.java`
- Create : `src/main/java/com/coco/subnet/model/VlsmPlan.java`
- Create : `src/main/java/com/coco/subnet/service/VlsmService.java`
- Test : `src/test/java/com/coco/subnet/service/VlsmServiceAllocationTest.java`

**Interfaces :**
- Consumes : `Cidr`, `SubnetInfo`, `Ipv4Address` (tâches 3 à 6).
- Produces :
  - `enum PlanMode { VLAN, REQUIREMENTS }` ;
  - `record Requirement(Integer vlanId, String name, Long hosts)`. Les champs sont des types « boîte » (`Integer`, `Long`), pour qu'une valeur absente arrive jusqu'à la validation ;
  - `record AllocatedSubnet(Requirement requirement, SubnetInfo subnet, Ipv4Address gateway, long freeHosts)`. `gateway` vaut `null` hors mode VLAN ;
  - `record VlsmPlan(Cidr network, PlanMode mode, List<AllocatedSubnet> subnets, long usedAddresses, long remainingAddresses, List<String> warnings)` ;
  - `@Service VlsmService` avec `VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements)`, et les méthodes de paquet `static long blockSize(PlanMode, long hosts)` et `static int prefixOf(long blockSize)`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceAllocationTest {

    private final VlsmService service = new VlsmService();

    private static Requirement need(String name, long hosts) {
        return new Requirement(null, name, hosts);
    }

    private static Requirement vlan(int id, long hosts) {
        return new Requirement(id, "VLAN" + id, hosts);
    }

    private static List<String> cidrs(VlsmPlan plan) {
        return plan.subnets().stream().map(s -> s.subnet().cidr().toString()).toList();
    }

    @ParameterizedTest(name = "{0} {1} hôtes -> bloc {2} (/{3})")
    @CsvSource({
            "REQUIREMENTS,1,4,30", "REQUIREMENTS,2,4,30", "REQUIREMENTS,3,8,29", "REQUIREMENTS,50,64,26",
            "REQUIREMENTS,62,64,26", "REQUIREMENTS,63,128,25", "REQUIREMENTS,254,256,24",
            "REQUIREMENTS,4294967294,4294967296,0",
            "VLAN,1,4,30", "VLAN,2,8,29", "VLAN,29,32,27", "VLAN,30,64,26", "VLAN,4294967293,4294967296,0"
    })
    void blockSizeIsTheSmallestFittingPowerOfTwo(PlanMode mode, long hosts, long block, int prefix) {
        assertThat(VlsmService.blockSize(mode, hosts)).isEqualTo(block);
        assertThat(VlsmService.prefixOf(block)).isEqualTo(prefix);
    }

    @Test
    void referenceExampleWithoutVlans() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", REQUIREMENTS,
                List.of(need("LAN-A", 50), need("LAN-B", 20), need("LAN-C", 10), need("WAN", 2)));

        assertThat(cidrs(plan)).containsExactly(
                "192.168.10.0/26", "192.168.10.64/27", "192.168.10.96/28", "192.168.10.112/30");
        AllocatedSubnet lanA = plan.subnets().get(0);
        assertThat(lanA.subnet().firstUsable()).hasToString("192.168.10.1");
        assertThat(lanA.subnet().lastUsable()).hasToString("192.168.10.62");
        assertThat(lanA.subnet().broadcastAddress()).hasToString("192.168.10.63");
        assertThat(lanA.gateway()).isNull();
        assertThat(plan.subnets()).extracting(AllocatedSubnet::freeHosts).containsExactly(12L, 10L, 4L, 0L);
        assertThat(plan.usedAddresses()).isEqualTo(116);
        assertThat(plan.remainingAddresses()).isEqualTo(140);
        assertThat(plan.mode()).isEqualTo(REQUIREMENTS);
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void referenceExampleWithFourVlansOfThirtyHosts() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", VLAN,
                List.of(vlan(10, 30), vlan(20, 30), vlan(30, 30), vlan(40, 30)));

        assertThat(cidrs(plan)).containsExactly(
                "192.168.10.0/26", "192.168.10.64/26", "192.168.10.128/26", "192.168.10.192/26");
        assertThat(plan.subnets()).extracting(s -> s.gateway().toString())
                .containsExactly("192.168.10.1", "192.168.10.65", "192.168.10.129", "192.168.10.193");
        assertThat(plan.subnets()).extracting(AllocatedSubnet::freeHosts).containsOnly(31L);
        assertThat(plan.usedAddresses()).isEqualTo(256);
        assertThat(plan.remainingAddresses()).isZero();
    }

    @Test
    void gatewayEdgeCase() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", VLAN, List.of(vlan(10, 30), vlan(20, 29)));

        assertThat(cidrs(plan)).containsExactly("192.168.10.0/26", "192.168.10.64/27");
        assertThat(plan.subnets().get(1).freeHosts()).isZero();
    }

    @Test
    void equalBlocksKeepTheInputOrder() {
        VlsmPlan plan = service.allocate("10.0.0.0/24", REQUIREMENTS,
                List.of(need("A", 13), need("B", 50), need("C", 14)));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name()).containsExactly("B", "A", "C");
        assertThat(cidrs(plan)).containsExactly("10.0.0.0/26", "10.0.0.64/28", "10.0.0.80/28");
    }

    @Test
    void hostAddressAsParentIsCorrectedWithAWarning() {
        VlsmPlan plan = service.allocate("192.168.10.37/24", REQUIREMENTS, List.of(need("LAN", 10)));

        assertThat(plan.network()).hasToString("192.168.10.0/24");
        assertThat(cidrs(plan)).containsExactly("192.168.10.0/28");
        assertThat(plan.warnings()).containsExactly("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void highAddressesAreAllocatedCorrectly() {
        VlsmPlan plan = service.allocate("200.1.0.0/16", REQUIREMENTS, List.of(need("A", 1000), need("B", 500)));

        assertThat(cidrs(plan)).containsExactly("200.1.0.0/22", "200.1.4.0/23");
        assertThat(plan.subnets().get(0).subnet().broadcastAddress()).hasToString("200.1.3.255");
        assertThat(plan.subnets().get(1).subnet().broadcastAddress()).hasToString("200.1.5.255");
    }

    @Test
    void aRequirementCanFillTheParentExactly() {
        VlsmPlan plan = service.allocate("192.168.1.0/24", REQUIREMENTS, List.of(need("TOUT", 254)));

        assertThat(cidrs(plan)).containsExactly("192.168.1.0/24");
        assertThat(plan.remainingAddresses()).isZero();
    }

    @Test
    void theWholeIpv4SpaceFitsInOneBlock() {
        VlsmPlan plan = service.allocate("0.0.0.0/0", REQUIREMENTS, List.of(need("TOUT", 4_294_967_294L)));

        assertThat(cidrs(plan)).containsExactly("0.0.0.0/0");
        assertThat(plan.usedAddresses()).isEqualTo(4_294_967_296L);
        assertThat(plan.remainingAddresses()).isZero();
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=VlsmServiceAllocationTest`
Expected : échec de compilation, `cannot find symbol: class PlanMode`.

- [ ] **Step 3 : Écrire les types du modèle**

`PlanMode.java` :

```java
package com.coco.subnet.model;

/** VLAN : chaque ligne est un VLAN avec passerelle. REQUIREMENTS : besoins nommés, sans VLAN. */
public enum PlanMode {
    VLAN, REQUIREMENTS
}
```

`Requirement.java` :

```java
package com.coco.subnet.model;

/** Un besoin d'adresses. Les champs peuvent être null tant qu'ils n'ont pas été validés. */
public record Requirement(Integer vlanId, String name, Long hosts) {
}
```

`AllocatedSubnet.java` :

```java
package com.coco.subnet.model;

/** Un besoin et le sous-réseau qui lui est attribué. gateway vaut null hors mode VLAN. */
public record AllocatedSubnet(Requirement requirement, SubnetInfo subnet, Ipv4Address gateway, long freeHosts) {
}
```

`VlsmPlan.java` :

```java
package com.coco.subnet.model;

import java.util.List;

/** Le plan d'adressage complet, dans l'ordre d'allocation. */
public record VlsmPlan(Cidr network,
                       PlanMode mode,
                       List<AllocatedSubnet> subnets,
                       long usedAddresses,
                       long remainingAddresses,
                       List<String> warnings) {
}
```

- [ ] **Step 4 : 🎓 À toi — écrire `blockSize` et `prefixOf`, puis l'allocation**

Indice : `Long.highestOneBit(52)` vaut 32. Si `needed` est déjà une puissance de 2, on le garde ; sinon, on prend la puissance de 2 suivante. Le préfixe s'obtient avec `32 - Long.numberOfTrailingZeros(bloc)`.

Implémentation de référence de `VlsmService.java` :

```java
package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.Ipv4Address;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class VlsmService {

    public VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements) {
        Cidr parent = Cidr.parse(networkText, "network");
        return allocateValidated(parent, mode, requirements);
    }

    /** Alloue les blocs du plus grand au plus petit, à la suite, à partir du début du réseau parent. */
    private VlsmPlan allocateValidated(Cidr parent, PlanMode mode, List<Requirement> requirements) {
        Cidr network = parent.toNetwork();
        List<Integer> order = new ArrayList<>(IntStream.range(0, requirements.size()).boxed().toList());
        // List.sort est stable : à taille égale, l'ordre de saisie est conservé
        order.sort(Comparator.comparingLong((Integer i) -> blockSize(mode, requirements.get(i).hosts())).reversed());

        long start = network.address().toUnsignedLong();
        long cursor = start;
        List<AllocatedSubnet> subnets = new ArrayList<>();
        for (int i : order) {
            Requirement requirement = requirements.get(i);
            long block = blockSize(mode, requirement.hosts());
            SubnetInfo subnet = SubnetInfo.of(new Cidr(new Ipv4Address((int) cursor), prefixOf(block)));
            Ipv4Address gateway = mode == PlanMode.VLAN ? subnet.firstUsable() : null;
            long free = subnet.usableHosts() - requirement.hosts() - (mode == PlanMode.VLAN ? 1 : 0);
            subnets.add(new AllocatedSubnet(requirement, subnet, gateway, free));
            cursor += block;
        }
        long used = cursor - start;
        return new VlsmPlan(network, mode, List.copyOf(subnets), used, network.size() - used,
                parent.correctionWarning().stream().toList());
    }

    /** Plus petite puissance de 2 qui contient les hôtes + réseau + broadcast (+ passerelle en VLAN). */
    static long blockSize(PlanMode mode, long hosts) {
        long needed = hosts + 2 + (mode == PlanMode.VLAN ? 1 : 0);
        long highest = Long.highestOneBit(needed);
        return highest == needed ? needed : highest << 1;
    }

    static int prefixOf(long blockSize) {
        return 32 - Long.numberOfTrailingZeros(blockSize);
    }
}
```

- [ ] **Step 5 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 6 : Commit**

```bash
git add src
git commit -m "feat: allocate VLSM and VLAN subnets"
```

---

### Task 8 : Validation des besoins (erreurs par champ)

**Notion :** on rassemble **toutes** les erreurs de champ avant de lever l'exception, avec une seule erreur par champ. Chaque erreur porte le chemin du champ, par exemple `vlans[2].hosts`.

**Files :**
- Modify : `src/main/java/com/coco/subnet/service/VlsmService.java`
- Test : `src/test/java/com/coco/subnet/service/VlsmServiceValidationTest.java`

**Interfaces :**
- Consumes : `InputError`, `InvalidInputException`, `ErrorCode` (tâche 2).
- Produces :
  - `VlsmService.MAX_REQUIREMENTS = 100` ;
  - `VlsmService.MAX_HOSTS = 4_294_967_294L` ;
  - les champs d'erreur : `network`, `vlans`, `vlans[i].id`, `vlans[i].name`, `vlans[i].hosts`, et `requirements`, `requirements[i].name`, `requirements[i].hosts` hors mode VLAN.
  - Le `Requirement` renvoyé dans le plan contient le nom résolu (`VLAN<id>` par défaut), sans espaces superflus. Hors mode VLAN, son `vlanId` vaut `null`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.*;
import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceValidationTest {

    private final VlsmService service = new VlsmService();

    private static final String NET = "192.168.10.0/24";

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT", "300.1.1.1/24|OCTET_OUT_OF_RANGE", "10.0.0.0|MISSING_PREFIX",
            "10.0.0.0/31|PARENT_NOT_SPLITTABLE", "10.0.0.1/32|PARENT_NOT_SPLITTABLE"
    })
    void invalidParentNetwork(String network, ErrorCode expected) {
        InputError error = firstError(() -> service.allocate(network, REQUIREMENTS, List.of(new Requirement(null, "A", 2L))));

        assertThat(error.code()).isEqualTo(expected);
        assertThat(error.field()).isEqualTo("network");
    }

    @Test
    void parentSlash31Message() {
        assertThat(firstError(() -> service.allocate("10.0.0.0/31", REQUIREMENTS, List.of(new Requirement(null, "A", 2L)))).message())
                .isEqualTo("Un /31 ou /32 ne peut pas être découpé");
    }

    @Test
    void noRequirements() {
        InputError nullList = firstError(() -> service.allocate(NET, VLAN, null));
        InputError emptyList = firstError(() -> service.allocate(NET, REQUIREMENTS, List.of()));

        assertThat(nullList).isEqualTo(new InputError(NO_REQUIREMENTS, "vlans", "Ajoutez au moins un VLAN ou un besoin"));
        assertThat(emptyList.field()).isEqualTo("requirements");
    }

    @Test
    void tooManyRequirements() {
        List<Requirement> rows = Collections.nCopies(101, new Requirement(null, "A", 1L));

        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, rows)))
                .isEqualTo(new InputError(TOO_MANY_REQUIREMENTS, "requirements", "Maximum 100 sous-réseaux"));
    }

    @Test
    void vlanIdIsRequired() {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(null, null, 10L)))))
                .isEqualTo(new InputError(MISSING_FIELD, "vlans[0].id", "Ligne 1 : l'ID du VLAN est obligatoire"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4095, 5000})
    void vlanIdOutOfRange(int id) {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(id, null, 10L)))))
                .isEqualTo(new InputError(VLAN_ID_OUT_OF_RANGE, "vlans[0].id", "VLAN " + id + " : l'ID doit être entre 1 et 4094"));
    }

    @Test
    void invalidVlanIdUsesTheLineNumberInOtherMessages() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 5L), new Requirement(5000, null, 0L))));

        assertThat(errors).extracting(InputError::message)
                .containsExactly("VLAN 5000 : l'ID doit être entre 1 et 4094", "Ligne 2 : au moins 1 hôte requis");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    void hostsMustBePositive(long hosts) {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, null, hosts)))))
                .isEqualTo(new InputError(HOSTS_NOT_POSITIVE, "vlans[0].hosts", "VLAN 10 : au moins 1 hôte requis"));
    }

    @Test
    void hostsAreRequired() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "LAN-A", null)))))
                .isEqualTo(new InputError(MISSING_FIELD, "requirements[0].hosts", "LAN-A : le nombre d'hôtes est obligatoire"));
    }

    @Test
    void hostsLimitDependsOnTheMode() {
        assertThat(firstError(() -> service.allocate("0.0.0.0/0", VLAN, List.of(new Requirement(10, null, 4_294_967_294L)))))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "vlans[0].hosts",
                        "VLAN 10 : un sous-réseau IPv4 ne peut pas dépasser 4 294 967 293 hôtes (hors passerelle)"));
        assertThat(firstError(() -> service.allocate("0.0.0.0/0", REQUIREMENTS, List.of(new Requirement(null, "LAN-A", 4_294_967_295L)))))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "requirements[0].hosts",
                        "LAN-A : un sous-réseau IPv4 ne peut pas dépasser 4 294 967 294 hôtes"));
    }

    @Test
    void largestValidVlanFitsInTheWholeSpace() {
        VlsmPlan plan = service.allocate("0.0.0.0/0", VLAN, List.of(new Requirement(10, null, 4_294_967_293L)));

        assertThat(plan.subnets().get(0).subnet().cidr()).hasToString("0.0.0.0/0");
        assertThat(plan.subnets().get(0).freeHosts()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nameIsRequiredWithoutVlans(String name) {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 2L), new Requirement(null, "B", 2L), new Requirement(null, name, 2L)))))
                .isEqualTo(new InputError(EMPTY_NAME, "requirements[2].name", "Le besoin n°3 doit avoir un nom"));
    }

    @Test
    void nullNameIsRequiredWithoutVlans() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, null, 2L)))).code())
                .isEqualTo(EMPTY_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a;b", "-A1", "=SUM", "Salle Info", "@cmd", "abcdefghijklmnopqrstuvwxyz0123456"})
    void invalidNames(String name) {
        InputError error = firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, name, 5L))));

        assertThat(error).isEqualTo(new InputError(INVALID_NAME, "vlans[0].name",
                "VLAN 10 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
    }

    @Test
    void invalidNameWithoutVlansUsesTheLineNumber() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "a;b", 5L)))).message())
                .isEqualTo("Besoin n°1 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)");
    }

    @Test
    void namesAreTrimmedAndAccentsAreAllowed() {
        VlsmPlan plan = service.allocate(NET, REQUIREMENTS, List.of(new Requirement(7, "  Comptabilité ", 5L)));

        assertThat(plan.subnets().get(0).requirement()).isEqualTo(new Requirement(null, "Comptabilité", 5L));
    }

    @Test
    void vlanNameDefaultsToVlanAndId() {
        VlsmPlan plan = service.allocate(NET, VLAN, List.of(new Requirement(10, null, 5L), new Requirement(20, " ", 5L)));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name()).containsExactly("VLAN10", "VLAN20");
    }

    @Test
    void nullRowIsReportedAsMissingFields() {
        List<Requirement> rows = new ArrayList<>(Arrays.asList(new Requirement(10, null, 5L), null));

        assertThat(errors(() -> service.allocate(NET, VLAN, rows)))
                .extracting(InputError::field).containsExactly("vlans[1].id", "vlans[1].hosts");
    }

    @Test
    void collectsErrorsFromEveryField() {
        List<InputError> errors = errors(() -> service.allocate("300.1.1.1/24", VLAN,
                List.of(new Requirement(10, "a;b", 0L), new Requirement(20, null, 5L))));

        assertThat(errors).extracting(InputError::field)
                .containsExactly("network", "vlans[0].name", "vlans[0].hosts");
        assertThat(errors).extracting(InputError::code)
                .containsExactly(OCTET_OUT_OF_RANGE, INVALID_NAME, HOSTS_NOT_POSITIVE);
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=VlsmServiceValidationTest`
Expected : échec de compilation, `cannot find symbol: variable MAX_REQUIREMENTS` (ou équivalent), ou des tests rouges.

- [ ] **Step 3 : Remplacer `VlsmService.java` par cette version**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Ipv4Address;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.coco.subnet.model.ErrorCode.EMPTY_NAME;
import static com.coco.subnet.model.ErrorCode.HOSTS_NOT_POSITIVE;
import static com.coco.subnet.model.ErrorCode.HOSTS_TOO_LARGE;
import static com.coco.subnet.model.ErrorCode.INVALID_NAME;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.NO_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.PARENT_NOT_SPLITTABLE;
import static com.coco.subnet.model.ErrorCode.TOO_MANY_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.VLAN_ID_OUT_OF_RANGE;

@Service
public class VlsmService {

    public static final int MAX_REQUIREMENTS = 100;
    /** Un bloc /0 : 2^32 adresses moins le réseau et le broadcast. */
    public static final long MAX_HOSTS = 4_294_967_294L;
    /** Commence par une lettre ou un chiffre : empêche l'injection de formules dans Excel. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,31}$");

    public VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements) {
        List<InputError> errors = new ArrayList<>();
        Cidr parent = parseParent(networkText, errors);
        List<Requirement> resolved = validateRequirements(mode, requirements, errors);
        throwIfAny(errors);
        return allocateValidated(parent, mode, resolved);
    }

    private Cidr parseParent(String networkText, List<InputError> errors) {
        try {
            Cidr parent = Cidr.parse(networkText, "network");
            if (parent.prefix() >= 31) {
                errors.add(new InputError(PARENT_NOT_SPLITTABLE, "network", "Un /31 ou /32 ne peut pas être découpé"));
            }
            return parent;
        } catch (InvalidInputException e) {
            errors.addAll(e.errors());
            return null;
        }
    }

    private List<Requirement> validateRequirements(PlanMode mode, List<Requirement> requirements, List<InputError> errors) {
        String list = listField(mode);
        if (requirements == null || requirements.isEmpty()) {
            errors.add(new InputError(NO_REQUIREMENTS, list, "Ajoutez au moins un VLAN ou un besoin"));
            return List.of();
        }
        if (requirements.size() > MAX_REQUIREMENTS) {
            errors.add(new InputError(TOO_MANY_REQUIREMENTS, list, "Maximum 100 sous-réseaux"));
            return List.of();
        }
        List<Requirement> resolved = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement row = requirements.get(i) == null ? new Requirement(null, null, null) : requirements.get(i);
            resolved.add(validateRow(mode, i, row, errors));
        }
        return resolved;
    }

    /** Valide une ligne et renvoie le besoin « résolu » (nom nettoyé, nom par défaut en VLAN). */
    private Requirement validateRow(PlanMode mode, int index, Requirement row, List<InputError> errors) {
        String path = listField(mode) + "[" + index + "].";
        String name = row.name() == null ? "" : row.name().strip();
        Integer vlanId = null;
        String label;
        if (mode == PlanMode.VLAN) {
            vlanId = row.vlanId();
            if (vlanId == null) {
                errors.add(new InputError(MISSING_FIELD, path + "id", "Ligne " + (index + 1) + " : l'ID du VLAN est obligatoire"));
                label = "Ligne " + (index + 1);
            } else if (vlanId < 1 || vlanId > 4094) {
                errors.add(new InputError(VLAN_ID_OUT_OF_RANGE, path + "id", "VLAN " + vlanId + " : l'ID doit être entre 1 et 4094"));
                label = "Ligne " + (index + 1);
            } else {
                label = "VLAN " + vlanId;
            }
            if (name.isEmpty() && vlanId != null) {
                name = "VLAN" + vlanId;
            }
        } else {
            label = NAME.matcher(name).matches() ? name : "Besoin n°" + (index + 1);
            if (name.isEmpty()) {
                errors.add(new InputError(EMPTY_NAME, path + "name", "Le besoin n°" + (index + 1) + " doit avoir un nom"));
            }
        }
        if (!name.isEmpty() && !NAME.matcher(name).matches()) {
            errors.add(new InputError(INVALID_NAME, path + "name",
                    label + " : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
        }
        Long hosts = row.hosts();
        long max = maxHosts(mode);
        if (hosts == null) {
            errors.add(new InputError(MISSING_FIELD, path + "hosts", label + " : le nombre d'hôtes est obligatoire"));
        } else if (hosts < 1) {
            errors.add(new InputError(HOSTS_NOT_POSITIVE, path + "hosts", label + " : au moins 1 hôte requis"));
        } else if (hosts > max) {
            errors.add(new InputError(HOSTS_TOO_LARGE, path + "hosts", label + " : un sous-réseau IPv4 ne peut pas dépasser "
                    + groupDigits(max) + " hôtes" + (mode == PlanMode.VLAN ? " (hors passerelle)" : "")));
        }
        return new Requirement(vlanId, name, hosts);
    }

    /** Alloue les blocs du plus grand au plus petit, à la suite, à partir du début du réseau parent. */
    private VlsmPlan allocateValidated(Cidr parent, PlanMode mode, List<Requirement> requirements) {
        Cidr network = parent.toNetwork();
        List<Integer> order = new ArrayList<>(IntStream.range(0, requirements.size()).boxed().toList());
        // List.sort est stable : à taille égale, l'ordre de saisie est conservé
        order.sort(Comparator.comparingLong((Integer i) -> blockSize(mode, requirements.get(i).hosts())).reversed());

        long start = network.address().toUnsignedLong();
        long cursor = start;
        List<AllocatedSubnet> subnets = new ArrayList<>();
        for (int i : order) {
            Requirement requirement = requirements.get(i);
            long block = blockSize(mode, requirement.hosts());
            SubnetInfo subnet = SubnetInfo.of(new Cidr(new Ipv4Address((int) cursor), prefixOf(block)));
            Ipv4Address gateway = mode == PlanMode.VLAN ? subnet.firstUsable() : null;
            long free = subnet.usableHosts() - requirement.hosts() - (mode == PlanMode.VLAN ? 1 : 0);
            subnets.add(new AllocatedSubnet(requirement, subnet, gateway, free));
            cursor += block;
        }
        long used = cursor - start;
        return new VlsmPlan(network, mode, List.copyOf(subnets), used, network.size() - used,
                parent.correctionWarning().stream().toList());
    }

    /** Plus petite puissance de 2 qui contient les hôtes + réseau + broadcast (+ passerelle en VLAN). */
    static long blockSize(PlanMode mode, long hosts) {
        long needed = hosts + 2 + (mode == PlanMode.VLAN ? 1 : 0);
        long highest = Long.highestOneBit(needed);
        return highest == needed ? needed : highest << 1;
    }

    static int prefixOf(long blockSize) {
        return 32 - Long.numberOfTrailingZeros(blockSize);
    }

    static long maxHosts(PlanMode mode) {
        return mode == PlanMode.VLAN ? MAX_HOSTS - 1 : MAX_HOSTS;
    }

    private static String listField(PlanMode mode) {
        return mode == PlanMode.VLAN ? "vlans" : "requirements";
    }

    /** 4294967294 → "4 294 967 294". */
    private static String groupDigits(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private static void throwIfAny(List<InputError> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
    }
}
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. `VlsmServiceAllocationTest` reste vert.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: validate VLSM requirements field by field"
```

---

### Task 9 : Vérifications globales (doublons et capacité)

**Notion :** certaines vérifications ne sont possibles qu'une fois **tous** les champs valides : les doublons avec un `Set`, puis la place disponible.

**Files :**
- Modify : `src/main/java/com/coco/subnet/service/VlsmService.java`
- Test : `src/test/java/com/coco/subnet/service/VlsmServiceGlobalChecksTest.java`

**Interfaces :**
- Produces : les codes `DUPLICATE_VLAN_ID` (champ `vlans[i].id`), `DUPLICATE_NAME` (`<list>[i].name`), `REQUIREMENT_TOO_LARGE` (`<list>[i].hosts`) et `NETWORK_TOO_SMALL` (`network`).

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_NAME;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_VLAN_ID;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.REQUIREMENT_TOO_LARGE;
import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceGlobalChecksTest {

    private final VlsmService service = new VlsmService();

    private static final String NET = "192.168.10.0/24";

    @Test
    void duplicateVlanIdIsReportedOnceOnTheSecondRow() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 5L), new Requirement(20, null, 5L), new Requirement(20, null, 5L))));

        assertThat(errors).containsExactly(new InputError(DUPLICATE_VLAN_ID, "vlans[2].id", "VLAN 20 est en double"));
    }

    @Test
    void duplicateNamesIgnoreCase() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "RH", 5L), new Requirement(null, "rh", 5L)))))
                .isEqualTo(new InputError(DUPLICATE_NAME, "requirements[1].name", "Le nom 'rh' est utilisé deux fois"));
    }

    @Test
    void duplicateVlanNames() {
        assertThat(firstError(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, "Compta", 5L), new Requirement(20, "compta", 5L)))))
                .isEqualTo(new InputError(DUPLICATE_NAME, "vlans[1].name", "Le nom 'compta' est utilisé deux fois"));
    }

    @Test
    void duplicatesAreCheckedBeforeCapacity() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 300L), new Requirement(10, null, 5L))));

        assertThat(errors).extracting(InputError::code).containsExactly(DUPLICATE_VLAN_ID);
    }

    @Test
    void requirementLargerThanTheParent() {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, null, 300L)))))
                .isEqualTo(new InputError(REQUIREMENT_TOO_LARGE, "vlans[0].hosts",
                        "VLAN 10 (300 hôtes, bloc /23) ne tient pas dans un /24"));
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "LAN-A", 300L)))))
                .isEqualTo(new InputError(REQUIREMENT_TOO_LARGE, "requirements[0].hosts",
                        "LAN-A (300 hôtes, bloc /23) ne tient pas dans un /24"));
    }

    @Test
    void tooLargeRequirementsAreAllReportedWithoutTheTotal() {
        List<InputError> errors = errors(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 300L), new Requirement(null, "B", 10L), new Requirement(null, "C", 600L))));

        assertThat(errors).extracting(InputError::field).containsExactly("requirements[0].hosts", "requirements[2].hosts");
    }

    @Test
    void networkTooSmall() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 200L), new Requirement(null, "B", 50L)))))
                .isEqualTo(new InputError(NETWORK_TOO_SMALL, "network",
                        "Le /24 (256 adresses) ne suffit pas : 320 nécessaires, il manque 64"));
    }

    @Test
    void overflowByASingleSlash30() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 254L), new Requirement(null, "B", 1L)))).message())
                .isEqualTo("Le /24 (256 adresses) ne suffit pas : 260 nécessaires, il manque 4");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=VlsmServiceGlobalChecksTest`
Expected : FAIL. Par exemple, `duplicateVlanIdIsReportedOnceOnTheSecondRow` échoue parce qu'aucune exception n'est levée.

- [ ] **Step 3 : Modifier `VlsmService`**

Ajoute ces imports :

```java
import java.util.HashSet;
import java.util.Set;

import static com.coco.subnet.model.ErrorCode.DUPLICATE_NAME;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_VLAN_ID;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.REQUIREMENT_TOO_LARGE;
```

Remplace la méthode `allocate` par :

```java
    public VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements) {
        List<InputError> errors = new ArrayList<>();
        Cidr parent = parseParent(networkText, errors);
        List<Requirement> resolved = validateRequirements(mode, requirements, errors);
        throwIfAny(errors);
        // Les vérifications globales n'ont de sens que si chaque champ est valide
        checkDuplicates(mode, resolved, errors);
        throwIfAny(errors);
        checkCapacity(mode, parent.toNetwork(), resolved, errors);
        throwIfAny(errors);
        return allocateValidated(parent, mode, resolved);
    }
```

Ajoute ces méthodes juste après `validateRow` :

```java
    private void checkDuplicates(PlanMode mode, List<Requirement> requirements, List<InputError> errors) {
        String list = listField(mode);
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement r = requirements.get(i);
            if (mode == PlanMode.VLAN && !ids.add(r.vlanId())) {
                errors.add(new InputError(DUPLICATE_VLAN_ID, list + "[" + i + "].id", "VLAN " + r.vlanId() + " est en double"));
                continue; // même ID → même nom par défaut : une seule erreur suffit
            }
            if (!names.add(r.name().toLowerCase(Locale.ROOT))) {
                errors.add(new InputError(DUPLICATE_NAME, list + "[" + i + "].name", "Le nom '" + r.name() + "' est utilisé deux fois"));
            }
        }
    }

    private void checkCapacity(PlanMode mode, Cidr network, List<Requirement> requirements, List<InputError> errors) {
        long capacity = network.size();
        long total = 0;
        for (int i = 0; i < requirements.size(); i++) {
            Requirement r = requirements.get(i);
            long block = blockSize(mode, r.hosts());
            total += block;
            if (block > capacity) {
                errors.add(new InputError(REQUIREMENT_TOO_LARGE, listField(mode) + "[" + i + "].hosts",
                        label(mode, r) + " (" + r.hosts() + " hôtes, bloc /" + prefixOf(block)
                                + ") ne tient pas dans un /" + network.prefix()));
            }
        }
        if (errors.isEmpty() && total > capacity) {
            errors.add(new InputError(NETWORK_TOO_SMALL, "network", "Le /" + network.prefix() + " (" + capacity
                    + " adresses) ne suffit pas : " + total + " nécessaires, il manque " + (total - capacity)));
        }
    }

    /** Libellé d'une ligne déjà validée, utilisé en tête des messages. */
    private static String label(PlanMode mode, Requirement r) {
        return mode == PlanMode.VLAN ? "VLAN " + r.vlanId() : r.name();
    }
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. Les quatre classes de test `VlsmService*` passent et la couverture reste à 100 %.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: check duplicates and capacity before allocating"
```

---

### Task 10 : Génération des VLAN uniformes et ID suivant

**Notion :** une même méthode du service sert de raccourci à la fois pour l'API (`vlanCount` + `hostsPerVlan`) et pour le bouton « Générer » de la page web.

**Files :**
- Modify : `src/main/java/com/coco/subnet/service/VlsmService.java`
- Test : `src/test/java/com/coco/subnet/service/VlsmServiceGenerationTest.java`

**Interfaces :**
- Produces :
  - `VlsmService.MAX_VLAN_COUNT = 100` ;
  - `List<Requirement> generateUniformVlans(Integer vlanCount, Long hostsPerVlan)`, qui renvoie les VLAN `10, 20, …` nommés `VLAN10…`. Ses champs d'erreur sont `vlanCount` et `hostsPerVlan` ;
  - `int nextVlanId(Collection<Integer> existingIds)`, qui renvoie le plus grand ID + 10, ou 10 si aucun ID n'existe. Les `null` sont ignorés.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.HOSTS_NOT_POSITIVE;
import static com.coco.subnet.model.ErrorCode.HOSTS_TOO_LARGE;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceGenerationTest {

    private final VlsmService service = new VlsmService();

    @Test
    void generatesDetailedRows() {
        assertThat(service.generateUniformVlans(4, 30L)).containsExactly(
                new Requirement(10, "VLAN10", 30L), new Requirement(20, "VLAN20", 30L),
                new Requirement(30, "VLAN30", 30L), new Requirement(40, "VLAN40", 30L));
    }

    @Test
    void hundredVlansEndAtId1000() {
        List<Requirement> vlans = service.generateUniformVlans(100, 1L);

        assertThat(vlans).hasSize(100);
        assertThat(vlans.get(99).vlanId()).isEqualTo(1000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void vlanCountOutOfRange(int count) {
        assertThat(firstError(() -> service.generateUniformVlans(count, 30L)))
                .isEqualTo(new InputError(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100"));
    }

    @Test
    void bothFieldsAreRequiredAndReportedTogether() {
        assertThat(errors(() -> service.generateUniformVlans(null, null))).containsExactly(
                new InputError(MISSING_FIELD, "vlanCount", "Le champ « Nombre de VLAN » est obligatoire"),
                new InputError(MISSING_FIELD, "hostsPerVlan", "Le champ « Hôtes par VLAN » est obligatoire"));
    }

    @Test
    void hostsPerVlanMustBeValid() {
        assertThat(firstError(() -> service.generateUniformVlans(4, 0L)))
                .isEqualTo(new InputError(HOSTS_NOT_POSITIVE, "hostsPerVlan", "Au moins 1 hôte requis"));
        assertThat(firstError(() -> service.generateUniformVlans(4, 4_294_967_294L)))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "hostsPerVlan",
                        "Un sous-réseau IPv4 ne peut pas dépasser 4 294 967 293 hôtes (hors passerelle)"));
    }

    @Test
    void nextVlanId() {
        assertThat(service.nextVlanId(List.of())).isEqualTo(10);
        assertThat(service.nextVlanId(List.of(10, 40, 20))).isEqualTo(50);
        assertThat(service.nextVlanId(Arrays.asList(null, 30, null))).isEqualTo(40);
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=VlsmServiceGenerationTest`
Expected : échec de compilation, `cannot find symbol: method generateUniformVlans`.

- [ ] **Step 3 : Ajouter dans `VlsmService`**

Ajoute ces imports :

```java
import java.util.Collection;
import java.util.Objects;

import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
```

Ajoute la constante sous `MAX_REQUIREMENTS` :

```java
    public static final int MAX_VLAN_COUNT = 100;
```

Ajoute ces méthodes publiques juste après `allocate` :

```java
    /** Raccourci « même nombre d'hôtes pour tous » : génère des lignes détaillées et modifiables. */
    public List<Requirement> generateUniformVlans(Integer vlanCount, Long hostsPerVlan) {
        List<InputError> errors = new ArrayList<>();
        if (vlanCount == null) {
            errors.add(new InputError(MISSING_FIELD, "vlanCount", "Le champ « Nombre de VLAN » est obligatoire"));
        } else if (vlanCount < 1 || vlanCount > MAX_VLAN_COUNT) {
            errors.add(new InputError(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100"));
        }
        long max = maxHosts(PlanMode.VLAN);
        if (hostsPerVlan == null) {
            errors.add(new InputError(MISSING_FIELD, "hostsPerVlan", "Le champ « Hôtes par VLAN » est obligatoire"));
        } else if (hostsPerVlan < 1) {
            errors.add(new InputError(HOSTS_NOT_POSITIVE, "hostsPerVlan", "Au moins 1 hôte requis"));
        } else if (hostsPerVlan > max) {
            errors.add(new InputError(HOSTS_TOO_LARGE, "hostsPerVlan",
                    "Un sous-réseau IPv4 ne peut pas dépasser " + groupDigits(max) + " hôtes (hors passerelle)"));
        }
        throwIfAny(errors);
        return IntStream.rangeClosed(1, vlanCount)
                .mapToObj(i -> new Requirement(i * 10, "VLAN" + (i * 10), hostsPerVlan))
                .toList();
    }

    /** ID proposé pour une nouvelle ligne : plus grand ID existant + 10, ou 10. */
    public int nextVlanId(Collection<Integer> existingIds) {
        return existingIds.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 10;
    }
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: generate uniform VLAN rows and next VLAN id"
```

---

### Task 11 : Export CSV

**Notion :** encodage UTF-8 et BOM, conventions d'Excel (`;`, `\r\n`), tableaux d'octets.

**Files :**
- Create : `src/main/java/com/coco/subnet/service/CsvExporter.java`
- Test : `src/test/java/com/coco/subnet/service/CsvExporterTest.java`

**Interfaces :**
- Consumes : `VlsmPlan`, `AllocatedSubnet` (tâche 7) et `VlsmService.allocate` (dans les tests).
- Produces : `@Service CsvExporter` avec `String toCsv(VlsmPlan)`, `byte[] toBytes(VlsmPlan)` et `String fileName(VlsmPlan)`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    private final VlsmService vlsm = new VlsmService();
    private final CsvExporter exporter = new CsvExporter();

    @Test
    void vlanPlan() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", VLAN, List.of(new Requirement(10, "Comptabilité", 30L)));

        assertThat(exporter.toCsv(plan)).isEqualTo(
                "﻿VLAN;Nom;Hôtes demandés;Réseau;Masque;Passerelle;Première;Dernière;Broadcast;Hôtes utilisables;Libres\r\n"
                        + "10;Comptabilité;30;192.168.10.0/26;255.255.255.192;192.168.10.1;192.168.10.1;192.168.10.62;192.168.10.63;62;31\r\n");
    }

    @Test
    void planWithoutVlansLeavesVlanAndGatewayEmpty() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        assertThat(exporter.toCsv(plan)).endsWith(
                "\r\n;WAN;2;192.168.10.0/30;255.255.255.252;;192.168.10.1;192.168.10.2;192.168.10.3;2;0\r\n");
    }

    @Test
    void bytesStartWithTheUtf8Bom() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        byte[] bytes = exporter.toBytes(plan);

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void fileNameUsesTheCorrectedNetwork() {
        VlsmPlan plan = vlsm.allocate("192.168.10.37/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        assertThat(exporter.fileName(plan)).isEqualTo("plan-adressage-192.168.10.0-24.csv");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=CsvExporterTest`
Expected : échec de compilation, `cannot find symbol: class CsvExporter`.

- [ ] **Step 3 : Écrire `CsvExporter.java`**

```java
package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Plan d'adressage au format CSV pour Excel : « ; », UTF-8 avec BOM, fins de ligne Windows. */
@Service
public class CsvExporter {

    static final String HEADER =
            "VLAN;Nom;Hôtes demandés;Réseau;Masque;Passerelle;Première;Dernière;Broadcast;Hôtes utilisables;Libres";

    public String toCsv(VlsmPlan plan) {
        StringBuilder csv = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (AllocatedSubnet allocated : plan.subnets()) {
            Requirement r = allocated.requirement();
            SubnetInfo s = allocated.subnet();
            csv.append(r.vlanId() == null ? "" : r.vlanId()).append(';')
                    .append(r.name()).append(';')
                    .append(r.hosts()).append(';')
                    .append(s.cidr()).append(';')
                    .append(s.subnetMask()).append(';')
                    .append(allocated.gateway() == null ? "" : allocated.gateway()).append(';')
                    .append(s.firstUsable()).append(';')
                    .append(s.lastUsable()).append(';')
                    .append(s.broadcastAddress()).append(';')
                    .append(s.usableHosts()).append(';')
                    .append(allocated.freeHosts()).append("\r\n");
        }
        return csv.toString();
    }

    public byte[] toBytes(VlsmPlan plan) {
        return toCsv(plan).getBytes(StandardCharsets.UTF_8);
    }

    public String fileName(VlsmPlan plan) {
        Cidr network = plan.network();
        return "plan-adressage-" + network.address() + "-" + network.prefix() + ".csv";
    }
}
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`, 100 % de couverture sur `model` et `service`. **Le cœur métier est terminé.**

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: export addressing plan as CSV"
```

---

### Task 12 : API `GET /api/subnets` et format `ProblemDetail`

**Notion :** `@RestController`, `@RequestParam`, `@RestControllerAdvice` limité à un controller, `ProblemDetail` (RFC 9457), tests avec `@WebMvcTest` et `MockMvc`.

**Files :**
- Create : `src/main/java/com/coco/subnet/dto/SubnetResponse.java`
- Create : `src/main/java/com/coco/subnet/controller/ApiProblems.java`
- Create : `src/main/java/com/coco/subnet/controller/ApiExceptionHandler.java`
- Create : `src/main/java/com/coco/subnet/controller/ApiController.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiSubnetEndpointTest.java`

**Interfaces :**
- Consumes : `SubnetService.calculate`, `CidrResult`, `InputError`, `ErrorCode`.
- Produces :
  - `record SubnetResponse(String cidr, String networkAddress, String subnetMask, String wildcardMask, String broadcastAddress, String firstUsable, String lastUsable, long usableHosts, long totalAddresses, List<String> warnings)` avec `static from(CidrResult)` ;
  - `ApiProblems.badRequest(List<InputError>)`, `ApiProblems.badRequest(ErrorCode, String field, String message)`, `ApiProblems.build(HttpStatus, String title, ErrorCode, String detail, List<InputError>, HttpHeaders)` et `ApiProblems.internalError()`, qui renvoient tous `ResponseEntity<ProblemDetail>`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiSubnetEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void calculatesTheReferenceExample() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "192.168.10.0/24"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cidr").value("192.168.10.0/24"))
                .andExpect(jsonPath("$.networkAddress").value("192.168.10.0"))
                .andExpect(jsonPath("$.subnetMask").value("255.255.255.0"))
                .andExpect(jsonPath("$.wildcardMask").value("0.0.0.255"))
                .andExpect(jsonPath("$.broadcastAddress").value("192.168.10.255"))
                .andExpect(jsonPath("$.firstUsable").value("192.168.10.1"))
                .andExpect(jsonPath("$.lastUsable").value("192.168.10.254"))
                .andExpect(jsonPath("$.usableHosts").value(254))
                .andExpect(jsonPath("$.totalAddresses").value(256))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void wholeSpaceCountsDoNotOverflow() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "0.0.0.0/0"))
                .andExpect(jsonPath("$.usableHosts").value(4_294_967_294L))
                .andExpect(jsonPath("$.totalAddresses").value(4_294_967_296L));
    }

    @Test
    void correctedAddressComesWithAWarning() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "192.168.10.37/24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidr").value("192.168.10.0/24"))
                .andExpect(jsonPath("$.warnings[0]").value("Adresse corrigée vers le réseau 192.168.10.0/24"));
    }

    @Test
    void invalidInputIsAProblemDetail() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "300.1.1.1/24"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Entrée invalide"))
                .andExpect(jsonPath("$.code").value("OCTET_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.detail").value("L'octet 300 est hors limites (0–255)"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"))
                .andExpect(jsonPath("$.errors[0].code").value("OCTET_OUT_OF_RANGE"));
    }

    @Test
    void emptyCidrIsAnEmptyInput() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_INPUT"));
    }

    @Test
    void missingParameter() throws Exception {
        mvc.perform(get("/api/subnets"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"))
                .andExpect(jsonPath("$.detail").value("Paramètre obligatoire manquant : cidr"));
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=ApiSubnetEndpointTest`
Expected : échec de compilation, `cannot find symbol: class ApiController`.

- [ ] **Step 3 : Écrire `SubnetResponse.java`**

```java
package com.coco.subnet.dto;

import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.SubnetInfo;

import java.util.List;

/** Résultat CIDR tel qu'il est affiché (JSON ou page web). */
public record SubnetResponse(String cidr,
                             String networkAddress,
                             String subnetMask,
                             String wildcardMask,
                             String broadcastAddress,
                             String firstUsable,
                             String lastUsable,
                             long usableHosts,
                             long totalAddresses,
                             List<String> warnings) {

    public static SubnetResponse from(CidrResult result) {
        SubnetInfo s = result.subnet();
        return new SubnetResponse(s.cidr().toString(), s.networkAddress().toString(), s.subnetMask().toString(),
                s.wildcardMask().toString(), s.broadcastAddress().toString(), s.firstUsable().toString(),
                s.lastUsable().toString(), s.usableHosts(), s.totalAddresses(), result.warnings());
    }
}
```

- [ ] **Step 4 : Écrire `ApiProblems.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;

/** Fabrique unique des réponses d'erreur de l'API (RFC 9457 + propriétés « code » et « errors »). */
public final class ApiProblems {

    private ApiProblems() {
    }

    public static ResponseEntity<ProblemDetail> badRequest(List<InputError> errors) {
        ErrorCode code = errors.size() == 1 ? errors.get(0).code() : ErrorCode.VALIDATION_FAILED;
        String detail = errors.size() == 1 ? errors.get(0).message() : errors.size() + " erreurs dans la requête";
        return build(HttpStatus.BAD_REQUEST, "Entrée invalide", code, detail, errors, new HttpHeaders());
    }

    public static ResponseEntity<ProblemDetail> badRequest(ErrorCode code, String field, String message) {
        return badRequest(List.of(new InputError(code, field, message)));
    }

    public static ResponseEntity<ProblemDetail> internalError() {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", ErrorCode.INTERNAL_ERROR,
                "Une erreur interne est survenue", List.of(), new HttpHeaders());
    }

    public static ResponseEntity<ProblemDetail> build(HttpStatus status, String title, ErrorCode code, String detail,
                                                      List<InputError> errors, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
```

- [ ] **Step 5 : Écrire `ApiExceptionHandler.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs survenues une fois la requête arrivée dans ApiController. */
@RestControllerAdvice(assignableTypes = ApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ProblemDetail> invalidInput(InvalidInputException ex) {
        return ApiProblems.badRequest(ex.errors());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException ex) {
        return ApiProblems.badRequest(ErrorCode.MISSING_PARAMETER, ex.getParameterName(),
                "Paramètre obligatoire manquant : " + ex.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Erreur inattendue dans l'API", ex);
        return ApiProblems.internalError();
    }
}
```

- [ ] **Step 6 : Écrire `ApiController.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Calculateur", description = "Calcul CIDR, découpage VLSM et plan par VLAN")
public class ApiController {

    private final SubnetService subnetService;
    private final VlsmService vlsmService;
    private final CsvExporter csvExporter;

    public ApiController(SubnetService subnetService, VlsmService vlsmService, CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping(value = "/subnets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calcule un réseau CIDR",
            description = "Adresse réseau, masque, wildcard, broadcast, plage utilisable et nombre d'hôtes.")
    public SubnetResponse subnet(@Parameter(example = "192.168.10.0/24") @RequestParam String cidr) {
        return SubnetResponse.from(subnetService.calculate(cidr));
    }
}
```

- [ ] **Step 7 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. Si `$.code` n'est pas trouvé à la racine du JSON, c'est que le mixin Jackson de `ProblemDetail` n'est pas actif. Il faut alors s'arrêter et le signaler, au lieu de contourner le problème.

- [ ] **Step 8 : Commit**

```bash
git add src
git commit -m "feat: expose CIDR calculation on GET /api/subnets"
```

---

### Task 13 : API `POST /api/vlsm`, `@Valid` et erreurs JSON

**Notion :** `@RequestBody` et `@Valid` sur des records, contraintes sur les éléments d'une liste (`List<@NotNull @Valid X>`), messages tirés de `messages.properties`, et lecture de la cause d'une exception Jackson.

**Files :**
- Create : `src/main/java/com/coco/subnet/dto/RequirementRequest.java`, `VlanRequest.java`, `VlsmRequest.java`, `SubnetEntry.java`, `VlsmResponse.java`
- Create : `src/main/java/com/coco/subnet/controller/ValidationErrors.java`
- Create : `src/main/resources/messages.properties`
- Modify : `src/main/java/com/coco/subnet/controller/ApiController.java`
- Modify : `src/main/java/com/coco/subnet/controller/ApiExceptionHandler.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiVlsmEndpointTest.java`

**Interfaces :**
- Consumes : `VlsmService.allocate`, `generateUniformVlans`, `VlsmPlan`.
- Produces :
  - `record VlsmRequest(String network, List<RequirementRequest> requirements, List<VlanRequest> vlans, Integer vlanCount, Long hostsPerVlan)` ;
  - `record RequirementRequest(String name, Long hosts)`, `record VlanRequest(Integer id, String name, Long hosts)` ;
  - `record SubnetEntry(Integer vlanId, String name, long requestedHosts, String gateway, String cidr, String networkAddress, String subnetMask, String wildcardMask, String broadcastAddress, String firstUsable, String lastUsable, long usableHosts, long totalAddresses, long freeHosts)` avec `static from(AllocatedSubnet)` ;
  - `record VlsmResponse(String network, String mode, List<SubnetEntry> subnets, long usedAddresses, long remainingAddresses, List<String> warnings)` avec `static from(VlsmPlan)` ;
  - `ValidationErrors.from(BindingResult) : List<InputError>` ;
  - dans `ApiController`, la méthode privée `VlsmPlan plan(VlsmRequest)`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiVlsmEndpointTest {

    @Autowired
    MockMvc mvc;

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(post("/api/vlsm").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void requirementsForm() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24",
                  "requirements": [ { "name": "LAN-A", "hosts": 50 }, { "name": "LAN-B", "hosts": 20 },
                                    { "name": "LAN-C", "hosts": 10 }, { "name": "WAN", "hosts": 2 } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.subnets[0].cidr").value("192.168.10.0/26"))
                .andExpect(jsonPath("$.subnets[3].cidr").value("192.168.10.112/30"))
                .andExpect(jsonPath("$.subnets[0].vlanId").doesNotExist())
                .andExpect(jsonPath("$.subnets[0].gateway").doesNotExist())
                .andExpect(jsonPath("$.usedAddresses").value(116))
                .andExpect(jsonPath("$.remainingAddresses").value(140));
    }

    @Test
    void vlansForm() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24",
                  "vlans": [ { "id": 10, "name": "Compta", "hosts": 50 }, { "id": 20, "hosts": 20 } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("VLAN"))
                .andExpect(jsonPath("$.subnets[0].name").value("Compta"))
                .andExpect(jsonPath("$.subnets[1].name").value("VLAN20"))
                .andExpect(jsonPath("$.subnets[1].gateway").value("192.168.10.65"));
    }

    @Test
    void shortcutFormIsTheReferenceExample() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlanCount": 4, "hostsPerVlan": 30 }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subnets", hasSize(4)))
                .andExpect(jsonPath("$.subnets[0].vlanId").value(10))
                .andExpect(jsonPath("$.subnets[0].name").value("VLAN10"))
                .andExpect(jsonPath("$.subnets[0].requestedHosts").value(30))
                .andExpect(jsonPath("$.subnets[0].gateway").value("192.168.10.1"))
                .andExpect(jsonPath("$.subnets[0].cidr").value("192.168.10.0/26"))
                .andExpect(jsonPath("$.subnets[0].subnetMask").value("255.255.255.192"))
                .andExpect(jsonPath("$.subnets[0].wildcardMask").value("0.0.0.63"))
                .andExpect(jsonPath("$.subnets[0].broadcastAddress").value("192.168.10.63"))
                .andExpect(jsonPath("$.subnets[0].lastUsable").value("192.168.10.62"))
                .andExpect(jsonPath("$.subnets[0].usableHosts").value(62))
                .andExpect(jsonPath("$.subnets[0].totalAddresses").value(64))
                .andExpect(jsonPath("$.subnets[0].freeHosts").value(31))
                .andExpect(jsonPath("$.subnets[3].vlanId").value(40))
                .andExpect(jsonPath("$.usedAddresses").value(256))
                .andExpect(jsonPath("$.remainingAddresses").value(0))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void twoFormsAtOnceIsAmbiguous() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 5 } ], "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_REQUEST"));
    }

    @Test
    void noFormAtAll() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_REQUIREMENTS"));
    }

    @Test
    void shortcutNeedsBothFields() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("hostsPerVlan"));
    }

    @Test
    void annotationErrorUsesTheBusinessCode() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 0 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOSTS_NOT_POSITIVE"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].hosts"))
                .andExpect(jsonPath("$.errors[0].message").value("Au moins 1 hôte requis"));
    }

    @Test
    void severalAnnotationErrorsGiveValidationFailed() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 5000, "hosts": 0 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("2 erreurs dans la requête"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void severalModelErrorsGiveValidationFailed() throws Exception {
        postJson("""
                { "network": "300.1.1.1/24", "vlans": [ { "id": 10, "name": "a;b", "hosts": 5 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("OCTET_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_NAME"));
    }

    @Test
    void tooManyRowsHasTheSameCodeAsTheModel() throws Exception {
        String vlans = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "{ \"id\": " + i + ", \"hosts\": 1 }")
                .collect(Collectors.joining(","));
        postJson("{ \"network\": \"10.0.0.0/8\", \"vlans\": [" + vlans + "] }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUIREMENTS"));
    }

    @Test
    void nullElementInAListIsAMissingField() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ null ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0]"));
    }

    @Test
    void networkTooSmallIsA400() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "requirements": [ { "name": "A", "hosts": 200 }, { "name": "B", "hosts": 50 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NETWORK_TOO_SMALL"));
    }

    @Test
    void malformedJson() throws Exception {
        postJson("{ \"network\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void textWhereANumberIsExpected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": "abc" } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].hosts"));
    }

    @Test
    void decimalHostsAreRejected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 2.5 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }

    @Test
    void hugeNumberIsATypeMismatch() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 99999999999999999999 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "host": 30 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].host"))
                .andExpect(jsonPath("$.detail").value("Champ inconnu : host"));
    }

    @Test
    void emptyBody() throws Exception {
        postJson("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_BODY"));
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=ApiVlsmEndpointTest`
Expected : échec de compilation, `cannot find symbol: class VlsmRequest` (ou équivalent).

- [ ] **Step 3 : Écrire `messages.properties`** (`src/main/resources/messages.properties`)

```properties
# Messages des annotations de validation (gabarit {error.CODE}).
# Pas d'apostrophe droite : Spring les interprète dans certains cas (MessageFormat).
error.MISSING_FIELD=Ce champ est obligatoire
error.HOSTS_NOT_POSITIVE=Au moins 1 hôte requis
error.VLAN_ID_OUT_OF_RANGE=ID de VLAN entre 1 et 4094
error.TOO_MANY_REQUIREMENTS=Maximum 100 sous-réseaux
error.VLAN_COUNT_OUT_OF_RANGE=Le nombre de VLAN doit être entre 1 et 100

# Formulaires web : texte saisi dans un champ numérique
typeMismatch=Doit être un nombre entier
```

- [ ] **Step 4 : Écrire les DTO de requête**

`RequirementRequest.java` :

```java
package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Le nom n'a pas d'annotation : c'est le modèle qui le valide (codes EMPTY_NAME / INVALID_NAME). */
public record RequirementRequest(
        @Schema(example = "LAN-A") String name,
        @NotNull(message = "{error.MISSING_FIELD}")
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "50") Long hosts) {
}
```

`VlanRequest.java` :

```java
package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VlanRequest(
        @NotNull(message = "{error.MISSING_FIELD}")
        @Min(value = 1, message = "{error.VLAN_ID_OUT_OF_RANGE}")
        @Max(value = 4094, message = "{error.VLAN_ID_OUT_OF_RANGE}")
        @Schema(example = "10") Integer id,
        @Schema(example = "Compta", description = "Facultatif : VLAN<id> par défaut") String name,
        @NotNull(message = "{error.MISSING_FIELD}")
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "30", description = "Hôtes hors passerelle") Long hosts) {
}
```

`VlsmRequest.java` :

```java
package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Exactement une des trois formes : requirements, vlans, ou vlanCount + hostsPerVlan.
 * Le réseau n'a pas d'annotation : le modèle renvoie EMPTY_INPUT, comme partout ailleurs.
 */
public record VlsmRequest(
        @Schema(example = "192.168.10.0/24") String network,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid RequirementRequest> requirements,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid VlanRequest> vlans,
        @Min(value = 1, message = "{error.VLAN_COUNT_OUT_OF_RANGE}")
        @Max(value = 100, message = "{error.VLAN_COUNT_OUT_OF_RANGE}")
        @Schema(example = "4") Integer vlanCount,
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "30") Long hostsPerVlan) {
}
```

- [ ] **Step 5 : Écrire les DTO de réponse**

`SubnetEntry.java` :

```java
package com.coco.subnet.dto;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;

/** Une ligne du plan d'adressage. vlanId et gateway valent null hors mode VLAN. */
public record SubnetEntry(Integer vlanId,
                          String name,
                          long requestedHosts,
                          String gateway,
                          String cidr,
                          String networkAddress,
                          String subnetMask,
                          String wildcardMask,
                          String broadcastAddress,
                          String firstUsable,
                          String lastUsable,
                          long usableHosts,
                          long totalAddresses,
                          long freeHosts) {

    public static SubnetEntry from(AllocatedSubnet allocated) {
        Requirement r = allocated.requirement();
        SubnetInfo s = allocated.subnet();
        return new SubnetEntry(r.vlanId(), r.name(), r.hosts(),
                allocated.gateway() == null ? null : allocated.gateway().toString(),
                s.cidr().toString(), s.networkAddress().toString(), s.subnetMask().toString(),
                s.wildcardMask().toString(), s.broadcastAddress().toString(), s.firstUsable().toString(),
                s.lastUsable().toString(), s.usableHosts(), s.totalAddresses(), allocated.freeHosts());
    }
}
```

`VlsmResponse.java` :

```java
package com.coco.subnet.dto;

import com.coco.subnet.model.VlsmPlan;

import java.util.List;

public record VlsmResponse(String network,
                           String mode,
                           List<SubnetEntry> subnets,
                           long usedAddresses,
                           long remainingAddresses,
                           List<String> warnings) {

    public static VlsmResponse from(VlsmPlan plan) {
        return new VlsmResponse(plan.network().toString(), plan.mode().name(),
                plan.subnets().stream().map(SubnetEntry::from).toList(),
                plan.usedAddresses(), plan.remainingAddresses(), plan.warnings());
    }
}
```

- [ ] **Step 6 : Écrire `ValidationErrors.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import jakarta.validation.ConstraintViolation;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traduit les erreurs @Valid en InputError, en retrouvant le code depuis le gabarit {error.CODE}. */
final class ValidationErrors {

    private static final Pattern TEMPLATE = Pattern.compile("\\{error\\.([A-Z_]+)}");

    private ValidationErrors() {
    }

    static List<InputError> from(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(error -> new InputError(codeOf(error), error.getField(), error.getDefaultMessage()))
                .toList();
    }

    static ErrorCode codeOf(FieldError error) {
        if (error.contains(ConstraintViolation.class)) {
            Matcher matcher = TEMPLATE.matcher(error.unwrap(ConstraintViolation.class).getMessageTemplate());
            if (matcher.matches()) {
                return ErrorCode.valueOf(matcher.group(1));
            }
        }
        return ErrorCode.MISSING_FIELD;
    }
}
```

- [ ] **Step 7 : Remplacer `ApiExceptionHandler.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs survenues une fois la requête arrivée dans ApiController. */
@RestControllerAdvice(assignableTypes = ApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ProblemDetail> invalidInput(InvalidInputException ex) {
        return ApiProblems.badRequest(ex.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        return ApiProblems.badRequest(ValidationErrors.from(ex.getBindingResult()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException ex) {
        return ApiProblems.badRequest(ErrorCode.MISSING_PARAMETER, ex.getParameterName(),
                "Paramètre obligatoire manquant : " + ex.getParameterName());
    }

    /** Du plus précis au plus général : UnrecognizedPropertyException hérite de MismatchedInputException. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadableBody(HttpMessageNotReadableException ex) {
        UnrecognizedPropertyException unknown = findCause(ex, UnrecognizedPropertyException.class);
        if (unknown != null) {
            return ApiProblems.badRequest(ErrorCode.UNKNOWN_FIELD, path(unknown), "Champ inconnu : " + unknown.getPropertyName());
        }
        MismatchedInputException mismatch = findCause(ex, MismatchedInputException.class);
        if (mismatch != null) {
            String field = path(mismatch);
            return ApiProblems.badRequest(ErrorCode.TYPE_MISMATCH, field, "Type invalide pour le champ « " + field + " »");
        }
        if (findCause(ex, InputCoercionException.class) != null) {
            return ApiProblems.badRequest(ErrorCode.TYPE_MISMATCH, null, "Nombre hors limites dans la requête");
        }
        if (ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            return ApiProblems.badRequest(ErrorCode.MISSING_BODY, null, "Le corps de la requête est obligatoire");
        }
        return ApiProblems.badRequest(ErrorCode.MALFORMED_JSON, null, "JSON mal formé");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Erreur inattendue dans l'API", ex);
        return ApiProblems.internalError();
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    /** Chemin Jackson → notation des champs, par exemple vlans[0].hosts. */
    private static String path(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
```

- [ ] **Step 8 : Remplacer `ApiController.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.dto.VlsmRequest;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Calculateur", description = "Calcul CIDR, découpage VLSM et plan par VLAN")
public class ApiController {

    private final SubnetService subnetService;
    private final VlsmService vlsmService;
    private final CsvExporter csvExporter;

    public ApiController(SubnetService subnetService, VlsmService vlsmService, CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping(value = "/subnets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calcule un réseau CIDR",
            description = "Adresse réseau, masque, wildcard, broadcast, plage utilisable et nombre d'hôtes.")
    public SubnetResponse subnet(@Parameter(example = "192.168.10.0/24") @RequestParam String cidr) {
        return SubnetResponse.from(subnetService.calculate(cidr));
    }

    @PostMapping(value = "/vlsm", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Découpe un réseau (VLSM ou VLAN)",
            description = "Envoyer une seule forme : requirements, vlans, ou vlanCount + hostsPerVlan.")
    public VlsmResponse vlsm(@Valid @RequestBody VlsmRequest request) {
        return VlsmResponse.from(plan(request));
    }

    /** Choisit la forme de la requête, puis délègue au service. */
    private VlsmPlan plan(VlsmRequest request) {
        boolean hasRequirements = request.requirements() != null;
        boolean hasVlans = request.vlans() != null;
        boolean hasShortcut = request.vlanCount() != null || request.hostsPerVlan() != null;
        int forms = (hasRequirements ? 1 : 0) + (hasVlans ? 1 : 0) + (hasShortcut ? 1 : 0);
        if (forms > 1) {
            throw InvalidInputException.of(ErrorCode.AMBIGUOUS_REQUEST, null,
                    "Envoyez une seule forme : requirements, vlans ou vlanCount + hostsPerVlan");
        }
        if (forms == 0) {
            throw InvalidInputException.of(ErrorCode.NO_REQUIREMENTS, "vlans", "Ajoutez au moins un VLAN ou un besoin");
        }
        if (hasRequirements) {
            List<Requirement> requirements = request.requirements().stream()
                    .map(r -> r == null ? null : new Requirement(null, r.name(), r.hosts()))
                    .toList();
            return vlsmService.allocate(request.network(), PlanMode.REQUIREMENTS, requirements);
        }
        if (hasVlans) {
            List<Requirement> vlans = request.vlans().stream()
                    .map(v -> v == null ? null : new Requirement(v.id(), v.name(), v.hosts()))
                    .toList();
            return vlsmService.allocate(request.network(), PlanMode.VLAN, vlans);
        }
        return vlsmService.allocate(request.network(), PlanMode.VLAN,
                vlsmService.generateUniformVlans(request.vlanCount(), request.hostsPerVlan()));
    }
}
```

- [ ] **Step 9 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 10 : Commit**

```bash
git add src
git commit -m "feat: add POST /api/vlsm with validation and JSON error mapping"
```

---

### Task 14 : API `POST /api/vlsm/csv`

**Notion :** renvoyer un fichier avec `ResponseEntity<byte[]>` et l'en-tête `Content-Disposition`, et la négociation de contenu (`produces`).

**Files :**
- Modify : `src/main/java/com/coco/subnet/controller/ApiController.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiCsvEndpointTest.java`

**Interfaces :**
- Consumes : `CsvExporter.toBytes`, `fileName` et `ApiController.plan` (tâche 13).

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiCsvEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void downloadsTheCsvFile() throws Exception {
        MvcResult result = mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"192.168.10.0/24\", \"vlanCount\": 4, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"plan-adressage-192.168.10.0-24.csv\"")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("\r\n40;VLAN40;30;192.168.10.192/26;");
    }

    @Test
    void errorsAreJsonEvenOnTheCsvEndpoint() throws Exception {
        mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"192.168.10.0/24\", \"vlanCount\": 0, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VLAN_COUNT_OUT_OF_RANGE"));
    }

    @Test
    void clientAcceptingOnlyCsvStillGetsTheRightStatus() throws Exception {
        mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON).accept("text/csv")
                        .content("{ \"network\": \"300.1.1.1/24\", \"vlanCount\": 4, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=ApiCsvEndpointTest`
Expected : FAIL, `downloadsTheCsvFile` reçoit 404 ou 405 au lieu de 200.

- [ ] **Step 3 : Ajouter la route dans `ApiController`**

Ajoute ces imports :

```java
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
```

Ajoute cette méthode juste après `vlsm(...)` :

```java
    @PostMapping(value = "/vlsm/csv", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/csv")
    @Operation(summary = "Plan d'adressage au format CSV",
            description = "Même requête que /api/vlsm. Les erreurs sont renvoyées en application/problem+json.")
    public ResponseEntity<byte[]> vlsmCsv(@Valid @RequestBody VlsmRequest request) {
        VlsmPlan plan = plan(request);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(csvExporter.fileName(plan)).build().toString())
                .body(csvExporter.toBytes(plan));
    }
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: add CSV export endpoint"
```

---

### Task 15 : Erreurs de routage (404/405/406/415), 413 et 500

**Notion :** une erreur peut survenir **avant** que Spring choisisse un controller. On voit donc la différence entre un `@ControllerAdvice` global et un advice limité à un controller, puis les filtres de servlet, qui s'exécutent encore plus tôt.

**Files :**
- Create : `src/main/java/com/coco/subnet/controller/PayloadTooLargeException.java`
- Create : `src/main/java/com/coco/subnet/controller/RoutingErrorHandler.java`
- Create : `src/main/java/com/coco/subnet/config/LimitedServletInputStream.java`
- Create : `src/main/java/com/coco/subnet/config/RequestSizeFilter.java`
- Modify : `src/main/java/com/coco/subnet/controller/ApiProblems.java`
- Modify : `src/main/java/com/coco/subnet/controller/ApiExceptionHandler.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiRoutingErrorsTest.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiUnexpectedErrorTest.java`
- Test : `src/test/java/com/coco/subnet/controller/ApiExceptionHandlerTest.java`
- Test : `src/test/java/com/coco/subnet/config/RequestSizeFilterTest.java`

**Interfaces :**
- Produces :
  - `ApiProblems.routing(Exception)` (404/405/406/415), `ApiProblems.payloadTooLarge()` et `ApiProblems.write(ResponseEntity<ProblemDetail>, HttpServletResponse, ObjectMapper)` ;
  - `PayloadTooLargeException extends IOException`, avec le constructeur `(long maxBytes)` ;
  - `RequestSizeFilter.MAX_BODY_BYTES = 65_536`.

- [ ] **Step 1 : Écrire les tests qui échouent**

`ApiRoutingErrorsTest.java` :

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiRoutingErrorsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unknownEndpoint() throws Exception {
        mvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void wrongMethod() throws Exception {
        mvc.perform(delete("/api/vlsm"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void bodyThatIsNotJson() throws Exception {
        mvc.perform(post("/api/vlsm").contentType(MediaType.TEXT_PLAIN).content("bonjour"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unsatisfiableAcceptHeader() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "10.0.0.0/8").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void bodyLargerThan64KiloBytes() throws Exception {
        mvc.perform(post("/api/vlsm").contentType(MediaType.APPLICATION_JSON).content("x".repeat(70_000)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }
}
```

`ApiUnexpectedErrorTest.java` :

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({VlsmService.class, CsvExporter.class})
class ApiUnexpectedErrorTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SubnetService subnetService;

    @Test
    void unexpectedErrorRevealsNothingInternal() throws Exception {
        when(subnetService.calculate(anyString())).thenThrow(new IllegalStateException("secret interne"));

        mvc.perform(get("/api/subnets").param("cidr", "10.0.0.0/8"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Une erreur interne est survenue"))
                .andExpect(content().string(not(containsString("secret interne"))));
    }
}
```

`ApiExceptionHandlerTest.java` :

```java
package com.coco.subnet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void streamedBodyCutByTheFilterIsA413() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("I/O error",
                new PayloadTooLargeException(65_536), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ProblemDetail> response = handler.unreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    void rawPayloadTooLargeIsA413() {
        assertThat(handler.payloadTooLarge().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
```

`RequestSizeFilterTest.java` :

```java
package com.coco.subnet.config;

import com.coco.subnet.controller.PayloadTooLargeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeFilterTest {

    private final RequestSizeFilter filter = new RequestSizeFilter(new ObjectMapper());

    /** Requête « chunked » : la taille n'est pas annoncée dans Content-Length. */
    private static MockHttpServletRequest chunked(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vlsm") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[size]);
        return request;
    }

    @Test
    void streamedBodyOverTheLimitIsCut() {
        FilterChain readAll = (req, res) -> req.getInputStream().readAllBytes();

        assertThatThrownBy(() -> filter.doFilter(chunked(70_000), new MockHttpServletResponse(), readAll))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void streamedBodyUnderTheLimitPasses() throws Exception {
        int[] read = new int[1];
        FilterChain readAll = (req, res) -> read[0] = req.getInputStream().readAllBytes().length;

        filter.doFilter(chunked(60_000), new MockHttpServletResponse(), readAll);

        assertThat(read[0]).isEqualTo(60_000);
    }

    @Test
    void singleByteReadsAreCountedToo() {
        FilterChain readByByte = (req, res) -> {
            while (req.getInputStream().read() != -1) {
                // lecture octet par octet
            }
        };

        assertThatThrownBy(() -> filter.doFilter(chunked(70_000), new MockHttpServletResponse(), readByByte))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void webPagesAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/vlsm");
        request.setContent(new byte[70_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
```

- [ ] **Step 2 : Lancer les tests pour les voir échouer**

Run : `./mvnw test -Dtest="ApiRoutingErrorsTest,ApiUnexpectedErrorTest,ApiExceptionHandlerTest,RequestSizeFilterTest"`
Expected : échec de compilation, `cannot find symbol: class PayloadTooLargeException`.

- [ ] **Step 3 : Écrire `PayloadTooLargeException.java`**

```java
package com.coco.subnet.controller;

import java.io.IOException;

/** Levée pendant la lecture d'un corps de requête qui dépasse la limite autorisée. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(long maxBytes) {
        super("Corps de requête supérieur à " + maxBytes + " octets");
    }
}
```

- [ ] **Step 4 : Ajouter dans `ApiProblems`**

Ajoute ces imports :

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
```

Ajoute ces méthodes :

```java
    public static ResponseEntity<ProblemDetail> payloadTooLarge() {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Requête trop volumineuse", ErrorCode.PAYLOAD_TOO_LARGE,
                "Le corps de la requête dépasse 64 Ko", List.of(), new HttpHeaders());
    }

    /** Erreurs qui surviennent pendant le choix du controller. */
    public static ResponseEntity<ProblemDetail> routing(Exception ex) {
        if (ex instanceof HttpRequestMethodNotSupportedException methodEx) {
            HttpHeaders headers = new HttpHeaders();
            Set<HttpMethod> allowed = methodEx.getSupportedHttpMethods();
            if (allowed != null) {
                headers.setAllow(allowed);
            }
            return build(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée", ErrorCode.METHOD_NOT_ALLOWED,
                    "La méthode " + methodEx.getMethod() + " n'est pas autorisée sur cette URL", List.of(), headers);
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Format de requête non supporté", ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Le corps de la requête doit être en JSON (Content-Type: application/json)", List.of(), new HttpHeaders());
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return build(HttpStatus.NOT_ACCEPTABLE, "Format de réponse non disponible", ErrorCode.NOT_ACCEPTABLE,
                    "Le format demandé dans l'en-tête Accept n'est pas disponible", List.of(), new HttpHeaders());
        }
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable", ErrorCode.NOT_FOUND,
                "Aucune ressource à cette adresse", List.of(), new HttpHeaders());
    }

    /** Écrit la réponse à la main : utilisé là où Spring MVC ne peut pas le faire (filtre, avant le routage). */
    public static void write(ResponseEntity<ProblemDetail> entity, HttpServletResponse response, ObjectMapper mapper)
            throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), toJson(entity.getBody()));
    }

    private static Map<String, Object> toJson(ProblemDetail problem) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", problem.getType().toString());
        json.put("title", problem.getTitle());
        json.put("status", problem.getStatus());
        json.put("detail", problem.getDetail());
        json.putAll(problem.getProperties());
        return json;
    }
```

- [ ] **Step 5 : Écrire `RoutingErrorHandler.java`**

```java
package com.coco.subnet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Erreurs levées avant qu'un controller soit choisi : un advice limité à ApiController ne les verrait pas.
 * Sous /api : JSON. Pages web : redirection (mauvaise méthode) ou page d'erreur de Spring Boot.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RoutingErrorHandler {

    private final ObjectMapper objectMapper;

    public RoutingErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class, HttpMediaTypeNotSupportedException.class})
    public void handle(Exception ex, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            ApiProblems.write(ApiProblems.routing(ex), response, objectMapper);
            return;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            response.sendRedirect(request.getContextPath() + (path.startsWith("/vlsm") ? "/vlsm" : "/"));
            return;
        }
        // Relancer : Spring Boot affiche alors templates/error/404.html
        throw ex;
    }
}
```

- [ ] **Step 6 : Écrire `LimitedServletInputStream.java`**

```java
package com.coco.subnet.config;

import com.coco.subnet.controller.PayloadTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;

/** Compte les octets lus et s'arrête au-delà de la limite (requêtes sans Content-Length). */
final class LimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long count;

    LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        if (b != -1) {
            add(1);
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = delegate.read(buffer, offset, length);
        if (n > 0) {
            add(n);
        }
        return n;
    }

    private void add(long n) throws PayloadTooLargeException {
        count += n;
        if (count > maxBytes) {
            throw new PayloadTooLargeException(maxBytes);
        }
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
        delegate.setReadListener(listener);
    }
}
```

- [ ] **Step 7 : Écrire `RequestSizeFilter.java`**

```java
package com.coco.subnet.config;

import com.coco.subnet.controller.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Limite le corps des requêtes /api/** à 64 Ko. S'exécute avant Spring MVC : écrit lui-même sa réponse. */
@Component
public class RequestSizeFilter extends OncePerRequestFilter {

    public static final long MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    public RequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            ApiProblems.write(ApiProblems.payloadTooLarge(), response, objectMapper);
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedServletInputStream(super.getInputStream(), MAX_BODY_BYTES);
            }
            return stream;
        }
    }
}
```

- [ ] **Step 8 : Compléter `ApiExceptionHandler`**

Ajoute ces imports :

```java
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

Au **début** de `unreadableBody`, avant la recherche d'`UnrecognizedPropertyException`, ajoute :

```java
        if (findCause(ex, PayloadTooLargeException.class) != null) {
            return ApiProblems.payloadTooLarge();
        }
```

Ajoute ces méthodes juste avant `unexpected(...)` :

```java
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ProblemDetail> payloadTooLarge() {
        return ApiProblems.payloadTooLarge();
    }

    /** Elles peuvent aussi survenir une fois ApiController choisi : ne jamais les laisser devenir des 500. */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ProblemDetail> routing(Exception ex) {
        return ApiProblems.routing(ex);
    }
```

- [ ] **Step 9 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. Tous les tests `Api*` et `RequestSizeFilterTest` sont verts.

- [ ] **Step 10 : Commit**

```bash
git add src
git commit -m "feat: handle routing errors, payload limit and unexpected errors in the API"
```

---

### Task 16 : Documentation Swagger / OpenAPI

**Notion :** springdoc génère la documentation OpenAPI à partir des annotations. On écrit aussi un test de démarrage complet, avec un vrai serveur lancé sur un port aléatoire.

**Files :**
- Create : `src/main/java/com/coco/subnet/config/OpenApiConfig.java`
- Test : `src/test/java/com/coco/subnet/ApplicationSmokeTest.java`

**Interfaces :**
- Produces : `/v3/api-docs` (titre « Subnet Calculator API », chemins `/api/**` uniquement) et `/swagger-ui/index.html`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void swaggerUiIsServed() {
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiDocumentsOnlyTheApi() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("Subnet Calculator API", "/api/subnets", "/api/vlsm", "/api/vlsm/csv")
                .doesNotContain("\"/vlsm\"");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=ApplicationSmokeTest`
Expected : FAIL dans `openApiDocumentsOnlyTheApi`, car le titre « Subnet Calculator API » est absent.

- [ ] **Step 3 : Écrire `OpenApiConfig.java`**

```java
package com.coco.subnet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subnetCalculatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Subnet Calculator API")
                .version("1.0")
                .description("Calcul CIDR, découpage VLSM et plan d'adressage par VLAN (IPv4). "
                        + "Les erreurs suivent le format RFC 9457 (application/problem+json) avec un champ « code »."));
    }
}
```

- [ ] **Step 4 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add src
git commit -m "feat: document the API with OpenAPI"
```

---

### Task 17 : Page web CIDR (Thymeleaf)

**Notion :** le cycle MVC côté serveur. `@Controller` et `Model` d'un côté, `@ModelAttribute` et `BindingResult` de l'autre, avec `th:object`, `th:field` et `th:errors` dans le template, et des fragments réutilisables.

**Files :**
- Create : `src/main/java/com/coco/subnet/dto/CidrForm.java`
- Create : `src/main/java/com/coco/subnet/controller/WebController.java`
- Create : `src/main/resources/templates/fragments/layout.html`
- Create : `src/main/resources/templates/index.html`
- Create : `src/main/resources/static/css/style.css`
- Test : `src/test/java/com/coco/subnet/controller/WebCidrPageTest.java`

**Interfaces :**
- Consumes : `SubnetService.calculate`, `SubnetResponse.from`, `InvalidInputException`.
- Produces :
  - `WebController` avec `GET /` et `POST /` (vue `index`, attributs `cidrForm` et `result`) ;
  - la méthode statique de paquet `WebController.reject(BindingResult, InvalidInputException)`, qui ignore les champs déjà en erreur ;
  - `static String toFormField(String)`.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebCidrPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void showsTheEmptyForm() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("cidrForm"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("Calcul CIDR")));
    }

    @Test
    void showsTheResult() throws Exception {
        MvcResult result = mvc.perform(post("/").param("cidr", "192.168.10.0/24"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("192.168.10.255")))
                .andReturn();

        SubnetResponse response = (SubnetResponse) result.getModelAndView().getModel().get("result");
        assertThat(response.usableHosts()).isEqualTo(254);
    }

    @Test
    void showsTheCorrectionWarning() throws Exception {
        mvc.perform(post("/").param("cidr", "192.168.10.37/24"))
                .andExpect(content().string(containsString("Adresse corrigée vers le réseau 192.168.10.0/24")));
    }

    @Test
    void showsTheErrorUnderTheFieldAndKeepsTheInput() throws Exception {
        mvc.perform(post("/").param("cidr", "300.1.1.1/24"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cidrForm", "cidr", "OCTET_OUT_OF_RANGE"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("octet 300 est hors limites")))
                .andExpect(content().string(containsString("value=\"300.1.1.1/24\"")));
    }

    @Test
    void emptyFormShowsAMessage() throws Exception {
        mvc.perform(post("/").param("cidr", ""))
                .andExpect(model().attributeHasFieldErrorCode("cidrForm", "cidr", "EMPTY_INPUT"));
    }

    @Test
    void mapsModelFieldsToFormFields() {
        assertThat(WebController.toFormField("vlans[2].id")).isEqualTo("rows[2].vlanId");
        assertThat(WebController.toFormField("requirements[0].hosts")).isEqualTo("rows[0].hosts");
        assertThat(WebController.toFormField("vlans")).isEqualTo("rows");
        assertThat(WebController.toFormField("network")).isEqualTo("network");
        assertThat(WebController.toFormField(null)).isEqualTo("rows");
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=WebCidrPageTest`
Expected : échec de compilation, `cannot find symbol: class WebController`.

- [ ] **Step 3 : Écrire `CidrForm.java`**

```java
package com.coco.subnet.dto;

/** Formulaire de la page CIDR (JavaBean : Spring le remplit avec les champs postés). */
public class CidrForm {

    private String cidr = "";

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }
}
```

- [ ] **Step 4 : Écrire `WebController.java`**

```java
package com.coco.subnet.controller;

import com.coco.subnet.dto.CidrForm;
import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Pages web : chaque action remplit le Model, puis renvoie le nom de la vue Thymeleaf. */
@Controller
public class WebController {

    private final SubnetService subnetService;
    private final VlsmService vlsmService;
    private final CsvExporter csvExporter;

    public WebController(SubnetService subnetService, VlsmService vlsmService, CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping("/")
    public String cidrPage(Model model) {
        model.addAttribute("cidrForm", new CidrForm());
        return "index";
    }

    @PostMapping("/")
    public String calculateCidr(@ModelAttribute("cidrForm") CidrForm form, BindingResult binding, Model model) {
        try {
            model.addAttribute("result", SubnetResponse.from(subnetService.calculate(form.getCidr())));
        } catch (InvalidInputException e) {
            reject(binding, e);
        }
        return "index";
    }

    /** Place chaque erreur du modèle sous son champ, sauf si ce champ a déjà une erreur (ex. typeMismatch). */
    static void reject(BindingResult binding, InvalidInputException e) {
        for (InputError error : e.errors()) {
            String field = toFormField(error.field());
            if (!binding.hasFieldErrors(field)) {
                binding.rejectValue(field, error.code().name(), error.message());
            }
        }
    }

    /** Noms de champs du modèle (vlans[0].id) → noms du formulaire web (rows[0].vlanId). */
    static String toFormField(String field) {
        if (field == null) {
            return "rows";
        }
        return field.replaceFirst("^(vlans|requirements)", "rows").replace(".id", ".vlanId");
    }
}
```

`vlsmService` et `csvExporter` ne servent pas encore : ils seront utilisés aux tâches 18 et 19.

- [ ] **Step 5 : Écrire `templates/fragments/layout.html`**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="${title} + ' — Calculateur de sous-réseaux'">Calculateur de sous-réseaux</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<header th:fragment="header" class="site-header">
    <a class="brand" th:href="@{/}">Calculateur de sous-réseaux</a>
    <nav>
        <a th:href="@{/}">CIDR</a>
        <a th:href="@{/vlsm}">VLSM / VLAN</a>
        <a th:href="@{/swagger-ui.html}">API</a>
    </nav>
</header>
<footer th:fragment="footer" class="site-footer">
    <p>IPv4 · CIDR · VLSM · VLAN — préparez vos plans d'adressage avant Packet Tracer.</p>
</footer>
</body>
</html>
```

- [ ] **Step 6 : Écrire `templates/index.html`**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('CIDR')}"></head>
<body>
<header th:replace="~{fragments/layout :: header}"></header>
<main>
    <h1>Calcul CIDR</h1>

    <form th:action="@{/}" th:object="${cidrForm}" method="post" class="card">
        <label for="cidr">Adresse au format CIDR</label>
        <input id="cidr" type="text" th:field="*{cidr}" placeholder="192.168.10.0/24" autocomplete="off"
               th:classappend="${#fields.hasErrors('cidr')} ? 'invalid'">
        <p class="error" th:if="${#fields.hasErrors('cidr')}" th:errors="*{cidr}">Erreur</p>
        <button type="submit">Calculer</button>
    </form>

    <section th:if="${result}" class="card">
        <p class="warning" th:each="warning : ${result.warnings}" th:text="${warning}">Avertissement</p>
        <table class="details">
            <tr><th>Réseau</th><td th:text="${result.cidr}"></td></tr>
            <tr><th>Adresse réseau</th><td th:text="${result.networkAddress}"></td></tr>
            <tr><th>Masque</th><td th:text="${result.subnetMask}"></td></tr>
            <tr><th>Masque générique (wildcard)</th><td th:text="${result.wildcardMask}"></td></tr>
            <tr><th>Broadcast</th><td th:text="${result.broadcastAddress}"></td></tr>
            <tr><th>Première adresse utilisable</th><td th:text="${result.firstUsable}"></td></tr>
            <tr><th>Dernière adresse utilisable</th><td th:text="${result.lastUsable}"></td></tr>
            <tr><th>Hôtes utilisables</th><td th:text="${result.usableHosts}"></td></tr>
            <tr><th>Adresses au total</th><td th:text="${result.totalAddresses}"></td></tr>
        </table>
    </section>
</main>
<footer th:replace="~{fragments/layout :: footer}"></footer>
</body>
</html>
```

- [ ] **Step 7 : Écrire `static/css/style.css`**

```css
:root {
    --bg: #f6f7f9;
    --card: #ffffff;
    --text: #1d2330;
    --muted: #5b6475;
    --accent: #1f6feb;
    --error: #c62828;
    --error-bg: #fdecea;
    --warning-bg: #fff4d6;
    --border: #d8dce3;
}

* { box-sizing: border-box; }

body {
    margin: 0;
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.5;
}

.site-header {
    display: flex;
    flex-wrap: wrap;
    gap: 1rem;
    align-items: center;
    justify-content: space-between;
    padding: 0.75rem 1rem;
    background: var(--text);
}
.site-header a { color: #fff; text-decoration: none; margin-right: 1rem; }
.site-header .brand { font-weight: 700; }

main { max-width: 64rem; margin: 0 auto; padding: 1rem; }

.card {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1rem;
    margin-bottom: 1rem;
    overflow-x: auto;
}

label { display: block; font-weight: 600; margin: 0.75rem 0 0.25rem; }
label.checkbox { font-weight: 400; }
input[type=text] { width: 100%; max-width: 24rem; padding: 0.5rem; border: 1px solid var(--border); border-radius: 4px; }
input.invalid, .row-error input { border-color: var(--error); }

button {
    margin-top: 0.75rem;
    padding: 0.5rem 1rem;
    border: 0;
    border-radius: 4px;
    background: var(--accent);
    color: #fff;
    cursor: pointer;
}
button.secondary { background: #e7ebf1; color: var(--text); }
button.link { background: none; color: var(--error); padding: 0; margin: 0; }
.default-submit { position: absolute; left: -9999px; }

.error { color: var(--error); margin: 0.25rem 0; }
.error-summary { background: var(--error-bg); border-left: 4px solid var(--error); padding: 0.5rem 1rem; }
.warning { background: var(--warning-bg); padding: 0.5rem 1rem; border-radius: 4px; }

fieldset { border: 1px solid var(--border); border-radius: 6px; margin-top: 1rem; }

table { border-collapse: collapse; width: 100%; margin-top: 0.75rem; }
th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid var(--border); white-space: nowrap; }
table.details th { width: 40%; color: var(--muted); font-weight: 600; }
.row-error { background: var(--error-bg); }

.actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }

.site-footer { text-align: center; color: var(--muted); padding: 2rem 1rem; }
```

- [ ] **Step 8 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

- [ ] **Step 9 : Vérifier à la main**

Run : `./mvnw spring-boot:run`, puis ouvre http://localhost:8080/.
- Saisis `192.168.10.37/24` : tu dois voir l'avertissement et le résultat.
- Saisis `300.1.1.1/24` : le message d'erreur s'affiche en rouge sous le champ, et ta saisie reste dans le champ.

Arrête le serveur avec `Ctrl+C`.

- [ ] **Step 10 : Commit**

```bash
git add src
git commit -m "feat: add CIDR web page with Thymeleaf layout"
```

---

### Task 18 : Page web VLSM / VLAN

**Notion :** lier un formulaire contenant une liste (`rows[0].hosts`), utiliser plusieurs boutons d'envoi dans un même formulaire (`name="action"`), et gérer un état sans JavaScript, uniquement côté serveur.

**Files :**
- Create : `src/main/java/com/coco/subnet/dto/RowForm.java`
- Create : `src/main/java/com/coco/subnet/dto/VlsmForm.java`
- Create : `src/main/resources/templates/vlsm.html`
- Modify : `src/main/java/com/coco/subnet/controller/WebController.java`
- Test : `src/test/java/com/coco/subnet/controller/WebVlsmPageTest.java`

**Interfaces :**
- Consumes : `VlsmService.allocate`, `generateUniformVlans`, `nextVlanId`, `VlsmResponse.from`, et `WebController.reject` (tâche 17).
- Produces :
  - `VlsmForm`, un JavaBean avec `network`, `useVlans`, `vlanCount`, `hostsPerVlan` et `rows`, plus les méthodes `PlanMode mode()`, `List<Requirement> toRequirements()` et `List<Integer> vlanIds()` ;
  - `RowForm(Integer vlanId, String name, Long hosts)` ;
  - les routes `GET /vlsm` et `POST /vlsm` (paramètre `action` : `addRow`, `removeRow-<i>`, `generate`, `calculate`, `refresh`) ;
  - la méthode privée `Optional<VlsmPlan> calculate(VlsmForm, BindingResult)`, que la tâche 19 réutilise.

- [ ] **Step 1 : Écrire le test qui échoue**

```java
package com.coco.subnet.controller;

import com.coco.subnet.dto.RowForm;
import com.coco.subnet.dto.SubnetEntry;
import com.coco.subnet.dto.VlsmForm;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebVlsmPageTest {

    @Autowired
    MockMvc mvc;

    /** Formulaire VLAN avec deux lignes : VLAN 10 (30 hôtes) et VLAN 20 « RH » (20 hôtes). */
    private static MockHttpServletRequestBuilder vlanForm(String action) {
        return vlanForm(action, "20", "30");
    }

    /**
     * Variante paramétrable. Ajouter .param() sur un champ déjà posté ajouterait une 2e valeur
     * (Spring garderait la première) : on passe donc les valeurs à modifier ici.
     */
    private static MockHttpServletRequestBuilder vlanForm(String action, String secondVlanId, String firstHosts) {
        return post("/vlsm")
                .param("network", "192.168.10.0/24")
                .param("useVlans", "true").param("_useVlans", "on")
                .param("rows[0].vlanId", "10").param("rows[0].name", "").param("rows[0].hosts", firstHosts)
                .param("rows[1].vlanId", secondVlanId).param("rows[1].name", "RH").param("rows[1].hosts", "20")
                .param("action", action);
    }

    private static Map<String, Object> model(MvcResult result) {
        return result.getModelAndView().getModel();
    }

    private static VlsmForm form(MvcResult result) {
        return (VlsmForm) model(result).get("vlsmForm");
    }

    @Test
    void showsTheFormWithOneVlanRow() throws Exception {
        MvcResult result = mvc.perform(get("/vlsm"))
                .andExpect(status().isOk())
                .andExpect(view().name("vlsm"))
                .andExpect(content().string(containsString("Hôtes (hors passerelle)")))
                .andReturn();

        assertThat(form(result).isUseVlans()).isTrue();
        assertThat(form(result).getRows()).hasSize(1);
        assertThat(form(result).getRows().get(0).getVlanId()).isEqualTo(10);
    }

    @Test
    void addRowProposesTheNextVlanId() throws Exception {
        MvcResult result = mvc.perform(vlanForm("addRow")).andExpect(status().isOk()).andReturn();

        assertThat(form(result).getRows()).hasSize(3);
        assertThat(form(result).getRows().get(2).getVlanId()).isEqualTo(30);
        assertThat(model(result)).doesNotContainKey("plan");
    }

    @Test
    void addRowWithoutVlansHasNoId() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "10.0.0.0/24").param("_useVlans", "on")
                        .param("action", "addRow"))
                .andReturn();

        assertThat(form(result).isUseVlans()).isFalse();
        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly((Integer) null);
    }

    @Test
    void removeRowRemovesTheRightLine() throws Exception {
        MvcResult result = mvc.perform(vlanForm("removeRow-0")).andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(20);
    }

    @Test
    void staleOrForgedRemoveRowIsIgnored() throws Exception {
        for (String action : new String[]{"removeRow-99", "removeRow--1", "removeRow-abc", "inconnu", "refresh"}) {
            MvcResult result = mvc.perform(vlanForm(action)).andExpect(status().isOk()).andReturn();

            assertThat(form(result).getRows()).as(action).hasSize(2);
        }
    }

    @Test
    void generateCreatesDetailedRows() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on")
                        .param("vlanCount", "4").param("hostsPerVlan", "30").param("action", "generate"))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(10, 20, 30, 40);
        assertThat(form(result).getRows()).extracting(RowForm::getName).containsExactly("VLAN10", "VLAN20", "VLAN30", "VLAN40");
        assertThat(form(result).getRows()).extracting(RowForm::getHosts).containsOnly(30L);
    }

    @Test
    void generateWithAnInvalidCountShowsTheErrorAndKeepsTheRows() throws Exception {
        MvcResult result = mvc.perform(vlanForm("generate").param("vlanCount", "0").param("hostsPerVlan", "30"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "vlanCount", "VLAN_COUNT_OUT_OF_RANGE"))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void generateWithTextShowsATypeMismatch() throws Exception {
        MvcResult result = mvc.perform(vlanForm("generate").param("vlanCount", "4").param("hostsPerVlan", "abc"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "hostsPerVlan", "typeMismatch"))
                .andExpect(content().string(containsString("Doit être un nombre entier")))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void calculateShowsThePlan() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("192.168.10.64/27")))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) model(result).get("plan");
        assertThat(plan.subnets()).extracting(SubnetEntry::cidr).containsExactly("192.168.10.0/26", "192.168.10.64/27");
        assertThat(plan.subnets()).extracting(SubnetEntry::gateway).containsExactly("192.168.10.1", "192.168.10.65");
    }

    @Test
    void calculateWithoutVlans() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24").param("_useVlans", "on")
                        .param("rows[0].name", "LAN-A").param("rows[0].hosts", "50")
                        .param("rows[1].name", "WAN").param("rows[1].hosts", "2")
                        .param("action", "calculate"))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) model(result).get("plan");
        assertThat(plan.mode()).isEqualTo("REQUIREMENTS");
        assertThat(plan.subnets()).extracting(SubnetEntry::cidr).containsExactly("192.168.10.0/26", "192.168.10.64/30");
    }

    @Test
    void modelErrorsAreShownOnTheRightRowField() throws Exception {
        mvc.perform(vlanForm("calculate", "10", "30"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[1].vlanId", "DUPLICATE_VLAN_ID"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andExpect(content().string(containsString("row-error")));
    }

    @Test
    void emptyFormShowsAnErrorOnEveryField() throws Exception {
        mvc.perform(post("/vlsm").param("network", "").param("useVlans", "true").param("_useVlans", "on")
                        .param("rows[0].vlanId", "").param("rows[0].hosts", "").param("action", "calculate"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "network", "EMPTY_INPUT"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].vlanId", "MISSING_FIELD"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].hosts", "MISSING_FIELD"))
                .andExpect(content().string(containsString("Corrigez les erreurs suivantes")));
    }

    @Test
    void textInHostsShowsOnlyTheTypeMismatch() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate", "20", "abc"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].hosts", "typeMismatch"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andReturn();

        assertThat(result.getModelAndView().getModel().toString()).doesNotContain("le nombre d'hôtes est obligatoire");
    }

    @Test
    void noRowsAtAll() throws Exception {
        mvc.perform(post("/vlsm").param("network", "10.0.0.0/24").param("_useVlans", "on").param("action", "calculate"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows", "NO_REQUIREMENTS"));
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `./mvnw test -Dtest=WebVlsmPageTest`
Expected : échec de compilation, `cannot find symbol: class VlsmForm`.

- [ ] **Step 3 : Écrire `RowForm.java`**

```java
package com.coco.subnet.dto;

/** Une ligne du tableau VLSM/VLAN dans le formulaire web. */
public class RowForm {

    private Integer vlanId;
    private String name;
    private Long hosts;

    public RowForm() {
    }

    public RowForm(Integer vlanId, String name, Long hosts) {
        this.vlanId = vlanId;
        this.name = name;
        this.hosts = hosts;
    }

    public Integer getVlanId() {
        return vlanId;
    }

    public void setVlanId(Integer vlanId) {
        this.vlanId = vlanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getHosts() {
        return hosts;
    }

    public void setHosts(Long hosts) {
        this.hosts = hosts;
    }
}
```

- [ ] **Step 4 : Écrire `VlsmForm.java`**

```java
package com.coco.subnet.dto;

import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;

import java.util.ArrayList;
import java.util.List;

/** Formulaire de la page VLSM/VLAN. Pas d'annotations : c'est le modèle qui valide (mêmes messages partout). */
public class VlsmForm {

    private String network = "";
    private boolean useVlans = true;
    private Integer vlanCount;
    private Long hostsPerVlan;
    private List<RowForm> rows = new ArrayList<>();

    public PlanMode mode() {
        return useVlans ? PlanMode.VLAN : PlanMode.REQUIREMENTS;
    }

    public List<Requirement> toRequirements() {
        return rows.stream()
                .map(row -> new Requirement(useVlans ? row.getVlanId() : null, row.getName(), row.getHosts()))
                .toList();
    }

    public List<Integer> vlanIds() {
        return rows.stream().map(RowForm::getVlanId).toList();
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public boolean isUseVlans() {
        return useVlans;
    }

    public void setUseVlans(boolean useVlans) {
        this.useVlans = useVlans;
    }

    public Integer getVlanCount() {
        return vlanCount;
    }

    public void setVlanCount(Integer vlanCount) {
        this.vlanCount = vlanCount;
    }

    public Long getHostsPerVlan() {
        return hostsPerVlan;
    }

    public void setHostsPerVlan(Long hostsPerVlan) {
        this.hostsPerVlan = hostsPerVlan;
    }

    public List<RowForm> getRows() {
        return rows;
    }

    public void setRows(List<RowForm> rows) {
        this.rows = rows;
    }
}
```

- [ ] **Step 5 : Ajouter les routes VLSM dans `WebController`**

Ajoute ces imports :

```java
import com.coco.subnet.dto.RowForm;
import com.coco.subnet.dto.VlsmForm;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
```

Ajoute ces méthodes juste après `calculateCidr(...)` :

```java
    @GetMapping("/vlsm")
    public String vlsmPage(Model model) {
        VlsmForm form = new VlsmForm();
        form.getRows().add(new RowForm(10, "", null));
        model.addAttribute("vlsmForm", form);
        return "vlsm";
    }

    /** Un seul formulaire, plusieurs boutons : le paramètre « action » dit lequel a été cliqué. */
    @PostMapping("/vlsm")
    public String vlsmAction(@ModelAttribute("vlsmForm") VlsmForm form, BindingResult binding,
                             @RequestParam(defaultValue = "calculate") String action, Model model) {
        switch (action) {
            case "addRow" -> form.getRows().add(
                    new RowForm(form.isUseVlans() ? vlsmService.nextVlanId(form.vlanIds()) : null, "", null));
            case "generate" -> generate(form, binding);
            case "calculate" -> calculate(form, binding)
                    .ifPresent(plan -> model.addAttribute("plan", VlsmResponse.from(plan)));
            default -> removeRow(form, action); // "refresh" et actions inconnues : simple réaffichage
        }
        return "vlsm";
    }

    private void generate(VlsmForm form, BindingResult binding) {
        if (binding.hasFieldErrors("vlanCount") || binding.hasFieldErrors("hostsPerVlan")) {
            return;
        }
        try {
            List<Requirement> vlans = vlsmService.generateUniformVlans(form.getVlanCount(), form.getHostsPerVlan());
            form.setUseVlans(true);
            form.setRows(vlans.stream()
                    .map(v -> new RowForm(v.vlanId(), v.name(), v.hosts()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        } catch (InvalidInputException e) {
            reject(binding, e);
        }
    }

    /** Lance toujours le modèle, pour afficher toutes les erreurs en une fois (même sur un formulaire vide). */
    private Optional<VlsmPlan> calculate(VlsmForm form, BindingResult binding) {
        try {
            VlsmPlan plan = vlsmService.allocate(form.getNetwork(), form.mode(), form.toRequirements());
            return binding.hasErrors() ? Optional.empty() : Optional.of(plan);
        } catch (InvalidInputException e) {
            reject(binding, e);
            return Optional.empty();
        }
    }

    private static void removeRow(VlsmForm form, String action) {
        if (!action.startsWith("removeRow-")) {
            return;
        }
        try {
            int index = Integer.parseInt(action.substring("removeRow-".length()));
            if (index >= 0 && index < form.getRows().size()) {
                form.getRows().remove(index);
            }
        } catch (NumberFormatException ignored) {
            // index falsifié : on réaffiche le formulaire sans rien changer
        }
    }
```

- [ ] **Step 6 : Écrire `templates/vlsm.html`**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('VLSM / VLAN')}"></head>
<body>
<header th:replace="~{fragments/layout :: header}"></header>
<main>
    <h1>Découpage VLSM / VLAN</h1>

    <form th:action="@{/vlsm}" th:object="${vlsmForm}" method="post" class="card">
        <!-- Bouton par défaut (touche Entrée) : calculer -->
        <button type="submit" name="action" value="calculate" class="default-submit" tabindex="-1" aria-hidden="true">Calculer</button>

        <div class="error-summary" th:if="${#fields.hasAnyErrors()}">
            <p>Corrigez les erreurs suivantes :</p>
            <ul>
                <li th:each="err : ${#fields.allErrors()}" th:text="${err}">Erreur</li>
            </ul>
        </div>

        <label for="network">Réseau à découper</label>
        <input id="network" type="text" th:field="*{network}" placeholder="192.168.10.0/24" autocomplete="off"
               th:classappend="${#fields.hasErrors('network')} ? 'invalid'">
        <p class="error" th:if="${#fields.hasErrors('network')}" th:errors="*{network}"></p>

        <label class="checkbox"><input type="checkbox" th:field="*{useVlans}"> Utiliser des VLAN</label>
        <button type="submit" name="action" value="refresh" class="secondary">Mettre à jour le formulaire</button>

        <fieldset th:if="*{useVlans}">
            <legend>Raccourci : même nombre d'hôtes pour tous les VLAN</legend>
            <label for="vlanCount">Nombre de VLAN</label>
            <input id="vlanCount" type="text" inputmode="numeric" th:field="*{vlanCount}"
                   th:classappend="${#fields.hasErrors('vlanCount')} ? 'invalid'">
            <p class="error" th:if="${#fields.hasErrors('vlanCount')}" th:errors="*{vlanCount}"></p>
            <label for="hostsPerVlan">Hôtes par VLAN (hors passerelle)</label>
            <input id="hostsPerVlan" type="text" inputmode="numeric" th:field="*{hostsPerVlan}"
                   th:classappend="${#fields.hasErrors('hostsPerVlan')} ? 'invalid'">
            <p class="error" th:if="${#fields.hasErrors('hostsPerVlan')}" th:errors="*{hostsPerVlan}"></p>
            <button type="submit" name="action" value="generate" class="secondary">Générer les lignes</button>
        </fieldset>

        <p class="error" th:if="${#fields.hasErrors('rows')}" th:errors="*{rows}"></p>
        <table class="rows">
            <thead>
            <tr>
                <th th:if="*{useVlans}">ID VLAN</th>
                <th>Nom</th>
                <th th:text="*{useVlans} ? 'Hôtes (hors passerelle)' : 'Hôtes'">Hôtes</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="row, stat : *{rows}"
                th:classappend="${#fields.hasErrors('rows[__${stat.index}__].*')} ? 'row-error'">
                <td th:if="*{useVlans}">
                    <input type="text" inputmode="numeric" th:field="*{rows[__${stat.index}__].vlanId}" aria-label="ID VLAN">
                    <p class="error" th:if="${#fields.hasErrors('rows[__${stat.index}__].vlanId')}"
                       th:errors="*{rows[__${stat.index}__].vlanId}"></p>
                </td>
                <td>
                    <input type="text" th:field="*{rows[__${stat.index}__].name}" aria-label="Nom">
                    <p class="error" th:if="${#fields.hasErrors('rows[__${stat.index}__].name')}"
                       th:errors="*{rows[__${stat.index}__].name}"></p>
                </td>
                <td>
                    <input type="text" inputmode="numeric" th:field="*{rows[__${stat.index}__].hosts}" aria-label="Hôtes">
                    <p class="error" th:if="${#fields.hasErrors('rows[__${stat.index}__].hosts')}"
                       th:errors="*{rows[__${stat.index}__].hosts}"></p>
                </td>
                <td>
                    <button type="submit" name="action" th:value="'removeRow-' + ${stat.index}" class="link">Supprimer</button>
                </td>
            </tr>
            </tbody>
        </table>

        <div class="actions">
            <button type="submit" name="action" value="addRow" class="secondary">Ajouter une ligne</button>
            <button type="submit" name="action" value="calculate">Calculer</button>
            <button type="submit" th:formaction="@{/vlsm/csv}" class="secondary">Exporter CSV</button>
        </div>
    </form>

    <section th:if="${plan}" class="card">
        <h2>Plan d'adressage — <span th:text="${plan.network}">réseau</span></h2>
        <p class="warning" th:each="warning : ${plan.warnings}" th:text="${warning}">Avertissement</p>
        <table class="results">
            <thead>
            <tr>
                <th th:if="${plan.mode == 'VLAN'}">VLAN</th>
                <th>Nom</th>
                <th>Hôtes demandés</th>
                <th>Réseau</th>
                <th>Masque</th>
                <th th:if="${plan.mode == 'VLAN'}">Passerelle</th>
                <th>Première</th>
                <th>Dernière</th>
                <th>Broadcast</th>
                <th>Libres</th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="s : ${plan.subnets}">
                <td th:if="${plan.mode == 'VLAN'}" th:text="${s.vlanId}"></td>
                <td th:text="${s.name}"></td>
                <td th:text="${s.requestedHosts}"></td>
                <td th:text="${s.cidr}"></td>
                <td th:text="${s.subnetMask}"></td>
                <td th:if="${plan.mode == 'VLAN'}" th:text="${s.gateway}"></td>
                <td th:text="${s.firstUsable}"></td>
                <td th:text="${s.lastUsable}"></td>
                <td th:text="${s.broadcastAddress}"></td>
                <td th:text="${s.freeHosts}"></td>
            </tr>
            </tbody>
        </table>
        <p>Adresses utilisées : <strong th:text="${plan.usedAddresses}">0</strong> —
            restantes : <strong th:text="${plan.remainingAddresses}">0</strong></p>
    </section>
</main>
<footer th:replace="~{fragments/layout :: footer}"></footer>
</body>
</html>
```

- [ ] **Step 7 : Lancer les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`.

Si `${s.vlanId}` ne s'affiche pas (accès aux composants de record via SpEL), arrête-toi et signale-le. Ne transforme pas les records en JavaBeans sans en avoir discuté.

- [ ] **Step 8 : Vérifier à la main**

Run : `./mvnw spring-boot:run`, puis ouvre http://localhost:8080/vlsm.
1. Saisis le réseau `192.168.10.0/24`, 4 VLAN de 30 hôtes, et clique sur « Générer les lignes » : 4 lignes apparaissent (VLAN 10 à 40).
2. Clique sur « Calculer » : les 4 sous-réseaux sont des /26, et leurs passerelles sont `.1`, `.65`, `.129` et `.193`.
3. Remplace un nombre d'hôtes par `abc`, puis clique sur « Calculer » : la ligne passe en rouge avec « Doit être un nombre entier ».
4. Décoche « Utiliser des VLAN » et clique sur « Mettre à jour le formulaire » : la colonne ID disparaît.

- [ ] **Step 9 : Commit**

```bash
git add src
git commit -m "feat: add VLSM/VLAN web page with server-side row actions"
```

---

### Task 19 : Export CSV depuis la page web et pages d'erreur

**Notion :** écrire un fichier directement dans `HttpServletResponse`, et utiliser les pages d'erreur par convention de Spring Boot (`templates/error/404.html`).

**Files :**
- Create : `src/main/resources/templates/error/404.html`
- Create : `src/main/resources/templates/error/500.html`
- Modify : `src/main/java/com/coco/subnet/controller/WebController.java`
- Modify : `src/test/java/com/coco/subnet/ApplicationSmokeTest.java`
- Test : `src/test/java/com/coco/subnet/controller/WebCsvAndRoutingTest.java`
- Test : `src/test/java/com/coco/subnet/WebErrorPageTest.java`

**Interfaces :**
- Consumes : `WebController.calculate` (tâche 18), `CsvExporter`, `RoutingErrorHandler` (tâche 15).
- Produces : la route `POST /vlsm/csv`.

- [ ] **Step 1 : Écrire les tests qui échouent**

`WebCsvAndRoutingTest.java` :

```java
package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebCsvAndRoutingTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exportsTheCsvOfTheSubmittedPlan() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm/csv").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "Compta").param("rows[0].hosts", "30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("plan-adressage-192.168.10.0-24.csv")))
                .andReturn();

        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("10;Compta;30;192.168.10.0/26;");
    }

    @Test
    void invalidPlanReturnsToTheFormWithoutAFile() throws Exception {
        mvc.perform(post("/vlsm/csv").param("network", "300.1.1.1/24").param("_useVlans", "on")
                        .param("rows[0].name", "A").param("rows[0].hosts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("vlsm"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "network", "OCTET_OUT_OF_RANGE"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void getOnTheCsvUrlRedirectsToTheVlsmPage() throws Exception {
        mvc.perform(get("/vlsm/csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vlsm"));
    }

    @Test
    void wrongMethodOnTheHomePageRedirectsHome() throws Exception {
        mvc.perform(put("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unknownWebPageIs404() throws Exception {
        mvc.perform(get("/toto")).andExpect(status().isNotFound());
    }
}
```

`WebErrorPageTest.java` :

```java
package com.coco.subnet;

import com.coco.subnet.service.SubnetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebErrorPageTest {

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    SubnetService subnetService;

    @Test
    void unexpectedErrorShowsTheFriendly500Page() {
        when(subnetService.calculate(anyString())).thenThrow(new IllegalStateException("secret interne"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("cidr", "10.0.0.0/8");

        ResponseEntity<String> response = rest.postForEntity("/", new HttpEntity<>(form, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Erreur interne").doesNotContain("secret interne", "Exception");
    }
}
```

Ajoute ces tests à la fin de `ApplicationSmokeTest` :

```java
    @Test
    void webPagesAreServed() {
        assertThat(rest.getForEntity("/", String.class).getBody()).contains("Calcul CIDR");
        assertThat(rest.getForEntity("/vlsm", String.class).getBody()).contains("Découpage VLSM / VLAN");
    }

    @Test
    void unknownWebPageShowsTheFriendly404Page() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = rest.exchange("/toto", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Page introuvable");
    }

    @Test
    void unknownApiUrlIsJson() {
        ResponseEntity<String> response = rest.getForEntity("/api/toto", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
    }
```

Ajoute aussi ces imports dans `ApplicationSmokeTest` :

```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
```

- [ ] **Step 2 : Lancer les tests pour les voir échouer**

Run : `./mvnw test -Dtest="WebCsvAndRoutingTest,WebErrorPageTest,ApplicationSmokeTest"`
Expected : FAIL. `exportsTheCsvOfTheSubmittedPlan` reçoit une redirection (la route POST `/vlsm/csv` n'existe pas encore), et `unknownWebPageShowsTheFriendly404Page` ne trouve pas « Page introuvable ».

- [ ] **Step 3 : Ajouter l'export CSV dans `WebController`**

Ajoute ces imports :

```java
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
```

Ajoute cette méthode juste après `vlsmAction(...)` :

```java
    /** Succès : le fichier est écrit dans la réponse (on renvoie null). Échec : retour au formulaire. */
    @PostMapping("/vlsm/csv")
    public String exportCsv(@ModelAttribute("vlsmForm") VlsmForm form, BindingResult binding,
                            HttpServletResponse response) throws IOException {
        Optional<VlsmPlan> plan = calculate(form, binding);
        if (plan.isEmpty()) {
            return "vlsm";
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(csvExporter.fileName(plan.get())).build().toString());
        response.getOutputStream().write(csvExporter.toBytes(plan.get()));
        return null;
    }
```

- [ ] **Step 4 : Écrire `templates/error/404.html`**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('Page introuvable')}"></head>
<body>
<header th:replace="~{fragments/layout :: header}"></header>
<main>
    <section class="card">
        <h1>Page introuvable</h1>
        <p>Cette adresse n'existe pas.</p>
        <p><a th:href="@{/}">Retour à l'accueil</a></p>
    </section>
</main>
<footer th:replace="~{fragments/layout :: footer}"></footer>
</body>
</html>
```

- [ ] **Step 5 : Écrire `templates/error/500.html`**

```html
<!DOCTYPE html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('Erreur interne')}"></head>
<body>
<header th:replace="~{fragments/layout :: header}"></header>
<main>
    <section class="card">
        <h1>Erreur interne</h1>
        <p>Une erreur inattendue est survenue. Réessayez, ou revenez à l'accueil.</p>
        <p><a th:href="@{/}">Retour à l'accueil</a></p>
    </section>
</main>
<footer th:replace="~{fragments/layout :: footer}"></footer>
</body>
</html>
```

- [ ] **Step 6 : Lancer tous les tests**

Run : `./mvnw verify`
Expected : `BUILD SUCCESS`. Tous les tests passent et la couverture est à 100 % sur `model` et `service`.

- [ ] **Step 7 : Commit**

```bash
git add src
git commit -m "feat: export CSV from the web page and add friendly error pages"
```

---

### Task 20 : Vérification finale et README

**Notion :** relire la spec et la confronter à ce qui a été construit, puis mettre la documentation à jour.

**Files :**
- Modify : `README.md`

- [ ] **Step 1 : Lancer le build complet**

Run : `./mvnw clean verify`
Expected : `BUILD SUCCESS`. Ouvre ensuite `target/site/jacoco/index.html` : les paquets `com.coco.subnet.model` et `com.coco.subnet.service` doivent être à 100 % (lignes et branches).

- [ ] **Step 2 : Faire la recette à la main**

Run : `./mvnw spring-boot:run`
- **Page CIDR** (http://localhost:8080/) : `192.168.10.0/24`, `192.168.10.37/24`, `10.0.0.0/31`, `300.1.1.1/24`.
- **Page VLSM** (http://localhost:8080/vlsm) : l'exemple 50/20/10/2 sans VLAN, puis 4 VLAN × 30 hôtes, puis « Exporter CSV ». Ouvre le fichier dans Excel : les accents et les colonnes doivent s'afficher correctement.
- **Swagger** (http://localhost:8080/swagger-ui.html) : lance `GET /api/subnets` et `POST /api/vlsm` avec les exemples, puis essaie une requête invalide.
- **Page inconnue** : http://localhost:8080/toto doit afficher « Page introuvable ».

- [ ] **Step 3 : Mettre à jour `README.md`**

Remplace le bloc de statut :

```markdown
> **Status:** design phase. The specification is written and reviewed; implementation has not started yet.
```

par :

```markdown
> **Status:** v1 implemented — CIDR, VLSM, VLAN, CSV export, web UI and REST API, with 100 % test coverage on the domain and service layers.
```

Remplace le titre `## API overview (planned)` par `## API overview`, et `## Getting started (once implemented)` par `## Getting started`. Dans la feuille de route, coche la v1 : `- [x] v1 — ...`.

- [ ] **Step 4 : Commit**

```bash
git add README.md
git commit -m "docs: mark v1 as implemented in README"
```
