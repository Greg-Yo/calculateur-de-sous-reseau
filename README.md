# Subnet Calculator (CIDR / VLSM / VLAN)

A Spring Boot web application and REST API that splits an IPv4 network into subnets and plans IPv6 addressing — the tool you use **before** configuring a network in Cisco Packet Tracer or on real hardware.

> **Status:** v1 (IPv4) and v2 (IPv6) implemented — CIDR, VLSM, VLAN, IPv6 prefix analysis and subnet planning, CSV export, web UI and REST API, with 100 % test coverage on the domain and service layers.
> Full specifications (in French):
> - v1 — IPv4: [`docs/superpowers/specs/2026-10-12-calculateur-sous-reseaux-design.md`](docs/superpowers/specs/2026-10-12-calculateur-sous-reseaux-design.md)
> - v2 — IPv6: [`docs/superpowers/specs/2026-10-13-ipv6-design.md`](docs/superpowers/specs/2026-10-13-ipv6-design.md)

---

## Features

- **CIDR calculator** — enter `192.168.10.0/24` and get the network address, subnet mask, wildcard mask, broadcast address, first and last usable host, and host count. Handles the `/31` (RFC 3021 point-to-point) and `/32` special cases, and auto-corrects a host address to its network (`192.168.10.37/24` → `192.168.10.0/24`) with a warning.
- **VLSM subnetting** — from a list of named requirements (e.g. 50, 20, 10 and 2 hosts), allocate the smallest fitting blocks, largest first, with no gaps.
- **VLAN planning** — three input modes: just a number of VLANs (the network is split into equal blocks, e.g. 3 VLANs in a /24 → three /26, one /26 left free), a number of VLANs with the same host count for all, or a host count specific to each VLAN. The first two generate editable per-VLAN rows. Each VLAN gets its own subnet, and a **gateway address is reserved automatically** (first usable address).
- **IPv6 prefix analysis** — enter `2001:db8:acad:10::/64` and get the network address in compressed (`2001:db8:acad:10::`) and full (`2001:0db8:acad:0010:0000:0000:0000:0000`) form, the last address, the number of addresses (`2^64`), the number of /64 subnets, and the address type (global unicast, documentation, unique local, link-local, multicast, loopback, …). A host address is auto-corrected to its network with a warning, as in IPv4.
- **IPv6 subnet planning** — split a prefix such as `2001:db8:acad::/48` into equal-size subnets (**/64 by default**, any size up to /127), allocated in input order. In VLAN mode (VLAN IDs and names, or just a number of VLANs → `VLAN10`, `VLAN20`, …) each subnet gets a **gateway at `…::1`**; without VLANs (e.g. /127 point-to-point links) each subnet just needs a name. Warnings are shown for subnets shorter or longer than /64 (no SLAAC beyond /64). The result shows how many subnets are used, available and left.
- **Clear error handling** — invalid input such as `300.1.1.1/24`, `/33`, `1.2.3.4.` or duplicate VLAN IDs is rejected with a precise, stable error code and a human-readable message.
- **REST API** — JSON endpoints documented with Swagger / OpenAPI.
- **Web interface** — server-rendered pages (Thymeleaf), usable without any technical tool.
- **CSV export** — download the addressing plan (IPv4 or IPv6), ready to open in Excel.

### Example

`192.168.10.0/24` with 4 VLANs × 30 hosts (+1 gateway each):

| VLAN | Network | Gateway | Usable range | Broadcast |
|---|---|---|---|---|
| 10 | 192.168.10.0/26 | .1 | .1 – .62 | .63 |
| 20 | 192.168.10.64/26 | .65 | .65 – .126 | .127 |
| 30 | 192.168.10.128/26 | .129 | .129 – .190 | .191 |
| 40 | 192.168.10.192/26 | .193 | .193 – .254 | .255 |

`2001:db8:acad::/48` with 4 VLANs in /64 subnets (4 used out of 65 536, 65 532 left):

| VLAN | Network | Gateway | Last address |
|---|---|---|---|
| 10 | 2001:db8:acad::/64 | 2001:db8:acad::1 | 2001:db8:acad:0:ffff:ffff:ffff:ffff |
| 20 | 2001:db8:acad:1::/64 | 2001:db8:acad:1::1 | 2001:db8:acad:1:ffff:ffff:ffff:ffff |
| 30 | 2001:db8:acad:2::/64 | 2001:db8:acad:2::1 | 2001:db8:acad:2:ffff:ffff:ffff:ffff |
| 40 | 2001:db8:acad:3::/64 | 2001:db8:acad:3::1 | 2001:db8:acad:3:ffff:ffff:ffff:ffff |

---

## Tech stack

- Java 21
- Spring Boot 3.5 (Web, Validation, Thymeleaf)
- springdoc-openapi (Swagger UI)
- JUnit 5, MockMvc, JaCoCo
- Maven (via the Maven Wrapper — no local Maven install required)

All IPv4 arithmetic is implemented by hand with bitwise operators (`&`, `|`, `~`, `<<`) on 32-bit integers — no networking library. That is a deliberate learning goal of the project. IPv6 follows the same rule: a 128-bit address is stored in **two `long`s** (high and low 64 bits), with hand-written mask, shift and add-with-carry operations; `BigInteger` is used only to count addresses and subnets.

---

## Architecture

Classic Spring **MVC**:

```
src/main/java/com/coco/subnet/
├── model/        Pure Java domain: Ipv4Address, Cidr, SubnetInfo, Ipv6Address, Ipv6Cidr, Ipv6Plan, error codes (no Spring)
├── service/      SubnetService, VlsmService, CsvExporter, Ipv6SubnetService, Ipv6PlanService, Ipv6CsvExporter
├── controller/   WebController, Ipv6WebController (Thymeleaf pages), ApiController, Ipv6ApiController (JSON), error handlers
├── dto/          Form and JSON request/response objects
└── config/       OpenAPI configuration, request size filter
src/main/resources/
├── templates/    Thymeleaf views (index, vlsm, ipv6, ipv6-plan, error pages)
└── static/       CSS
```

The web controller and the API controller call the **same services**, so every calculation is written once. The IPv6 code lives in its own `Ipv6*` classes alongside the IPv4 ones; the IPv4 classes are untouched apart from the shared error codes, error handlers and navigation bar.

---

## API overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/subnets?cidr=192.168.10.0/24` | CIDR calculation |
| `POST` | `/api/vlsm` | VLSM / VLAN subnetting |
| `POST` | `/api/vlsm/csv` | Same, returned as a CSV file |
| `GET` | `/api/ipv6/subnets?cidr=2001:db8:acad:10::/64` | IPv6 prefix analysis |
| `POST` | `/api/ipv6/plan` | IPv6 subnet plan |
| `POST` | `/api/ipv6/plan/csv` | Same, returned as a CSV file |

Example request:

```json
POST /api/vlsm
{ "network": "192.168.10.0/24", "vlanCount": 4, "hostsPerVlan": 30 }
```

Send exactly one form: `requirements`, `vlans` (hosts per VLAN), `vlanCount` + `hostsPerVlan` (same hosts for all), or `vlanCount` alone (equal split).

IPv6 example:

```json
POST /api/ipv6/plan
{ "network": "2001:db8:acad::/48", "vlanCount": 4 }
```

Send exactly one form: `vlanCount`, `vlans` (`id` + optional `name`) or `subnets` (`name` only, no VLAN, no gateway). `subnetPrefix` is optional and defaults to 64. Large numbers (`totalAddresses`, `subnets64`, `availableSubnets`, `remainingSubnets`) are returned as **strings**, because a JSON number above 2^53 loses precision in JavaScript.

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

- Web interface: http://localhost:8080/ (IPv6: http://localhost:8080/ipv6 and http://localhost:8080/ipv6/plan)
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
- [x] v2 — IPv6 support: prefix analysis, equal-size subnet planning (/64 by default), VLAN mode with gateway, CSV export, web UI, REST API
- [ ] Next ideas (spec v2, §8):
  - subnet number = VLAN ID (VLAN 10 → `2001:db8:acad:10::/64`, as in Cisco courses)
  - EUI-64: compute a host address from its MAC address
  - embedded IPv4 notation (`::ffff:192.168.1.1`)
