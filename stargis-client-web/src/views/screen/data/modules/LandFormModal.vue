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
            「管理地块图形」入口（2026-11-15 由「添加地块」升级）：
            图形编辑整体搬进 FeatureGroupModal —— 那里能回显该宗地名下的**全部小块**
            （切片源表按 crzdbh 精确匹配），每块可定位/移除、可继续添加，取消可还原。
            表单里只剩入口与组状态展示，落库统一走表单「保存」（属性与几何一次落定）。
            ★ 按钮上的 disabled 判据只有一条：地图没挂载（?map=0 预览）时进管理弹窗
              只会看到一片空白，那种情况下不如直接不给点。
          -->
          <div class="land-form__map-pick">
            <button
              id="lf-crzdtx"
              type="button"
              class="land-form__map-pick-btn"
              :disabled="!mapEnabled"
              :aria-label="mapEnabled ? '管理地块图形：查看、定位、新增或移除该宗地的地块块' : '地图未加载，无法管理地块图形'"
              @click="handleOpenGroup"
            >
              <screen-icon name="grid" :size="16" />
              <span>管理地块图形</span>
            </button>
            <!-- 组状态：dirty 优先（有未保存变更），其次 legacy（旧字段待迁移），再次已存块数 -->
            <span v-if="partCount" class="land-form__map-pick-drawn">
              {{ partSummary }}
            </span>
            <span class="land-form__map-pick-tip">
              {{ mapEnabled ? (partCount ? '' : '尚未绘制；点击后可按块新增/移除/定位，随表单保存一并落库') : '地图未加载（?map=0 预览模式），暂不能管理地块图形' }}
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
    <!-- 图形管理弹窗：暂存编辑器（没有独立保存），「完成」把整组交回本表单 -->
    <feature-group-modal ref="groupModal" @change="handlePartsChange" />
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
// 图形组预览面的清理仍归表单会话（管理弹窗打开期间由它接管，提交/取消时统一清）
import { clear as clearDkPreview } from '@/views/screen/previewPolygon'
// 旧 dkGeojson（过渡回显）与各块顶点数的解析口径
import { parsePolygons, countVertices } from '@/views/screen/geoPolygons'
// 切片源上下文（连接编码/表名/唯一值字段）与保存后的图层重载
import { getModuleSliceContext, reloadModuleLayers } from '../sliceInfo'
import FeatureGroupModal from './FeatureGroupModal.vue'
import {
  queryLandAdminById,
  addLandAdmin,
  editLandAdmin,
  checkCrzdbh,
  queryLandDictItems,
  querySliceFeatureGroup,
  saveSliceFeatureGroup,
  deleteSliceFeatureGroup
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
    ScreenIcon,
    FeatureGroupModal
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
       * 该宗地名下的地块图形组（切片源表按 crzdbh 关联，表单是组的唯一持有者）：
       *   [{ geojson, saved, legacy, vertices }]
       *   saved=true 切片库已存；legacy=true 旧 dkGeojson 过渡回显；都 false=本次新画。
       * 管理弹窗（FeatureGroupModal）只是编辑器，「完成」把整组交回这里，
       * 表单「保存」时整组替换进切片库（空组 = 显式清空）。
       */
      geoParts: [],
      /** 管理弹窗交回过新组（状态行「保存后生效」的判据） */
      geoDirty: false,
      /** 打开表单时切片库里已存的块数（组状态展示用） */
      savedPartCount: 0,
      /**
       * 图形组是否已从切片库**真实**拉到（含真实空组）。
       * ★ 防清库的关键闸门：切片源未就绪 / 回显接口失败时为 false —— 此时
       *   geoParts 只是 legacy 回退（或空），绝不能当「用户主动清空」提交，
       *   syncFeatureGroupToSlice 会据此跳过 POST，否则一次保存就把切片库里
       *   已有的图形整组删光。管理弹窗「完成」交回组时置 true（用户看到过
       *   真实状态并主动编辑，以弹窗组为准）。
       */
      geoPartsLoaded: false,
      /** 地图是否可用（?map=0 预览时为 false，此时「管理地块图形」不给点） */
      mapEnabled: true,
      // 先给兜底，created 里再用字典覆盖（字典接口挂了页面也能填）
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item })),
      yesNoOptions: YES_NO.map((item) => ({ value: item, label: item }))
    }
  },
  computed: {
    /** 图形组块数（状态行显隐） */
    partCount () {
      return (this.geoParts || []).length
    },
    /**
     * 组状态一句话：有未保存变更 → 「保存后生效」；全部来自旧字段 → 「保存后转入切片库」；
     * 其余 → 「切片库已存 N 块」。
     */
    partSummary () {
      const n = this.partCount
      if (this.geoDirty) return `已选 ${n} 块 · 保存表单后生效`
      const hasLegacy = (this.geoParts || []).some((part) => part.legacy)
      if (hasLegacy) return `旧字段数据 ${n} 块 · 保存后转入切片库`
      return `切片库已存 ${n} 块`
    }
  },
  watch: {
    /**
     * 弹窗任何途径关掉（底部「取消」、右上角 X、Esc、保存成功）都要收尾：
     * 清掉常驻预览面（管理弹窗打开期间由它接管，这里兜底清残留）。
     * ★ 右上角 X / Esc 走的是 ScreenModal 的 update:visible，**不会**经过
     *   handleCancel，所以收尾必须挂在这里兜底（clearDkPreview 幂等）。
     */
    visible (v) {
      if (!v) {
        clearDkPreview()
        // 图形管理弹窗还开着的话一起关掉（它是本表单的子编辑器，
        // 表单都关了它留着只会让人误以为还能继续操作）
        if (this.$refs.groupModal && this.$refs.groupModal.visible) {
          this.$refs.groupModal.visible = false
        }
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
      // 图形组从零开始（新增记录的编号刚由用户填，PG 里按编号关联；
      // loaded=true —— 新增时「空组」就是真实状态，保存允许落库）
      this.geoParts = []
      this.geoDirty = false
      this.savedPartCount = 0
      this.geoPartsLoaded = true
      this._originalCrzdbh = ''
      // 兜底清一次预览面（正常路径提交/取消时已清，防的是异常中断留下的残留）
      clearDkPreview()
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
      // 图形组等 loadDetail 回来再拉（以切片库为准，拉不到才回退旧字段）
      this.geoParts = []
      this.geoDirty = false
      this.savedPartCount = 0
      this._originalCrzdbh = ''
      // 兜底清一次预览面（正常路径提交/取消时已清，防的是异常中断留下的残留）
      clearDkPreview()
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
          // ★ 记住打开时的编号：保存时编号变了要先把旧编号名下的图形整组删掉，
          //   否则旧编号在切片库里变成孤儿图形，继续在图层上显示误导人
          this._originalCrzdbh = String(data.crzdbh || '').trim()
          // 图形组以切片库为准回显（拉不到才回退旧 dkGeojson 字段，过渡期双保险）
          this.loadFeatureGroup()
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 地块图形组（切片源表，按 crzdbh 关联） ---------------- */

    /**
     * 拉切片库里该编号名下的全部图形块。
     * ★ 切片源上下文来自壳层隐藏图层树（sliceInfo.getModuleSliceContext），
     *   树未就绪时静默回退 legacy —— 不阻断编辑，保存时同步环节会再试一次。
     */
    loadFeatureGroup () {
      this.geoParts = []
      this.geoDirty = false
      this.savedPartCount = 0
      this.geoPartsLoaded = false
      const ctx = getModuleSliceContext('land')
      const value = String(this.model.crzdbh || '').trim()
      if (!ctx || !value) {
        this.useLegacyGeoParts()
        return
      }
      querySliceFeatureGroup({
        source: ctx.info.source,
        table: ctx.info.table,
        field: ctx.field,
        value: value
      })
        .then((res) => {
          if (!res || !res.success) {
            this.useLegacyGeoParts()
            return
          }
          const list = Array.isArray(res.result) ? res.result : []
          // 拉到了真实状态（含真实空组）：此后保存的整组替换以这里为准
          this.geoPartsLoaded = true
          this.savedPartCount = list.length
          if (list.length) {
            this.geoParts = list.map((geojson) => ({
              geojson: geojson,
              saved: true,
              legacy: false,
              vertices: countVertices(geojson)
            }))
          } else {
            this.useLegacyGeoParts()
          }
        })
        .catch(() => {
          // 接口不通按「切片库没有」处理：legacy 回显，保存时同步环节会把真实错误报出来
          this.useLegacyGeoParts()
        })
    },

    /**
     * 过渡期回退：切片库没有图形时用旧 dkGeojson 字段拆块回显。
     * ★ 标注 legacy=true，状态行如实显示「旧字段数据 · 保存后转入切片库」；
     *   拆块只取外环（红线无洞的常态），保存进切片库后以 PG 数据为准。
     */
    useLegacyGeoParts () {
      const rings = parsePolygons((this.model && this.model.dkGeojson) || '')
      this.geoParts = rings.map((ring) => ({
        geojson: JSON.stringify({ type: 'Polygon', coordinates: [ring] }),
        saved: false,
        legacy: true,
        vertices: ring.length - 1
      }))
    },

    /** 管理弹窗「完成」交回整组：这里只接收（顶点数重算），落库等表单「保存」 */
    handlePartsChange (parts) {
      this.geoParts = (parts || []).map((part) => Object.assign({}, part, {
        vertices: countVertices(part.geojson)
      }))
      // 用户在管理弹窗里看到过真实状态并主动编辑：以弹窗组为准（空组=主动清空，允许落库）
      this.geoPartsLoaded = true
      this.geoDirty = true
    },

    /**
     * 打开图形管理弹窗。
     * ★ 编号必须先填：图形以 crzdbh 关联切片源表，没有编号就无从关联。
     */
    handleOpenGroup () {
      if (!this.mapEnabled) {
        toast.warning('地图尚未加载，无法管理地块图形')
        return
      }
      const value = String(this.model.crzdbh || '').trim()
      if (!value) {
        toast.warning('请先填写「出让宗地编号」，地块图形以编号关联切片源表')
        return
      }
      this.$refs.groupModal.open({
        sourceType: 'land',
        parts: this.geoParts,
        uniqueValue: value,
        mapEnabled: this.mapEnabled
      })
    },

    /**
     * 表单保存成功后，把图形组整组同步进切片库（fire-and-forget，结果用 toast 反馈）。
     *
     * 时序（属性先行、几何可补偿——双库没有跨库事务，这是如实能做到的最好保证）：
     *   ① 业务属性已落库（submit 的 then 分支里）；
     *   ② 编号改过 → 先删旧编号的整组（防孤儿图形继续在图层上显示）；
     *   ③ 再把当前组整组替换进切片库（空组 = 显式清空，把块全删光的合法结果）；
     *   ④ 成功 → 重载切片图层（绕开瓦片缓存）；失败 → 弹窗已关、属性已存，
     *     toast 明示重试路径：重新打开该记录编辑并保存即可再次同步。
     */
    syncFeatureGroupToSlice () {
      const ctx = getModuleSliceContext('land')
      const value = String(this.model.crzdbh || '').trim()
      if (!ctx || !value) {
        if (value) {
          toast.warning('切片图层尚未就绪，地块图形本次未同步；稍后重新打开该记录编辑并保存即可同步')
        }
        return
      }
      // ★ 防清库闸门：图形组没从切片库真实拉到过、此刻又是空组 —— 说明「空」
      //   只是回显失败的回退态，绝不能当「用户主动清空」提交（否则一次保存
      //   就把切片库里已有的图形整组删光）。提示用户重开编辑重试。
      if (!this.geoPartsLoaded && this.geoParts.length === 0) {
        toast.warning('地块图形状态未知（切片源未就绪），本次未同步图形；重新打开该记录编辑并保存即可同步')
        return
      }
      const base = { source: ctx.info.source, table: ctx.info.table, field: ctx.field }
      const old = String(this._originalCrzdbh || '').trim()
      const post = saveSliceFeatureGroup(Object.assign({}, base, {
        value: value,
        geometries: this.geoParts.map((part) => part.geojson)
      }))
      const chain = (old && old !== value)
        ? deleteSliceFeatureGroup(Object.assign({}, base, { value: old })).then(() => post)
        : post
      chain
        .then((res) => {
          if (!res || !res.success) {
            toast.error('属性已保存，但地块图形同步失败：' + ((res && res.message) || '未知错误') +
              '。重新打开该记录编辑并保存即可重试')
            return
          }
          toast.success(`地块图形已同步到切片库（${this.geoParts.length} 块）`)
          reloadModuleLayers('land')
        })
        .catch((e) => {
          toast.error('属性已保存，但地块图形同步失败：' + (e && e.message ? e.message : e) +
            '。重新打开该记录编辑并保存即可重试')
        })
    },

    /* ---------------- 交互 ---------------- */

    /**
     * 编号失焦时做唯一性校验。
     * ★ 校验只在「用户确实填了编号」时发起：空编号已经由必填校验兜住，
     *   再打一次接口只会多一条「编号不能为空」的噪音提示。
     * ★ 编号可用时**静默通过**（2026-11-15 用户要求去掉「该编号可以使用」类提示）：
     *   可用是常态，弹提示反而是打断；只有「已被占用」才标红——那才是需要用户行动的信息。
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
        // dkGeojson 恒传 null（= 后端跳过该列，保留原值）：图形的唯一权威已经是
        // 切片源表（保存时 syncFeatureGroupToSlice 整组替换），业务表的 dk_geojson
        // 只剩存量数据、等清理字段后一并退役 —— 既不写新值也不清旧值
        if (key === 'dkGeojson') {
          data[key] = null
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
          // 保存成功：弹窗关掉，地图上那个常驻预览面也跟着本次表单会话一起消失；
          // 图形组整组同步进切片库（属性已落库，几何异步跟随，结果用 toast 反馈）
          clearDkPreview()
          this.visible = false
          this.$emit('ok', payload.id)
          this.syncFeatureGroupToSlice()
        })
        .catch(() => {
          // 请求层已经弹过错误提示，这里只负责不把弹窗留在 loading 状态
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      // 取消/关闭表单：本次的图形预览从地图上清掉（数据没提交，不留痕迹）
      clearDkPreview()
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
