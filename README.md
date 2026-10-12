<div align="center">

<img src="site/assets/favicon.svg" width="72" height="72" alt="" />

# Wallet Insights

Le tue finanze, sul tuo server.

[![Licenza MIT](https://img.shields.io/badge/licenza-MIT-22634c)](LICENSE) [![Sito](https://img.shields.io/badge/sito-darge98.github.io%2Fwallet--insight-22634c)](https://darge98.github.io/wallet-insight/)

</div>

Wallet Insights è un'applicazione per tenere sotto controllo le proprie finanze, pensata
per **una persona sola** e da installare **sul proprio computer o server**. Raccoglie
conti e movimenti e li riorganizza in categorie, budget e abbonamenti, così da vedere in
un colpo d'occhio quanto entra, quanto esce e dove vanno i soldi.

I dati non passano da servizi di terzi: restano nel database che avvii tu.

<a href="https://darge98.github.io/wallet-insight/">
  <picture>
    <source srcset="site/assets/panoramica-dark.webp" media="(prefers-color-scheme: dark)" />
    <img src="site/assets/panoramica-light.webp" alt="La panoramica di Wallet Insights: saldo, entrate, uscite e margine del periodo, la curva delle spese e la ripartizione per categoria." />
  </picture>
</a>

<sub>Gli screenshot mostrano dati fittizi.</sub>

## Cosa fa

- **Panoramica**: saldo di tutti i conti, entrate, uscite e quanto resta nel periodo,
  confrontati col periodo precedente; come crescono le spese e in quali categorie
  finiscono.
- **Movimenti**: tutti i movimenti, filtrabili per periodo, conto e categoria, con i
  totali. Descrizione, controparte e categoria si possono correggere.
- **Budget**: un tetto di spesa mensile per gruppi di categorie, anche con
  sotto-budget, per vedere quanto hai speso e quanto ti resta.
- **Abbonamenti**: le spese ricorrenti, in elenco o in calendario, con i prossimi
  addebiti e il costo mensile e annuo.
- **Categorie**: un albero a due livelli deciso da te. Se sposti una categoria, i suoi
  movimenti la seguono.
- **Aggiornamento automatico**: i nuovi movimenti arrivano ogni giorno, senza
  doverli inserire a mano.

<table>
  <tr>
    <td width="50%">
      <picture>
        <source srcset="site/assets/movimenti-dark.webp" media="(prefers-color-scheme: dark)" />
        <img src="site/assets/movimenti-light.webp" alt="I movimenti del mese raggruppati per giorno, con i totali e le uscite per categoria." />
      </picture>
      <p align="center"><b>Movimenti</b></p>
    </td>
    <td width="50%">
      <picture>
        <source srcset="site/assets/budget-dark.webp" media="(prefers-color-scheme: dark)" />
        <img src="site/assets/budget-light.webp" alt="I budget del mese con lo speso e il residuo di ogni gruppo di categorie." />
      </picture>
      <p align="center"><b>Budget</b></p>
    </td>
  </tr>
  <tr>
    <td colspan="2">
      <picture>
        <source srcset="site/assets/abbonamenti-dark.webp" media="(prefers-color-scheme: dark)" />
        <img src="site/assets/abbonamenti-light.webp" alt="Gli abbonamenti in arrivo nei prossimi 30 giorni, con il costo mensile e annuo." />
      </picture>
      <p align="center"><b>Abbonamenti</b></p>
    </td>
  </tr>
</table>

## Da dove arrivano i dati

| Sorgente                                                     | Stato       |
| ------------------------------------------------------------ | ----------- |
| [BudgetBakers Wallet](https://budgetbakers.com), col token personale | Disponibile |
| Conti bancari direttamente dalla banca (PSD2)                | In sviluppo |

## Installazione

Serve [Docker](https://www.docker.com/). Poi:

```bash
git clone https://github.com/darge98/wallet-insight.git
cd wallet-insight
cp .env.example .env
openssl rand -base64 32        # da incollare in ENCRYPTION_KEY dentro .env
docker compose up -d --build
```

L'app è su <http://localhost:8081>. Al primo accesso si crea il profilo e si collega
BudgetBakers col token personale, che si genera dalle impostazioni della sua app web.

`ENCRYPTION_KEY` cifra le credenziali delle sorgenti: conservala, perché se cambia i
token già salvati diventano illeggibili e vanno ricollegati.

> L'API non ha autenticazione: è pensata per girare in locale o in una rete privata,
> non esposta su internet.

## Struttura del progetto

```text
wallet-insight/
├── frontend/      l'applicazione web, cioè le schermate che usi nel browser
├── backend/       il servizio che importa i movimenti, li conserva e fa i calcoli
├── site/          la landing del progetto, pubblicata su GitHub Pages
├── compose.yaml   avvia tutto insieme: database, backend e applicazione
└── .env.example   le impostazioni da copiare in .env prima del primo avvio
```

Chi vuole metterci le mani trova i dettagli per lo sviluppo nei README di
[`frontend/`](frontend/README.md) e [`backend/`](backend/README.md).

## Licenza

[MIT](LICENSE).
