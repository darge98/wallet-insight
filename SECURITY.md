# Politica di sicurezza

## Versioni supportate

Wallet Insights non ha ancora rilasci numerati: le correzioni di sicurezza arrivano
solo sul branch `main`. Aggiorna la tua installazione all'ultima versione di `main`
prima di segnalare un problema.

## Segnalare una vulnerabilità

**Non aprire una issue pubblica.** Usa la
[segnalazione privata di GitHub](https://github.com/darge98/wallet-insight/security/advisories/new)
oppure scrivi a [mattia.darge@gmail.com](mailto:mattia.darge@gmail.com).

Nella segnalazione indica, per quanto possibile:

- la parte coinvolta (frontend, backend, import, configurazione di Docker);
- i passi per riprodurre il problema e il suo impatto;
- la versione, cioè il commit di `main` su cui l'hai osservato.

Riceverai una prima risposta entro una settimana. Il progetto è mantenuto da una
persona nel tempo libero: i tempi della correzione dipendono dalla gravità, e ti terrò
aggiornato finché il problema non è risolto. Se lo desideri, la correzione ti citerà
come autore della segnalazione.

## Cosa rientra

Sono di particolare interesse i problemi che espongono i dati finanziari o le
credenziali delle sorgenti: la cifratura dei token nel database, l'import dalle
sorgenti esterne, le dipendenze con vulnerabilità note.

L'API non prevede autenticazione, per scelta documentata nel [README](README.md):
l'assenza di un login non è di per sé una vulnerabilità.
