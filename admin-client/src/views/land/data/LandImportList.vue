<template>
  <a-card :bordered="false" class="land-import">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">经营性用地批量导入</h2>
        <p class="page-head__desc">
          Excel 批量导入出让宗地（方案 2.3.1（三）第 3 项）。
          四步走：<b>下载模板 → 填表 → 预览校验 → 确认入库</b>；
          校验不通过时默认<b>整批不入库</b>，并可下载<b>错误回执</b>（保留你填的内容 + 追加错误原因列），
          改完那几行直接重传即可。列名沿用旧库字段名，中心手里的旧台账 Excel 可以直接导入。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:data:import'" icon="download" @click="handleDownloadTemplate">下载导入模板</a-button>
        <a-button v-has="'land:data:import'" type="primary" icon="upload" @click="handleUpload">上传 Excel 导入</a-button>
        <a-button icon="reload" :loading="logLoading" @click="loadLogs">刷新记录</a-button>
      </div>
    </div>

    <!-- ============ 四步流程 ============ -->
    <div class="import-steps">
      <div v-for="(step, index) in steps" :key="step.key" class="import-step">
        <div class="import-step__no">{{ index + 1 }}</div>
        <div class="import-step__body">
          <div class="import-step__title">{{ step.title }}</div>
          <div class="import-step__desc">{{ step.desc }}</div>
        </div>
      </div>
    </div>

    <!-- ============ 能力指标 ============ -->
    <div class="import-stats">
      <a-row :gutter="12">
        <a-col v-for="card in statCards" :key="card.key" :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- ============ 本次导入结果 ============ -->
    <div v-if="result" class="result-panel">
      <div class="result-panel__head">
        <span class="result-panel__title">
          {{ phase === 'imported' ? '导入结果' : '预览校验结果' }}
          <span class="result-panel__file">{{ lastFileName }}</span>
        </span>
        <div class="result-panel__actions">
          <a-button
            v-if="errorCount"
            v-has="'land:data:import'"
            size="small"
            icon="download"
            :loading="reporting"
            @click="handleDownloadReport">下载错误回执（{{ errorCount }} 条）</a-button>
          <a-button size="small" icon="close" @click="result = null">收起结果</a-button>
        </div>
      </div>

      <!-- 计数 -->
      <div class="result-stats">
        <span class="result-stat">
          解析数据行 <b>{{ result.totalRows }}</b>
        </span>
        <span class="result-stat">
          校验通过 <b class="is-primary">{{ result.validRows }}</b>
        </span>
        <span class="result-stat">
          <template v-if="phase === 'imported'">
            已新增 <b class="is-ok">{{ result.insertedRows }}</b>
          </template>
          <template v-else>
            将新增 <b class="is-primary">{{ result.willInsertRows }}</b>
          </template>
        </span>
        <span class="result-stat">
          <template v-if="phase === 'imported'">
            已覆盖 <b class="is-ok">{{ result.updatedRows }}</b>
          </template>
          <template v-else>
            将覆盖 <b class="is-primary">{{ result.willUpdateRows }}</b>
          </template>
        </span>
        <span v-if="result.skippedDuplicates" class="result-stat">
          跳过重复 <b>{{ result.skippedDuplicates }}</b>
        </span>
        <span class="result-stat">
          错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
        </span>
      </div>

      <!-- 结论提示 -->
      <a-alert
        v-if="phase === 'imported' && result.insertedRows + result.updatedRows > 0"
        class="result-alert"
        type="success"
        showIcon
        :message="importSummary(result)" />
      <a-alert
        v-else-if="phase === 'imported'"
        class="result-alert"
        type="warning"
        showIcon
        message="本次没有写入任何数据（存在错误行，且未勾选「跳过错误行」）" />
      <a-alert
        v-else-if="result.aborted"
        class="result-alert"
        type="warning"
        showIcon
        message="本次不会写入任何数据：预览只校验、不写库；直接点「确认导入」也会被拒绝，除非勾选「跳过错误行」" />
      <a-alert
        v-else
        class="result-alert"
        type="info"
        showIcon
        :message="`校验通过：确认导入将新增 ${result.willInsertRows} 条、覆盖更新 ${result.willUpdateRows} 条`" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="result-alert"
        type="info"
        showIcon
        :message="item" />
      <a-alert
        v-for="(item, index) in result.notices || []"
        :key="'n' + index"
        class="result-alert"
        type="warning"
        showIcon
        :message="item" />

      <!-- 错误明细（按行聚合，避免同一行多个问题被看成多行要改） -->
      <div v-if="errorCount" class="error-block">
        <div class="error-block__head">
          <span class="error-block__title">
            错误清单：<b>{{ errorRows.length }}</b> 行有问题、共 <b>{{ errorCount }}</b> 条
          </span>
          <span class="error-block__hint">行号与 Excel 左侧行号一致，可直接跳过去改；改完下载回执或重传文件</span>
        </div>
        <a-table
          rowKey="key"
          size="small"
          :columns="errorColumns"
          :dataSource="errorRows"
          :pagination="errorPagination"
          :scroll="{ y: 240 }">
          <template slot="columns" slot-scope="text">
            <a-tag v-for="item in text" :key="item">{{ item }}</a-tag>
          </template>
          <template slot="message" slot-scope="text">
            <span class="error-block__message">{{ text }}</span>
          </template>
        </a-table>
      </div>

      <!-- 确认导入 -->
      <div v-if="phase !== 'imported'" class="confirm-bar">
        <div class="confirm-bar__left">
          <a-checkbox v-model="skipErrorRows" :disabled="!errorCount">
            跳过错误行，只导入正确的行
            <span v-if="errorCount" class="confirm-bar__count">（{{ importableCount }} 行）</span>
          </a-checkbox>
          <a-tooltip title="「重复即报错」不会写库也不会改动已有数据；「跳过重复」只导新增；「覆盖更新」会按单元格有值才覆盖，空单元格保持库内原值">
            <span class="confirm-bar__strategy">
              重复宗地编号处理：
              <a-select
                v-model="duplicateStrategy"
                size="small"
                style="width: 260px"
                :options="strategyOptions"
                @change="handleStrategyChange" />
            </span>
          </a-tooltip>
        </div>
        <a-button
          v-has="'land:data:import'"
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="errorCount > 0 && !skipErrorRows"
          @click="handleConfirm">
          确认导入{{ canImport && importableCount ? '（' + importableCount + ' 行）' : '' }}
        </a-button>
      </div>
      <div v-else class="confirm-bar confirm-bar--done">
        <span class="confirm-bar__done">
          导入留痕已记录，可在下方「导入记录」里查看本次与历史导入的结果。
        </span>
        <a-button icon="reload" @click="loadLogs">刷新导入记录</a-button>
      </div>
    </div>

    <!-- ============ 导入记录 ============ -->
    <div class="log-panel">
      <div class="log-panel__head">
        <span class="log-panel__title">导入记录</span>
        <span class="log-panel__hint">
          每次「确认导入」都会留痕（含整批未入库的那次），用于事后核对「这批到底进去没有、哪几条没进去」
        </span>
      </div>
      <a-table
        rowKey="id"
        size="small"
        :columns="logColumns"
        :dataSource="logs"
        :loading="logLoading"
        :pagination="{ pageSize: 10, size: 'small', showTotal: total => `共 ${total} 次导入` }"
        :scroll="{ x: 1200 }">
        <template slot="strategy" slot-scope="text">
          {{ strategyLabel(text) }}
        </template>
        <template slot="result" slot-scope="text, record">
          <span v-if="record.aborted" class="is-error">整批未入库</span>
          <span v-else>
            新增 <b>{{ record.insertedRows || 0 }}</b> / 覆盖 <b>{{ record.updatedRows || 0 }}</b>
            <span v-if="record.skippedRows"> / 跳过 <b>{{ record.skippedRows }}</b></span>
          </span>
        </template>
        <template slot="errorRows" slot-scope="text, record">
          <a-tooltip v-if="record.errorSummary" :title="record.errorSummary">
            <a-tag :color="text ? 'orange' : undefined">{{ text || 0 }}</a-tag>
          </a-tooltip>
          <a-tag v-else>{{ text || 0 }}</a-tag>
        </template>
      </a-table>
    </div>

    <!-- ============ 字段说明（可折叠） ============ -->
    <div class="field-panel">
      <div class="field-panel__head" @click="fieldCollapsed = !fieldCollapsed">
        <span class="field-panel__title">
          字段说明（模板共 {{ fields.length }} 列，其中必填 {{ requiredCount }} 列）
        </span>
        <span class="field-panel__hint">
          模板第 1 行是英文字段名（程序按它认列），第 2 行是中文说明，数据从第 3 行开始；
          删列、调列顺序都可以，但不要改第 1 行的英文名
        </span>
        <a-icon class="field-panel__toggle" :type="fieldCollapsed ? 'down' : 'up'" />
      </div>
      <div v-show="!fieldCollapsed" class="field-panel__body">
        <a-table
          rowKey="column"
          size="small"
          :columns="fieldColumns"
          :dataSource="fields"
          :pagination="false"
          :scroll="{ y: 320 }">
          <template slot="column" slot-scope="text, record">
            <span class="field-panel__column">{{ text }}</span>
            <a-tag v-if="record.required" color="red">必填</a-tag>
          </template>
          <template slot="type" slot-scope="text">
            <a-tag :color="fieldTypeColor(text)">{{ fieldTypeText(text) }}</a-tag>
          </template>
          <template slot="optionText" slot-scope="text">
            <span class="field-panel__option">{{ text || '—' }}</span>
          </template>
          <template slot="sample" slot-scope="text">
            <span class="field-panel__sample">{{ text || '—' }}</span>
          </template>
        </a-table>
      </div>
    </div>

    <!-- ============ 导入弹窗 ============ -->
    <land-import-modal ref="importModal" @ok="handleImportDone" />
  </a-card>
</template>

<script>
  import LandImportModal from './modules/LandImportModal'
  import {
    downloadImportTemplate,
    downloadErrorReport,
    confirmLandImport,
    queryDuplicateStrategies,
    queryImportFields,
    queryImportLogs,
    importSummary,
    groupErrorsByRow,
    fieldTypeText,
    fieldTypeColor,
    DUPLICATE_STRATEGIES_FALLBACK,
    LAND_IMPORT_FIELDS_FALLBACK
  } from '@/api/land/landImport'

  /**
   * 经营性用地批量导入（方案 2.3.1（三）第 3 项）
   *
   * 本页面负责三件事：
   *  1. 提供「下载模板 / 上传导入」两个入口，并把导入弹窗的四步流程串起来；
   *  2. 把「预览/入库结果」用同一套面板渲染（计数 + 结论 + 错误清单 + 确认入库），
   *     这样用户看到的东西在预览与入库两个阶段是一致的，不会「预览说 3 条、导入说 0 条」；
   *  3. 保留导入留痕（每次确认导入都记一条），用于事后核对。
   *
   * ★ 为什么「错误回执」按钮放在结果面板上而不是弹窗里：
   *   用户在弹窗里已经看过错误清单、点了「确认导入」或直接关掉弹窗之后，
   *   回到页面上仍然要能再下一次回执（他可能想拿回执去核对，或者第一次忘了下）。
   *   回执用的是「当次上传的原文件 + 当次结果」，所以在页面这一层要留住这两样东西。
   */
  export default {
    name: 'LandImportList',
    components: { LandImportModal },
    data () {
      return {
        // ---- 字段字典 / 策略 ----
        fields: LAND_IMPORT_FIELDS_FALLBACK,
        strategies: DUPLICATE_STRATEGIES_FALLBACK,
        // ---- 导入结果 ----
        result: null,
        phase: '', // preview | imported
        file: null,
        lastFileName: '',
        duplicateStrategy: 'reject',
        skipErrorRows: false,
        importing: false,
        reporting: false,
        // ---- 导入记录 ----
        logs: [],
        logLoading: false,
        // ---- 折叠态 ----
        fieldCollapsed: true,
        // ---- 错误清单分页 ----
        errorPagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 行`
        },
        errorColumns: [
          { title: 'Excel 行号', dataIndex: 'rowNum', width: 100 },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 200, customRender: text => text || '—' },
          { title: '出错字段', dataIndex: 'columns', width: 220, scopedSlots: { customRender: 'columns' } },
          { title: '错误原因', dataIndex: 'message', scopedSlots: { customRender: 'message' } }
        ],
        logColumns: [
          { title: '导入时间', dataIndex: 'createTime', width: 170 },
          { title: '文件名', dataIndex: 'fileName', width: 220, customRender: text => text || '—' },
          { title: '操作人', dataIndex: 'operatorName', width: 110, customRender: text => text || '—' },
          { title: '解析行数', dataIndex: 'totalRows', width: 100 },
          { title: '重复策略', dataIndex: 'duplicateStrategy', width: 150, scopedSlots: { customRender: 'strategy' } },
          { title: '结果', dataIndex: 'result', width: 220, scopedSlots: { customRender: 'result' } },
          { title: '错误', dataIndex: 'errorRows', width: 90, scopedSlots: { customRender: 'errorRows' } }
        ],
        fieldColumns: [
          { title: '列名（第 1 行）', dataIndex: 'column', width: 180, scopedSlots: { customRender: 'column' } },
          { title: '中文名（第 2 行）', dataIndex: 'label', width: 220 },
          { title: '类型', dataIndex: 'type', width: 90, scopedSlots: { customRender: 'type' } },
          { title: '取值 / 说明', dataIndex: 'optionText', scopedSlots: { customRender: 'optionText' } },
          { title: '示例', dataIndex: 'sample', width: 200, scopedSlots: { customRender: 'sample' } }
        ],
        steps: [
          { key: 'template', title: '下载模板', desc: '表头是英文字段名 + 中文说明，数据从第 3 行开始' },
          { key: 'fill', title: '填表', desc: '空着的列按「不填」处理；是/否列可填 √/×/1/0' },
          { key: 'preview', title: '预览校验', desc: '不写库，先看会新增/覆盖多少条、错在哪几行' },
          { key: 'confirm', title: '确认入库', desc: '有错默认整批不入库；可下载错误回执改完重传' }
        ]
      }
    },
    computed: {
      errorCount () {
        return this.result && this.result.errors ? this.result.errors.length : 0
      },
      /** 按 Excel 行号聚合后的错误（同一行的多个问题合成一条） */
      errorRows () {
        return groupErrorsByRow(this.result)
      },
      requiredCount () {
        return this.fields.filter(item => item.required).length
      },
      strategyOptions () {
        return this.strategies.map(item => ({ label: item.label, value: item.code }))
      },
      canImport () {
        // 有错误行时后端默认整批不入库，因此这里也默认禁用；勾「跳过错误行」才放行
        return !this.errorCount || this.skipErrorRows
      },
      importableCount () {
        if (!this.result) {
          return 0
        }
        if (this.errorCount && !this.skipErrorRows) {
          return 0
        }
        return this.result.validRows || 0
      },
      statCards () {
        const last = this.logs && this.logs.length ? this.logs[0] : null
        return [
          {
            key: 'fields',
            label: '模板列数',
            value: this.fields.length,
            extra: `必填 ${this.requiredCount} 列（出让宗地编号/项目分类/录入单位/录入人/联系电话）`
          },
          {
            key: 'strategy',
            label: '重复编号策略',
            value: this.strategies.length,
            extra: '重复即报错 / 跳过重复只导新增 / 覆盖更新'
          },
          {
            key: 'error',
            label: '错误回执',
            value: '3 列',
            extra: 'Excel 行号 / 错误字段 / 错误原因，前 N 列与模板一致'
          },
          {
            key: 'last',
            label: '最近一次导入',
            value: last ? (last.insertedRows || 0) + (last.updatedRows || 0) : '—',
            extra: last
              ? `${last.createTime || ''} · ${last.aborted ? '整批未入库' : '已写入'}`
              : '还没有导入记录'
          }
        ]
      }
    },
    mounted () {
      this.loadFields()
      this.loadStrategies()
      this.loadLogs()
    },
    methods: {
      importSummary: importSummary,
      fieldTypeText: fieldTypeText,
      fieldTypeColor: fieldTypeColor,

      // ---------------- 字典与策略 ----------------

      loadFields () {
        queryImportFields().then(res => {
          if (res.success && res.result && res.result.length) {
            this.fields = res.result
          }
        }).catch(() => {
          // 接口不可用时用本地兜底，不打断页面
        })
      },
      loadStrategies () {
        queryDuplicateStrategies().then(res => {
          if (res.success && res.result && res.result.length) {
            this.strategies = res.result
          }
        }).catch(() => {
          // 同上
        })
      },
      strategyLabel (code) {
        const hit = this.strategies.filter(item => item.code === code)[0]
        if (hit) {
          return hit.label
        }
        const fallback = DUPLICATE_STRATEGIES_FALLBACK.filter(item => item.code === code)[0]
        return fallback ? fallback.label : (code || '—')
      },

      // ---------------- 导入记录 ----------------

      loadLogs () {
        this.logLoading = true
        queryImportLogs(20).then(res => {
          this.logs = res.success && res.result ? res.result : []
        }).catch(() => {
          this.logs = []
        }).finally(() => {
          this.logLoading = false
        })
      },

      // ---------------- 入口 ----------------

      handleDownloadTemplate () {
        downloadImportTemplate()
        this.$message.success('已开始下载模板，请稍候…')
      },
      handleUpload () {
        this.$refs.importModal.open()
      },
      handleImportDone (payload) {
        // 弹窗导入完成后，把结果接到页面上的结果面板（可继续下载回执 / 看错误清单）
        if (payload && payload.result) {
          this.result = payload.result
          this.phase = 'imported'
          this.file = payload.file || null
          this.lastFileName = payload.fileName || '—'
          this.duplicateStrategy = payload.duplicateStrategy || this.duplicateStrategy
          this.skipErrorRows = !!payload.skipErrorRows
          this.$nextTick(() => {
            const panel = document.querySelector('.result-panel')
            if (panel && panel.scrollIntoView) {
              panel.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }
          })
        }
        this.loadLogs()
      },

      // ---------------- 结果面板 ----------------

      handleStrategyChange () {
        // 策略变了，之前那次预览的结论就不再成立，提示用户重新校验
        if (this.phase === 'preview') {
          this.$message.info('已切换重复处理策略，请点「上传 Excel 导入」重新预览校验')
        }
      },
      handleConfirm () {
        if (!this.file || !this.canImport) {
          return
        }
        this.importing = true
        confirmLandImport(this.file, this.duplicateStrategy, this.skipErrorRows).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.skipErrorRows = !!this.skipErrorRows
            if ((this.result.insertedRows || 0) + (this.result.updatedRows || 0) > 0) {
              this.phase = 'imported'
              this.$message.success(importSummary(this.result))
            } else {
              this.phase = 'preview'
              this.$message.warning('没有写入任何数据（存在错误行，且未勾选跳过）')
            }
            this.loadLogs()
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '导入失败')
        }).finally(() => {
          this.importing = false
        })
      },
      handleDownloadReport () {
        if (!this.file) {
          this.$message.warning('原上传文件已不在页面上，请重新上传后再下载回执')
          return
        }
        this.reporting = true
        downloadErrorReport(this.file, this.duplicateStrategy).then(() => {
          this.$message.success('错误回执已开始下载')
        }).catch(e => {
          this.$message.error((e && e.message) || '生成错误回执失败')
        }).finally(() => {
          this.reporting = false
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;
  @primary: #2e7cf6;

  .land-import {
    /*
     * ★ 用 /deep/ 而不是 :deep()：本工程是 Vue2 + vue-loader 15，
     *   :deep() 是 Vue3 的写法，在 less 编译阶段不会被识别成深度选择器，
     *   结果就是这条 padding 规则静默失效（页面上表现为卡片内边距忽大忽小）。
     *   本目录下其它 land 页面（archive / escalation / document）一律用 /deep/。
     */
    /deep/ .ant-card-body {
      padding: 16px;
    }

    .page-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 14px;
      margin-bottom: 14px;
      border-bottom: 1px solid @border-color;

      &__title {
        margin: 0 0 6px;
        font-size: 18px;
        font-weight: 600;
        color: #0f172a;
      }

      &__desc {
        max-width: 940px;
        margin: 0;
        font-size: 12px;
        line-height: 20px;
        color: @text-muted;

        b {
          color: #0f172a;
        }
      }

      &__actions {
        display: flex;
        flex: 0 0 auto;
        flex-wrap: wrap;
        gap: 8px;
      }
    }

    /* ---------- 四步流程 ---------- */
    .import-steps {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 14px;
    }

    .import-step {
      display: flex;
      flex: 1 1 210px;
      gap: 10px;
      align-items: flex-start;
      padding: 10px 12px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__no {
        flex: 0 0 22px;
        width: 22px;
        height: 22px;
        font-size: 12px;
        font-weight: 600;
        line-height: 22px;
        color: #fff;
        text-align: center;
        background: @primary;
        border-radius: 50%;
      }

      &__title {
        font-size: 13px;
        font-weight: 600;
        color: #0f172a;
      }

      &__desc {
        margin-top: 2px;
        font-size: 12px;
        line-height: 18px;
        color: @text-weak;
      }
    }

    /* ---------- 指标卡 ---------- */
    .import-stats {
      margin-bottom: 14px;
    }

    .stat-card {
      padding: 10px 12px;
      margin-bottom: 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__label {
        font-size: 12px;
        color: @text-weak;
      }

      &__value {
        margin: 4px 0 2px;
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 22px;
        line-height: 26px;
        color: #0f172a;
      }

      &__extra {
        font-size: 11px;
        line-height: 16px;
        color: @text-weak;
      }
    }

    /* ---------- 结果面板 ---------- */
    .result-panel {
      padding: 12px 16px;
      margin-bottom: 14px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;

      &__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        padding-bottom: 10px;
        border-bottom: 1px dashed @border-color;
      }

      &__title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }

      &__file {
        margin-left: 8px;
        font-size: 12px;
        font-weight: 400;
        color: @text-weak;
      }

      &__actions {
        display: flex;
        gap: 8px;
      }
    }

    .result-stats {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      padding: 10px 0 4px;
      font-size: 13px;
      color: @text-muted;
    }

    .result-stat b {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 16px;
      color: #0f172a;

      &.is-primary {
        color: @primary;
      }

      &.is-ok {
        color: #10b981;
      }

      &.is-error {
        color: #ef4444;
      }
    }

    .result-alert {
      margin-top: 8px;
    }

    .error-block {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px dashed @border-color;

      &__head {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: 8px;
        margin-bottom: 8px;
      }

      &__title {
        font-size: 13px;
        font-weight: 600;
        color: #d48806;

        b {
          color: #cf1322;
        }
      }

      &__hint {
        font-size: 12px;
        color: @text-weak;
      }

      &__message {
        color: #cf1322;
      }
    }

    .confirm-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding-top: 12px;
      margin-top: 14px;
      border-top: 1px solid @border-color;

      &__left {
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
        align-items: center;
      }

      &__count {
        color: @text-weak;
      }

      &__strategy {
        font-size: 13px;
        color: @text-muted;
      }

      &--done {
        color: @text-muted;
      }

      &__done {
        font-size: 13px;
        color: @text-muted;
      }
    }

    /* ---------- 导入记录 ---------- */
    .log-panel {
      margin-bottom: 14px;

      &__head {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 10px;
        margin-bottom: 8px;
      }

      &__title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }

      &__hint {
        font-size: 12px;
        color: @text-weak;
      }
    }

    /* ---------- 字段说明 ---------- */
    .field-panel {
      border: 1px solid @border-color;
      border-radius: 8px;

      &__head {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 10px;
        padding: 10px 12px;
        cursor: pointer;
        background: #f8fafc;
        border-radius: 8px;
      }

      &__title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }

      &__hint {
        flex: 1 1 auto;
        font-size: 12px;
        color: @text-weak;
      }

      &__body {
        padding: 8px 12px 12px;
      }

      &__column {
        margin-right: 6px;
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 12px;
        color: @primary;
      }

      &__option {
        font-size: 12px;
        color: @text-muted;
      }

      &__sample {
        font-size: 12px;
        color: @text-weak;
      }
    }

    .is-error {
      color: #ef4444;
    }
  }
</style>
