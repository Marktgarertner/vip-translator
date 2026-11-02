// Prüft die Rechenlogik des Jobticket-Rechners ohne Browser:
//   node jobticket/test/rechner-test.mjs
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(here, '..', 'index.html'), 'utf8');
const D = JSON.parse(html.match(/<script id="jobticket-data"[^>]*>([\s\S]*?)<\/script>/)[1]);
const ctx = {};
vm.createContext(ctx);
vm.runInContext(html.match(/<script id="jobticket-engine">([\s\S]*?)<\/script>/)[1] + '\nthis.R = JobticketRechner;', ctx);
const { rechnen, eur } = ctx.R;

let failures = 0;
function check(name, cond, detail) {
  if (cond) console.log('  ok   ' + name);
  else { failures++; console.log('  FAIL ' + name + (detail ? ' - ' + detail : '')); }
}
const calc = e => rechnen(Object.assign({ jahrIndex: 0, anzahl: 1, zuschuss: 'min', steuer: 'frei' }, e), D);

console.log('2026 (Deutschlandticket 63,00 €)');
const m = calc({});
check('Mindestzuschuss 25 % = 15,75 €', m.mindestzuschuss === 15.75);
check('Jobticket-Rabatt 5 % = 3,15 €', m.rabatt === 3.15);
check('Beschäftigte zahlen höchstens 44,10 € (wie Tarifberater dticketJob)', m.beschaeftigte === 44.10, m.beschaeftigte);
check('Vorteil 18,90 € im Monat, 226,80 € im Jahr', m.vorteilProPerson === 18.90 && m.vorteilJahr === 226.80);
const z30 = calc({ zuschuss: 30, anzahl: 20 });
check('30 € Zuschuss, 20 Personen: Eigenanteil 29,85 €, Kosten 600 € / 7.200 €', z30.beschaeftigte === 29.85 && z30.arbeitgeberMonat === 600 && z30.arbeitgeberJahr === 7200);
const voll = calc({ zuschuss: 'voll' });
check('Ticket komplett: 59,85 € Zuschuss, Beschäftigte zahlen 0', voll.zuschuss === 59.85 && voll.beschaeftigte === 0);
const zuviel = calc({ zuschuss: 80 });
check('Zuschuss über Ticketpreis wird gekappt', zuviel.gekappt && zuviel.zuschuss === 59.85 && zuviel.beschaeftigte === 0);
const zuwenig = calc({ zuschuss: 10 });
check('Unter Mindestzuschuss: kein Jobticket, kein Rabatt, 53,00 € Eigenanteil', !zuwenig.zulaessig && zuwenig.rabatt === 0 && zuwenig.beschaeftigte === 53);
check('Genau 15,75 € ist zulässig', calc({ zuschuss: 15.75 }).zulaessig);
const p = calc({ zuschuss: 'min', steuer: 'pauschal', anzahl: 10 });
check('Pauschalsteuer 25 % + 5,5 % Soli auf 15,75 € = 4,15 €', p.steuerProPerson === 4.15, p.steuerProPerson);
check('Pauschal: 10 Personen kosten 199,00 € im Monat', p.arbeitgeberMonat === 199.0, p.arbeitgeberMonat);
const park = calc({ zuschuss: 30, anzahl: 20, parkKosten: 80, parkAnzahl: 5 });
check('Parkplätze: 600 € − 400 € = 200 € netto im Monat, 2.400 € im Jahr', park.nettoMonat === 200 && park.nettoJahr === 2400);
check('Faustwert Gehalt: 18,90 € netto ≈ 31,50 € brutto ≈ 38,12 € Arbeitgeberkosten', m.gehaltBrutto === 31.5 && m.gehaltArbeitgeber === 38.12, m.gehaltBrutto + ' / ' + m.gehaltArbeitgeber);
check('Anzahl mindestens 1, ganze Zahl', calc({ anzahl: 0 }).anzahl === 1 && calc({ anzahl: 3.7 }).anzahl === 3);

console.log('\n2027 (Deutschlandticket 66,80 €)');
const n = calc({ jahrIndex: 1 });
check('Mindestzuschuss 16,70 €, Rabatt 3,34 €, Eigenanteil höchstens 46,76 €', n.mindestzuschuss === 16.70 && n.rabatt === 3.34 && n.beschaeftigte === 46.76, JSON.stringify([n.mindestzuschuss, n.rabatt, n.beschaeftigte]));

console.log('\nFormat');
check('eur: 7.200,00 €', eur(7200) === '7.200,00 €', eur(7200));
check('eur: negativ mit echtem Minus', eur(-400) === '−400,00 €');

console.log('\nRobustheit');
let bad = 0;
for (const j of [0, 1]) for (const a of [1, 2, 7, 50, 999]) for (const z of ['min', 'voll', 0, 5, 15.74, 15.75, 20, 30, 45.5, 100]) for (const s of ['frei', 'pauschal']) {
  const r = calc({ jahrIndex: j, anzahl: a, zuschuss: z, steuer: s });
  const sum = r2(r.rabatt + r.zuschuss + r.beschaeftigte);
  if (Object.values(r).some(v => typeof v === 'number' && !isFinite(v)) || r.beschaeftigte < 0 || sum !== r.preis) { bad++; console.log('  ' + JSON.stringify({ j, a, z, s, sum })); }
}
function r2(x) { return Math.round(x * 100) / 100; }
check('400 Kombinationen: Rabatt + Zuschuss + Eigenanteil = Ticketpreis, keine negativen Werte', bad === 0);

console.log('\n' + (failures ? failures + ' Prüfung(en) fehlgeschlagen' : 'Alle Prüfungen bestanden'));
process.exit(failures ? 1 : 0);
