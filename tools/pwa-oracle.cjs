// Read-only differential oracle: requires an exact copy of main:index.html.
const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync(process.argv[2], 'utf8');
function extract(name) {
  const start = html.indexOf(`function ${name}(`);
  if (start < 0) throw new Error(`Missing original function ${name}`);
  let opening = html.indexOf('{', start), depth = 1, i = opening + 1;
  // These calculation functions contain balanced braces in comments/strings.
  for (; depth; i++) { if (html[i] === '{') depth++; if (html[i] === '}') depth--; }
  return html.slice(start, i);
}
const names = ['getAnnoCfg','getRoundedMonthRate','getPermessiOrePerMese','getSaldoInizDate','monthAccrues','getAnnualMat','getMonthMat','getYearUsed','getResiduoAnniPrecedenti','getYearTotals','getMonthUsed','getMonthSaldo','getSpecialPermUsed','getSpecialPermMonth','getBancaOreSaldo'];
const code = names.map(extract).join('\n');
const RealDate = Date;
class FixedDate extends RealDate { constructor(...args) { super(...(args.length ? args : ['2026-09-30T12:00:00Z'])); } }
const sandbox = { Date: FixedDate, simToday: null, getEffectiveToday: () => ({year:2026,month:9}) };
vm.createContext(sandbox); vm.runInContext(code,sandbox);
const user = { id:'a', calcMode:'1.2', dataInizioContratto:'2024-09-11', saldoIniziale:{data:'2025-06-16',ferie:3,permessi:-4,bancaOre:7.5}, anni:{2025:{ferieAnnue:26,permessiOreAnnui:100},2026:{ferieAnnue:26,permessiOreAnnui:100}}, entries:[
  {id:'1',tipo:'ferie',dateFrom:'2025-07-01',anno:2025,mese:7,qty:20,sim:false},
  {id:'2',tipo:'permesso',dateFrom:'2025-07-01',anno:2025,mese:7,qty:40,sim:false},
  {id:'3',tipo:'permesso',dateFrom:'2026-01-01',anno:2026,mese:1,qty:5,sim:false},
  {id:'4',tipo:'permesso_pagato',dateFrom:'2026-09-01',anno:2026,mese:9,qty:8,sim:false},
  {id:'5',tipo:'ferie',dateFrom:'2026-09-01',anno:2026,mese:9,qty:2,sim:true},
  {id:'6',tipo:'banca_accumulo',dateFrom:'2026-09-01',anno:2026,mese:9,qty:2.5,sim:false}
]};
console.log(JSON.stringify({previous:sandbox.getResiduoAnniPrecedenti(user,2026),annual:sandbox.getAnnualMat(user,2026),totals:sandbox.getYearTotals(user,2026,false),month:sandbox.getMonthSaldo(user,2026,9,true),bank:sandbox.getBancaOreSaldo(user,2026,9)},null,2));
