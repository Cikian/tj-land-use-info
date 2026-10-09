<template>
  <!--
    EscalationMaterialTable 提级论证材料表
    --------------------------------
    材料是「项目录入向导的第三步」，也是审批页 / 详情页的只读材料区，
    所以本组件按 projectId 是否传入自动切换成两种形态（调用方不用关心）：

      1. 本地模式（projectId 为空）—— 纯本地数组 + v-model，材料随主表一起提交；
      2. 远程模式（传了 projectId）—— 自己拉 /material/list，上传与删除直接调材料接口。

    v-model 契约：一个材料对象数组，字段与后端 t_escalation_material 对齐：
      { id, projectId, materialType, fileName, fileExt, fileSize, fileMd5,
        storeType, storePath, previewPath, version, isSupplement }

    业务流程与后端校验对齐：
      1. 材料类型必选（后端会拒绝没有类型的材料），先选类型再上传；
      2. 文件走 jeecg 通用上传接口 /sys/common/upload，返回的是**相对存储路径**，
         它作为 storePath 提交给 /project/add|edit 或 /material/save；
      3. 版本与补充标记：**同一材料类型的第 2 次上传自动记为补充材料并升版本号**
         （version = 该类型已有条数 + 1，isSupplement = version > 1 ? 1 : 0），
         也可以显式选「标记为补充材料」强制置位。

    ★ 上传三件套必须走 Java 业务后端（javaUploadUrl）与 jeecg 自己的令牌
      （JEECG_ACCESS_TOKEN，不是中台的 ACCESS_TOKEN），照 ArchiveFileTable.vue 的写法。

    公开方法（供父组件调用）：
      validate()  提交前校验，返回 '' 表示通过，否则是给用户看的错误文案
      collect()   本地模式下汇总给主表的材料数组
      refresh()   远程模式下重新拉取
  -->
  <div class="escalation-material">
    <!-- ---------- 上传工具条（只读时隐藏） ---------- -->
    <div v-if="!disabled" class="escalation-material__toolbar">
      <div class="escalation-material__type">
        <span class="escalation-material__label">
          材料类型
          <i class="escalation-material__required" aria-hidden="true">*</i>
        </span>
        <screen-select
          v-model="uploadType"
          :options="materialTypeOptions"
          placeholder="先选类型，再上传"
          aria-label="本批上传的材料类型"
        />
      </div>

      <div class="escalation-material__type">
        <span class="escalation-material__label">补充标记</span>
        <screen-radio-group
          v-model="supplementMode"
          :options="supplementOptions"
          size="sm"
          aria-label="补充材料标记方式"
        />
      </div>

      <!--
        ★ 未选「材料类型」时直接禁用上传按钮。
          ScreenUpload 没有 beforeUpload 钩子（只有扩展名 / 体积校验），
          一旦点开文件选择器，文件就已经 POST 到后端了，事后再拒绝只会留下一个孤儿临时文件。
          所以用 disabled 卡在源头，handleUploadSuccess 里的兜底判断只作为第二道防线。
      -->
      <screen-upload
        ref="uploader"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :allowed-ext="MATERIAL_ALLOWED_EXT"
        :max-size-mb="MATERIAL_MAX_SIZE_MB"
        :disabled="uploading || !uploadType"
        button-text="上传材料"
        @success="handleUploadSuccess"
        @error="handleUploadError"
        @reject="handleUploadReject"
        @uploading-change="uploading = $event"
      />

      <span v-if="!uploadType" class="escalation-material__hint is-strong">
        请先选择「材料类型」，再上传材料
      </span>
      <span v-else class="escalation-material__hint">
        允许 {{ MATERIAL_ALLOWED_EXT_TEXT }}，单文件不超过 {{ MATERIAL_MAX_SIZE_MB }}MB；
        同一类型的第 2 个文件会自动记为补充材料并升版本号
      </span>
    </div>

    <!-- ---------- 材料列表 ---------- -->
    <screen-data-table
      class="escalation-material__table"
      :columns="columns"
      :data="rows"
      row-key="rowKey"
      :min-width="900"
      :max-height="disabled ? 320 : 260"
      :animated="false"
      :empty-text="disabled ? '该项目还没有上传材料' : '还没有材料，请先选择材料类型再上传'"
    >
      <template #index="{ index }">
        <span class="escalation-material__index">{{ padIndex(index + 1) }}</span>
      </template>

      <template #materialType="{ row }">
        <span v-if="row.materialType">{{ row.materialType }}</span>
        <span v-else class="escalation-material__danger">未选类型</span>
      </template>

      <template #fileName="{ row }">
        <button
          type="button"
          class="escalation-material__file"
          :title="`预览 ${row.fileName}`"
          @click="handlePreview(row)"
        >
          {{ row.fileName }}
        </button>
      </template>

      <template #fileSize="{ row }">
        {{ row.readableSize || formatSize(row.fileSize) }}
      </template>

      <template #version="{ row }">
        <span class="escalation-material__version">v{{ row.version || 1 }}</span>
        <screen-tag v-if="isSupplement(row)" tone="warning" size="sm">补充材料</screen-tag>
      </template>

      <template #uploadTime="{ row }">
        {{ row.uploadTime || '—' }}
      </template>

      <template #action="{ row }">
        <span class="escalation-material__actions">
          <button type="button" class="escalation-material__link" @click="handleDownload(row)">下载</button>
          <button type="button" class="escalation-material__link" @click="handlePreview(row)">预览</button>

          <screen-popconfirm
            v-if="!disabled"
            title="删除后不可恢复，确定删除该材料吗？"
            :description="row.fileName || ''"
            width="264"
            @confirm="handleRemove(row)"
          >
            <button type="button" class="escalation-material__link is-danger">删除</button>
          </screen-popconfirm>
        </span>
      </template>
    </screen-data-table>

    <!-- ---------- 汇总与提醒 ---------- -->
    <div class="escalation-material__foot">
      <span class="escalation-material__total">
        共 <b>{{ rows.length }}</b> 个材料
        <template v-if="totalSizeText !== '—'"> · 合计 {{ totalSizeText }}</template>
      </span>

      <!-- 缺类型是提交失败的常见原因，这里常驻提示（不是一闪而过的 toast） -->
      <span v-if="!disabled && missingTypeCount" class="escalation-material__warn" role="status">
        <screen-icon name="alert-triangle" :size="13" />
        有 {{ missingTypeCount }} 个材料还没选择材料类型，请补齐后再提交
      </span>
      <span v-else-if="supplementCount" class="escalation-material__muted">
        其中补充材料 {{ supplementCount }} 个
      </span>
    </div>
  </div>
</template>

<script>
import Vue from 'vue'
import {
  ScreenDataTable,
  ScreenTag,
  ScreenSelect,
  ScreenRadioGroup,
  ScreenUpload,
  ScreenIcon,
  ScreenPopconfirm,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { JEECG_ACCESS_TOKEN } from '@/store/mutation-types'
import { javaUploadUrl, getJavaFileAccessHttpUrl } from '@/api/manageJava'
import {
  queryMaterialList,
  saveMaterial,
  deleteMaterial,
  downloadMaterial,
  previewMaterial,
} from '@/api/land/escalation'
import {
  MATERIAL_TYPE_OPTIONS,
  MATERIAL_ALLOWED_EXT,
  MATERIAL_ALLOWED_EXT_TEXT,
  MATERIAL_MAX_SIZE_MB,
  toOptions,
  formatSize,
  resolveExt,
  buildBizPath,
  nowText,
} from '../constants'

export default {
  name: 'EscalationMaterialTable',
  components: {
    ScreenDataTable,
    ScreenTag,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenUpload,
    ScreenIcon,
    ScreenPopconfirm,
  },
  props: {
    /** 本地模式下的材料数组（v-model） */
    value: { type: Array, default: () => [] },
    /** 已落库项目 ID；传入即进入「远程模式」 */
    projectId: { type: String, default: '' },
    /** 只读（详情页 / 审批页查看） */
    disabled: { type: Boolean, default: false },
  },
  data () {
    return {
      MATERIAL_ALLOWED_EXT,
      MATERIAL_ALLOWED_EXT_TEXT,
      MATERIAL_MAX_SIZE_MB,
      uploading: false,
      /** 本批上传的材料类型（必选） */
      uploadType: '',
      /** 补充标记：auto 按「同类型第 2 次」自动判定 / force 强制标记 */
      supplementMode: 'auto',
      supplementOptions: [
        { value: 'auto', label: '自动判定' },
        { value: 'force', label: '标记为补充材料' },
      ],
      /** 远程模式的数据源 */
      remoteRows: [],
      materialTypeOptions: toOptions(MATERIAL_TYPE_OPTIONS),
      /**
       * 上传地址指向 **Java 业务后端**（VUE_DATA_JAVA_URL）：
       * jeecg 的通用上传接口在 Java 端，中台地址会 404。
       */
      uploadAction: javaUploadUrl(),
      /** 原生上传不走 axios，没有拦截器加令牌，必须手动带 jeecg 自己的令牌 */
      uploadHeaders: { 'X-Access-Token': Vue.ls.get(JEECG_ACCESS_TOKEN) },
      columns: [
        { key: 'index', title: '#', width: 48, type: 'slot', align: 'center' },
        { key: 'materialType', title: '材料类型', width: 130, type: 'slot' },
        { key: 'fileName', title: '文件名', width: 260, type: 'slot' },
        { key: 'fileSize', title: '大小', width: 100, type: 'slot', align: 'right' },
        { key: 'version', title: '版本', width: 170, type: 'slot' },
        { key: 'uploadBy', title: '上传人', width: 110, placeholder: '—' },
        { key: 'uploadTime', title: '上传时间', width: 160, type: 'slot' },
        { key: 'action', title: '操作', width: 170, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    remoteMode () {
      return Boolean(this.projectId)
    },
    /** 统一的行视图：给每行补一个稳定 rowKey（临时文件还没有 id） */
    rows () {
      const list = this.remoteMode ? this.remoteRows : (this.value || [])
      return list.map((item, index) => Object.assign({}, item, {
        rowKey: item.id || item.storePath || `tmp-${index}`,
      }))
    },
    totalSizeText () {
      const total = this.rows.reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
      return formatSize(total)
    },
    missingTypeCount () {
      return this.rows.filter((item) => !item.materialType).length
    },
    supplementCount () {
      return this.rows.filter((item) => this.isSupplement(item)).length
    },
    /** 上传子目录：/escalation/{yyyy}/{MM} */
    bizPath () {
      return buildBizPath(new Date())
    },
  },
  watch: {
    projectId: {
      immediate: true,
      handler () {
        if (this.remoteMode) this.loadRemote()
      },
    },
  },
  methods: {
    formatSize,
    padIndex (index) {
      return String(index).padStart(2, '0')
    },
    /** 补充标记兼容后端返回的 0/1 */
    isSupplement (row) {
      return row && (row.isSupplement === 1 || row.isSupplement === '1' || row.isSupplement === true)
    },

    /* ---------------- 远程模式 ---------------- */

    loadRemote () {
      if (!this.projectId) return Promise.resolve()
      return queryMaterialList(this.projectId).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '材料列表加载失败')
          return
        }
        this.remoteRows = res.result || []
      })
    },

    /** 供父组件刷新（审批页登记意见后材料数会变化） */
    refresh () {
      return this.remoteMode ? this.loadRemote() : Promise.resolve()
    },

    /* ---------------- 上传 ---------------- */

    handleUploadSuccess ({ file, storePath }) {
      const name = (file && file.name) || ''
      if (!storePath) {
        toast.error(`文件「${name}」上传成功但没有返回存储路径，请检查上传接口`)
        return
      }
      if (!this.uploadType) {
        // ScreenUpload 不校验业务必填，这里兜一层：宁可不落库，也不要存成「无类型」的脏数据
        toast.warning('请先选择「材料类型」再上传材料')
        return
      }

      const material = this.buildMaterial(file, storePath)
      if (!this.remoteMode) {
        this.$emit('input', (this.value || []).concat([material]))
        toast.success(`「${name}」上传成功`)
        this.$emit('change')
        return
      }

      saveMaterial(Object.assign({ projectId: this.projectId }, material)).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || `材料「${name}」保存失败`)
          return
        }
        toast.success(`「${name}」上传成功`)
        this.loadRemote()
        this.$emit('change')
      })
    },

    handleUploadError (message) {
      toast.error(message || '材料上传失败')
    },

    handleUploadReject (reason) {
      toast.warning(reason || '文件不符合上传要求')
    },

    /**
     * 版本号与补充标记：
     * 同一材料类型第 n 次上传 → version = n，n > 1 记为补充材料；
     * 勾了「标记为补充材料」时强制置位（version 仍按类型计数，不做额外 +1）。
     */
    buildMaterial (file, storePath) {
      const sameType = this.rows.filter((item) => item.materialType === this.uploadType)
      const version = sameType.length + 1
      return {
        id: null,
        materialType: this.uploadType,
        fileName: (file && file.name) || '',
        fileExt: resolveExt(file && file.name),
        fileSize: (file && file.size) || 0,
        fileMd5: null,
        storeType: 'local',
        storePath,
        previewPath: null,
        version,
        isSupplement: this.supplementMode === 'force' || version > 1 ? 1 : 0,
        uploadBy: this.currentRealname(),
        uploadTime: nowText(),
      }
    },

    /* ---------------- 行内操作 ---------------- */

    handleDownload (row) {
      if (row.id) {
        downloadMaterial(row.id)
        return
      }
      // 还没落库的临时文件只能走静态资源地址
      const url = getJavaFileAccessHttpUrl(row.storePath)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该材料还没有可访问的地址，请先保存项目')
      }
    },

    handlePreview (row) {
      if (row.id) {
        previewMaterial(row.id)
        return
      }
      const url = getJavaFileAccessHttpUrl(row.previewPath || row.storePath)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该材料还没有可访问的地址，请先保存项目')
      }
    },

    handleRemove (row) {
      if (this.remoteMode) {
        if (!row.id) return
        deleteMaterial(row.id).then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '删除失败')
            return
          }
          toast.success(res.message || '删除成功')
          this.loadRemote()
          this.$emit('change')
        })
        return
      }
      // 本地模式：整数组替换而不是就地改属性，保证 v-model 的消费方能拿到新数组
      const next = (this.value || []).filter((item) => {
        if (item.id && row.id) return item.id !== row.id
        return item.storePath !== row.storePath
      })
      this.$emit('input', next)
      this.$emit('change')
    },

    /* ---------------- 对外校验 / 汇总 ---------------- */

    /**
     * 提交前校验（父组件调用）。
     * @returns {string} '' 表示通过；否则是给用户看的错误文案
     */
    validate () {
      const list = this.rows
      if (!list.length) return '请至少上传一个提级论证材料'
      const missing = list.filter((item) => !item.materialType).length
      if (missing) return `有 ${missing} 个材料还没选择材料类型，请补齐后再提交`
      return ''
    },

    /** 本地模式下汇总给主表提交的材料数组（只提交后端认识的字段） */
    collect () {
      return (this.value || []).map((item) => ({
        id: item.id || null,
        materialType: item.materialType,
        fileName: item.fileName,
        fileExt: item.fileExt || resolveExt(item.fileName),
        fileSize: item.fileSize,
        fileMd5: item.fileMd5 || null,
        storeType: item.storeType || 'local',
        storePath: item.storePath,
        previewPath: item.previewPath || null,
        version: item.version || 1,
        isSupplement: item.isSupplement ? 1 : 0,
      }))
    },

    /* ---------------- 工具 ---------------- */

    currentRealname () {
      const getters = (this.$store && this.$store.getters) || {}
      const info = getters.userInfo || {}
      return info.realname || info.username || ''
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-material {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-width: 0;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
    padding: var(--screen-space-3);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__type {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;

    // 类型下拉给个固定宽度，避免切换时整条工具条重排
    /deep/ .screen-select {
      width: 150px;
    }
  }

  &__label {
    flex: none;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;
  }

  &__required {
    font-style: normal;
    color: var(--screen-danger);
  }

  &__hint {
    flex: 1 1 240px;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);

    /* 没选材料类型时的提示：既要文字也要颜色，不能只靠颜色 */
    &.is-strong {
      color: var(--screen-warning);
    }
  }

  // 表格自身限高（max-height 属性）后内部滚动，这里不用再设高度
  &__table {
    min-width: 0;
  }

  &__index {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  &__file {
    display: block;
    max-width: 100%;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    color: var(--screen-accent);
    text-align: left;
    background: none;
    border: 0;
    cursor: pointer;
    .screen-ellipsis();
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
      text-decoration: underline;
    }
  }

  &__version {
    margin-right: var(--screen-space-2);
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    justify-content: center;
  }

  &__link {
    .screen-link-action();
  }

  &__danger {
    color: var(--screen-danger);
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  &__warn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--screen-warning);
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__total {
    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-accent-soft);
    }
  }
}
</style>
