<template>
  <!--
    LandImportModal 经营性用地批量导入
    --------------------------------
    三步：下载模板 → 预览校验（不写库）→ 确认入库，与配套导入
    （FacilityImportModal）同一套心智，差别只有三处：
      · 判重键是**出让宗地编号本身**（配套是「宗地 + 名称」两列）；
      · 没有孤儿清单（宗地编号就是主键，不存在「挂不上」的概念）；
      · 没有 allowOrphan 开关。

    ★ 为什么必须先「预览」：批量写库是不可逆的（逻辑删除还在，但几百条一起写错
      很难逐条回退）。先让用户看到「会新增多少条、会覆盖多少条、错在哪几行」，
      再决定是否落库。

    交互上的几个刻意设计（与 admin-client 版本一致，保证两端同一套心智）：
      1. 有错误行时**默认禁用「确认导入」**（后端默认也是整批不入库），
         确实想先救一部分时勾「跳过错误行」才放行；
      2. 错误清单**按 Excel 行号聚合**：同一行踩两个问题时合成一条，
         否则用户会以为要改 10 行、其实只有 6 行；
      3. 切换重复策略后给「按当前策略重新校验」按钮，而不是自动重跑 ——
         重新解析上传文件是有成本的，让用户自己决定什么时候跑。

    ★ 文件选择用原生 input（1px 裁切、保留在可访问树里）而不是 ScreenUpload：
      ScreenUpload 会自己把文件 POST 到 action，拿到的是 jeecg 通用上传接口的
      存储路径；而批量导入要的是「把文件交给 preview/confirm 两个业务接口」，
      需要拿到 File 对象自己发请求。

    事件：
      ok  导入成功后抛出，面板据此刷新
  -->
  <screen-modal
    :visible.sync="visible"
    title="经营性用地批量导入"
    :width="980"
    :show-footer="false"
    :body-max-height="'calc(100vh - 180px)'"
    @cancel="handleClose"
  >
    <div class="land-import">
      <!-- ================= 步骤条 ================= -->
      <ol class="land-import__steps">
        <li
          v-for="(item, index) in steps"
          :key="item.title"
          class="land-import__step"
          :class="{ 'is-active': step === index, 'is-done': step > index }"
        >
          <span class="land-import__step-index">{{ index + 1 }}</span>
          <span class="land-import__step-text">
            <b>{{ item.title }}</b>
            <em>{{ item.desc }}</em>
          </span>
        </li>
      </ol>

      <!-- ================= 一、模板与文件 ================= -->
      <section class="land-import__block">
        <div class="land-import__row">
          <screen-button icon="download" @click="handleDownloadTemplate">下载导入模板</screen-button>
          <span class="land-import__hint">
            模板第 1 行是<b>英文字段名</b>（程序按它认列，例如 <code>crzdbh</code>）、
            第 2 行是中文说明、数据从<b>第 3 行</b>开始；第 2 个 sheet 是逐字段的「填表说明」。
            必填 5 列：出让宗地编号、项目分类、录入单位、录入人、联系电话。
          </span>
        </div>

        <div class="land-import__row">
          <input
            id="land-import-file"
            ref="fileInput"
            class="land-import__file"
            type="file"
            accept=".xlsx,.xls"
            aria-hidden="true"
            tabindex="-1"
            @change="handleFileChange"
          />
          <screen-button icon="upload" @click="handlePickFile">选择 Excel（.xlsx / .xls）</screen-button>
          <span v-if="fileName" class="land-import__file-name" :title="fileName">
            <screen-icon name="file-text" :size="13" />
            {{ fileName }}
          </span>
          <span v-else class="land-import__hint">尚未选择文件</span>

          <screen-button
            type="primary"
            icon="check-circle"
            :loading="previewing"
            :disabled="!file"
            @click="handlePreview"
          >
            预览校验
          </screen-button>

          <screen-button
            v-if="loadedFile"
            icon="reload"
            :loading="previewing"
            @click="handleRepreview"
          >
            按当前策略重新校验
          </screen-button>
        </div>

        <div class="land-import__row">
          <screen-field label="重复编号处理" label-width="110px" class="land-import__strategy">
            <screen-select
              v-model="duplicateStrategy"
              :options="strategyOptions"
              aria-label="重复宗地编号处理策略"
            />
          </screen-field>
          <span class="land-import__hint">
            ★ 库里已有该编号时的处理方式；同一份文件里的重复一律报错。
            「重复即报错」不写库也不改动已有数据，「跳过重复」只导新增，
            「覆盖更新」按单元格有值才覆盖，<b>空单元格保持库内原值</b>。
          </span>
        </div>
      </section>

      <!-- ================= 二、结果 ================= -->
      <section v-if="result" class="land-import__block">
        <div class="land-import__stats">
          <span class="land-import__stat">解析数据行 <b>{{ result.totalRows || 0 }}</b></span>
          <span class="land-import__stat">校验通过 <b class="is-primary">{{ result.validRows || 0 }}</b></span>
          <span class="land-import__stat">
            <template v-if="imported">已新增 <b class="is-ok">{{ result.insertedRows || 0 }}</b></template>
            <template v-else>将新增 <b class="is-primary">{{ result.willInsertRows || 0 }}</b></template>
          </span>
          <span class="land-import__stat">
            <template v-if="imported">已覆盖 <b class="is-ok">{{ result.updatedRows || 0 }}</b></template>
            <template v-else>将覆盖 <b class="is-primary">{{ result.willUpdateRows || 0 }}</b></template>
          </span>
          <span v-if="result.skippedDuplicates" class="land-import__stat">
            跳过重复 <b>{{ result.skippedDuplicates }}</b>
          </span>
          <span class="land-import__stat">
            错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
          </span>
        </div>

        <p v-if="imported" class="land-import__alert is-ok" role="status">
          <screen-icon name="check-circle" :size="14" />
          {{ summaryText }}
        </p>
        <p v-else-if="result.aborted" class="land-import__alert is-warning" role="status">
          <screen-icon name="alert-triangle" :size="14" />
          本次不会写入任何数据（存在错误行）
        </p>
        <p v-else class="land-import__alert is-info" role="status">
          <screen-icon name="info" :size="14" />
          校验通过：确认导入将新增 {{ result.willInsertRows || 0 }} 条、覆盖更新 {{ result.willUpdateRows || 0 }} 条
        </p>

        <p
          v-for="(item, index) in result.warnings || []"
          :key="'warn-' + index"
          class="land-import__alert is-info"
        >
          <screen-icon name="info" :size="14" />
          {{ item }}
        </p>
        <p
          v-for="(item, index) in result.notices || []"
          :key="'notice-' + index"
          class="land-import__alert is-warning"
        >
          <screen-icon name="alert-triangle" :size="14" />
          {{ item }}
        </p>

        <!-- 错误清单：按 Excel 行号聚合，用户能直接跳到那一行改 -->
        <div v-if="errorCount" class="land-import__errors">
          <div class="land-import__errors-head">
            <h4 class="land-import__errors-title">
              错误清单：共 {{ errorRows.length }} 行有问题、{{ errorCount }} 条原因
            </h4>
            <screen-button size="sm" icon="download" :loading="reporting" @click="handleDownloadReport">
              下载错误回执
            </screen-button>
          </div>

          <screen-data-table
            :columns="errorColumns"
            :data="pagedErrorRows"
            row-key="key"
            :min-width="760"
            :animated="false"
          >
            <template #rowNum="{ row }">
              <span class="land-import__row-num">第 {{ row.rowNum }} 行</span>
            </template>
            <template #crzdbh="{ row }">
              <span class="land-import__mono">{{ row.crzdbh || '—' }}</span>
            </template>
            <template #columnLabels="{ row }">
              <span class="land-import__cols">
                <screen-tag v-for="item in row.columnLabels" :key="item" tone="warning" size="sm">
                  {{ item }}
                </screen-tag>
                <span v-if="!row.columnLabels.length" class="land-import__mono">整行</span>
              </span>
            </template>
            <template #message="{ row }">
              <span class="land-import__error-text">{{ row.message }}</span>
            </template>
          </screen-data-table>

          <div v-if="errorPageCount > 1" class="land-import__errors-foot">
            <screen-pagination
              :current="errorPage"
              :page-size="errorPageSize"
              :total="errorRows.length"
              :page-size-options="[10, 20, 50]"
              @change="handleErrorPageChange"
            />
          </div>

          <p class="land-import__errors-hint">
            回执里保留你填的原始内容，末尾追加 <code>__excel行号 / __错误字段 / __错误原因</code> 三列；
            改完删掉这三列即可当导入文件直接重传。
          </p>
        </div>

        <!-- ================= 三、确认导入 ================= -->
        <div v-if="!imported" class="land-import__confirm">
          <label class="land-import__skip" :class="{ 'is-disabled': !errorCount }">
            <input
              class="land-import__check"
              type="checkbox"
              :checked="skipErrorRows"
              :disabled="!errorCount"
              @change="handleSkipChange"
            />
            跳过错误行，只导入正确的行
            <span v-if="errorCount">（{{ importableCount }} 行）</span>
          </label>

          <screen-button
            type="primary"
            icon="check"
            :loading="importing"
            :disabled="!canImport"
            @click="handleConfirm"
          >
            确认导入{{ canImport && importableCount ? '（' + importableCount + ' 行）' : '' }}
          </screen-button>
        </div>

        <div v-else class="land-import__confirm">
          <span class="land-import__hint">导入完成，可关闭本窗口查看列表。</span>
          <screen-button type="primary" @click="handleClose">完成</screen-button>
        </div>
      </section>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenIcon,
  ScreenField,
  ScreenSelect,
  ScreenTag,
  ScreenDataTable,
  ScreenPagination
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  downloadLandImportTemplate,
  previewLandImport,
  confirmLandImport,
  downloadLandErrorReport,
  queryLandDuplicateStrategies,
  groupErrorsByRow,
  importSummary,
  DUPLICATE_STRATEGIES_FALLBACK
} from '@/api/land/landImport'

export default {
  name: 'LandImportModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenIcon,
    ScreenField,
    ScreenSelect,
    ScreenTag,
    ScreenDataTable,
    ScreenPagination
  },
  data () {
    return {
      visible: false,
      step: 0,
      file: null,
      fileName: '',
      /** 已经「按当前策略」校验过的文件（用于判断要不要显示「重新校验」） */
      loadedFile: null,
      previewing: false,
      importing: false,
      reporting: false,
      imported: false,
      skipErrorRows: false,
      duplicateStrategy: 'reject',
      strategies: DUPLICATE_STRATEGIES_FALLBACK,
      result: null,
      errorPage: 1,
      errorPageSize: 10,
      steps: [
        { title: '下载模板并填写', desc: '第 1 行英文字段名 · 数据从第 3 行起' },
        { title: '预览校验', desc: '不写库，先看会新增/覆盖多少条' },
        { title: '确认导入', desc: '有错默认整批不入库' }
      ],
      errorColumns: [
        { key: 'rowNum', title: 'Excel 行号', width: 108, type: 'slot' },
        { key: 'crzdbh', title: '出让宗地编号', width: 200, type: 'slot' },
        { key: 'columnLabels', title: '出错字段', width: 220, type: 'slot' },
        { key: 'message', title: '错误原因', type: 'slot' }
      ]
    }
  },
  computed: {
    errorCount () {
      return this.result && this.result.errors ? this.result.errors.length : 0
    },
    /** 按 Excel 行号聚合后的错误行 */
    errorRows () {
      return groupErrorsByRow(this.result)
    },
    pagedErrorRows () {
      const start = (this.errorPage - 1) * this.errorPageSize
      return this.errorRows.slice(start, start + this.errorPageSize)
    },
    errorPageCount () {
      return Math.max(1, Math.ceil(this.errorRows.length / this.errorPageSize))
    },
    strategyOptions () {
      return (this.strategies || []).map((item) => ({ value: item.code, label: item.label }))
    },
    /** 有错误行时后端默认整批不入库，因此这里也默认禁用；勾「跳过错误行」才放行 */
    canImport () {
      return !this.errorCount || this.skipErrorRows
    },
    /** 本次会实际写入的行数 */
    importableCount () {
      if (!this.result) return 0
      if (this.errorCount && !this.skipErrorRows) return 0
      return this.result.validRows || 0
    },
    summaryText () {
      return importSummary(this.result) || '导入完成'
    }
  },
  created () {
    // 策略选项以接口为准（后台可以调整措辞），接口不可用时用本地兜底
    queryLandDuplicateStrategies()
      .then((res) => {
        if (res && res.success && res.result && res.result.length) {
          this.strategies = res.result
        }
      })
      .catch(() => {
        this.strategies = DUPLICATE_STRATEGIES_FALLBACK
      })
  },
  methods: {
    open () {
      this.visible = true
      this.reset()
    },

    reset () {
      this.step = 0
      this.file = null
      this.fileName = ''
      this.loadedFile = null
      this.previewing = false
      this.importing = false
      this.reporting = false
      this.imported = false
      this.skipErrorRows = false
      this.result = null
      this.errorPage = 1
      this.resetInput()
    },

    /**
     * 关闭弹窗。
     * ★ 只有「真的导入了」才把结果往页面上抛：用户只是预览了一下就关掉，
     *   不该在页面上留下一个「导入结果」（那会让人以为已经写库了）。
     */
    handleClose () {
      const changed = this.imported
      this.visible = false
      if (changed) {
        this.$emit('ok')
      }
    },

    handleDownloadTemplate () {
      downloadLandImportTemplate()
      toast.info('已开始下载模板，请稍候…')
    },

    /** 触发隐藏 input：打开选择器前清空 value，保证连选同一个文件也能再次触发 change */
    handlePickFile () {
      const input = this.$refs.fileInput
      if (!input) return
      input.value = ''
      input.click()
    },

    resetInput () {
      const input = this.$refs.fileInput
      if (input) input.value = ''
    },

    handleFileChange (event) {
      const input = event && event.target ? event.target : this.$refs.fileInput
      const files = input && input.files ? input.files : []
      if (!files.length) return
      const file = files[0]
      if (!/\.(xlsx|xls)$/i.test(file.name)) {
        toast.error('只支持 .xlsx / .xls 文件（请用「下载导入模板」得到的表填写）')
        this.resetInput()
        return
      }
      this.file = file
      this.fileName = file.name
      this.loadedFile = null
      this.result = null
      this.imported = false
      this.errorPage = 1
      this.step = 1
    },

    handleSkipChange (event) {
      this.skipErrorRows = !!(event && event.target && event.target.checked)
      this.errorPage = 1
    },

    handleErrorPageChange ({ current, pageSize }) {
      this.errorPage = current
      this.errorPageSize = pageSize
    },

    handlePreview () {
      this.doPreview(false)
    },

    handleRepreview () {
      this.doPreview(true)
    },

    doPreview (rePreview) {
      if (!this.file) return
      this.previewing = true
      const file = this.file
      previewLandImport(file, this.duplicateStrategy)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '预览失败')
            return
          }
          this.result = res.result || {}
          this.loadedFile = file
          this.imported = false
          this.skipErrorRows = false
          this.errorPage = 1
          this.step = 1
          if (this.errorCount) {
            toast.warning(`校验完成：${this.errorRows.length} 行有问题（共 ${this.errorCount} 条），请先修正（或勾选「跳过错误行」）`)
          } else {
            toast.success(`校验通过：将新增 ${this.result.willInsertRows || 0} 条、覆盖更新 ${this.result.willUpdateRows || 0} 条`)
          }
          if (rePreview) {
            toast.info('已按当前重复处理策略重新校验')
          }
        })
        .catch(() => {
          // 请求层已提示，这里只保证按钮不再 loading
        })
        .finally(() => {
          this.previewing = false
        })
    },

    handleConfirm () {
      if (!this.file || !this.canImport) return
      this.importing = true
      confirmLandImport(this.file, this.duplicateStrategy, this.skipErrorRows)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '导入失败')
            return
          }
          this.result = res.result || {}
          this.errorPage = 1
          if ((this.result.insertedRows || 0) + (this.result.updatedRows || 0) > 0) {
            this.imported = true
            this.step = 2
            toast.success(this.summaryText)
          } else {
            this.imported = false
            toast.warning('没有写入任何数据（存在错误行，且未勾选跳过）')
          }
        })
        .catch(() => {})
        .finally(() => {
          this.importing = false
        })
    },

    handleDownloadReport () {
      const file = this.loadedFile || this.file
      if (!file) {
        toast.warning('请先选择并预览文件')
        return
      }
      this.reporting = true
      downloadLandErrorReport(file, this.duplicateStrategy)
        .then(() => {
          toast.success('错误回执已开始下载')
        })
        .catch((error) => {
          toast.error((error && error.message) || '生成错误回执失败')
        })
        .finally(() => {
          this.reporting = false
        })
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-import {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  /* ---------------- 步骤条 ---------------- */
  &__steps {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3);
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__step {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    color: var(--screen-text-mute);
    background: var(--screen-elevate);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);

    &.is-active {
      color: var(--screen-text);
      border-color: var(--screen-accent);
    }

    &.is-done {
      color: var(--screen-text-sub);
      border-color: var(--screen-border);
    }
  }

  &__step-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 20px;
    height: 20px;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-on-accent);
    background: var(--screen-accent-deep);
    border-radius: 50%;
  }

  &__step.is-active &__step-index {
    background: var(--screen-accent);
  }

  &__step-text {
    display: flex;
    flex-direction: column;
    min-width: 0;

    b {
      font-size: var(--screen-font-sm);
      font-weight: 600;
      .screen-ellipsis();
    }

    em {
      font-style: normal;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-mute);
      .screen-ellipsis();
    }
  }

  /* ---------------- 区块 ---------------- */
  &__block {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
  }

  &__strategy {
    flex: 0 0 300px;
    min-width: 0;
  }

  &__hint {
    flex: 1 1 240px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);

    b {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }

    code {
      padding: 0 4px;
      font-family: var(--screen-font-number-family);
      font-size: var(--screen-font-xs);
      color: var(--screen-accent);
      background: rgba(6, 20, 40, 0.72);
      border-radius: var(--screen-radius-sm);
    }
  }

  /* 隐藏的原生文件输入：1px + clip 裁到不可见，但仍在可访问树与可编程激活范围内 */
  &__file {
    position: absolute;
    top: 0;
    left: 0;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    border: 0;
    overflow: hidden;
    white-space: nowrap;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
  }

  &__file-name {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    max-width: 320px;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent-soft);
    .screen-ellipsis();
  }

  /* ---------------- 结果统计 ---------------- */
  &__stats {
    display: flex;
    flex-wrap: wrap;
    gap: var(--screen-space-4);
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);

    b {
      margin-left: 4px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-md);
      color: var(--screen-text);
    }

    b.is-primary {
      color: var(--screen-accent);
    }

    b.is-ok {
      color: var(--screen-success);
    }

    b.is-error {
      color: var(--screen-danger);
    }
  }

  &__alert {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);

    &.is-ok {
      color: var(--screen-success);
      background: rgba(67, 233, 114, 0.08);
      border-color: var(--screen-success);
    }

    &.is-warning {
      color: var(--screen-warning);
      background: rgba(245, 165, 36, 0.08);
      border-color: var(--screen-warning);
    }

    &.is-info {
      color: var(--screen-accent-soft);
      background: rgba(130, 198, 255, 0.08);
    }
  }

  /* ---------------- 错误清单 ---------------- */
  &__errors {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding-top: var(--screen-space-2);
    border-top: 1px dashed var(--screen-border-soft);
  }

  &__errors-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
  }

  &__errors-title {
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-warning);
  }

  &__row-num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-warning);
  }

  &__mono {
    font-family: var(--screen-font-number-family);
    color: var(--screen-text-sub);
  }

  &__cols {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    flex-wrap: wrap;
  }

  &__error-text {
    color: var(--screen-danger);
  }

  &__errors-foot {
    display: flex;
    justify-content: flex-end;
  }

  &__errors-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);

    code {
      padding: 0 4px;
      font-family: var(--screen-font-number-family);
      color: var(--screen-text-sub);
      background: rgba(6, 20, 40, 0.72);
      border-radius: var(--screen-radius-sm);
    }
  }

  /* ---------------- 确认导入 ---------------- */
  &__confirm {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    padding-top: var(--screen-space-3);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__skip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    cursor: pointer;

    &.is-disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }
  }

  &__check {
    flex: 0 0 auto;
    width: 14px;
    height: 14px;
    margin: 0;
    accent-color: var(--screen-accent);
    cursor: inherit;
  }
}

@media (max-width: 1100px) {
  .land-import__steps {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
