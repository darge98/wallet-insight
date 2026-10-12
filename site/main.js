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
