// Verificador de traducciones: claves que faltan y formatos (%d/%s) que no cuadran
const fs=require('fs');
const base=JSON.parse(fs.readFileSync('assets/i18n/es.json','utf8'));
const fmt=s=>(s.match(/%[a-z]/g)||[]).sort().join(',');
let bad=0;
for (const f of fs.readdirSync('assets/i18n')) {
  if (!f.endsWith('.json') || f==='es.json') continue;
  const t=JSON.parse(fs.readFileSync('assets/i18n/'+f,'utf8'));
  const missing=Object.keys(base).filter(k=>!(k in t));
  const extra=Object.keys(t).filter(k=>!(k in base));
  const mismatched=Object.keys(base).filter(k=>k in t && fmt(base[k])!==fmt(t[k]));
  console.log(f.padEnd(9), 'claves', String(Object.keys(t).length).padStart(4),
    '| faltan', String(missing.length).padStart(3),
    '| sobran', String(extra.length).padStart(3),
    '| formatos mal', String(mismatched.length).padStart(3));
  if (missing.length) console.log('   faltan:', missing.slice(0,8).join(', '), missing.length>8?'...':'');
  if (mismatched.length) console.log('   formatos:', mismatched.slice(0,8).join(', '));
  bad += missing.length + extra.length + mismatched.length;
}
process.exit(bad ? 1 : 0);
