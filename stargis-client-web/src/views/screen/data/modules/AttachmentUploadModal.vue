<template>
  <!--
    AttachmentUploadModal 附件上传
    --------------------------------
    ★ 界面结构（2026-10-10 按要求重做）：
      第一段：选归属 —— 归属类型 + 所属对象（两个下拉并排）
      第二段：**材料类型目录树** —— 选完归属对象后立刻展示，
              每个材料类型就是一个目录节点，节点右侧有「上传」按钮，
              节点下面是该类型已有的文件。

      用户的操作只有两件事：① 选归属；② 在某个类型上点「上传」。
      **不需要选材料类型** —— 点哪个类型的上传，文件就归到那个类型。

    ★ 为什么树要在弹窗里而不是只放在管理页：
      用户传附件时心里想的是「给这个项目的这一类材料补文件」，
      在弹窗里直接看到「这个项目有哪些材料、哪类还空着」才顺；
      让他先关掉弹窗去管理页看结构、再回来选类型，是把一件事拆成两处。

    ★ 为什么树里要显示**已有文件**：
      上传前先看到已有什么，才能判断该补哪一类；否则用户会重复上传。

    ★ 为什么选文件夹上传时内部文件**拉平**到该类型目录：
      旧系统的存储结构就只有「项目/材料类型/文件」两层，没有更深层级；
      按目录层级重建会造出旧系统从来没有的结构，历史对照也就无从谈起。

    ★ 上传分两步（职责必须分清，这是旧系统出问题最多的地方）：
      第一步（文件字节）：ScreenUpload → POST `/sys/common/upload` 落盘。
      第二步（元数据）：本弹窗 → POST `/land/data/attachment/save` 落库。

    公开方法：
      open(preset)  preset 可预置 { bizType, bizId, bizKey, bizName }
    事件：
      ok  至少有一个文件保存成功后抛出，父组件据此刷新列表
  -->
  <screen-modal
    :visible.sync="visible"
    title="上传附件"
    :width="920"
    :show-footer="false"
    :body-max-height="'calc(100vh - 190px)'"
    @cancel="handleClose"
  >
    <div class="attachment-upload">
      <!-- ============ 第一段：选择归属 ============ -->
      <section class="attachment-upload__section">
        <div class="attachment-upload__grid">
          <screen-field label="归属类型" required :error="errors.bizType" label-width="88px">
            <screen-select
              v-model="form.bizType"
              :options="bizTypeOptions"
              :invalid="!!errors.bizType"
              placeholder="请选择"
              aria-label="归属类型"
              @change="handleBizTypeChange"
            />
          </screen-field>

          <screen-field label="所属对象" required :error="errors.bizId" label-width="88px">
            <screen-select
              v-model="form.bizId"
              :options="objectOptions"
              :invalid="!!errors.bizId"
              :disabled="!form.bizType"
              :searchable="true"
              :filter-local="false"
              :placeholder="objectPlaceholder"
              :empty-text="objectSearching ? '查询中…' : '没有匹配的记录'"
              aria-label="所属对象"
              @search="handleObjectSearch"
              @change="handleObjectChange"
            />
            <!-- 已带出归属、但下拉候选里还没有这条记录时（从别的页面跳进来），
                 把名称显示出来，用户才知道这些附件会挂到哪里去 -->
            <p v-if="pickedName && !objectOptionShowsPicked" class="attachment-upload__picked">
              已选择：<b>{{ pickedName }}</b>
            </p>
          </screen-field>
        </div>
      </section>

      <!-- ============ 第二段：材料类型目录树 ============ -->
      <section class="attachment-upload__section attachment-upload__section--tree">
        <div class="attachment-upload__tree-head">
          <h4 class="attachment-upload__section-title">
            <screen-icon name="folder-open" :size="14" />
            材料目录
          </h4>
          <span class="attachment-upload__tree-hint">
            {{ treeHint }}
          </span>
        </div>

        <!-- 未选归属对象：树没有主体，明确告诉他先选 -->
        <p v-if="!form.bizId" class="attachment-upload__placeholder">
          请先选择归属类型与所属对象，然后在这里按材料类型上传文件
        </p>

        <div v-else-if="treeLoading" class="attachment-upload__placeholder">加载中…</div>

        <template v-else>
          <div
            v-for="group in groups"
            :key="group.key"
            class="attachment-upload__group"
            :class="{ 'is-uploading': uploadingType === group.fileType }"
          >
            <!-- 材料类型节点：点「上传」即归到该类型，不需要再选类型 -->
            <div class="attachment-upload__group-head">
              <button
                type="button"
                class="attachment-upload__group-toggle"
                :aria-expanded="isExpanded(group.key) ? 'true' : 'false'"
                @click="toggle(group.key)"
              >
                <screen-icon :name="isExpanded(group.key) ? 'chevron-down' : 'chevron-right'" :size="12" />
                <screen-icon
                  :name="isExpanded(group.key) ? 'folder-open' : 'folder'"
                  :size="14"
                  class="attachment-upload__folder"
                />
                <span class="attachment-upload__group-name">{{ group.fileTypeName }}</span>
              </button>

              <span class="attachment-upload__group-badge">
                {{ group.fileCount }} 个 · {{ formatSize(group.totalSize) }}
              </span>

              <!--
                ★ 两个独立的触发点，对应两种选择器：
                  · 选择文件：普通多选文件框 —— **不会**触发浏览器「是否上传文件夹」的确认弹窗
                  · 选择文件夹：加 webkitdirectory，拿到的是一整个目录
                为什么要分成两个按钮而不是一个：一个输入框加不加 webkitdirectory
                是二选一的（加了就只能选目录），而用户需要两种都能用。
                浏览器那个确认弹窗只有目录模式才会出现，所以把「选文件」单列一个入口
                就是「不想要那个弹窗」的正解。
              -->
              <span class="attachment-upload__group-ops">
                <button
                  type="button"
                  class="attachment-upload__op"
                  :disabled="busy"
                  :title="`选择文件上传到「${group.fileTypeName}」`"
                  @click="triggerPick('file', group)"
                >
                  <screen-icon name="file-plus" :size="13" />
                  选择文件
                </button>
                <button
                  type="button"
                  class="attachment-upload__op"
                  :disabled="busy"
                  :title="`选择文件夹上传到「${group.fileTypeName}」（内部文件会转到该类型下）`"
                  @click="triggerPick('folder', group)"
                >
                  <screen-icon name="folder" :size="13" />
                  选择文件夹
                </button>
              </span>
            </div>

            <!--
              正在传这个类型时，把该实例的进度/校验清单显示在节点下面。
              ★ 进度必须跟着「哪个类型在传」走，否则用户看不出传的是哪一类。
            -->
            <div v-if="uploadingType === group.fileType" class="attachment-upload__progress">
              <div class="attachment-upload__track" aria-hidden="true">
                <i class="attachment-upload__fill" :style="{ width: percent + '%' }" />
              </div>
              <span class="attachment-upload__percent">{{ percent }}%</span>
            </div>
            <ul v-if="rejected.length" class="attachment-upload__rejects">
              <li v-for="(item, index) in rejected" :key="`${item.name}-${index}`">
                {{ item.name }} —— {{ item.reason }}
              </li>
            </ul>

            <!-- 该类型已有的文件（上传前先看到已有什么，避免重复上传） -->
            <ul v-if="isExpanded(group.key)" class="attachment-upload__files">
              <li v-for="file in group.files" :key="file.id" class="attachment-upload__file">
                <screen-icon name="file-text" :size="13" class="attachment-upload__file-icon" />
                <span class="attachment-upload__file-name" :title="file.fileName">{{ file.fileName }}</span>
                <span class="attachment-upload__file-size">
                  {{ file.readableSize || formatSize(file.fileSize) }}
                </span>
                <span class="attachment-upload__file-tag">已上传</span>
              </li>
              <!-- 本次会话刚传上去的（后端已落库，但列表刷新前先就地显示，给即时反馈） -->
              <li v-for="item in justUploaded[group.fileType] || []" :key="item.key" class="attachment-upload__file is-new">
                <screen-icon name="check-circle" :size="13" class="attachment-upload__file-icon is-new" />
                <span class="attachment-upload__file-name" :title="item.fileName">{{ item.fileName }}</span>
                <span class="attachment-upload__file-size">{{ formatSize(item.fileSize) }}</span>
                <span class="attachment-upload__file-tag is-new">刚刚上传</span>
              </li>
              <li v-if="!group.fileCount && !(justUploaded[group.fileType] || []).length" class="attachment-upload__file is-empty">
                <span>该类型还没有文件</span>
              </li>
            </ul>
          </div>

          <p v-if="!groups.length" class="attachment-upload__placeholder">
            该对象还没有任何附件；上传第一份文件后，这里会按材料类型列出目录
          </p>
        </template>
      </section>

      <!-- ============ 结果反馈 ============ -->
      <p v-if="errorMessage" class="attachment-upload__alert is-error" role="alert">
        <screen-icon name="alert-triangle" :size="14" />
        {{ errorMessage }}
      </p>

      <p v-if="doneCount" class="attachment-upload__alert is-success" role="status">
        <screen-icon name="check-circle" :size="14" />
        本次已上传 {{ doneCount }} 个文件
      </p>

      <footer class="attachment-upload__foot">
        <span class="attachment-upload__foot-hint">
          可以选单个文件，也可以直接选整个文件夹；文件夹里的文件会转到对应材料类型下
        </span>
        <screen-button type="primary" @click="handleClose">
          {{ doneCount ? '完成' : '关闭' }}
        </screen-button>
      </footer>
    </div>

    <!--
      ★ 上传控件复用 ScreenUpload，而不是自己写 XMLHttpRequest。
        理由：jeecg 通用上传接口的 multipart 约定（字段名 file、响应里
        storePath 放在 message 而非 result）、令牌头名、进度聚合、abort，
        ScreenUpload 已经踩过一遍且线上在用；自己重写一份等于把这些坑再踩一次。
        这里用它的 trigger 插槽把按钮外观换成树里的小按钮，
        并把它的 bar / 进度 / 校验清单隐藏（改为由本弹窗按类型渲染）。

      ★ 两个实例 = 两种选择器：
        · fileInputRef  普通多选文件框（不加 webkitdirectory）→ 不触发浏览器确认弹窗
        · 目录模式加 webkitdirectory → 一次拿到整个目录
        一个输入框加不加 webkitdirectory 是二选一的，所以要两个实例。
    -->
    <div class="attachment-upload__uploaders" aria-hidden="true">
      <screen-upload
        ref="fileUploader"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :multiple="true"
        :directory="false"
        :allowed-ext="ALLOWED_EXT"
        :max-size-mb="MAX_SIZE_MB"
        button-text="选择文件"
        @success="handleUploaded"
        @reject="handleRejected"
        @error="handleUploadError"
        @progress="handleProgress"
        @uploading-change="handleUploadingChange"
      >
        <template #trigger="{ trigger }">
          <button ref="fileTrigger" type="button" class="attachment-upload__hidden-trigger" @click="trigger">选择文件</button>
        </template>
      </screen-upload>

      <screen-upload
        ref="folderUploader"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :directory="true"
        :max-files="MAX_FILES"
        :allowed-ext="ALLOWED_EXT"
        :max-size-mb="MAX_SIZE_MB"
        button-text="选择文件夹"
        @success="handleUploaded"
        @reject="handleRejected"
        @error="handleUploadError"
        @progress="handleProgress"
        @uploading-change="handleUploadingChange"
      >
        <template #trigger="{ trigger }">
          <button ref="folderTrigger" type="button" class="attachment-upload__hidden-trigger" @click="trigger">选择文件夹</button>
        </template>
      </screen-upload>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenSelect,
  ScreenButton,
  ScreenIcon,
  ScreenUpload
} from '@/components/screen'
import {
  saveAttachment,
  queryAttachmentTree,
  queryAllowedAttachmentTypes,
  attachmentUploadAction,
  attachmentUploadHeaders,
  ATTACHMENT_BIZ_TYPES
} from '@/api/land/attachment'
import { queryLandOptions, searchFacilityOptions } from '@/api/land/landData'
import { buildBizPath, formatSize, dictToOptions, resolveExt } from '../constants'

/**
 * 本地兜底的材料类型清单，**按归属类型给对应那套**。
 * ★ 兜底也要分两套：宗地 5 类与配套 13 类是不同的清单（名称逐字取自旧系统存储目录），
 *   接口一挂就给错清单的话，用户会选到一个根本不存在的类型。
 */
const LAND_TYPE_FALLBACK = [
  { value: '01', text: '土地整理计划' },
  { value: '02', text: '配套方案' },
  { value: '03', text: '配套情况函' },
  { value: '04', text: '配套筹备函' },
  { value: '05', text: '出让宗地图形数据（SHP）' }
]

const FACILITY_TYPE_FALLBACK = [
  { value: '01', text: '项建批复文件' },
  { value: '02', text: '可研批复文件' },
  { value: '03', text: '初设及概算批复文件' },
  { value: '04', text: '道路规划' },
  { value: '05', text: '专业配套方案' },
  { value: '06', text: '施工许可' },
  { value: '07', text: '专业管理意见' },
  { value: '08', text: '配套项目核定用地与地籍调查' },
  { value: '09', text: '规划工程许可' },
  { value: '10', text: '规划用地许可与划拨手续办理' },
  { value: '11', text: '不动产登记' },
  { value: '12', text: '竣工文件' },
  { value: '13', text: '移交文件' }
]

function fallbackTypesOf (bizType) {
  const list = bizType === 'facility' ? FACILITY_TYPE_FALLBACK : LAND_TYPE_FALLBACK
  return list.map(item => ({ value: item.value, label: item.text }))
}

/**
 * 允许的扩展名（小写、不含点）。
 * 与档案模块同一份口径：办公文档 / 图片 / 文本 / 压缩包 / CAD / GIS 矢量 / 视频。
 * 配套附件里 dwg / shp 很常见，必须包含。
 */
const ALLOWED_EXT = [
  'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv', 'ofd',
  'rar', 'zip', '7z',
  'dwg', 'dxf', 'shp', 'dbf', 'shx', 'prj', 'kml', 'kmz',
  'mp4', 'avi', 'mov', 'wmv'
]

const MAX_SIZE_MB = 200
/** 单次选择的文件数上限：上传是串行的，几千个文件会让用户以为界面卡死 */
const MAX_FILES = 300

function emptyForm () {
  return {
    bizType: 'facility',
    bizId: ''
  }
}

export default {
  name: 'AttachmentUploadModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenSelect,
    ScreenButton,
    ScreenIcon,
    ScreenUpload
  },
  data () {
    return {
      visible: false,
      form: emptyForm(),
      errors: {},
      bizTypeOptions: ATTACHMENT_BIZ_TYPES,
      /** 「所属对象」下拉的候选（值=真实 id，label=用户看得懂的名称） */
      objectOptions: [],
      objectSearching: false,
      /** 当前归属对象的显示名称 */
      pickedName: '',
      /** 材料类型目录树：{ groups:[...], totalFiles, totalSize, typeCount } */
      tree: { groups: [] },
      treeLoading: false,
      /** 本地兜底的材料类型（接口不可用时仍能显示出树的结构） */
      typeOptions: fallbackTypesOf('facility'),
      /** 当前正在上传的材料类型码（'' = 没有在上传） */
      uploadingType: '',
      /** 整批进度 0-100（由 ScreenUpload 的 progress 事件给出） */
      percent: 0,
      /** 是否有一批正在跑 */
      batchRunning: false,
      /** 校验未通过的文件（ScreenUpload 内部也有清单，这里汇总到弹窗级别） */
      rejected: [],
      /** 展开的材料类型 key 集合（收起的在里面） */
      collapsed: [],
      /** 本次会话刚上传成功的（按材料类型分组），用于即时反馈 */
      justUploaded: {},
      /** 本次会话成功总数 */
      doneCount: 0,
      errorMessage: '',
      ALLOWED_EXT,
      MAX_SIZE_MB,
      MAX_FILES
    }
  },
  computed: {
    /** 上传子目录：/{归属}/attachment/yyyy/MM */
    bizPath () {
      const module = this.form.bizType === 'land' ? 'land' : this.form.bizType === 'process' ? 'process' : 'facility'
      return buildBizPath(module)
    },
    uploadAction () {
      return attachmentUploadAction()
    },
    /** 每次渲染都重新取：用户长时间开着弹窗时令牌可能已被滑动续期 */
    uploadHeaders () {
      return attachmentUploadHeaders()
    },
    /** 是否正在上传（两个 ScreenUpload 任一个在传都算） */
    busy () {
      return !!this.uploadingType
    },
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
      return (this.typeOptions || []).map(option => {
        const hit = byCode[option.value]
        if (hit) {
          return hit
        }
        // 清单里有、但还没有文件的类型：造一个空节点
        return {
          key: 'empty:' + option.value,
          fileType: option.value,
          fileTypeName: option.label,
          files: [],
          fileCount: 0,
          totalSize: 0
        }
      })
    },
    treeHint () {
      if (!this.form.bizId) {
        return '选好归属对象后，这里会按材料类型列出目录'
      }
      const total = Number((this.tree && this.tree.totalFiles) || 0)
      return total > 0
        ? `该对象已有 ${total} 个附件，按材料类型分布如下`
        : '该对象还没有附件，可在任意材料类型下上传第一份'
    },
    objectPlaceholder () {
      if (!this.form.bizType) return '请先选择归属类型'
      if (this.form.bizType === 'process') return '请输入或选择环节'
      return this.form.bizType === 'land' ? '输入编号或地块名称搜索' : '输入名称搜索'
    },
    objectOptionShowsPicked () {
      if (!this.form.bizId) return false
      return this.objectOptions.some(item => item.value === this.form.bizId)
    }
  },
  methods: {
    formatSize,

    /* ---------------- 对外入口 ---------------- */

    open (preset) {
      const record = preset || {}
      this.form = Object.assign(emptyForm(), {
        bizType: record.bizType || 'facility',
        bizId: record.bizId || ''
      })
      this.errors = {}
      this.tree = { groups: [] }
      this.justUploaded = {}
      this.doneCount = 0
      this.errorMessage = ''
      this.collapsed = []
      this.uploadingType = ''
      this.percent = 0
      this.batchRunning = false
      this.rejected = []
      this.pickedName = record.bizName || record.bizKey || ''
      this.objectOptions = []
      this.visible = true
      // 两个上传控件复位：清队列、清进度、清校验清单
      this.$nextTick(() => {
        if (this.$refs.fileUploader) this.$refs.fileUploader.clear()
        if (this.$refs.folderUploader) this.$refs.folderUploader.clear()
      })
      this.loadTypes()
      if (this.form.bizType) {
        this.loadObjectOptions('')
      }
      // 已带出归属对象：直接展示该对象的材料目录（这是本次改动的核心）
      if (this.form.bizId) {
        this.loadTree()
      }
    },

    handleClose () {
      this.visible = false
      if (this.doneCount > 0) {
        this.$emit('ok')
      }
    },

    /* ---------------- 材料类型清单 ---------------- */

    /**
     * 材料类型清单（决定树上列哪些类型）。
     * ★ 必须带 bizType：宗地与配套是两套不同的清单。
     */
    loadTypes () {
      const bizType = this.form.bizType || 'facility'
      this.typeOptions = fallbackTypesOf(bizType)
      queryAllowedAttachmentTypes(bizType).then((res) => {
        if (res && res.success && res.result && res.result.length) {
          // 后端给的是 {value,text}，统一成 {value,label} 供树渲染
          this.typeOptions = dictToOptions(res.result)
        }
      }).catch(() => {
        // 接口不可用时保留本地兜底清单（它按归属分两套，与字典逐字一致）
      })
    },

    /* ---------------- 所属对象 ---------------- */

    handleBizTypeChange () {
      this.form.bizId = ''
      this.pickedName = ''
      this.objectOptions = []
      this.tree = { groups: [] }
      this.justUploaded = {}
      this.errors = {}
      this.uploadingType = ''
      this.rejected = []
      this.loadTypes()
      this.loadObjectOptions('')
    },

    /**
     * 拉「所属对象」候选：值为真实 id、label 为可读名称。
     * ★ 这是「用户看到名称、系统存 id」的实现点：form.bizId 始终是 id。
     */
    loadObjectOptions (keyword) {
      const type = this.form.bizType
      if (!type || type === 'process') {
        this.objectOptions = []
        return
      }
      this.objectSearching = true
      const request = type === 'land'
        ? queryLandOptions({ keyword, limit: 50 })
        : searchFacilityOptions({ keyword, limit: 50 })
      request.then((res) => {
        const rows = (res && res.success && res.result) ? res.result : []
        const options = rows.map((row) => (type === 'land'
          ? { value: row.id, label: `${row.crzdbh || ''}${row.dkmc ? '　' + row.dkmc : ''}`, raw: row }
          : { value: row.id, label: `${row.ptxmmc || ''}${row.crzdbh ? '（' + row.crzdbh + '）' : ''}`, raw: row }
        ))
        // 已选中的那条若不在结果里就补进去，否则用户会以为选择被清掉了
        if (this.form.bizId && !options.some(item => item.value === this.form.bizId)) {
          options.unshift({ value: this.form.bizId, label: this.pickedName || this.form.bizId, raw: null })
        }
        this.objectOptions = options
      }).catch(() => {
        this.objectOptions = this.form.bizId
          ? [{ value: this.form.bizId, label: this.pickedName || this.form.bizId, raw: null }]
          : []
      }).finally(() => {
        this.objectSearching = false
      })
    },

    handleObjectSearch (keyword) {
      this.loadObjectOptions(String(keyword || '').trim())
    },

    /** 选中归属对象：记下名称，并**立刻加载该对象的材料目录树** */
    handleObjectChange (value, raw) {
      this.errors = Object.assign({}, this.errors, { bizId: '' })
      if (!value) {
        this.pickedName = ''
        this.tree = { groups: [] }
        return
      }
      if (raw && this.form.bizType === 'land') {
        const name = String(raw.dkmc || '').trim()
        const code = String(raw.crzdbh || '').trim()
        this.pickedName = name ? (code ? `${name}（${code}）` : name) : code
      } else if (raw) {
        this.pickedName = `${raw.ptxmmc || ''}${raw.crzdbh ? '（' + raw.crzdbh + '）' : ''}`.trim()
      } else {
        this.pickedName = this.pickedName || value
      }
      this.tree = { groups: [] }
      this.justUploaded = {}
      this.loadTree()
    },

    /* ---------------- 材料目录树 ---------------- */

    /** 加载该归属对象的材料目录（含已有文件） */
    loadTree () {
      if (!this.form.bizId) {
        return
      }
      this.treeLoading = true
      queryAttachmentTree(this.form.bizType, this.form.bizId).then((res) => {
        this.tree = (res && res.success && res.result) ? res.result : { groups: [] }
      }).catch(() => {
        this.tree = { groups: [] }
      }).finally(() => {
        this.treeLoading = false
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
     * ★ 为什么要「先记类型，再异步等 success」：
     *   ScreenUpload 自己维护队列与进度，success 是**逐个文件**抛出的，
     *   它并不知道这些文件属于哪个材料类型 —— 类型是「点哪个按钮」决定的，
     *   所以点的时候记下来，success 时用这个记录去登记。
     *   中途不许换类型（busy 时按钮禁用），避免记错。
     */
    triggerPick (mode, group) {
      this.uploadingType = group.fileType
      this.rejected = []
      this.errorMessage = ''
      const uploader = mode === 'folder' ? this.$refs.folderUploader : this.$refs.fileUploader
      if (!uploader) {
        return
      }
      // ScreenUpload 的公开触发方法：清空 value 后 click，保证再选同一文件仍触发 change
      uploader.handleTriggerClick()
    },

    /** 校验未通过（ScreenUpload 已把清单显示在自己内部，这里汇总到弹窗级别） */
    handleRejected (reason) {
      this.errorMessage = reason || '有文件未通过校验'
    },

    /** 网络层/服务端失败：ScreenUpload 会终止整批 */
    handleUploadError (message) {
      this.batchRunning = false
      this.uploadingType = ''
      this.errorMessage = message || '上传失败'
    },

    /** 整批进度（0-100）：由当前在传的那个 ScreenUpload 抛出 */
    handleProgress (percent) {
      this.percent = Number(percent) || 0
      this.batchRunning = true
    },

    /**
     * ScreenUpload 的上传态变化。
     *
     * ★ 必须有这个处理：整批结束后要把 uploadingType 清掉，
     *   否则 busy 会一直是 true，树上所有节点的两个按钮全被禁用 ——
     *   表现就是「传完一次以后再也传不了」。
     * ★ 用 ScreenUpload 自己的 uploading-change 而不是在 success 里清：
     *   传多个文件时 success 是逐个抛的，中途清掉会让后续文件归错类型。
     */
    handleUploadingChange (uploading) {
      if (uploading) {
        this.batchRunning = true
        return
      }
      this.batchRunning = false
      this.uploadingType = ''
      this.percent = 0
      // 整批结束后刷新树，让「刚刚上传」并入「已有文件」
      this.loadTree()
    },

    /**
     * 单个文件**第一步成功后**（字节已落盘）触发：立刻登记元数据。
     *
     * ★ 这里承接的是「第二步」：文件已经在服务器上，把它挂到业务对象与材料类型上。
     *   登记失败不会让 ScreenUpload 重传字节（那样会浪费一次上传），
     *   而是记进失败清单、由用户在列表里看到「文件在盘上但没登记」。
     */
    handleUploaded (payload) {
      const file = payload && payload.file
      const storePath = payload && payload.storePath
      const typeCode = this.uploadingType
      if (!file || !storePath || !typeCode) {
        return
      }
      this.doneCount += 1
      this.pushJustUploaded(typeCode, file, storePath)
      this.saveMeta(file, typeCode, storePath).then((saved) => {
        if (!saved) {
          return
        }
        // 每成功一份就通知父组件刷新：批量中途关掉弹窗，已传上去的也该出现在列表里
        this.$emit('ok')
        // 树上「已有文件」要刷新，否则与「刚刚上传」并列显示会重复
        this.loadTree()
      }).catch((e) => {
        this.errorMessage = `${file.name}：${(e && e.message) || '附件登记失败'}`
      })
    },

    /** 第二步：落库（材料类型由「点哪个类型」决定） */
    saveMeta (file, typeCode, storePath) {
      const data = {
        bizType: this.form.bizType,
        bizId: this.form.bizId,
        // bizKey 存可读名称（列表与检索直接展示它，避免每次联表）
        bizKey: this.pickedName || null,
        fileType: typeCode,
        fileName: file.name,
        fileExt: resolveExt(file.name),
        fileSize: file.size,
        contentType: file.type || null,
        storeType: 'local',
        storePath,
        sortNo: null
      }
      return saveAttachment(data).then((res) => {
        if (!res || !res.success) {
          throw new Error((res && res.message) || '附件登记失败')
        }
        return true
      })
    },

    /** 就地显示「刚刚上传」，给即时反馈（树刷新后会并入已有文件列表） */
    pushJustUploaded (typeCode, file, storePath) {
      const next = Object.assign({}, this.justUploaded)
      const list = (next[typeCode] || []).slice()
      list.push({ key: storePath, fileName: file.name, fileSize: file.size })
      next[typeCode] = list
      this.justUploaded = next
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/*
 * 设计口径（来自 ui-ux-pro-max 的企业级密集面板建议）：
 *   · 目录用强调蓝、文件用弱化的中性色 —— 一眼分得清「容器」与「内容」
 *   · 8px 间距节奏；hover 150-250ms；不做多余装饰与重阴影
 *   · 密集排布（这是要在弹窗里放下十几个材料类型的面板）
 */
.attachment-upload {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);

    // 目录树这一段要吃掉弹窗的剩余高度，自己内部滚动
    &--tree {
      flex: 1 1 auto;
      min-height: 200px;
      max-height: 46vh;
      overflow-y: auto;
      .screen-scrollbar();
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__picked {
    margin: 4px 0 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);

    b {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }
  }

  &__tree-head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
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

  &__tree-hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__placeholder {
    margin: 0;
    padding: var(--screen-space-4) 0;
    font-size: var(--screen-font-xs);
    line-height: 1.8;
    color: var(--screen-text-mute);
    text-align: center;
  }

  /* ---------- 材料类型节点 ---------- */
  &__group {
    border-bottom: 1px solid var(--screen-border-soft);

    &:last-of-type {
      border-bottom: 0;
    }

    &.is-uploading {
      background: rgba(103, 178, 255, 0.06);
      border-radius: var(--screen-radius-sm);
    }
  }

  &__group-head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;
    padding: 6px var(--screen-space-2);
    border-radius: var(--screen-radius-sm);
    transition: background var(--screen-duration) var(--screen-ease);

    &:hover {
      background: rgba(255, 255, 255, 0.04);
    }
  }

  &__group-toggle {
    flex: 0 1 auto;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    font-weight: 600;
    color: var(--screen-text);
    background: none;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__folder {
    // 目录用强调色：与下面的文件行形成「容器 / 内容」的层级差
    flex: 0 0 auto;
    color: var(--screen-accent);
  }

  &__group-name {
    min-width: 0;
    .screen-ellipsis();
  }

  &__group-badge {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* 两个上传入口：选择文件 / 选择文件夹 */
  &__group-ops {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  &__op {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent-soft);
    background: rgba(103, 178, 255, 0.1);
    border: 1px solid rgba(103, 178, 255, 0.28);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: background var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(:disabled) {
      background: rgba(103, 178, 255, 0.2);
      border-color: rgba(103, 178, 255, 0.5);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }

  /* 当前在传类型的整批进度 */
  &__progress {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    padding: 0 var(--screen-space-2) 6px 30px;
  }

  &__track {
    flex: 1 1 auto;
    height: 4px;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.1);
    border-radius: var(--screen-radius-pill);
  }

  &__fill {
    display: block;
    height: 100%;
    background: var(--screen-accent);
    border-radius: var(--screen-radius-pill);
    transition: width 200ms var(--screen-ease);
  }

  &__percent {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* 校验未通过的清单 */
  &__rejects {
    margin: 0;
    padding: 0 var(--screen-space-2) 6px 30px;
    list-style: none;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-danger);
  }

  /* ---------- 文件行 ---------- */
  &__files {
    margin: 0;
    padding: 0 0 6px;
    list-style: none;
  }

  &__file {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    // 缩进一级：文件属于上面那个材料类型
    padding: 3px var(--screen-space-2) 3px 30px;
    font-size: var(--screen-font-xs);

    &.is-new {
      color: var(--screen-success);
    }

    &.is-empty {
      padding-left: 30px;
      color: var(--screen-text-mute);
      font-size: var(--screen-font-xs);
    }
  }

  &__file-icon {
    flex: 0 0 auto;
    // 文件用中性色，把强调色留给目录
    color: var(--screen-text-mute);

    &.is-new {
      color: var(--screen-success);
    }
  }

  &__file-name {
    flex: 1 1 auto;
    min-width: 0;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__file-size {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    color: var(--screen-text-mute);
  }

  &__file-tag {
    flex: 0 0 auto;
    padding: 0 6px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    background: rgba(255, 255, 255, 0.06);
    border-radius: var(--screen-radius-pill);

    &.is-new {
      color: var(--screen-success);
      background: rgba(67, 233, 114, 0.12);
    }
  }

  /* ---------- 隐藏的文件输入（1px + 裁切，保留在可访问树里） ---------- */
  &__input {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
    border: 0;
  }

  /*
   * 上传控件容器：只保留 ScreenUpload 的「隐藏 input」，
   * 它的按钮（trigger 插槽里那个）与进度/校验清单由本弹窗自己渲染 ——
   * 否则同一份信息会在界面上出现两遍。
   * 用 clip 而不是 display:none：display:none 的 input 在部分浏览器里
   * 无法被 .click() 唤起选择框。
   */
  &__uploaders {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);

    /deep/ .screen-upload__input {
      width: 1px;
      height: 1px;
    }
  }

  &__hidden-trigger {
    // 这个按钮只作为 ScreenUpload 的 trigger 插槽占位，实际点击由树上的按钮代理
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    border: 0;
    opacity: 0;
    pointer-events: none;
  }

  /* ---------- 反馈 ---------- */
  &__alert {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: 6px var(--screen-space-2);
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    border-radius: var(--screen-radius-sm);

    &.is-error {
      color: var(--screen-danger);
      background: rgba(255, 107, 90, 0.08);
    }

    &.is-success {
      color: var(--screen-success);
      background: rgba(67, 233, 114, 0.08);
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    padding-top: var(--screen-space-3);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__foot-hint {
    flex: 1 1 320px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);
  }
}

@media (max-width: 900px) {
  .attachment-upload__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
