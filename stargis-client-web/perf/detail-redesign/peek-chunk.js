/* global fetch */
const base = process.argv[2] || 'http://127.0.0.1:3000'
const chunk = process.argv[3] || '/0.js'

fetch(base + chunk).then((r) => r.text()).then((text) => {
  const m = /\.screen-descriptions\[data-v-[0-9a-f]+\]\s*\{/.exec(text)
  if (!m) { console.log('anchor not found'); return }
  const slice = text.slice(m.index, m.index + 14000)
  const rules = slice.split('\\n}')
  rules.forEach((r) => {
    if (/white-space/.test(r)) {
      const head = r.slice(0, 220).replace(/\\n/g, ' | ')
      console.log('RULE: ' + head)
      console.log('')
    }
  })
}).catch((e) => { console.error(e); process.exit(1) })
