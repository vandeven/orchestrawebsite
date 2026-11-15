'use strict';
/* Start state of the demo. All names, texts and files are made up. */
const IMG = { conductor: svg('Conductor', '#7a1f3d', '#d98aa5'), band: svg('Band', '#264653', '#2a9d8f'), choir: svg('Choir', '#6a4c93', '#c9a0dc'), concert: svg('Concert', '#b5651d', '#f4a259') };
App.initial = () => ({
  v: 3, role: null, cookie: false, returnTo: '', seq: 100,
  texts: {
    about: { title: 'About page text', page: 'About (home page)', changed: '2026-11-02', html: `<h2>Who we are</h2><p>We are an orchestra that combines a <strong>band</strong> and a large <strong>choir</strong>. We play and sing everything from film music to classical favourites, and we perform several times a year.</p><h3>The band</h3><p>About 35 wind, brass and percussion players.</p><h3>The choir</h3><p>About 60 singers in four voices.</p><h2>Our conductor</h2><p><img class="s right" src="${IMG.conductor}" alt="Portrait of the conductor">The conductor has led the orchestra since 2015 and loves to bring amateurs and professionals together. (Placeholder text.)</p><h2>Rehearsals</h2><table><tr><th>Group</th><th>When</th><th>Where</th></tr><tr><td>Band</td><td>Tuesday 20:00 to 22:00</td><td>Community hall, Main street 5</td></tr><tr><td>Choir</td><td>Thursday 19:30 to 21:45</td><td>Community hall, Main street 5</td></tr></table>` },
    bam: { title: 'Become a member page text', page: 'Become a member', changed: '2026-10-21', html: `<h2>Sing with us</h2><p>New singers are always welcome. You do not need to read music. Come and join a rehearsal on a Thursday evening, free of charge.</p><p>Questions? Email us at <a href="mailto:info@example.org">info@example.org</a>.</p>` },
    audition: { title: 'Audition text', page: 'Become a member (audition section)', changed: '2026-11-08', html: `<p>Auditions for the choir take place on <strong>Thursday 4 and 11 December</strong> at 19:00 in the community hall. Please sign up with the contact form below.</p>` },
    agenda: { title: 'Agenda text', page: 'Agenda', changed: '2026-11-10', html: `<h2>Upcoming performances</h2><ul><li><strong>Saturday 13 December 2026, 20:00</strong>: Christmas concert, Great Church</li><li><strong>Sunday 22 March 2027, 15:00</strong>: Spring concert, Theatre Square</li><li><strong>Saturday 5 June 2027, 19:30</strong>: Open air evening, City park</li></ul><p><img class="m" src="${IMG.concert}" alt="Impression of last year's concert"></p>` }
  },
  songs: [
    { id: 1, title: 'Bohemian Rhapsody', notes: 'Arrangement for choir and band', active: true, files: [{ id: 11, type: 'sheet', name: 'Soprano' }, { id: 12, type: 'sheet', name: 'Alto' }, { id: 13, type: 'sheet', name: 'Trumpet 1' }, { id: 14, type: 'audio', name: 'Full recording', f: 440 }, { id: 15, type: 'audio', name: 'Alto practice', f: 523 }, { id: 16, type: 'lyrics', name: 'Lyrics' }] },
    { id: 2, title: 'Ode to Joy', notes: '', active: true, files: [{ id: 21, type: 'sheet', name: 'Full score' }, { id: 22, type: 'audio', name: 'Full recording', f: 392 }] },
    { id: 3, title: 'Hallelujah', notes: 'Christmas concert', active: true, files: [{ id: 31, type: 'sheet', name: 'Tenor' }, { id: 32, type: 'lyrics', name: 'Lyrics' }] },
    { id: 4, title: 'Eye of the Tiger', notes: 'Last season', active: false, files: [{ id: 41, type: 'sheet', name: 'Drums' }] }
  ],
  users: [
    { id: 1, email: 'master@example.org', role: 'master', added: '2026-01-05', last: '2026-11-14', mailed: null },
    { id: 2, email: 'anna@example.org', role: 'admin', added: '2026-01-06', last: '2026-11-12', mailed: '2026-01-06' },
    { id: 3, email: 'bram@example.org', role: 'admin', added: '2026-02-11', last: '2026-11-01', mailed: '2026-02-11' },
    { id: 4, email: 'chloe@example.org', role: 'member', added: '2026-03-02', last: '2026-11-13', mailed: '2026-03-02' },
    { id: 5, email: 'daan@example.org', role: 'member', added: '2026-03-02', last: '2026-10-09', mailed: '2026-03-02' },
    { id: 6, email: 'eva@example.org', role: 'member', added: '2026-11-10', last: null, mailed: '2026-11-10' }
  ],
  images: [
    { id: 1, src: IMG.conductor, alt: 'Portrait of the conductor', size: '184 KB', date: '2026-01-10' },
    { id: 2, src: IMG.concert, alt: "Impression of last year's concert", size: '240 KB', date: '2026-03-14' },
    { id: 3, src: IMG.band, alt: 'The band during a rehearsal', size: '210 KB', date: '2026-04-02' },
    { id: 4, src: IMG.choir, alt: 'The choir on stage', size: '198 KB', date: '2026-04-02' }
  ]
});
App.S = loadState();
