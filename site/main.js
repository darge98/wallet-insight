// Comportamenti della landing: entrate in vista, copia
// dei comandi e bordo della barra. Tutto progressivo: senza JS la pagina è
// completa, solo ferma.

// Entrate in vista. IntersectionObserver e non un listener di scroll: il
// browser avvisa una volta per elemento, senza lavoro a ogni frame.
const reveals = document.querySelectorAll('.reveal');
if ('IntersectionObserver' in window) {
  // Gli elementi fratelli entrano a scaglioni, nell'ordine di lettura.
  reveals.forEach((el) => {
    const siblings = [...el.parentElement.children].filter((c) => c.classList.contains('reveal'));
    el.style.setProperty('--i', siblings.indexOf(el));
  });

  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          io.unobserve(entry.target);
        }
      }
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.12 },
  );
  reveals.forEach((el) => io.observe(el));
} else {
  reveals.forEach((el) => el.classList.add('is-visible'));
}

// Copia dei comandi d'installazione.
for (const button of document.querySelectorAll('[data-copy]')) {
  const label = button.querySelector('.copy__label');
  button.addEventListener('click', async () => {
    const text = document.getElementById(button.dataset.copy).innerText;
    try {
      await navigator.clipboard.writeText(text);
      label.textContent = 'Copiato';
    } catch {
      label.textContent = 'Copia non riuscita';
    }
    setTimeout(() => (label.textContent = 'Copia'), 2000);
  });
}

// Il bordo della barra compare quando il contenuto le scorre sotto: una
// sentinella in cima alla pagina, osservata, al posto di leggere scrollY.
const nav = document.querySelector('.nav');
const sentinel = document.createElement('div');
sentinel.setAttribute('aria-hidden', 'true');
sentinel.style.cssText = 'position:absolute;top:0;height:1px;width:1px;';
document.body.prepend(sentinel);
new IntersectionObserver(([entry]) => {
  nav.classList.toggle('is-scrolled', !entry.isIntersecting);
}).observe(sentinel);

// -----------------------------------------------------------------------------
// Animazioni di layout (FLIP: First, Last, Invert, Play).
//
// Quando il layout cambia, l'elemento non viene animato nelle sue proprietà di
// layout: si misura dov'era (First), si applica il cambio, si misura dov'è
// (Last), lo si riporta visivamente al punto di partenza con un transform
// (Invert) e si anima il transform verso zero (Play). Il browser ricalcola il
// layout una volta sola e l'animazione tocca solo transform e opacity.
// -----------------------------------------------------------------------------

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const EASE = 'cubic-bezier(0.16, 1, 0.3, 1)';

/** Sposta `elements` dalla posizione precedente a `mutate()` a quella successiva. */
function flip(elements, mutate, { duration = 420 } = {}) {
  if (reducedMotion.matches) {
    mutate();
    return [];
  }
  const first = elements.map((el) => el.getBoundingClientRect());
  mutate();
  return elements.flatMap((el, i) => {
    const last = el.getBoundingClientRect();
    const dx = first[i].left - last.left;
    const dy = first[i].top - last.top;
    if (!dx && !dy) return [];
    return [
      el.animate([{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'none' }], {
        duration,
        easing: EASE,
      }),
    ];
  });
}

/** La trasformazione che porta il rettangolo `to` sopra il rettangolo `from`. */
function invert(from, to) {
  return `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${from.width / to.width}, ${from.height / to.height})`;
}

// Domande frequenti: aprendo o chiudendo una risposta, le domande sotto
// scivolano nella nuova posizione invece di saltare.
for (const details of document.querySelectorAll('.faq__list details')) {
  const summary = details.querySelector('summary');
  const answer = details.querySelector('p');
  const below = () => {
    const all = [...details.parentElement.children];
    return all.slice(all.indexOf(details) + 1);
  };

  summary.addEventListener('click', async (event) => {
    if (reducedMotion.matches) return;
    event.preventDefault();
    if (details.open) {
      // La risposta sparisce prima che le altre risalgano a coprirne il posto.
      await answer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 120, easing: 'ease-out' }).finished;
      flip(below(), () => (details.open = false));
    } else {
      flip(below(), () => (details.open = true));
      answer.animate(
        [
          { opacity: 0, transform: 'translateY(-6px)' },
          { opacity: 1, transform: 'none' },
        ],
        { duration: 360, delay: 60, easing: EASE, fill: 'backwards' },
      );
    }
  });
}

// Screenshot ingrandito: l'immagine parte dalla miniatura e ci torna, come se
// fosse lo stesso elemento che cambia posto e dimensione.
const viewer = document.querySelector('.viewer');
const viewerImg = viewer?.querySelector('.viewer__img');
let source = null;

function openViewer(thumb) {
  source = thumb;
  // currentSrc rispetta la variante chiara o scura scelta dal <picture>.
  viewerImg.src = thumb.currentSrc || thumb.src;
  viewerImg.alt = thumb.alt;
  // Le dimensioni vere subito, prima che l'immagine sia decodificata: la
  // posizione finale va misurata adesso, non quando arriva il primo frame.
  viewerImg.width = thumb.naturalWidth;
  viewerImg.height = thumb.naturalHeight;
  viewer.showModal();
  if (reducedMotion.matches) return;

  const from = thumb.getBoundingClientRect();
  const to = viewerImg.getBoundingClientRect();
  thumb.style.visibility = 'hidden';
  viewerImg
    .animate([{ transform: invert(from, to) }, { transform: 'none' }], { duration: 520, easing: EASE })
    .finished.finally(() => (thumb.style.visibility = ''));
  viewer.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 300, pseudoElement: '::backdrop' });
}

async function closeViewer() {
  if (!viewer.open || viewer.dataset.closing) return;
  const thumb = source;
  if (!reducedMotion.matches && thumb) {
    viewer.dataset.closing = 'true';
    thumb.style.visibility = 'hidden';
    // La miniatura si rimisura adesso: la pagina sotto può essere cambiata.
    const from = viewerImg.getBoundingClientRect();
    const to = thumb.getBoundingClientRect();
    viewer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 360, pseudoElement: '::backdrop', fill: 'forwards' });
    viewer.querySelector('.viewer__close').animate([{ opacity: 1 }, { opacity: 0 }], { duration: 160, fill: 'forwards' });
    const scale = `translate(${to.left - from.left}px, ${to.top - from.top}px) scale(${to.width / from.width}, ${to.height / from.height})`;
    await viewerImg.animate([{ transform: 'none' }, { transform: scale }], { duration: 420, easing: EASE, fill: 'forwards' }).finished;
    viewer.getAnimations({ subtree: true }).forEach((a) => a.cancel());
    delete viewer.dataset.closing;
  }
  viewer.close();
  if (thumb) thumb.style.visibility = '';
  thumb?.closest('.shot__open')?.focus({ preventScroll: true });
}

if (viewer) {
  for (const button of document.querySelectorAll('.shot__open')) {
    button.addEventListener('click', () => openViewer(button.querySelector('img')));
  }
  viewer.querySelector('.viewer__close').addEventListener('click', closeViewer);
  viewerImg.addEventListener('click', closeViewer);
  // Esc e clic sullo sfondo chiudono con la stessa animazione.
  viewer.addEventListener('cancel', (event) => {
    event.preventDefault();
    closeViewer();
  });
  viewer.addEventListener('click', (event) => {
    if (event.target === viewer) closeViewer();
  });
  // Il browser può chiudere il dialog senza aspettare l'animazione (un Esc che
  // non si lascia annullare): la miniatura torna visibile comunque.
  viewer.addEventListener('close', () => {
    viewer.getAnimations({ subtree: true }).forEach((a) => a.cancel());
    delete viewer.dataset.closing;
    if (source) source.style.visibility = '';
  });
}
