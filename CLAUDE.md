# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Il monorepo

**Wallet Insights** è un personal finance tracker in due progetti indipendenti, ognuno col proprio
build system e la propria guida operativa:

- `frontend/` — Angular 22 + Nx (zoneless, signal, ports & adapters). Guida:
  `frontend/AGENTS.md`, riferimento completo in `frontend/README.md`.
- `backend/` — Spring Boot 4 + Spring Modulith, Spring Data JDBC, Flyway, PostgreSQL 17,
  Java 25. Comprende anche l'importazione dalle sorgenti esterne (BudgetBakers). Guida:
  `backend/AGENTS.md`, riferimento completo in `backend/README.md`.

Prima di lavorare in uno dei due progetti leggi il suo `AGENTS.md`: lì stanno comandi,
architettura e vincoli. Questo file copre solo ciò che li attraversa entrambi.

## Comandi dalla radice

```bash
cp .env.example .env                       # poi imposta ENCRYPTION_KEY
openssl rand -base64 32                    # genera la chiave
docker compose up -d --build               # Postgres :5432, backend :8080, frontend :8081
docker compose up -d --build backend       # ricostruisce solo il backend
```

`ENCRYPTION_KEY` è obbligatoria e senza default, di proposito: cifra i token delle
sorgenti nel database. Cambiarla rende illeggibili le credenziali già salvate.

Ogni progetto si builda e si testa **dalla propria cartella** (`npm run verify` in
`frontend/`, `./gradlew test` in `backend/`); non c'è un build di radice.

## Come si parlano

- **In sviluppo**: `npm start` (frontend su :4200) chiama `/api` sulla propria origine e
  il proxy del dev server (`frontend/apps/wallet/proxy.conf.json`) lo inoltra a
  `localhost:8080`: va bene il backend del compose come un `bootRun`, senza CORS. Il
  profilo `local` apre comunque il CORS verso :4200, per chi chiama l'API da lì. Per il solo
  Postgres c'è `backend/compose.yaml`, indipendente da quello di radice.
- **Nello stack compose**: nginx del frontend serve l'app e fa proxy di `/api/` verso
  `backend:8080` — stessa origine, nessun CORS.

Il contratto HTTP è vincolante da entrambe le parti: importi interi in **centesimi**
(mai decimali, nemmeno nel JSON), id **UUIDv7** generati dall'applicazione, date
`yyyy-MM-dd` senza fuso, enum in **kebab-case**, paginazione
`{items, total, index, size, pageCount}`, errori come `ProblemDetail` (RFC 9457).
Filtri, ordinamenti, totali e aggregazioni li fa **PostgreSQL**, mai il client.

Il dominio finanziario si ricostruisce dalle **sorgenti reali** di import (BudgetBakers,
PSD2), non dai tipi del frontend: è il frontend che si adegua alla forma del backend.

## Convenzioni comuni

Codice, commenti, documentazione, nomi dei test e testi dell'interfaccia sono **in
italiano**; i commenti spiegano il _perché_. Le decisioni si prendono su misure reali
(tempi, saldi confrontati con la sorgente), e i documenti le riportano con i numeri.
