<template>
  <!--
    FacilityImportPanel 配套信息批量导入（方案 2.3.1（三）「配套信息批量导入管理」）
    --------------------------------
    结构与「经营性用地批量导入」面板一致，但多一块**孤儿跟进卡**：
    批量导入最容易出的问题是「我到底导进去几条、哪几条挂不上宗地」。
    结果面板关掉就没了，所以：
      · 导入记录常驻（每次入库的结果留痕，含孤儿行数与是否允许孤儿）；
      · 最近一次导入有孤儿时，顶部直接给一张「孤儿待办」提示 ——
        孤儿要去催另一个岗位录宗地，这件事在旧系统里完全没有落脚点。

    ★ 「导入设置」放在弹窗里而不是面板上：
      策略与「允许孤儿」都是**校验口径**，只对某一次导入有意义；
      放在面板上会让人以为是全局配置，改一次就永久生效。
  -->
  <div class="facility-import-panel">
    <screen-panel class="facility-import-panel__guide" title="导入说明" collapsible>
      <div class="facility-import-panel__guide-body">
        <ol class="facility-import-panel__steps">
          <li>下载模板，按<b>第 1 行的英文字段名</b>对列填写（数据从第 3 行开始）</li>
          <li>点「批量导入」选择填好的 Excel，先<b>预览校验</b>（不写库，含孤儿清单）</li>
          <li>确认「会新增多少条、覆盖多少条、错在哪几行、哪些行挂不上宗地」后，再<b>确认入库</b></li>
        </ol>

        <div class="facility-import-panel__actions">
          <screen-button type="primary" icon="upload" @click="handleOpenImport">批量导入</screen-button>
          <screen-button icon="download" @click="handleDownloadTemplate">下载导入模板</screen-button>
          <screen-button icon="file-text" @click="fieldsVisible = true">查看字段说明</screen-button>
        </div>
      </div>
    </screen-panel>

    <!-- ★ 孤儿跟进：最近一次导入有孤儿时置顶提示，这是「待办」的落脚点 -->
    <section v-if="orphanAlert" class="facility-import-panel__orphan stage-hit">
      <span class="facility-import-panel__orphan-icon">
        <screen-icon name="alert-triangle" :size="16" />
      </span>
      <div class="facility-import-panel__orphan-body">
        <b>{{ orphanAlert.title }}</b>
        <span>{{ orphanAlert.desc }}</span>
      </div>
      <screen-button size="sm" icon="external-link" @click="handleGoLandEntry">去录入宗地</screen-button>
    </section>

    <screen-panel class="facility-import-panel__list" title="导入记录">
      <template #extra>
        <span class="facility-import-panel__total">
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
        :min-width="1460"
        empty-text="还没有导入记录"
      >
        <template #fileName="{ row }">
          <span class="facility-import-panel__file" :title="row.fileName || ''">
            {{ row.fileName || '—' }}
          </span>
        </template>

        <template #totalRows="{ row }">
          <span class="facility-import-panel__num">{{ row.totalRows || 0 }}</span>
        </template>

        <template #insertedRows="{ row }">
          <span class="facility-import-panel__num is-ok">{{ row.insertedRows || 0 }}</span>
        </template>

        <template #updatedRows="{ row }">
          <span class="facility-import-panel__num is-primary">{{ row.updatedRows || 0 }}</span>
        </template>

        <template #skippedRows="{ row }">
          <span class="facility-import-panel__num">{{ row.skippedRows || 0 }}</span>
        </template>

        <template #errorRows="{ row }">
          <span class="facility-import-panel__num" :class="{ 'is-error': row.errorRows > 0 }">
            {{ row.errorRows || 0 }}
          </span>
        </template>

        <!-- 孤儿行数是配套导入独有的列：它才是要跟进的那一项 -->
        <template #orphanRows="{ row }">
          <span class="facility-import-panel__num" :class="{ 'is-warning': row.orphanRows > 0 }">
            {{ row.orphanRows || 0 }}
          </span>
        </template>

        <template #allowOrphan="{ row }">
          <screen-tag :tone="row.allowOrphan ? 'warning' : 'muted'" size="sm">
            {{ row.allowOrphan ? '已允许' : '未允许' }}
          </screen-tag>
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
          <span class="facility-import-panel__summary" :title="row.errorSummary || ''">
            {{ row.errorSummary || '—' }}
          </span>
        </template>
      </screen-data-table>
    </screen-panel>

    <facility-import-modal ref="importModal" @ok="handleImported" />

    <!-- 字段说明：列定义一律来自后端 /fields，不在前端再抄一份 -->
    <screen-modal
      v-if="fieldsVisible"
      :visible.sync="fieldsVisible"
      title="配套导入模板字段说明"
      :width="1120"
      :show-footer="false"
      :body-max-height="'calc(100vh - 200px)'"
      @cancel="fieldsVisible = false"
    >
      <div class="facility-import-panel__fields">
        <p class="facility-import-panel__fields-hint">
          字段清单由后端接口下发（<code>/land/data/facilityImport/fields</code>），
          前端不硬编码列定义 —— 后端加列时这里会自动跟上，避免「按页面提示填的表被后端拒绝」。
          其中<b>必填</b>列共 {{ requiredFields.length }} 个：
          <b>{{ requiredFieldText }}</b>。
        </p>

        <p class="facility-import-panel__fields-hint is-warning">
          ★ 标着「历史标志位，只读」的 11 列（项建批复文件、施工许可…）是旧表的
          「有没有这份文件」的是/否标志位，只为了历史数据可导入而保留；
          新数据请到「配套附件管理」上传实际文件。
        </p>

        <screen-data-table
          :columns="fieldColumns"
          :data="fields"
          row-key="column"
          :min-width="960"
          :max-height="440"
          :animated="false"
          empty-text="字段说明加载失败，可重新打开本窗口重试"
        >
          <template #column="{ row }">
            <span class="facility-import-panel__mono">{{ row.column }}</span>
          </template>
          <template #label="{ row }">
            <span>{{ row.label }}</span>
            <span v-if="row.required" class="facility-import-panel__required">*</span>
          </template>
          <template #type="{ row }">
            <screen-tag tone="muted" size="sm">{{ fieldTypeText(row.type) }}</screen-tag>
          </template>
          <template #optionText="{ row }">
            <span class="facility-import-panel__option" :title="row.optionText || ''">
              {{ row.optionText || '自由文本' }}
            </span>
          </template>
          <template #sample="{ row }">
            <span class="facility-import-panel__mono">{{ row.sample || '—' }}</span>
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
  ScreenModal,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import FacilityImportModal from './FacilityImportModal.vue'
import {
  downloadFacilityTemplate,
  queryFacilityImportLogs,
  queryFacilityImportFields,
  fieldTypeText,
  DUPLICATE_STRATEGIES_FALLBACK,
  FACILITY_IMPORT_FIELDS_FALLBACK
} from '@/api/land/facilityImport'
import { formatTime } from '../constants'

/** 策略码 → 中文（本地映射，避免为了三个字再去请求一次 strategies 接口） */
const STRATEGY_TEXT = {
  reject: '重复即报错',
  skip: '跳过重复',
  update: '覆盖更新'
}

export default {
  name: 'FacilityImportPanel',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenTag,
    ScreenDataTable,
    ScreenModal,
    ScreenIcon,
    FacilityImportModal
  },
  data () {
    return {
      loading: false,
      logs: [],
      fields: [],
      fieldsVisible: false,
      columns: [
        { key: 'fileName', title: '文件名', width: 240, type: 'slot' },
        { key: 'totalRows', title: '解析行', width: 90, type: 'slot', align: 'right' },
        { key: 'insertedRows', title: '新增', width: 80, type: 'slot', align: 'right' },
        { key: 'updatedRows', title: '覆盖', width: 80, type: 'slot', align: 'right' },
        { key: 'skippedRows', title: '跳过重复', width: 100, type: 'slot', align: 'right' },
        { key: 'errorRows', title: '错误', width: 80, type: 'slot', align: 'right' },
        { key: 'orphanRows', title: '孤儿行', width: 90, type: 'slot', align: 'right' },
        { key: 'allowOrphan', title: '允许孤儿', width: 100, type: 'slot', align: 'center' },
        { key: 'strategy', title: '重复策略', width: 120, type: 'slot', align: 'center' },
        { key: 'aborted', title: '入库结果', width: 110, type: 'slot', align: 'center' },
        { key: 'errorSummary', title: '问题摘要', width: 320, type: 'slot' },
        { key: 'operatorName', title: '操作人', width: 110, formatter: (value, row) => value || row.operator || '—' },
        { key: 'createTime', title: '导入时间', width: 170, formatter: (value) => formatTime(value) }
      ],
      fieldColumns: [
        { key: 'column', title: '列名（第 1 行）', width: 200, type: 'slot' },
        { key: 'label', title: '中文说明（第 2 行）', width: 240, type: 'slot' },
        { key: 'type', title: '类型', width: 90, type: 'slot', align: 'center' },
        { key: 'optionText', title: '取值', width: 300, type: 'slot' },
        { key: 'sample', title: '示例', type: 'slot' }
      ]
    }
  },
  computed: {
    lastLog () {
      return this.logs.length ? this.logs[0] : null
    },
    /**
     * 孤儿跟进提示。
     * ★ 只对「最近一次导入」提示：更早那些批次的孤儿多半已经跟进过了，
     *   每次都弹会让提示变成噪音，最后没人看。
     * ★ 分两种语气：允许孤儿时是「请跟进」（warning），
     *   不允许孤儿时那次导入根本没写库，是「有行被拦下」（danger 更合适，
     *   但这里不阻断任何操作，所以仍用 warning 语气 + 明确的文案）。
     */
    orphanAlert () {
      const log = this.lastLog
      if (!log || !log.orphanRows) return null
      const count = Number(log.orphanRows) || 0
      if (log.allowOrphan) {
        return {
          title: `最近一次导入有 ${count} 行挂到了未登记宗地（孤儿行）`,
          desc: '这些配套在系统里没有归属的宗地，档案与收发文按宗地关联时挂不上。请按导出/回执里的孤儿清单核对宗地编号，或先录入对应宗地。'
        }
      }
      if (log.aborted) {
        return {
          title: `最近一次导入因 ${count} 行孤儿被拦下，整批未入库`,
          desc: '如果这些宗地确实还没录入，重新导入时勾选「允许挂到未登记宗地（记入孤儿清单）」即可照常导入。'
        }
      }
      return {
        title: `最近一次导入识别出 ${count} 行孤儿行`,
        desc: '这些行被跳过（未写入），请核对宗地编号是否写错，或先录入对应宗地。'
      }
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
      queryFacilityImportLogs(20)
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
      queryFacilityImportFields()
        .then((res) => {
          if (res && res.success && res.result && res.result.length) {
            this.fields = res.result
            return
          }
          this.fields = FACILITY_IMPORT_FIELDS_FALLBACK
        })
        .catch(() => {
          this.fields = FACILITY_IMPORT_FIELDS_FALLBACK
        })
    },

    handleOpenImport () {
      if (this.$refs.importModal) this.$refs.importModal.open()
    },

    handleDownloadTemplate () {
      downloadFacilityTemplate()
      toast.info('已开始下载模板，请稍候…')
    },

    /** 导入成功后刷新记录（用户最关心的就是「刚才那次导进去几条、有没有孤儿」） */
    handleImported () {
      this.loadLogs()
    },

    /** 孤儿要跟进 → 直接跳到宗地录入面板，省一次菜单点击 */
    handleGoLandEntry () {
      this.$emit('drill', { tab: 'land' })
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

.facility-import-panel {
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
    // flex-basis 必须是 0：用 auto 会按内容撑开，分页/表头会被挤出可见区
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

  /* ---------------- ★ 孤儿跟进条 ---------------- */
  &__orphan {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex: 0 0 auto;
    padding: var(--screen-space-2) var(--screen-space-3);
    background: rgba(245, 165, 36, 0.08);
    border: 1px solid var(--screen-warning);
    border-radius: var(--screen-radius);
  }

  &__orphan-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 28px;
    height: 28px;
    color: var(--screen-warning);
    background: rgba(245, 165, 36, 0.14);
    border-radius: 50%;
  }

  &__orphan-body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1 1 auto;
    min-width: 0;

    b {
      font-size: var(--screen-font-sm);
      font-weight: 600;
      color: var(--screen-warning);
    }

    span {
      font-size: var(--screen-font-xs);
      line-height: 1.7;
      color: var(--screen-text-sub);
    }
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

    &.is-warning {
      color: var(--screen-warning);
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

    &.is-warning {
      color: var(--screen-warning);
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
.facility-import-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
