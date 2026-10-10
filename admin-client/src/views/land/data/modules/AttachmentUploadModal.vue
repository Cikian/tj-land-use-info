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
      <div class="attach-upload__field">
        <span class="attach-upload__label">所属对象<i>*</i></span>
        <a-select
          v-model="bizId"
          show-search
          allow-clear
          :disabled="!bizType || bizType === 'process'"
          :placeholder="bizPlaceholder"
          style="width: 100%"
          :filter-option="false"
          :default-active-first-option="false"
          :not-found-content="bizLoading ? '搜索中…' : '输入编号 / 名称后搜索'"
          @search="handleBizSearch"
          @change="handleBizChange">
          <a-select-option v-for="item in bizOptions" :key="item.id" :value="item.id">
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

      <a-spin v-else :spinning="treeLoading">
        <div
          v-for="group in groups"
          :key="group.key"
          class="attach-upload__group"
          :class="{ 'is-uploading': uploadingType === group.fileType }">
          <!-- 材料类型节点：点「上传」即归到该类型，不需要再选类型 -->
          <div class="attach-upload__group-head">
            <a class="attach-upload__group-toggle" @click="toggle(group.key)">
              <a-icon :type="isExpanded(group.key) ? 'down' : 'right'" />
              <a-icon :type="isExpanded(group.key) ? 'folder-open' : 'folder'" class="attach-upload__folder" />
              <span class="attach-upload__group-name">{{ group.fileTypeName }}</span>
            </a>
            <span class="attach-upload__group-badge">
              {{ group.fileCount }} 个 / {{ formatSize(group.totalSize) }}
            </span>
            <a-button
              size="small"
              icon="upload"
              :loading="uploadingType === group.fileType"
              :disabled="uploadingType !== '' && uploadingType !== group.fileType"
              @click="pickFiles(group)">
              {{ uploadingType === group.fileType ? '上传中…' : '上传' }}
            </a-button>
          </div>

          <!-- 该类型已有的文件 -->
          <ul v-if="isExpanded(group.key)" class="attach-upload__files">
            <li v-for="file in group.files" :key="file.id" class="attach-upload__file">
              <a-icon type="file" class="attach-upload__file-icon" />
              <span class="attach-upload__file-name" :title="file.fileName">{{ file.fileName }}</span>
              <span class="attach-upload__file-size">{{ file.readableSize || formatSize(file.fileSize) }}</span>
              <span class="attach-upload__file-tag">已上传</span>
            </li>
            <!-- 本次会话刚传上去的：先就地显示，给即时反馈 -->
            <li
              v-for="item in justUploaded[group.fileType] || []"
              :key="item.key"
              class="attach-upload__file is-new">
              <a-icon type="check-circle" class="attach-upload__file-icon is-new" />
              <span class="attach-upload__file-name" :title="item.fileName">{{ item.fileName }}</span>
              <span class="attach-upload__file-size">{{ formatSize(item.fileSize) }}</span>
              <span class="attach-upload__file-tag is-new">刚刚上传</span>
            </li>
            <li
              v-if="!group.fileCount && !(justUploaded[group.fileType] || []).length"
              class="attach-upload__file is-empty">
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
      隐藏的文件输入：树里每个类型共用一个（节点只负责「用我的类型触发它」）。
      webkitdirectory 让用户能选整个文件夹；不支持时退化成多选文件。
    -->
    <input
      ref="fileInput"
      class="attach-upload__input"
      type="file"
      multiple
      webkitdirectory
      :accept="acceptAttr"
      @change="handleFilesPicked" />
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

  /** 允许的扩展名（与后端白名单同源口径；这里只用于文件框的 accept 提示与前端预校验） */
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

  /** 取小写扩展名（服务端也会兜底推导，这里显式给出，避免库里出现 "JPG" 与 "jpg" 两种写法） */
  function extractExt (fileName) {
    const name = fileName || ''
    const index = name.lastIndexOf('.')
    return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
  }

  /**
   * 本地兜底的材料类型清单，**按归属类型给对应那套**。
   * ★ 兜底也要分两套：宗地 5 类与配套 13 类是不同的清单（逐字取自旧系统存储目录），
   *   接口一挂就给错清单的话，用户会选到一个根本不存在的类型。
   */
  const LAND_TYPE_FALLBACK = [
    { value: '01', label: '土地整理计划' },
    { value: '02', label: '配套方案' },
    { value: '03', label: '配套情况函' },
    { value: '04', label: '配套筹备函' },
    { value: '05', label: '出让宗地图形数据（SHP）' }
  ]
  const FACILITY_TYPE_FALLBACK = [
    { value: '01', label: '项建批复文件' },
    { value: '02', label: '可研批复文件' },
    { value: '03', label: '初设及概算批复文件' },
    { value: '04', label: '道路规划' },
    { value: '05', label: '专业配套方案' },
    { value: '06', label: '施工许可' },
    { value: '07', label: '专业管理意见' },
    { value: '08', label: '配套项目核定用地与地籍调查' },
    { value: '09', label: '规划工程许可' },
    { value: '10', label: '规划用地许可与划拨手续办理' },
    { value: '11', label: '不动产登记' },
    { value: '12', label: '竣工文件' },
    { value: '13', label: '移交文件' }
  ]

  function fallbackTypesOf (bizType) {
    return (bizType === 'facility' ? FACILITY_TYPE_FALLBACK : LAND_TYPE_FALLBACK).slice()
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
        bizTypeOptions: BIZ_TYPE_REQUIRED_OPTIONS,
        // ---- 材料类型目录 ----
        /** 材料类型清单（决定树上列哪些类型，含还没有文件的） */
        typeOptions: fallbackTypesOf('facility'),
        /** 该归属对象的材料目录：{ groups:[...], totalFiles, totalSize, typeCount } */
        tree: { groups: [] },
        treeLoading: false,
        /** 收起的材料类型 key（默认全展开，结构一眼可见） */
        collapsed: [],
        // ---- 上传 ----
        /** 当前正在上传的材料类型码 */
        uploadingType: '',
        batchRunning: false,
        justUploaded: {},
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
        return (this.typeOptions || []).map(option => {
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
      }
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
        if (record.bizId) {
          this.bizId = record.bizId
          this.bizKey = record.bizKey || ''
          // 预置的业务对象不在远程搜索结果里，补一个选项让下拉能显示出来
          if (record.bizKey) {
            this.bizOptions = [{ id: record.bizId, crzdbh: record.bizKey, dkmc: '', ptxmmc: record.bizKey }]
          }
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
        this.tree = { groups: [] }
        this.collapsed = []
        this.uploadingType = ''
        this.batchRunning = false
        this.justUploaded = {}
        this.doneCount = 0
        this.errorMessage = ''
      },

      handleClose () {
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
        this.justUploaded = {}
        this.loadTypes()
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
          return
        }
        const hit = this.bizOptions.filter(item => item.id === value)[0]
        this.bizId = value
        // 业务可读键：宗地用编号、配套用名称 —— 列表里直接展示，避免每次联表
        this.bizKey = hit ? (this.bizType === 'facility' ? hit.ptxmmc : hit.crzdbh) : ''
        this.tree = { groups: [] }
        this.justUploaded = {}
        this.loadTree()
      },

      optionLabel (item) {
        if (this.bizType === 'facility') {
          return item.ptxmmc + (item.crzdbh ? `（${item.crzdbh}）` : '')
        }
        return (item.crzdbh || '') + (item.dkmc ? `（${item.dkmc}）` : '')
      },

      /* ---------------- 材料目录树 ---------------- */

      loadTree () {
        if (!this.bizId) {
          return
        }
        this.treeLoading = true
        queryAttachmentTree(this.bizType, this.bizId).then(res => {
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
       * 点某个类型的「上传」：记下目标类型，唤起文件选择。
       * ★ 用户不需要选材料类型 —— 点哪个类型就归到哪个类型。
       * ★ 取消选择也要复位：用户在系统文件框点「取消」时 change 不触发，
       *   用 window 的 focus 兜底（文件框关闭后窗口重新获得焦点）。
       */
      pickFiles (group) {
        this.uploadingType = group.fileType
        this.errorMessage = ''
        const typeCode = group.fileType
        const resetOnCancel = () => {
          window.removeEventListener('focus', resetOnCancel)
          window.setTimeout(() => {
            if (this.uploadingType === typeCode && !this.batchRunning) {
              this.uploadingType = ''
            }
          }, 400)
        }
        window.addEventListener('focus', resetOnCancel)
        this.$nextTick(() => {
          const input = this.$refs.fileInput
          if (input) {
            input.value = ''
            input.click()
          }
        })
      },

      /** 原生 change：校验 → 串行上传 */
      handleFilesPicked (event) {
        const input = event && event.target ? event.target : this.$refs.fileInput
        const selected = input && input.files ? Array.prototype.slice.call(input.files) : []
        if (!selected.length) {
          this.uploadingType = ''
          return
        }
        if (selected.length > MAX_FILES) {
          this.errorMessage = `所选文件夹含 ${selected.length} 个文件，超过一次最多 ${MAX_FILES} 个的限制，请分批上传`
          this.uploadingType = ''
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
          this.uploadingType = ''
          return
        }
        this.uploadBatch(accepted)
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
       */
      uploadBatch (files) {
        const typeCode = this.uploadingType
        const pending = files.slice()
        const failed = []
        let ok = 0
        this.batchRunning = true
        const step = () => {
          const file = pending.shift()
          if (!file) {
            this.batchRunning = false
            this.uploadingType = ''
            this.doneCount += ok
            if (failed.length) {
              this.errorMessage = `成功 ${ok} 个，失败 ${failed.length} 个：` +
                failed.slice(0, 5).join('；') + (failed.length > 5 ? ' 等' : '')
            } else if (ok > 0) {
              this.errorMessage = ''
              this.$message.success(`已上传 ${ok} 个文件`)
              this.$emit('ok')
            }
            // 树上的「已有文件」要刷新，否则与「刚刚上传」并列显示会重复
            this.loadTree()
            return
          }
          this.uploadOne(file, typeCode).then(success => {
            if (success) {
              ok += 1
            } else {
              failed.push(file.name)
            }
          }).then(step)
        }
        step()
      },

      /** 单份文件：落盘 → 登记，并把成功的那条就地显示在对应类型下 */
      uploadOne (file, typeCode) {
        return uploadFileBytes(file, buildUploadBiz(this.bizType)).then(uploaded => {
          return this.saveMeta(file, typeCode, uploaded.storePath).then(saved => {
            if (saved) {
              this.pushJustUploaded(typeCode, file, uploaded.storePath)
            }
            return saved
          })
        }).catch(() => false)
      },

      /** 第 2 步：登记（材料类型由「点哪个类型」决定） */
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
