'use strict';
/* Public pages: About (home), Become a member, Agenda, Login, password recovery, legal pages */
const placeholder = msg => `<p class="notice">${msg}</p>`;
const textPage = (title, key, emptyMsg) => ({
  access: 'public', title,
  render: () => `<h1>${esc(title)}</h1><div class="content">${textHtml(key) || placeholder(emptyMsg)}</div>`
});

App.pages['/'] = textPage('About us', 'about', 'Information about the orchestra will follow soon.');
App.pages['/agenda'] = textPage('Agenda', 'agenda', 'There are no performances planned at the moment.');

App.pages['/become-a-member'] = {
  access: 'public', title: 'Become a member',
  render: () => `<h1>Become a member</h1>
  <div class="content">${textHtml('bam') || placeholder('Information about becoming a member will follow soon.')}</div>
  ${textHtml('audition') ? `<section class="card" aria-labelledby="aud"><h2 id="aud">Auditions</h2><div class="content">${textHtml('audition')}</div></section>` : ''}
  <section aria-labelledby="cf"><h2 id="cf">Contact us</h2>
  <form id="contact" class="card" novalidate>
    <label for="c-email">Your email address</label><input id="c-email" name="email" type="email" autocomplete="email">
    <label for="c-subject">Subject</label><input id="c-subject" name="subject" maxlength="200">
    <label for="c-msg">Message</label><textarea id="c-msg" name="message" rows="6" maxlength="5000"></textarea>
    <p class="hint"><span id="c-count">0</span> of 5,000 characters</p>
    <input class="hp" name="website" tabindex="-1" autocomplete="off" aria-hidden="true">
    <p class="hint">Your message is only emailed to our board. It is not stored on this website. See the <a href="#/privacy">privacy page</a>.</p>
    <button class="btn">Send message</button><div id="c-result" role="status"></div>
  </form></section>`,
  after() {
    $('#c-msg').addEventListener('input', e => { $('#c-count').textContent = e.target.value.length.toLocaleString('en-GB'); });
    $('#contact').onsubmit = e => {
      e.preventDefault(); const f = e.target; let ok = true;
      const em = $('#c-email').value.trim();
      ok = setErr($('#c-email'), !em ? 'Enter your email address.' : !validEmail(em) ? 'Enter a valid email address.' : '') && ok;
      ok = setErr($('#c-subject'), !$('#c-subject').value.trim() ? 'Enter a subject.' : '') && ok;
      ok = setErr($('#c-msg'), !$('#c-msg').value.trim() ? 'Enter a message.' : '') && ok;
      if (!ok) { f.querySelector('[aria-invalid=true]').focus(); return; }
      $('#c-result').innerHTML = '<p class="ok">Thank you! Your message was sent to our board. (Demo: nothing was really sent.)</p>';
      f.reset(); $('#c-count').textContent = '0';
    };
  }
};

App.pages['/login'] = {
  access: 'public', title: 'Log in',
  render: () => `<h1>Log in</h1>
  ${st().returnTo ? '<p class="notice">Please log in to see that page. You will go there afterwards.</p>' : ''}
  <form id="login" class="card" novalidate style="max-width:460px">
    <p class="notice">Demo: any input works. Choose which role to look at. Type <code>wrong</code> as password to see the error message.</p>
    <label for="l-email">Email address</label><input id="l-email" type="email" autocomplete="username" placeholder="anyone@example.org">
    <label for="l-pw">Password</label>
    <div class="field-row"><input id="l-pw" type="password" autocomplete="current-password" placeholder="anything"><button type="button" class="btn sec" data-action="toggle-pw" aria-controls="l-pw">Show</button></div>
    <fieldset><legend>Demo: log in as</legend>
      ${Object.entries(ROLES).map(([k, v], i) => `<label style="font-weight:400;margin:6px 0"><input type="radio" name="role" value="${k}" ${i === 0 ? 'checked' : ''}> ${v}</label>`).join('')}</fieldset>
    <div id="l-error" role="alert"></div>
    <button class="btn">Log in</button> <a href="#/forgot" style="margin-left:12px">Forgot password?</a>
  </form>`,
  after() {
    $('#login').onsubmit = e => {
      e.preventDefault();
      if ($('#l-pw').value === 'wrong') { $('#l-error').innerHTML = '<p class="err">Email address or password is incorrect.</p>'; $('#l-pw').value = ''; $('#l-pw').focus(); return; }
      st().role = $('input[name=role]:checked').value; const back = st().returnTo; st().returnTo = ''; save();
      toast('Logged in as ' + ROLES[st().role]);
      App.go(back || '#/members');
    };
  }
};
App.actions['toggle-pw'] = el => { const i = $('#' + el.getAttribute('aria-controls')); i.type = i.type === 'password' ? 'text' : 'password'; el.textContent = i.type === 'password' ? 'Show' : 'Hide'; };

App.pages['/forgot'] = {
  access: 'public', title: 'Forgot password',
  render: () => `<h1>Forgot password</h1>
  <form id="forgot" class="card" novalidate style="max-width:460px">
    <p>Enter your email address and we will send you a link to choose a new password.</p>
    <label for="f-email">Email address</label><input id="f-email" type="email" autocomplete="email">
    <button class="btn" style="margin-top:14px">Send recovery email</button><div id="f-result" role="status"></div>
  </form>`,
  after() {
    $('#forgot').onsubmit = e => {
      e.preventDefault(); const i = $('#f-email'), v = i.value.trim();
      if (!setErr(i, !v ? 'Enter your email address.' : !validEmail(v) ? 'Enter a valid email address.' : '')) { i.focus(); return; }
      $('#f-result').innerHTML = '<p class="ok">If an account exists for this address, we have sent an email.</p><p class="hint">Demo: <a href="#/set-password">open the link from the email</a></p>';
    };
  }
};

App.pages['/set-password'] = {
  access: 'public', title: 'Choose a new password',
  render: () => `<h1>Choose a new password</h1>
  <form id="setpw" class="card" novalidate style="max-width:460px">
    <label for="p1">New password</label><input id="p1" type="password" autocomplete="new-password"><p class="hint">At least 12 characters, and not a very common password.</p>
    <label for="p2">New password again</label><input id="p2" type="password" autocomplete="new-password">
    <button class="btn" style="margin-top:14px">Save password</button>
  </form>`,
  after() {
    $('#setpw').onsubmit = e => {
      e.preventDefault(); const a = $('#p1'), b = $('#p2'); let ok = true;
      ok = setErr(a, passwordError(a.value)) && ok;
      ok = setErr(b, b.value !== a.value ? 'The passwords do not match.' : '') && ok;
      if (ok) { toast('Password saved. Please log in.'); location.hash = '/login'; }
    };
  }
};

/* Legal pages (docs/legal-pages.md) */
const legalUpdated = '<p class="hint">Placeholder text, last updated 15 November 2026.</p>';
App.pages['/privacy'] = {
  access: 'public', title: 'Privacy',
  render: () => `<h1>Privacy</h1>${legalUpdated}
  <h2>Who is responsible</h2><p>[Orchestra name], a foundation. Privacy questions: <a href="mailto:privacy@example.org">privacy@example.org</a>.</p>
  <h2>Which data we use</h2><ul><li><strong>Accounts:</strong> email address, role, a password that is only stored as a hash, and dates.</li><li><strong>Contact form:</strong> your email address, subject and message. They are emailed to the board and not stored on the website.</li><li><strong>Logs:</strong> technical logs and a trail of changes made by admins.</li></ul>
  <h2>Who receives data</h2><ul><li><strong>Google Cloud</strong> (hosting, database, files and logs, in the EU region europe-west4)</li><li><strong>Brevo</strong> (sending email, EU)</li><li><strong>GitHub</strong> (the code only, no personal data)</li></ul>
  <h2>How long we keep data</h2>
  <div class="scroll"><table><thead><tr><th>Data</th><th>Kept</th></tr></thead><tbody>
  <tr><td>Account (email address, role, password hash, dates)</td><td>Until the person is removed</td></tr><tr><td>Login session</td><td>At most 1 day</td></tr><tr><td>Password recovery link</td><td>1 hour</td></tr>
  <tr><td>Application logs and the admin audit trail</td><td>30 days</td></tr><tr><td>Database backups</td><td>30 days</td></tr><tr><td>Deleted member files and songs</td><td>7 days</td></tr><tr><td>Contact form messages</td><td>Not stored on the website</td></tr></tbody></table></div>
  <h2>Your rights</h2><p>You can ask to see your data, correct it, have it removed or object to its use. We answer within one month. You can also complain to the Dutch Data Protection Authority (Autoriteit Persoonsgegevens).</p>`
};
App.pages['/cookies'] = {
  access: 'public', title: 'Cookies',
  render: () => `<h1>Cookies</h1>${legalUpdated}
  <p>This website sets one cookie. It is strictly necessary, so you do not need to give consent. There are no tracking, advertising or analytics cookies, and nothing is loaded from other websites.</p>
  <div class="scroll"><table><thead><tr><th>Name</th><th>Purpose</th><th>Lifetime</th></tr></thead><tbody><tr><td><code>__Host-session</code></td><td>Keeps you logged in</td><td>At most 1 day</td></tr></tbody></table></div>`
};
App.pages['/terms'] = {
  access: 'public', title: 'Terms of use',
  render: () => `<h1>Terms of use</h1>${legalUpdated}
  <ul><li>The files on the member page (sheet music, recordings and lyrics) are for members of the orchestra, for rehearsing and performing with the orchestra only.</li>
  <li>Do not share, publish or copy the files for others, and do not upload them elsewhere.</li><li>Accounts are personal and must not be shared.</li>
  <li>Admins only upload files that the orchestra is allowed to use.</li><li>The orchestra may remove access or files at any time.</li></ul>`
};
