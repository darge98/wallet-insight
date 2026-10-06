#!/usr/bin/env node
/**
 * Genera una versione **a file singolo** dell'applicazione.
 *
 * Prende l'output della build `preview`, unisce tutti i chunk JavaScript in un
 * solo bundle (esbuild, formato IIFE: anche gli import dinamici vengono inlined)
 * e incorpora CSS e script in un unico documento HTML autoconsistente.
 *
 * Il risultato si apre con un doppio clic o si pubblica su qualsiasi hosting
 * statico: utile per demo, anteprime e condivisioni rapide. Il routing con hash
 * rende l'operazione possibile senza configurazioni lato server.
 *
 * Uso:
 *   npm run build:single-file
 *   node tools/build-single-file.mjs [--fragment]
 *
 * Con `--fragment` emette solo il contenuto del `<body>` (senza doctype, `html`
 * e `head`), per gli host che forniscono già l'involucro della pagina.
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const BROWSER_DIR = resolve('dist/apps/wallet/browser');
const OUTPUT_FILE = resolve('dist/wallet-insights-standalone.html');
const FONT_IMPORT =
  "@import url('https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,600;9..40,700&display=swap');";

const asFragment = process.argv.includes('--fragment');

function bundleScripts() {
  const workDir = mkdtempSync(join(tmpdir(), 'wallet-single-file-'));
  const outFile = join(workDir, 'bundle.js');

  try {
    execFileSync(
      'npx',
      [
        'esbuild',
        join(BROWSER_DIR, 'main.js'),
        '--bundle',
        '--format=iife',
        '--minify',
        '--target=es2022',
        `--outfile=${outFile}`,
      ],
      { stdio: 'inherit' },
    );

    return readFileSync(outFile, 'utf8');
  } finally {
    rmSync(workDir, { recursive: true, force: true });
  }
}

function buildDocument({ styles, script }) {
  const head = `<title>Wallet Insights</title>
<style>
${FONT_IMPORT}
${styles}
</style>`;

  const body = `<app-root></app-root>
<script>${script}</script>`;

  if (asFragment) {
    return `${head}\n${body}\n`;
  }

  return `<!doctype html>
<html lang="it" data-theme="light">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="color-scheme" content="light dark" />
${head}
</head>
<body>
${body}
</body>
</html>
`;
}

const styles = readFileSync(join(BROWSER_DIR, 'styles.css'), 'utf8');
const script = bundleScripts();
const document = buildDocument({ styles, script });

writeFileSync(OUTPUT_FILE, document, 'utf8');

const sizeKb = Math.round(Buffer.byteLength(document) / 1024);
console.log(`\n✔ ${OUTPUT_FILE} (${sizeKb} kB)`);
