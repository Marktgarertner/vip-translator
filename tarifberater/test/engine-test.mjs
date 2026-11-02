// Prüft die Empfehlungslogik des Tarifberaters ohne Browser:
//   node tarifberater/test/engine-test.mjs
// Zieht Tarifdaten und Engine direkt aus index.html, damit Test und Seite
// nie auseinanderlaufen.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(here, '..', 'index.html'), 'utf8');
const data = JSON.parse(html.match(/<script id="tarif-data"[^>]*>([\s\S]*?)<\/script>/)[1]);
const i18nSrc = html.match(/<script id="tarif-i18n">([\s\S]*?)<\/script>/)[1];
const engineSrc = html.match(/<script id="tarif-engine">([\s\S]*?)<\/script>/)[1];
const ctx = {};
vm.createContext(ctx);
vm.runInContext(i18nSrc + '\nthis.TarifI18n = TarifI18n;', ctx);
vm.runInContext(engineSrc + '\nthis.TarifEngine = TarifEngine;', ctx);
const { empfehlen, eur } = ctx.TarifEngine;
const I18N = ctx.TarifI18n;

let failures = 0;
function check(name, cond, detail) {
  if (cond) console.log('  ok   ' + name);
  else { failures++; console.log('  FAIL ' + name + (detail ? ' - ' + detail : '')); }
}
function run(a) { return empfehlen(Object.assign({ extras: [], jahrIndex: 0 }, a), data); }
function top(a) { return run(a).empfehlungen[0]; }
function ids(a) { return run(a).empfehlungen.map(e => e.id); }

console.log('Tarifdaten: Stand ' + data.stand + ', Jahre ' + data.jahre.map(j => j.key).join('/'));

// --- Datenplausibilität --------------------------------------------------
console.log('\nDaten');
function walk(obj, path) {
  for (const [k, v] of Object.entries(obj)) {
    if (Array.isArray(v) && v.length === 2 && typeof v[0] === 'number') {
      check(path + k + ' hat zwei positive Preise', v[0] > 0 && v[1] > 0, JSON.stringify(v));
      check(path + k + ' steigt 2027 nicht um mehr als 10 %', v[1] <= v[0] * 1.10 + 0.005, v[0] + ' -> ' + v[1]);
    } else if (v && typeof v === 'object' && !Array.isArray(v)) walk(v, path + k + '.');
  }
}
walk({ potsdam: data.potsdam, berlin: data.berlin, zeit: data.zeit, fahrrad: data.fahrrad }, '');
check('4-Fahrten-Karte AB ist je Fahrt günstiger als der Einzelfahrausweis', data.potsdam.ab4.regel[0] / 4 < data.potsdam.ab.regel[0]);
check('Abo Potsdam AB ist günstiger als die Monatskarte', data.zeit.umweltAB.abo[0] < data.zeit.umweltAB.monat[0]);
check('Deutschlandticket ist günstiger als Umweltkarte Berlin ABC im Abo', data.zeit.dticket[0] < data.zeit.umweltBerlinABC.abo[0]);
check('Schülerticket-Eigenanteil liegt unter dem Gesamtpreis', data.zeit.schuelerticketPotsdam.eigenanteil[0] < data.zeit.schuelerticketPotsdam.gesamt[0]);

// --- Sofort-Fälle ---------------------------------------------------------
console.log('\nSofort-Fälle');
check('Kind unter 6: kein Ticket', run({ wer: 'u6' }).sofort && run({ wer: 'u6' }).sofort.name.startsWith('Kein Ticket'));
check('Schwerbehindertenausweis: Freifahrt', /Freifahrt/.test(run({ wer: 'sb' }).sofort.name));
check('Schwerbehindertenausweis nennt Wertmarke als Unterlage', run({ wer: 'sb' }).unterlagen.some(u => /Wertmarke/.test(u)));

// --- Einzelfahrt ------------------------------------------------------------
console.log('\nEinzelfahrt');
check('Erwachsen, einmal, AB -> Einzelfahrausweis AB 3,00', top({ wer: 'erwachsen', wie: 'einmal', wohin: 'ab' }).id === 'einzel-ab' && top({ wer: 'erwachsen', wie: 'einmal', wohin: 'ab' }).preis === 3.00);
check('Kind, einmal, AB -> Ermäßigungstarif 2,10', top({ wer: 'kind', wie: 'einmal', wohin: 'ab' }).preis === 2.10);
check('Einmal AB bietet Kurzstrecke als Alternative', ids({ wer: 'erwachsen', wie: 'einmal', wohin: 'ab' }).includes('kurz'));
check('Einmal Berlin -> Berlin ABC 5,00', top({ wer: 'erwachsen', wie: 'einmal', wohin: 'berlin' }).preis === 5.00);
check('Einmal Berlin nennt Anschlussfahrausweis-Tipp', run({ wer: 'erwachsen', wie: 'einmal', wohin: 'berlin' }).hinweise.some(h => /Anschlussfahrausweis/.test(h.text)));
check('Einmal weit -> Preis nach Strecke, Tageskarte als Alternative', top({ wer: 'erwachsen', wie: 'einmal', wohin: 'weit' }).preis === null && ids({ wer: 'erwachsen', wie: 'einmal', wohin: 'weit' }).includes('tag-vbb'));
check('Einmal mit Hund -> Hinweis auf Ermäßigungs-Fahrausweis', run({ wer: 'erwachsen', wie: 'einmal', wohin: 'ab', extras: ['hund'] }).hinweise.some(h => /Hund/.test(h.text) && /2,10/.test(h.text)));

// --- Tag -------------------------------------------------------------------
console.log('\nMehrere Fahrten an einem Tag');
check('Tag AB -> 24-Stunden-Karte 6,30', top({ wer: 'erwachsen', wie: 'tag', wohin: 'ab' }).id === 'tag-ab' && top({ wer: 'erwachsen', wie: 'tag', wohin: 'ab' }).preis === 6.30);
check('Tag AB mit Gruppe -> Kleingruppenkarte 15,50', top({ wer: 'erwachsen', wie: 'tag', wohin: 'ab', extras: ['gruppe'] }).id === 'gruppe-ab');
check('Tag weit -> Tageskarte Gesamtnetz mit 9-Uhr-Hinweis', top({ wer: 'erwachsen', wie: 'tag', wohin: 'weit' }).id === 'tag-vbb' && top({ wer: 'erwachsen', wie: 'tag', wohin: 'weit' }).beachten.some(t => /9 Uhr/.test(t)));
check('Tag AB mit Kindern -> Hinweis Kinder kostenlos', run({ wer: 'erwachsen', wie: 'tag', wohin: 'ab', extras: ['kinder'] }).hinweise.some(h => /drei Kinder/.test(h.text)));
const zwei = run({ wer: 'erwachsen', wie: 'tag', wohin: 'ab' }).empfehlungen.find(e => e.id === 'einzel-ab');
check('Tag AB: "2 Einzelfahrausweise" kostet 6,00', zwei && Math.abs(zwei.preis - 6.00) < 0.001);

// --- Gelegentlich -----------------------------------------------------------
console.log('\nEin paar Mal im Monat');
check('Gelegentlich AB -> 4-Fahrten-Karte', top({ wer: 'erwachsen', wie: 'gelegentlich', wohin: 'ab' }).id === 'vierer-ab');
check('Gelegentlich AB nennt Abo-Schwelle (19 Fahrten)', top({ wer: 'erwachsen', wie: 'gelegentlich', wohin: 'ab' }).beachten.some(t => /ab etwa 19 Fahrten/.test(t)));
check('Gelegentlich weit -> Deutschlandticket', top({ wer: 'erwachsen', wie: 'gelegentlich', wohin: 'weit' }).id === 'dticket');
check('Gelegentlich Berlin bietet Deutschlandticket an', ids({ wer: 'erwachsen', wie: 'gelegentlich', wohin: 'berlin' }).includes('dticket'));
check('Schüler gelegentlich AB -> Schülerticket zuerst', top({ wer: 'schueler', wie: 'gelegentlich', wohin: 'ab' }).id === 'schueler-potsdam');

// --- Regelmäßig -------------------------------------------------------------
console.log('\nRegelmäßig');
const w15 = run({ wer: 'erwachsen', wie: 'wenig', wohin: 'ab' });
check('1–2 Tage/Woche AB -> 4-Fahrten-Karten (36,00) vor Abo (43,50)', w15.empfehlungen[0].id === 'vierer-ab' && Math.abs(w15.empfehlungen[0].preis - 36.00) < 0.001 && w15.empfehlungen[1].id === 'umwelt-ab-abo');
const o40 = run({ wer: 'erwachsen', wie: 'oft', wohin: 'ab' });
check('Fast täglich AB -> Umweltkarte Abo 43,50 zuerst', o40.empfehlungen[0].id === 'umwelt-ab-abo' && o40.empfehlungen[0].preis === 43.50);
check('Fast täglich AB: Deutschlandticket ist dabei', ids({ wer: 'erwachsen', wie: 'oft', wohin: 'ab' }).includes('dticket'));
check('Fast täglich Berlin -> Deutschlandticket zuerst', top({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin' }).id === 'dticket');
check('Fast täglich Berlin: Kombi AB+Anschluss nennt 4 Berlin-Fahrten', run({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin' }).empfehlungen.find(e => e.id === 'kombi').warum[0].includes('bis etwa 4 Berlin-Fahrten'));
check('Fast täglich weit -> Deutschlandticket', top({ wer: 'erwachsen', wie: 'oft', wohin: 'weit' }).id === 'dticket');
check('Fast täglich ABC: Hinweis zum 4,00-€-Abstand Abo/D-Ticket', run({ wer: 'erwachsen', wie: 'oft', wohin: 'abc' }).hinweise.some(h => /4,00/.test(h.text)));
check('Schüler fast täglich AB -> Schülerticket 15,00', top({ wer: 'schueler', wie: 'oft', wohin: 'ab' }).id === 'schueler-potsdam' && top({ wer: 'schueler', wie: 'oft', wohin: 'ab' }).preis === 15.00);
check('Schüler fast täglich Berlin -> normales Deutschlandticket + Hinweis auf vollschulische Ausbildung', top({ wer: 'schueler', wie: 'oft', wohin: 'berlin' }).id === 'dticket' && run({ wer: 'schueler', wie: 'oft', wohin: 'berlin' }).hinweise.some(h => /vollschulische/.test(h.text)));
check('Schüler bekommen das Azubi-Deutschlandticket nicht als Angebot', !ids({ wer: 'schueler', wie: 'oft', wohin: 'ab' }).includes('dticket-azubi'));
check('Azubi fast täglich AB -> Abo Azubi 33,70 vor D-Ticket Azubi 37,80', top({ wer: 'azubi', wie: 'oft', wohin: 'ab' }).id === 'abo-azubi' && ids({ wer: 'azubi', wie: 'oft', wohin: 'ab' })[1] === 'dticket-azubi');
check('Azubi fast täglich Berlin -> D-Ticket Azubi', top({ wer: 'azubi', wie: 'oft', wohin: 'berlin' }).id === 'dticket-azubi');
check('Leistungsbescheid fast täglich AB -> Mobilitätsticket 28,50', top({ wer: 'leistung', wie: 'oft', wohin: 'ab' }).id === 'mobil' && top({ wer: 'leistung', wie: 'oft', wohin: 'ab' }).preis === 28.50);
check('Leistungsbescheid Berlin -> Warnhinweis, D-Ticket voller Preis', top({ wer: 'leistung', wie: 'oft', wohin: 'berlin' }).id === 'dticket' && run({ wer: 'leistung', wie: 'oft', wohin: 'berlin' }).hinweise.some(h => h.art === 'warn'));
check('Kind fast täglich AB -> Schülerticket', top({ wer: 'kind', wie: 'oft', wohin: 'ab' }).id === 'schueler-potsdam');
check('Kind fast täglich AB bekommt keine Umweltkarte', !ids({ wer: 'kind', wie: 'oft', wohin: 'ab' }).some(i => /umwelt/.test(i)));
check('Student fast täglich -> Semesterticket-Hinweis zuerst', /semesterticket/i.test(run({ wer: 'student', wie: 'oft', wohin: 'berlin' }).hinweise[0].text));
check('Fast täglich Berlin: Kombi steht an zweiter Stelle, nicht vorn', ids({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin' })[1] === 'kombi');
check('Senior fast täglich -> 65plus-Hinweis', run({ wer: 'senior', wie: 'oft', wohin: 'ab' }).hinweise.some(h => /65plus/.test(h.text)));
check('Fahrrad regelmäßig -> Monatskarte Fahrrad 14,60', run({ wer: 'erwachsen', wie: 'oft', wohin: 'ab', extras: ['fahrrad'] }).hinweise.some(h => /14,60/.test(h.text)));
check('Abo-Empfehlung nennt Unterlagen', run({ wer: 'erwachsen', wie: 'oft', wohin: 'ab' }).unterlagen.some(u => /Bankverbindung/.test(u)));
check('Preisliste ist sortiert', o40.empfehlungen.every((e, i, arr) => i === 0 || arr[i - 1].preis <= e.preis));

// --- Folgejahr --------------------------------------------------------------
console.log('\nFolgejahr 2027');
check('2026: Deutschlandticket zeigt Folgepreis 66,80', top({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin' }).preisNext === 66.80);
check('2027: Deutschlandticket 66,80 ohne Folgepreis', top({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin', jahrIndex: 1 }).preis === 66.80 && top({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin', jahrIndex: 1 }).preisNext === null);
check('2027: Einzelfahrausweis AB 3,10', top({ wer: 'erwachsen', wie: 'einmal', wohin: 'ab', jahrIndex: 1 }).preis === 3.10);

// --- Vollständigkeit: jede Kombination liefert ein Ergebnis ohne Fehler ------
console.log('\nAlle Kombinationen');
const wer = ['erwachsen', 'kind', 'u6', 'schueler', 'azubi', 'student', 'leistung', 'sb', 'senior'];
const wie = ['einmal', 'tag', 'gelegentlich', 'wenig', 'oft'];
const wohin = ['ab', 'abc', 'berlin', 'weit'];
const extrasSets = [[], ['kinder'], ['gruppe'], ['fahrrad'], ['hund'], ['kinder', 'gruppe', 'fahrrad', 'hund']];
let combos = 0, broken = 0;
for (const w of wer) for (const h of wie) for (const z of wohin) for (const x of extrasSets) for (const j of [0, 1]) {
  combos++;
  try {
    const r = run({ wer: w, wie: h, wohin: z, extras: x, jahrIndex: j });
    const ok = r.sofort || (r.empfehlungen.length > 0 && r.empfehlungen.every(e => e.name && (e.preis === null || e.preis >= 0)));
    if (!ok) { broken++; console.log('  FAIL leer: ' + JSON.stringify({ w, h, z, x, j })); }
  } catch (err) { broken++; console.log('  FAIL Fehler: ' + JSON.stringify({ w, h, z, x, j }) + ' ' + err.message); }
}
check(combos + ' Kombinationen ohne Fehler und ohne leeres Ergebnis', broken === 0, broken + ' defekt');

// --- Sprachen ---------------------------------------------------------------
console.log('\nSprachen');
const LANGS = I18N.langs.map(l => l.code);
check('Fünf Sprachen: ' + LANGS.join(', '), LANGS.join(',') === 'de,en,uk,ar,tr');
const ph = s => (s.match(/\{\w+\}/g) || []).sort().join(',');
let missing = [], badPh = [];
for (const [key, e] of Object.entries(I18N.text)) {
  for (const l of LANGS) {
    if (typeof e[l] !== 'string' || (e[l] === '' && !(key === 'names_note' && l === 'de'))) missing.push(key + '/' + l);
    else if (ph(e[l]) !== ph(e.de)) badPh.push(key + '/' + l + ' ' + ph(e[l]) + ' statt ' + ph(e.de));
  }
}
check(Object.keys(I18N.text).length + ' Texte in allen Sprachen vorhanden', missing.length === 0, missing.slice(0, 8).join(' '));
check('Platzhalter ({p}, {n} ...) in allen Sprachen gleich', badPh.length === 0, badPh.slice(0, 5).join(' | '));
// Jeder im Code benutzte Textschlüssel existiert.
const usedKeys = new Set();
for (const m of html.matchAll(/\bt\('([a-z0-9_]+)'\s*[,)]/g)) usedKeys.add(m[1]);
for (const m of html.matchAll(/data-t="([a-z0-9_]+)"/g)) usedKeys.add(m[1]);
for (const q of I18N.questions) {
  for (const s of ['_label', '_title', '_hint']) usedKeys.add('q_' + q.key + s);
  for (const o of q.opts) { usedKeys.add('o_' + q.key + '_' + o); usedKeys.add('o_' + q.key + '_' + o + '_sub'); }
}
const unknown = [...usedKeys].filter(k => !I18N.has(k));
check(usedKeys.size + ' benutzte Schlüssel sind alle definiert', unknown.length === 0, unknown.join(', '));
const unused = Object.keys(I18N.text).filter(k => !usedKeys.has(k) && !/^jahr_/.test(k));
check('Keine verwaisten Texte', unused.length === 0, unused.join(', '));
check('Fragenliste deckt die Hash-Werte ab', I18N.questions.map(q => q.key).join() === 'wer,wie,wohin,extras');
check('Arabisch läuft von rechts nach links', I18N.info('ar').dir === 'rtl' && I18N.info('de').dir === 'ltr');
check('Gerätesprache "tr-TR" wird erkannt, "fr" nicht', I18N.match('tr-TR') === 'tr' && I18N.match('fr') === null);
check('Deutsch bleibt bei "3,00 €"', I18N.eur(3, 'de') === '3,00 €');
check('Englisch formatiert "€3.00"', I18N.eur(3, 'en') === '€3.00', I18N.eur(3, 'en'));
check('Arabisch nutzt lateinische Ziffern', /3\.00/.test(I18N.eur(3, 'ar')), I18N.eur(3, 'ar'));

// Logik ist sprachunabhängig, Texte sind vollständig ersetzt.
let langCombos = 0, langBroken = [];
const strings = r => [
  ...(r.sofort ? [r.sofort.name, r.sofort.text, ...r.sofort.beachten] : []),
  ...r.empfehlungen.flatMap(e => [e.name, e.einheit, e.tag, e.kaufen, ...e.warum, ...e.beachten]),
  ...r.hinweise.map(h => h.text), ...r.unterlagen
].filter(x => x !== null && x !== undefined && x !== '');
for (const w of wer) for (const h of wie) for (const z of wohin) for (const x of extrasSets) for (const j of [0, 1]) {
  const base = run({ wer: w, wie: h, wohin: z, extras: x, jahrIndex: j });
  const baseIds = base.empfehlungen.map(e => e.id + ':' + e.preis).join();
  for (const l of LANGS.slice(1)) {
    langCombos++;
    const r = run({ wer: w, wie: h, wohin: z, extras: x, jahrIndex: j, lang: l });
    const ids2 = r.empfehlungen.map(e => e.id + ':' + e.preis).join();
    const bad = strings(r).filter(s => /\{\w+\}|undefined|NaN/.test(s) || /^[a-z]+_[a-z0-9_]+$/.test(s));
    if (ids2 !== baseIds || r.hinweise.length !== base.hinweise.length || r.unterlagen.length !== base.unterlagen.length || bad.length) {
      langBroken.push(l + ' ' + JSON.stringify({ w, h, z, x, j }) + (bad.length ? ' ' + bad[0] : ''));
    }
  }
}
check(langCombos + ' Kombinationen in en/uk/ar/tr: gleiche Empfehlung wie Deutsch, keine offenen Platzhalter', langBroken.length === 0, langBroken.slice(0, 3).join(' | '));
check('Englisch ist wirklich übersetzt', run({ wer: 'erwachsen', wie: 'oft', wohin: 'ab', lang: 'en' }).hinweise.length === 0
  && run({ wer: 'u6', lang: 'en' }).sofort.name === 'No ticket needed'
  && run({ wer: 'erwachsen', wie: 'tag', wohin: 'weit', lang: 'en' }).empfehlungen[0].beachten.some(t => /9 am/.test(t)));
check('Produktnamen bleiben deutsch', top({ wer: 'erwachsen', wie: 'oft', wohin: 'berlin', lang: 'ar' }).name === 'Deutschlandticket');

console.log('\n' + (failures ? failures + ' Prüfung(en) fehlgeschlagen' : 'Alle Prüfungen bestanden'));
process.exit(failures ? 1 : 0);
