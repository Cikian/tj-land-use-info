<template>
  <a-modal
    :title="title"
    :width="1100"
    :visible="visible"
    :confirm-loading="saving"
    :mask-closable="false"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleSubmit"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <a-form :label-col="labelCol" :wrapper-col="wrapperCol" class="land-form">
        <!-- ============ 一、标识与分类 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            一、标识与分类
            <span class="land-form__hint">
              「出让宗地编号」是整条数据线的业务主键 —— 配套项目、档案、收发文、
              道路台账都靠它挂上来，录错一位，后面四个模块就都关联不上
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item
                label="出让宗地编号"
                required
                :validate-status="errors.crzdbh ? 'error' : ''"
                :help="errors.crzdbh">
                <a-input
                  v-model="model.crzdbh"
                  placeholder="必填，例如 津西青西（挂）2022-004"
                  @blur="handleCheckCrzdbh" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="项目分类" required :validate-status="errors.xmfl ? 'error' : ''" :help="errors.xmfl">
                <a-select v-model="model.xmfl" placeholder="必填：市级项目 / 区级项目" allow-clear :options="xmflOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="地块名称">
                <a-input v-model="model.dkmc" placeholder="例如 某某地块" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="行政区划">
                <a-select v-model="model.xzqh" placeholder="请选择（十六个区）" allow-clear show-search :options="xzqhOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="规划用地性质">
                <a-input v-model="model.ghydxz" placeholder="例如 城镇住宅、商服" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 二、出让与面积 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            二、出让与面积
            <span class="land-form__hint">
              金额单位是<b>亿元</b>、面积单位是<b>平方米</b>、配套费单位是<b>万元</b> ——
              单位不一致是旧台账最常见的数据质量问题，这里在标签上直接写死单位
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="出让金（亿元）">
                <a-input-number v-model="model.crj" :min="0" :precision="4" style="width: 100%" placeholder="例如 12.35" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="出让时间">
                <a-date-picker v-model="model.crsj" style="width: 100%" value-format="YYYY-MM-DD" placeholder="选择日期" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="完成度">
                <a-input-number v-model="model.wcd" :min="0" :precision="2" style="width: 100%" placeholder="百分数，不确定可留空" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="可建设用地面积（㎡）">
                <a-input-number v-model="model.kjsydmj" :min="0" :precision="2" style="width: 100%" placeholder="例如 45231.50" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="总用地面积（㎡）">
                <a-input-number v-model="model.zydmj" :min="0" :precision="2" style="width: 100%" placeholder="例如 51008.00" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="建设面积（㎡）">
                <a-input-number v-model="model.jsmj" :min="0" :precision="2" style="width: 100%" placeholder="例如 90463.00" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="纳入成本的配套费（万元）">
                <a-input-number v-model="model.nrcbdptf" :min="0" :precision="2" style="width: 100%" placeholder="例如 1580.00" />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 三、受让人与交付 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            三、受让人与交付
            <span class="land-form__hint">
              合同约定交付时间与楼盘交付时间是两个独立的时间点：
              前者出自出让合同，后者是实际（或计划）交房时间，跟进楼盘进度时用的是后者
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="受让人">
                <a-input v-model="model.srr" placeholder="例如 天津某某置业有限公司" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="合同约定交付时间">
                <a-date-picker v-model="model.htydjfsj" style="width: 100%" value-format="YYYY-MM-DD" placeholder="选择日期" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="楼盘名称">
                <a-input v-model="model.lpmc" placeholder="例如 某某云著" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="楼盘交付时间">
                <a-date-picker v-model="model.lpjfsj" style="width: 100%" value-format="YYYY-MM-DD" placeholder="实际或计划交付时间" />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 四、四至 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            四、四至
            <span class="land-form__hint">
              填相邻道路 / 现状地物即可，不必写「东至」两个字（标签已经写了），
              免得同一含义出现「东至××路」和「××路」两种写法
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="6">
              <a-form-item label="东至">
                <a-input v-model="model.dz" placeholder="例如 规划路一" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="西至">
                <a-input v-model="model.xz" placeholder="例如 现状住宅" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="南至">
                <a-input v-model="model.nz" placeholder="例如 规划绿地" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="北至">
                <a-input v-model="model.bz" placeholder="例如 某某路" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 五、土地整理 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            五、土地整理
            <span class="land-form__hint">
              「计划下达文件号」是核对土地整理成本与计划的口径依据，通常形如 津国土房整〔2024〕15号
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="土地整理单位">
                <a-input v-model="model.tdzldw" placeholder="例如 天津市土地利用事务中心" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="土地整理计划">
                <a-select v-model="model.tdzljh" placeholder="请选择" allow-clear :options="yesNoOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="计划下达文件号">
                <a-input v-model="model.tdzljhxdwjh" placeholder="例如 津国土房整〔2024〕15号" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 六、配套与图形 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            六、配套与图形
            <span class="land-form__hint">
              ★ 「配套情况函 / 配套筹备函 / 图形数据」在库里存的是<b>是/否</b>，
              函件与图形文件本体走「配套附件管理」上传 ——
              旧系统只存一个「是」字，磁盘目录一丢就再也找不到那份函了
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="配套是否齐全">
                <a-select v-model="model.ptsfqq" placeholder="请选择" allow-clear :options="yesNoOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="配套情况函">
                <a-select v-model="model.ptqkh" placeholder="是否已出具" allow-clear :options="yesNoOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="配套筹备函">
                <a-select v-model="model.ptcbh" placeholder="是否已出具" allow-clear :options="yesNoOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="出让宗地图形数据">
                <a-select v-model="model.crzdtxsj" placeholder="是否已有 shp" allow-clear :options="yesNoOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="16">
              <a-form-item label="配套建设内容">
                <a-input v-model="model.ptjsnr" placeholder="例如 地块北侧规划路及雨污水管线" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 七、录入信息 ============ -->
        <div class="land-form__section">
          <div class="land-form__section-title">
            七、录入信息
            <span class="land-form__hint">
              录入单位 / 录入人 / 联系电话是「这条数据找谁核对」的凭据；
              批量导入模板里这三项是必填，逐条录入只强制编号与项目分类
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="录入单位">
                <a-input v-model="model.lrdw" placeholder="例如 天津市土地利用事务中心" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="录入单位简称">
                <a-input v-model="model.xzqh2" placeholder="旧列名 xzqh2，内容其实是单位简称" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="录入人">
                <a-input v-model="model.lrr" placeholder="例如 张三" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="联系电话" :validate-status="errors.lxdh ? 'error' : ''" :help="errors.lxdh">
                <a-input v-model="model.lxdh" placeholder="11 位手机号" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="16">
              <a-form-item label="资料缺失内容及说明">
                <a-input v-model="model.zlqsnrsm" placeholder="例如 缺少出让合同扫描件" allow-clear />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="备注">
                <a-textarea v-model="model.beizhu" :rows="2" :max-length="2000" placeholder="选填；迁移生成的数据会在这里标注「出让宗地未匹配」这类提示" />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <div class="land-form__foot">
          <a-icon type="info-circle" />
          保存时会自动把空字符串转成 null（数值与日期列传空串会被后端拒绝），
          改动的字段会逐条记入「变更履历」。
        </div>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script>
  import { getDictItems } from '@/components/dict/JDictSelectUtil'
  import { queryXzqhOptions } from '@/api/land/landData'
  import {
    addLand,
    checkCrzdbh,
    editLand,
    normalize,
    queryLandDetail
  } from '@/api/land/landAdmin'

  /**
   * 经营性用地 - 新增 / 编辑弹窗（34 个业务字段，分 7 组）
   *
   * ★ 为什么必须分组、而不是把 34 个输入框铺在一列里：
   *   34 个字段平铺大约是三屏高度，用户填到一半就分不清自己在填哪一类信息，
   *   而「录入单位 / 联系电话」这类字段会被埋在最下面 —— 恰恰是事后要找人核对时最需要的。
   *   分组口径与后端 `DataFieldLabels` 的字段分组、以及详情弹窗逐字一致
   *   （标识与分类 5 / 出让与面积 7 / 受让人与交付 4 / 四至 4 /
   *    土地整理 3 / 配套与图形 5 / 录入信息 6 = 34）。
   *
   * ★ 为什么只有「出让宗地编号」「项目分类」是必填：
   *   后端 `LandAdminServiceImpl.validateRequired` 只强制这两个（加上有值时的手机号格式）。
   *   批量导入模板把 lrdw/lrr/lxdh 也标成必填，是因为整表导入时「谁录的」无法逐个追溯；
   *   逐条录入有登录用户与操作留痕，不重复要求。前端不擅自加必填，
   *   否则会出现「页面不让存、导入却能进」两套口径。
   *
   * ★ 为什么「配套情况函 / 配套筹备函 / 图形数据 / 土地整理计划 / 配套是否齐全」是是/否下拉而不是文本框：
   *   这几列在旧库里存的就是「是 / 否」两个字（见 `LandImportField` 的 YES_NO 列），
   *   字典里也没有它们。放开自由文本会得到「是 / 有 / √ / Y」多种写法，
   *   于是「按配套是否齐全筛出未配套的宗地」这类查询会静默漏数据。
   *
   * ★ 校验为什么用「手写 errors 对象」而不是 a-form-model + rules：
   *   编号唯一性要打服务端接口（`checkCrzdbh`），这不是一条 rules 能表达的异步规则；
   *   如果规则一半在 rules、一半手写，提交时就会出现「表单说通过、接口说重复」的双重标准。
   *   统一成一个 `validate()`：先跑本地规则，再跑服务端唯一性校验，最后才提交。
   *
   * ★ 提交前为什么一定要过 `normalize()`：
   *   后端的数值列是 BigDecimal、日期列是 Date，空字符串 "" 会被 Spring 当成
   *   「有值但转换不了」而抛 400，界面上只能看到一句看不懂的报错。
   *   normalize 把空串转成 null、把数值字符串转成 Number，语义也更准确。
   *   注：xmfl 不在 normalize 的文本清单里，但它必填且本地校验已保证非空，
   *   不会出现「把空串发给后端」的情况。
   */
  export default {
    name: 'LandFormModal',
    data () {
      return {
        visible: false,
        loading: false,
        saving: false,
        isEdit: false,
        labelCol: { span: 8 },
        wrapperCol: { span: 16 },
        model: this.buildEmptyModel(),
        errors: this.buildEmptyErrors(),
        /** 是/否列（旧库这三类列只存「是/否」，不入字典，见类注释） */
        yesNoOptions: [
          { value: '是', label: '是' },
          { value: '否', label: '否' }
        ],
        /** 项目分类兜底：字典取不到时仍能选出后端认可的两个值 */
        xmflOptions: [
          { value: '市级项目', label: '市级项目' },
          { value: '区级项目', label: '区级项目' }
        ],
        xzqhOptions: []
      }
    },
    computed: {
      title () {
        return this.isEdit ? `编辑经营性用地${this.model.crzdbh ? '：' + this.model.crzdbh : ''}` : '新增经营性用地'
      }
    },
    created () {
      this.loadDicts()
    },
    methods: {
      // ---------------- 模型 ----------------

      /** 34 个业务字段的空表单（数值/日期用 null，a-input-number 与日期选择器才显示为空） */
      buildEmptyModel () {
        return {
          id: undefined,
          // 标识与分类
          crzdbh: '',
          dkmc: '',
          xzqh: undefined,
          xmfl: undefined,
          ghydxz: '',
          // 出让与面积
          crj: null,
          crsj: undefined,
          kjsydmj: null,
          zydmj: null,
          jsmj: null,
          nrcbdptf: null,
          wcd: null,
          // 受让人与交付
          srr: '',
          htydjfsj: undefined,
          lpmc: '',
          lpjfsj: undefined,
          // 四至
          dz: '',
          xz: '',
          nz: '',
          bz: '',
          // 土地整理
          tdzldw: '',
          tdzljhxdwjh: '',
          tdzljh: undefined,
          // 配套与图形
          ptsfqq: undefined,
          ptqkh: undefined,
          ptcbh: undefined,
          crzdtxsj: undefined,
          ptjsnr: '',
          // 录入信息
          lrdw: '',
          xzqh2: '',
          lrr: '',
          lxdh: '',
          zlqsnrsm: '',
          beizhu: ''
        }
      },
      /**
       * 空错误表。
       *
       * ★ 键要一次性建全：Vue 2 只能侦测「已存在的属性」的变化，
       *   事后往 errors 上塞新键（this.errors.xxx = ...）不会触发重渲染，
       *   表现就是「校验提示写在对象里了、但界面上不标红」。
       */
      buildEmptyErrors () {
        return { crzdbh: '', xmfl: '', lxdh: '' }
      },

      /**
       * 打开弹窗。
       *
       * @param {object|null} record 列表行（null / 不传 = 新增）
       */
      open (record) {
        const isEdit = !!(record && record.id)
        this.isEdit = isEdit
        this.errors = this.buildEmptyErrors()
        this.saving = false
        this.visible = true
        if (!isEdit) {
          this.model = this.buildEmptyModel()
          return
        }
        this.fill(record)
        // ★ 列表接口返回的就是完整实体（34 个业务字段都在），正常路径不再请求详情；
        //   只有调用方手上只有 id 时才补拉一次，否则「编辑时再拉详情」会在用户
        //   已经开始改字之后把内容覆盖回去。
        if (record.crzdbh === undefined && record.dkmc === undefined) {
          this.loadDetail(record.id)
        }
      },
      /**
       * 用一条宗地记录填充表单。
       *
       * ★ 逐字段取值而不是 Object.assign：接口可能带回审计字段与展示字段，
       *   而表单只认识这 34 个键 —— 在这里就把模型收窄，提交时不必再猜哪些键该删。
       */
      fill (record) {
        const model = this.buildEmptyModel()
        if (record) {
          Object.keys(model).forEach(key => {
            if (record[key] !== undefined) {
              model[key] = record[key]
            }
          })
        }
        this.model = model
      },
      loadDetail (id) {
        this.loading = true
        queryLandDetail(id).then(res => {
          if (res.success && res.result) {
            this.fill(res.result)
          } else if (res.message) {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '宗地详情加载失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleClose () {
        this.visible = false
        this.saving = false
      },

      // ---------------- 字典 ----------------

      /**
       * 加载行政区划与项目分类。
       *
       * ★ 用 `getDictItems`：它优先读 localStorage 里登录时缓存好的整份字典
       *   （`getDictItemsFromCache`），因此同一页多处使用不会各发一次请求。
       * ★ 两份字典都留了兜底：项目分类兜底成后端写死的两个值（否则字典接口挂了
       *   就一个都选不出来，表单直接卡死）；行政区划兜底成「按已有宗地聚合」的区划下拉
       *   （`queryXzqhOptions`），语义与字典一致，只是取值来自现有数据。
       */
      loadDicts () {
        getDictItems('land_project_type').then(items => {
          if (items && items.length) {
            this.xmflOptions = items.map(item => ({ value: item.value, label: item.label || item.text }))
          }
        }).catch(() => { /* 保留兜底的两个值 */ })
        getDictItems('land_xzqh').then(items => {
          if (items && items.length) {
            this.xzqhOptions = items.map(item => ({ value: item.value, label: item.label || item.text }))
            return
          }
          this.loadXzqhFallback()
        }).catch(() => {
          this.loadXzqhFallback()
        })
      },
      loadXzqhFallback () {
        queryXzqhOptions().then(res => {
          if (res.success && res.result && res.result.length) {
            this.xzqhOptions = res.result.map(item => ({ value: item.value, label: item.label }))
          }
        }).catch(() => { /* 区划是选填，取不到就不给选项，不阻断录入 */ })
      },

      // ---------------- 校验 ----------------

      /**
       * 出让宗地编号唯一性（失焦时实时提示）。
       *
       * ★ 校验失败（接口挂了）时**不写入错误提示**：编号是否重复最终由后端裁定，
       *   把一次网络抖动显示成「编号已被占用」会让用户白白改掉一个好编号。
       */
      handleCheckCrzdbh () {
        const crzdbh = (this.model.crzdbh || '').trim()
        if (!crzdbh) {
          this.errors.crzdbh = ''
          return
        }
        checkCrzdbh(crzdbh, this.model.id).then(res => {
          const data = res && res.success ? (res.result || {}) : null
          if (!data) {
            return
          }
          this.errors.crzdbh = data.available === false ? (data.message || '该编号已被占用') : ''
          // 可用但带提示（例如回收站里有同编号历史记录）时如实转达，不要吞掉
          if (data.available !== false && data.message && data.message.indexOf('可以使用') >= 0) {
            this.$message.info(data.message)
          }
        }).catch(() => { /* 失焦校验失败不打断录入，提交前还会再校验一次 */ })
      },
      /** 本地规则校验（返回 false 时 errors 上已有对应提示） */
      validate () {
        this.errors = this.buildEmptyErrors()
        let valid = true
        if (!(this.model.crzdbh || '').trim()) {
          this.errors.crzdbh = '出让宗地编号不能为空（它是配套、档案、收发文的关联键）'
          valid = false
        }
        const xmfl = (this.model.xmfl || '').trim()
        if (!xmfl) {
          this.errors.xmfl = '项目分类不能为空'
          valid = false
        } else if (xmfl !== '市级项目' && xmfl !== '区级项目') {
          this.errors.xmfl = `项目分类「${xmfl}」不合法，只能是 市级项目 / 区级项目`
          valid = false
        }
        const lxdh = (this.model.lxdh || '').trim()
        if (lxdh && !/^1[3456789]\d{9}$/.test(lxdh)) {
          this.errors.lxdh = '联系电话格式不正确，应为 1 开头的 11 位手机号'
          valid = false
        }
        return valid
      },
      /**
       * 提交前的服务端唯一性校验。
       *
       * @returns {Promise<boolean>} 是否允许继续提交
       */
      checkCrzdbhBeforeSubmit () {
        const crzdbh = (this.model.crzdbh || '').trim()
        return checkCrzdbh(crzdbh, this.model.id).then(res => {
          const data = res && res.success ? (res.result || {}) : null
          if (!data) {
            return true
          }
          if (data.available === false) {
            this.errors.crzdbh = data.message || '该编号已被占用'
            this.$message.warning(this.errors.crzdbh)
            return false
          }
          return true
        }).catch(() => {
          // 预校验接口不可用时放行：后端 add/edit 自己会再查一次并给出「已被谁占用」的提示，
          // 这里拦下来只会让用户在接口抖动时完全没法录入
          return true
        })
      },

      // ---------------- 提交 ----------------

      handleSubmit () {
        if (!this.validate()) {
          this.$message.warning('还有未通过校验的字段，请在标红处修改后再保存')
          return
        }
        this.saving = true
        this.checkCrzdbhBeforeSubmit().then(available => {
          if (!available) {
            this.saving = false
            return
          }
          this.doSave()
        })
      },
      doSave () {
        const payload = normalize(Object.assign({}, this.model))
        // 审计字段与服务端维护字段不属于入参契约（后端也会忽略，但不发出去更清楚）
        const dropKeys = ['createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag', 'sourceId']
        dropKeys.forEach(key => { delete payload[key] })
        if (!this.isEdit) {
          // ★ 新增时不带 id —— 后端会忽略，但带上容易让人误以为这次是「覆盖某一条」
          delete payload.id
        }
        const action = this.isEdit ? editLand : addLand
        action(payload).then(res => {
          if (!res.success) {
            // 后端的冲突提示里带着「被哪条记录占用」，原样展示，不要盖成「保存失败」
            this.$message.warning(res.message || '保存失败')
            return
          }
          this.$message.success(this.isEdit ? '修改成功！' : '新增成功！')
          this.visible = false
          this.$emit('ok')
        }).catch(e => {
          this.$message.error((e && e.message) || '保存失败')
        }).finally(() => {
          this.saving = false
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .land-form {
    &__section {
      padding: 10px 14px 0;
      margin-bottom: 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    &__section-title {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 10px;
      padding-bottom: 8px;
      margin-bottom: 10px;
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
      border-bottom: 1px solid @border-color;
    }

    &__hint {
      flex: 1 1 auto;
      min-width: 260px;
      font-size: 12px;
      font-weight: 400;
      line-height: 18px;
      color: @text-weak;

      b {
        color: #0f172a;
      }
    }

    &__foot {
      margin-bottom: 8px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    /* 34 个字段分 7 组后仍然偏长：压缩组内行距，让一屏能多看两组 */
    /deep/ .ant-form-item {
      margin-bottom: 12px;
    }

    /deep/ .ant-form-item-label {
      text-align: right;
    }

    /deep/ .ant-form-item-required::before {
      margin-right: 2px;
    }

    .anticon-info-circle {
      margin-right: 4px;
      color: @text-muted;
    }
  }
</style>
