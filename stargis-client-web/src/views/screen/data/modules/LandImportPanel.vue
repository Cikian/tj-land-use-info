<template>
  <!--
    LandImportPanel 经营性用地批量导入（方案 2.3.1（三）第 3 项）
    --------------------------------
    「批量导入」在旧系统里是一张独立页面（batchImportXjCommercialLand.html），
    这里收进数据管理的第一个页签，与后面的「配套信息批量导入」形成同一套节奏：
      上面 = 导入入口（模板 / 选文件 / 预览 / 确认 / 错误回执，全在弹窗里）
      中间 = 导入说明（字段字典来自后端 /fields，不硬编码列定义）
      下面 = 导入记录（每次入库的结果留痕，含错误与跳过行数）

    ★ 为什么「导入记录」放在面板里而不是弹窗里：
      批量导入最容易出的问题是「我到底导进去几条、哪几条被跳过了」。
      结果面板关掉就没了，所以记录必须常驻，且每次导入后自动刷新。
  -->
  <div class="land-import-panel">
    <screen-panel class="land-import-panel__guide" title="导入说明" collapsible>
      <div class="land-import-panel__guide-body">
        <ol class="land-import-panel__steps">
          <li>下载模板，按<b>第 1 行的英文字段名</b>对列填写（数据从第 3 行开始）</li>
          <li>点「批量导入」选择填好的 Excel，先<b>预览校验</b>（不写库）</li>
          <li>确认「会新增多少条、覆盖多少条、错在哪几行」后，再<b>确认入库</b></li>
        </ol>

        <div class="land-import-panel__actions">
          <screen-button type="primary" icon="upload" @click="handleOpenImport">批量导入</screen-button>
          <screen-button icon="download" @click="handleDownloadTemplate">下载导入模板</screen-button>
          <screen-button icon="file-text" @click="fieldsVisible = true">查看字段说明</screen-button>
        </div>
      </div>
    </screen-panel>

    <screen-panel class="land-import-panel__list" title="导入记录">
      <template #extra>
        <span class="land-import-panel__total">
          共 <b>{{ logs.length }}</b> 次导入
          <template v-if="lastLog">
            · 最近一次 <b>{{ formatTime(lastLog.createTime) }}</b>
          </template>
        </span>
        <screen-button size="sm" icon="reload" :loading="loading" @click="loadLogs">刷新</screen-button>
      </template>

      <screen-data-table
        :columns="columns"
        :data="logs"
        :loading="loading"
        row-key="id"
        :min-width="1280"
        empty-text="还没有导入记录"
      >
        <template #fileName="{ row }">
          <span class="land-import-panel__file" :title="row.fileName || ''">
            {{ row.fileName || '—' }}
          </span>
        </template>

        <template #totalRows="{ row }">
          <span class="land-import-panel__num">{{ row.totalRows || 0 }}</span>
        </template>

        <template #insertedRows="{ row }">
          <span class="land-import-panel__num is-ok">{{ row.insertedRows || 0 }}</span>
        </template>

        <template #updatedRows="{ row }">
          <span class="land-import-panel__num is-primary">{{ row.updatedRows || 0 }}</span>
        </template>

        <template #skippedRows="{ row }">
          <span class="land-import-panel__num">{{ row.skippedRows || 0 }}</span>
        </template>

        <template #errorRows="{ row }">
          <span class="land-import-panel__num" :class="{ 'is-error': row.errorRows > 0 }">
            {{ row.errorRows || 0 }}
          </span>
        </template>

        <template #strategy="{ row }">
          <screen-tag tone="muted" size="sm">{{ strategyText(row.duplicateStrategy) }}</screen-tag>
        </template>

        <template #aborted="{ row }">
          <screen-tag :tone="row.aborted ? 'danger' : 'success'" size="sm">
            {{ row.aborted ? '整批未入库' : '已入库' }}
          </screen-tag>
        </template>

        <template #errorSummary="{ row }">
          <span class="land-import-panel__summary" :title="row.errorSummary || ''">
            {{ row.errorSummary || '—' }}
          </span>
        </template>
      </screen-data-table>
    </screen-panel>

    <land-import-modal ref="importModal" @ok="handleImported" />

    <!-- 字段说明：列定义一律来自后端 /fields，不在前端再抄一份 -->
    <screen-modal
      :visible.sync="fieldsVisible"
      title="导入模板字段说明"
      :width="1080"
      :show-footer="false"
      :body-max-height="'calc(100vh - 200px)'"
      @cancel="fieldsVisible = false"
    >
      <div class="land-import-panel__fields">
        <p class="land-import-panel__fields-hint">
          字段清单由后端接口下发（<code>/land/data/import/fields</code>），
          前端不硬编码列定义 —— 后端加列时这里会自动跟上，避免「按页面提示填的表被后端拒绝」。
          其中<b>必填</b>列共 {{ requiredFields.length }} 个：
          <b>{{ requiredFieldText }}</b>。
        </p>

        <screen-data-table
          :columns="fieldColumns"
          :data="fields"
          row-key="column"
          :min-width="900"
          :max-height="420"
          :animated="false"
          empty-text="字段说明加载失败，可重新打开本窗口重试"
        >
          <template #column="{ row }">
            <span class="land-import-panel__mono">{{ row.column }}</span>
          </template>
          <template #label="{ row }">
            <span>{{ row.label }}</span>
            <span v-if="row.required" class="land-import-panel__required">*</span>
          </template>
          <template #type="{ row }">
            <screen-tag tone="muted" size="sm">{{ fieldTypeText(row.type) }}</screen-tag>
          </template>
          <template #optionText="{ row }">
            <span class="land-import-panel__option" :title="row.optionText || ''">
              {{ row.optionText || '自由文本' }}
            </span>
          </template>
          <template #sample="{ row }">
            <span class="land-import-panel__mono">{{ row.sample || '—' }}</span>
          </template>
        </screen-data-table>
      </div>
    </screen-modal>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenTag,
  ScreenDataTable,
  ScreenModal
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import LandImportModal from './LandImportModal.vue'
import {
  downloadLandImportTemplate,
  queryLandImportLogs,
  queryLandImportFields,
  fieldTypeText,
  DUPLICATE_STRATEGIES_FALLBACK,
  LAND_IMPORT_FIELDS_FALLBACK
} from '@/api/land/landImport'
import { formatTime } from '../constants'

/** 策略码 → 中文（本地映射，避免为了三个字再去请求一次 strategies 接口） */
const STRATEGY_TEXT = {
  reject: '重复即报错',
  skip: '跳过重复',
  update: '覆盖更新'
}

export default {
  name: 'LandImportPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenTag,
    ScreenDataTable,
    ScreenModal,
    LandImportModal
  },
  data () {
    return {
      loading: false,
      logs: [],
      fields: [],
      fieldsVisible: false,
      columns: [
        { key: 'fileName', title: '文件名', width: 260, type: 'slot' },
        { key: 'totalRows', title: '解析行', width: 90, type: 'slot', align: 'right' },
        { key: 'insertedRows', title: '新增', width: 80, type: 'slot', align: 'right' },
        { key: 'updatedRows', title: '覆盖', width: 80, type: 'slot', align: 'right' },
        { key: 'skippedRows', title: '跳过重复', width: 100, type: 'slot', align: 'right' },
        { key: 'errorRows', title: '错误', width: 80, type: 'slot', align: 'right' },
        { key: 'strategy', title: '重复策略', width: 120, type: 'slot', align: 'center' },
        { key: 'aborted', title: '入库结果', width: 110, type: 'slot', align: 'center' },
        { key: 'errorSummary', title: '问题摘要', width: 320, type: 'slot' },
        { key: 'operatorName', title: '操作人', width: 110, formatter: (value, row) => value || row.operator || '—' },
        { key: 'createTime', title: '导入时间', width: 170, formatter: (value) => formatTime(value) }
      ],
      fieldColumns: [
        { key: 'column', title: '列名（第 1 行）', width: 200, type: 'slot' },
        { key: 'label', title: '中文说明（第 2 行）', width: 220, type: 'slot' },
        { key: 'type', title: '类型', width: 90, type: 'slot', align: 'center' },
        { key: 'optionText', title: '取值', width: 280, type: 'slot' },
        { key: 'sample', title: '示例', type: 'slot' }
      ]
    }
  },
  computed: {
    lastLog () {
      return this.logs.length ? this.logs[0] : null
    },
    requiredFields () {
      return this.fields.filter((item) => item && item.required)
    },
    requiredFieldText () {
      const labels = this.requiredFields.map((item) => item.label)
      return labels.length ? labels.join('、') : '—'
    }
  },
  created () {
    this.loadLogs()
    this.loadFields()
  },
  methods: {
    fieldTypeText,
    formatTime,

    loadLogs () {
      this.loading = true
      queryLandImportLogs(20)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '导入记录加载失败')
            return
          }
          this.logs = res.result || []
        })
        .catch(() => {
          this.logs = []
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 字段字典。
     * ★ 接口不可用时退化为本地兜底，而不是留一张空表：
     *   字段说明是「用户能自己把表填对」的唯一依据，空着等于让人盲填。
     */
    loadFields () {
      queryLandImportFields()
        .then((res) => {
          if (res && res.success && res.result && res.result.length) {
            this.fields = res.result
            return
          }
          this.fields = LAND_IMPORT_FIELDS_FALLBACK
        })
        .catch(() => {
          this.fields = LAND_IMPORT_FIELDS_FALLBACK
        })
    },

    handleOpenImport () {
      if (this.$refs.importModal) this.$refs.importModal.open()
    },

    handleDownloadTemplate () {
      downloadLandImportTemplate()
      toast.info('已开始下载模板，请稍候…')
    },

    /** 导入成功后刷新记录（用户最关心的就是「刚才那次导进去几条」） */
    handleImported () {
      this.loadLogs()
    },

    strategyText (code) {
      if (STRATEGY_TEXT[code]) return STRATEGY_TEXT[code]
      const hit = DUPLICATE_STRATEGIES_FALLBACK.find((item) => item.code === code)
      return hit ? hit.label : code || '—'
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-import-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  // 说明区按内容高度，记录表吃掉剩余高度
  &__guide {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
  }

  &__guide-body {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-4);
    flex-wrap: wrap;
  }

  &__steps {
    flex: 1 1 420px;
    min-width: 0;
    margin: 0;
    padding-left: 20px;
    font-size: var(--screen-font-sm);
    line-height: 1.9;
    color: var(--screen-text-sub);

    li + li {
      margin-top: 2px;
    }

    b {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
  }

  &__total {
    margin-right: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-text);
    }
  }

  &__file {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;

    &.is-ok {
      color: var(--screen-success);
    }

    &.is-primary {
      color: var(--screen-accent);
    }

    &.is-error {
      color: var(--screen-danger);
    }
  }

  &__summary {
    display: block;
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  /* ---------------- 字段说明 ---------------- */
  &__fields {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
  }

  &__fields-hint {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.8;
    color: var(--screen-text-sub);

    b {
      color: var(--screen-accent);
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

  &__mono {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
  }

  &__required {
    margin-left: 2px;
    color: var(--screen-danger);
  }

  &__option {
    display: block;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.land-import-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
