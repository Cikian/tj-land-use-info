<template>
  <!--
    FacilityFormModal 配套项目新增 / 编辑弹窗
    --------------------------------
    ★ 字段数说明：配套表是**旧库宽表直接复用**，一共 53 个业务的列。
      把它们平铺成两列长表单，要翻三屏才能到底，
      用户根本不知道「哪些是必填、哪些是历史标志位」。
      这里按**用户填表的真实顺序**分成 6 组，并且刻意把两类字段做不同的呈现：

        ① 宗地归属与标识    —— 必填，且决定后面所有内容挂在哪
        ② 项目属性          —— 类别 / 建设性质 / 道路等级 / 规模 / 资金
        ③ 参建单位          —— 六方单位，纯文本
        ④ 前期审批与进度    —— 「是否完成 + 状态 + 金额 + 时间」，是本页最常改的一组
        ⑤ 档案要件（历史）  —— ★ 只读展示，新数据走「配套附件管理」
        ⑥ 说明与录入信息    —— 长文本 + 录入人/电话

    ★ 第 ⑤ 组为什么是**只读**：
      旧表里的 xjpfwj / kypfwj / csjgspfwj / dlgh / ghgcxk / gxzhslsj / zyptfa /
      zygljy / ghydxkyhbsxbl / sgxk / bdcdj 这 11 列是「是/否 标志位」，
      表示「这份要件有没有」。新系统改成了统一附件模块（t_land_attachment 的
      「12 竣工与移交文件」等 13 类），标志位只为了历史数据可导入而保留。
      如果在这一组里放可编辑下拉，用户会以为「填个『是』就等于上传了文件」——
      那是错的，而且会让新旧两套口径并存。所以这里只展示、只读，并给出引导。

    ★ 客户端校验镜像服务端（FacilityAdminServiceImpl.createFacility / checkPtxmmc）：
      · 出让宗地编号必须能查到真实宗地（旧系统不校验，产生了 60 行孤儿配套）；
      · 同一宗地下的配套项目名称不可重复。
      前者靠下拉选中保证（输入框只读），后者用失焦时的唯一性接口提示。

    公开方法：
      showAdd(crzdbh)    新增（可预置宗地编号，从「按宗地看配套」入口进来时用）
      showEdit(record)   编辑
    事件：
      ok  保存成功后抛出，父组件据此刷新
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1280"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="facility-form">
      <p v-if="errors.summary" class="facility-form__error" role="alert">
        <screen-icon name="alert-triangle" :size="14" />
        {{ errors.summary }}
      </p>

      <!-- ================= ① 宗地归属与标识 ================= -->
      <section class="facility-form__section">
        <h4 class="facility-form__section-title">
          <screen-icon name="link" :size="14" />
          宗地归属与标识
          <span class="facility-form__required" aria-hidden="true">*</span>
        </h4>

        <div class="facility-form__grid">
          <screen-field label="出让宗地" required :error="errors.crzdbh" label-width="150px">
            <screen-select
              v-model="model.crzdbh"
              :options="landOptions"
              searchable
              :filter-local="false"
              :invalid="!!errors.crzdbh"
              placeholder="输入编号或地块名称搜索"
              aria-label="出让宗地"
              @search="handleSearchLand"
              @change="handleLandChange"
            />
          </screen-field>

          <screen-field label="配套项目名称" required :error="errors.ptxmmc" html-for="ff-ptxmmc" label-width="150px">
            <screen-input
              id="ff-ptxmmc"
              v-model="model.ptxmmc"
              :maxlength="100"
              :invalid="!!errors.ptxmmc"
              placeholder="例如：侯台片区规划路一道路工程"
              @blur="handleCheckPtxmmc"
            />
          </screen-field>

          <screen-field label="地块名称" html-for="ff-dkmc" label-width="150px">
            <screen-input id="ff-dkmc" v-model="model.dkmc" :maxlength="255" placeholder="随宗地自动带出" />
          </screen-field>

          <!--
            「选取地块」：与「经营性用地」表单同一个功能（进入全屏地图模式，
            所有页面隐藏、只显示地图，上方中间两个按钮）。
            ★ 为什么配套表单也要这个入口：配套本身也是落在地块上的实体，
              用户「按图找配套」的需求和宗地一样强；两处共用同一套会话状态
              （views/screen/pickSession.js），互不干扰。
          -->
          <div class="facility-form__map-pick">
            <button
              id="ff-crzdtx"
              type="button"
              class="facility-form__map-pick-btn"
              :disabled="!mapEnabled"
              :aria-label="mapEnabled ? '选取地块，进入全屏地图勾选地块' : '地图未加载，无法选取地块'"
              @click="handleOpenMapPick"
            >
              <screen-icon name="grid" :size="16" />
              <span>选取地块</span>
            </button>
            <span class="facility-form__map-pick-tip">
              {{ mapEnabled ? '' : '地图未加载（?map=0 预览模式），暂不能选取地块' }}
            </span>
          </div>

          <screen-field label="行政区划" label-width="150px">
            <screen-select
              v-model="model.xzqh"
              :options="xzqhOptions"
              placeholder="留空则自动取宗地的行政区划"
              aria-label="行政区划"
            />
          </screen-field>

          <screen-field label="项目分类" label-width="150px">
            <screen-select
              v-model="model.xmfl"
              :options="projectTypeOptions"
              placeholder="留空则自动取宗地的项目分类"
              aria-label="项目分类"
            />
          </screen-field>

          <screen-field label="配套设施类别" label-width="150px">
            <screen-select
              v-model="model.ptsslb"
              :options="categoryOptions"
              placeholder="请选择配套设施类别"
              aria-label="配套设施类别"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= ② 项目属性与规模 ================= -->
      <section class="facility-form__section">
        <h4 class="facility-form__section-title">
          <screen-icon name="sliders" :size="14" />
          项目属性与规模
        </h4>

        <div class="facility-form__grid">
          <screen-field label="建设性质" label-width="150px">
            <screen-select
              v-model="model.jsxx"
              :options="natureOptions"
              placeholder="请选择建设性质"
              aria-label="建设性质"
            />
          </screen-field>

          <screen-field label="道路等级" label-width="150px">
            <screen-select
              v-model="model.dldj"
              :options="roadLevelOptions"
              placeholder="请选择道路等级"
              aria-label="道路等级"
            />
          </screen-field>

          <screen-field label="是否涉及提级论证" label-width="150px">
            <screen-select v-model="model.sfzsjtjlz" :options="yesNoOptions" placeholder="请选择" aria-label="是否涉及提级论证" />
          </screen-field>

          <screen-field label="提级论证是否通过" label-width="150px">
            <screen-select v-model="model.tjlzsftg" :options="yesNoOptions" placeholder="请选择" aria-label="提级论证是否通过" />
          </screen-field>

          <screen-field label="规划红线宽度（米）" :error="errors.ghhxkd" html-for="ff-ghhxkd" label-width="150px">
            <screen-input
              id="ff-ghhxkd"
              v-model="model.ghhxkd"
              type="number"
              :step="0.01"
              :invalid="!!errors.ghhxkd"
              placeholder="数值" />
          </screen-field>

          <screen-field label="长度（米）" :error="errors.cd" html-for="ff-cd" label-width="150px">
            <screen-input
              id="ff-cd"
              v-model="model.cd"
              type="number"
              :step="0.01"
              :invalid="!!errors.cd"
              placeholder="数值" />
          </screen-field>

          <screen-field label="投资估算（万元）" :error="errors.tzgs" html-for="ff-tzgs" label-width="150px">
            <screen-input
              id="ff-tzgs"
              v-model="model.tzgs"
              type="number"
              :step="0.01"
              :invalid="!!errors.tzgs"
              placeholder="数值" />
          </screen-field>

          <screen-field label="资金来源" label-width="150px">
            <screen-select v-model="model.zjly" :options="fundSourceOptions" placeholder="请选择资金来源" aria-label="资金来源" />
          </screen-field>

          <screen-field label="地块出让时承诺的配套竣工时间" label-width="150px">
            <screen-date-input v-model="model.dkcrscndptjgsj" placeholder="请选择日期" />
          </screen-field>
        </div>
      </section>

      <!-- ================= ③ 参建单位 ================= -->
      <section class="facility-form__section">
        <h4 class="facility-form__section-title">
          <screen-icon name="user" :size="14" />
          参建单位
        </h4>

        <div class="facility-form__grid">
          <screen-field label="建设单位" html-for="ff-jsdw" label-width="150px">
            <screen-input id="ff-jsdw" v-model="model.jsdw" :maxlength="100" placeholder="建设单位" />
          </screen-field>
          <screen-field label="设计单位" html-for="ff-sjdw" label-width="150px">
            <screen-input id="ff-sjdw" v-model="model.sjdw" :maxlength="100" placeholder="设计单位" />
          </screen-field>
          <screen-field label="勘察单位" html-for="ff-kcdw" label-width="150px">
            <screen-input id="ff-kcdw" v-model="model.kcdw" :maxlength="100" placeholder="勘察单位" />
          </screen-field>
          <screen-field label="监理单位" html-for="ff-jldw" label-width="150px">
            <screen-input id="ff-jldw" v-model="model.jldw" :maxlength="100" placeholder="监理单位" />
          </screen-field>
          <screen-field label="施工单位" html-for="ff-sgdw" label-width="150px">
            <screen-input id="ff-sgdw" v-model="model.sgdw" :maxlength="100" placeholder="施工单位" />
          </screen-field>
          <screen-field label="接收管养单位" html-for="ff-jsgydw" label-width="150px">
            <screen-input id="ff-jsgydw" v-model="model.jsgydw" :maxlength="100" placeholder="接收管养单位" />
          </screen-field>
        </div>
      </section>

      <!-- ================= ④ 前期审批与进度 ================= -->
      <section class="facility-form__section">
        <h4 class="facility-form__section-title">
          <screen-icon name="trending-up" :size="14" />
          前期审批与进度
        </h4>

        <div class="facility-form__grid">
          <screen-field label="项建批复是否完成" label-width="150px">
            <screen-select v-model="model.xjpfsfwc" :options="yesNoOptions" placeholder="请选择" aria-label="项建批复是否完成" />
          </screen-field>
          <screen-field label="项建批复状态" label-width="150px">
            <screen-select v-model="model.xjpfzt" :options="progressStateOptions" placeholder="请选择" aria-label="项建批复状态" />
          </screen-field>

          <screen-field label="可研批复是否完成" label-width="150px">
            <screen-select v-model="model.kypfsfwc" :options="yesNoOptions" placeholder="请选择" aria-label="可研批复是否完成" />
          </screen-field>
          <screen-field label="可研批复状态" label-width="150px">
            <screen-select v-model="model.kypfzt" :options="progressStateOptions" placeholder="请选择" aria-label="可研批复状态" />
          </screen-field>

          <screen-field label="初设及概算批复是否完成" label-width="150px">
            <screen-select v-model="model.csjgspfsfwc" :options="yesNoOptions" placeholder="请选择" aria-label="初设及概算批复是否完成" />
          </screen-field>
          <screen-field label="初设及概算批复状态" label-width="150px">
            <screen-select v-model="model.csjgspfzt" :options="progressStateOptions" placeholder="请选择" aria-label="初设及概算批复状态" />
          </screen-field>

          <screen-field label="概算批复金额（万元）" :error="errors.gspfje" html-for="ff-gspfje" label-width="150px">
            <screen-input
              id="ff-gspfje"
              v-model="model.gspfje"
              type="number"
              :step="0.01"
              :invalid="!!errors.gspfje"
              placeholder="数值" />
          </screen-field>
          <screen-field label="资金落实情况" label-width="150px">
            <screen-select v-model="model.zjlsqk" :options="fundStateOptions" placeholder="请选择" aria-label="资金落实情况" />
          </screen-field>

          <screen-field label="是否开工" label-width="150px">
            <screen-select v-model="model.sfkg" :options="yesNoOptions" placeholder="请选择" aria-label="是否开工" />
          </screen-field>
          <screen-field label="开工状态" label-width="150px">
            <screen-select v-model="model.kgzt" :options="progressStateOptions" placeholder="请选择" aria-label="开工状态" />
          </screen-field>
          <screen-field label="预计开工时间" label-width="150px">
            <screen-date-input v-model="model.yjkgsj" placeholder="请选择日期" />
          </screen-field>
          <screen-field label="实际开工时间" label-width="150px">
            <screen-date-input v-model="model.sjkgsj" placeholder="请选择日期" />
          </screen-field>

          <screen-field label="是否竣工" label-width="150px">
            <screen-select v-model="model.sfjg" :options="yesNoOptions" placeholder="请选择" aria-label="是否竣工" />
          </screen-field>
          <screen-field label="预计竣工时间" label-width="150px">
            <screen-date-input v-model="model.yjjgsj" placeholder="请选择日期" />
          </screen-field>
          <screen-field label="实际竣工时间" label-width="150px">
            <screen-date-input v-model="model.sjjgsj" placeholder="请选择日期" />
          </screen-field>
          <screen-field label="是否移交" label-width="150px">
            <screen-select v-model="model.sfyj" :options="yesNoOptions" placeholder="请选择" aria-label="是否移交" />
          </screen-field>
        </div>
      </section>

      <!-- ================= ⑤ 档案要件（历史，只读） ================= -->
      <section class="facility-form__section is-readonly">
        <h4 class="facility-form__section-title">
          <screen-icon name="archive" :size="14" />
          档案要件（历史标志位 · 只读）
          <span class="facility-form__badge">历史数据</span>
        </h4>

        <div class="facility-form__readonly">
          <span v-for="item in archiveFlags" :key="item.key" class="facility-form__readonly-item">
            <span class="facility-form__readonly-label">{{ item.label }}</span>
            <screen-tag :tone="flagTone(model[item.key])" size="sm">{{ model[item.key] || '未填' }}</screen-tag>
          </span>
        </div>

        <div class="facility-form__grid">
          <screen-field label="配套项目核定用地与地籍调查" label-width="210px">
            <screen-select v-model="model.ptxmhdydydjdc" :options="yesNoOptions" placeholder="请选择" aria-label="配套项目核定用地与地籍调查" />
          </screen-field>
        </div>
      </section>

      <!-- ================= ⑥ 说明与录入信息 ================= -->
      <section class="facility-form__section">
        <h4 class="facility-form__section-title">
          <screen-icon name="file-text" :size="14" />
          说明与录入信息
        </h4>

        <div class="facility-form__grid">
          <screen-field label="具体问题" html-for="ff-jtwt" label-width="150px">
            <screen-input
              id="ff-jtwt"
              v-model="model.jtwt"
              type="textarea"
              :rows="2"
              :maxlength="2500"
              placeholder="例如：北侧燃气管道未迁改，影响路基施工"
            />
          </screen-field>

          <screen-field label="工作建议" html-for="ff-gzjy" label-width="150px">
            <screen-input
              id="ff-gzjy"
              v-model="model.gzjy"
              type="textarea"
              :rows="2"
              :maxlength="100"
              placeholder="例如：建议协调燃气公司提前迁改"
            />
          </screen-field>

          <screen-field label="资料缺失内容及说明" html-for="ff-zlqsnrjsm" label-width="150px">
            <screen-input
              id="ff-zlqsnrjsm"
              v-model="model.zlqsnrjsm"
              type="textarea"
              :rows="2"
              :maxlength="2500"
              placeholder="缺哪些扫描件、缺在哪个环节"
            />
          </screen-field>

          <screen-field label="备注" html-for="ff-bz" label-width="150px">
            <screen-input
              id="ff-bz"
              v-model="model.bz"
              type="textarea"
              :rows="2"
              :maxlength="100"
              placeholder="补充说明" />
          </screen-field>

          <screen-field label="录入单位" html-for="ff-lrdw" label-width="150px">
            <screen-input id="ff-lrdw" v-model="model.lrdw" :maxlength="100" placeholder="录入单位" />
          </screen-field>

          <screen-field label="录入人" html-for="ff-lrr" label-width="150px">
            <screen-input id="ff-lrr" v-model="model.lrr" :maxlength="100" placeholder="姓名" />
          </screen-field>

          <screen-field label="联系电话" :error="errors.lxdh" html-for="ff-lxdh" label-width="150px">
            <screen-input
              id="ff-lxdh"
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
  ScreenTag,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { uid } from '@/components/screen/utils'
// 「选取地块」会话：表单弹窗与全屏覆盖层都挂在 body 上（不在同一棵组件树里），
// 只能通过这份共享状态对话，详见 views/screen/pickSession.js 的说明
import { startPick, endPick, isPickIssuer } from '@/views/screen/pickSession'
import {
  queryFacilityAdminById,
  addFacilityAdmin,
  editFacilityAdmin,
  checkPtxmmc
} from '@/api/land/facilityAdmin'
import { queryLandOptions } from '@/api/land/landData'
import { queryLandDictItems } from '@/api/land/landAdmin'
import {
  XZQH_LIST,
  PROJECT_TYPES,
  FACILITY_CATEGORIES,
  YES_NO,
  BUILD_NATURES,
  ROAD_LEVELS,
  FUND_SOURCES,
  PROGRESS_STATES,
  FUND_STATES,
  dictDefinitions
} from '../constants'

/** 需要转成数字提交的字段（后端是 BigDecimal，空串会反序列化失败） */
const NUMBER_FIELDS = ['ghhxkd', 'cd', 'tzgs', 'gspfje']

/** 需要转成 null 的日期字段 */
const DATE_FIELDS = ['dkcrscndptjgsj', 'yjkgsj', 'sjkgsj', 'yjjgsj', 'sjjgsj']

/** 后端强校验的手机号规则（与 LandAdminServiceImpl 同一套口径） */
const PHONE_PATTERN = /^1[3456789]\d{9}$/

/** 第 ⑤ 组的只读历史标志位（顺序与旧表列顺序一致） */
const ARCHIVE_FLAGS = [
  { key: 'xjpfwj', label: '项建批复文件' },
  { key: 'kypfwj', label: '可研批复文件' },
  { key: 'csjgspfwj', label: '初设及概算批复文件' },
  { key: 'dlgh', label: '道路规划' },
  { key: 'ghgcxk', label: '规划工程许可' },
  { key: 'gxzhslsj', label: '管线综合矢量数据(shp)' },
  { key: 'zyptfa', label: '专业配套方案' },
  { key: 'zyglyj', label: '专业管理意见' },
  { key: 'ghydxkyhbsxbl', label: '规划用地许可与划拨手续办理' },
  { key: 'sgxk', label: '施工许可' },
  { key: 'bdcdj', label: '不动产登记' }
]

/** 53 个业务字段的空模型（键名与 Facility 实体逐字一致） */
function emptyModel () {
  return {
    id: '',
    crzdbh: '',
    ptxmmc: '',
    dkmc: '',
    ptsslb: '',
    xzqh: '',
    xmfl: '',
    jsxx: '',
    dldj: '',
    sfzsjtjlz: '',
    tjlzsftg: '',
    ghhxkd: '',
    cd: '',
    tzgs: '',
    zjly: '',
    dkcrscndptjgsj: '',
    jsdw: '',
    sjdw: '',
    kcdw: '',
    jldw: '',
    sgdw: '',
    jsgydw: '',
    xjpfsfwc: '',
    xjpfzt: '',
    kypfsfwc: '',
    kypfzt: '',
    csjgspfsfwc: '',
    csjgspfzt: '',
    gspfje: '',
    zjlsqk: '',
    sfkg: '',
    kgzt: '',
    yjkgsj: '',
    sjkgsj: '',
    sfjg: '',
    yjjgsj: '',
    sjjgsj: '',
    sfyj: '',
    ptxmhdydydjdc: '',
    xjpfwj: '',
    kypfwj: '',
    csjgspfwj: '',
    dlgh: '',
    ghgcxk: '',
    gxzhslsj: '',
    zyptfa: '',
    zyglyj: '',
    ghydxkyhbsxbl: '',
    sgxk: '',
    bdcdj: '',
    jtwt: '',
    gzjy: '',
    zlqsnrjsm: '',
    bz: '',
    lrdw: '',
    lrr: '',
    lxdh: ''
  }
}

export default {
  name: 'FacilityFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenTag,
    ScreenIcon
  },
  data () {
    return {
      visible: false,
      title: '新增配套项目',
      saving: false,
      loading: false,
      /** 是否已经点过一次保存：只有点过之后才把校验错误显示出来 */
      submitted: false,
      model: emptyModel(),
      errors: {},
      /** 宗地下拉（远程搜索，filter-local=false，由 @search 拉取） */
      landOptions: [],
      landSearchTimer: null,
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item })),
      categoryOptions: FACILITY_CATEGORIES.map((item) => ({ value: item, label: item })),
      yesNoOptions: YES_NO.map((item) => ({ value: item, label: item })),
      natureOptions: BUILD_NATURES.map((item) => ({ value: item, label: item })),
      roadLevelOptions: ROAD_LEVELS.map((item) => ({ value: item, label: item })),
      fundSourceOptions: FUND_SOURCES.map((item) => ({ value: item, label: item })),
      progressStateOptions: PROGRESS_STATES.map((item) => ({ value: item, label: item })),
      fundStateOptions: FUND_STATES.map((item) => ({ value: item, label: item })),
      archiveFlags: ARCHIVE_FLAGS,
      /**
       * 本次「选取地块」会话的令牌（没进过选取模式时为空串）。
       * ★ 必须保存下来：结束会话时要 verbatim 交回去做发起者校验，
       *   否则「两个表单都开着时，后一个的取消会把前一个的选取模式关掉」。
       */
      pickToken: '',
      /** 地图是否可用（?map=0 预览时为 false，此时「选取地块」不给点） */
      mapEnabled: true,
      /** 本组件实例的唯一 id：只用于把「谁开的选取会话」写进会话状态，便于排查 */
      uid: uid('facility-form')
    }
  },
  created () {
    /*
     * 地图是否加载：判据只有一个 —— 地址里有没有 ?map=0（全站已有的预览开关，
     * 与 views/screen/index.vue 的 mapEnabled 同一判据）。
     * 不在这里探测 window.viewer：Cesium 是异步初始化的，
     * 「按钮先可点、点下去才发现地图没起来」比「一开始就不给点」体验差得多。
     */
    this.mapEnabled = !(this.$route && this.$route.query && this.$route.query.map === '0')
    queryLandDictItems(dictDefinitions()).then((dicts) => {
      if (dicts.xzqh) this.xzqhOptions = dicts.xzqh
      if (dicts.projectType) this.projectTypeOptions = dicts.projectType
      if (dicts.facilityCategory) this.categoryOptions = dicts.facilityCategory
    })
    /*
     * 订阅「选取地块」会话的结束事件。
     * ★ 为什么走 $bus：覆盖层由 views/screen/index.vue 渲染，与本弹窗是 body 上的
     *   两棵平行节点，没有父子关系。$bus 是本工程既有的跨组件通道。
     * ★ payload 带 token，必须先校验是不是自己发起的那次会话 ——
     *   同页可能同时开着宗地表单与配套表单。
     */
    this.$bus.$on('facilityMapPickFinished', this.handlePickEvent)
  },
  beforeDestroy () {
    this.$bus.$off('facilityMapPickFinished', this.handlePickEvent)
    if (this.landSearchTimer) {
      clearTimeout(this.landSearchTimer)
      this.landSearchTimer = null
    }
  },
  methods: {
    /* ---------------- 对外入口 ---------------- */

    showAdd (crzdbh) {
      this.title = '新增配套项目'
      this.model = emptyModel()
      this.model.crzdbh = crzdbh || ''
      this.model.lrdw = this.currentDeptName()
      this.model.lrr = this.currentRealname()
      this.errors = {}
      this.submitted = false
      this.closePick()
      this.visible = true
      // 预置了宗地编号时，下拉里必须先有这一项，否则触发器会显示原始编号看起来很怪
      this.loadLandOptions(crzdbh || '')
      if (crzdbh) this.applyLandDefaults(crzdbh)
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少配套项目主键，无法编辑')
        return
      }
      this.title = '编辑配套项目'
      this.model = emptyModel()
      this.errors = {}
      this.submitted = false
      this.closePick()
      this.visible = true
      this.loading = true
      this.loadDetail(record.id)
    },

    loadDetail (id) {
      queryFacilityAdminById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '配套项目详情加载失败')
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
          // 当前值必须补进下拉，否则宗地选择器会显示空白（值不在选项里时不会自动造项）
          this.loadLandOptions(this.model.crzdbh || '', this.model.dkmc || '')
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 「选取地块」全屏地图模式 ---------------- */

    /**
     * 打开「选取地块」全屏地图模式。
     * ===============================================================
     * 打开后由 views/screen/index.vue 渲染的 ScreenPickStage 覆盖整个页面：
     * 所有页面隐藏、只留下方地图，页面上方中间两个按钮（取消选择 / 选择完成）。
     *
     * ★ 本表单**自己不渲染任何地图**，也不负责关闭覆盖层：
     *   地图是 ScreenStage 里那个常驻的 Cesium 实例（本弹窗挂在 body 上，
     *   拿不到它，也不该再创建第二个 Viewer —— 那会白烧一份 GPU 与内存）。
     *
     * @returns {string} 本次会话令牌；地图不可用时不开启会话，返回空串
     */
    handleOpenMapPick () {
      if (!this.mapEnabled) {
        toast.warning('地图尚未加载，无法选取地块')
        return ''
      }
      // 自己上次的令牌如果已经不是当前会话（被 ended / 被别人接管），先清掉，
      // 否则 closePick 会拿一个陈旧令牌去 endPick，语义上会显得「莫名关掉了别的会话」
      if (this.pickToken && !isPickIssuer(this.pickToken)) this.pickToken = ''
      if (this.pickToken) return this.pickToken
      // ★ startPick 在「已有别人开的会话」时会返回**那个**会话的令牌。
      //   这里如实记下来（而不是拒绝），保证本次操作总能被自己收到结束事件；
      //   代价是别人那次会话的发起者会在结束时被顺带清掉令牌 —— 可接受，
      //   因为同一时刻只可能有一张全屏地图，本来就不支持并行选取。
      this.pickToken = startPick({
        source: 'facility',
        title: '正在选取配套项目地块，绘制期间地图可以自由拖动与缩放',
        issuer: `FacilityFormModal#${this.uid}`
      })
      return this.pickToken
    },

    /**
     * 覆盖层抛回的结束事件（$bus: facilityMapPickFinished）。
     * @param {{ token: string, geometry: * }} payload
     */
    handlePickEvent (payload) {
      const data = payload || {}
      // 不是自己发起的那次会话 → 与自己无关，直接忽略
      if (!this.pickToken || data.token !== this.pickToken) return
      this.handlePickFinished(data.geometry)
    },

    /**
     * 选取结束（取消选择 / 选择完成都会走）。
     * @param {*} geometry 画好的地块图形；本期绘制未接入，恒为 null。
     *
     * ★ 本期**不写回任何字段**：没有图形时保留原值，静默清空会让用户以为
     *   「选完了但没生效」。接入绘制后在这里按 geometry 填 model 的对应字段即可。
     */
    handlePickFinished (geometry) {
      this.pickToken = ''
      if (geometry) {
        // 目前没有任何字段能接收图形（后端实体也没有对应列，属于后续改动）
        toast.info('已获取地块图形，写入字段的功能待接入')
      }
    },

    /** 关闭弹窗 / 重新打开表单时，把可能残留的选取会话一起收掉 */
    closePick () {
      const token = this.pickToken
      this.pickToken = ''
      if (token) endPick(token)
    },

    /* ---------------- 宗地下拉 ---------------- */
    /**
     * 远程搜索宗地。
     * ★ 本地 filter-local=false + @search：选项由这里拉，
     *   因为宗地有上千条，全量下发不现实。
     * ★ 300ms 防抖：每敲一个字都请求会让输入框明显发涩。
     */
    handleSearchLand (keyword) {
      if (this.landSearchTimer) clearTimeout(this.landSearchTimer)
      this.landSearchTimer = setTimeout(() => {
        this.loadLandOptions(keyword)
      }, 300)
    },

    loadLandOptions (keyword, extraLabel) {
      queryLandOptions({ keyword: keyword || undefined, limit: 50 })
        .then((res) => {
          if (!res || !res.success) return
          const options = (res.result || []).map((item) => ({
            value: item.crzdbh,
            label: item.label || item.crzdbh,
            raw: item
          }))
          // 编辑态：当前值可能不在前 50 条里，手工补一项，否则触发器显示空白
          const current = this.model.crzdbh
          if (current && !options.some((item) => item.value === current)) {
            options.unshift({
              value: current,
              label: extraLabel ? `${current}（${extraLabel}）` : current,
              raw: null
            })
          }
          this.landOptions = options
        })
        .catch(() => {
          this.landOptions = []
        })
    },

    /**
     * 选中宗地后带出地块名称 / 区划 / 项目分类。
     * ★ 只填**空的**字段：用户手工改过的值不能被覆盖 —— 配套的地块名称
     *   与宗地的可能不同（分标段时会改写），自动覆盖等于把用户的编辑抹掉。
     */
    handleLandChange (crzdbh) {
      if (!crzdbh) return
      this.applyLandDefaults(crzdbh)
    },

    applyLandDefaults (crzdbh) {
      const hit = this.landOptions.find((item) => item.value === crzdbh)
      const raw = hit && hit.raw ? hit.raw : null
      if (raw) {
        this.fillIfBlank('dkmc', raw.dkmc)
        this.fillIfBlank('xzqh', raw.xzqh)
        this.fillIfBlank('xmfl', raw.xmfl)
        return
      }
      // 下拉里没带原始对象（编辑态补项时）→ 用编码选项接口按编号查一次
      queryLandOptions({ keyword: crzdbh, limit: 5 })
        .then((res) => {
          if (!res || !res.success) return
          const item = (res.result || []).find((row) => row && row.crzdbh === crzdbh)
          if (!item) return
          this.fillIfBlank('dkmc', item.dkmc)
          this.fillIfBlank('xzqh', item.xzqh)
          this.fillIfBlank('xmfl', item.xmfl)
        })
        .catch(() => {})
    },

    fillIfBlank (key, value) {
      if (!value) return
      const current = this.model[key]
      if (current === '' || current === null || current === undefined) {
        this.model[key] = value
      }
    },

    /* ---------------- 校验 ---------------- */

    /**
     * 名称唯一性（同一宗地下不可重复）。
     * ★ 一次调用同时校验「宗地是否存在」与「名称是否重复」：
     *   后端 checkPtxmmc 的两个结论都放在 message 里，
     *   宗地不存在的文案是「请先录入该宗地」—— 这是最常见的失败原因，要原样透出。
     */
    handleCheckPtxmmc () {
      const crzdbh = String(this.model.crzdbh || '').trim()
      const ptxmmc = String(this.model.ptxmmc || '').trim()
      if (!crzdbh || !ptxmmc) return
      checkPtxmmc(crzdbh, ptxmmc, this.model.id || undefined)
        .then((res) => {
          if (!res || !res.success) return
          const result = res.result || {}
          if (result.available === false) {
            this.errors = Object.assign({}, this.errors, { ptxmmc: result.message || '该名称不可用' })
            return
          }
          const next = Object.assign({}, this.errors)
          delete next.ptxmmc
          this.errors = next
        })
        .catch(() => {
          // 唯一性校验是辅助提示，接口不通不阻断保存（后端保存时还会再校验）
        })
    },

    /** @returns {boolean} 是否通过（镜像后端 createFacility 的口径） */
    validateForm () {
      const errors = {}
      if (!String(this.model.crzdbh || '').trim()) {
        errors.crzdbh = '请选择出让宗地（配套必须挂在已录入的宗地下）'
      }
      if (!String(this.model.ptxmmc || '').trim()) {
        errors.ptxmmc = '配套项目名称不能为空'
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

    /* ---------------- 提交 ---------------- */

    buildPayload () {
      const data = {}
      const model = this.model || {}
      Object.keys(model).forEach((key) => {
        if (key === 'id') return
        const value = model[key]
        if (NUMBER_FIELDS.indexOf(key) > -1) {
          const text = String(value === null || value === undefined ? '' : value).trim()
          data[key] = text === '' ? null : Number(text)
          return
        }
        if (DATE_FIELDS.indexOf(key) > -1) {
          data[key] = value || null
          return
        }
        data[key] = value === '' || value === undefined ? null : value
      })
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
      const request = payload.id ? editFacilityAdmin(payload) : addFacilityAdmin(payload)

      request
        .then((res) => {
          if (!res || !res.success) {
            this.errors = Object.assign({}, this.errors, { summary: (res && res.message) || '保存失败' })
            toast.error((res && res.message) || '保存失败')
            return
          }
          toast.success(res.message || '保存成功')
          // 保存成功会关掉弹窗，正在进行的选取会话必须一起收掉，
          // 否则会留下一张没有出口的全屏地图
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
      this.closePick()
      this.visible = false
    },

    /* ---------------- 展示辅助 ---------------- */

    /** 历史标志位 → 标签语气（是=绿、否=灰、未填=灰） */
    flagTone (value) {
      if (value === '是') return 'success'
      if (value === '否') return 'muted'
      return 'muted'
    },

    currentRealname () {
      const info = this.$store && this.$store.getters ? this.$store.getters.userInfo : null
      if (!info) return ''
      return info.realname || info.username || ''
    },

    currentDeptName () {
      const info = this.$store && this.$store.getters ? this.$store.getters.userInfo : null
      if (!info) return ''
      return info.departName || info.orgCodeTxt || ''
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-form {
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

    &.is-readonly {
      // 历史只读组：底色再压暗一档，与可编辑区一眼分开
      background: rgba(6, 20, 40, 0.3);
      border-style: dashed;
    }
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

  &__required {
    line-height: 1;
    color: var(--screen-danger);
  }

  &__badge {
    margin-left: 4px;
    padding: 1px 6px;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-pill);
  }

  &__section-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);

    b {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }

    code {
      padding: 0 4px;
      font-family: var(--screen-font-number-family);
      color: var(--screen-accent-soft);
      background: rgba(6, 20, 40, 0.72);
      border-radius: var(--screen-radius-sm);
    }
  }

  /* 标签列 150px + 控件列，三列排布；窄屏逐级降列 */
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  /* 只读历史标志位：一排小方块，标签 + 是/否标签 */
  &__readonly {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-2) var(--screen-space-3);
  }

  &__readonly-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 4px 8px;
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    min-width: 0;
  }

  &__readonly-label {
    min-width: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
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
   * 「选取地块」入口（与 LandFormModal 的同名块样式一致，两处刻意保持同款：
   * 同一个功能在两个表单里长得不一样会让人以为是两件事）。
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
}

@media (max-width: 1500px) {
  .facility-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .facility-form__readonly {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .facility-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .facility-form__readonly {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
