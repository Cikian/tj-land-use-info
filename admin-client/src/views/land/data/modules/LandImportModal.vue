<template>
  <a-modal
    title="经营性用地批量导入"
    :width="940"
    :visible="visible"
    :footer="null"
    :maskClosable="false"
    @cancel="handleClose">
    <!-- ============ 步骤条 ============ -->
    <a-steps :current="step" size="small" class="import-modal__steps">
      <a-step title="下载模板并填写" description="第 1 行英文字段名 · 数据从第 3 行起" />
      <a-step title="预览校验" description="不写库，先看会新增/覆盖多少条" />
      <a-step title="确认导入" description="有错默认整批不入库" />
    </a-steps>

    <!-- ============ 一、准备与上传 ============ -->
    <div class="import-modal__block">
      <div class="import-modal__row">
        <a-button icon="download" @click="handleDownloadTemplate">下载导入模板</a-button>
        <span class="import-modal__hint">
          模板第 1 行是<b>英文字段名</b>（程序按它认列，例如 <code>crzdbh</code>）、
          第 2 行是中文说明、数据从<b>第 3 行</b>开始；第 2 个 sheet 是逐字段的「填表说明」。
          必填 5 列：出让宗地编号、项目分类、录入单位、录入人、联系电话。
        </span>
      </div>

      <div class="import-modal__row">
        <a-upload
          :fileList="fileList"
          :beforeUpload="handleBeforeUpload"
          :remove="handleRemove"
          accept=".xlsx,.xls"
          :maxCount="1">
          <a-button icon="upload">选择 Excel（.xlsx / .xls）</a-button>
        </a-upload>
        <a-button
          type="primary"
          icon="check-circle"
          :loading="previewing"
          :disabled="!file"
          @click="handlePreview">预览校验</a-button>
        <a-button
          v-if="loadedFile"
          icon="reload"
          :loading="previewing"
          @click="handleRepreview">按当前策略重新校验</a-button>
      </div>

      <div class="import-modal__row">
        <a-tooltip title="「重复即报错」不会写库也不会改动已有数据；「跳过重复」只导新增；「覆盖更新」按单元格有值才覆盖，空单元格保持库内原值">
          <span class="import-modal__strategy">
            重复宗地编号处理：
            <a-select
              v-model="duplicateStrategy"
              size="small"
              style="width: 280px"
              :options="strategyOptions" />
          </span>
        </a-tooltip>
        <span class="import-modal__hint import-modal__hint--inline">
          ★ 库里已有该编号时的处理方式；同一份文件里编号重复一律报错
        </span>
      </div>
    </div>

    <!-- ============ 二、结果 ============ -->
    <div v-if="result" class="import-modal__block">
      <div class="import-modal__stats">
        <span class="import-modal__stat">解析数据行 <b>{{ result.totalRows }}</b></span>
        <span class="import-modal__stat">校验通过 <b class="is-primary">{{ result.validRows }}</b></span>
        <span class="import-modal__stat">
          <template v-if="imported">已新增 <b class="is-ok">{{ result.insertedRows }}</b></template>
          <template v-else>将新增 <b class="is-primary">{{ result.willInsertRows }}</b></template>
        </span>
        <span class="import-modal__stat">
          <template v-if="imported">已覆盖 <b class="is-ok">{{ result.updatedRows }}</b></template>
          <template v-else>将覆盖 <b class="is-primary">{{ result.willUpdateRows }}</b></template>
        </span>
        <span v-if="result.skippedDuplicates" class="import-modal__stat">
          跳过重复 <b>{{ result.skippedDuplicates }}</b>
        </span>
        <span class="import-modal__stat">
          错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
        </span>
      </div>

      <a-alert
        v-if="imported"
        class="import-modal__alert"
        type="success"
        showIcon
        :message="importSummary(result)" />
      <a-alert
        v-else-if="result.aborted"
        class="import-modal__alert"
        type="warning"
        showIcon
        message="本次不会写入任何数据（存在错误行）" />
      <a-alert
        v-else
        class="import-modal__alert"
        type="info"
        showIcon
        :message="`校验通过：确认导入将新增 ${result.willInsertRows} 条、覆盖更新 ${result.willUpdateRows} 条`" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="import-modal__alert"
        type="info"
        showIcon
        :message="item" />
      <a-alert
        v-for="(item, index) in result.notices || []"
        :key="'n' + index"
        class="import-modal__alert"
        type="warning"
        showIcon
        :message="item" />

      <!-- 错误清单：按 Excel 行号聚合 -->
      <div v-if="errorCount" class="import-modal__errors">
        <div class="import-modal__errors-head">
          <span class="import-modal__errors-title">
            错误清单：<b>{{ errorRows.length }}</b> 行有问题、共 <b>{{ errorCount }}</b> 条
          </span>
          <a-button
            size="small"
            icon="download"
            :loading="reporting"
            @click="handleDownloadReport">下载错误回执</a-button>
        </div>
        <a-table
          rowKey="key"
          size="small"
          :columns="errorColumns"
          :dataSource="errorRows"
          :pagination="errorPagination"
          :scroll="{ y: 220 }">
          <template slot="columns" slot-scope="text">
            <a-tag v-for="item in text" :key="item">{{ item }}</a-tag>
          </template>
          <template slot="message" slot-scope="text">
            <span class="import-modal__error-text">{{ text }}</span>
          </template>
        </a-table>
        <div class="import-modal__errors-hint">
          回执里保留你填的原始内容，末尾追加 <code>__excel行号 / __错误字段 / __错误原因</code> 三列；
          改完删掉这三列即可当导入文件直接重传。
        </div>
      </div>

      <!-- 确认导入 -->
      <div class="import-modal__row import-modal__confirm">
        <a-checkbox v-if="!imported" v-model="skipErrorRows" :disabled="!errorCount">
          跳过错误行，只导入正确的行
          <span v-if="errorCount">（{{ importableCount }} 行）</span>
        </a-checkbox>
        <span v-else class="import-modal__done-hint">导入完成，结果已同步到页面的「导入记录」</span>
        <a-button
          v-if="!imported"
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="errorCount > 0 && !skipErrorRows"
          @click="handleConfirm">
          确认导入{{ canImport && importableCount ? '（' + importableCount + ' 行）' : '' }}
        </a-button>
        <a-button v-else type="primary" @click="handleClose">完成</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    confirmLandImport,
    downloadErrorReport,
    downloadImportTemplate,
    groupErrorsByRow,
    importSummary,
    previewLandImport,
    queryDuplicateStrategies,
    DUPLICATE_STRATEGIES_FALLBACK
  } from '@/api/land/landImport'

  /**
   * 经营性用地批量导入 - 弹窗（方案 2.3.1（三）第 3 项）
   *
   * 三步：下载模板 → 预览校验（不写库）→ 确认导入。
   *
   * ★ 为什么必须有「预览」这一步：批量写库是**不可逆**的
   *   （虽然逻辑删除还在，但几百条一起写错很难逐条回退）。
   *   先让用户看到「会新增多少条、会覆盖多少条、错在哪几行」，再决定是否落库。
   *
   * 交互上几个刻意设计：
   *  1. 有错误行时**默认禁用「确认导入」**（后端默认也是整批不入库）；
   *     确实想先救一部分时，勾「跳过错误行」才可点；
   *  2. 错误清单**按 Excel 行号聚合**：同一行踩两个问题时合成一条，
   *     否则用户会以为要改 10 行、其实只有 6 行；
   *  3. 切换重复策略后给「按当前策略重新校验」按钮，而不是自动重跑 ——
   *     重新解析上传文件是有成本的，让用户自己决定什么时候跑；
   *  4. 旧的 `xjKjkfbCommercialLandController.importData` 在手机号格式错时
   *     「中断并留下已导入的 N 条」，本实现改成整表预校验：
   *     有错一行都不写，用户不必去猜「到底进去几条了」。
   */
  export default {
    name: 'LandImportModal',
    data () {
      return {
        visible: false,
        step: 0,
        file: null,
        fileList: [],
        /** 已经「按当前策略」校验过一次的文件（用于判断要不要显示「重新校验」） */
        loadedFile: null,
        previewing: false,
        importing: false,
        reporting: false,
        imported: false,
        skipErrorRows: false,
        duplicateStrategy: 'reject',
        strategies: DUPLICATE_STRATEGIES_FALLBACK,
        result: null,
        errorPagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 行`
        },
        errorColumns: [
          { title: 'Excel 行号', dataIndex: 'rowNum', width: 100 },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 200, customRender: text => text || '—' },
          { title: '出错字段', dataIndex: 'columns', width: 200, scopedSlots: { customRender: 'columns' } },
          { title: '错误原因', dataIndex: 'message', scopedSlots: { customRender: 'message' } }
        ]
      }
    },
    computed: {
      errorCount () {
        return this.result && this.result.errors ? this.result.errors.length : 0
      },
      /** 按 Excel 行号聚合后的错误 */
      errorRows () {
        return groupErrorsByRow(this.result)
      },
      strategyOptions () {
        return this.strategies.map(item => ({ label: item.label, value: item.code }))
      },
      canImport () {
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
      }
    },
    mounted () {
      queryDuplicateStrategies().then(res => {
        if (res.success && res.result && res.result.length) {
          this.strategies = res.result
        }
      }).catch(() => {
        // 接口不可用时用本地兜底
      })
    },
    methods: {
      importSummary: importSummary,

      open () {
        this.visible = true
        this.reset()
      },
      reset () {
        this.step = 0
        this.file = null
        this.fileList = []
        this.loadedFile = null
        this.previewing = false
        this.importing = false
        this.reporting = false
        this.imported = false
        this.skipErrorRows = false
        this.result = null
      },
      /**
       * 关闭弹窗。
       *
       * ★ 只有「真的导入了」才把结果往页面上抛：用户只是预览了一下就关掉，
       *   不该在页面上留下一个「导入结果」面板（那会让人以为已经写库了）。
       */
      handleClose () {
        const imported = this.imported
        const payload = imported
          ? {
            result: this.result,
            file: this.loadedFile,
            fileName: this.loadedFile ? this.loadedFile.name : '',
            duplicateStrategy: this.duplicateStrategy,
            skipErrorRows: this.skipErrorRows
          }
          : null
        this.visible = false
        if (payload) {
          this.$emit('ok', payload)
        }
      },
      handleDownloadTemplate () {
        downloadImportTemplate()
        this.$message.success('已开始下载模板，请稍候…')
      },
      handleBeforeUpload (file) {
        if (!/\.(xlsx|xls)$/i.test(file.name)) {
          this.$message.error('只支持 .xlsx / .xls 文件（请用「下载导入模板」得到的表填写）')
          return false
        }
        this.file = file
        this.fileList = [file]
        this.loadedFile = null
        this.result = null
        this.imported = false
        this.step = 1
        // 阻止 a-upload 自动上传：由本组件手动调预览/入库接口
        return false
      },
      handleRemove () {
        this.file = null
        this.fileList = []
        this.loadedFile = null
        this.result = null
        this.step = 0
      },
      handlePreview () {
        this.doPreview(false)
      },
      handleRepreview () {
        this.doPreview(true)
      },
      doPreview (rePreview) {
        if (!this.file) {
          return
        }
        this.previewing = true
        const file = this.file
        previewLandImport(file, this.duplicateStrategy).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.loadedFile = file
            this.imported = false
            this.skipErrorRows = false
            this.step = 1
            if (this.errorCount) {
              this.$message.warning(
                `校验完成：${this.errorRows.length} 行有问题（共 ${this.errorCount} 条），请先修正（或勾选「跳过错误行」）`)
            } else {
              this.$message.success(
                `校验通过：将新增 ${this.result.willInsertRows || 0} 条、覆盖更新 ${this.result.willUpdateRows || 0} 条`)
            }
            if (rePreview) {
              this.$message.info('已按当前重复处理策略重新校验')
            }
          } else {
            this.$message.warning(res.message)
          }
        }).catch(e => {
          this.$message.error((e && e.message) || '预览失败')
        }).finally(() => {
          this.previewing = false
        })
      },
      handleConfirm () {
        if (!this.file || !this.canImport) {
          return
        }
        this.importing = true
        confirmLandImport(this.file, this.duplicateStrategy, this.skipErrorRows).then(res => {
          if (res.success) {
            this.result = res.result || {}
            if ((this.result.insertedRows || 0) + (this.result.updatedRows || 0) > 0) {
              this.imported = true
              this.step = 2
              this.$message.success(importSummary(this.result))
            } else {
              this.imported = false
              this.$message.warning('没有写入任何数据（存在错误行，且未勾选跳过）')
            }
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
        const file = this.loadedFile || this.file
        if (!file) {
          this.$message.warning('请先选择并预览文件')
          return
        }
        this.reporting = true
        downloadErrorReport(file, this.duplicateStrategy).then(() => {
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

  .import-modal {
    &__steps {
      margin-bottom: 16px;
    }

    &__block {
      padding: 12px 16px;
      margin-bottom: 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    &__row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      margin-bottom: 12px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    &__hint {
      flex: 1 1 auto;
      min-width: 260px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;

      b {
        color: #0f172a;
      }

      code {
        padding: 0 4px;
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 11px;
        color: @primary;
        background: #f1f5f9;
        border-radius: 3px;
      }

      &--inline {
        min-width: 0;
      }
    }

    &__strategy {
      font-size: 13px;
      color: @text-muted;
    }

    &__stats {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      font-size: 13px;
      color: @text-muted;

      b {
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
    }

    &__alert {
      margin-top: 10px;
    }

    &__errors {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px dashed @border-color;
    }

    &__errors-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin-bottom: 8px;
    }

    &__errors-title {
      font-size: 13px;
      font-weight: 600;
      color: #d48806;

      b {
        color: #cf1322;
      }
    }

    &__errors-hint {
      margin-top: 8px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;

      code {
        padding: 0 4px;
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 11px;
        color: @text-muted;
        background: #f1f5f9;
        border-radius: 3px;
      }
    }

    &__error-text {
      color: #cf1322;
    }

    &__confirm {
      justify-content: space-between;
      margin-top: 14px;
      padding-top: 12px;
      border-top: 1px solid @border-color;
    }

    &__done-hint {
      font-size: 13px;
      color: @text-muted;
    }
  }
</style>
