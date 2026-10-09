/**
 * 在 dev server 的产物里找「当前源码是否已经编译进去」。
 * 判据：lazy chunk 里能不能搜到本轮新增的 CSS 令牌 --sd-label-max。
 *
 * 为什么需要：webpack-dev-server 从内存出 chunk，磁盘 cache 不能当证据；
 * 只截一张登录页不能说明改过的组件编进去了。
 *
 * 用法：node perf/detail-redesign/find-chunk.js [--needle "--sd-label-max"] [--max 60]
 */
/* global fetch */
const argv = process.argv.slice(2)
const getArg = (name, def) => {
  const i = argv.indexOf(name)
  return i > -1 && argv[i + 1] ? argv[i + 1] : def
}

const base = getArg('--base', 'http://127.0.0.1:3000')
const needle = getArg('--needle', '--sd-label-max')
const limit = parseInt(getArg('--max', '0'), 10)

async function main () {
  const listRes = await fetch(base + '/webpack-dev-server')
  const html = await listRes.text()
  const links = []
  const re = /href="([^"]+\.js)"/g
  let m
  while ((m = re.exec(html)) !== null) links.push(m[1])
  console.log('assets:', links.length, '  needle:', JSON.stringify(needle))

  const targets = limit > 0 ? links.slice(0, limit) : links
  let scanned = 0
  let bytes = 0
  const hits = []

  // 串行但只取前若干字节？不行 —— 需要全文搜，所以并发小一点
  const CONCURRENCY = 8
  let cursor = 0
  async function worker () {
    while (cursor < targets.length) {
      const i = cursor
      cursor += 1
      const url = base + targets[i]
      try {
        const res = await fetch(url)
        const text = await res.text()
        scanned += 1
        bytes += text.length
        if (text.includes(needle)) {
          hits.push({ url, size: text.length })
          console.log('HIT  ' + url + '  (' + text.length + ' bytes)')
        }
      } catch (e) {
        // 忽略单个失败
      }
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker))

  console.log('---')
  console.log('scanned ' + scanned + ' chunks, ' + (bytes / 1024 / 1024).toFixed(1) + ' MB')
  console.log(hits.length ? ('命中 ' + hits.length + ' 个 chunk：' + hits.map((h) => h.url).join(', '))
    : '没有 chunk 含该字符串')
  process.exit(hits.length ? 0 : 1)
}

main().catch((e) => { console.error(e); process.exit(1) })
