<template>
  <!--
    AttachmentUploadModal 附件上传
    --------------------------------
    上传是**两步**，两步的职责必须分清（这是旧系统出问题最多的地方）：

      第一步（文件字节）：ScreenUpload → POST `/sys/common/upload` 落盘。
        目录规则、大小限制、扩展名限制全在这一个通用接口里，
        所以 `biz` 字段决定分目录（按 业务/attachment/yyyy/MM）。
      第二步（元数据）：本弹窗 → POST `/land/data/attachment/save` 落库。
        这一步才决定「这个文件挂在哪条业务上、属于哪一类附件」。

    ★ 为什么必须先选「归属」和「附件类型」才允许上传：
      jeecg 的通用上传接口一旦落盘，文件已经在磁盘上了；如果这时用户取消，
      磁盘上就多一个没人引用的孤儿文件。把归属与类型放在上传控件**之前**
      并要求填完，能让「点上传」这个动作本身就意味着「我知道这个文件是什么」。

    ★ 为什么 type 用后端的 allowedTypes 而不是本地 13 类常量：
      附件类型是最会扩的一类字典（旧系统正是写死 4 个槽位才被替换）。
      接口挂了才退到本地常量。

    ★ storePath 在响应的 `message` 里（`res.message || res.result`）——
      这是 jeecg 通用上传接口的历史行为，ScreenUpload 已经按这个约定解析好，
      本组件直接拿 `storePath` 即可，不要再去 result 里翻。

    公开方法：
      open(preset)  preset 可预置 { bizType, bizId, bizKey }（从配套详情跳过来时用）
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
      <p class="attachment-upload__hint">
        <screen-icon name="info" :size="14" />
        上传分两步：文件先落到业务后端的存储目录，再把「挂在哪条业务上、属于哪类附件」
        写入附件表。所以<b>请先填好归属与附件类型再选文件</b>。
      </p>

      <section class="attachment-upload__section">
        <h4 class="attachment-upload__section-title">
          <screen-icon name="link" :size="14" />
          第一步：归属与分类
          <span class="attachment-upload__required" aria-hidden="true">*</span>
        </h4>

        <div class="attachment-upload__grid">
          <screen-field label="业务类型" required :error="errors.bizType" label-width="112px">
            <screen-select
              v-model="form.bizType"
              :options="bizTypeOptions"
              :invalid="!!errors.bizType"
              placeholder="请选择业务类型"
              aria-label="业务类型"
            />
          </screen-field>

          <screen-field
            label="业务主键"
            required
            :error="errors.bizId"
            html-for="au-bizId"
            label-width="112px"
            tip="宗地 / 配套项目 / 环节进度的 id（从列表跳过来时已自动带上）"
          >
            <screen-input
              id="au-bizId"
              v-model="form.bizId"
              :maxlength="64"
              :invalid="!!errors.bizId"
              placeholder="业务对象的 id"
            />
          </screen-field>

          <screen-field
            label="业务可读键"
            html-for="au-bizKey"
            label-width="112px"
            tip="宗地编号 / 配套项目名称，列表与检索直接展示它，避免每次联表"
          >
            <screen-input
              id="au-bizKey"
              v-model="form.bizKey"
              :maxlength="200"
              placeholder="例如：津西青(挂)2024-01号"
            />
          </screen-field>

          <screen-field label="附件类型" required :error="errors.fileType" label-width="112px">
            <screen-select
              v-model="form.fileType"
              :options="typeOptions"
              :invalid="!!errors.fileType"
              placeholder="请选择附件类型"
              aria-label="附件类型"
            />
          </screen-field>

          <screen-field label="备注" html-for="au-remark" label-width="112px">
            <screen-input
              id="au-remark"
              v-model="form.remark"
              type="textarea"
              :rows="2"
              :maxlength="500"
              placeholder="这份材料的补充说明，可留空"
            />
          </screen-field>
        </div>
      </section>

      <section class="attachment-upload__section">
        <h4 class="attachment-upload__section-title">
          <screen-icon name="upload" :size="14" />
          第二步：选择文件
          <span class="attachment-upload__required" aria-hidden="true">*</span>
        </h4>
        <p class="attachment-upload__section-hint">
          单文件上限 {{ MAX_SIZE_MB }}MB。保存目录按
          <code>{{ bizPath }}</code> 分年月，与档案、收发文互不干扰。
        </p>

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
          请先选择业务类型与附件类型、并填写业务主键，上传控件才会启用。
        </p>

        <!-- 本次会话已落库的附件：让用户看到「刚才那个文件确实进去了」 -->
        <div v-if="uploaded.length" class="attachment-upload__done">
          <h5 class="attachment-upload__done-title">
            本次已上传并落库 {{ uploaded.length }} 个文件
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
        <span class="attachment-upload__foot-hint">
          文件本身不受本弹窗影响：即使这里保存元数据失败，也可以修正后重新提交，
          不必重新上传（磁盘上的文件还在）。
        </span>
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
    bizKey: '',
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
      ALLOWED_EXT,
      ALLOWED_EXT_TEXT,
      MAX_SIZE_MB
    }
  },
  computed: {
    /**
     * 上传子目录：/facility/attachment/yyyy/MM（按业务类型分目录）。
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
        bizKey: record.bizKey || '',
        fileType: record.fileType || '',
        remark: ''
      })
      this.errors = {}
      this.uploaded = []
      this.visible = true
      this.resetUploader()
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
        toast.warning('请先补全业务类型 / 业务主键 / 附件类型，再重新选择该文件')
        return
      }

      const data = {
        bizType: this.form.bizType,
        bizId: this.form.bizId,
        bizKey: this.form.bizKey || null,
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
            toast.error((res && res.message) || '附件元数据保存失败（文件已上传，可重新提交）')
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

  &__hint {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-accent-soft);
    background: rgba(130, 198, 255, 0.08);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);

    b {
      color: var(--screen-accent);
      font-weight: 600;
    }
  }

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

  &__section-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);

    code {
      padding: 0 4px;
      font-family: var(--screen-font-number-family);
      color: var(--screen-accent-soft);
      background: rgba(6, 20, 40, 0.72);
      border-radius: var(--screen-radius-sm);
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
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

  /* 已落库清单 */
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
