<template>
  <!--
    AttachmentUploadModal 附件上传（管理端）
    --------------------------------
    ★ 界面结构（2026-10-10 按要求重做，与大屏端保持一致）：
      第一段：选归属 —— 归属类型 + 所属对象（两个下拉并排）
      第二段：**材料类型目录树** —— 选完归属对象后立刻展示，
              每个材料类型就是一个目录节点，节点右侧有「上传」按钮，
              节点下面是该类型已有的文件。

      用户的操作只有两件事：① 选归属；② 在某个类型上点「上传」。
      **不需要选材料类型** —— 点哪个类型的上传，文件就归到那个类型。

    ★ 为什么树要在弹窗里而不是只放在管理页：
      用户传附件时心里想的是「给这个项目的这一类材料补文件」，
      在弹窗里直接看到「这个项目有哪些材料、哪类还空着」才顺。

    ★ 为什么树里要显示**已有文件**：上传前先看到已有什么，才判断得出该补哪一类。

    ★ 为什么选文件夹时内部文件**拉平**到该类型目录：
      旧系统的存储结构就只有「项目/材料类型/文件」两层；按目录层级重建会造出
      旧系统从来没有的结构，历史对照也就无从谈起。

    ★ 上传是两步（见 doSaveMeta / uploadBytes 的注释）：
      第 1 步落盘、第 2 步登记。两步的失败原因完全不同，
      所以提示与重试也要分开（字节失败重传；登记失败只重跑第 2 步）。
  -->
  <a-modal
    title="上传附件"
    :width="920"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    @cancel="handleClose">
    <!-- ============ 第一段：选择归属 ============ -->
    <div class="attach-upload__head">
      <div class="attach-upload__field">
        <span class="attach-upload__label">归属类型<i>*</i></span>
        <a-select
          v-model="bizType"
          placeholder="请选择"
          style="width: 100%"
          :options="bizTypeOptions"
          @change="handleBizTypeChange" />
      </div>
      <div v-if="bizType === 'process'" class="attach-upload__field">
        <span class="attach-upload__label">所属宗地<i>*</i></span>
        <a-select
          v-model="processLandId"
          show-search
          placeholder="输入宗地编号 / 地块名称"
          style="width: 100%"
          :filter-option="false"
          :default-active-first-option="false"
          @search="loadProcessLandOptions"
          @change="handleProcessLandChange">
          <a-select-option v-for="item in processLandOptions" :key="item.id" :value="item.id">
            {{ optionLabel(item) }}
          </a-select-option>
        </a-select>
      </div>
      <div v-if="bizType === 'process'" class="attach-upload__field">
        <span class="attach-upload__label">所属配套<i>*</i></span>
        <a-select
          v-model="processFacilityId"
          placeholder="请选择配套项目"
          style="width: 100%"
          :disabled="!processLandId && !processFacilityId"
          @change="handleProcessFacilityChange">
          <a-select-option v-for="item in processFacilityOptions" :key="item.id" :value="item.id">
            {{ optionLabel(item) }}
          </a-select-option>
        </a-select>
      </div>
      <div class="attach-upload__field">
        <span class="attach-upload__label">{{ bizType === 'process' ? '所属环节' : '所属对象' }}<i>*</i></span>
        <a-select
          v-model="bizId"
          show-search
          allow-clear
          :disabled="!bizType || (bizType === 'process' && !processFacilityId)"
          :placeholder="bizPlaceholder"
          style="width: 100%"
          :filter-option="bizType !== 'process'"
          :default-active-first-option="false"
          :not-found-content="bizLoading ? '搜索中…' : '输入编号 / 名称后搜索'"
          @search="handleBizSearch"
          @change="handleBizChange">
          <a-select-option v-for="item in bizOptions" :key="processOptionValue(item)" :value="processOptionValue(item)" :disabled="bizType === 'process' && !item.progressId">
            {{ optionLabel(item) }}
          </a-select-option>
        </a-select>
      </div>
    </div>

    <!-- ============ 第二段：材料类型目录树 ============ -->
    <div class="attach-upload__tree">
      <div class="attach-upload__tree-head">
        <span class="attach-upload__tree-title">材料目录</span>
        <span class="attach-upload__tree-hint">{{ treeHint }}</span>
      </div>

      <p v-if="!bizId" class="attach-upload__placeholder">
        请先选择归属类型与所属对象，然后在这里按材料类型上传文件
      </p>

      <a-spin v-else :spinning="treeLoading && !treeReady">
        <div
          v-for="group in groups"
          :key="group.key"
          class="attach-upload__group"
          :class="{ 'is-uploading': uploadingType === group.fileType }">
          <!-- 材料类型节点：在哪个类型下上传就归到哪个类型，不需要再选类型 -->
          <div class="attach-upload__group-head">
            <a class="attach-upload__group-toggle" @click="toggle(group.key)">
              <a-icon :type="isExpanded(group.key) ? 'down' : 'right'" />
              <a-icon :type="isExpanded(group.key) ? 'folder-open' : 'folder'" class="attach-upload__folder" />
              <span class="attach-upload__group-name">{{ group.fileTypeName }}</span>
            </a>
            <span class="attach-upload__group-badge">
              {{ group.fileCount }} 个 / {{ formatSize(group.totalSize) }}
            </span>
            <!--
              ★ 两个独立的触发点，对应两种选择器：
                · 选择文件：普通多选文件框 —— **不会**触发浏览器「是否上传文件夹」的确认弹窗
                · 选择文件夹：加 webkitdirectory，拿到的是一整个目录
              一个输入框加不加 webkitdirectory 是二选一的（加了就只能选目录），
              而用户两种都要用，所以要两个入口。
            -->
            <span class="attach-upload__group-ops">
              <a-button
                size="small"
                icon="file-add"
                :disabled="busy"
                :title="`选择文件上传到「${group.fileTypeName}」`"
                @click="pickFiles('file', group)">选择文件</a-button>
              <a-button
                size="small"
                icon="folder-open"
                :disabled="busy"
                :title="`选择文件夹上传到「${group.fileTypeName}」（内部文件会转到该类型下）`"
                @click="pickFiles('folder', group)">选择文件夹</a-button>
            </span>
          </div>

          <!-- 正在传这个类型时的整批进度 -->
          <div v-if="uploadingType === group.fileType" class="attach-upload__progress">
            <div class="attach-upload__track" aria-hidden="true">
              <i class="attach-upload__fill" :style="{ width: percent + '%' }" />
            </div>
            <span class="attach-upload__percent">{{ percent }}%</span>
          </div>

          <!--
            该类型下的文件：**已有文件与刚上传的合并成一份列表**。
            ★ 合并是必须的：分开渲染时同一条附件会出现两次
              （服务端刷新回来一次、「刚刚上传」本地记一次），
              而且两处的体积来源不同（服务端存的字节 vs 浏览器报的 file.size），
              显示出来就是「两个文件、大小还不一样」。
              合并规则见 visibleFilesOf：按 id 去重，服务端记录优先。
          -->
          <ul v-if="isExpanded(group.key)" class="attach-upload__files">
            <li
              v-for="file in visibleFilesOf(group)"
              :key="file.id || file.key"
              class="attach-upload__file"
              :class="{ 'is-new': file.local }">
              <a-icon
                :type="file.local ? 'check-circle' : 'file-text'"
                class="attach-upload__file-icon"
                :class="{ 'is-new': file.local }" />
              <span class="attach-upload__file-name" :title="file.fileName">{{ file.fileName }}</span>
              <span class="attach-upload__file-size">{{ file.readableSize || formatSize(file.fileSize) }}</span>
              <span class="attach-upload__file-tag" :class="{ 'is-new': file.local }">
                {{ file.local ? '刚刚上传' : '已上传' }}
              </span>
            </li>
            <li v-if="!visibleFilesOf(group).length" class="attach-upload__file is-empty">
              <span>该类型还没有文件</span>
            </li>
          </ul>
        </div>

        <p v-if="!groups.length" class="attach-upload__placeholder">
          该对象还没有任何附件；上传第一份文件后，这里会按材料类型列出目录
        </p>
      </a-spin>
    </div>

    <!-- ============ 反馈 ============ -->
    <a-alert
      v-if="errorMessage"
      class="attach-upload__alert"
      type="error"
      show-icon
      :message="errorMessage" />
    <a-alert
      v-if="doneCount"
      class="attach-upload__alert"
      type="success"
      show-icon
      :message="`本次已上传 ${doneCount} 个文件`" />

    <div class="attach-upload__foot">
      <span class="attach-upload__foot-hint">
        可以选单个文件，也可以直接选整个文件夹；文件夹里的文件会转到对应材料类型下
      </span>
      <a-button type="primary" @click="handleClose">{{ doneCount ? '完成' : '关闭' }}</a-button>
    </div>

    <!--
      ★ 两个原生输入对应两种选择器，而不是一个：
        · fileInput  普通多选文件框（**不加** webkitdirectory）→ 不触发浏览器
                     「是否将 N 个文件上传到此站点？」的确认弹窗
        · dirInput   加 webkitdirectory → 一次拿到整个目录（含各层级）
      一个输入框加不加 webkitdirectory 是二选一的：加了就只能选目录。
      用户两种都要用，所以必须有两个入口、两个输入。
      ★ 用 clip 而不是 display:none：display:none 的 input 在部分浏览器里
        无法被 .click() 唤起选择框。
    -->
    <input
      ref="fileInput"
      class="attach-upload__input"
      type="file"
      multiple
      :accept="acceptAttr"
      aria-hidden="true"
      tabindex="-1"
      @change="handleFilesPicked($event, 'file')"
      @cancel="handlePickerCancel" />
    <input
      ref="dirInput"
      class="attach-upload__input"
      type="file"
      multiple
      webkitdirectory
      aria-hidden="true"
      tabindex="-1"
      @change="handleFilesPicked($event, 'folder')"
      @cancel="handlePickerCancel" />
  </a-modal>
</template>

<script>
  import {
    buildUploadBiz,
    queryAllowedTypes,
    queryAttachmentTree,
    saveAttachmentMeta,
    uploadFileBytes
  } from '@/api/land/attachment'
  import { BIZ_TYPE_REQUIRED_OPTIONS } from '@/api/land/dataRecycle'
  import { queryLandOptions, searchFacilityOptions } from '@/api/land/landData'
  import { queryFacilityByLand, queryProcessList } from '@/api/land/facilityAdmin'

  /** 允许的扩展名（与后端白名单同源口径；这里只用于文件框的 accept 提示与前端预校验） */
  const ALLOWED_EXT = [
    'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
    'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv',
    // WPS 文字 / 表格 / 演示：后端按 office 归口交 kkFileView 预览，上传侧不能再拦住
    'wps', 'wpt', 'et', 'ett', 'dps', 'dpt', 'ofd',
    'rar', 'zip', '7z',
    'dwg', 'dxf', 'shp', 'dbf', 'shx', 'prj', 'kml', 'kmz',
    'mp4', 'avi', 'mov', 'wmv'
  ]
  const MAX_SIZE_MB = 200
  /** 单次选择的文件数上限：上传是串行的，几千个文件会让用户以为界面卡死 */
  const MAX_FILES = 300

  /** 取小写扩展名（服务端也会兜底推导，这里显式给出，避免库里出现 "JPG" 与 "jpg" 两种写法） */
  function extractExt (fileName) {
    const name = fileName || ''
    const index = name.lastIndexOf('.')
    return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
  }

  /**
   * 本地兜底的材料类型清单，**按归属类型给对应那套**（唯一基准：P1-08 报告图 3）。
   *
   * ★ 兜底也要分两套：宗地 4 类与配套 13 类是不同的清单，而且两套的 01~04 重号
   *   （宗地 01=土地整理计划、配套 01=核定用地与地籍调查），
   *   合并成一份平铺下拉会出现两条 value='01'，选中的名字永远错一半。
   * ★ 与后端 `LandAttachmentServiceImpl` 的白名单逐字一致；环节进度跟随挂靠侧
   *   （环节挂在配套项目下），所以也用配套那 13 类。
   */
  const LAND_TYPE_FALLBACK = [
    { value: '01', label: '土地整理计划' },
    { value: '02', label: '配套情况函' },
    { value: '03', label: '配套筹备函' },
    { value: '04', label: '出让宗地图形数据（shp）' }
  ]
  const FACILITY_TYPE_FALLBACK = [
    { value: '01', label: '配套项目核定用地与地籍调查' },
    { value: '02', label: '项建批复' },
    { value: '03', label: '可研批复' },
    { value: '04', label: '初设及概算批复' },
    { value: '05', label: '道路规划' },
    { value: '06', label: '规划工程许可' },
    { value: '07', label: '专业配套方案' },
    { value: '08', label: '专业管理意见' },
    { value: '09', label: '规划用地许可与划拨手续办理' },
    { value: '10', label: '施工许可' },
    { value: '11', label: '不动产登记' },
    { value: '12', label: '竣工文件' },
    { value: '13', label: '移交文件' }
  ]

  function fallbackTypesOf (bizType) {
    // 与后端 typeNamesOf 同一条规则：facility / process 用配套那套，其余（含未选）用宗地那套
    return (bizType === 'facility' || bizType === 'process'
      ? FACILITY_TYPE_FALLBACK
      : LAND_TYPE_FALLBACK).slice()
  }

  export default {
    name: 'AttachmentUploadModal',
    data () {
      return {
        visible: false,
        // ---- 归属 ----
        bizType: undefined,
        bizId: undefined,
        bizKey: '',
        bizOptions: [],
        bizLoading: false,
        processLandId: undefined,
        processFacilityId: undefined,
        processLandOptions: [],
        processFacilityOptions: [],
        bizTypeOptions: BIZ_TYPE_REQUIRED_OPTIONS,
        // ---- 材料类型目录 ----
        /** 材料类型清单（决定树上列哪些类型，含还没有文件的） */
        typeOptions: fallbackTypesOf('facility'),
        /** 该归属对象的材料目录：{ groups:[...], totalFiles, totalSize, typeCount } */
        tree: { groups: [] },
        treeLoading: false,
        /** 本对象是否已经成功取过一次树（决定首次是否显示加载态） */
        treeReady: false,
        /** 收起的材料类型 key（默认全展开，结构一眼可见） */
        collapsed: [],
        // ---- 上传 ----
        /**
         * 已点开系统选择器、但**还没拿到文件**的目标类型（'' = 没有待选）。
         * ★ 为什么要有它：打开文件选择器 ≠ 开始上传。若在点按钮时就置 uploadingType，
         *   用户点「取消」后 change 不触发、也没有任何回调，界面就永远停在「上传中」
         *   并把所有类型的按钮禁用（P0-04 报的就是这个「取消后锁死」）。
         */
        pendingType: '',
        /** 当前正在上传的材料类型码（只有真正拿到文件并通过校验才会被赋值） */
        uploadingType: '',
        batchRunning: false,
        /** 整批进度 0-100：按「已完成文件数 / 本批总数」推进（上传接口没有字节级进度） */
        percent: 0,
        /** 主动中止标记：用户在上传中途关闭弹窗时置真，step 循环据此停下 */
        batchAborted: false,
        justUploaded: {},
        /** 本次会话刚上传成功的附件 id（用于在服务端记录上标「刚刚上传」） */
        recentIds: {},
        doneCount: 0,
        errorMessage: '',
        ALLOWED_EXT
      }
    },
    computed: {
      /**
       * 树上要显示的材料类型：**以材料清单为准，而不是只看已有附件**。
       *
       * ★ 这是与「管理页树」的关键差别：管理页只显示有文件的类型（空目录不显示），
       *   而上传弹窗必须把**所有类型都列出来**，否则用户没法给「还没有文件的类型」
       *   传第一份文件 —— 而那恰恰是上传弹窗最常见的用途。
       */
      groups () {
        const treeGroups = (this.tree && this.tree.groups) || []
        const byCode = {}
        treeGroups.forEach(group => { byCode[group.fileType] = group })
        const listed = (this.typeOptions || []).map(option => {
          const hit = byCode[option.value]
          if (hit) {
            return hit
          }
          return {
            key: 'empty:' + option.value,
            fileType: option.value,
            fileTypeName: option.label,
            files: [],
            fileCount: 0,
            totalSize: 0
          }
        })
        // ★ 清单之外的码值（迁移遗留的 99「其他（历史）」）追加在末尾：
        //   不追加的话，历史附件在这个弹窗里「凭空消失」，用户会以为文件丢了。
        //   它们放在最后，与后端 buildTree 的处置一致，不影响现行目录顺序。
        const listedCodes = {}
        listed.forEach(group => { listedCodes[group.fileType] = true })
        treeGroups.forEach(group => {
          if (!listedCodes[group.fileType]) {
            listed.push(group)
          }
        })
        return listed
      },
      treeHint () {
        if (!this.bizId) {
          return '选好归属对象后，这里会按材料类型列出目录'
        }
        const total = Number((this.tree && this.tree.totalFiles) || 0)
        return total > 0
          ? `该对象已有 ${total} 个附件，按材料类型分布如下`
          : '该对象还没有附件，可在任意材料类型下上传第一份'
      },
      bizPlaceholder () {
        if (!this.bizType) return '请先选择归属类型'
        if (this.bizType === 'process') return '请输入或选择环节'
        return this.bizType === 'land' ? '输入编号 / 地块名称搜索' : '输入配套项目名称搜索'
      },
      acceptAttr () {
        return ALLOWED_EXT.map(ext => '.' + ext).join(',')
      },
      /** 是否正在上传（节点上的两个按钮据此禁用，避免中途换类型） */
      busy () {
        return !!this.uploadingType
      }
    },
    beforeDestroy () {
      // 弹窗被父组件销毁时摘掉 window 上的兜底监听：它是全局的，留着会指向已销毁的实例
      this.clearCancelWatch()
    },
    methods: {
      formatSize (bytes) {
        const size = Number(bytes || 0)
        if (size <= 0) return '0 B'
        const units = ['B', 'KB', 'MB', 'GB']
        let value = size
        let unit = 0
        while (value >= 1024 && unit < units.length - 1) {
          value /= 1024
          unit += 1
        }
        return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
      },

      /* ---------------- 对外入口 ---------------- */

      /**
       * @param {object} [preset] 预置 { bizType, bizId, bizKey }
       * ★ 不再需要 fileType：材料类型由「点哪个类型的上传」决定
       */
      open (preset) {
        const record = preset || {}
        this.reset()
        if (record.bizType) {
          this.bizType = record.bizType
        }
        const presetId = record.bizId || record.processId || record.progressId
        if (presetId) {
          this.bizId = presetId
          this.bizKey = record.bizKey || record.lcName || ''
          // 预置的业务对象不在远程搜索结果里，补一个选项让下拉能显示出来
          if (this.bizKey) {
            this.bizOptions = this.bizType === 'process'
              ? [{ progressId: presetId, lcName: this.bizKey, stageName: '' }]
              : [{ id: presetId, crzdbh: this.bizKey, dkmc: '', ptxmmc: this.bizKey }]
          }
        }
        if (this.bizType === 'process') {
          this.processLandId = record.landId
          this.processFacilityId = record.ptId || record.facilityId
          this.loadProcessLandOptions(record.crzdbh || '')
          if (record.crzdbh) this.loadProcessFacilities(record.crzdbh)
          if (this.processFacilityId) this.loadProcessOptions()
        }
        this.visible = true
        this.loadTypes()
        if (this.bizId) {
          this.loadTree()
        }
      },

      reset () {
        this.bizId = undefined
        this.bizKey = ''
        this.bizOptions = []
        this.processLandId = undefined
        this.processFacilityId = undefined
        this.processLandOptions = []
        this.processFacilityOptions = []
        this.tree = { groups: [] }
        this.treeReady = false
        this.collapsed = []
        this.resetPickState()
        this.batchAborted = false
        this.justUploaded = {}
        this.recentIds = {}
        this.doneCount = 0
        this.errorMessage = ''
      },

      handleClose () {
        // 上传中途关闭 = 主动中止：让 step 循环在当前的这一份传完后停下并复位，
        // 否则弹窗关了、uploadingType 还挂着，下次打开会看到「幽灵上传中」。
        if (this.batchRunning) {
          this.batchAborted = true
        }
        this.visible = false
        if (this.doneCount > 0) {
          this.$emit('ok')
        }
      },

      /* ---------------- 材料类型清单 ---------------- */

      loadTypes () {
        const bizType = this.bizType || 'facility'
        this.typeOptions = fallbackTypesOf(bizType)
        queryAllowedTypes(bizType).then(res => {
          if (res && res.success && res.result && res.result.length) {
            // 后端给的是 {value, text}（与字典同源），统一成 {value,label}
            this.typeOptions = res.result.map(item => ({
              value: item.value,
              label: item.text || item.label
            }))
          }
        }).catch(() => {
          // 接口不可用时保留本地兜底清单（按归属分两套，与字典逐字一致）
        })
      },

      /* ---------------- 业务对象远程搜索 ---------------- */

      handleBizTypeChange () {
        // 换类型后原来选中的业务对象已不属于当前类型，必须清掉
        this.bizId = undefined
        this.bizKey = ''
        this.bizOptions = []
        this.tree = { groups: [] }
        this.treeReady = false
        this.justUploaded = {}
        this.recentIds = {}
        // 材料类型清单要跟着换（宗地 4 类 / 配套 13 类），上传态一并复位
        this.resetPickState()
        this.processLandId = undefined
        this.processFacilityId = undefined
        this.processLandOptions = []
        this.processFacilityOptions = []
        this.loadTypes()
        if (this.bizType === 'process') this.loadProcessLandOptions('')
      },

      loadProcessLandOptions (keyword) {
        this.bizLoading = true
        return queryLandOptions({ keyword, limit: 50 }).then(res => {
          this.processLandOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.processLandOptions = []
        }).finally(() => {
          this.bizLoading = false
        })
      },

      handleProcessLandChange (value) {
        const hit = this.processLandOptions.filter(item => item.id === value)[0]
        this.processFacilityId = undefined
        this.processFacilityOptions = []
        this.bizId = undefined
        this.bizKey = ''
        this.bizOptions = []
        this.tree = { groups: [] }
        this.treeReady = false
        if (hit && hit.crzdbh) this.loadProcessFacilities(hit.crzdbh)
      },

      loadProcessFacilities (crzdbh) {
        return queryFacilityByLand(crzdbh).then(res => {
          this.processFacilityOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.processFacilityOptions = []
        })
      },

      handleProcessFacilityChange () {
        this.bizId = undefined
        this.bizKey = ''
        this.bizOptions = []
        this.tree = { groups: [] }
        this.treeReady = false
        this.loadProcessOptions()
      },

      loadProcessOptions () {
        if (!this.processFacilityId) {
          this.bizOptions = []
          return Promise.resolve()
        }
        this.bizLoading = true
        return queryProcessList(this.processFacilityId).then(res => {
          this.bizOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.bizOptions = []
        }).finally(() => {
          this.bizLoading = false
        })
      },

      processOptionValue (item) {
        return this.bizType === 'process' ? (item.progressId || `unregistered:${item.lcId}`) : item.id
      },

      handleBizSearch (keyword) {
        this.bizOptions = []
        if (!this.bizType || this.bizType === 'process' || !keyword) {
          return
        }
        this.bizLoading = true
        const fetcher = this.bizType === 'land' ? queryLandOptions : searchFacilityOptions
        fetcher({ keyword, limit: 50 }).then(res => {
          this.bizOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.bizOptions = []
        }).finally(() => {
          this.bizLoading = false
        })
      },

      /** 选中归属对象：记下可读键，并**立刻加载该对象的材料目录树** */
      handleBizChange (value) {
        this.errorMessage = ''
        if (!value) {
          this.bizId = undefined
          this.bizKey = ''
          this.tree = { groups: [] }
          this.treeReady = false
          return
        }
        const hit = this.bizOptions.filter(item => this.processOptionValue(item) === value)[0]
        this.bizId = value
        // 环节也必须保存真实进度表 id，lcId 仅作未登记行的禁用占位。
        this.bizKey = hit
          ? (this.bizType === 'process' ? hit.lcName : (this.bizType === 'facility' ? hit.ptxmmc : hit.crzdbh))
          : ''
        this.tree = { groups: [] }
        this.treeReady = false
        this.justUploaded = {}
        this.recentIds = {}
        this.loadTree()
      },

      optionLabel (item) {
        if (this.bizType === 'process' && (item.lcId || item.progressId)) {
          return `${item.stageName || ''} / ${item.lcName || ''}${item.progressId ? '' : '（未登记进度，请先保存）'}`
        }
        if (item.ptxmmc) {
          return item.ptxmmc + (item.crzdbh ? `（${item.crzdbh}）` : '')
        }
        return (item.crzdbh || '') + (item.dkmc ? `（${item.dkmc}）` : '')
      },

      /* ---------------- 材料目录树 ---------------- */

      /**
       * 加载该归属对象的材料目录（含已有文件）。
       *
       * @param {boolean} [silent] true = 后台静默刷新：不摆 loading、不清空列表。
       *
       * ★ 为什么必须有 silent：上传成功后每落库一份就要刷新一次树，
       *   如果每次都走「loading 遮罩 → 请求 → 重建」，传一个多文件批次时
       *   界面会连续闪烁十几次。静默刷新只替换数据，结构原地更新。
       */
      loadTree (silent) {
        if (!this.bizId) {
          return
        }
        if (!silent) {
          this.treeLoading = true
        }
        const bizId = this.bizId
        return queryAttachmentTree(this.bizType, bizId).then(res => {
          // 请求期间用户可能已经换了归属对象：丢弃过期响应
          if (bizId !== this.bizId) {
            return
          }
          this.tree = (res && res.success && res.result) ? res.result : { groups: [] }
          this.treeReady = true
        }).catch(() => {
          if (bizId === this.bizId) {
            this.tree = { groups: [] }
            this.treeReady = true
          }
        }).finally(() => {
          if (!silent) {
            this.treeLoading = false
          }
        })
      },

      isExpanded (key) {
        return this.collapsed.indexOf(key) < 0
      },

      toggle (key) {
        const index = this.collapsed.indexOf(key)
        if (index > -1) {
          this.collapsed.splice(index, 1)
        } else {
          this.collapsed.push(key)
        }
      },

      /* ---------------- 在某个材料类型下上传 ---------------- */

      /**
       * 点某个类型的「选择文件 / 选择文件夹」：记下目标类型，唤起对应的选择器。
       * ★ 用户不需要选材料类型 —— 点哪个类型就归到哪个类型。
       *
       * ★ 打开选择器**不等于**开始上传（P0-04 的要害）：
       *   这里只记 pendingType，uploadingType/batchRunning/percent 都不动。
       *   真正进入上传态发生在 handleFilesPicked 拿到文件、校验通过、开始串行上传之后
       *   （见 uploadBatch 开头）。这样用户点「取消」时界面本来就没被锁住。
       */
      pickFiles (mode, group) {
        if (this.busy) return
        this.pendingType = group.fileType
        this.errorMessage = ''
        const input = mode === 'folder' ? this.$refs.dirInput : this.$refs.fileInput
        if (!input) {
          this.resetPickState()
          return
        }
        this.armPickerCancel(group.fileType)
        this.$nextTick(() => {
          // 每次唤起前清空 value：否则「取消后再选同一个文件/同一个文件夹」不会触发 change，
          // 表现就是点了没反应（P0-04 的第二个症状）
          input.value = ''
          input.click()
        })
      },

      /**
       * 兜底监听「选择器被取消」。
       *
       * ★ 为什么需要兜底：原生 input 的 cancel 事件只有较新版本 Chromium 才派发，
       *   老版本取消时**什么事件都没有**（change 不触发、focus 不一定有）。
       *   所以两条腿都要有：模板上的 @cancel（精确）+ window focus（兜底）。
       *   延时 400ms 是为了让 change 先到达 —— 正常选完文件时 change 会同步执行并
       *   清掉 pendingType，这个定时器醒来就什么都不做。
       */
      armPickerCancel (typeCode) {
        this.clearCancelWatch()
        this._cancelFocusHandler = () => {
          this._cancelTimer = window.setTimeout(() => {
            if (!this.batchRunning && !this.uploadingType && this.pendingType === typeCode) {
              this.resetPickState()
            }
          }, 400)
        }
        window.addEventListener('focus', this._cancelFocusHandler)
      },

      clearCancelWatch () {
        if (this._cancelFocusHandler) {
          window.removeEventListener('focus', this._cancelFocusHandler)
          this._cancelFocusHandler = null
        }
        if (this._cancelTimer) {
          window.clearTimeout(this._cancelTimer)
          this._cancelTimer = null
        }
      },

      /**
       * 上传态的唯一复位入口。
       *
       * ★ 为什么必须收口成一个方法：正常完成 / 全部被校验拒绝 / 网络失败 /
       *   主动中止 / 用户取消，这五条路径以前各写一遍赋值，漏一处就是
       *   「某个类型的按钮一直灰着」这种要重启页面才能恢复的故障。
       *   现在五处都调它，清 pendingType/uploadingType/batchRunning/percent 四件套。
       */
      resetPickState () {
        this.clearCancelWatch()
        this.pendingType = ''
        this.uploadingType = ''
        this.batchRunning = false
        this.percent = 0
      },

      /** 原生 input 的 cancel 事件（选择器被取消）：有它就不必等 focus 兜底 */
      handlePickerCancel () {
        if (!this.batchRunning) {
          this.resetPickState()
        }
      },

      /** 原生 change：校验 → 串行上传（mode 用于区分是哪个输入触发的） */
      handleFilesPicked (event, mode) {
        const fallback = mode === 'folder' ? this.$refs.dirInput : this.$refs.fileInput
        const input = event && event.target ? event.target : fallback
        const selected = input && input.files ? Array.prototype.slice.call(input.files) : []
        // 已经拿到（或明确没有）文件：focus 兜底不再需要
        this.clearCancelWatch()
        const typeCode = this.pendingType
        if (!selected.length) {
          // 取消选择（change 有时会以空 files 的形式到达）
          this.resetPickState()
          return
        }
        if (!typeCode) {
          // 没有目标类型说明状态已错乱，宁可复位让用户重来，也不要传到一个猜的类型下
          this.resetPickState()
          this.errorMessage = '未能确定材料类型，请重新点击该类型下的上传按钮'
          return
        }
        if (selected.length > MAX_FILES) {
          this.errorMessage = `所选文件夹含 ${selected.length} 个文件，超过一次最多 ${MAX_FILES} 个的限制，请分批上传`
          this.resetPickState()
          return
        }
        const accepted = []
        const rejected = []
        selected.forEach(file => {
          const reason = this.validateFile(file)
          if (reason) {
            rejected.push(`${file.name}（${reason}）`)
          } else {
            accepted.push(file)
          }
        })
        if (rejected.length) {
          this.errorMessage = `以下 ${rejected.length} 个文件未通过校验：${rejected.slice(0, 5).join('；')}` +
            (rejected.length > 5 ? ' 等' : '')
        }
        if (!accepted.length) {
          // 全部被校验拒绝：也走统一复位（错误提示保留，用户能看见原因）
          this.resetPickState()
          return
        }
        this.uploadBatch(accepted, typeCode)
      },

      validateFile (file) {
        const ext = extractExt(file.name)
        if (ALLOWED_EXT.length && ALLOWED_EXT.indexOf(ext) < 0) {
          return '不支持的格式'
        }
        if (file.size > MAX_SIZE_MB * 1024 * 1024) {
          return `超过 ${MAX_SIZE_MB}MB`
        }
        return ''
      },

      /**
       * 串行上传整批（单份失败不中断）。
       * ★ 为什么串行：每份要跑两步（落盘 + 登记），并发会让进度与失败定位都不可解释。
       * ★ 为什么失败不中断：用户选的可能是一整个文件夹，因一个文件格式不对就丢弃其余，
       *   代价太大；失败的汇总在末尾提示。
       * ★ 进入上传态的位置就在这里：文件已经拿到、校验已通过，才置 uploadingType/batchRunning。
       *
       * @param {File[]} files    通过校验的文件
       * @param {string} typeCode 目标材料类型（点哪个类型就归哪个类型）
       */
      uploadBatch (files, typeCode) {
        const pending = files.slice()
        const total = pending.length
        const failed = []
        let ok = 0
        this.uploadingType = typeCode
        this.batchRunning = true
        this.pendingType = ''
        this.percent = 0
        const step = () => {
          // 主动中止（用户在上传中途点了关闭）：已传成功的照常计数，余下的不再发请求
          if (this.batchAborted) {
            this.batchAborted = false
            this.resetPickState()
            this.doneCount += ok
            if (ok > 0) {
              this.$emit('ok')
            }
            this.loadTree(true)
            return
          }
          const file = pending.shift()
          if (!file) {
            // 正常完成（可能有部分失败）
            this.resetPickState()
            this.doneCount += ok
            if (failed.length) {
              this.errorMessage = `成功 ${ok} 个，失败 ${failed.length} 个：` +
                failed.slice(0, 5).join('；') + (failed.length > 5 ? ' 等' : '')
            } else if (ok > 0) {
              this.errorMessage = ''
              this.$message.success(`已上传 ${ok} 个文件`)
              this.$emit('ok')
            }
            // ★ 静默刷新（不摆 loading），否则每传一份就整棵树重建一次 —— 界面闪烁
            this.loadTree(true)
            return
          }
          this.uploadOne(file, typeCode).then(success => {
            if (success) {
              ok += 1
            } else {
              failed.push(file.name)
            }
            // 进度按「已完成份数 / 本批总数」推进：上传接口没有字节级进度可给
            this.percent = Math.round(((ok + failed.length) / total) * 100)
          }).then(step)
        }
        step()
      },

      /** 单份文件：落盘 → 登记，并把成功的那条就地显示在对应类型下 */
      uploadOne (file, typeCode) {
        return uploadFileBytes(file, buildUploadBiz(this.bizType)).then(uploaded => {
          return this.saveMeta(file, typeCode, uploaded.storePath).then(record => {
            if (record) {
              this.upsertRecent(typeCode, record)
            }
            return !!record
          })
        }).catch(() => false)
      },

      /**
       * 第 2 步：登记（材料类型由「点哪个类型」决定）。
       * @returns {Promise<object|null>} 落库后的附件记录
       */
      saveMeta (file, typeCode, storePath) {
        const meta = {
          bizType: this.bizType,
          bizId: this.bizId,
          bizKey: this.bizKey,
          fileType: typeCode,
          fileName: file.name,
          fileSize: file.size,
          fileExt: extractExt(file.name),
          contentType: file.type || '',
          storePath,
          remark: ''
        }
        return saveAttachmentMeta(meta).then(res => {
          if (!res || !res.success) {
            throw new Error((res && res.message) || '附件登记失败')
          }
          return res.result || null
        })
      },

      /**
       * 某个类型下要显示的文件：**已有文件与刚上传的合并成一份**。
       *
       * ★ 为什么要合并：分开渲染会让同一条附件出现两次 ——
       *   服务端刷新回来一次、「刚刚上传」本地记一次；
       *   而且两处的体积来源不同（服务端存的字节 vs 浏览器报的 file.size），
       *   显示出来就是「两个文件、大小还不一样」。
       * ★ 去重按 id：saveAttachmentMeta 返回的就是落库后的记录（带 id），
       *   合并时以服务端那份为准，只保留「刚上传」标记用于显示绿色。
       */
      visibleFilesOf (group) {
        const serverFiles = (group && group.files) || []
        const serverIds = {}
        serverFiles.forEach(file => { serverIds[file.id] = true })
        const merged = serverFiles.map(file => Object.assign({}, file, {
          local: !!this.recentIds[file.id]
        }))
        const pending = this.justUploaded[group && group.fileType] || []
        pending.forEach(item => {
          if (item.id && serverIds[item.id]) {
            return
          }
          merged.push(item)
        })
        return merged
      },

      /**
       * 记下「刚上传」的一条（幂等：同一条重复回调不会变成两条）。
       * @param {string} typeCode 材料类型码
       * @param {object} record   落库后的附件记录（含 id 与服务端算出的字节数）
       */
      upsertRecent (typeCode, record) {
        if (!typeCode || !record) {
          return
        }
        const next = Object.assign({}, this.justUploaded)
        const list = (next[typeCode] || []).filter(item => item.id !== record.id)
        list.push(Object.assign({}, record, { local: true }))
        next[typeCode] = list
        this.justUploaded = next
        if (record.id) {
          this.recentIds = Object.assign({}, this.recentIds, { [record.id]: true })
        }
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;
  @primary: #2563eb;

  /*
   * 设计口径（来自 ui-ux-pro-max 的企业级密集面板建议）：
   *   · 目录用主色蓝、文件用中性灰 —— 一眼分得清「容器」与「内容」
   *   · 8px 间距节奏；hover 150-250ms；不做多余装饰与重阴影
   */
  .attach-upload {
    &__head {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px 20px;
      margin-bottom: 16px;
    }

    &__field {
      display: flex;
      flex-direction: column;
      gap: 6px;
      min-width: 0;
    }

    &__label {
      font-size: 13px;
      color: #0f172a;

      i {
        margin-left: 4px;
        color: #dc2626;
        font-style: normal;
      }
    }

    &__tree {
      padding: 12px;
      background: #fafbfd;
      border: 1px solid @border-color;
      border-radius: 4px;
      // 材料类型较多时这一段自己滚动，不把弹窗撑高
      max-height: 46vh;
      overflow-y: auto;
    }

    &__tree-head {
      display: flex;
      align-items: baseline;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 8px;
    }

    &__tree-title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    &__tree-hint {
      font-size: 12px;
      color: @text-weak;
    }

    &__placeholder {
      margin: 0;
      padding: 24px 0;
      color: @text-weak;
      font-size: 12px;
      text-align: center;
    }

    /* ---------- 材料类型节点 ---------- */
    &__group {
      border-bottom: 1px solid #f0f0f0;

      &:last-of-type {
        border-bottom: 0;
      }

      &.is-uploading {
        background: rgba(37, 99, 235, 0.05);
        border-radius: 4px;
      }
    }

    &__group-head {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 8px;
      border-radius: 4px;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }
    }

    &__group-toggle {
      flex: 1 1 auto;
      min-width: 0;
      color: #0f172a;
      font-weight: 500;

      > .anticon + .anticon {
        margin-left: 6px;
      }
    }

    &__folder {
      // 目录用主色：与下面的文件行形成「容器 / 内容」的层级差
      color: @primary;
    }

    &__group-name {
      margin-left: 4px;
    }

    &__group-badge {
      flex: 0 0 auto;
      color: @text-weak;
      font-size: 12px;
    }

    /* 两个上传入口：选择文件 / 选择文件夹 */
    &__group-ops {
      flex: 0 0 auto;
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }

    /* 当前在传类型的整批进度 */
    &__progress {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 0 8px 8px 32px;
    }

    &__track {
      flex: 1 1 auto;
      height: 4px;
      overflow: hidden;
      background: #eef1f5;
      border-radius: 2px;
    }

    &__fill {
      display: block;
      height: 100%;
      background: @primary;
      border-radius: 2px;
      transition: width 0.2s;
    }

    &__percent {
      flex: 0 0 auto;
      color: @text-weak;
      font-size: 12px;
    }

    /* ---------- 文件行 ---------- */
    &__files {
      margin: 0;
      padding: 0 0 8px;
      list-style: none;
    }

    &__file {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 3px 8px 3px 32px;
      font-size: 12px;

      &.is-new {
        color: #16a34a;
      }

      &.is-empty {
        color: @text-weak;
      }
    }

    &__file-icon {
      // 文件用中性灰，把主色留给目录
      color: @text-weak;

      &.is-new {
        color: #16a34a;
      }
    }

    &__file-name {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #475569;
    }

    &__file-size {
      flex: 0 0 auto;
      color: @text-weak;
    }

    &__file-tag {
      flex: 0 0 auto;
      padding: 0 6px;
      font-size: 12px;
      color: @text-weak;
      background: #f0f2f5;
      border-radius: 10px;

      &.is-new {
        color: #16a34a;
        background: rgba(22, 163, 74, 0.1);
      }
    }

    /* ---------- 隐藏的文件输入 ---------- */
    &__input {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    &__alert {
      margin-top: 12px;
    }

    &__foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid @border-color;
    }

    &__foot-hint {
      flex: 1 1 320px;
      min-width: 0;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }
  }
</style>
