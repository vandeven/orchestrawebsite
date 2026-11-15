'use strict';
/* Router, menu, footer and the shared click handling */
const ACCESS_LEVEL = { public: 0, member: 1, admin: 2 };
const canOpen = access => access === 'public' || (access === 'member' && loggedIn()) || (access === 'admin' && isAdmin());

App.go = hash => { if (location.hash === hash) App.route(); else location.hash = hash; };

function chrome(path) {
  const links = [['/', 'About', 1], ['/become-a-member', 'Become a member', 1], ['/agenda', 'Agenda', 1],
    ['/members', 'Member page', loggedIn()], ['/songs', 'Song management', isAdmin()], ['/edit-pages', 'Edit pages', isAdmin()], ['/users', 'User management', isAdmin()], ['/account', 'My account', loggedIn()]];
  const cur = p => path === p || (p === '/edit-pages' && (path.startsWith('/edit') || path === '/images'));
  $('#nav').innerHTML = links.filter(l => l[2]).map(l => `<a href="#${l[0]}" ${cur(l[0]) ? 'aria-current="page"' : ''}>${l[1]}</a>`).join('') +
    '<span class="sep" aria-hidden="true"></span>' + (loggedIn() ? '<button data-action="logout">Log out</button>' : `<a href="#/login" ${path === '/login' ? 'aria-current="page"' : ''}>Log in</a>`);
  $('#demobar').innerHTML = `<span><b>PROTOTYPE</b> flow only, not the specification. Name and design are placeholders.</span>
    <label for="roleSel" style="display:inline;margin:0;font-weight:400">View as: <select id="roleSel" style="width:auto"><option value="">Anonymous visitor</option>${Object.entries(ROLES).map(([k, v]) => `<option value="${k}" ${st().role === k ? 'selected' : ''}>${v}</option>`).join('')}</select></label>
    <button data-action="reset">Reset demo data</button>`;
  $('#roleSel').onchange = e => { st().role = e.target.value || null; save(); toast('Now viewing as ' + (st().role ? ROLES[st().role] : 'an anonymous visitor')); App.route(); };
  $('#footer').innerHTML = `<p>[Orchestra name], a foundation · [Address] · Registration no. [number] · <a href="mailto:info@example.org">info@example.org</a></p>
    <p><a href="#/privacy">Privacy</a> · <a href="#/cookies">Cookies</a> · <a href="#/terms">Terms of use</a> · <a href="#" data-action="social">Instagram</a> · <a href="#" data-action="social">Facebook</a></p>
    ${isAdmin() ? '<p>Version 3f9a1c2 · built 15 November 2026</p>' : ''}`;
  $('#cookie').hidden = st().cookie;
}

function errorPage(title, msg, action) {
  return `<h1>${esc(title)}</h1><p>${esc(msg)}</p>${action || ''}<p class="hint">Request ID: ${reqId()}${isAdmin() ? ' · version 3f9a1c2' : ''}</p>`;
}

App.route = function () {
  let h = (location.hash.slice(1) || '/'), arg = '', key = h;
  if (h === '/about') { location.replace('#/'); return; }
  if (h.startsWith('/edit/')) { arg = h.slice(6); key = '/edit'; }
  const page = App.pages[key];
  let html, after;
  if (!page) html = errorPage('Page not found', 'We could not find that page.', '<a class="btn" href="#/">Go to the home page</a>');
  else if (!canOpen(page.access)) {
    if (!loggedIn()) { st().returnTo = '#' + h; save(); location.replace('#/login'); return; }
    html = errorPage('No access', 'This page is only for admins.', '<a class="btn" href="#/members">Go to the member page</a>');
  } else { html = page.render(arg); after = () => page.after && page.after(arg); }
  $('#main').innerHTML = html; if (after) after();
  chrome(key === '/edit' ? '/edit-pages' : h);
  $('#nav').classList.remove('open'); $('#menubtn').setAttribute('aria-expanded', 'false');
  const h1 = $('#main h1'); document.title = (h1 ? h1.childNodes[0].textContent.trim() + ' · ' : '') + '[Orchestra name] (demo)';
};

App.actions['logout'] = () => { st().role = null; st().returnTo = ''; save(); toast('Logged out'); App.go('#/'); };
App.actions['reset'] = async () => { if (await confirmDlg('Reset the demo?', '<p>All changes you made in the demo are removed.</p>', 'Reset')) { App.S = App.initial(); save(); toast('Demo reset'); App.go('#/'); } };
App.actions['cookie-ok'] = () => { st().cookie = true; save(); $('#cookie').hidden = true; };
App.actions['social'] = (el, e) => { e.preventDefault(); toast('Demo: this would open the social media page'); };

document.addEventListener('click', e => {
  const el = e.target.closest('[data-action]'); if (!el) return;
  const fn = App.actions[el.dataset.action]; if (fn) fn(el, e);
});
$('#menubtn').onclick = e => { const o = $('#nav').classList.toggle('open'); e.currentTarget.setAttribute('aria-expanded', o); };
addEventListener('hashchange', () => { App.route(); scrollTo(0, 0); $('#main').focus(); });
App.route();
