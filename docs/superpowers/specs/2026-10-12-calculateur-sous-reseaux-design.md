# Calculateur de sous-réseaux (CIDR / VLSM / VLAN) — Spécification

- **Date :** 2026-10-12
- **Statut :** en relecture
- **Mode de travail :** construction guidée, pas à pas (TDD). Claude explique chaque notion ; l'apprenant écrit une partie du code.

---

## 1. Objectif

Une application web Spring Boot qui découpe une adresse réseau IPv4 en sous-réseaux. C'est l'outil qu'on utilise avant de configurer un réseau dans Packet Tracer ou sur du vrai matériel.

### Ce que l'application doit savoir faire (périmètre v1)

1. Recevoir une adresse au format CIDR (ex. `192.168.10.0/24`) et afficher : adresse réseau, masque, masque générique (wildcard), broadcast, première et dernière adresse utilisable, nombre d'hôtes.
2. Faire un découpage VLSM à partir d'une liste de besoins (ex. 50, 20, 10 et 2 hôtes).
3. Faire un découpage **par VLAN** : nombre de VLAN, avec un nombre d'hôtes par VLAN (identique ou détaillé). Le résultat est toujours détaillé VLAN par VLAN.
4. Refuser proprement les entrées invalides, avec un message d'erreur clair (voir §6).
5. Exposer ces fonctions via une API REST JSON documentée avec Swagger/OpenAPI.
6. Proposer des pages web simples (Thymeleaf) pour l'utiliser sans outil technique.
7. Exporter le plan d'adressage en CSV.
8. Être couverte par des tests unitaires sur les calculs.

### Hors périmètre v1

- IPv6 (prévu en v2 : il aura son propre type, sans modifier le code IPv4).
- Authentification, base de données, déploiement en ligne.

### Critères de réussite

- `./mvnw spring-boot:run` lance l'application. On a alors la page web sur `/`, l'API sur `/api/**` et Swagger UI sur `/swagger-ui.html` (qui redirige vers `/swagger-ui/index.html`).
- Tous les exemples de ce document donnent exactement les résultats annoncés.
- `./mvnw test` passe, avec une couverture de 100 % sur `model/` et `service/`.

---

## 2. Choix techniques

| Élément | Choix |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 3.5.x. La branche 3.5 n'a probablement plus de support open source aujourd'hui (Boot 4 est sorti fin 2025). On la garde parce que la plupart des tutoriels et la documentation de springdoc 2.x portent sur Boot 3, et que l'application tourne en local. On le vérifiera sur start.spring.io à l'étape 1. |
| Build | Maven, via le wrapper `mvnw` (Maven n'est pas installé sur le poste). Le squelette du projet, wrapper compris, est généré avec start.spring.io. |
| Dépendances | `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-thymeleaf`, `springdoc-openapi-starter-webmvc-ui` (2.8.x, version compatible avec Boot 3.5), `spring-boot-starter-test` |
| Couverture | plugin JaCoCo |
| Package racine | `com.coco.subnet` |

**Les calculs sont écrits à la main, en binaire.** Une adresse IPv4 est un `int` de 32 bits, et les calculs utilisent `&`, `|`, `~` et `<<`. Aucune librairie réseau n'est utilisée : c'est le but pédagogique du projet.

Formules de base, pour un préfixe `n` :

```
masque     = (n == 0) ? 0 : (-1 << (32 - n))     // attention : en Java, -1 << 32 == -1
réseau     = ip & masque
broadcast  = réseau | ~masque
total      = 1L << (32 - n)                      // en long : /0 donne 4 294 967 296
```

**Pièges à connaître :**
- En Java, un `int` est signé. Les adresses ≥ `128.0.0.0` sont donc négatives en `int`. Pour comparer des adresses ou les compter, on passe par `Integer.toUnsignedLong(...)`.
- Tous les compteurs (`totalAddresses`, `usableHosts`, hôtes demandés, adresses utilisées ou restantes) sont des `long`. Un /0 a 4 294 967 294 hôtes utilisables, ce qui ne tient pas dans un `int`.

---

## 3. Architecture MVC

```
src/main/java/com/coco/subnet/
├── SubnetCalculatorApplication.java
├── model/                       ← M : métier, Java pur (aucune dépendance à Spring)
│   ├── Ipv4Address.java           record (int value) — parse/format, opérations binaires
│   ├── Cidr.java                  record (Ipv4Address address, int prefix) — parse "a.b.c.d/n"
│   ├── SubnetInfo.java            record — résultat d'un calcul CIDR
│   ├── Requirement.java           record (Integer vlanId, String name, long hosts) — un besoin
│   ├── AllocatedSubnet.java       record — un besoin + son SubnetInfo + adresses libres
│   ├── ErrorCode.java             enum — tous les codes d'erreur métier (§6.1)
│   ├── InputError.java            record (ErrorCode code, String field, String message)
│   │                              (nommé ainsi pour ne pas entrer en conflit avec FieldError de Spring)
│   └── InvalidInputException.java RuntimeException(List<InputError> errors) — une ou plusieurs erreurs
├── service/                     ← M : logique applicative
│   ├── SubnetService.java         calcul CIDR
│   ├── VlsmService.java           découpage VLSM / VLAN, génération des VLAN uniformes
│   └── CsvExporter.java           plan d'adressage → CSV
├── controller/                  ← C
│   ├── WebController.java         @Controller → vues Thymeleaf
│   ├── ApiController.java         @RestController → JSON sous /api
│   ├── ApiExceptionHandler.java   @RestControllerAdvice(assignableTypes = ApiController.class)
│   │                              → erreurs métier, validation, JSON illisible (voir §6.3)
│   └── RoutingErrorHandler.java   @ControllerAdvice global → 404 / 405 / 406 / 415
│                                  (JSON sous /api, page d'erreur ailleurs ; voir §6.3)
├── dto/                         objets des formulaires (web) et du JSON (API)
│   ├── CidrForm.java / SubnetResponse.java
│   ├── VlsmForm.java / RequirementForm.java
│   ├── VlsmRequest.java / VlanRequest.java / VlsmResponse.java
│   └── ...
└── config/
    ├── OpenApiConfig.java         titre, description, groupe /api/**
    └── RequestSizeFilter.java     sur /api/** : refuse les corps de plus de 64 Ko (→ 413)

src/main/resources/
├── templates/                   ← V
│   ├── fragments/layout.html      en-tête, menu (CIDR | VLSM / VLAN), pied de page
│   ├── index.html                 formulaire + résultat CIDR
│   ├── vlsm.html                  formulaire + tableau VLSM / VLAN
│   └── error/404.html, error/500.html   (utilisées automatiquement par Spring Boot)
├── static/css/style.css
├── messages.properties          messages d'erreur français (codes → texte, typeMismatch…)
└── application.properties
```

**Règles de dépendance :**
- `model` ne dépend de rien.
- `service` dépend uniquement de `model`.
- `controller` dépend de `service`, `dto` et `model`.

Les deux controllers appellent les **mêmes services**, donc chaque calcul n'est écrit qu'une seule fois.

**Une requête web, étape par étape (exemple pour le CIDR) :**
1. Le navigateur envoie `POST /`.
2. `WebController` reçoit le `CidrForm` validé par `@Valid`.
3. Il appelle `SubnetService.calculate(...)`.
4. Il ajoute le résultat au `Model`.
5. Il renvoie la vue `index`, que Thymeleaf affiche.

**Une requête API :**
1. Le client envoie `GET /api/subnets?cidr=...`.
2. `ApiController` appelle le même `SubnetService`.
3. Il renvoie un `SubnetResponse` en JSON.

---

## 4. Règles de calcul

### 4.1 Calcul CIDR

- Si l'adresse saisie n'est pas celle du réseau, le calcul se fait quand même sur le réseau qui la contient : `192.168.10.37/24` → réseau `192.168.10.0/24`. On ajoute alors l'avertissement « Adresse corrigée vers le réseau 192.168.10.0/24 ».
- Nombre d'hôtes utilisables :
  - préfixe de /0 à /30 : `2^(32-n) - 2` ; la première adresse utilisable est `réseau + 1` et la dernière est `broadcast - 1` ;
  - **/31** (lien point à point, RFC 3021) : 2 hôtes ; la première adresse utilisable est le réseau et la dernière est le « broadcast » ;
  - **/32** : 1 hôte ; la première et la dernière adresse utilisable sont l'adresse elle-même.

**Exemple de référence : `192.168.10.0/24`**

| Champ | Valeur |
|---|---|
| networkAddress | 192.168.10.0 |
| subnetMask | 255.255.255.0 |
| wildcardMask | 0.0.0.255 |
| broadcastAddress | 192.168.10.255 |
| firstUsable | 192.168.10.1 |
| lastUsable | 192.168.10.254 |
| usableHosts | 254 |
| totalAddresses | 256 |

### 4.2 Découpage VLSM / VLAN

**Entrée :** un réseau parent en CIDR et une liste de besoins (`Requirement`). Chaque besoin a un nom, un nombre d'hôtes et, en mode VLAN, un ID de VLAN.

**Algorithme :**
1. Valider le réseau parent. Un réseau en /31 ou /32 ne peut pas être découpé. Si l'adresse saisie n'est pas celle du réseau (`192.168.10.37/24`), elle est corrigée vers `192.168.10.0/24` avec un avertissement, comme pour le calcul CIDR (§4.1).
2. Valider chaque besoin (voir §6.1), puis vérifier qu'il n'y a pas de doublon d'ID de VLAN ni de nom. Les noms sont comparés sans tenir compte des majuscules : `RH` et `rh` sont un doublon.
3. Pour chaque besoin, calculer la taille du bloc : la plus petite puissance de 2 qui contient au moins `adresses nécessaires` adresses.
   - **Mode sans VLAN :** `hôtes + 2` (le réseau et le broadcast en plus des hôtes).
   - **Mode VLAN :** `hôtes + 1 + 2`, car la passerelle prend aussi une adresse (voir §4.3).

   Dans les deux cas, le plus petit bloc possible est un /30.
4. Trier les besoins **par taille de bloc décroissante**. Si deux blocs ont la même taille, on garde l'ordre de saisie (tri stable).
5. Vérifier qu'aucun bloc n'est plus grand que le réseau parent, sinon erreur `REQUIREMENT_TOO_LARGE`.
6. Vérifier que la somme des blocs tient dans le réseau parent, sinon erreur `NETWORK_TOO_SMALL` (le message indique le nombre d'adresses qui manquent).
7. Allouer les blocs à la suite, à partir de l'adresse du réseau parent. Comme les tailles sont des puissances de 2 triées dans l'ordre décroissant, chaque bloc tombe automatiquement sur une adresse alignée.
8. Pour chaque bloc, calculer le `SubnetInfo` et les adresses libres :
   - **Mode sans VLAN :** `usableHosts - hôtes demandés`.
   - **Mode VLAN :** `usableHosts - hôtes demandés - 1`. On ajoute aussi la passerelle, qui est la première adresse utilisable.

Le résultat est donné dans l'ordre d'allocation. Il indique aussi le total des adresses utilisées et le total des adresses restantes dans le réseau parent.

**Exemple de référence : `192.168.10.0/24`, besoins 50 / 20 / 10 / 2**

| Nom | Hôtes | Réseau | Masque | 1re – dernière | Broadcast | Libres |
|---|---|---|---|---|---|---|
| LAN-A | 50 | 192.168.10.0/26 | 255.255.255.192 | .1 – .62 | .63 | 12 |
| LAN-B | 20 | 192.168.10.64/27 | 255.255.255.224 | .65 – .94 | .95 | 10 |
| LAN-C | 10 | 192.168.10.96/28 | 255.255.255.240 | .97 – .110 | .111 | 4 |
| WAN | 2 | 192.168.10.112/30 | 255.255.255.252 | .113 – .114 | .115 | 0 |

### 4.3 Mode VLAN

- Chaque VLAN a un **ID** (de 1 à 4094, unique), un **nom** et un **nombre d'hôtes**.
- **Passerelle :** le nombre d'hôtes saisi **ne comprend pas** la passerelle. Celle-ci est réservée automatiquement : c'est la première adresse utilisable du sous-réseau, qui correspond à l'interface ou la sous-interface du routeur dans Packet Tracer. Le formulaire l'indique par le libellé « Hôtes (hors passerelle) ».
- **Le mode « même nombre d'hôtes » est un raccourci de saisie :** à partir de `vlanCount` et `hostsPerVlan`, on génère `vlanCount` lignes détaillées, avec les ID 10, 20, 30… et les noms `VLAN10`, `VLAN20`… Ces lignes restent modifiables une par une, et le calcul se fait toujours sur la liste détaillée.
- L'ID proposé pour la ligne `i` (en commençant à 1) est `i × 10`. Avec 100 VLAN au maximum, le dernier ID est 1000, ce qui reste dans la plage autorisée.
- Une ligne ajoutée à la main (`addRow`) reçoit l'ID `plus grand ID existant + 10`, ou 10 si le tableau est vide. Si cet ID dépasse 4094, il sera refusé au moment du calcul (`VLAN_ID_OUT_OF_RANGE`).
- Si le nom d'un VLAN est absent ou vide, il vaut `VLAN<id>`.
- Le résultat comporte **toujours une ligne par VLAN**, même si tous les VLAN ont le même nombre d'hôtes.

**Exemple de référence : `192.168.10.0/24`, 4 VLAN × 30 hôtes**

30 hôtes + 1 passerelle + 2 = 33 adresses, donc un bloc de 64 adresses (/26). Les 4 VLAN remplissent exactement le /24.

| VLAN | Nom | Hôtes | Réseau | Masque | Passerelle | 1re – dernière | Broadcast | Libres |
|---|---|---|---|---|---|---|---|---|
| 10 | VLAN10 | 30 | 192.168.10.0/26 | 255.255.255.192 | .1 | .1 – .62 | .63 | 31 |
| 20 | VLAN20 | 30 | 192.168.10.64/26 | 255.255.255.192 | .65 | .65 – .126 | .127 | 31 |
| 30 | VLAN30 | 30 | 192.168.10.128/26 | 255.255.255.192 | .129 | .129 – .190 | .191 | 31 |
| 40 | VLAN40 | 30 | 192.168.10.192/26 | 255.255.255.192 | .193 | .193 – .254 | .255 | 31 |

Total : 256 adresses utilisées, 0 restante.

**Cas limite :** avec 29 hôtes, on a 29 + 1 + 2 = 32 adresses, donc un /27. 30 adresses sont utilisables : la passerelle en prend 1, les hôtes 29, et il en reste 0 de libre.

**Sans VLAN :** si la case « Utiliser des VLAN » n'est pas cochée, on garde le mode « besoins nommés » (ex. `LAN-A`, `WAN`), sans ID de VLAN. C'est utile pour les liens entre routeurs. Dans ce mode, le nom est **obligatoire** (`EMPTY_NAME` s'il manque).

### 4.4 Export CSV

- Séparateur `;`, encodage UTF-8 avec BOM (pour qu'Excel affiche bien les accents), une ligne d'en-tête.
- Colonnes : `VLAN;Nom;Hôtes demandés;Réseau;Masque;Passerelle;Première;Dernière;Broadcast;Hôtes utilisables;Libres`.
- En mode sans VLAN, les colonnes `VLAN` et `Passerelle` restent vides.
- Le fichier se nomme `plan-adressage-<réseau>-<préfixe>.csv`, par exemple `plan-adressage-192.168.10.0-24.csv`.

---

## 5. Interfaces

### 5.1 API REST (`/api`, documentée dans Swagger)

| Méthode | URL | Entrée | Sortie |
|---|---|---|---|
| `GET` | `/api/subnets?cidr=192.168.10.0/24` | paramètre `cidr` | `SubnetResponse` (JSON) |
| `POST` | `/api/vlsm` | `VlsmRequest` (JSON) | `VlsmResponse` (JSON) |
| `POST` | `/api/vlsm/csv` | `VlsmRequest` (JSON) | `text/csv` (pièce jointe) |

**`SubnetResponse`**
```json
{
  "cidr": "192.168.10.0/24",
  "networkAddress": "192.168.10.0",
  "subnetMask": "255.255.255.0",
  "wildcardMask": "0.0.0.255",
  "broadcastAddress": "192.168.10.255",
  "firstUsable": "192.168.10.1",
  "lastUsable": "192.168.10.254",
  "usableHosts": 254,
  "totalAddresses": 256,
  "warnings": []
}
```

**`VlsmRequest`** : on envoie **exactement une** de ces trois formes.
- La forme raccourcie est reconnue dès que `vlanCount` **ou** `hostsPerVlan` est présent.
- Si des champs de deux formes différentes sont présents, on renvoie `AMBIGUOUS_REQUEST`.
- Si aucune forme n'est présente, on renvoie `NO_REQUIREMENTS`.
```json
{ "network": "192.168.10.0/24",
  "requirements": [ { "name": "LAN-A", "hosts": 50 }, { "name": "WAN", "hosts": 2 } ] }
```
```json
{ "network": "192.168.10.0/24",
  "vlans": [ { "id": 10, "name": "Compta", "hosts": 50 }, { "id": 20, "name": "RH", "hosts": 20 } ] }
```
```json
{ "network": "192.168.10.0/24", "vlanCount": 4, "hostsPerVlan": 30 }
```
Dans la forme `vlans`, le `name` est facultatif (voir §4.3). Dans la forme `requirements`, il est obligatoire. Dans la forme raccourcie, `vlanCount` et `hostsPerVlan` sont obligatoires tous les deux : si l'un manque, on renvoie `MISSING_FIELD` sur le champ manquant.

**Désérialisation stricte (Jackson) :**
- `spring.jackson.deserialization.accept-float-as-int=false`. Sans ce réglage, `"hosts": 2.5` serait accepté et tronqué à 2 sans prévenir. Avec lui, on renvoie `TYPE_MISMATCH`.
- `spring.jackson.deserialization.fail-on-unknown-properties=true`. Sans ce réglage, une faute de frappe comme `"host": 30` serait ignorée sans prévenir. Avec lui, on renvoie `UNKNOWN_FIELD`, avec le nom du champ inconnu.

**`produces` explicite :** chaque route de l'API déclare `produces` (`application/json` ou `text/csv`). Ainsi, un en-tête `Accept` incompatible est détecté dès le choix de la route (406), et non pendant l'écriture de la réponse.

**`VlsmResponse`**
```json
{
  "network": "192.168.10.0/24",
  "mode": "VLAN",
  "subnets": [
    { "vlanId": 10, "name": "VLAN10", "requestedHosts": 30, "gateway": "192.168.10.1",
      "cidr": "192.168.10.0/26", "networkAddress": "192.168.10.0", "subnetMask": "255.255.255.192",
      "wildcardMask": "0.0.0.63", "broadcastAddress": "192.168.10.63",
      "firstUsable": "192.168.10.1", "lastUsable": "192.168.10.62",
      "usableHosts": 62, "totalAddresses": 64, "freeHosts": 31 },
    { "vlanId": 20, "...": "..." },
    { "vlanId": 30, "...": "..." },
    { "vlanId": 40, "...": "..." }
  ],
  "usedAddresses": 256,
  "remainingAddresses": 0,
  "warnings": []
}
```
Cet exemple correspond aux 4 VLAN × 30 hôtes de §4.3. Les VLAN 20 à 40 sont abrégés ici, mais ont les mêmes champs que le VLAN 10.

`mode` vaut `"VLAN"` ou `"REQUIREMENTS"`. En mode `REQUIREMENTS`, `vlanId` et `gateway` valent `null`.

### 5.2 Pages web (Thymeleaf)

| Méthode | URL | Rôle |
|---|---|---|
| `GET` | `/` | Formulaire CIDR |
| `POST` | `/` | Calcul : formulaire et résultat sur la même page |
| `GET` | `/vlsm` | Formulaire VLSM / VLAN |
| `POST` | `/vlsm` | Action choisie par le paramètre `action` (voir ci-dessous) |
| `POST` | `/vlsm/csv` | Téléchargement du CSV du plan saisi |

**Les actions du formulaire VLSM** (`action`) sont traitées **côté serveur**, sans JavaScript :
- `addRow` : ajoute une ligne vide ;
- `removeRow-<i>` : supprime la ligne `i` ;
- `generate` : en mode VLAN uniforme, (re)génère les lignes à partir du nombre de VLAN et du nombre d'hôtes ;
- `calculate` : lance le calcul et affiche le tableau des résultats, avec un bouton « Exporter CSV » ;
- `refresh` : réaffiche simplement le formulaire. Ce bouton sert après avoir coché ou décoché « Utiliser des VLAN » : sans JavaScript, la page ne peut pas se mettre à jour toute seule.

Le formulaire VLSM contient : le réseau parent, la case « Utiliser des VLAN », les champs « Nombre de VLAN » et « Hôtes par VLAN » avec le bouton « Générer » (visibles en mode VLAN), et le tableau des lignes. En mode VLAN, une ligne contient ID, nom et hôtes ; sinon, nom et hôtes.

---

## 6. Gestion des erreurs

Une seule exception métier : `InvalidInputException(List<InputError> errors)`. Chaque `InputError` contient un code, un champ et un message.
- Le **modèle** la lève.
- Les **controllers** la présentent : en JSON pour l'API, sous le champ concerné pour le web.
- Les messages sont en français.
- Les codes restent stables : ils sont utilisés dans le JSON et dans les tests.

**Une erreur par champ, toutes les erreurs du formulaire.** Pour un même champ, on ne garde que la première erreur trouvée (voir l'ordre ci-dessous). En revanche, `VlsmService` valide **tous** les champs avant de lever l'exception : le réseau parent et chaque ligne. L'utilisateur voit ainsi toutes ses erreurs en une fois. Les vérifications globales (doublons, `REQUIREMENT_TOO_LARGE`, `NETWORK_TOO_SMALL`) ne sont faites que si chaque champ est valide.

**Deux niveaux de validation :**
1. **`@Valid` sur les DTO** vérifie la forme : `@NotBlank`, `@NotNull`, `@Positive`, `@Size(max = 100)`, `@Min` / `@Max`. Chaque annotation porte son code d'erreur dans `message`, par exemple `@Size(max = 100, message = "TOO_MANY_REQUIREMENTS")`. Les handlers traduisent ce code en texte français grâce à `messages.properties`. Un `@NotNull` / `@NotBlank` sans code plus précis donne `MISSING_FIELD`. Concrètement, le message s'écrit sous la forme `{error.CODE}`, par exemple `message = "{error.TOO_MANY_REQUIREMENTS}"`. Le handler retrouve le code à partir de ce gabarit.
   Les formulaires web, eux, n'ont **pas** d'annotations : ils sont validés uniquement par le modèle, qui donne les mêmes codes et les mêmes messages. Cela évite les doublons d'erreur sur un même champ, et toutes les erreurs du formulaire (y compris un formulaire vide) s'affichent en une fois.
2. **Le modèle** vérifie le sens : octets, préfixe, place disponible, doublons. Il ne fait jamais confiance au controller. Il refait les mêmes contrôles de plage que les annotations, avec **les mêmes codes**. Une erreur a donc le même code, qu'elle soit détectée par `@Valid` ou par le modèle.

### 6.1 Erreurs d'entrée (modèle et services, communes au web et à l'API)

Les espaces au début et à la fin d'une saisie sont retirés avant la validation. Un espace **à l'intérieur** de la saisie (`192.168. 1.1`) reste interdit (`INVALID_CHARACTERS`).

**Ordre des vérifications.** Une saisie ne produit qu'**une seule** erreur : la première rencontrée dans cet ordre.
- **CIDR :** vide → nombre de `/` → adresse (règles ci-dessous) → préfixe.
- **Adresse :** vide → caractères (seuls les chiffres et `.` sont autorisés) → octet vide → nombre d'octets → zéro initial → valeur de l'octet.

**Pièges d'implémentation :**
- Découper l'adresse avec `split("\\.", -1)`. Sans le `-1`, Java supprime les morceaux vides à la fin, et `1.2.3.4.` serait accepté par erreur.
- Un octet de plus de 3 chiffres (`99999999999`) donne `OCTET_OUT_OF_RANGE` **sans** appeler `Integer.parseInt`, qui lèverait une exception de dépassement.

**Adresse IPv4**

| Code | Exemple | Message |
|---|---|---|
| `EMPTY_INPUT` | `""`, `"   "`, `null` | L'adresse est obligatoire |
| `INVALID_CHARACTERS` | `192.168.a.1`, `1,2,3,4`, `1.2.3.-4` | Caractère non autorisé : 'a' |
| `EMPTY_OCTET` | `192..1.1`, `.1.2.3`, `1.2.3.4.` | Octet vide en position 2 |
| `WRONG_OCTET_COUNT` | `192.168.1`, `1.2.3.4.5` | Une adresse IPv4 contient 4 octets (3 trouvés) |
| `LEADING_ZERO` | `01.2.3.4`, `1.2.3.00` (mais `0` seul est valide) | Zéro initial interdit dans '01' (ambigu : octal) |
| `OCTET_OUT_OF_RANGE` | `300.1.1.1`, `1.2.3.99999999999` | L'octet 300 est hors limites (0–255) |

**Notation CIDR**

| Code | Exemple | Message |
|---|---|---|
| `MISSING_PREFIX` | `192.168.1.0`, `abc` | Préfixe manquant : format attendu a.b.c.d/n |
| `MULTIPLE_SLASHES` | `1.2.3.4/24/8` | Un seul '/' autorisé |
| `INVALID_PREFIX` | `/abc`, `/`, `/2.5`, `/-1`, `/+8` | Le préfixe doit être un nombre entier positif |
| `PREFIX_OUT_OF_RANGE` | `/33`, `/999` | Le préfixe doit être compris entre 0 et 32 |

Pour être valide, le préfixe ne doit contenir que des chiffres (1 à 3) ; c'est seulement ensuite qu'on vérifie qu'il est compris entre 0 et 32. Un zéro initial est toléré dans le préfixe (`/024` = `/24`), car il n'y a pas d'ambiguïté octale.

**Plan VLSM / VLAN**

| Code | Exemple | Message |
|---|---|---|
| `NO_REQUIREMENTS` | liste vide | Ajoutez au moins un VLAN ou un besoin |
| `TOO_MANY_REQUIREMENTS` | 101 lignes | Maximum 100 sous-réseaux |
| `HOSTS_NOT_POSITIVE` | `0`, `-5` | VLAN 10 : au moins 1 hôte requis |
| `HOSTS_TOO_LARGE` | `5000000000` | VLAN 10 : un sous-réseau IPv4 ne peut pas dépasser 4 294 967 293 hôtes (hors passerelle) — sans VLAN : « LAN-A : … 4 294 967 294 hôtes » |
| `VLAN_ID_OUT_OF_RANGE` | `0`, `4095`, `5000` | VLAN 5000 : l'ID doit être entre 1 et 4094 |
| `DUPLICATE_VLAN_ID` | deux VLAN 20 | VLAN 20 est en double |
| `DUPLICATE_NAME` | « RH » et « rh » | Le nom 'RH' est utilisé deux fois |
| `EMPTY_NAME` | nom absent, mode sans VLAN | Le besoin n°3 doit avoir un nom |
| `INVALID_NAME` | > 32 caractères, `;`, `=`, espace… | Nom invalide : lettres (accents compris), chiffres, - et _ (32 max) |
| `VLAN_COUNT_OUT_OF_RANGE` | `vlanCount` 0 ou 101 | Le nombre de VLAN doit être entre 1 et 100 |
| `REQUIREMENT_TOO_LARGE` | 300 hôtes dans un /24 | VLAN 10 (300 hôtes, bloc /23) ne tient pas dans un /24 |
| `NETWORK_TOO_SMALL` | total trop grand | Le /24 (256 adresses) ne suffit pas : 320 nécessaires, il manque 64 |
| `PARENT_NOT_SPLITTABLE` | réseau parent en /31 ou /32 | Un /31 ou /32 ne peut pas être découpé |
| `MISSING_FIELD` | `hostsPerVlan` absent, champ obligatoire vide | Le champ « Hôtes par VLAN » est obligatoire |

**Règle de nom :** un nom valide correspond à l'expression `^[\p{L}\p{N}][\p{L}\p{N}_-]{0,31}$`.
- Il commence par une lettre ou un chiffre.
- Il contient ensuite des lettres (accents compris : `Comptabilité` est accepté), des chiffres, `-` ou `_`.
- Il fait 32 caractères au maximum.

Tous les autres caractères sont interdits (`;`, `"`, `=`, `+`, `@`, espace…), et un nom ne peut pas commencer par `-`. Cela empêche de casser le CSV et d'y injecter une formule Excel : une cellule qui commence par `=`, `+`, `-` ou `@` est interprétée comme une formule.

**Limite d'hôtes :** 4 294 967 294 hôtes correspondent à un bloc /0, le plus grand possible en IPv4. En mode VLAN, la limite est de 4 294 967 293, car la passerelle prend une adresse. Toute valeur supérieure donne `HOSTS_TOO_LARGE`. Une valeur inférieure mais trop grande pour le réseau parent donne `REQUIREMENT_TOO_LARGE`.

En mode sans VLAN, les messages commencent par le nom du besoin (« LAN-A : … ») au lieu de « VLAN 10 : … ». Si le besoin n'a pas de nom valide, on utilise son numéro de ligne (« Besoin n°3 : … »). De même, si un VLAN a un ID invalide, on utilise son numéro de ligne (« Ligne 2 : … »).

L'auto-correction d'une adresse vers celle de son réseau **n'est pas une erreur** : elle ajoute un avertissement dans `warnings`, et un bandeau d'information sur la page web.

### 6.2 Erreurs web (pages Thymeleaf)

| Situation | Comportement |
|---|---|
| Erreur d'entrée sur un champ | Message en rouge sous le champ (`th:errors`), saisie conservée, champ en surbrillance |
| Plusieurs erreurs | Toutes affichées en même temps, avec un résumé en haut du formulaire |
| Erreur sur une ligne VLAN | Ligne surlignée et message sur la ligne (`vlans[2].hosts`) |
| Texte dans un champ numérique (`"abc"` comme nombre d'hôtes) | Erreur de conversion (`typeMismatch`) traduite par `messages.properties` : « Doit être un nombre entier » (pas de page d'erreur Spring) |
| Formulaire vide | « Ce champ est obligatoire » sur chaque champ concerné |
| « Générer » avec un nombre de VLAN invalide | Message sous le champ, aucune ligne générée |
| « Exporter CSV » avec un plan invalide | Retour au formulaire avec les erreurs, aucun fichier envoyé |
| URL inconnue | Page `error/404.html` avec un lien vers l'accueil |
| Mauvaise méthode (`GET /vlsm/csv`, `PUT /`…) | Redirection vers la page GET correspondante (`/vlsm/csv` → `/vlsm`, `/` → `/`), traitée par `RoutingErrorHandler` |
| Erreur imprévue | Page `error/500.html` sans stack trace ; l'erreur est écrite dans les logs serveur |

### 6.3 Erreurs API (HTTP / JSON)

Toutes les erreurs de l'API utilisent le format `ProblemDetail` (RFC 9457), avec le type de contenu `application/problem+json`.
- **`errors` est toujours présent** : c'est la liste des `InputError`, éventuellement vide pour les erreurs HTTP comme 404 ou 405.
- **Le `code` principal :**
  - s'il y a **une seule** erreur d'entrée, c'est son code (par exemple `OCTET_OUT_OF_RANGE`) ;
  - s'il y en a **plusieurs**, c'est `VALIDATION_FAILED`.

```json
{ "type": "about:blank", "status": 400, "title": "Entrée invalide",
  "code": "VALIDATION_FAILED",
  "detail": "2 erreurs dans la requête",
  "errors": [ { "field": "vlans[0].hosts", "code": "HOSTS_NOT_POSITIVE", "message": "VLAN 10 : au moins 1 hôte requis" },
              { "field": "network", "code": "PREFIX_OUT_OF_RANGE", "message": "Le préfixe doit être compris entre 0 et 32" } ] }
```

| Situation | Statut | Code |
|---|---|---|
| Une erreur d'entrée (§6.1), détectée par `@Valid` ou par le modèle | 400 | le code métier |
| Plusieurs erreurs d'entrée | 400 | `VALIDATION_FAILED` + `errors` |
| JSON mal formé | 400 | `MALFORMED_JSON` |
| Mauvais type (`"hosts": "abc"`, `"hosts": 2.5`) | 400 | `TYPE_MISMATCH` |
| Champ inconnu (`"host": 30`) | 400 | `UNKNOWN_FIELD` |
| Paramètre obligatoire absent | 400 | `MISSING_PARAMETER` |
| Corps vide sur un POST | 400 | `MISSING_BODY` |
| Plusieurs formes de `VlsmRequest` à la fois | 400 | `AMBIGUOUS_REQUEST` |
| Aucune forme de `VlsmRequest` | 400 | `NO_REQUIREMENTS` |
| Endpoint inconnu | 404 | `NOT_FOUND` |
| Mauvaise méthode | 405 | `METHOD_NOT_ALLOWED` (+ en-tête `Allow`) |
| `Content-Type` qui n'est pas du JSON | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| `Accept` impossible à satisfaire | 406 | `NOT_ACCEPTABLE` (corps possiblement vide, voir ci-dessous) |
| Corps de plus de 64 Ko | 413 | `PAYLOAD_TOO_LARGE` |
| Erreur imprévue | 500 | `INTERNAL_ERROR` (message générique, aucun détail interne) |

Toutes les erreurs dues à une saisie de l'utilisateur renvoient un 400, y compris `NETWORK_TOO_SMALL`.

**Qui traite quelle erreur :**
- **`ApiExceptionHandler`** (limité à `ApiController`) traite les erreurs qui surviennent une fois que la requête est arrivée dans `ApiController` :
  - `InvalidInputException` ;
  - `MethodArgumentNotValidException` (`@Valid` sur un corps JSON). Le paramètre `cidr` n'a pas d'annotation de contrainte : c'est le modèle qui le valide ;
  - `MissingServletRequestParameterException` ;
  - `HttpMessageNotReadableException`. On lit sa cause pour choisir le code, en testant les cas **dans cet ordre**, du plus précis au plus général : `UnrecognizedPropertyException` → `UNKNOWN_FIELD` ; `InvalidFormatException` / `MismatchedInputException` → `TYPE_MISMATCH` ; corps absent → `MISSING_BODY` ; sinon → `MALFORMED_JSON`. L'ordre compte, car `UnrecognizedPropertyException` hérite de `MismatchedInputException` ;
  - les mêmes exceptions HTTP que `RoutingErrorHandler` (405, 406, 415), avec la même réponse. Elles peuvent aussi survenir une fois `ApiController` choisi, et elles ne doivent **jamais** tomber dans le cas général ci-dessous, qui les transformerait à tort en 500 ;
  - `Exception` → 500.
- **`RoutingErrorHandler`** (global) traite les erreurs qui surviennent **avant** qu'un controller soit choisi. Un advice limité à `ApiController` ne les verrait jamais :
  - `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException` (405), `HttpMediaTypeNotAcceptableException` (406), `HttpMediaTypeNotSupportedException` (415) ;
  - si l'URL commence par `/api/`, il renvoie un `ProblemDetail` JSON ;
  - sinon, il renvoie la page `error/404` (ou la redirection de §6.2).
- **`RequestSizeFilter`** est un filtre de servlet, exécuté avant Spring MVC. Les advices ne le voient donc pas : il écrit lui-même la réponse 413 en JSON. Il vérifie l'en-tête `Content-Length` et compte aussi les octets lus, pour les requêtes envoyées sans cet en-tête (corps « chunked »).
- Pour les pages web, la taille des formulaires est déjà limitée par Tomcat (`server.tomcat.max-http-form-post-size`, 2 Mo par défaut).

**Erreurs de `/api/vlsm/csv` :** les erreurs sont renvoyées en `application/problem+json`, pas en CSV. Un client qui envoie `Accept: text/csv` **seul** ne peut donc pas recevoir ce corps d'erreur. Il reçoit le bon statut, mais le corps peut être vide. C'est le même cas que le 406. Un client qui accepte `*/*` (comme curl par défaut ou Swagger UI) reçoit l'erreur complète. Ce comportement est testé.

---

## 7. Tests

| Niveau | Outil | Contenu |
|---|---|---|
| Modèle (`Ipv4Address`, `Cidr`) | JUnit 5 seul | Conversion texte ↔ `int`, masques, opérations binaires, **chaque code de §6.1** concernant les adresses et le CIDR |
| Services | JUnit 5 seul | Calcul CIDR, algorithme VLSM, mode VLAN, génération des VLAN uniformes, codes VLSM de §6.1, format du CSV |
| `ApiController` | `@WebMvcTest` + `MockMvc` (avec `RoutingErrorHandler` et `RequestSizeFilter`) | Statuts, JSON, **chaque ligne de §6.3** |
| `WebController` | `@WebMvcTest` + `MockMvc` | Vue renvoyée, attributs du `Model`, erreurs dans `BindingResult`, actions `addRow` / `removeRow` / `generate` / `calculate`, téléchargement CSV, cas de §6.2 |
| Démarrage | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` | L'application démarre ; `/`, `/vlsm`, `/swagger-ui/index.html` et `/v3/api-docs` répondent 200 (`/swagger-ui.html` répond par une redirection 302, pas par un 200) |

**Techniques et cas obligatoires :**
- `@ParameterizedTest` + `@CsvSource` : tous les préfixes de /0 à /32 (masque et nombre d'hôtes) ; toutes les entrées invalides avec le code attendu.
- `assertThrows` pour vérifier le code de chaque `InvalidInputException`.
- Cas limites : `0.0.0.0/0`, `255.255.255.255/32`, /31, /32, l'auto-correction de `192.168.10.37/24`, des adresses ≥ `128.0.0.0` (piège de l'`int` signé), un VLSM qui remplit exactement le parent, un VLSM qui le dépasse d'un seul bloc /30. Il ne peut pas le dépasser d'une seule adresse : les blocs font tous au moins 4 adresses, et leur total est toujours un multiple de 4.
- Désérialisation stricte : `"hosts": 2.5` → `TYPE_MISMATCH`, `"host": 30` → `UNKNOWN_FIELD`.
- Plusieurs erreurs à la fois : un réseau invalide **et** une ligne invalide → `VALIDATION_FAILED` avec 2 éléments dans `errors`. Une seule erreur → son code métier en code principal.
- Même code d'erreur via `@Valid` et via le modèle (par exemple 101 lignes → `TOO_MANY_REQUIREMENTS` dans les deux cas).
- Nom commençant par `-` (`-A1`) → `INVALID_NAME`.
- Erreurs de saisie comme `1.2.3.4.` (morceau vide final) et `1.2.3.99999999999` (octet trop long).
- Les trois exemples de référence de §4 (CIDR, VLSM 50/20/10/2, 4 VLAN × 30) sont des tests à part entière, ainsi que le cas limite de la passerelle (29 hôtes → /27, 30 hôtes → /26).

**Méthode :** on applique le TDD. Pour chaque fonction : test rouge → code minimal → test vert → refactorisation. Pour certaines fonctions, Claude écrit le test et l'apprenant propose l'implémentation, puis Claude la relit.

**Couverture :** JaCoCo, 100 % des lignes et des branches sur `model/` et `service/`. Le build échoue si ce seuil n'est pas atteint.

---

## 8. Évolutions prévues (hors v1)

- **v2 — IPv6 :** un type `Ipv6Address` séparé, basé sur deux `long` ou un `BigInteger`, avec des services et des endpoints dédiés. Le code IPv4 n'est pas modifié.
