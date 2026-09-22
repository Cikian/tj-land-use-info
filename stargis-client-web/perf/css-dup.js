/**
 * 统计开发态 app.js 里每个 less/css 模块被编译进了几份（判断重复注入是否已消除）。
 */
const http = require('http')
const fs = require('fs')

const URL = process.argv[2] || 'http://localhost:3002/app.js'
const CACHE = require('os').tmpdir() + '\\dsh-app-scan.js'

function download(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      const chunks = []
      res.on('data', c => chunks.push(c))
      res.on('end', () => resolve(Buffer.concat(chunks)))
    }).on('error', reject)
  })
}

;(async () => {
  let buf
  if (fs.existsSync(CACHE) && process.argv[3] !== '--fresh') {
    buf = fs.readFileSync(CACHE)
    console.log('(使用本地缓存 ' + CACHE + ')')
  } else {
    console.log('下载 app.js ...')
    buf = await download(URL)
    fs.writeFileSync(CACHE, buf)
  }
  const text = buf.toString('utf8')
  console.log('app.js 大小: ' + (buf.length / 1048576).toFixed(2) + ' MB\n')

  // webpack 的模块 id 行形如：/***/ "./src/assets/less/common_pop.less":
  const moduleRe = /\/\*\*\*\/\s*"([^"]+\.(?:less|css|vue))"/g
  const count = new Map()
  let m
  while ((m = moduleRe.exec(text)) !== null) {
    const key = m[1]
    count.set(key, (count.get(key) || 0) + 1)
  }

  const interesting = [...count.entries()]
    .filter(([k]) => /common_(btn|pop)\.less|\/common\.less|font(-override)?\.(css|less)|stargis-function\.css|JAreaLinkage/.test(k))
    .sort((a, b) => b[1] - a[1])

  console.log('=== 关键样式模块被编译的份数 ===')
  if (!interesting.length) console.log('  (未找到，模块 id 命名可能不同)')
  interesting.forEach(([k, v]) => console.log('  ' + String(v).padStart(3) + ' 份  ' + k))

  const dup = [...count.values()].filter(v => v > 1).length
  console.log('\n同名样式模块出现多次的总数: ' + dup + ' 个')
  const totalCss = [...count.entries()].filter(([k]) => /\.(less|css)$/.test(k)).length
  console.log('样式模块总数: ' + totalCss)
})()
