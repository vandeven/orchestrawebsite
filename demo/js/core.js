'use strict';
/* Clickable demo: no server, no real data. State lives in localStorage so changes survive a reload. */
const App = { S: null, pages: {}, actions: {} };
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const st = () => App.S;
const ROLES = { member: 'Member', admin: 'Admin', master: 'Master' };
const loggedIn = () => !!st().role;
const isAdmin = () => st().role === 'admin' || st().role === 'master';
const TZ = 'Europe/Amsterdam';

/* Dates: always Europe/Amsterdam, written so they cannot be misread (see docs/PLAN.md). */
const fmtDate = d => d ? new Date(d + 'T12:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: TZ }) : '';
const fmtDateTime = d => { const x = new Date(d); return x.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: TZ }) + ', ' + x.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: TZ }); };
const today = () => new Date().toISOString().slice(0, 10);
const reqId = () => 'r-' + Math.random().toString(16).slice(2, 10);

const svg = (label, c1, c2) => 'data:image/svg+xml;utf8,' + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="400"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/></linearGradient></defs><rect width="640" height="400" fill="url(#g)"/><text x="320" y="210" font-size="34" text-anchor="middle" fill="#fff" font-family="sans-serif">${label}</text></svg>`);

function save() { try { localStorage.setItem('orchestraDemo', JSON.stringify(App.S)); } catch (e) { /* storage may be unavailable */ } }
function loadState() {
  try { const x = JSON.parse(localStorage.getItem('orchestraDemo')); if (x && x.texts && x.v === App.initial().v) return x; } catch (e) { }
  return App.initial();
}

let toastTimer;
function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2800); }

/* Dialogs (native <dialog>) */
function confirmDlg(title, bodyHtml, okLabel = 'Confirm', danger = true) {
  return new Promise(res => {
    const d = $('#dlg');
    d.innerHTML = `<form method="dialog"><h2 style="margin-top:0">${esc(title)}</h2><div>${bodyHtml}</div><div class="row" style="margin-top:18px;justify-content:flex-end"><button class="btn sec" value="no">Cancel</button><button class="btn ${danger ? 'danger' : ''}" value="yes">${esc(okLabel)}</button></div></form>`;
    d.onclose = () => res(d.returnValue === 'yes'); d.returnValue = ''; d.showModal();
  });
}
function formDlg(title, bodyHtml, okLabel, validate) {
  return new Promise(res => {
    const d = $('#dlg');
    d.innerHTML = `<form method="dialog" novalidate><h2 style="margin-top:0">${esc(title)}</h2>${bodyHtml}<div class="row" style="margin-top:18px;justify-content:flex-end"><button class="btn sec" value="no" formnovalidate>Cancel</button><button class="btn" value="yes">${esc(okLabel)}</button></div></form>`;
    const f = d.querySelector('form');
    f.onsubmit = e => { if (e.submitter && e.submitter.value === 'yes' && validate && !validate(f)) e.preventDefault(); };
    d.onclose = () => res(d.returnValue === 'yes' ? new FormData(f) : null); d.returnValue = ''; d.showModal();
  });
}
function infoDlg(title, bodyHtml) {
  const d = $('#dlg');
  d.innerHTML = `<h2 style="margin-top:0">${esc(title)}</h2><div>${bodyHtml}</div><form method="dialog" style="margin-top:16px"><button class="btn">Close</button></form>`;
  d.onclose = null; d.showModal();
}

/* Field errors: text with an icon, linked to the field, announced to screen readers (DES-8) */
function setErr(input, msg) {
  const id = input.id || (input.id = 'f' + Math.random().toString(16).slice(2, 8));
  let e = input.parentElement.querySelector('.err[data-for="' + id + '"]');
  if (!msg) { input.removeAttribute('aria-invalid'); input.removeAttribute('aria-describedby'); if (e) e.remove(); return true; }
  input.setAttribute('aria-invalid', 'true');
  if (!e) { e = document.createElement('p'); e.className = 'err'; e.dataset.for = id; e.id = 'err-' + id; e.setAttribute('role', 'alert'); input.setAttribute('aria-describedby', e.id); input.after(e); }
  e.textContent = msg; return false;
}
/* Read a form control by its name (avoids clashes with built-in form properties such as form.name) */
const fld = (f, n) => f.querySelector('[name="' + n + '"]');
const validEmail = v => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v) && v.length <= 254;
const COMMON_PASSWORDS = ['password1234', 'passwordpassword', '123456789012', 'qwertyuiop12', 'iloveyou1234'];
const passwordError = (v, current) => v.length < 12 ? 'Use at least 12 characters.' : v.length > 128 ? 'Use at most 128 characters.' : COMMON_PASSWORDS.includes(v.toLowerCase()) ? 'This password is very common. Choose another one.' : current && v === current ? 'Choose a password that is different from the current one.' : '';

/* A real, playable test tone (4 seconds) so the audio player works without any files */
function makeTone(freq) {
  const rate = 8000, n = rate * 4, b = new DataView(new ArrayBuffer(44 + n * 2)), w = (o, s) => [...s].forEach((c, i) => b.setUint8(o + i, c.charCodeAt(0)));
  w(0, 'RIFF'); b.setUint32(4, 36 + n * 2, true); w(8, 'WAVEfmt '); b.setUint32(16, 16, true); b.setUint16(20, 1, true); b.setUint16(22, 1, true);
  b.setUint32(24, rate, true); b.setUint32(28, rate * 2, true); b.setUint16(32, 2, true); b.setUint16(34, 16, true); w(36, 'data'); b.setUint32(40, n * 2, true);
  for (let i = 0; i < n; i++) b.setInt16(44 + i * 2, Math.sin(2 * Math.PI * freq * i / rate) * 3000 * (1 - i / n), true);
  return URL.createObjectURL(new Blob([b], { type: 'audio/wav' }));
}

/* Safe HTML only: an allowlist, like the real sanitizer (EDT-6) */
const SAFE_TAGS = new Set(['H2', 'H3', 'H4', 'P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'STRIKE', 'UL', 'OL', 'LI', 'A', 'BLOCKQUOTE', 'HR', 'TABLE', 'TBODY', 'THEAD', 'TR', 'TD', 'TH', 'IMG', 'DIV']);
function sanitize(html) {
  const doc = new DOMParser().parseFromString('<body>' + html, 'text/html'), removed = [];
  (function walk(n) {
    [...n.children].forEach(c => {
      if (!SAFE_TAGS.has(c.tagName)) { removed.push('<' + c.tagName.toLowerCase() + '>'); const keep = ['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'EMBED'].includes(c.tagName) ? [] : [...c.childNodes]; c.replaceWith(...keep); return; }
      [...c.attributes].forEach(a => {
        const okAttr = (c.tagName === 'A' && a.name === 'href') || (c.tagName === 'IMG' && ['src', 'alt', 'class'].includes(a.name)) || (a.name === 'style' && /^text-align:\s*(left|center|right);?$/.test(a.value));
        if (!okAttr) { removed.push(a.name + ' attribute'); c.removeAttribute(a.name); }
      });
      if (c.tagName === 'A') { const h = c.getAttribute('href') || ''; if (!/^(https?:|mailto:|#)/i.test(h)) { removed.push('unsafe link'); c.removeAttribute('href'); } else if (/^https?:/i.test(h)) { c.setAttribute('target', '_blank'); c.setAttribute('rel', 'noopener'); } }
      if (c.tagName === 'IMG' && !/^(data:image|\/|\.|https:)/.test(c.getAttribute('src') || '')) { removed.push('image source'); c.removeAttribute('src'); }
      walk(c);
    });
  })(doc.body);
  return { html: doc.body.innerHTML, removed: [...new Set(removed)] };
}
const textHtml = key => (st().texts[key].html || '').trim() ? st().texts[key].html : '';
const imgUsedIn = src => Object.values(st().texts).filter(t => t.html.includes(src)).map(t => t.title);
const emailOf = role => ({ member: 'member@example.org', admin: 'admin@example.org', master: 'master@example.org' }[role]);
