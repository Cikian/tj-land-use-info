<template>
  <!--
    AttachmentUploadModal 附件上传
    --------------------------------
    上传分两步（两步职责必须分清，这是旧系统出问题最多的地方）：
      第一步（文件字节）：ScreenUpload → POST `/sys/common/upload` 落盘。
      第二步（元数据）：本弹窗 → POST `/land/data/attachment/save` 落库。

    ★ 界面文案的两条原则（2026-10-09 按反馈调整）：
      1. **不解释实现**。用户不需要知道「先落盘再落库」「相对路径」这些内部机制，
         界面上只保留「这一步要做什么」的标签与必要的错误提示。
      2. **不出现数据库概念**。「id」「主键」对用户没有意义：用户看到的是
         「所属对象」的名称（配套项目名称 / 出让宗地编号），
         只有提交给后端时才换成真实 id（见 form.bizId 与 handlePickObject）。

    ★ 为什么必须先选「归属」再选文件：
      通用上传接口一旦落盘，文件已经在磁盘上了；如果这时用户取消，
      磁盘上就多一个没人引用的孤儿文件。把归属放在上传控件之前并要求填完，
      让「点上传」这个动作本身就意味着「我知道这个文件是什么」。

    ★ 为什么附件类型用后端的 allowedTypes 而不是本地常量：
      附件类型是最会扩的一类字典（旧系统正是写死 4 个槽位才被替换）。
      接口挂了才退到本地常量。

    ★ storePath 在响应的 `message` 里（`res.message || res.result`）——
      jeecg 通用上传接口的历史行为，ScreenUpload 已按这个约定解析好。

    公开方法：
      open(preset)  preset 可预置 { bizType, bizId, bizKey, bizName }
    事件：
      ok  至少有一个文件保存成功后抛出，父组件据此刷新列表
  -->
  <screen-modal
    :visible.sync="visible"
    title="上传附件"
    :width="820"
    :show-footer="false"
    :body-max-height="'calc(100vh - 200px)'"
    @cancel="handleClose"
  >
    <div class="attachment-upload">
      <!-- ============ 第一步：选择归属 ============ -->
      <section class="attachment-upload__section">
        <h4 class="attachment-upload__section-title">
          <screen-icon name="link" :size="14" />
          第一步：选择归属
          <span class="attachment-upload__required" aria-hidden="true">*</span>
        </h4>

        <div class="attachment-upload__grid">
          <screen-field label="归属类型" required :error="errors.bizType" label-width="112px">
            <screen-select
              v-model="form.bizType"
              :options="bizTypeOptions"
              :invalid="!!errors.bizType"
              placeholder="请选择"
              aria-label="归属类型"
              @change="handleBizTypeChange"
            />
          </screen-field>

          <screen-field label="所属对象" required :error="errors.bizId" label-width="112px">
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
            <!-- 已带出归属、但下拉候选里还没有这条记录时（例如从别的页面跳进来，
                 列表接口按关键字没搜到它），把名称显示出来，
                 用户才知道这个附件会挂到哪里去；已在下拉里显示时不重复 -->
            <p v-if="pickedName && !objectOptionShowsPicked" class="attachment-upload__picked">
              已选择：<b>{{ pickedName }}</b>
            </p>
          </screen-field>

          <screen-field label="材料类型" required :error="errors.fileType" label-width="112px">
            <screen-select
              v-model="form.fileType"
              :options="typeOptions"
              :invalid="!!errors.fileType"
              placeholder="请选择"
              aria-label="材料类型"
            />
          </screen-field>

          <screen-field label="备注" html-for="au-remark" label-width="112px">
            <screen-input
              id="au-remark"
              v-model="form.remark"
              type="textarea"
              :rows="2"
              :maxlength="500"
              placeholder="可留空"
            />
          </screen-field>
        </div>
      </section>

      <!-- ============ 第二步：选择文件 ============ -->
      <section class="attachment-upload__section">
        <h4 class="attachment-upload__section-title">
          <screen-icon name="upload" :size="14" />
          第二步：选择文件
          <span class="attachment-upload__required" aria-hidden="true">*</span>
        </h4>

        <screen-upload
          ref="uploader"
          :action="uploadAction"
          :headers="uploadHeaders"
          :data="{ biz: bizPath }"
          :disabled="!canUpload"
          :allowed-ext="ALLOWED_EXT"
          :allowed-ext-text="ALLOWED_EXT_TEXT"
          :max-size-mb="MAX_SIZE_MB"
          button-text="选择文件上传"
          @success="handleUploaded"
          @reject="handleRejected"
          @error="handleUploadError"
        />

        <p v-if="!canUpload" class="attachment-upload__alert is-warning">
          <screen-icon name="alert-triangle" :size="14" />
          请先选好归属类型、所属对象与材料类型。
        </p>

        <!-- 本次会话已落库的附件 -->
        <div v-if="uploaded.length" class="attachment-upload__done">
          <h5 class="attachment-upload__done-title">
            本次已上传 {{ uploaded.length }} 个文件
          </h5>
          <ul class="attachment-upload__done-list">
            <li v-for="(item, index) in uploaded" :key="index" class="attachment-upload__done-item">
              <screen-icon name="check-circle" :size="13" />
              <span class="attachment-upload__done-name" :title="item.fileName">{{ item.fileName }}</span>
              <span class="attachment-upload__done-size">{{ formatSize(item.fileSize) }}</span>
            </li>
          </ul>
        </div>
      </section>

      <footer class="attachment-upload__foot">
        <screen-button type="primary" @click="handleClose">
          {{ uploaded.length ? '完成' : '关闭' }}
        </screen-button>
      </footer>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenButton,
  ScreenIcon,
  ScreenUpload
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  saveAttachment,
  queryAllowedAttachmentTypes,
  attachmentUploadAction,
  attachmentUploadHeaders,
  ATTACHMENT_BIZ_TYPES,
  ATTACHMENT_TYPES_FALLBACK
} from '@/api/land/attachment'
import { queryLandDictItems } from '@/api/land/landAdmin'
import { queryLandOptions, searchFacilityOptions } from '@/api/land/landData'
import {
  buildBizPath,
  formatSize,
  dictToOptions,
  dictDefinitions,
  resolveExt
} from '../constants'

/**
 * 允许的扩展名（小写、不含点）。
 * 与档案模块的 ALLOWED_EXT 同一份口径：办公文档 / 图片 / 文本 / 压缩包 /
 * CAD / GIS 矢量 / 视频。配套附件里 dwg / shp 很常见，必须包含。
 */
const ALLOWED_EXT = [
  'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv', 'ofd',
  'rar', 'zip', '7z',
  'dwg', 'dxf', 'shp', 'dbf', 'shx', 'prj', 'kml', 'kmz',
  'mp4', 'avi', 'mov', 'wmv'
]

const ALLOWED_EXT_TEXT = 'pdf / doc(x) / xls(x) / ppt(x) / 图片 / ofd / dwg / shp / zip / rar 等'
const MAX_SIZE_MB = 200

function emptyForm () {
  return {
    bizType: 'facility',
    bizId: '',
    fileType: '',
    remark: ''
  }
}

export default {
  name: 'AttachmentUploadModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
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
      uploaded: [],
      bizTypeOptions: ATTACHMENT_BIZ_TYPES,
      typeOptions: ATTACHMENT_TYPES_FALLBACK.map((item) => ({ value: item.value, label: item.text })),
      /** 「所属对象」下拉的候选（值=真实 id，label=用户看得懂的名称） */
      objectOptions: [],
      objectSearching: false,
      /** 当前归属对象的显示名称 —— 提交给后端的 bizKey 就是它 */
      pickedName: '',
      ALLOWED_EXT,
      ALLOWED_EXT_TEXT,
      MAX_SIZE_MB
    }
  },
  computed: {
    /**
     * 上传子目录：/facility/attachment/yyyy/MM（按归属类型分目录）。
     * ★ 目录名带业务类型而不是用户名：运维按目录清理时才能分辨哪些能删。
     */
    bizPath () {
      const module = this.form.bizType === 'land' ? 'land' : this.form.bizType === 'process' ? 'process' : 'facility'
      return buildBizPath(module)
    },
    uploadAction () {
      return attachmentUploadAction()
    },
    /**
     * 上传请求头。
     * ★ 必须用 jeecg 自己的令牌（JEECG_ACCESS_TOKEN）：中台的 ACCESS_TOKEN
     *   在 Java 端是无效令牌，会 401。attachmentUploadHeaders 内部已经取对了。
     * ★ 每次渲染都重新取：用户长时间开着弹窗时令牌可能已被滑动续期，
     *   取一次缓存住会在续期后失效。
     */
    uploadHeaders () {
      return attachmentUploadHeaders()
    },
    canUpload () {
      return !!(this.form.bizType && this.form.bizId && this.form.fileType)
    },
    objectPlaceholder () {
      if (!this.form.bizType) return '请先选择归属类型'
      if (this.form.bizType === 'process') return '请输入或选择环节'
      return this.form.bizType === 'land' ? '输入编号或地块名称搜索' : '输入名称搜索'
    },
    /**
     * 「所属对象」下拉里是否已经能看到当前选中项。
     * 能看到就不必再额外回显一行名称，否则同一句话会显示两遍。
     */
    objectOptionShowsPicked () {
      if (!this.form.bizId) return false
      return this.objectOptions.some((item) => item.value === this.form.bizId)
    }
  },
  created () {
    this.loadTypes()
  },
  methods: {
    formatSize,

    /* ---------------- 对外入口 ---------------- */

    open (preset) {
      const record = preset || {}
      this.form = Object.assign(emptyForm(), {
        bizType: record.bizType || 'facility',
        bizId: record.bizId || '',
        fileType: record.fileType || '',
        remark: ''
      })
      this.errors = {}
      this.uploaded = []
      // ★ 名称优先取 preset 显式给的（父组件手里有列表，知道它叫什么）；
      //   退而用 bizKey（宗地编号 / 配套项目名称，本身就是可读的）
      this.pickedName = record.bizName || record.bizKey || ''
      this.objectOptions = []
      this.visible = true
      this.resetUploader()
      // 已带出归属：预取一次候选，让下拉里能看到并切换
      if (this.form.bizType) {
        this.loadObjectOptions('')
      }
    },

    /** 清掉上一次打开时残留的队列与进度（ScreenUpload 是受控的内部状态） */
    resetUploader () {
      const tryClear = (attempt) => {
        this.$nextTick(() => {
          const uploader = this.$refs.uploader
          if (uploader && typeof uploader.clear === 'function') {
            uploader.clear()
            return
          }
          if (attempt < 4) tryClear(attempt + 1)
        })
      }
      tryClear(0)
    },

    handleClose () {
      const changed = this.uploaded.length > 0
      this.visible = false
      if (changed) {
        this.$emit('ok')
      }
    },

    /**
     * 附件类型。
     * 三层兜底：专用接口 → 字典 → 本地常量（见组件头注释）。
     */
    loadTypes () {
      queryAllowedAttachmentTypes()
        .then((res) => {
          if (res && res.success && res.result && res.result.length) {
            this.typeOptions = dictToOptions(res.result)
            return true
          }
          return false
        })
        .catch(() => false)
        .then((done) => {
          if (done) return
          queryLandDictItems(dictDefinitions()).then((dicts) => {
            if (dicts.attachType) this.typeOptions = dicts.attachType
          })
        })
    },

    /* ---------------- 所属对象（下拉里是名称，提交的是 id） ---------------- */

    handleBizTypeChange () {
      this.form.bizId = ''
      this.pickedName = ''
      this.objectOptions = []
      this.errors = Object.assign({}, this.errors, { bizId: '' })
      this.loadObjectOptions('')
    },

    /**
     * 拉「所属对象」候选。
     *
     * ★ 值为真实 id、label 为可读名称 —— 这是本弹窗「用户看到名称、
     *   实际存唯一 id」的实现点：form.bizId 始终是 id，
     *   用户从不接触 id。
     */
    loadObjectOptions (keyword) {
      const type = this.form.bizType
      if (!type) {
        this.objectOptions = []
        return
      }
      // 环节进度没有独立的下拉接口：它的归属来自配套详情/环节列表跳转，
      // 这里把已带出的 id 作为唯一选项保留，不做搜索
      if (type === 'process') {
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
        // 已选中的那条若不在本次结果里，补进去，否则用户会以为选择被清掉了
        if (this.form.bizId && !options.some((item) => item.value === this.form.bizId)) {
          const name = this.pickedName || this.form.bizId
          options.unshift({ value: this.form.bizId, label: name, raw: null })
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

    /**
     * 选中候选：把 id 存进表单、把名称记下来（提交时作为 bizKey）。
     *
     * ★ 名称解析顺序（宁可多兜一层，也不要让 biz_key 变成空）：
     *   ① raw 存在 → 按归属类型拼「名称（编号）」（与下拉、服务端同一口径）；
     *   ② 拿不到 raw（例如候选项是补进来的、raw 为 null）→ 用该 option 的 label，
     *      它本身就是用户在下拉里看到的那串文字，语义完全一致；
     *   ③ 最后才退到 id。
     *   服务端在 bizKey 为空时还会用 bizId 反查业务对象兜底（见 saveUploaded），
     *   这里把前端这一层做扎实，是为了不让列表出现「有附件、无归属」的空档。
     */
    handleObjectChange (value, raw) {
      this.errors = Object.assign({}, this.errors, { bizId: '' })
      if (!value) {
        this.pickedName = ''
        return
      }
      if (raw && this.form.bizType === 'land') {
        // 与配套同一口径「名称（编号）」；地块名缺失时退到编号
        const name = String(raw.dkmc || '').trim()
        const code = String(raw.crzdbh || '').trim()
        this.pickedName = name ? (code ? `${name}（${code}）` : name) : code
        return
      }
      if (raw && this.form.bizType === 'facility') {
        this.pickedName = `${raw.ptxmmc || ''}${raw.crzdbh ? '（' + raw.crzdbh + '）' : ''}`.trim()
        return
      }
      // ② 没有 raw：用下拉里显示过的 label
      const option = (this.objectOptions || []).find((item) => item.value === value)
      if (option && option.label) {
        this.pickedName = String(option.label).trim()
        return
      }
      // ③ 还没有（例如 raw 与 label 都缺）→ 保持已有的名称，最后退到 id
      this.pickedName = this.pickedName || String(value)
    },

    /* ---------------- 上传回调 ---------------- */

    /**
     * 第一步成功（文件已落盘）→ 立刻做第二步（落库）。
     * ★ 落库失败时**不清空** uploaded，也不阻止用户再传下一个：
     *   磁盘上的文件还在，用户可以改完类型重新提交（本组件没有提供单独
     *   「重新落库」的入口，是因为这种失败极少见，重新选一次文件最省事）。
     */
    handleUploaded (payload) {
      const file = payload && payload.file
      const storePath = payload && payload.storePath
      if (!storePath) {
        toast.error('上传成功但未返回存储路径，无法落库')
        return
      }
      if (!this.form.bizType || !this.form.bizId || !this.form.fileType) {
        toast.warning('请先补全归属类型、所属对象与材料类型，再重新选择该文件')
        return
      }

      const data = {
        bizType: this.form.bizType,
        // ★ 提交的是唯一 id，不是名称
        bizId: this.form.bizId,
        // ★ bizKey 存可读名称（列表与检索直接展示它，避免每次联表）
        bizKey: this.pickedName || null,
        fileType: this.form.fileType,
        fileName: file ? file.name : storePath.split('/').pop(),
        fileExt: resolveExt(file ? file.name : storePath),
        fileSize: file ? file.size : null,
        contentType: file && file.type ? file.type : null,
        // storeType=local：本系统目前只有本地存储一种；字段留着是为了将来接 minio / oss
        storeType: 'local',
        storePath,
        remark: this.form.remark || null,
        sortNo: this.uploaded.length + 1
      }

      saveAttachment(data)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '附件保存失败，可修正后重新提交')
            return
          }
          toast.success(res.message || '附件已保存')
          this.uploaded.push(data)
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    handleRejected (reason) {
      // ScreenUpload 自己已经弹了提示并渲染了可见清单，这里不重复提示
      if (reason) {
        // 保留一个显式分支便于将来接入埋点
      }
    },

    handleUploadError () {
      // 同上：ScreenUpload 已负责提示与错误清单
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-upload {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

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

  &__required {
    line-height: 1;
    color: var(--screen-danger);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  /* 已选归属对象的名称回显 */
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

  &__alert {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;

    &.is-warning {
      color: var(--screen-warning);
    }
  }

  /* 已上传清单 */
  &__done {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    padding: var(--screen-space-2) 10px;
    background: rgba(67, 233, 114, 0.06);
    border: 1px solid var(--screen-success);
    border-radius: var(--screen-radius-sm);
  }

  &__done-title {
    margin: 0;
    font-size: var(--screen-font-xs);
    font-weight: 600;
    color: var(--screen-success);
  }

  &__done-list {
    margin: 0;
    padding: 0;
    max-height: 140px;
    overflow-y: auto;
    list-style: none;
    .screen-scrollbar();
  }

  &__done-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: var(--screen-font-xs);
    line-height: 1.8;
    color: var(--screen-text-sub);

    /deep/ .screen-icon {
      color: var(--screen-success);
    }
  }

  &__done-name {
    flex: 1 1 auto;
    min-width: 0;
    .screen-ellipsis();
  }

  &__done-size {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    color: var(--screen-text-mute);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--screen-space-3);
    padding-top: var(--screen-space-3);
    border-top: 1px solid var(--screen-border-soft);
  }
}

@media (max-width: 900px) {
  .attachment-upload__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
