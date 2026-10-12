# Contribuire a Wallet Insights

Grazie dell'interesse. Segnalazioni, idee e pull request sono benvenute; questa pagina
spiega come proporle perché arrivino in fondo senza giri inutili.

Chi partecipa si impegna a rispettare il [codice di comportamento](CODE_OF_CONDUCT.md).

## Prima di scrivere codice

- **Un problema?** Apri una [issue](https://github.com/darge98/wallet-insight/issues/new/choose)
  col modello "Segnala un problema": cosa hai fatto, cosa ti aspettavi, cosa è successo.
- **Una vulnerabilità?** Non aprire una issue pubblica: segui la
  [politica di sicurezza](SECURITY.md).
- **Una funzione nuova o una modifica grossa?** Apri prima una issue col modello
  "Proponi una funzione", così ne parliamo prima che tu ci metta tempo.

Le correzioni piccole (refusi, documentazione, bug evidenti) possono andare direttamente
in pull request.

## Ambiente di sviluppo

Il repository contiene due progetti indipendenti, ognuno con la propria guida:

| Cartella                 | Guida                                                         | Cosa serve          |
| ------------------------ | ------------------------------------------------------------- | ------------------- |
| [`frontend/`](frontend)  | [`frontend/AGENTS.md`](frontend/AGENTS.md), [`README`](frontend/README.md) | Node 24 (`.nvmrc`)  |
| [`backend/`](backend)    | [`backend/AGENTS.md`](backend/AGENTS.md), [`README`](backend/README.md)    | Java 25, Docker     |

Il modo più rapido per lavorare sull'interfaccia è avviare il backend con Docker e il
frontend in locale:

```bash
cp .env.example .env                   # e imposta ENCRYPTION_KEY (openssl rand -base64 32)
docker compose up -d --build backend   # PostgreSQL e API su :8080
cd frontend && npm ci && npm start     # app su :4200, /api inoltrato al backend
```

Ogni progetto si verifica dalla propria cartella, ed è ciò che la CI esegue su ogni PR:

```bash
cd frontend && npm run verify          # lint, test, build
cd backend && ./gradlew test           # i test col database usano Testcontainers
```

## Convenzioni

- **In italiano**: codice, commenti, documentazione, nomi dei test e testi
  dell'interfaccia. I commenti spiegano il _perché_, non il _cosa_.
- **Il contratto HTTP** fra i due progetti è vincolante: importi interi in centesimi,
  id UUIDv7, date `yyyy-MM-dd`, enum in kebab-case, errori come `ProblemDetail`. Filtri,
  totali e aggregazioni li calcola PostgreSQL, mai il client.
- **Le decisioni si prendono su misure reali** (tempi, saldi confrontati con la
  sorgente), e la PR riporta i numeri.

## Commit e pull request

I messaggi di commit seguono [Conventional Commits](https://www.conventionalcommits.org/it/),
in italiano, con il progetto come ambito quando serve:

```text
feat(frontend): scelta della categoria con la ricerca
build(backend): Tomcat 11.0.26
ci: controllo settimanale delle vulnerabilità nelle dipendenze
```

Tipi usati: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`.

Per la pull request:

1. parti da `main` aggiornato, in un branch con un nome parlante;
2. tieni la PR su un solo argomento: due modifiche indipendenti sono due PR;
3. compila il modello della PR: cosa cambia, perché, come l'hai verificato;
4. aspetta che i controlli della CI siano verdi: sono obbligatori per il merge.
