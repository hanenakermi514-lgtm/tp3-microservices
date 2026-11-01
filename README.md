# TP3 REST : Microservices « Calculatrice » avec Spring Boot

**Faculté des Sciences de Sfax – Département d'informatique et des Communications**
**Matière :** Architecture MicroServices | **Filière :** MPSRCC1 | **Année universitaire :** 2026-2027
**Enseignantes :** Corinne Amel ZAYANI, Amal ABID
**Étudiant(e) :** Hanen Akermi

---

## Sommaire

1. [Présentation](#1-présentation)
2. [Structure du dépôt](#2-structure-du-dépôt)
3. [Partie A : Du service web au microservice](#3-partie-a--du-service-web-au-microservice)
4. [Partie B : Création de calcul-service](#4-partie-b--création-de-calcul-service)
5. [Partie C : Premier microservice (la somme)](#5-partie-c--premier-microservice--la-somme)
6. [Partie D : Compléter la calculatrice](#6-partie-d--compléter-la-calculatrice)
7. [Partie E : Échanger des données en JSON](#7-partie-e--échanger-des-données-en-json)
8. [Partie F : historique-service](#8-partie-f--historique-service)
9. [Questions de synthèse](#9-questions-de-synthèse)
10. [Compilation et lancement](#10-compilation-et-lancement)
11. [Captures d'écran](#11-captures-décran)

---

## 1. Présentation

Ce TP reprend la calculatrice REST du TP2 (JAX-RS + Tomcat + fichier `.war`) et la réalise sous forme de **deux microservices Spring Boot indépendants** :

| Microservice | Port | Rôle |
|---|---|---|
| `calcul-service` | 8081 | Effectue les quatre calculs (`/calcul/...`) |
| `historique-service` | 8082 | Enregistre les opérations (`/operations`), sans logique de calcul : il appelle `calcul-service` par HTTP |

```
                 ┌──────────────────────────┐
                 │ Client (navigateur, curl, │
                 │ Postman)                  │
                 └──────┬─────────────┬─────┘
                  HTTP  │             │  HTTP
          ┌─────────────▼───┐   ┌─────▼────────────────┐
          │ calcul-service  │◄──┤ historique-service   │
          │ port 8081       │GET│ port 8082            │
          │ /calcul         │   │ /operations          │
          │ Calculatrice    │   │ GET /calcul/... ──►  │
          └─────────────────┘   └──────────────────────┘
```

**Outils :** JDK 17+/21, Maven 3.9+, IntelliJ IDEA, Postman, curl. **Spring Boot : 4.0.9.** Aucun Tomcat externe n'est nécessaire (Tomcat embarqué dans le `.jar`).

---

## 2. Structure du dépôt

```
.
├── README.md
├── calcul-service/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/exemple/calcul/
│       │   ├── CalculServiceApplication.java
│       │   ├── controller/CalculController.java
│       │   ├── service/Calculatrice.java
│       │   └── model/Resultat.java
│       └── resources/application.properties
└── historique-service/
    ├── pom.xml
    └── src/main/
        ├── java/com/exemple/historique/
        │   ├── HistoriqueServiceApplication.java
        │   ├── controller/OperationController.java
        │   ├── service/OperationService.java
        │   ├── client/CalculClient.java
        │   └── model/Operation.java
        └── resources/application.properties
```

---

## 3. Partie A : Du service web au microservice

### A.1 / A.2 Architecture

- **TP2** : une seule application `RestWebApp.war` (classe `Calculatrice`, ressources `/calcul` et `/operations`) déployée dans un Tomcat installé à part, port 8080.
- **TP3** : deux applications autonomes, chacune dans un `.jar` exécutable avec son propre Tomcat embarqué et son propre port. Elles communiquent par HTTP.

### A.3 Service web (TP2) vs microservice (TP3)

| Aspect | Service web du TP2 | Microservice du TP3 |
|---|---|---|
| Technologie | JAX-RS (Jersey) | Spring Boot (Spring MVC) |
| Découpage | Une seule application (`.war`) | Deux applications indépendantes |
| Résultat de la construction | `.war` (archive web) | `.jar` exécutable (application + serveur) |
| Serveur | Tomcat installé à part | Tomcat embarqué, `java -jar` |
| Port | 8080 partagé | Un port par microservice (8081, 8082) |
| Adresse | `http://localhost:8080/RestWebApp/api/calcul/somme` | `http://localhost:8081/calcul/somme` |
| Activation | `RestApplication` + `@ApplicationPath` | `@SpringBootApplication` + `main` |
| Ressource REST | `@Path` | `@RestController` + `@RequestMapping` |
| Verbes HTTP | `@GET`, `@POST`, `@PUT`, `@DELETE` | `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` |
| Paramètres | `@QueryParam`, `@PathParam`, `@Consumes` | `@RequestParam`, `@PathVariable`, `@RequestBody` |
| Réponse HTTP | `Response` | `ResponseEntity` |
| Conversion JSON | JSON-B | Jackson |
| Création des objets | `new`, attributs `static` | Objets gérés par Spring (`@Service`, injection par constructeur, singleton) |
| Communication | Appel de méthode Java | Requête HTTP entre microservices |

### A.4 Questions de conception

**1. Avantage et inconvénient du calcul via HTTP.**
- *Avantage :* `calcul-service` est indépendant : on peut le développer, le redéployer, le mettre à l'échelle (plusieurs instances) ou le remplacer sans toucher à `historique-service`, et une panne reste isolée.
- *Inconvénient :* un appel réseau est beaucoup plus lent et moins fiable qu'un appel de méthode (latence, timeouts, service indisponible), et il faut gérer ces erreurs ; l'ensemble est aussi plus complexe à déployer et à superviser.

**2. Si `calcul-service` est arrêté :**
- *Échouent :* toutes les requêtes sur `:8081/calcul/...` (connexion refusée), ainsi que `POST :8082/operations` et `PUT :8082/operations/{id}` (elles ont besoin d'un calcul → réponse 503).
- *Continuent de fonctionner :* `GET :8082/operations`, `GET :8082/operations/{id}` et `DELETE :8082/operations/{id}`, qui n'utilisent que les données en mémoire de `historique-service`.

**3. Pourquoi pas le même port ?** Un port ne peut être ouvert (« bind ») que par un seul processus à la fois sur une machine. Le second microservice échouerait au démarrage avec `Port 808x was already in use`.

**4. Code HTTP quand `calcul-service` est injoignable :** **503 Service Unavailable** (le client n'est pas en faute, et ce n'est pas un bug du serveur : une dépendance est temporairement indisponible).

---

## 4. Partie B : Création de calcul-service

### B.1 Spring Initializr (https://start.spring.io)

| Champ | Valeur |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | **4.0.9** |
| Group | `com.exemple` |
| Artifact / Name | `calcul-service` |
| Package name | `com.exemple.calcul` |
| Packaging | Jar |
| Java | 17 ou 21 (version du JDK) |
| Dependencies | Spring Web |

Le projet est ensuite ouvert dans IntelliJ IDEA (`File > Open`).

### B.3 Le `pom.xml`

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.9</version>
    <relativePath/>
</parent>

<groupId>com.exemple</groupId>
<artifactId>calcul-service</artifactId>
<version>0.0.1-SNAPSHOT</version>

<properties>
    <java.version>17</java.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Points retenus :
- `spring-boot-starter-parent` fixe des versions compatibles de toutes les bibliothèques (d'où l'absence de `<version>` sur le starter).
- Un **starter** regroupe plusieurs dépendances : `spring-boot-starter-webmvc` apporte Spring MVC, Jackson et Tomcat embarqué (au TP2 : quatre dépendances + un Tomcat externe).
- Pas de `<packaging>war</packaging>` : le packaging par défaut est `jar`.
- `spring-boot-maven-plugin` rend le `.jar` exécutable (application + dépendances + Tomcat).

### B.4 Classe principale

```java
package com.exemple.calcul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CalculServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CalculServiceApplication.class, args);
    }
}
```

`@SpringBootApplication` active Spring, la configuration automatique et la recherche des classes annotées dans `com.exemple.calcul` et ses sous-paquetages. La méthode `main` démarre l'application et le Tomcat embarqué : cette classe remplace `RestApplication` **et** le serveur Tomcat du TP2.

---

## 5. Partie C : Premier microservice : la somme

### C.1 `Calculatrice` (paquetage `service`)

```java
package com.exemple.calcul.service;

import org.springframework.stereotype.Service;

@Service
public class Calculatrice {

    public int somme(int a, int b) {
        return a + b;
    }

    public int soustraction(int a, int b) {
        return a - b;
    }

    public int produit(int a, int b) {
        return a * b;
    }

    public double division(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division par 0 impossible");
        }
        return (double) a / b;
    }
}
```

`@Service` demande à Spring de créer un objet `Calculatrice` au démarrage et de l'injecter là où il est nécessaire (injection de dépendances).

### C.2 `CalculController` (paquetage `controller`) : version complète finale

```java
package com.exemple.calcul.controller;

import com.exemple.calcul.model.Resultat;
import com.exemple.calcul.service.Calculatrice;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calcul")
public class CalculController {

    private final Calculatrice calculService;

    // Injection par constructeur : Spring fournit l'objet Calculatrice
    public CalculController(Calculatrice calculService) {
        this.calculService = calculService;
    }

    // ---------- C : somme ----------
    @GetMapping(value = "/somme", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> somme(@RequestParam("a") int a,
                                        @RequestParam("b") int b) {
        int result = calculService.somme(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }

    // ---------- D.1 : soustraction et produit ----------
    @GetMapping(value = "/soustraction", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> soustraction(@RequestParam("a") int a,
                                               @RequestParam("b") int b) {
        int result = calculService.soustraction(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }

    @GetMapping(value = "/produit", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> produit(@RequestParam("a") int a,
                                          @RequestParam("b") int b) {
        int result = calculService.produit(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }

    // ---------- D.2 : division avec gestion d'erreur ----------
    @GetMapping(value = "/division", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> division(@RequestParam("a") int a,
                                           @RequestParam("b") int b) {
        if (b == 0) {
            return ResponseEntity.badRequest()
                    .body("Erreur : division par 0 impossible");
        }
        double result = calculService.division(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }

    // ---------- D.4 : paramètres dans le chemin ----------
    @GetMapping(value = "/produit/{a}/{b}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> produitChemin(@PathVariable("a") int a,
                                                @PathVariable("b") int b) {
        int result = calculService.produit(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }

    // ---------- E.2 : réponse JSON ----------
    @GetMapping("/somme/detail")
    public Resultat sommeDetail(@RequestParam("a") int a,
                                @RequestParam("b") int b) {
        return new Resultat("somme", a, b, calculService.somme(a, b));
    }

    // ---------- E.4 : exercice, division détaillée ----------
    @GetMapping("/division/detail")
    public ResponseEntity<Resultat> divisionDetail(@RequestParam("a") int a,
                                                   @RequestParam("b") int b) {
        if (b == 0) {
            return ResponseEntity.badRequest().build();
        }
        Resultat r = new Resultat("division", a, b, calculService.division(a, b));
        return ResponseEntity.ok(r);
    }
}
```

**Correspondance Spring / JAX-RS (tableau complété) :**

| Spring (TP3) | JAX-RS (TP2) | Rôle dans cet exemple |
|---|---|---|
| `@RestController` | (pas d'équivalent direct) | Déclare la classe comme contrôleur REST : Spring la détecte et écrit la valeur retournée dans le corps de la réponse HTTP (= `@Controller` + `@ResponseBody`) |
| `@RequestMapping("/calcul")` | `@Path("/calcul")` sur la classe | Préfixe commun de toutes les URI du contrôleur |
| `@GetMapping("/somme")` | `@GET` + `@Path("/somme")` | La méthode répond au verbe GET sur `/calcul/somme` |
| `produces = MediaType.TEXT_PLAIN_VALUE` | `@Produces(MediaType.TEXT_PLAIN)` | La réponse est du texte brut (`Content-Type: text/plain`) |
| `@RequestParam("a") int a` | `@QueryParam("a") int a` | Lit le paramètre `a` de la chaîne de requête (`?a=2`) |
| `ResponseEntity.ok(...)` | `Response.ok(...).build()` | Réponse de statut 200 avec un corps |

> Aucun `new Calculatrice()` : Spring crée un seul objet `Calculatrice` et un seul `CalculController` (singletons), réutilisés pour toutes les requêtes (JAX-RS créait un nouvel objet à chaque requête).

### C.3 Emplacement des classes

`Calculatrice` est dans `com.exemple.calcul.service` et `CalculController` dans `com.exemple.calcul.controller`, tous deux sous `com.exemple.calcul` (paquetage de `CalculServiceApplication`) : Spring les trouve automatiquement. Dans le cas contraire, toutes les requêtes répondraient 404.

### C.4 `application.properties` de calcul-service

```properties
spring.application.name=calcul-service
server.port=8081
```

### C.5 Lancement

| Méthode | Commande |
|---|---|
| IntelliJ | Flèche verte à côté du `main` de `CalculServiceApplication` |
| Maven | `mvn spring-boot:run` |
| `.jar` exécutable | `mvn clean package` puis `java -jar target/calcul-service-0.0.1-SNAPSHOT.jar` |

Lignes attendues dans la console :

```
... Tomcat started on port 8081 (http) with context path '/'
... Started CalculServiceApplication in 1.234 seconds
```

Changer le port sans modifier le fichier : `java -jar target/calcul-service-0.0.1-SNAPSHOT.jar --server.port=8083` (l'option de la ligne de commande l'emporte sur `application.properties`).

### C.6 Comprendre l'URL `http://localhost:8081/calcul/somme?a=2&b=3`

| Partie de l'URL | Origine |
|---|---|
| `localhost:8081` | Machine locale et port fixé par `server.port` dans `application.properties` |
| `/calcul` | Annotation `@RequestMapping("/calcul")` de la classe |
| `/somme` | Valeur de `@GetMapping("/somme")` de la méthode `somme` |
| `?a=2&b=3` | Chaîne de requête (*query string*) lue par les `@RequestParam("a")` et `@RequestParam("b")` |

**Question C.1 : comparaison avec l'URL du TP2** (`http://localhost:8080/RestWebApp/api/calcul/somme?a=2&b=3`).
Ont disparu :
- `/RestWebApp` : c'était le nom de l'application déployée (`RestWebApp.war`) dans Tomcat, qui servait de *context path*. Il n'y a plus de `.war` déployé dans un serveur : l'application est lancée seule et répond à la racine (`context path '/'`).
- `/api` : c'était la valeur de `@ApplicationPath("/api")` de la classe `RestApplication`, qui n'existe plus ; `@SpringBootApplication` ne définit aucun préfixe.
- Le port a aussi changé : 8080 (Tomcat partagé) → 8081 (port propre au microservice).

### C.7 Tests de la somme

```bash
curl -i "http://localhost:8081/calcul/somme?a=2&b=3"
```

Résultat attendu : `HTTP/1.1 200`, `Content-Type: text/plain;charset=UTF-8`, corps `5`. Dans le navigateur : la page affiche `5`. Dans Postman : `GET` + URL + *Send* → corps `5`, statut `200 OK`.

**Question C.2 : `POST` à la place de `GET`.** On obtient **405 Method Not Allowed** (la méthode `somme` n'est mappée que sur GET ; Spring ajoute l'en-tête `Allow: GET`). C'est **le même code qu'au TP2**.

---

## 6. Partie D : Compléter la calculatrice

### D.1 Soustraction et produit

| Requête | Résultat attendu |
|---|---|
| `GET /calcul/soustraction?a=10&b=4` | `6` |
| `GET /calcul/produit?a=6&b=7` | `42` |

### D.2 Division et gestion d'erreur

| Requête | Statut | Corps |
|---|---|---|
| `GET /calcul/division?a=7&b=2` | 200 | `3.5` |
| `GET /calcul/division?a=7&b=0` | 400 | `Erreur : division par 0 impossible` |

`ResponseEntity.badRequest().body(...)` équivaut à `Response.status(Response.Status.BAD_REQUEST).entity(...).build()` du TP2.

### D.3 Observations

| Requête | Statut | Corps |
|---|---|---|
| `GET /calcul/somme?a=2` (b absent) | 400 | JSON d'erreur (`MissingServletRequestParameterException`) |
| `GET /calcul/somme?a=abc&b=3` (a non entier) | 400 | JSON d'erreur (`MethodArgumentTypeMismatchException`) |

**Question D.1.** Dans les deux cas Spring **refuse la requête avec 400 Bad Request** : un paramètre `@RequestParam` est obligatoire par défaut, et une valeur non convertible en `int` est rejetée. JAX-RS, lui, utilisait la valeur par défaut 0 et exécutait quand même `somme`. L'attitude de Spring est **plus conforme à REST** : le client est informé explicitement que sa requête est incorrecte (4xx = faute du client), au lieu de recevoir un résultat faux (`0 + 3`) qui masque l'erreur.

**Question D.2.** Le JSON d'erreur par défaut de Spring Boot contient les champs :
`timestamp`, `status`, `error` (et `path`) ; selon la configuration, `message` et `trace` peuvent s'y ajouter. Exemple :

```json
{"timestamp":"2026-11-01T10:00:00.000+00:00","status":400,"error":"Bad Request","path":"/calcul/somme"}
```

### D.4 `@PathVariable`

`GET /calcul/produit/6/7` → `42` (méthode `produitChemin`, voir le code de `CalculController`).

**Question D.3 : correspondance.**

| Spring | JAX-RS |
|---|---|
| `@RequestParam` | `@QueryParam` |
| `@PathVariable` | `@PathParam` |

Pour lire l'identifiant dans `/operations/{id}`, on utilisera **`@PathVariable`**.

---

## 7. Partie E : Échanger des données en JSON

### E.1 Classe `Resultat` (paquetage `model`)

```java
package com.exemple.calcul.model;

public class Resultat {

    private String operation;
    private int a;
    private int b;
    private double resultat;

    // Constructeur sans argument : obligatoire pour la conversion JSON
    public Resultat() {
    }

    public Resultat(String operation, int a, int b, double resultat) {
        this.operation = operation;
        this.a = a;
        this.b = b;
        this.resultat = resultat;
    }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public int getA() { return a; }
    public void setA(int a) { this.a = a; }

    public int getB() { return b; }
    public void setB(int b) { this.b = b; }

    public double getResultat() { return resultat; }
    public void setResultat(double resultat) { this.resultat = resultat; }
}
```

### E.2 Méthode JSON

```bash
curl -i "http://localhost:8081/calcul/somme/detail?a=2&b=3"
```

Attendu : `200`, `Content-Type: application/json`, corps `{"operation":"somme","a":2,"b":3,"resultat":5.0}`.

**Question E.1.** Oui, les deux JSON sont **équivalents** : un objet JSON est un ensemble de paires clé/valeur non ordonné, l'ordre des champs n'a aucune importance pour le client. Cette fois, la conversion a été faite par **Jackson** (inclus dans `spring-boot-starter-webmvc`), et non par JSON-B.

### E.3 En-tête `Accept`

| Requête | Statut |
|---|---|
| `curl -i -H "Accept: application/json" ".../somme/detail?a=2&b=3"` | **200** |
| `curl -i -H "Accept: application/xml" ".../somme/detail?a=2&b=3"` | **406 Not Acceptable** |

**Question E.2.** Même sans `produces`, Spring ne dispose que des convertisseurs de messages présents dans le classpath : ici Jackson pour le **JSON** (pas de bibliothèque XML). Quand le client demande `application/xml`, aucun convertisseur ne sait produire ce format pour un objet `Resultat` ; Spring répond donc 406. Le format est imposé par les convertisseurs disponibles, pas par le code de la méthode.

### E.4 Exercice `divisionDetail`

Voir le code dans `CalculController` : `GET /calcul/division/detail?a=7&b=2` → `200` et `{"operation":"division","a":7,"b":2,"resultat":3.5}` ; `b=0` → `400` (corps vide).

---

## 8. Partie F : historique-service

### F.1 Projet et configuration

Projet généré comme en B.1 : Artifact `historique-service`, package `com.exemple.historique`, dépendance Spring Web, **Spring Boot 4.0.9**. Sous-paquetages : `controller`, `service`, `client`, `model`.

`src/main/resources/application.properties` :

```properties
spring.application.name=historique-service
server.port=8082
calcul.service.url=http://localhost:8081
```

La propriété `calcul.service.url` est personnalisée et lue dans le code avec `@Value` : l'adresse de `calcul-service` est dans la configuration, pas dans le code (déplaçable sans recompiler).

### F.2 `Operation` (paquetage `model`)

```java
package com.exemple.historique.model;

public class Operation {

    private int id;
    private String operateur; // somme, soustraction, produit ou division
    private int a;
    private int b;
    private double resultat;

    // Constructeur sans argument : obligatoire pour la conversion JSON
    public Operation() {
    }

    public Operation(int id, String operateur, int a, int b, double resultat) {
        this.id = id;
        this.operateur = operateur;
        this.a = a;
        this.b = b;
        this.resultat = resultat;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOperateur() { return operateur; }
    public void setOperateur(String operateur) { this.operateur = operateur; }

    public int getA() { return a; }
    public void setA(int a) { this.a = a; }

    public int getB() { return b; }
    public void setB(int b) { this.b = b; }

    public double getResultat() { return resultat; }
    public void setResultat(double resultat) { this.resultat = resultat; }
}
```

### F.3 `CalculClient` (paquetage `client`)

```java
package com.exemple.historique.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CalculClient {

    private final RestClient client;

    // @Value lit la propriété calcul.service.url de application.properties
    public CalculClient(@Value("${calcul.service.url}") String url) {
        this.client = RestClient.create(url);
    }

    // Envoie GET {url}/calcul/{operateur}?a=..&b=.. et retourne le résultat.
    // En cas d'erreur (statut 4xx, service injoignable...), RestClient lance une exception.
    public double calculer(String operateur, int a, int b) {
        String corps = client.get()
                .uri("/calcul/{op}?a={a}&b={b}", operateur, a, b)
                .retrieve()
                .body(String.class);
        return Double.parseDouble(corps);
    }
}
```

- `RestClient.create(url)` : toutes les URI commencent par `http://localhost:8081`.
- `uri(...)` remplace `{op}`, `{a}`, `{b}` par les valeurs fournies, dans l'ordre.
- `retrieve().body(String.class)` envoie la requête et lit la réponse (`text/plain`), que `Double.parseDouble` convertit en nombre.
- Réponse 400 de `calcul-service` → `HttpClientErrorException` ; service arrêté → `ResourceAccessException`.

### F.4 `OperationService` (paquetage `service`)

```java
package com.exemple.historique.service;

import com.exemple.historique.model.Operation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OperationService {

    // Pas de "static" : un @Service est un singleton géré par Spring
    private final Map<Integer, Operation> operations = new ConcurrentHashMap<>();
    private final AtomicInteger compteur = new AtomicInteger(0);

    public Operation ajouter(Operation op) {
        op.setId(compteur.incrementAndGet());
        operations.put(op.getId(), op);
        return op;
    }

    public List<Operation> lister() {
        List<Operation> liste = new ArrayList<>(operations.values());
        liste.sort(Comparator.comparingInt(Operation::getId));
        return liste;
    }

    public Operation lire(int id) {
        return operations.get(id); // null si l'identifiant n'existe pas
    }

    public boolean existe(int id) {
        return operations.containsKey(id);
    }

    public void remplacer(int id, Operation op) {
        op.setId(id);
        operations.put(id, op);
    }

    public Operation supprimer(int id) {
        return operations.remove(id); // null si l'identifiant n'existait pas
    }
}
```

**Remarque : plus de `static`.** Au TP2, JAX-RS créait un nouvel objet `OperationRest` à chaque requête : les attributs devaient être `static` pour persister. Avec Spring, un `@Service` est un **singleton** créé une seule fois et partagé : des attributs ordinaires suffisent. Comme plusieurs requêtes peuvent arriver simultanément, on utilise `ConcurrentHashMap` et `AtomicInteger` (accès concurrents sûrs).

### F.5 / F.8 / F.9 `OperationController` (paquetage `controller`) : version complète

```java
package com.exemple.historique.controller;

import com.exemple.historique.client.CalculClient;
import com.exemple.historique.model.Operation;
import com.exemple.historique.service.OperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private static final List<String> OPERATEURS =
            List.of("somme", "soustraction", "produit", "division");

    private final OperationService operationService;
    private final CalculClient calculClient;

    public OperationController(OperationService operationService,
                               CalculClient calculClient) {
        this.operationService = operationService;
        this.calculClient = calculClient;
    }

    // Demande le résultat à calcul-service et le range dans l'opération.
    // Retourne une réponse d'erreur si le calcul est impossible, null sinon.
    private ResponseEntity<Object> calculer(Operation op) {
        if (op.getOperateur() == null || !OPERATEURS.contains(op.getOperateur())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Opérateur inconnu : " + op.getOperateur()));
        }
        try {
            double resultat = calculClient.calculer(op.getOperateur(), op.getA(), op.getB());
            op.setResultat(resultat);
            return null;
        } catch (HttpClientErrorException e) {
            // calcul-service a répondu 4xx (ex. division par 0)
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Calcul impossible (division par 0 ?)"));
        } catch (ResourceAccessException e) {
            // calcul-service est injoignable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("erreur", "calcul-service est indisponible"));
        }
    }

    // POST /operations : crée une opération
    @PostMapping
    public ResponseEntity<Object> creer(@RequestBody Operation op) {
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) {
            return erreur;
        }
        Operation creee = operationService.ajouter(op);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creee.getId())
                .toUri();
        return ResponseEntity.created(location).body(creee);
    }

    // GET /operations : retourne toutes les opérations
    @GetMapping
    public List<Operation> lister() {
        return operationService.lister();
    }

    // GET /operations/{id} : retourne une opération
    @GetMapping("/{id}")
    public ResponseEntity<Operation> lire(@PathVariable("id") int id) {
        Operation op = operationService.lire(id);
        if (op == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(op);
    }

    // PUT /operations/{id} : modifie une opération (F.8)
    @PutMapping("/{id}")
    public ResponseEntity<Object> modifier(@PathVariable("id") int id,
                                           @RequestBody Operation op) {
        if (!operationService.existe(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Opération introuvable : " + id));
        }
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) {
            return erreur;
        }
        operationService.remplacer(id, op);
        return ResponseEntity.ok(op);
    }

    // DELETE /operations/{id} : supprime une opération (F.9)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable("id") int id) {
        Operation supprimee = operationService.supprimer(id);
        if (supprimee == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
```

> Les calculs `ResponseEntity.created(location)` (201 + en-tête `Location`), `notFound()` (404), `noContent()` (204) et le statut 503 sont ceux décrits dans l'énoncé.

### F.6 Tests de création et lecture

Lancer `calcul-service` (8081) **puis** `historique-service` (8082) : deux consoles ouvertes.

Création avec curl (Linux/macOS/Git Bash) :

```bash
curl -i -X POST -H "Content-Type: application/json" \
     -d '{"operateur":"somme","a":8,"b":2}' "http://localhost:8082/operations"
```

Création avec curl (cmd Windows) :

```bat
curl -i -X POST -H "Content-Type: application/json" -d "{\"operateur\":\"somme\",\"a\":8,\"b\":2}" "http://localhost:8082/operations"
```

Postman : `POST`, URL `http://localhost:8082/operations`, onglet *Body* → *raw* → *JSON*.

**Série de tests (préfixe `http://localhost:8082`) :**

| N° | Requête | Corps envoyé | Statut | Réponse attendue |
|---|---|---|---|---|
| 1 | `POST /operations` | `{"operateur":"somme","a":8,"b":2}` | **201** | En-tête `Location: http://localhost:8082/operations/1` ; corps `{"id":1,"operateur":"somme","a":8,"b":2,"resultat":10.0}` |
| 2 | `POST /operations` | `{"operateur":"division","a":7,"b":2}` | **201** | `Location: .../operations/2` ; `{"id":2,"operateur":"division","a":7,"b":2,"resultat":3.5}` |
| 3 | `GET /operations` | Aucun | **200** | Tableau JSON des deux opérations (id 1 et 2) |
| 4 | `GET /operations/2` | Aucun | **200** | `{"id":2,"operateur":"division","a":7,"b":2,"resultat":3.5}` |
| 5 | `GET /operations/99` | Aucun | **404** | Corps vide |
| 6 | `POST /operations` | `{"operateur":"division","a":7,"b":0}` | **400** | `{"erreur":"Calcul impossible (division par 0 ?)"}` (calcul-service a répondu 400) ; rien n'est enregistré |
| 7 | `POST /operations` | `{"operateur":"racine","a":9,"b":0}` | **400** | `{"erreur":"Opérateur inconnu : racine"}` (calcul-service n'est même pas appelé) |

Les tests 6 et 7 n'enregistrent rien et ne consomment pas d'identifiant : la prochaine création obtiendra donc l'id 3.

### F.7 Expérience : arrêt de `calcul-service`

`calcul-service` arrêté, `historique-service` en marche :

| N° | Requête | Corps envoyé | Statut | Réponse attendue |
|---|---|---|---|---|
| 8 | `GET /operations` | Aucun | **200** | La liste des opérations déjà enregistrées (id 1 et 2) |
| 9 | `GET /operations/1` | Aucun | **200** | `{"id":1,"operateur":"somme","a":8,"b":2,"resultat":10.0}` |
| 10 | `POST /operations` | `{"operateur":"produit","a":3,"b":4}` | **503** | `{"erreur":"calcul-service est indisponible"}` |

Après relance de `calcul-service`, le renvoi de la requête 10 répond **201** avec `{"id":3,"operateur":"produit","a":3,"b":4,"resultat":12.0}`.

**Question F.1.** Les lectures (`GET`) ne font que consulter la mémoire de `historique-service` : elles n'ont aucun besoin de `calcul-service`, donc elles continuent de fonctionner. **Au TP2, ce n'était pas possible** : calcul et historique étaient dans la même application `.war`, avec un simple appel de méthode ; il n'existait pas de panne partielle (si l'application s'arrêtait, tout s'arrêtait).

**Question F.2.** Sans le bloc `catch (ResourceAccessException e)`, l'exception remonterait non traitée jusqu'à Spring, qui répondrait **500 Internal Server Error** (réponse générique de Spring). Le client croirait à un bug du serveur au lieu d'une indisponibilité temporaire d'une dépendance. Le `catch` permet de répondre **503** avec un message clair. (La réponse est rapide car, le port 8081 étant fermé, la connexion est refusée immédiatement.)

### F.8 `PUT /operations/{id}`

| Situation | Statut | Corps de la réponse |
|---|---|---|
| Aucune opération ne porte cet identifiant | **404** | `{"erreur":"Opération introuvable : <id>"}` |
| Calcul impossible (opérateur inconnu, division par 0) | **400** | `{"erreur":"..."}` |
| `calcul-service` injoignable | **503** | `{"erreur":"calcul-service est indisponible"}` |
| Modification réussie | **200** | L'opération modifiée en JSON |

Test : `PUT /operations/1` avec `{"operateur":"produit","a":8,"b":2}` → **200** et `{"id":1,"operateur":"produit","a":8,"b":2,"resultat":16.0}` ; puis `GET /operations/1` renvoie la même opération modifiée.

```bash
curl -i -X PUT -H "Content-Type: application/json" \
     -d '{"operateur":"produit","a":8,"b":2}' "http://localhost:8082/operations/1"
curl -i "http://localhost:8082/operations/1"
```

### F.9 `DELETE /operations/{id}`

| Situation | Statut | Corps de la réponse |
|---|---|---|
| Aucune opération ne porte cet identifiant | **404** | Vide |
| Suppression réussie | **204** | Vide |

```bash
curl -i -X DELETE "http://localhost:8082/operations/1"   # 204
curl -i "http://localhost:8082/operations/1"             # 404
curl -i -X DELETE "http://localhost:8082/operations/1"   # 404
```

---

## 9. Questions de synthèse

### 1. Tableau A.3 : étape où la différence a été observée

| Aspect | Étape du TP |
|---|---|
| Technologie | B.1, B.3 (starter `spring-boot-starter-webmvc`) |
| Découpage | B.1 et F.1 (deux projets créés) |
| Résultat de la construction | B.3 (pas de `<packaging>war`), C.5 (`mvn clean package` → `.jar`) |
| Serveur | C.5 (ligne « Tomcat started on port 8081 », `java -jar`) |
| Port | C.4 (`server.port`), F.1 |
| Adresse | C.6 (analyse de l'URL), question C.1 |
| Activation | B.4 (`@SpringBootApplication` + `main`) |
| Ressource REST | C.2 (`@RestController`, `@RequestMapping`) |
| Verbes HTTP | C.2, F.5, F.8, F.9 |
| Paramètres | C.2, D.3, D.4, F.5 (`@RequestBody`) |
| Réponse HTTP | C.2, D.2 (`ResponseEntity`) |
| Conversion JSON | E.1, E.2 (Jackson) |
| Création des objets | C.2 (remarque), F.4 (plus de `static`, singleton) |
| Communication | F.3 (RestClient), F.7 (arrêt de calcul-service) |

### 2. Correspondance complète JAX-RS (TP2) ↔ Spring (TP3)

| JAX-RS (TP2) | Spring (TP3) |
|---|---|
| `@ApplicationPath("/api")` + `RestApplication` | `@SpringBootApplication` + `main` (pas de préfixe) |
| `@Path("/calcul")` (classe) | `@RequestMapping("/calcul")` + `@RestController` |
| `@Path("/somme")` (méthode) | valeur de `@GetMapping("/somme")` |
| `@GET` | `@GetMapping` |
| `@POST` | `@PostMapping` |
| `@PUT` | `@PutMapping` |
| `@DELETE` | `@DeleteMapping` |
| `@QueryParam` | `@RequestParam` |
| `@PathParam` | `@PathVariable` |
| paramètre de corps + `@Consumes(APPLICATION_JSON)` | `@RequestBody` (ou `consumes`) |
| `@Produces(MediaType.TEXT_PLAIN)` | `produces = MediaType.TEXT_PLAIN_VALUE` |
| `Response.ok(x).build()` | `ResponseEntity.ok(x)` |
| `Response.status(BAD_REQUEST).entity(x).build()` | `ResponseEntity.badRequest().body(x)` |
| `Response.status(NOT_FOUND).build()` | `ResponseEntity.notFound().build()` |
| `Response.created(uri).entity(x).build()` | `ResponseEntity.created(location).body(x)` |
| `Response.noContent().build()` | `ResponseEntity.noContent().build()` |
| `uriInfo.getAbsolutePathBuilder()` | `ServletUriComponentsBuilder.fromCurrentRequest()` |
| JSON-B | Jackson |

### 3. Construire et lancer les deux microservices sans IntelliJ

Terminal 1 :
```bash
cd calcul-service
mvn clean package
java -jar target/calcul-service-0.0.1-SNAPSHOT.jar
```
Terminal 2 (calcul-service déjà démarré) :
```bash
cd historique-service
mvn clean package
java -jar target/historique-service-0.0.1-SNAPSHOT.jar
```
Variante sans construire le `.jar` : `mvn spring-boot:run` dans chaque dossier. Contrairement au TP2, aucun Tomcat n'est installé ni aucun fichier déposé : le `.jar` contient son serveur.

### 4. Microservice et service web

- **Un microservice est un service web** : `calcul-service` et `historique-service` exposent une API REST accessible par HTTP.
- **Un service web n'est pas toujours un microservice** : le `RestWebApp.war` du TP2 était un service web, mais monolithique (calcul + historique dans une seule application), non autonome (déployé dans un Tomcat externe partagé), non découpé en fonctionnalités indépendantes. Un microservice ajoute les critères d'autonomie : une seule responsabilité, serveur embarqué, port propre, déploiement indépendant.

### 5. Adresse de `calcul-service` dans `application.properties` et 3 instances

L'adresse est **fixe et unique** : `historique-service` enverrait toutes ses requêtes à une seule instance, les deux autres ne recevraient aucune charge ; si cette instance tombe, `historique-service` échoue alors que d'autres instances fonctionnent. Il n'y a aucune répartition de charge, et il faudrait modifier la configuration à la main à chaque ajout, retrait ou changement d'adresse/port. Le TP4 résout cela avec un annuaire de services (découverte de services) et un répartiteur de charge côté client.

### 6. Stateless et liste des opérations

Le principe *stateless* concerne l'**état de la conversation (session) du client** : chaque requête doit contenir toutes les informations nécessaires à son traitement, et le serveur ne garde pas de contexte propre à un client entre deux requêtes. La liste des opérations n'est pas un état de client : ce sont les **données (l'état des ressources)** gérées par le service, communes à tous les clients et identifiées par leur URI. N'importe quelle requête est traitée de façon indépendante, sans dépendre des requêtes précédentes du même client. (En pratique, stocker ces données en mémoire limite la montée en charge : une base de données serait préférable.)

### 7. Répétition de `POST` et `PUT`

- **`POST /operations` envoyée deux fois** : crée **deux opérations distinctes** (ex. id 3 puis id 4) avec deux réponses 201 et deux `Location` différentes. `POST` **n'est pas idempotent**.
- **`PUT /operations/1` envoyée deux fois** : l'opération 1 est remplacée par le même contenu, l'état final est **identique** après la première et la deuxième requête (200 à chaque fois, même corps). `PUT` **est idempotent**.
- **Différent du TP2 ?** Non : l'idempotence est une propriété des verbes HTTP, elle ne dépend pas du framework (JAX-RS ou Spring).

---

## 10. Compilation et lancement

Ordre conseillé : démarrer `calcul-service` (port 8081), puis `historique-service` (port 8082).

```bash
# Dans chaque projet
mvn clean package
java -jar target/<artifact>-0.0.1-SNAPSHOT.jar
```

Vérifications rapides :

```bash
curl -i "http://localhost:8081/calcul/somme?a=2&b=3"
curl -i -X POST -H "Content-Type: application/json" \
     -d '{"operateur":"somme","a":8,"b":2}' "http://localhost:8082/operations"
```

---

