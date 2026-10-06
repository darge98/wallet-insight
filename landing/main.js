// Dati d'esempio. Gli importi sono interi in centesimi, come nel contratto HTTP dell'app.
document.documentElement.classList.add('js');

const euro = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' });
const euroTondo = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const formatta = (centesimi, segno = false) =>
  (segno && centesimi > 0 ? '+' : '') + euro.format(centesimi / 100);

const NS = 'http://www.w3.org/2000/svg';
const svg = (nome, attributi = {}) => {
  const el = document.createElementNS(NS, nome);
  for (const [k, v] of Object.entries(attributi)) el.setAttribute(k, v);
  return el;
};

// KPI ---------------------------------------------------------------------------

for (const el of document.querySelectorAll('[data-centesimi]')) {
  el.textContent = formatta(Number(el.dataset.centesimi), el.hasAttribute('data-segno'));
}

// Curva cumulativa delle uscite ---------------------------------------------------

// Generatore deterministico: la curva è la stessa a ogni caricamento.
function casuale(seme) {
  return () => {
    seme = (seme * 16807) % 2147483647;
    return seme / 2147483647;
  };
}

// Affitto il primo del mese, poi spese sparse; normalizzate sul totale voluto.
function cumulata(giorni, totale, seme) {
  const r = casuale(seme);
  const pesi = Array.from({ length: giorni }, (_, i) => (i === 0 ? 9 : r() < 0.3 ? 0 : r() * 1.4));
  const somma = pesi.reduce((a, b) => a + b, 0);
  let corrente = 0;
  return pesi.map((p) => (corrente += Math.round((p / somma) * totale)));
}

function disegnaGrafico(contenitore) {
  const L = 560;
  const A = 245;
  const margine = { sx: 44, dx: 8, su: 8, giu: 24 };
  const massimo = 250000;
  const ottobre = cumulata(21, 186760, 11);
  const settembre = cumulata(30, 204510, 7);

  const x = (giorno) => margine.sx + ((giorno - 1) / 30) * (L - margine.sx - margine.dx);
  const y = (centesimi) => A - margine.giu - (centesimi / massimo) * (A - margine.su - margine.giu);
  const percorso = (serie) => serie.map((v, i) => `${i ? 'L' : 'M'}${x(i + 1).toFixed(1)},${y(v).toFixed(1)}`).join('');

  const radice = svg('svg', { viewBox: `0 0 ${L} ${A}`, 'aria-hidden': 'true' });
  const gradiente = svg('linearGradient', { id: 'riempimento', x1: 0, y1: 0, x2: 0, y2: 1 });
  gradiente.append(svg('stop', { offset: 0, class: 'riempimento-da' }), svg('stop', { offset: 1, class: 'riempimento-a' }));
  const defs = svg('defs');
  defs.append(gradiente);
  radice.append(defs);

  for (const valore of [0, 100000, 200000]) {
    radice.append(svg('line', { class: 'griglia', x1: margine.sx, x2: L - margine.dx, y1: y(valore), y2: y(valore) }));
    const etichetta = svg('text', { class: 'asse', x: 0, y: y(valore) + 4 });
    etichetta.textContent = euroTondo.format(valore / 100);
    radice.append(etichetta);
  }
  for (const giorno of [1, 10, 20, 31]) {
    const etichetta = svg('text', { class: 'asse', x: x(giorno), y: A - 4, 'text-anchor': 'middle' });
    etichetta.textContent = giorno;
    radice.append(etichetta);
  }

  const ultimo = ottobre.length;
  radice.append(
    svg('path', { class: 'curva-prima', d: percorso(settembre) }),
    svg('path', { class: 'area', d: `${percorso(ottobre)}L${x(ultimo)},${y(0)}L${x(1)},${y(0)}Z` }),
  );
  const curva = svg('path', { class: 'curva', d: percorso(ottobre) });
  radice.append(curva, svg('circle', { class: 'punto', cx: x(ultimo), cy: y(ottobre[ultimo - 1]), r: 4.5 }));

  contenitore.append(radice);
  curva.style.setProperty('--lunghezza', Math.ceil(curva.getTotalLength()));
}

disegnaGrafico(document.getElementById('grafico-uscite'));

// Classifica per categoria --------------------------------------------------------

const categorie = [
  ['Affitto', 62000],
  ['Spesa', 38450],
  ['Trasporti', 17820],
  ['Ristoranti', 14230],
  ['Svago', 9060],
];

document.getElementById('classifica').append(
  ...categorie.map(([nome, centesimi], i) => {
    const li = document.createElement('li');
    li.innerHTML = `<span class="nome"></span><span class="barra"></span><span class="cifra"></span>`;
    li.querySelector('.nome').textContent = nome;
    li.querySelector('.cifra').textContent = formatta(centesimi);
    const barra = li.querySelector('.barra');
    barra.style.setProperty('--quota', (centesimi / categorie[0][1]).toFixed(3));
    barra.style.setProperty('--i', i);
    return li;
  }),
);

// Budget --------------------------------------------------------------------------

const budget = [
  ['Spesa', 38450, 45000],
  ['Ristoranti', 14230, 15000],
  ['Svago', 9060, 12000],
];

document.getElementById('budget').append(
  ...budget.map(([nome, speso, limite], i) => {
    const quota = speso / limite;
    const attenzione = quota >= 0.9;
    // La scala va oltre il limite, così la tacca resta visibile anche a budget quasi pieno.
    const scala = limite * 1.15;
    const li = document.createElement('li');
    li.innerHTML = `
      <div class="budget__riga"><span></span><span class="cifra"></span></div>
      <div class="budget__barra"><span class="barra"></span></div>
      <span class="budget__stato"><i class="ph" aria-hidden="true"></i><span></span></span>`;
    li.querySelector('.budget__riga > span').textContent = nome;
    li.querySelector('.budget__riga .cifra').textContent = `${formatta(speso)} di ${euroTondo.format(limite / 100)}`;
    const contenitore = li.querySelector('.budget__barra');
    contenitore.style.setProperty('--limite', `${((limite / scala) * 100).toFixed(1)}%`);
    const barra = li.querySelector('.barra');
    barra.style.cssText = `display:block;--quota:${(speso / scala).toFixed(3)};--i:${i}`;
    const stato = li.querySelector('.budget__stato');
    stato.classList.toggle('budget__stato--attenzione', attenzione);
    stato.querySelector('i').classList.add(attenzione ? 'ph-warning' : 'ph-check-circle');
    stato.querySelector('span').textContent = attenzione
      ? `Quasi al limite, restano ${formatta(limite - speso)}`
      : `Restano ${formatta(limite - speso)}`;
    return li;
  }),
);

// Movimenti con filtro --------------------------------------------------------------

const movimenti = [
  { descrizione: 'Stipendio', categoria: 'Lavoro', centesimi: 248000 },
  { descrizione: 'Esselunga', categoria: 'Casa / Spesa', centesimi: -6874 },
  { descrizione: 'Trenitalia', categoria: 'Trasporti', centesimi: -3290 },
  { descrizione: 'Rimborso cena', categoria: 'Fuori / Ristoranti', centesimi: 2150 },
  { descrizione: 'Osteria del Ponte', categoria: 'Fuori / Ristoranti', centesimi: -4300 },
];

const lista = document.getElementById('movimenti');
const totale = document.getElementById('movimenti-totale');

function mostra(natura) {
  const scelti = movimenti.filter(
    (m) => natura === 'tutti' || (natura === 'uscita' ? m.centesimi < 0 : m.centesimi > 0),
  );
  lista.replaceChildren(
    ...scelti.slice(0, 4).map((m, i) => {
      const li = document.createElement('li');
      li.style.setProperty('--i', i);
      li.innerHTML = `<span class="descrizione"></span><span class="cifra"></span><span class="categoria"></span>`;
      li.querySelector('.descrizione').textContent = m.descrizione;
      li.querySelector('.categoria').textContent = m.categoria;
      const cifra = li.querySelector('.cifra');
      cifra.textContent = formatta(m.centesimi, true);
      cifra.classList.toggle('positivo', m.centesimi > 0);
      return li;
    }),
  );
  // Il totale è dell'intero filtro, non delle sole righe visibili: come nell'app.
  totale.textContent = formatta(scelti.reduce((a, m) => a + m.centesimi, 0), true);
}

for (const voce of document.querySelectorAll('.filtro__voce')) {
  voce.addEventListener('click', () => {
    for (const altra of document.querySelectorAll('.filtro__voce')) {
      altra.setAttribute('aria-pressed', String(altra === voce));
    }
    mostra(voce.dataset.natura);
  });
}
mostra('tutti');

// Abbonamenti ---------------------------------------------------------------------

const abbonamenti = [
  { giorno: 3, centesimi: 1399 },
  { giorno: 12, centesimi: 1199 },
  { giorno: 18, centesimi: 299 },
  { giorno: 27, centesimi: 3599 },
];
const mensile = abbonamenti.reduce((a, b) => a + b.centesimi, 0);
document.getElementById('abbonamenti-mese').textContent = formatta(mensile);
document.getElementById('abbonamenti-anno').textContent = formatta(mensile * 12);

const calendario = document.getElementById('calendario');
// Ottobre 2026 comincia di giovedì: tre caselle vuote da lunedì.
for (let i = 0; i < 3; i++) {
  const vuoto = document.createElement('li');
  vuoto.dataset.vuoto = '';
  vuoto.setAttribute('aria-hidden', 'true');
  calendario.append(vuoto);
}
for (let giorno = 1; giorno <= 31; giorno++) {
  const li = document.createElement('li');
  li.textContent = giorno;
  const rinnovo = abbonamenti.find((a) => a.giorno === giorno);
  if (rinnovo) {
    li.dataset.rinnovo = '';
    li.setAttribute('aria-label', `${giorno} ottobre, rinnovo da ${formatta(rinnovo.centesimi)}`);
  } else {
    li.setAttribute('aria-hidden', 'true');
  }
  calendario.append(li);
}

// Comparsa allo scorrimento ---------------------------------------------------------

const osservatore = new IntersectionObserver(
  (voci) => {
    for (const voce of voci) {
      if (!voce.isIntersecting) continue;
      voce.target.dataset.visibile = '';
      osservatore.unobserve(voce.target);
    }
  },
  { threshold: 0.2, rootMargin: '0px 0px -40px 0px' },
);
for (const el of document.querySelectorAll('.rivela')) osservatore.observe(el);

// Bordo della barra di navigazione: compare solo quando la pagina è scorsa.
const sentinella = document.createElement('div');
sentinella.setAttribute('aria-hidden', 'true');
document.body.prepend(sentinella);
new IntersectionObserver(([voce]) => {
  document.querySelector('.nav').toggleAttribute('data-staccata', !voce.isIntersecting);
}).observe(sentinella);

// Tema ----------------------------------------------------------------------------

const pulsanteTema = document.getElementById('cambia-tema');
const scuro = () =>
  document.documentElement.dataset.theme
    ? document.documentElement.dataset.theme === 'dark'
    : matchMedia('(prefers-color-scheme: dark)').matches;

function aggiornaIcona() {
  const icona = pulsanteTema.querySelector('i');
  icona.className = `ph ${scuro() ? 'ph-sun' : 'ph-moon'}`;
  pulsanteTema.setAttribute('aria-label', scuro() ? 'Tema chiaro' : 'Tema scuro');
}

pulsanteTema.addEventListener('click', () => {
  const tema = scuro() ? 'light' : 'dark';
  document.documentElement.dataset.theme = tema;
  try {
    localStorage.setItem('tema', tema);
  } catch {}
  aggiornaIcona();
});
aggiornaIcona();

// Copia dei comandi -----------------------------------------------------------------

document.getElementById('copia').addEventListener('click', async () => {
  const esito = document.getElementById('copia-esito');
  const comandi = [...document.querySelectorAll('#comandi code')]
    .map((c) => c.textContent)
    .join('')
    .split('\n')
    .filter((riga) => riga.startsWith('$ '))
    .map((riga) => riga.slice(2))
    .join('\n');
  try {
    await navigator.clipboard.writeText(comandi);
    esito.textContent = 'Comandi copiati.';
  } catch {
    esito.textContent = 'Copia non riuscita: selezionali a mano.';
  }
});
