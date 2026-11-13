(() => {
  const cuts = __CUTS__;
  // The narrated cut has the same scenes after a longer title card.
  const shift = __SHIFT__;
  const player = document.getElementById('player');
  const note = document.getElementById('cut-note');
  const cutButtons = [...document.querySelectorAll('[data-cut]')];
  const chapterButtons = [...document.querySelectorAll('[data-chapter]')];
  let cut = 0;

  const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`;
  const seek = (t) => {
    const go = () => { player.currentTime = t; player.play().catch(() => {}); };
    if (player.readyState >= 1) go(); else player.addEventListener('loadedmetadata', go, {once: true});
  };

  const setCut = (i, keepScene) => {
    if (i === cut) return;
    const from = cuts[cut].chapters[0];
    const local = player.currentTime - from;
    cut = i;
    cutButtons.forEach((b, j) => b.setAttribute('aria-pressed', String(j === i)));
    note.textContent = cuts[i].note;
    chapterButtons.forEach((b, j) => { b.querySelector('.ch-time').textContent = fmt(cuts[i].chapters[j]); });
    player.src = cuts[i].file;
    if (keepScene && local > 0) seek(cuts[i].chapters[0] + local);
  };
  cutButtons.forEach((b, i) => b.addEventListener('click', () => setCut(i, true)));
  chapterButtons.forEach((b, i) => b.addEventListener('click', () => seek(cuts[cut].chapters[i])));

  player.addEventListener('timeupdate', () => {
    const t = player.currentTime;
    const marks = cuts[cut].chapters;
    let on = -1;
    marks.forEach((m, i) => { if (t >= m) on = i; });
    chapterButtons.forEach((b, i) => b.classList.toggle('is-on', i === on));
  });

  document.querySelectorAll('[data-seek]').forEach((b) => b.addEventListener('click', () => {
    const t = Number(b.dataset.seek) + (cut === 1 ? shift : 0);
    document.getElementById('video').scrollIntoView({behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'});
    seek(t);
  }));

  // Lightbox over every screen on the page, in page order.
  const frames = [...document.querySelectorAll('[data-full]')];
  const box = document.getElementById('lightbox');
  const img = document.getElementById('lb-img');
  const cap = document.getElementById('lb-cap');
  const count = document.getElementById('lb-count');
  let at = 0;
  let opener = null;
  const show = (i) => {
    at = (i + frames.length) % frames.length;
    const f = frames[at];
    img.src = f.dataset.full;
    img.alt = f.dataset.caption;
    cap.textContent = f.dataset.caption;
    count.textContent = `${at + 1} / ${frames.length}`;
  };
  const open = (i, el) => { opener = el; show(i); box.hidden = false; document.getElementById('lb-close').focus(); };
  const close = () => { box.hidden = true; img.removeAttribute('src'); if (opener) opener.focus(); };
  frames.forEach((f, i) => f.addEventListener('click', () => open(i, f)));
  document.getElementById('lb-prev').addEventListener('click', () => show(at - 1));
  document.getElementById('lb-next').addEventListener('click', () => show(at + 1));
  document.getElementById('lb-close').addEventListener('click', close);
  box.addEventListener('click', (ev) => { if (ev.target === box) close(); });
  document.addEventListener('keydown', (ev) => {
    if (box.hidden) return;
    if (ev.key === 'Escape') close();
    else if (ev.key === 'ArrowLeft') show(at - 1);
    else if (ev.key === 'ArrowRight') show(at + 1);
  });

  // Mark the nav link of the section in view.
  const links = new Map([...document.querySelectorAll('.toc a')].map((a) => [a.getAttribute('href').slice(1), a]));
  const seen = new IntersectionObserver((entries) => {
    entries.forEach((en) => {
      if (!en.isIntersecting) return;
      links.forEach((a) => a.removeAttribute('aria-current'));
      const a = links.get(en.target.id);
      if (a) { a.setAttribute('aria-current', 'true'); a.parentElement.scrollTo({left: a.offsetLeft - 16}); }
    });
  }, {rootMargin: '-30% 0px -60% 0px'});
  links.forEach((_, id) => { const el = document.getElementById(id); if (el) seen.observe(el); });
})();
