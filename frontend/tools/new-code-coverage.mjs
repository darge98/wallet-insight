#!/usr/bin/env node
/**
 * La coverage delle sole righe cambiate rispetto a un ramo, letta dagli lcov di
 * `nx run-many -t test --coverage`: è la condizione su cui il quality gate di
 * SonarCloud ferma le PR, misurata prima di aprirle.
 *
 *   node tools/new-code-coverage.mjs [ramo]   # default origin/main
 *
 * Come Sonar conta righe e rami insieme: (rami presi + righe eseguite) / (rami + righe).
 * Conta anche le modifiche non ancora committate. Come «Sonar way», sotto le 20
 * righe da coprire la soglia non si applica.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const SOGLIA = 80;
const RIGHE_MINIME = 20;
const ESCLUSI = [/\.spec\.ts$/, /test-setup\.ts$/];

const base = process.argv[2] ?? 'origin/main';
const git = (...args) => execFileSync('git', args, { encoding: 'utf8' });

const righeNuove = new Map();
let file = null;
for (const riga of git(
  'diff',
  '-U0',
  '--no-color',
  '--relative',
  git('merge-base', base, 'HEAD').trim(),
).split('\n')) {
  if (riga.startsWith('+++ ')) {
    const percorso = riga.slice(4).replace(/^b\//, '');
    const interessa =
      /^(apps|libs)\/.*\.ts$/.test(percorso) && !ESCLUSI.some((re) => re.test(percorso));
    file = interessa ? percorso : null;
    if (file) righeNuove.set(file, new Set());
  } else if (file && riga.startsWith('@@')) {
    const [, inizio, quante = '1'] = riga.match(/\+(\d+)(?:,(\d+))?/);
    for (let n = 0; n < Number(quante); n++) righeNuove.get(file).add(Number(inizio) + n);
  }
}

// Ogni libreria scrive i percorsi relativi a sé, nella cartella che porta il suo nome.
const coperture = new Map();
const lcov = (dir) =>
  readdirSync(dir).flatMap((voce) => {
    const percorso = join(dir, voce);
    if (statSync(percorso).isDirectory()) return voce === 'lcov-report' ? [] : lcov(percorso);
    return voce === 'lcov.info' ? [percorso] : [];
  });
for (const report of lcov('coverage')) {
  const radice = relative('coverage', join(report, '..'));
  let righe = null;
  let rami = null;
  for (const riga of readFileSync(report, 'utf8').split('\n')) {
    if (riga.startsWith('SF:')) {
      const sorgente = riga.slice(3);
      righe = new Map();
      rami = new Map();
      coperture.set(sorgente.startsWith(`${radice}/`) ? sorgente : `${radice}/${sorgente}`, {
        righe,
        rami,
      });
    } else if (righe && riga.startsWith('DA:')) {
      const [numero, passaggi] = riga.slice(3).split(',').map(Number);
      righe.set(numero, passaggi);
    } else if (rami && riga.startsWith('BRDA:')) {
      const [numero, , , presi] = riga.slice(5).split(',');
      const ramo = rami.get(Number(numero)) ?? { totali: 0, presi: 0 };
      ramo.totali += 1;
      ramo.presi += presi === '-' || presi === '0' ? 0 : 1;
      rami.set(Number(numero), ramo);
    }
  }
}

let daCoprire = 0;
let righeDaCoprire = 0;
let coperte = 0;
const nonMisurati = [];
for (const [percorso, nuove] of [...righeNuove].sort()) {
  const copertura = coperture.get(percorso);
  if (!copertura) {
    nonMisurati.push(percorso);
    continue;
  }
  const { righe, rami } = copertura;
  const eseguibili = [...nuove].filter((n) => righe.has(n));
  const condizioni = [...nuove].filter((n) => rami.has(n)).map((n) => [n, rami.get(n)]);
  const totale = eseguibili.length + condizioni.reduce((somma, [, ramo]) => somma + ramo.totali, 0);
  if (totale === 0) continue;
  const scoperte = new Set([
    ...eseguibili.filter((n) => righe.get(n) === 0),
    ...condizioni.filter(([, ramo]) => ramo.presi < ramo.totali).map(([n]) => n),
  ]);
  const prese =
    eseguibili.filter((n) => righe.get(n) > 0).length +
    condizioni.reduce((somma, [, ramo]) => somma + ramo.presi, 0);
  daCoprire += totale;
  righeDaCoprire += eseguibili.length;
  coperte += prese;
  const segno = scoperte.size === 0 ? '✓' : '✗';
  const dettaglio =
    scoperte.size === 0 ? '' : `  da coprire: ${[...scoperte].sort((a, b) => a - b).join(', ')}`;
  console.log(`${segno} ${percorso}  ${prese}/${totale}${dettaglio}`);
}

if (nonMisurati.length > 0) {
  console.log(
    `\nSenza dati di coverage (manca --coverage o un lcov?):\n  ${nonMisurati.join('\n  ')}`,
  );
}

const percentuale = daCoprire === 0 ? 100 : (coperte / daCoprire) * 100;
console.log(
  `\nCodice nuovo rispetto a ${base}: ${percentuale.toFixed(1)}% su ${daCoprire} fra righe e rami.`,
);
if (righeDaCoprire < RIGHE_MINIME) {
  console.log(`Sotto le ${RIGHE_MINIME} righe la soglia non si applica.`);
} else if (percentuale < SOGLIA) {
  console.log(`Sotto la soglia del ${SOGLIA}%: il quality gate fermerebbe la PR.`);
  process.exitCode = 1;
}
