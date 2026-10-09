<template>
  <a-modal
    title="配套信息批量导入"
    :width="980"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    @cancel="handleClose">
    <!-- ============ 步骤条 ============ -->
    <a-steps :current="step" size="small" class="facility-import-modal__steps">
      <a-step title="下载模板并填写" description="第 1 行英文字段名 · 数据从第 3 行起" />
      <a-step title="预览校验" description="不写库；同时给出孤儿清单" />
      <a-step title="确认入库" description="默认有错整批不入库" />
    </a-steps>

    <!-- ============ 一、准备与上传 ============ -->
    <div class="facility-import-modal__block">
      <div class="facility-import-modal__row">
        <a-button icon="download" @click="handleDownloadTemplate">下载导入模板</a-button>
        <span class="facility-import-modal__hint">
          模板第 1 行是<b>英文字段名</b>（程序按它认列）、第 2 行是中文说明、数据从<b>第 3 行</b>开始；
          第 2 个 sheet 是逐字段的「填表说明」。
          <b>出让宗地编号必须与系统里的写法完全一致</b> —— 少一个「号」字、用了半角括号，
          都会在宗地校验这一步被判成「查不到该宗地」。
        </span>
      </div>

      <div class="facility-import-modal__row">
        <a-upload
          :file-list="fileList"
          :before-upload="handleBeforeUpload"
          :remove="handleRemove"
          accept=".xlsx,.xls"
          :max-count="1">
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
          @click="handleRepreview">按当前选项重新校验</a-button>
      </div>
    </div>

    <!-- ============ 二、导入选项（判重键与孤儿口径） ============ -->
    <div class="facility-import-modal__block">
      <div class="facility-import-modal__row">
        <span class="facility-import-modal__label">重复处理策略</span>
        <a-select v-model="duplicateStrategy" size="small" style="width: 300px" :options="strategyOptions" />
        <span class="facility-import-modal__hint facility-import-modal__hint--inline">
          ★ 判重键是 <b>（出让宗地编号, 配套项目名称）</b>：同一宗地下可以有多条配套，
          只有两者同时相同才算重复。改了策略要重新校验，预览结论才作数
        </span>
      </div>
      <div class="facility-import-modal__row">
        <a-checkbox v-model="allowOrphan">允许挂到未登记宗地（记入孤儿清单）</a-checkbox>
        <span class="facility-import-modal__hint facility-import-modal__hint--inline">
          <template v-if="allowOrphan">
            <b>允许</b>：宗地编号查不到的行照常导入并记入孤儿清单；
            但在宗地录入之前，这些配套不会出现在任何按宗地关联的档案 / 台账里
          </template>
          <template v-else>
            <b>不允许（默认）</b>：宗地编号查不到的行算硬错误，会让整批数据都不入库，
            避免产生一批挂不上业务的静默数据
          </template>
        </span>
      </div>
    </div>

    <!-- ============ 三、结果 ============ -->
    <div v-if="result" class="facility-import-modal__block">
      <div class="facility-import-modal__stats">
        <span class="facility-import-modal__stat">解析数据行 <b>{{ result.totalRows }}</b></span>
        <span class="facility-import-modal__stat">校验通过 <b class="is-primary">{{ result.validRows }}</b></span>
        <span class="facility-import-modal__stat">
          <template v-if="imported">已新增 <b class="is-ok">{{ result.insertedRows }}</b></template>
          <template v-else>将新增 <b class="is-primary">{{ result.willInsertRows }}</b></template>
        </span>
        <span class="facility-import-modal__stat">
          <template v-if="imported">已覆盖 <b class="is-ok">{{ result.updatedRows }}</b></template>
          <template v-else>将覆盖 <b class="is-primary">{{ result.willUpdateRows }}</b></template>
        </span>
        <span v-if="result.skippedDuplicates" class="facility-import-modal__stat">
          跳过重复 <b>{{ result.skippedDuplicates }}</b>
        </span>
        <span class="facility-import-modal__stat">匹配到宗地 <b class="is-ok">{{ result.matchedLandRows || 0 }}</b></span>
        <span class="facility-import-modal__stat">
          错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
        </span>
      </div>

      <a-alert
        v-if="imported"
        class="facility-import-modal__alert"
        type="success"
        show-icon
        :message="importSummary(result)" />
      <a-alert
        v-else-if="result.aborted"
        class="facility-import-modal__alert"
        type="warning"
        show-icon
        message="本次不会写入任何数据（存在错误行，且未勾选「跳过错误行」）" />
      <a-alert
        v-else
        class="facility-import-modal__alert"
        type="info"
        show-icon
        :message="`校验通过：确认入库将新增 ${result.willInsertRows || 0} 条、覆盖更新 ${result.willUpdateRows || 0} 条`" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="facility-import-modal__alert"
        type="info"
        show-icon
        :message="item" />
      <a-alert
        v-for="(item, index) in result.notices || []"
        :key="'n' + index"
        class="facility-import-modal__alert"
        type="warning"
        show-icon
        :message="item" />

      <!-- ★ 孤儿清单：独立成块、排在错误清单之前 -->
      <div v-if="orphanRows" class="facility-import-modal__orphans" :class="allowOrphan ? 'is-allowed' : 'is-blocking'">
        <div class="facility-import-modal__orphans-head">
          <span class="facility-import-modal__orphans-title">
            ★ 孤儿清单：<b>{{ orphanRows }}</b> 行
            <a-tag :color="allowOrphan ? 'orange' : 'red'">
              {{ allowOrphan ? '已允许：照常入库' : '未允许：阻止整批入库' }}
            </a-tag>
          </span>
          <span class="facility-import-modal__orphans-hint">
            孤儿 = 该宗地还没有录入系统。配套可以先录，但在宗地录入之前，
            这些配套不会出现在任何按宗地关联的档案 / 台账里
          </span>
        </div>
        <div v-if="!allowOrphan" class="facility-import-modal__orphans-action">
          <span class="facility-import-modal__orphans-action-text">
            这 {{ orphanRows }} 行会作为硬错误让整批数据不入库
          </span>
          <a-button size="small" type="primary" icon="reload" :loading="previewing" @click="handleAllowOrphan">
            允许挂到未登记宗地并重新校验
          </a-button>
        </div>
        <a-table
          row-key="key"
          size="small"
          :columns="orphanColumns"
          :data-source="orphanRowsList"
          :pagination="orphanPagination"
          :scroll="{ y: 180 }">
          <template slot="reason" slot-scope="text">
            <span class="facility-import-modal__orphans-reason">{{ text || '系统中没有该出让宗地编号' }}</span>
          </template>
        </a-table>
      </div>

      <!-- 错误清单：按 Excel 行号聚合 -->
      <div v-if="errorCount" class="facility-import-modal__errors">
        <div class="facility-import-modal__errors-head">
          <span class="facility-import-modal__errors-title">
            错误清单：<b>{{ errorRows.length }}</b> 行有问题、共 <b>{{ errorCount }}</b> 条
          </span>
          <a-button size="small" icon="download" :loading="reporting" @click="handleDownloadReport">
            下载错误回执
          </a-button>
        </div>
        <a-table
          row-key="key"
          size="small"
          :columns="errorColumns"
          :data-source="errorRows"
          :pagination="errorPagination"
          :scroll="{ y: 180 }">
          <template slot="columns" slot-scope="text">
            <a-tag v-for="item in text" :key="item">{{ item }}</a-tag>
          </template>
          <template slot="message" slot-scope="text">
            <span class="facility-import-modal__error-text">{{ text }}</span>
          </template>
        </a-table>
        <div class="facility-import-modal__errors-hint">
          回执保留你填的原始内容，末尾追加 <code>__excel行号 / __错误字段 / __错误原因</code> 三列；
          <b>有孤儿时还会多一个「孤儿清单」sheet</b>（孤儿不算错误，按错误列导不出来）。
          改完删掉这三列即可当导入文件直接重传。
        </div>
      </div>

      <!-- 确认入库 -->
      <div class="facility-import-modal__row facility-import-modal__confirm">
        <a-checkbox v-if="!imported" v-model="skipErrorRows" :disabled="!errorCount">
          跳过错误行，只导入正确的行
          <span v-if="errorCount">（{{ importableCount }} 行）</span>
        </a-checkbox>
        <span v-else class="facility-import-modal__done-hint">导入完成，结果已同步到页面上的「导入记录」</span>
        <a-button
          v-if="!imported"
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="errorCount > 0 && !skipErrorRows"
          @click="handleConfirm">
          确认入库{{ canImport && importableCount ? '（' + importableCount + ' 行）' : '' }}
        </a-button>
        <a-button v-else type="primary" @click="handleClose">完成</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    DUPLICATE_STRATEGIES_FALLBACK,
    confirmFacilityImport,
    downloadErrorReport,
    downloadImportTemplate,
    flattenOrphans,
    groupErrorsByRow,
    importSummary,
    previewFacilityImport,
    queryDuplicateStrategies
  } from '@/api/land/facilityImport'

  /**
   * 配套信息批量导入 - 弹窗（方案 2.3.1（三）第 4 项）
   *
   * 三步：下载模板 → 预览校验（不写库）→ 确认入库。
   * 与「经营性用地批量导入」弹窗的三点差异：
   *
   * ★ 1）判重键是 (出让宗地编号, 配套项目名称)，与宗地导入只看编号不同；
   *      策略下拉旁边必须写清这一点，否则用户会以为「同一宗地的第二条配套是重复的」。
   *
   * ★ 2）多一个「允许挂到未登记宗地」开关，它改的是**校验口径**而不是入库选项，
   *      因此预览与入库都要带上它。这也意味着：改了开关一定要重新校验，
   *      否则面板上的错误清单还是旧口径的结论（用户会看到「12 行错误」却导进去 12 条）。
   *      为此开关旁边保留了「按当前选项重新校验」按钮，孤儿块里也有一个一键允许并重跑。
   *
   * ★ 3）孤儿清单独立成块、排在错误清单之前，且**孤儿不算错误**：
   *      allowOrphan=true 时孤儿行照常入库，此时把它报成错误会自相矛盾；
   *      allowOrphan=false 时它同时也在错误清单里（后端就是这么给的），
   *      这里再单独列一份是为了让用户一眼看到「这批数据里有 N 条找不到宗地」，
   *      可以直接拿这份清单去催录宗地 —— 混在几百条错误里就看不出来了。
   *
   * ★ 交互上沿用宗地导入弹窗的几个刻意设计：
   *   有错误行时默认禁用「确认入库」；错误清单按 Excel 行号聚合（一行两个问题算一行）；
   *   切换策略后不自动重跑（重新解析上传文件是有成本的），由用户点「按当前选项重新校验」。
   */
  export default {
    name: 'FacilityImportModal',
    props: {
      /** 打开时采用的默认重复处理策略（页面上的「导入选项」） */
      defaultStrategy: {
        type: String,
        default: 'reject'
      },
      /** 打开时采用的默认孤儿口径（页面上的「导入选项」） */
      defaultAllowOrphan: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        visible: false,
        step: 0,
        file: null,
        fileList: [],
        /** 已经「按当前选项」校验过一次的文件（用于判断要不要显示「重新校验」） */
        loadedFile: null,
        previewing: false,
        importing: false,
        reporting: false,
        imported: false,
        skipErrorRows: false,
        duplicateStrategy: 'reject',
        allowOrphan: false,
        strategies: DUPLICATE_STRATEGIES_FALLBACK,
        result: null,
        errorPagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 行`
        },
        orphanPagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 行孤儿`
        },
        errorColumns: [
          { title: 'Excel 行号', dataIndex: 'rowNum', width: 100 },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 200, customRender: text => text || '—' },
          { title: '出错字段', dataIndex: 'columns', width: 200, scopedSlots: { customRender: 'columns' } },
          { title: '错误原因', dataIndex: 'message', scopedSlots: { customRender: 'message' } }
        ],
        orphanColumns: [
          { title: 'Excel 行号', dataIndex: 'rowNum', width: 100 },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 200, customRender: text => text || '—' },
          { title: '配套项目名称', dataIndex: 'ptxmmc', width: 200, customRender: text => text || '—' },
          { title: '说明', dataIndex: 'reason', scopedSlots: { customRender: 'reason' } }
        ]
      }
    },
    computed: {
      errorCount () {
        return this.result && this.result.errors ? this.result.errors.length : 0
      },
      orphanRows () {
        return this.result ? Number(this.result.orphanRows || 0) : 0
      },
      orphanRowsList () {
        return flattenOrphans(this.result)
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

      /**
       * 打开弹窗。
       *
       * @param {File} [file] 页面上已经选过的文件（用于「勾了允许孤儿后重新校验」这类场景，
       *                      不必让用户重新选一遍文件）
       */
      open (file) {
        this.visible = true
        this.reset()
        if (file) {
          this.file = file
          this.fileList = [file]
          this.step = 1
        }
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
        // 每次打开都回到页面上的「导入选项」，避免上一次在弹窗里的临时改动影响这一次
        this.duplicateStrategy = this.defaultStrategy || 'reject'
        this.allowOrphan = !!this.defaultAllowOrphan
        this.result = null
      },
      /**
       * 关闭弹窗。
       *
       * ★ 只有「真的导入了」或「已经预览过」才把结果往页面上抛：
       *   用户只是打开看了一眼就关掉，不该在页面上留下一个结果面板
       *   （那会让人以为已经写库了）。预览过的结果要抛上去，因为页面上的
       *   「允许孤儿后重新校验」要用到页面留存的原始文件。
       */
      handleClose () {
        const payload = this.result
          ? {
            result: this.result,
            phase: this.imported ? 'imported' : 'preview',
            file: this.loadedFile || this.file,
            fileName: (this.loadedFile || this.file) ? (this.loadedFile || this.file).name : '',
            duplicateStrategy: this.duplicateStrategy,
            skipErrorRows: this.skipErrorRows,
            allowOrphan: this.allowOrphan
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
        // 阻止 a-upload 自动上传：由本组件手动调预览 / 入库接口
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
      /**
       * 允许挂到未登记宗地并立刻按新口径重新校验。
       *
       * ★ allowOrphan 是校验口径而不是入库选项：不重跑就会出现
       *   「面板上还是 12 行错误、确认按钮却已经放行」的错位。
       */
      handleAllowOrphan () {
        this.allowOrphan = true
        this.doPreview(true)
      },
      doPreview (rePreview) {
        if (!this.file) {
          return
        }
        this.previewing = true
        const file = this.file
        previewFacilityImport(file, this.duplicateStrategy, this.allowOrphan).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.loadedFile = file
            this.imported = false
            this.skipErrorRows = false
            this.step = 1
            const orphans = Number(this.result.orphanRows || 0)
            if (this.errorCount) {
              this.$message.warning(
                `校验完成：${this.errorRows.length} 行有问题（共 ${this.errorCount} 条），请先修正（或勾选「跳过错误行」）`)
            } else {
              this.$message.success(
                `校验通过：将新增 ${this.result.willInsertRows || 0} 条、覆盖更新 ${this.result.willUpdateRows || 0} 条`)
            }
            if (rePreview) {
              this.$message.info(orphans
                ? `已按当前选项重新校验：${orphans} 行孤儿将${this.allowOrphan ? '照常入库' : '被拦下'}`
                : '已按当前选项重新校验：本次没有孤儿行')
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
        confirmFacilityImport(this.file, this.duplicateStrategy, this.skipErrorRows, this.allowOrphan).then(res => {
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
        downloadErrorReport(file, this.duplicateStrategy, this.allowOrphan).then(() => {
          this.$message.success(this.orphanRows
            ? '错误回执已开始下载（含「孤儿清单」sheet）'
            : '错误回执已开始下载')
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

  .facility-import-modal {
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

    &__label {
      font-size: 13px;
      color: #0f172a;
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

      &--inline {
        min-width: 0;
      }
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

    /* ★ 孤儿清单：独立成块、排在错误清单之前 */
    &__orphans {
      padding: 8px 10px;
      margin-top: 12px;
      border-radius: 8px;

      &.is-allowed {
        background: #fffbe6;
        border: 1px solid #ffe58f;
      }

      &.is-blocking {
        background: #fff1f0;
        border: 1px solid #ffccc7;
      }
    }

    &__orphans-head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 8px;
      margin-bottom: 8px;
    }

    &__orphans-title {
      font-size: 13px;
      font-weight: 600;
      color: #cf1322;

      b {
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 16px;
      }
    }

    &__orphans-hint {
      flex: 1 1 auto;
      min-width: 240px;
      font-size: 12px;
      line-height: 18px;
      color: #874d00;
    }

    &__orphans-action {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding-bottom: 8px;
      margin-bottom: 8px;
      border-bottom: 1px dashed #ffccc7;
    }

    &__orphans-action-text {
      flex: 1 1 auto;
      min-width: 200px;
      font-size: 12px;
      color: #874d00;
    }

    &__orphans-reason {
      font-size: 12px;
      color: #874d00;
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

      b {
        color: #0f172a;
      }

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
