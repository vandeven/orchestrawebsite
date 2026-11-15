'use strict';
/* Admin pages: song management, edit pages (editor), image library, user management */

/* ---------- Song management ---------- */
App.pages['/songs'] = {
  access: 'admin', title: 'Song management',
  render() {
    const list = act => { const ss = st().songs.filter(s => s.active === act); return ss.length ? ss.map(s => `<div class="card"><div class="row"><h3 class="grow" style="margin:0">${esc(s.title)}</h3>
      <button class="btn sm sec" data-action="edit-song" data-id="${s.id}">Edit</button>
      <button class="btn sm sec" data-action="${act ? 'archive' : 'unarchive'}" data-id="${s.id}">${act ? 'Archive' : 'Unarchive'}</button>
      <button class="btn sm danger" data-action="del-song" data-id="${s.id}">Delete</button></div>
      ${s.notes ? `<p class="hint">${esc(s.notes)}</p>` : ''}
      <ul>${s.files.map(f => `<li>${ICON[f.type]} ${esc(f.name)} <span class="hint">(${TYPE_NAME[f.type]})</span>
        <button class="btn sm sec" data-action="rename-file" data-id="${s.id}" data-file="${f.id}">Rename</button> <button class="btn sm sec" data-action="del-file" data-id="${s.id}" data-file="${f.id}">Remove</button></li>`).join('') || '<li class="hint">No files yet</li>'}</ul>
      <button class="btn sm" data-action="add-file" data-id="${s.id}">+ Add file</button></div>`).join('') : '<p class="hint">None</p>'; };
    return `<h1>Song management <span class="role-tag">${ROLES[st().role]}</span></h1>
    <form id="addsong" class="card" novalidate><h2>Add a song</h2><label for="s-title">Title</label><input id="s-title" maxlength="200">
      <label for="s-notes">Notes (optional)</label><input id="s-notes" maxlength="2000"><button class="btn" style="margin-top:14px">Add song</button></form>
    <h2>Active songs</h2>${list(true)}<h2>Archived songs</h2><p class="hint">Members cannot see archived songs.</p>${list(false)}`;
  },
  after() {
    $('#addsong').onsubmit = e => { e.preventDefault(); const t = $('#s-title');
      if (!setErr(t, !t.value.trim() ? 'Enter a title.' : '')) { t.focus(); return; }
      st().songs.unshift({ id: ++st().seq, title: t.value.trim(), notes: $('#s-notes').value.trim(), active: true, files: [] }); save(); toast('Song added. Members can see it right away.'); App.route(); };
  }
};
const songOf = el => st().songs.find(s => s.id === +el.dataset.id);
App.actions['edit-song'] = async el => {
  const s = songOf(el);
  const fd = await formDlg('Edit song', `<label for="et">Title</label><input id="et" name="title" maxlength="200" value="${esc(s.title)}"><label for="en">Notes (optional)</label><input id="en" name="notes" maxlength="2000" value="${esc(s.notes)}">`, 'Save', f => setErr(fld(f, 'title'), !fld(f, 'title').value.trim() ? 'Enter a title.' : ''));
  if (fd) { s.title = fd.get('title').trim(); s.notes = fd.get('notes').trim(); save(); toast('Song updated'); App.route(); }
};
App.actions['archive'] = el => { songOf(el).active = false; save(); toast('Archived: members no longer see this song'); App.route(); };
App.actions['unarchive'] = el => { songOf(el).active = true; save(); toast('Song is active again'); App.route(); };
App.actions['del-song'] = async el => {
  const s = songOf(el);
  if (await confirmDlg('Delete “' + s.title + '”?', '<p>This removes the song and all its files. <strong>This cannot be undone.</strong> Archive the song instead if it may be needed again.</p>', 'Delete forever')) { st().songs = st().songs.filter(x => x !== s); save(); toast('Song deleted'); App.route(); }
};
App.actions['del-file'] = async el => {
  const s = songOf(el), f = s.files.find(x => x.id === +el.dataset.file);
  if (await confirmDlg('Remove file?', `<p>Remove “${esc(f.name)}” from “${esc(s.title)}”?</p>`, 'Remove')) { s.files = s.files.filter(x => x !== f); save(); toast('File removed'); App.route(); }
};
App.actions['rename-file'] = async el => {
  const s = songOf(el), f = s.files.find(x => x.id === +el.dataset.file);
  const fd = await formDlg('Rename file', `<label for="rn">Display name</label><input id="rn" name="name" maxlength="100" value="${esc(f.name)}">`, 'Save', fm => setErr(fld(fm, 'name'), !fld(fm, 'name').value.trim() ? 'Enter a display name.' : ''));
  if (fd) { f.name = fd.get('name').trim(); save(); toast('File renamed'); App.route(); }
};
App.actions['add-file'] = async el => {
  const s = songOf(el);
  const fd = await formDlg('Add file to “' + s.title + '”', `<label for="ft">Type</label><select id="ft" name="type"><option value="sheet">Sheet music (PDF)</option><option value="audio">Audio (MP3)</option><option value="lyrics">Lyrics (TXT)</option></select>
    <label for="fn">Display name</label><input id="fn" name="name" maxlength="100" placeholder="For example Soprano"><label for="ff">File</label><input id="ff" name="file" type="file"><p class="hint">PDF, MP3 or TXT, at most 15 MB. Keep your own copy of every original file.</p>`, 'Add file', f => {
    const ext = { sheet: 'pdf', audio: 'mp3', lyrics: 'txt' }[fld(f, 'type').value], file = fld(f, 'file').files[0]; let ok = true;
    ok = setErr(fld(f, 'name'), !fld(f, 'name').value.trim() ? 'Enter a display name.' : '') && ok;
    ok = setErr(fld(f, 'file'), !file ? 'Choose a file.' : !file.name.toLowerCase().endsWith('.' + ext) ? 'This type needs a .' + ext + ' file.' : file.size > 15 * 1048576 ? 'The file is larger than 15 MB.' : '') && ok; return ok; });
  if (fd) { s.files.push({ id: ++st().seq, type: fd.get('type'), name: fd.get('name').trim(), f: 440 }); save(); toast('File added'); App.route(); }
};

/* ---------- Edit pages and the editor ---------- */
App.pages['/edit-pages'] = {
  access: 'admin', title: 'Edit pages',
  render: () => `<h1>Edit pages <span class="role-tag">${ROLES[st().role]}</span></h1>
  <div class="scroll"><table><thead><tr><th scope="col">Text</th><th scope="col">Shown on</th><th scope="col">Last changed</th><th scope="col"><span class="sr">Edit</span></th></tr></thead><tbody>
  ${Object.entries(st().texts).map(([k, t]) => `<tr><td>${esc(t.title)}</td><td>${esc(t.page)}</td><td>${fmtDate(t.changed)}</td><td><a class="btn sm" href="#/edit/${k}">Edit</a></td></tr>`).join('')}</tbody></table></div>
  <p><a class="btn sec" href="#/images">Image library</a></p>`
};
let editorMode = 'wysiwyg';
App.pages['/edit'] = {
  access: 'admin', title: 'Edit text',
  render(key) {
    const t = st().texts[key]; if (!t) return '<h1>Not found</h1><p><a href="#/edit-pages">Back to all texts</a></p>';
    editorMode = 'wysiwyg';
    const tools = [['formatBlock:H2', 'H2'], ['formatBlock:H3', 'H3'], ['formatBlock:H4', 'H4'], ['formatBlock:P', '¶'], ['bold', '<b>B</b>'], ['italic', '<i>I</i>'], ['underline', '<u>U</u>'], ['strikeThrough', '<s>S</s>'], ['insertUnorderedList', '• List'], ['insertOrderedList', '1. List'], ['formatBlock:BLOCKQUOTE', '“ ”'], ['insertHorizontalRule', '―'], ['justifyLeft', '⇤'], ['justifyCenter', '↔'], ['justifyRight', '⇥']];
    return `<h1>Edit: ${esc(t.title)}</h1><p><a href="#/edit-pages">← Back to all texts</a></p>
    <div class="row" role="tablist" aria-label="Editor view" style="margin-bottom:8px"><button class="btn sm" id="tab-w" role="tab" aria-selected="true">Editor</button><button class="btn sm sec" id="tab-h" role="tab" aria-selected="false">HTML</button></div>
    <div id="toolbar" class="toolbar" role="toolbar" aria-label="Formatting">${tools.map(([c, l]) => `<button type="button" data-cmd="${c}" title="${c.replace('formatBlock:', 'Block ')}">${l}</button>`).join('')}
      <button type="button" data-action="ed-link">Link</button><button type="button" data-action="ed-table">Table</button><button type="button" data-action="ed-image">Image</button></div>
    <div id="wys" class="editor content" contenteditable="true" role="textbox" aria-multiline="true" aria-label="Text editor">${t.html}</div>
    <textarea id="htmlv" class="htmlview" hidden aria-label="HTML view"></textarea><div id="warn"></div>
    <div class="row" style="margin-top:14px"><button class="btn sec" data-action="ed-preview">Preview</button><button class="btn" data-action="ed-save" data-key="${key}">Save</button><a class="btn sec" href="#/edit-pages">Cancel</a></div>
    ${key === 'audition' || key === 'agenda' ? '<p class="hint">Tip: clear the text and save to hide the audition section, or to show the “no performances” message.</p>' : ''}`;
  },
  after() {
    const w = $('#wys'), h = $('#htmlv');
    const show = m => {
      if (m === 'html') h.value = w.innerHTML; else if (!h.hidden) w.innerHTML = sanitize(h.value).html;
      editorMode = m; w.hidden = m === 'html'; h.hidden = m !== 'html'; $('#toolbar').style.display = m === 'html' ? 'none' : '';
      $('#tab-w').setAttribute('aria-selected', m !== 'html'); $('#tab-h').setAttribute('aria-selected', m === 'html');
      $('#tab-w').classList.toggle('sec', m === 'html'); $('#tab-h').classList.toggle('sec', m !== 'html');
    };
    $('#tab-w').onclick = () => { if (editorMode === 'html') show('wysiwyg'); }; $('#tab-h').onclick = () => { if (editorMode !== 'html') show('html'); };
    h.oninput = () => { const r = sanitize(h.value); $('#warn').innerHTML = r.removed.length ? `<p class="err" role="alert">This is not allowed and will be removed when saving: ${esc(r.removed.join(', '))}</p>` : ''; };
    $('#toolbar').onmousedown = e => { if (e.target.closest('button')) e.preventDefault(); };
    $('#toolbar').onclick = e => { const b = e.target.closest('[data-cmd]'); if (!b) return; const [c, v] = b.dataset.cmd.split(':'); document.execCommand(c, false, v ? '<' + v + '>' : null); w.focus(); };
  }
};
const edHtml = () => editorMode === 'html' ? $('#htmlv').value : $('#wys').innerHTML;
App.actions['ed-link'] = async () => {
  const fd = await formDlg('Add link', '<label for="lu">Web address or mailto: link</label><input id="lu" name="u" placeholder="https://… or mailto:…">', 'Add', f => setErr(fld(f, 'u'), /^(https?:\/\/|mailto:)/i.test(fld(f, 'u').value) ? '' : 'Use a link that starts with https://, http:// or mailto:'));
  if (fd) { $('#wys').focus(); document.execCommand('createLink', false, fd.get('u')); }
};
App.actions['ed-table'] = () => { $('#wys').focus(); document.execCommand('insertHTML', false, '<table><tr><th>Header</th><th>Header</th></tr><tr><td>Cell</td><td>Cell</td></tr></table><p></p>'); };
App.actions['ed-image'] = async () => {
  const fd = await formDlg('Insert image', `<label for="ii">Choose from the library</label><select id="ii" name="img">${st().images.map(i => `<option value="${i.id}">${esc(i.alt)}</option>`).join('')}</select>
    <label for="ia">Alt text (describe the picture)</label><input id="ia" name="alt"><label style="font-weight:400"><input type="checkbox" name="deco"> Decorative image (no alt text needed)</label>
    <label for="is">Size</label><select id="is" name="size"><option value="s">Small</option><option value="m" selected>Medium</option><option value="l">Full width</option></select>
    <label for="ial">Alignment</label><select id="ial" name="al"><option value="">None</option><option value="left">Left</option><option value="right">Right</option></select>`, 'Insert',
    f => setErr(fld(f, 'alt'), fld(f, 'deco').checked || fld(f, 'alt').value.trim() ? '' : 'Alt text is required, or mark the image as decorative.'));
  if (fd) { const im = st().images.find(i => i.id === +fd.get('img')); $('#wys').focus(); document.execCommand('insertHTML', false, `<img class="${fd.get('size')} ${fd.get('al')}" src="${im.src}" alt="${fd.get('deco') ? '' : esc(fd.get('alt'))}">`); }
};
App.actions['ed-preview'] = () => { const r = sanitize(edHtml()); infoDlg('Preview', `<div class="content">${r.html || '<p class="hint">(empty)</p>'}</div>`); };
App.actions['ed-save'] = el => {
  const r = sanitize(edHtml()), t = st().texts[el.dataset.key];
  t.html = r.html.replace(/^(<p><br><\/p>|<br>)$/, ''); t.changed = today(); save();
  toast('Saved' + (r.removed.length ? ' (removed: ' + r.removed.join(', ') + ')' : '') + '. The change shows on the website within about a minute.'); App.go('#/edit-pages');
};

/* ---------- Image library ---------- */
let imgFilter = 'all';
App.pages['/images'] = {
  access: 'admin', title: 'Image library',
  render() {
    const imgs = st().images.filter(i => imgFilter === 'all' || !imgUsedIn(i.src).length);
    return `<h1>Image library <span class="role-tag">${ROLES[st().role]}</span></h1><p><a href="#/edit-pages">← Back to edit pages</a></p>
    <div class="row"><label for="imgf" style="margin:0">Show</label><select id="imgf" style="width:auto"><option value="all" ${imgFilter === 'all' ? 'selected' : ''}>All images</option><option value="unused" ${imgFilter === 'unused' ? 'selected' : ''}>Unused only</option></select><button class="btn" data-action="upload">Upload image</button></div>
    <p class="hint">JPEG, PNG or WebP, at most 10 MB. Large images are resized in your browser, which also removes location data. Alt text is required.</p>
    <div class="thumbs">${imgs.map(i => { const u = imgUsedIn(i.src); return `<figure class="card" style="margin:0"><img src="${i.src}" alt="${esc(i.alt)}"><figcaption><p style="margin:8px 0 2px"><strong>${esc(i.alt)}</strong></p><p class="hint" style="margin:0">${i.size} · ${fmtDate(i.date)} ${u.length ? '' : '<span class="tag">Unused</span>'}</p><button class="btn sm danger" style="margin-top:8px" data-action="del-img" data-id="${i.id}">Delete</button></figcaption></figure>`; }).join('') || '<p class="hint">No images.</p>'}</div>`;
  },
  after() { $('#imgf').onchange = e => { imgFilter = e.target.value; App.route(); }; }
};
App.actions['del-img'] = async el => {
  const im = st().images.find(i => i.id === +el.dataset.id), u = imgUsedIn(im.src);
  if (await confirmDlg('Delete image?', `<p>${esc(im.alt)}</p>` + (u.length ? '<p><strong>Still used in:</strong> ' + u.map(esc).join(', ') + '</p>' : '<p>This image is not used anywhere.</p>') + '<p><strong>A deleted image cannot be restored.</strong></p>', 'Delete image')) { st().images = st().images.filter(i => i !== im); save(); toast('Image deleted'); App.route(); }
};
App.actions['upload'] = async () => {
  const fd = await formDlg('Upload image', `<p class="notice">Only upload photos of people when they have given permission, and only images the orchestra may use.</p><label for="uf1">Image</label><input id="uf1" name="file" type="file" accept="image/jpeg,image/png,image/webp"><label for="ua">Alt text (short description)</label><input id="ua" name="alt" maxlength="200">`, 'Upload', f => {
    const file = fld(f, 'file').files[0]; let ok = setErr(fld(f, 'file'), !file ? 'Choose an image.' : !/^image\/(jpeg|png|webp)$/.test(file.type) ? 'Only JPEG, PNG and WebP are allowed.' : file.size > 10 * 1048576 ? 'The image is larger than 10 MB.' : '');
    return setErr(fld(f, 'alt'), !fld(f, 'alt').value.trim() ? 'Alt text is required.' : '') && ok; });
  if (fd) { const file = fd.get('file'), r = new FileReader(); r.onload = () => { st().images.unshift({ id: ++st().seq, src: r.result, alt: fd.get('alt').trim(), size: Math.round(file.size / 1024) + ' KB', date: today() }); save(); toast('Image uploaded'); App.route(); }; r.readAsDataURL(file); }
};

/* ---------- User management ---------- */
let uSort = 'email', uDir = 1, uFilter = '';
App.pages['/users'] = {
  access: 'admin', title: 'User management',
  render() {
    const us = st().users.filter(u => !uFilter || u.role === uFilter).sort((a, b) => ((a[uSort] || '') > (b[uSort] || '') ? 1 : -1) * uDir);
    const th = (k, l) => `<th scope="col" aria-sort="${uSort === k ? (uDir > 0 ? 'ascending' : 'descending') : 'none'}"><button data-action="sort" data-k="${k}">${l}${uSort === k ? (uDir > 0 ? ' ▲' : ' ▼') : ''}</button></th>`;
    return `<h1>User management <span class="role-tag">${ROLES[st().role]}</span></h1>
    <form id="adduser" class="card" novalidate><h2>Add a user</h2><label for="u-email">Email address</label><input id="u-email" type="email" autocomplete="off">
      <p class="hint">The new user is a member. They get an email with a link to choose their own password.</p><button class="btn">Add user and send email</button></form>
    <div class="row" style="margin-top:22px"><h2 class="grow" style="margin:0">All users</h2><label for="uf" style="margin:0">Filter</label><select id="uf" style="width:auto"><option value="">All roles</option>${Object.entries(ROLES).map(([k, v]) => `<option value="${k}" ${uFilter === k ? 'selected' : ''}>${v}</option>`).join('')}</select></div>
    <div class="scroll" style="margin-top:8px"><table><thead><tr>${th('email', 'Email address')}${th('role', 'Role')}${th('added', 'Added')}${th('last', 'Last login')}${th('mailed', 'Recovery email sent')}<th scope="col">Actions</th></tr></thead><tbody>
    ${us.map(u => `<tr><td>${esc(u.email)}</td><td><span class="tag ${u.role}">${ROLES[u.role]}</span></td><td>${fmtDate(u.added)}</td><td>${u.last ? fmtDate(u.last) : '<em>Never logged in</em>'}</td><td>${u.mailed ? fmtDate(u.mailed) : '–'}</td><td><div class="row">
      ${u.role !== 'master' ? `<button class="btn sm sec" data-action="resend" data-id="${u.id}">Send email again</button>` : ''}
      ${u.role === 'member' ? `<button class="btn sm sec" data-action="mk-admin" data-id="${u.id}">Make admin</button><button class="btn sm danger" data-action="rm-user" data-id="${u.id}">Remove</button>` : ''}
      ${u.role === 'admin' && st().role === 'master' ? `<button class="btn sm sec" data-action="demote" data-id="${u.id}">Remove admin rights</button><button class="btn sm danger" data-action="rm-user" data-id="${u.id}">Remove</button>` : ''}
      ${u.role === 'master' ? '<span class="hint">Set by the owner</span>' : ''}</div></td></tr>`).join('')}</tbody></table></div>
    ${st().role === 'admin' ? '<p class="hint">Only the master can remove admins or take away admin rights.</p>' : ''}`;
  },
  after() {
    $('#uf').onchange = e => { uFilter = e.target.value; App.route(); };
    $('#adduser').onsubmit = e => {
      e.preventDefault(); const i = $('#u-email'), v = i.value.trim().toLowerCase();
      if (!setErr(i, !v ? 'Enter an email address.' : !validEmail(v) ? 'Enter a valid email address.' : st().users.some(u => u.email === v) ? 'This address already has an account.' : '')) { i.focus(); return; }
      st().users.push({ id: ++st().seq, email: v, role: 'member', added: today(), last: null, mailed: today() }); save(); toast('User added. A password email was sent to ' + v + '.'); App.route();
    };
  }
};
const userOf = el => st().users.find(u => u.id === +el.dataset.id);
App.actions['sort'] = el => { uDir = uSort === el.dataset.k ? -uDir : 1; uSort = el.dataset.k; App.route(); };
App.actions['resend'] = el => { const u = userOf(el); u.mailed = today(); save(); toast('Password email sent again to ' + u.email); App.route(); };
App.actions['mk-admin'] = async el => { const u = userOf(el); if (await confirmDlg('Make admin?', `<p>${esc(u.email)} will be able to manage the website.</p>`, 'Make admin', false)) { u.role = 'admin'; save(); App.route(); } };
App.actions['demote'] = async el => { const u = userOf(el); if (await confirmDlg('Remove admin rights?', `<p>${esc(u.email)} becomes a member.</p>`, 'Remove rights')) { u.role = 'member'; save(); App.route(); } };
App.actions['rm-user'] = async el => { const u = userOf(el); if (await confirmDlg('Remove ' + u.email + '?', '<p>The account is deleted and access stops right away.</p>', 'Remove user')) { st().users = st().users.filter(x => x !== u); save(); App.route(); } };
