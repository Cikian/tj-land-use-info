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
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1240"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
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
        <p class="land-form__section-hint">
          出让宗地编号是**业务主键**，配套项目、档案、收发文都按它关联
          （实测 1433 条配套里 1373 条能按编号匹配到宗地，按地块名称只有 84.9%
          且会重名），所以编号一旦写入就尽量不要再改。
        </p>

        <div class="land-form__grid">
          <screen-field
            label="出让宗地编号"
            required
            :error="errors.crzdbh"
            html-for="lf-crzdbh"
            label-width="150px"
            tip="注意全角/半角括号与末尾的「号」字，旧库有 60 行是书写不一致"
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

          <screen-field label="可建设用地面积(㎡)" :error="errors.kjsydmj" html-for="lf-kjsydmj" label-width="150px">
            <screen-input
              id="lf-kjsydmj"
              v-model="model.kjsydmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.kjsydmj"
              placeholder="数值，可留空"
            />
          </screen-field>

          <screen-field label="总用地面积（㎡）" :error="errors.zydmj" html-for="lf-zydmj" label-width="150px">
            <screen-input
              id="lf-zydmj"
              v-model="model.zydmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.zydmj"
              placeholder="数值，可留空"
            />
          </screen-field>

          <screen-field label="建设面积（㎡）" :error="errors.jsmj" html-for="lf-jsmj" label-width="150px">
            <screen-input
              id="lf-jsmj"
              v-model="model.jsmj"
              type="number"
              :step="0.01"
              :invalid="!!errors.jsmj"
              placeholder="数值，可留空"
            />
          </screen-field>

          <screen-field label="纳入成本的配套费(万元)" :error="errors.nrcbdptf" html-for="lf-nrcbdptf" label-width="150px">
            <screen-input
              id="lf-nrcbdptf"
              v-model="model.nrcbdptf"
              type="number"
              :step="0.01"
              :invalid="!!errors.nrcbdptf"
              placeholder="数值，可留空"
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
        <p class="land-form__section-hint">
          联系电话是**服务端强校验**的三项之一（另两项是出让宗地编号与项目分类），
          必须填 1 开头的 11 位手机号。
        </p>

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
      // 先给兜底，created 里再用字典覆盖（字典接口挂了页面也能填）
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item })),
      yesNoOptions: YES_NO.map((item) => ({ value: item, label: item }))
    }
  },
  created () {
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

  /* 标签列 150px + 控件列，三列排布；窄屏逐级降列 */
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
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
}

@media (max-width: 1500px) {
  .land-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .land-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
