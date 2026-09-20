<template>
  <!--
    ArchiveFileTable 卷内文件表
    --------------------------------
    档案的「卷内文件」列表 + 上传。它是新增/编辑档案弹窗的第三段，也是档案详情页的
    第二个页签，所以通过 `disabled` 区分「可编辑」与「只读」。

    v-model 契约：一个文件对象数组，字段与后端 t_archive_file 对齐：
      { id, categoryId, categoryName, fileName, fileTitle, fileExt,
        fileSize, fileMd5, storeType, storePath, status, sortNo }

    业务流程（对照后端 ArchiveServiceImpl 的校验）：
      1. 先选「批量上传默认类别」，再上传文件；
      2. 文件走 jeecg 通用上传接口 /sys/common/upload，返回的是**相对存储路径**，
         它作为 storePath 提交给 /land/archive/add|edit；
      3. 每个文件必须有一个**末级**档案类别，否则后端会拒绝保存，
         所以 validate() 会把缺类别的情况明确报出来，而不是等提交后报 500。

    为什么把校验放在这里：
      父弹窗只负责调 validate() 并展示返回的文案，文件相关的规则（至少一个文件、
      每个文件都要有类别）留在本组件，避免规则散落到两个文件里。
  -->
  <div class="archive-files">
    <!-- ---------- 上传工具条 ---------- -->
    <div v-if="!disabled" class="archive-files__toolbar">
      <div class="archive-files__default-category">
        <span class="archive-files__toolbar-label">批量上传默认类别</span>
        <category-picker
          :value="defaultCategoryId"
          :clearable="false"
          placeholder="先选类别再上传"
          aria-label="批量上传默认类别"
          @input="defaultCategoryId = $event"
        />
      </div>

      <screen-upload
        ref="uploader"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :allowed-ext="ALLOWED_EXT"
        :allowed-ext-text="ALLOWED_EXT_TEXT"
        :max-size-mb="MAX_SIZE_MB"
        :disabled="uploading"
        button-text="选择文件上传"
        @success="handleUploadSuccess"
        @error="handleUploadError"
        @reject="handleUploadReject"
        @uploading-change="uploading = $event"
      />

      <span class="archive-files__hint">
        支持 {{ ALLOWED_EXT_TEXT }}，单文件不超过 {{ MAX_SIZE_MB }}MB
      </span>
    </div>

    <!-- ---------- 文件列表 ---------- -->
    <screen-data-table
      class="archive-files__table"
      :columns="columns"
      :data="rows"
      row-key="rowKey"
      :min-width="860"
      :max-height="disabled ? 320 : 260"
      :animated="false"
      empty-text="还没有卷内文件，请先上传"
    >
      <template #index="{ index }">
        <span class="archive-files__index">{{ padIndex(index + 1) }}</span>
      </template>

      <template #fileName="{ row }">
        <button
          type="button"
          class="archive-files__name"
          :title="`预览 / 下载 ${row.fileName}`"
          @click="handlePreview(row)"
        >
          {{ row.fileName }}
        </button>
        <span v-if="row.fileTitle && row.fileTitle !== row.fileName" class="archive-files__title">
          {{ row.fileTitle }}
        </span>
      </template>

      <template #category="{ row, index }">
        <category-picker
          v-if="!disabled"
          :ref="`rowCategory-${index}`"
          :value="row.categoryId"
          :invalid="!row.categoryId"
          :clearable="false"
          placeholder="选择末级类别"
          :aria-label="`为文件 ${row.fileName} 选择档案类别`"
          @change="handleCategoryChange(index, $event)"
        />
        <span v-else :title="row.categoryName || ''">{{ row.categoryName || '未分类' }}</span>
      </template>

      <template #fileSize="{ row }">
        {{ row.readableSize || formatSize(row.fileSize) }}
      </template>

      <template #status="{ row }">
        <screen-tag :tone="fileStatusTone(row.status)" size="sm">{{ row.status || '已归档' }}</screen-tag>
      </template>

      <template #action="{ row, index }">
        <span class="archive-files__actions">
          <button type="button" class="archive-files__link" @click="handlePreview(row)">下载</button>
          <button
            v-if="!disabled" type="button"
            class="archive-files__link is-danger"
            @click="handleRemove(index)"
          >
            移除
          </button>
        </span>
      </template>
    </screen-data-table>

    <!-- ---------- 汇总与提醒 ---------- -->
    <div class="archive-files__foot">
      <span class="archive-files__total">
        共 <b>{{ rows.length }}</b> 个文件
        <template v-if="totalSizeText !== '—'"> · {{ totalSizeText }}</template>
      </span>

      <!-- 缺类别是提交失败的常见原因，这里常驻提示（不是一闪而过的 toast） -->
      <span v-if="missingCategoryCount" class="archive-files__warn" role="status">
        <screen-icon name="alert-triangle" :size="13" />
        有 {{ missingCategoryCount }} 个文件还没选择档案类别，请补齐后再提交
      </span>
    </div>
  </div>
</template>

<script>
import Vue from 'vue'
import {
  ScreenDataTable,
  ScreenTag,
  ScreenUpload,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { archiveUrl, buildJavaDownloadUrl } from '@/api/land/archive'
import { javaUploadUrl, getJavaFileAccessHttpUrl } from '@/api/manageJava'
import CategoryPicker from './CategoryPicker.vue'
import {
  ALLOWED_EXT,
  ALLOWED_EXT_TEXT,
  MAX_SIZE_MB,
  formatSize,
  fileStatusTone,
  resolveExt,
  stripExt,
} from '../constants'

export default {
  name: 'ArchiveFileTable',
  components: { ScreenDataTable, ScreenTag, ScreenUpload, ScreenIcon, CategoryPicker },
  props: {
    /** 文件数组（v-model） */
    value: { type: Array, default: () => [] },
    /** 上传的业务子目录，例如 /archive/2026/09 */
    bizPath: { type: String, default: '/archive' },
    /** 只读模式（档案详情页） */
    disabled: { type: Boolean, default: false },
  },
  data () {
    return {
      ALLOWED_EXT,
      ALLOWED_EXT_TEXT,
      MAX_SIZE_MB,
      uploading: false,
      /** 批量上传时给新文件用的默认类别 */
      defaultCategoryId: '',
      /**
       * 上传地址指向 **Java 业务后端**（VUE_DATA_JAVA_URL）。
       * jeecg 的通用上传接口 /sys/common/upload 在 Java 端，不在中台上；
       * 用中台地址（domianURL / VUE_APP_API_BASE_URL）会 404。
       */
      uploadAction: javaUploadUrl(),
      /** 原生上传不走 axios，没有拦截器加令牌，需要手动带上（Java 端 JwtFilter 的首选来源） */
      uploadHeaders: { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) },
      columns: [
        { key: 'index', title: '#', width: 48, type: 'slot', align: 'center' },
        { key: 'fileName', title: '文件名', width: 260, type: 'slot' },
        { key: 'category', title: '档案类别', width: 240, type: 'slot' },
        { key: 'fileSize', title: '大小', width: 100, type: 'slot', align: 'right' },
        { key: 'status', title: '状态', width: 90, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 110, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    /** 给每行补一个稳定的 rowKey：临时文件（尚未落库）还没有 id */
    rows () {
      return (this.value || []).map((item, index) => Object.assign({}, item, {
        rowKey: item.id || item.storePath || `tmp-${index}`,
      }))
    },
    totalSizeText () {
      const total = (this.value || []).reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
      return formatSize(total)
    },
    missingCategoryCount () {
      return (this.value || []).filter((item) => !item.categoryId).length
    },
  },
  methods: {
    formatSize,
    fileStatusTone,
    padIndex (index) {
      return String(index).padStart(2, '0')
    },

    /* ---------------- 上传 ---------------- */

    /** 上传成功：把后端返回的相对路径包装成一条卷内文件记录 */
    handleUploadSuccess ({ file, storePath }) {
      const name = file && file.name ? file.name : ''
      if (!storePath) {
        toast.error(`文件「${name}」上传成功但没有返回存储路径，请检查上传接口`)
        return
      }
      const next = (this.value || []).concat([
        {
          id: null,
          categoryId: this.defaultCategoryId || '',
          categoryName: '',
          fileName: name,
          fileTitle: stripExt(name),
          fileExt: resolveExt(name),
          fileSize: file && file.size ? file.size : 0,
          fileMd5: null,
          storeType: 'local',
          storePath,
          status: '已归档',
          sortNo: (this.value || []).length + 1,
        },
      ])
      this.$emit('input', next)
      if (!this.defaultCategoryId) {
        toast.warning('文件已上传，请为它选择档案类别')
      }
    },
    handleUploadError (message) {
      toast.error(message || '文件上传失败')
    },
    handleUploadReject (reason) {
      toast.warning(reason || '文件不符合上传要求')
    },

    /* ---------------- 列表操作 ---------------- */

    /**
     * 改某个文件的类别。
     * 用「整数组替换」而不是就地改属性：v-model 的消费方（父弹窗）拿到的是新数组，
     * 变更可预测，也不会因为对象被外部复用而互相污染。
     *
     * 类别名从该行的 CategoryPicker 上取（它持有类别树）。
     * 后端保存时会用 categoryId 重算 categoryName / categoryPath，
     * 这里先填上只是为了保存前也能显示正确类别，不影响最终落库值。
     */
    handleCategoryChange (index, categoryId) {
      const picker = this.$refs[`rowCategory-${index}`]
      const node = picker && typeof picker.findNode === 'function' ? picker.findNode(categoryId) : null
      const categoryName = node ? node.fullPathName || node.name || '' : ''

      const next = (this.value || []).map((item, position) => {
        if (position !== index) return item
        return Object.assign({}, item, {
          categoryId: categoryId || '',
          categoryName,
          // path 也交给后端重算，避免本地拼错前缀导致子树检索不准
          categoryPath: '',
        })
      })
      this.$emit('input', next)
    },
    handleRemove (index) {
      const next = (this.value || []).filter((item, position) => position !== index)
      this.$emit('input', next)
    },
    /**
     * 预览 / 下载。
     * 已落库的文件走 Java 后端的 /land/archive/file/download（服务端会记一条
     * 「下载」操作记录，并且能校验文件是否真的还在磁盘上）；
     * 刚上传还没保存的临时文件只能走 Java 后端的静态访问 /sys/common/static。
     */
    handlePreview (row) {
      if (row.id) {
        window.open(buildJavaDownloadUrl(archiveUrl.fileDownload, { id: row.id }), '_blank')
        return
      }
      const url = getJavaFileAccessHttpUrl(row.url || row.storePath)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该文件还没有可访问的地址，请先保存档案')
      }
    },

    /* ---------------- 对外校验 ---------------- */

    /**
     * 供父弹窗在提交前调用。
     * @returns {string} '' 表示通过；否则是给用户看的错误文案
     */
    validate () {
      const files = this.value || []
      if (!files.length) return '请至少上传一个档案文件'
      const missing = files.filter((item) => !item.categoryId).length
      if (missing) return `有 ${missing} 个文件还没选择档案类别，请补齐后再提交`
      return ''
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-files {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-width: 0;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
  }

  &__default-category {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    // 类别选择器给个固定宽度，避免切换类别时整条工具条重排
    /deep/ .screen-select,
    /deep/ .screen-tree-select {
      width: 240px;
    }
  }

  &__toolbar-label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;
  }

  &__hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
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

  &__name {
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

  &__title {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        color: #ffab9f;
      }
    }
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

  &__warn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--screen-warning);
  }
}
</style>
