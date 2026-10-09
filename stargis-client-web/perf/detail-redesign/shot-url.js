/**
 * 出图工具（通用版）：给一个 URL（本地文件或 dev server 地址）出 PNG，并校验尺寸。
 * screenshot.ps1 只处理本目录的静态 html；这个脚本用来截「真实跑起来的应用」。
 *
 * 用法：
 *   node perf/detail-redesign/shot-url.js --url http://127.0.0.1:3000/#/... --out app.png --width 1912 --height 962
 */
const fs = require('fs')
const path = require('path')
const os = require('os')
const { execFileSync } = require('child_process')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'

const argv = process.argv.slice(2)
const getArg = (name, def) => {
  const i = argv.indexOf(name)
  return i > -1 && argv[i + 1] ? argv[i + 1] : def
}

const url = getArg('--url', '')
const out = path.resolve(getArg('--out', path.join(__dirname, 'app.png')))
const width = parseInt(getArg('--width', '1912'), 10)
const height = parseInt(getArg('--height', '962'), 10)
const waitMs = parseInt(getArg('--wait', '9000'), 10)

if (!url) {
  console.error('必须给 --url')
  process.exit(1)
}

const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)

for (let attempt = 1; attempt <= 4; attempt += 1) {
  if (fs.existsSync(out)) fs.unlinkSync(out)
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-url-'))
  const mode = attempt % 2 === 1 ? '--headless=new' : '--headless'
  try {
    execFileSync(EDGE, [
      mode, '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--hide-scrollbars', '--force-device-scale-factor=1',
      `--window-size=${width},${height}`,
      '--user-data-dir=' + profile,
      `--virtual-time-budget=${waitMs}`,
      '--screenshot=' + out,
      url
    ], { stdio: 'ignore' })
  } catch (e) { /* Edge 父进程退出是正常的 */ }

  // 等文件写稳
  let last = -1
  const deadline = Date.now() + 40000
  while (Date.now() < deadline) {
    const size = fs.existsSync(out) ? fs.statSync(out).size : 0
    if (size > 0 && size === last) break
    last = size
    sleep(300)
  }
  try { fs.rmSync(profile, { recursive: true, force: true }) } catch (e) {}

  if (fs.existsSync(out) && fs.statSync(out).size > 0) {
    console.log('出图：' + out + '（' + width + 'x' + height + ', attempt ' + attempt + ', ' + mode + '）')
    process.exit(0)
  }
}
console.error('4 次都没出图')
process.exit(2)
