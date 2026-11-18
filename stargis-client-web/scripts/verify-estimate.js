/** 配套估算链路回归：使用页面原方法，替换地图与业务接口边界，无需写数据库。 */
/* eslint-disable no-new-func */
const assert = require('assert')
const fs = require('fs')
const path = require('path')
const compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '../src/views/screen/analysis')
const helperSource = fs.readFileSync(path.join(root, 'estimate.js'), 'utf8').replace(/export function /g, 'function ')
const { estimateLandCode, readEstimateLand } = new Function(helperSource + '\nreturn { estimateLandCode, readEstimateLand }')()
const component = compiler.parseComponent(fs.readFileSync(path.join(root, 'index.vue'), 'utf8'))
const source = component.script.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\r?$/gm, '').replace('export default', 'return')
let query = async () => { throw new Error('不应请求业务接口') }
const options = new Function('AnalysisToolPanel', 'ScreenResizeBar', 'ScreenSideRail', 'ScreenBottomDock',
  'queryLandAdminPage', 'estimateLandCode', 'readEstimateLand', source)(
  {}, {}, {}, {}, params => query(params), estimateLandCode, readEstimateLand)
const messages = []
const makeVm = () => Object.assign({
  _estimatePickSequence: 0,
  estimateResult: null,
  estimateLoading: false,
  estimateMessage: '',
  $screenToast: { info: text => messages.push(text) }
}, options.methods)
const deferred = () => {
  let settle
  const promise = new Promise(resolve => { settle = resolve })
  return { promise, resolve: settle }
}
const position = { x: 10, y: 20 }
let props = {}
global.window = {
  Cesium: { Cartesian2: function (x, y) { this.x = x; this.y = y } },
  viewer: {
    scene: {
      pick: () => ({ id: { properties: props } }),
      globe: { pick: () => position, ellipsoid: { cartesianToCartographic: () => ({ longitude: 2, latitude: 0.7 }) } }
    },
    camera: { getPickRay: () => ({}), pickEllipsoid: () => null },
    imageryLayers: { length: 0 }
  }
}

async function run () {
  let passed = 0
  const test = async (name, action) => { await action(); passed++; console.log('通过：' + name) }
  await test('编码、中文金额、金额单位及无效金额', () => {
    assert.strictEqual(estimateLandCode(' 津西(挂)2025—01【城更】 '), '津西（挂）2025-01号')
    assert.strictEqual(readEstimateLand({ CRJ: '1,200.5' }).crj, 1200.5)
    assert.strictEqual(readEstimateLand({ '出让金（万元）': '20000' }).crj, 2)
    assert.strictEqual(readEstimateLand({ '出让金（元）': '200000000' }).crj, 2)
    assert.strictEqual(readEstimateLand({ 出让金: '20,000万元' }).crj, 2)
    for (const value of ['', null, '未知', Infinity, -1, 0, true, '2%']) {
      assert.strictEqual(readEstimateLand({ crj: value }).crj, null)
    }
    assert.strictEqual(readEstimateLand({ 出让金缴纳比例: 50 }).crj, null)
  })
  await test('3D Tiles 优先读取要素本身的地块属性', () => {
    const feature = {
      primitive: {},
      getPropertyNames: () => ['CRZDBH', 'CRJ'],
      getProperty: name => ({ CRZDBH: '地块1', CRJ: 2 })[name]
    }
    const pickedInfo = makeVm().extractPickInfo(feature)
    assert.strictEqual(pickedInfo.props.CRJ, 2)
    assert.strictEqual(readEstimateLand(pickedInfo.props).crj, 2)
  })
  await test('三维要素有出让金时直接估算且不查询业务接口', async () => {
    props = { crzdbh: '地块1号', dkmc: '地块一', crj: 2 }
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult.low, '7,000')
    assert.strictEqual(vm.estimateResult.high, '13,000')
    assert.strictEqual(vm.estimateLoading, false)
  })
  await test('可见 MVT 拾取并跳过隐藏图层、非地块要素', async () => {
    props = {}
    const layers = [
      {
        show: true,
        _mvtResourceId: 'land',
        imageryProvider: {
          minimumLevel: 3,
          maximumLevel: 14,
          tilingScheme: { positionToTileXY: (c, level) => { assert.strictEqual(level, 14); return { x: 1, y: 2 } } },
          pickFeatures: async () => [{ data: { ptxmmc: '道路' } }, { data: { CRZDBH: '地块1号', CRJ: 2 } }]
        }
      },
      { show: false, _mvtResourceId: 'hidden', imageryProvider: { pickFeatures: () => { throw new Error('隐藏层不得拾取') } } }
    ]
    window.viewer.imageryLayers = { length: layers.length, get: index => layers[index] }
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult.crzdbh, '地块1号')
    assert.strictEqual(vm.estimateResult.low, '7,000')
  })
  await test('切片无出让金，回查业务表 crj 并拒绝模糊查询首条误匹配', async () => {
    props = { crzdbh: '津西(挂)2025-01', dkmc: '切片名称' }
    query = async params => {
      assert.strictEqual(params.crzdbh, '津西（挂）2025-01号')
      return {
        success: true,
        result: {
          total: 2,
          records: [
            { crzdbh: '津西（挂）2025-010号', crj: 99 },
            { crzdbh: '津西（挂）2025-01号', dkmc: '业务名称', crj: 2 }
          ]
        }
      }
    }
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult.dkmc, '业务名称')
    assert.strictEqual(vm.estimateResult.low, '7,000')
  })
  await test('切片金额为零也回查业务表', async () => {
    props.crj = 0
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult.crj, 2)
  })
  await test('业务查询无精确匹配时不给出另一地块的估算', async () => {
    query = async () => ({ success: true, result: { records: [{ crzdbh: '其它地块', crj: 99 }] } })
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult, null)
    assert(vm.estimateMessage.includes('未找到宗地'))
  })
  await test('业务表出让金为空时提示补录并清除旧结果', async () => {
    query = async () => ({ success: true, result: { records: [{ crzdbh: props.crzdbh, crj: null }] } })
    const vm = makeVm()
    vm.estimateResult = { low: '旧数据' }
    await vm.handleEstimatePick(position)
    assert.strictEqual(vm.estimateResult, null)
    assert(vm.estimateMessage.includes('补录'))
  })
  await test('业务请求失败时结束加载并提示业务服务', async () => {
    query = async () => { throw new Error('Network Error') }
    const vm = makeVm()
    await vm.handleEstimatePick(position)
    assert(vm.estimateMessage.includes('业务服务'))
    assert.strictEqual(vm.estimateLoading, false)
  })
  await test('连续点击时旧业务请求不能覆盖最新估算', async () => {
    const pending = deferred()
    query = () => pending.promise
    const vm = makeVm()
    const first = vm.handleEstimatePick(position)
    props = { crzdbh: '地块2号', crj: 4 }
    await vm.handleEstimatePick(position)
    pending.resolve({ success: true, result: { records: [{ crzdbh: '津西（挂）2025-01号', crj: 2 }] } })
    await first
    assert.strictEqual(vm.estimateResult.crzdbh, '地块2号')
    assert.strictEqual(vm.estimateResult.low, '14,000')
  })
  await test('退出工具使在途业务请求失效并销毁拾取句柄', async () => {
    props = { crzdbh: '地块1号' }
    const pending = deferred()
    query = () => pending.promise
    const vm = makeVm()
    let destroyed = false
    vm._estimateHandler = { destroy: () => { destroyed = true } }
    const first = vm.handleEstimatePick(position)
    vm.stopEstimatePick()
    pending.resolve({ success: true, result: { records: [{ crzdbh: '地块1号', crj: 2 }] } })
    await first
    assert.strictEqual(vm.estimateResult, null)
    assert.strictEqual(vm.estimateLoading, false)
    assert(destroyed)
  })
  await test('无编号无金额及点击空白时给出空态', async () => {
    props = { dkmc: '无编号地块' }
    const vm = makeVm()
    vm.pickEstimateMvt = async () => readEstimateLand(props)
    await vm.handleEstimatePick(position)
    assert(vm.estimateMessage.includes('宗地编号'))
    props = {}
    vm.pickEstimateMvt = async () => null
    await vm.handleEstimatePick(position)
    assert(vm.estimateMessage.includes('未拾取到'))
  })
  console.log(`配套估算回归：${passed} 项全部通过`)
}
run().catch(error => { console.error(error); process.exitCode = 1 })
