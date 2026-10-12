# Subnet Calculator (CIDR / VLSM / VLAN)

A Spring Boot web application and REST API that splits an IPv4 network into subnets — the tool you use **before** configuring a network in Cisco Packet Tracer or on real hardware.

> **Status:** v1 implemented — CIDR, VLSM, VLAN, CSV export, web UI and REST API, with 100 % test coverage on the domain and service layers.
> Full specification (in French): [`docs/superpowers/specs/2026-10-12-calculateur-sous-reseaux-design.md`](docs/superpowers/specs/2026-10-12-calculateur-sous-reseaux-design.md)

---

## Features

- **CIDR calculator** — enter `192.168.10.0/24` and get the network address, subnet mask, wildcard mask, broadcast address, first and last usable host, and host count. Handles the `/31` (RFC 3021 point-to-point) and `/32` special cases, and auto-corrects a host address to its network (`192.168.10.37/24` → `192.168.10.0/24`) with a warning.
- **VLSM subnetting** — from a list of named requirements (e.g. 50, 20, 10 and 2 hosts), allocate the smallest fitting blocks, largest first, with no gaps.
- **VLAN planning** — three input modes: just a number of VLANs (the network is split into equal blocks, e.g. 3 VLANs in a /24 → three /26, one /26 left free), a number of VLANs with the same host count for all, or a host count specific to each VLAN. The first two generate editable per-VLAN rows. Each VLAN gets its own subnet, and a **gateway address is reserved automatically** (first usable address).
- **Clear error handling** — invalid input such as `300.1.1.1/24`, `/33`, `1.2.3.4.` or duplicate VLAN IDs is rejected with a precise, stable error code and a human-readable message.
- **REST API** — JSON endpoints documented with Swagger / OpenAPI.
- **Web interface** — server-rendered pages (Thymeleaf), usable without any technical tool.
- **CSV export** — download the addressing plan, ready to open in Excel.

### Example

`192.168.10.0/24` with 4 VLANs × 30 hosts (+1 gateway each):

| VLAN | Network | Gateway | Usable range | Broadcast |
|---|---|---|---|---|
| 10 | 192.168.10.0/26 | .1 | .1 – .62 | .63 |
| 20 | 192.168.10.64/26 | .65 | .65 – .126 | .127 |
| 30 | 192.168.10.128/26 | .129 | .129 – .190 | .191 |
| 40 | 192.168.10.192/26 | .193 | .193 – .254 | .255 |

---

## Tech stack

- Java 21
- Spring Boot 3.5 (Web, Validation, Thymeleaf)
- springdoc-openapi (Swagger UI)
- JUnit 5, MockMvc, JaCoCo
- Maven (via the Maven Wrapper — no local Maven install required)

All IPv4 arithmetic is implemented by hand with bitwise operators (`&`, `|`, `~`, `<<`) on 32-bit integers — no networking library. That is a deliberate learning goal of the project.

---

## Architecture

Classic Spring **MVC**:

```
src/main/java/com/coco/subnet/
├── model/        Pure Java domain: Ipv4Address, Cidr, SubnetInfo, error codes (no Spring)
├── service/      SubnetService, VlsmService, CsvExporter
├── controller/   WebController (Thymeleaf pages), ApiController (JSON), error handlers
├── dto/          Form and JSON request/response objects
└── config/       OpenAPI configuration, request size filter
src/main/resources/
├── templates/    Thymeleaf views (index, vlsm, error pages)
└── static/       CSS
```

The web controller and the API controller call the **same services**, so every calculation is written once.

---

## API overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/subnets?cidr=192.168.10.0/24` | CIDR calculation |
| `POST` | `/api/vlsm` | VLSM / VLAN subnetting |
| `POST` | `/api/vlsm/csv` | Same, returned as a CSV file |

Example request:

```json
POST /api/vlsm
{ "network": "192.168.10.0/24", "vlanCount": 4, "hostsPerVlan": 30 }
```

Send exactly one form: `requirements`, `vlans` (hosts per VLAN), `vlanCount` + `hostsPerVlan` (same hosts for all), or `vlanCount` alone (equal split).

Errors follow the RFC 9457 `ProblemDetail` format with a stable `code` field:

```json
{ "status": 400, "title": "Entrée invalide", "code": "PREFIX_OUT_OF_RANGE",
  "detail": "Le préfixe doit être compris entre 0 et 32", "errors": [ ... ] }
```

> User-facing messages are in French; error codes are in English.

---

## Getting started

```bash
./mvnw spring-boot:run
```

- Web interface: http://localhost:8080/
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

Run the tests:

```bash
./mvnw test
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

---

## Roadmap

- [x] v1 — CIDR, VLSM, VLAN, CSV export, web UI, REST API, tests (100 % coverage on `model` and `service`)
- [ ] v2 — IPv6 support
