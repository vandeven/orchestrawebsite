'use strict';
/* Pages for logged-in users: member page and My account */
const ICON = { sheet: '📄', audio: '🎵', lyrics: '📝' };
const TYPE_NAME = { sheet: 'sheet music', audio: 'audio', lyrics: 'lyrics' };

App.pages['/members'] = {
  access: 'member', title: 'Member page',
  render() {
    const songs = st().songs.filter(s => s.active);
    return `<h1>Member page <span class="role-tag">${ROLES[st().role]}</span></h1>
    <p class="notice">These files are for members of the orchestra only. Please do not share them. <a href="#/terms">Terms of use</a></p>
    ${songs.length ? songs.map(s => `<article class="card song" aria-labelledby="s${s.id}"><h3 id="s${s.id}">${esc(s.title)}</h3>${s.notes ? `<p class="hint">${esc(s.notes)}</p>` : ''}
      <div class="files">${[['sheet', 'Sheet music'], ['audio', 'Audio'], ['lyrics', 'Lyrics']].map(([t, h]) => {
      const fs = s.files.filter(f => f.type === t);
      return `<div><h4>${h}</h4>${fs.length ? fs.map(f => `<div class="file"><div class="row"><span>${ICON[t]} ${esc(f.name)}</span><button class="btn sm sec" data-action="download" data-name="${esc(s.title + ' - ' + f.name)}">Download</button></div>${t === 'audio' ? `<audio controls preload="none" data-tone="${f.f || 440}" aria-label="Play ${esc(f.name)}"></audio>` : ''}</div>`).join('') : '<p class="hint">None</p>'}</div>`;
    }).join('')}</div></article>`).join('') : placeholder('There are no active songs at the moment.')}`;
  },
  after() { $$('audio[data-tone]').forEach(a => { a.src = makeTone(+a.dataset.tone); }); }
};
App.actions['download'] = el => toast('Demo: the download of “' + el.dataset.name + '” would start');

App.pages['/account'] = {
  access: 'member', title: 'My account',
  render: () => `<h1>My account</h1>
  <div class="card"><p style="margin:0"><strong>Email address:</strong> ${esc(emailOf(st().role))}<br><strong>Role:</strong> ${ROLES[st().role]}</p></div>
  ${st().role === 'master' ? placeholder('The password of the master account is set by the owner and cannot be changed here.') : `
  <form id="chpw" class="card" novalidate style="max-width:460px"><h2>Change password</h2>
    <label for="cp0">Current password</label><input id="cp0" type="password" autocomplete="current-password">
    <label for="cp1">New password</label><input id="cp1" type="password" autocomplete="new-password"><p class="hint">At least 12 characters, and not a very common password.</p>
    <label for="cp2">New password again</label><input id="cp2" type="password" autocomplete="new-password">
    <button class="btn" style="margin-top:14px">Change password</button><div id="cp-result" role="status"></div>
  </form>`}`,
  after() {
    const f = $('#chpw'); if (!f) return;
    f.onsubmit = e => {
      e.preventDefault(); const a = $('#cp0'), b = $('#cp1'), c = $('#cp2'); let ok = true;
      ok = setErr(a, !a.value ? 'Enter your current password.' : a.value === 'wrong' ? 'The current password is not correct.' : '') && ok;
      ok = setErr(b, passwordError(b.value, a.value)) && ok;
      ok = setErr(c, c.value !== b.value ? 'The passwords do not match.' : '') && ok;
      if (ok) { $('#cp-result').innerHTML = `<p class="ok">Your password was changed on ${esc(fmtDateTime(new Date()))}. Your other sessions were logged out and we emailed you a confirmation. (Demo: nothing was sent.)</p>`; f.reset(); }
    };
  }
};
