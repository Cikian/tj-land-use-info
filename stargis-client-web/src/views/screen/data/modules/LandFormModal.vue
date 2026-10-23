<template>
  <!--
    LandFormModal 经营性用地新增 / 编辑弹窗
    --------------------------------
    字段集与旧系统 addXjCommercialLand.html 一致（34 个业务字段），
    并在大屏端改成了弹窗 + 分组：
      · **为什么改成弹窗**：录入页要「边看地图边填」，整页表单会把地图挤没；
        弹窗保留上下文，且关闭后列表的检索条件与分页不丢。
      · **为什么分组**：34 个字段平铺一定读不下去。分组依据是「用户填表的顺序」：
        宗地标识 → 出让与面积 → 四至与配套 → 土地整理 → 录入信息。

    ★ 客户端校验**刻意镜像服务端**（不是自己发明一套）：
      服务端 LandAdminServiceImpl.validateRequired + createLand：
        crzdbh 必填且唯一（GET /checkCrzdbh）
        xmfl ∈ {市级项目, 区级项目}
        lxdh 命中 ^1[3456789]\d{9}$
      前端做一遍只为少一次往返，服务端仍会再校验一次 —— 所以这里的文案
      与服务端保持同口径，避免「前端说没问题、后端却报错」时用户不知道信谁。

    ★ 空串必须转 null：后端这些列是 BigDecimal / Date，
      传 '' 会在反序列化阶段直接失败（不是「存空值」那么温和）。

    公开方法（父组件通过 $refs 调用）：
      showAdd()        打开新增
      showEdit(record) 打开编辑（按 id 拉详情，回显最新值）
    事件：
      ok  保存成功后抛出，父组件据此刷新列表
  -->
  <!--
    弹窗形态（2026-10 用户实测后调整）：
      · 宽 960 + 表单 2 列：3 列时每列太窄，缩到 960 后 2 列才是正常比例
        （标签 150 + 控件约 330）。
      · 高度取「原来的一半」：改造前 body 上限 = calc(100vh - 200px)，
        头 46 + 底栏 54 ≈ 100，所以原来的总高 ≈ 100vh - 100；
        这里取 calc(50vh - 150px)，实测总高正好是原来的一半。
        正文因此会滚动 —— 这是有意的，弹窗矮下去才看得到下面的地图。
      · draggable + :dimmed="false"：背景不压暗、弹窗可拖到一边，
        用户就能一边填表一边直接在地图上操作（这也是「选取地块」不再需要
        全屏隐藏页面的原因，见 ScreenPickStage）。
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="960"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(50vh - 150px)'"
    draggable
    :dimmed="false"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="land-form">
      <p v-if="errors.summary" class="land-form__error" role="alert">
        <screen-icon name="alert-triangle" :size="14" />
        {{ errors.summary }}
      </p>

      <!-- ================= 1. 宗地标识 ================= -->
      <section class="land-form__section">
        <h4 class="land-form__section-title">
          <screen-icon name="database" :size="14" />
          宗地标识
        </h4>

        <div class="land-form__grid">
          <screen-field
            label="出让宗地编号"
            required
            :error="errors.crzdbh"
            html-for="lf-crzdbh"
            label-width="150px"
          >
            <screen-input
              id="lf-crzdbh"
              v-model="model.crzdbh"
              :maxlength="100"
              :invalid="!!errors.crzdbh"
              placeholder="例如：津西青(挂)2024-01号"
              @blur="handleCheckCrzdbh"
            />
          </screen-field>

          <screen-field label="地块名称" required :error="errors.dkmc" html-for="lf-dkmc" label-width="150px">
            <screen-input
              id="lf-dkmc"
              v-model="model.dkmc"
              :maxlength="255"
              :invalid="!!errors.dkmc"
              placeholder="例如：侯台片区地块一"
            />
          </screen-field>

          <!--
            「添加地块」：进入地图绘制模式（2026-10 由「选取地块」改名改义：
            不再是去地图上点选已有地块，而是直接在地球上绘制一个多边形，
            「完成绘制」后把 GeoJSON 存进 dkGeojson 字段）。
            ★ 2026-10 起**不再隐藏任何页面**（原先是全屏隐藏、只留地图）：
              弹窗本身已经可以拖到一边、且背景不压暗，地图随时可操作，
              再藏页面反而把「填到一半的表单」也一起藏了，用户还得先退出来才能核对。
            ★ 为什么放在「地块名称」旁边而不是单独一块：
              用户的心智是「先把这块地指出来」，地块名称 / 行政区划 / 面积都跟着这块地走，
              所以入口贴着地块名称，比放在表单底部更容易被发现。
            ★ 按钮上的 disabled 判据只有一条：地图没挂载（?map=0 预览）时进绘制模式
              只会看到一片空白，那种情况下不如直接不给点。
          -->
          <div class="land-form__map-pick">
            <button
              id="lf-crzdtx"
              type="button"
              class="land-form__map-pick-btn"
              :disabled="!mapEnabled"
              :aria-label="mapEnabled ? '添加地块，在地图上绘制多边形；可先把本窗口拖到一边' : '地图未加载，无法添加地块'"
              @click="handleOpenMapPick"
            >
              <screen-icon name="grid" :size="16" />
              <span>添加地块</span>
            </button>
            <!-- 已绘制的状态：有图形时显示顶点数并允许清除，没有则提示尚未绘制 -->
            <span v-if="drawnVertexCount" class="land-form__map-pick-drawn">
              已绘制（{{ drawnVertexCount }} 个顶点）
              <button
                type="button"
                class="land-form__map-pick-clear"
                aria-label="清除地块图形"
                @click="handleClearGeojson"
              >清除</button>
            </span>
            <span class="land-form__map-pick-tip">
              {{ mapEnabled ? (drawnVertexCount ? '' : '尚未绘制；点击后在地图上左键加点，双击或右键结束') : '地图未加载（?map=0 预览模式），暂不能添加地块' }}
            </span>
          </div>

          <screen-field label="行政区划" :error="errors.xzqh" label-width="150px">
            <screen-select
              v-model="model.xzqh"
              :options="xzqhOptions"
              :invalid="!!errors.xzqh"
              placeholder="请选择行政区划"
              aria-label="行政区划"
            />
          </screen-field>

          <screen-field label="项目分类" required :error="errors.xmfl" label-width="150px">
            <screen-select
              v-model="model.xmfl"
              :options="projectTypeOptions"
              :invalid="!!errors.xmfl"
              placeholder="请选择项目分类"
              aria-label="项目分类"
            />
          </screen-field>

          <screen-field label="规划用地性质" html-for="lf-ghydxz" label-width="150px">
            <screen-input
              id="lf-ghydxz"
              v-model="model.ghydxz"
              :maxlength="100"
              placeholder="例如：商业用地"
            />
          </screen-field>

          <screen-field label="录入单位简称" html-for="lf-xzqh2" label-width="150px">
            <screen-input
              id="lf-xzqh2"
              v-model="model.xzqh2"
              :maxlength="100"
              placeholder="如：西青分局"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 2. 出让与面积 ================= -->
      <section class="land-form__section">
        <h4 class="land-form__section-title">
          <screen-icon name="trending-up" :size="14" />
          出让与面积
        </h4>

        <div class="land-form__grid">
          <screen-field label="出让金（亿元）" :error="errors.crj" html-for="lf-crj" label-width="150px">
            <screen-input
              id="lf-crj"
              v-model="model.crj"
              type="number"
              :step="0.0001"
              :invalid="!!errors.crj"
              placeholder="数值，可留空"
            />
          </screen-field>

          <screen-field label="出让时间" label-width="150px">
            <screen-date-input v-model="model.crsj" placeholder="请选择出让时间" />
          </screen-field>

          <screen-field label="可建设用地面积" :error="errors.kjsydmj" html-for="lf-kjsydmj" label-width="150px">
            <screen-input
              id="lf-kjsydmj"
              v-model="model.kjsydmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.kjsydmj"
              placeholder="单位（㎡）"
            />
          </screen-field>

          <screen-field label="总用地面积" :error="errors.zydmj" html-for="lf-zydmj" label-width="150px">
            <screen-input
              id="lf-zydmj"
              v-model="model.zydmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.zydmj"
              placeholder="单位（㎡）"
            />
          </screen-field>

          <screen-field label="建设面积" :error="errors.jsmj" html-for="lf-jsmj" label-width="150px">
            <screen-input
              id="lf-jsmj"
              v-model="model.jsmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.jsmj"
              placeholder="单位（㎡）"
            />
          </screen-field>

          <screen-field label="纳入成本的配套费" :error="errors.nrcbdptf" html-for="lf-nrcbdptf" label-width="150px">
            <screen-input
              id="lf-nrcbdptf"
              v-model="model.nrcbdptf"
              type="number"
              :step="0.01"
              :invalid="!!errors.nrcbdptf"
              placeholder="单位（万元）"
            />
          </screen-field>

          <screen-field label="受让人" html-for="lf-srr" label-width="150px">
            <screen-input id="lf-srr" v-model="model.srr" :maxlength="100" placeholder="受让人名称" />
          </screen-field>

          <screen-field label="合同约定交付时间" label-width="150px">
            <screen-date-input v-model="model.htydjfsj" placeholder="请选择日期" />
          </screen-field>

          <screen-field label="楼盘名称" html-for="lf-lpmc" label-width="150px">
            <screen-input id="lf-lpmc" v-model="model.lpmc" :maxlength="100" placeholder="楼盘名称" />
          </screen-field>

          <screen-field label="楼盘交付时间" label-width="150px">
            <screen-date-input v-model="model.lpjfsj" placeholder="实际或计划交付时间" />
          </screen-field>

          <screen-field label="完成度" :error="errors.wcd" html-for="lf-wcd" label-width="150px">
            <screen-input
              id="lf-wcd"
              v-model="model.wcd"
              type="number"
              :step="0.01"
              :invalid="!!errors.wcd"
              placeholder="数值，可留空"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 3. 四至与配套 ================= -->
      <section class="land-form__section">
        <h4 class="land-form__section-title">
          <screen-icon name="layers" :size="14" />
          四至与配套
        </h4>

        <div class="land-form__grid">
          <screen-field label="东至" html-for="lf-dz" label-width="150px">
            <screen-input id="lf-dz" v-model="model.dz" :maxlength="100" placeholder="东至" />
          </screen-field>
          <screen-field label="西至" html-for="lf-xz" label-width="150px">
            <screen-input id="lf-xz" v-model="model.xz" :maxlength="100" placeholder="西至" />
          </screen-field>
          <screen-field label="南至" html-for="lf-nz" label-width="150px">
            <screen-input id="lf-nz" v-model="model.nz" :maxlength="100" placeholder="南至" />
          </screen-field>
          <screen-field label="北至" html-for="lf-bz" label-width="150px">
            <screen-input id="lf-bz" v-model="model.bz" :maxlength="100" placeholder="北至" />
          </screen-field>

          <screen-field label="配套是否齐全" label-width="150px">
            <screen-select
              v-model="model.ptsfqq"
              :options="yesNoOptions"
              placeholder="请选择"
              aria-label="配套是否齐全"
            />
          </screen-field>
          <screen-field label="配套建设内容" html-for="lf-ptjsnr" label-width="150px">
            <screen-input
              id="lf-ptjsnr"
              v-model="model.ptjsnr"
              type="textarea"
              :rows="2"
              :maxlength="2000"
              placeholder="该宗地需要落实的配套建设内容"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 4. 土地整理与资料 ================= -->
      <section class="land-form__section">
        <h4 class="land-form__section-title">
          <screen-icon name="file-text" :size="14" />
          土地整理与资料
        </h4>

        <div class="land-form__grid">
          <screen-field label="土地整理单位" html-for="lf-tdzldw" label-width="150px">
            <screen-input id="lf-tdzldw" v-model="model.tdzldw" :maxlength="100" placeholder="土地整理单位" />
          </screen-field>
          <screen-field label="整理计划下达文件号" html-for="lf-tdzljhxdwjh" label-width="150px">
            <screen-input
              id="lf-tdzljhxdwjh"
              v-model="model.tdzljhxdwjh"
              :maxlength="2000"
              placeholder="文件号"
            />
          </screen-field>
          <screen-field label="土地整理计划" label-width="150px">
            <screen-select v-model="model.tdzljh" :options="yesNoOptions" placeholder="请选择" aria-label="土地整理计划" />
          </screen-field>
          <screen-field label="配套情况函" label-width="150px">
            <screen-select v-model="model.ptqkh" :options="yesNoOptions" placeholder="请选择" aria-label="配套情况函" />
          </screen-field>
          <screen-field label="配套筹备函" label-width="150px">
            <screen-select v-model="model.ptcbh" :options="yesNoOptions" placeholder="请选择" aria-label="配套筹备函" />
          </screen-field>
          <screen-field label="出让宗地图形数据(shp)" label-width="150px">
            <screen-select
              v-model="model.crzdtxsj"
              :options="yesNoOptions"
              placeholder="请选择"
              aria-label="出让宗地图形数据"
            />
          </screen-field>
          <screen-field label="资料缺失内容及说明" html-for="lf-zlqsnrsm" label-width="150px">
            <screen-input
              id="lf-zlqsnrsm"
              v-model="model.zlqsnrsm"
              type="textarea"
              :rows="2"
              :maxlength="2500"
              placeholder="缺哪些扫描件、缺在哪个环节"
            />
          </screen-field>
          <screen-field label="备注" html-for="lf-beizhu" label-width="150px">
            <screen-input
              id="lf-beizhu"
              v-model="model.beizhu"
              type="textarea"
              :rows="2"
              :maxlength="2000"
              placeholder="补充说明"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 5. 录入信息 ================= -->
      <section class="land-form__section">
        <h4 class="land-form__section-title">
          <screen-icon name="user" :size="14" />
          录入信息
        </h4>

        <div class="land-form__grid">
          <screen-field label="录入单位" html-for="lf-lrdw" label-width="150px">
            <screen-input id="lf-lrdw" v-model="model.lrdw" :maxlength="100" placeholder="录入单位" />
          </screen-field>
          <screen-field label="录入人" html-for="lf-lrr" label-width="150px">
            <screen-input id="lf-lrr" v-model="model.lrr" :maxlength="100" placeholder="姓名" />
          </screen-field>
          <screen-field label="联系电话" :error="errors.lxdh" html-for="lf-lxdh" label-width="150px">
            <screen-input
              id="lf-lxdh"
              v-model="model.lxdh"
              :maxlength="100"
              :invalid="!!errors.lxdh"
              placeholder="11 位手机号"
            />
          </screen-field>
        </div>
      </section>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { uid } from '@/components/screen/utils'
// 「添加地块」绘制会话：表单弹窗与全屏覆盖层都挂在 body 上（不在同一棵组件树里），
// 只能通过这份共享状态对话，详见 views/screen/pickSession.js 的说明
import { startPick, endPick, isPickIssuer } from '@/views/screen/pickSession'
// 画完留在地图上的常驻预览面（生命周期跟表单会话：提交/取消/重画/清除时清掉）
import { show as showDkPreview, clear as clearDkPreview } from '@/views/screen/previewPolygon'
import { countVertices } from '@/views/screen/geoPolygons'
import {
  queryLandAdminById,
  addLandAdmin,
  editLandAdmin,
  checkCrzdbh,
  queryLandDictItems
} from '@/api/land/landAdmin'
import {
  XZQH_LIST,
  PROJECT_TYPES,
  YES_NO,
  dictDefinitions,
  today
} from '../constants'

/** 需要转成数字提交的字段（后端是 BigDecimal，空串会反序列化失败） */
const NUMBER_FIELDS = ['crj', 'kjsydmj', 'zydmj', 'jsmj', 'nrcbdptf', 'wcd']

/** 需要转成 null 的日期字段 */
const DATE_FIELDS = ['crsj', 'htydjfsj', 'lpjfsj']

/** 后端强校验的手机号规则（与 LandAdminServiceImpl.validateRequired 逐字一致） */
const PHONE_PATTERN = /^1[3456789]\d{9}$/

function emptyModel () {
  return {
    id: '',
    crzdbh: '',
    dkmc: '',
    xzqh: '',
    xmfl: '市级项目',
    ghydxz: '',
    crj: '',
    crsj: '',
    kjsydmj: '',
    zydmj: '',
    jsmj: '',
    nrcbdptf: '',
    wcd: '',
    ptsfqq: '否',
    ptjsnr: '',
    srr: '',
    htydjfsj: '',
    lpmc: '',
    lpjfsj: '',
    tdzldw: '',
    tdzljhxdwjh: '',
    tdzljh: '否',
    dz: '',
    xz: '',
    nz: '',
    bz: '',
    ptqkh: '否',
    ptcbh: '否',
    crzdtxsj: '否',
    // 「添加地块」画出的多边形（裸 Polygon GeoJSON 字符串；与后端 dk_geojson 列对应）。
    // 空串 = 没画过；编辑回显时 loadDetail 会按 key 自动带回来
    dkGeojson: '',
    xzqh2: '',
    zlqsnrsm: '',
    lrdw: '',
    lrr: '',
    lxdh: '',
    beizhu: ''
  }
}

export default {
  name: 'LandFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenIcon
  },
  data () {
    return {
      visible: false,
      title: '新增经营性用地',
      saving: false,
      loading: false,
      /** 是否已经点过一次保存：只有点过之后才把校验错误显示出来，避免一打开就满屏飘红 */
      submitted: false,
      model: emptyModel(),
      errors: {},
      /**
       * 本次「添加地块」绘制会话的令牌（没进过绘制模式时为空串）。
       * ★ 必须保存下来：结束会话时要 verbatim 交回去做发起者校验，
       *   否则「两个表单都开着时，后一个的取消会把前一个的绘制模式关掉」。
       */
      pickToken: '',
      /** 地图是否可用（?map=0 预览时为 false，此时「添加地块」不给点） */
      mapEnabled: true,
      /** 本组件实例的唯一 id：只用于把「谁开的选取会话」写进会话状态，便于排查 */
      uid: uid('land-form'),
      // 先给兜底，created 里再用字典覆盖（字典接口挂了页面也能填）
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item })),
      yesNoOptions: YES_NO.map((item) => ({ value: item, label: item }))
    }
  },
  computed: {
    /**
     * 已绘制地块图形的顶点数（未绘制时为 0，状态行据此显隐）。
     * 走 geoPolygons.countVertices：同时认 Polygon（绘制产物）与 MultiPolygon
     * （GeoJSON 导入合并产物），跨全部块统计 —— 不然导入过多块图形的宗地
     * 编辑时会被误显示成「尚未绘制」。解析失败（脏数据）按未绘制处理。
     */
    drawnVertexCount () {
      return countVertices((this.model && this.model.dkGeojson) || '')
    }
  },
  watch: {
    /**
     * 弹窗任何途径关掉（底部「取消」、右上角 X、Esc、保存成功）都要收尾：
     * 清掉常驻预览面与可能残留的绘制会话。★ 右上角 X / Esc 走的是 ScreenModal
     * 的 update:visible，**不会**经过 handleCancel，所以收尾必须挂在这里兜底
     * （closePick / clearDkPreview 都幂等，重复调用无副作用）。
     */
    visible (v) {
      if (!v) {
        clearDkPreview()
        this.closePick()
      }
    }
  },
  created () {
    /*
     * 地图是否加载：判据只有一个 —— 地址里有没有 ?map=0。
     * 这是全站已有的预览开关（views/screen/index.vue 的 mapEnabled 用的是同一个判据），
     * 不在这里探测 window.viewer：Cesium 是异步初始化的，
     * 「按钮先可点、点下去才发现地图没起来」比「一开始就不给点」体验差得多。
     */
    this.mapEnabled = !(this.$route && this.$route.query && this.$route.query.map === '0')
    queryLandDictItems(dictDefinitions()).then((dicts) => {
      if (dicts.xzqh) this.xzqhOptions = dicts.xzqh
      if (dicts.projectType) this.projectTypeOptions = dicts.projectType
    })
  },
  mounted () {
    /*
     * 订阅「选取地块」会话的结束事件。
     *
     * ★ 为什么走 $bus 而不是 props / $emit：
     *   全屏覆盖层由 views/screen/index.vue 渲染，它和本弹窗是 body 上的两棵
     *   平行节点，没有父子关系。$bus 是本工程既有的跨组件通道（utils/vueBus.js）。
     *   ★ payload 里带 token，本组件必须先校验「是不是自己发起的那次会话」——
     *     同页可能同时开着新增宗地与新增配套两个表单，不做校验就会出现
     *     「另一个表单的选取结果被本表单吃掉了」。
     */
    this.$bus.$on('landMapPickFinished', this.handlePickEvent)
  },
  beforeDestroy () {
    this.$bus.$off('landMapPickFinished', this.handlePickEvent)
  },
  methods: {
    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '新增经营性用地'
      this.model = emptyModel()
      this.model.lrdw = this.currentDeptName()
      this.model.lrr = this.currentRealname()
      this.errors = {}
      this.submitted = false
      this.loading = false
      // 兜底清一次预览面（正常路径提交/取消时已清，防的是异常中断留下的残留）
      clearDkPreview()
      this.closePick()
      this.visible = true
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少宗地主键，无法编辑')
        return
      }
      this.title = '编辑经营性用地'
      this.model = emptyModel()
      this.errors = {}
      this.submitted = false
      // 兜底清一次预览面（正常路径提交/取消时已清，防的是异常中断留下的残留）
      clearDkPreview()
      this.closePick()
      this.visible = true
      this.loading = true
      this.loadDetail(record.id)
    },

    /**
     * 拉详情回显。
     * ★ 为什么编辑时也要重新拉一次而不是直接用列表行：
     *   列表返回的是 `t_land` 精简投影的实体，但用户可能刚在别处改过；
     *   且详情接口是「以库为准」的唯一入口，用它回显能避免「编辑把字段写回旧值」。
     */
    loadDetail (id) {
      queryLandAdminById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '宗地详情加载失败')
            this.visible = false
            return
          }
          const data = res.result || {}
          const model = emptyModel()
          Object.keys(model).forEach((key) => {
            if (data[key] !== null && data[key] !== undefined) {
              model[key] = data[key]
            }
          })
          model.id = data.id || ''
          this.model = model
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    /**
     * 打开「添加地块」地图绘制模式。
     * ===============================================================
     * 打开后由 views/screen/index.vue 渲染的 ScreenPickStage 接手：
     * 纯 GIS 操作（无顶部按钮）——左键加点，双击或右键结束即回传，Esc 取消；
     * 绘制引擎是 views/screen/drawPolygon.js。
     * ★ 2026-10 起**不再隐藏任何页面**（原来会把整块画布连同本弹窗一起藏掉）：
     *   本弹窗现在可拖动、背景也不压暗，地图本来就随时可操作；
     *   再藏页面只会把「填到一半的表单」也藏了，用户反而没法一边画一边核对。
     *
     * ★ 本表单**自己不渲染任何地图**，也不负责关闭绘制模式：
     *   地图是 ScreenStage 里那个常驻的 Cesium 实例（本弹窗挂在 body 上，
     *   拿不到它，也不该再创建第二个 Viewer —— 那会白烧一份 GPU 与内存）。
     *   本方法只做两件事：① 记录会话令牌；② 把入口状态发出去。
     *   关闭由绘制模式发起的取消 / 完成事件驱动（见 handlePickFinished）。
     *
     * @returns {string} 本次会话令牌；地图不可用时不开启会话，返回空串
     */
    handleOpenMapPick () {
      if (!this.mapEnabled) {
        toast.warning('地图尚未加载，无法添加地块')
        return ''
      }
      // 已经画过（或编辑回显有图形）时再次点「添加地块」：旧面从地图上消失，
      // 开始一次全新的绘制（model.dkGeojson 仍保留，画完新面才会覆盖它）
      clearDkPreview()
      // 自己上次的令牌如果已经不是当前会话（被 ended / 被别人接管），先清掉，
      // 否则 closePick 会拿一个陈旧令牌去 endPick，语义上会显得「莫名关掉了别的会话」
      if (this.pickToken && !isPickIssuer(this.pickToken)) this.pickToken = ''
      if (this.pickToken) return this.pickToken
      // ★ startPick 在「已有别人开的会话」时会返回**那个**会话的令牌。
      //   这里如实记下来（而不是拒绝），保证本次操作总能被自己收到结束事件；
      //   代价是别人那次会话的发起者会在结束时被顺带清掉令牌 —— 可接受，
      //   因为同一时刻只可能有一张全屏地图，本来就不支持并行绘制。
      this.pickToken = startPick({
        source: 'land',
        title: '正在添加经营性用地地块：左键加点，双击或右键结束，Esc 取消',
        issuer: `LandFormModal#${this.uid}`
      })
      return this.pickToken
    },

    /**
     * 覆盖层关闭后回到这里（取消绘制 / 完成绘制都会走）。
     * @param {{geojson: string, vertexCount: number}|null} geometry
     *   画好的地块图形（由 drawPolygon.js 的 getGeoJSON() 定义）；null = 没画出来。
     *
     * ★ 有图形 → 写入 model.dkGeojson（表单状态行随之显示顶点数）；
     *   没图形 → **保留原值**（可能是编辑态已有的图形，也可能是从未画过），
     *   静默清空会让用户以为「画完了但没生效」，实际是「根本没画」。
     * ★ 再次点「完成绘制」且这次画了新图形 → 直接覆盖旧图形（重画的语义）。
     */
    handlePickFinished (geometry) {
      this.pickToken = ''
      if (geometry && geometry.geojson) {
        this.model.dkGeojson = geometry.geojson
        // 预览面已由 ScreenPickStage 转交常驻层接管（画完的面留在地图上，
        // 提交 / 取消 / 重画 / 清除时才消失），这里不用再动地图
        toast.success(`地块图形已添加（${geometry.vertexCount} 个顶点），保存时随表单一并提交`)
      } else if (this.model.dkGeojson) {
        // 取消绘制（Esc）：表单里已有的图形不受影响，把它的预览面显示回来
        showDkPreview(this.model.dkGeojson)
      }
    },

    /** 清除已绘制的地块图形（提交时该字段按空串 → null 的惯例上送），地图上的预览面一并清掉 */
    handleClearGeojson () {
      this.model.dkGeojson = ''
      clearDkPreview()
    },

    /**
     * 覆盖层抛回的结束事件（$bus: landMapPickFinished）。
     * @param {{ token: string, geometry: * }} payload
     */
    handlePickEvent (payload) {
      const data = payload || {}
      // 不是自己发起的那次会话 → 与自己无关，直接忽略
      if (!this.pickToken || data.token !== this.pickToken) return
      this.handlePickFinished(data.geometry)
    },

    /** 关闭弹窗 / 重新打开表单时，把可能残留的选取会话一起收掉 */
    closePick () {
      const token = this.pickToken
      this.pickToken = ''
      if (!token) return
      // ★ 必须是静态 import：动态 require 会破坏 tree-shaking，
      //   而且 pickSession 是独立小模块，不存在循环依赖（index.vue 才引它）
      endPick(token)
    },

    /**
     * 编号失焦时做唯一性校验。
     * ★ 校验只在「用户确实填了编号」时发起：空编号已经由必填校验兜住，
     *   再打一次接口只会多一条「编号不能为空」的噪音提示。
     * ★ 即使 available=true 也把 message 透出来：后端在回收站里存在同编号历史记录时
     *   会提示「该编号可以使用；注意回收站里有一条同编号的历史记录」，这条信息有用。
     */
    handleCheckCrzdbh () {
      const value = String(this.model.crzdbh || '').trim()
      if (!value) return
      checkCrzdbh(value, this.model.id || undefined)
        .then((res) => {
          if (!res || !res.success) return
          const result = res.result || {}
          if (result.available === false) {
            this.errors = Object.assign({}, this.errors, { crzdbh: result.message || '该编号已被占用' })
            return
          }
          if (result.message) {
            toast.info(result.message)
          }
          const next = Object.assign({}, this.errors)
          delete next.crzdbh
          this.errors = next
        })
        .catch(() => {
          // 唯一性校验是「辅助提示」，接口不通不阻断保存（后端保存时还会再校验一次）
        })
    },

    /* ---------------- 校验与提交 ---------------- */

    /** @returns {boolean} 是否通过（镜像后端 validateRequired 的口径） */
    validateForm () {
      const errors = {}
      const crzdbh = String(this.model.crzdbh || '').trim()
      if (!crzdbh) {
        errors.crzdbh = '出让宗地编号不能为空'
      }
      if (!String(this.model.dkmc || '').trim()) {
        errors.dkmc = '地块名称不能为空'
      }
      if (!this.model.xmfl) {
        errors.xmfl = '请选择项目分类'
      } else if (PROJECT_TYPES.indexOf(String(this.model.xmfl).trim()) === -1) {
        errors.xmfl = '项目分类只能是「市级项目」或「区级项目」'
      }

      NUMBER_FIELDS.forEach((key) => {
        const value = String(this.model[key] === null || this.model[key] === undefined ? '' : this.model[key]).trim()
        if (value && Number.isNaN(Number(value))) {
          errors[key] = '请输入数字'
        }
      })

      const lxdh = String(this.model.lxdh || '').trim()
      if (lxdh && !PHONE_PATTERN.test(lxdh)) {
        errors.lxdh = '联系电话格式不正确，应为 1 开头的 11 位手机号'
      }

      this.errors = errors
      return Object.keys(errors).length === 0
    },

    /**
     * 组装提交体。
     * ★ 后端 Service 是**显式逐字段赋值**（防越权写入），所以这里多传的字段
     *   （id / delFlag / createTime）对新增无效、对编辑只有 id 有意义；
     *   审计字段一律不传，避免误以为前端能决定它们。
     * ★ 空串转 null：BigDecimal / Date 列收到 '' 会直接反序列化失败。
     */
    buildPayload () {
      const data = {}
      const model = this.model || {}
      Object.keys(model).forEach((key) => {
        if (key === 'id') return
        const value = model[key]
        // dkGeojson 的空串是「清除图形」的显式信号，**不能**按文本惯例规整成 null：
        // 后端靠「没带字段（null = 跳过该列，保留已画图形）」与「传空串（= 真正写空）」
        // 区分两种语义（见 LandAdminServiceImpl.copyBusinessFields 的注释）
        if (key === 'dkGeojson') {
          data[key] = value === undefined ? null : value
          return
        }
        if (NUMBER_FIELDS.indexOf(key) > -1) {
          const text = String(value === null || value === undefined ? '' : value).trim()
          data[key] = text === '' ? null : Number(text)
          return
        }
        if (DATE_FIELDS.indexOf(key) > -1) {
          data[key] = value || null
          return
        }
        // 文本框的空串按后端习惯也归一成 null（cleanAndCap 对 null 是安全的）
        data[key] = value === '' || value === undefined ? null : value
      })
      // 新增时不传 id；编辑时 id 必须传，否则后端报「缺少宗地主键，无法编辑」
      if (model.id) {
        data.id = model.id
      }
      return data
    },

    handleOk () {
      this.submitted = true
      if (!this.validateForm()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      this.submit()
    },

    submit () {
      const payload = this.buildPayload()
      this.saving = true
      const request = payload.id ? editLandAdmin(payload) : addLandAdmin(payload)

      request
        .then((res) => {
          if (!res || !res.success) {
            // 后端的 message 是「创建失败！出让宗地编号「xxx」已存在（地块名称：…）」这类
            // 能直接指导用户下一步的文案，原样透出
            this.errors = Object.assign({}, this.errors, { summary: (res && res.message) || '保存失败' })
            toast.error((res && res.message) || '保存失败')
            return
          }
          toast.success(res.message || '保存成功')
          // 保存成功：弹窗关掉、绘制会话收掉，地图上那个常驻预览面
          // 也跟着本次表单会话一起消失
          clearDkPreview()
          this.closePick()
          this.visible = false
          this.$emit('ok', payload.id)
        })
        .catch(() => {
          // 请求层已经弹过错误提示，这里只负责不把弹窗留在 loading 状态
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      // 取消/关闭表单：本次画的面从地图上清掉（数据没提交，不留痕迹）
      clearDkPreview()
      this.closePick()
      this.visible = false
    },

    /* ---------------- 工具 ---------------- */

    /** 预填录入人：取当前登录用户的真实姓名，取不到就用用户名，再取不到留空 */
    currentRealname () {
      const info = this.$store && this.$store.getters ? this.$store.getters.userInfo : null
      if (!info) return ''
      return info.realname || info.username || ''
    },

    /** 预填录入单位：用户所属部门名（取不到时留空，交给用户自己填） */
    currentDeptName () {
      const info = this.$store && this.$store.getters ? this.$store.getters.userInfo : null
      if (!info) return ''
      return info.departName || info.orgCodeTxt || ''
    },

    today
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-form {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-4);

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__section-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);
  }

  /* 标签列 150px + 控件列，**两列**排布（弹窗宽 960）；窄屏再降到一列 */
  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__error {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    color: var(--screen-danger);
    background: rgba(255, 122, 107, 0.08);
    border: 1px solid var(--screen-danger);
    border-radius: var(--screen-radius-sm);
  }

  /*
   * 「添加地块」入口。
   * ★ 定位与控件列对齐：本组是 2 列网格（标签列 150px + 控件列），
   *   这个入口直接占一个格子，视觉上与「地块名称」同排，不需要额外的偏移 hack。
   */
  &__map-pick {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    min-width: 0;
  }

  &__map-pick-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    flex: 0 0 auto;
    min-height: var(--screen-control-md);
    padding: 0 18px;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
    color: var(--screen-accent);
    background: rgba(130, 198, 255, 0.12);
    border: 1px solid var(--screen-border-strong);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(:disabled) {
      color: var(--screen-text-on-accent);
      background: var(--screen-accent);
      border-color: var(--screen-accent-bright);
    }

    &:disabled {
      color: var(--screen-text-mute);
      background: rgba(6, 20, 40, 0.38);
      border-color: var(--screen-border-soft);
      cursor: not-allowed;
    }
  }

  &__map-pick-tip {
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
    .screen-ellipsis-multi(2);
  }

  /* 已绘制状态行：强调色文字 + 行内「清除」小按钮 */
  &__map-pick-drawn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-accent);
  }

  &__map-pick-clear {
    flex: 0 0 auto;
    padding: 1px 8px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-text);
    background: transparent;
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    .screen-focus-ring();

    &:hover {
      color: var(--screen-danger);
      border-color: var(--screen-danger);
    }
  }
}

@media (max-width: 900px) {
  .land-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
