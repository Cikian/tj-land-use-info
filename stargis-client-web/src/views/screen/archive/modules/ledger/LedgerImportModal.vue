<template>
  <!--
    LedgerImportModal Excel 批量补录资料
    --------------------------------
    三步：下载模板 → 预览校验（不写库）→ 确认导入。

    ★ 为什么必须先「预览」：迁移过来的台账里还有 713 条一类资料都没勾、8 类资料全为 0，
      中心会成批补录。批量写库是不可逆的（逻辑删除还在，但错了很难逐条回退），
      所以先让用户看到「会改多少条、错在哪几行」，再决定是否落库。

    交互上的几个刻意设计（与 admin-client 版本一致，保证两端同一套心智）：
      1. 有错误行时**默认禁用「确认导入」**（后端默认也是整批不入库），
         确实想先救一部分时勾「跳过错误行」才放行；
      2. 错误清单分页展示，行号用 Excel 的 1 基行号，可照着直接跳过去改；
      3. 「空单元格 = 不修改」写在提示里 —— 这是最容易误解的一点，
         用户以为空着就会被清空，于是不敢传只填了部分列的表。

    ★ 文件选择用原生 input（1px 裁切、保留在可访问树里）而不是 ScreenUpload：
      ScreenUpload 会**自己把文件 POST 到 action**，拿到的是 jeecg 通用上传接口的
      存储路径；而批量补录要的是「把文件交给 preview/confirm 两个业务接口」，
      需要拿到 File 对象自己发请求，所以这里用原生 input + 组件库按钮触发。

    事件：
      ok  导入成功后抛出，面板据此刷新列表与统计
  -->
  <screen-modal
    :visible.sync="visible"
    title="Excel 批量补录资料"
    :width="940"
    :show-footer="false"
    :body-max-height="'calc(100vh - 180px)'"
    @cancel="handleClose"
  >
    <div class="ledger-import">
      <!-- ================= 步骤条 ================= -->
      <ol class="ledger-import__steps">
        <li
          v-for="(item, index) in steps"
          :key="item.title"
          class="ledger-import__step"
          :class="{ 'is-active': step === index, 'is-done': step > index }"
        >
          <span class="ledger-import__step-index">{{ index + 1 }}</span>
          <span class="ledger-import__step-text">
            <b>{{ item.title }}</b>
            <em>{{ item.desc }}</em>
          </span>
        </li>
      </ol>

      <!-- ================= 一、模板与文件 ================= -->
      <section class="ledger-import__block">
        <div class="ledger-import__row">
          <screen-button icon="download" @click="handleDownloadTemplate">下载补录模板</screen-button>
          <span class="ledger-import__hint">
            模板第 2 个 sheet 是「填表说明」：匹配键是<b>台账编号</b>，道路名称只用于核对；
            资料列填 √/是/1（已归集）或 ×/否/0（未归集）；
            <b>空单元格表示「不修改」</b>，不会把库里已有的值清空。
          </span>
        </div>

        <div class="ledger-import__row">
          <input
            id="ledger-import-file"
            ref="fileInput"
            class="ledger-import__file"
            type="file"
            accept=".xlsx"
            aria-hidden="true"
            tabindex="-1"
            @change="handleFileChange"
          />
          <screen-button icon="upload" @click="handlePickFile">选择 Excel（.xlsx）</screen-button>
          <span v-if="fileName" class="ledger-import__file-name" :title="fileName">
            <screen-icon name="file-text" :size="13" />
            {{ fileName }}
          </span>
          <span v-else class="ledger-import__hint">尚未选择文件</span>

          <screen-button
            type="primary"
            icon="check-circle"
            :loading="previewing"
            :disabled="!file"
            @click="handlePreview"
          >
            预览校验
          </screen-button>
        </div>
      </section>

      <!-- ================= 二、结果 ================= -->
      <section v-if="result" class="ledger-import__block">
        <div class="ledger-import__stats">
          <span class="ledger-import__stat">解析数据行 <b>{{ result.totalRows || 0 }}</b></span>
          <span class="ledger-import__stat">命中台账 <b>{{ result.matchedRows || 0 }}</b></span>
          <span class="ledger-import__stat">
            <template v-if="imported">已更新 <b class="is-ok">{{ result.updatedRows || 0 }}</b></template>
            <template v-else>可更新 <b class="is-primary">{{ importableCount }}</b></template>
          </span>
          <span class="ledger-import__stat">新增资料 <b class="is-ok">{{ result.materialFilled || 0 }}</b> 项</span>
          <span class="ledger-import__stat">
            错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
          </span>
          <span v-if="result.skippedRows" class="ledger-import__stat">
            跳过 <b>{{ result.skippedRows }}</b> 行（无可写内容）
          </span>
        </div>

        <p v-if="imported" class="ledger-import__alert is-ok" role="status">
          <screen-icon name="check-circle" :size="14" />
          导入完成：已更新 {{ result.updatedRows || 0 }} 条台账，新增归集资料 {{ result.materialFilled || 0 }} 项
        </p>
        <p v-else-if="result.aborted" class="ledger-import__alert is-warning" role="status">
          <screen-icon name="alert-triangle" :size="14" />
          本次不会写入任何数据（存在错误行）
        </p>

        <p
          v-for="(item, index) in result.warnings || []"
          :key="'warn-' + index"
          class="ledger-import__alert is-info"
        >
          <screen-icon name="info" :size="14" />
          {{ item }}
        </p>

        <!-- 错误清单：Excel 行号 + 台账编号 + 原因（用户能直接跳到那一行改） -->
        <div v-if="errorCount" class="ledger-import__errors">
          <h4 class="ledger-import__errors-title">
            错误清单（共 {{ errorCount }} 条，行号与 Excel 左侧行号一致）
          </h4>

          <screen-data-table
            :columns="errorColumns"
            :data="pagedErrors"
            row-key="key"
            :min-width="620"
            :animated="false"
          >
            <template #rowNum="{ row }">
              <span class="ledger-import__row-num">第 {{ row.rowNum }} 行</span>
            </template>
            <template #message="{ row }">
              <span class="ledger-import__error-text">{{ row.message }}</span>
            </template>
          </screen-data-table>

          <div v-if="errorPageCount > 1" class="ledger-import__errors-foot">
            <screen-pagination
              :current="errorPage"
              :page-size="errorPageSize"
              :total="errorCount"
              :page-size-options="[10, 20, 50]"
              @change="handleErrorPageChange"
            />
          </div>
        </div>

        <!-- ================= 三、确认导入 ================= -->
        <div v-if="!imported" class="ledger-import__confirm">
          <label class="ledger-import__skip" :class="{ 'is-disabled': !errorCount }">
            <input
              class="ledger-import__check"
              type="checkbox"
              :checked="skipErrorRows"
              :disabled="!errorCount"
              @change="handleSkipChange"
            />
            跳过错误行，只导入正确的行（{{ Math.max(0, (result.matchedRows || 0) - errorCount) }} 行）
          </label>

          <screen-button
            type="primary"
            icon="check"
            :loading="importing"
            :disabled="!canImport"
            @click="handleConfirm"
          >
            确认导入{{ canImport ? '（' + importableCount + ' 行）' : '' }}
          </screen-button>
        </div>

        <div v-else class="ledger-import__confirm">
          <span class="ledger-import__hint">导入完成，可关闭本窗口查看列表。</span>
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
  ScreenDataTable,
  ScreenPagination
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { downloadLedgerTemplate, previewLedgerImport, confirmLedgerImport } from '@/api/land/ledger'

/** 错误清单列（行号 / 台账编号 / 原因） */
const ERROR_COLUMNS = [
  { key: 'rowNum', title: 'Excel 行号', width: 110, type: 'slot' },
  { key: 'ledgerNo', title: '台账编号', width: 150 },
  { key: 'message', title: '原因', type: 'slot' }
]

export default {
  name: 'LedgerImportModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenIcon,
    ScreenDataTable,
    ScreenPagination
  },
  data () {
    return {
      visible: false,
      step: 0,
      file: null,
      fileName: '',
      previewing: false,
      importing: false,
      imported: false,
      skipErrorRows: false,
      result: null,
      errorPage: 1,
      errorPageSize: 10,
      errorColumns: ERROR_COLUMNS,
      steps: [
        { title: '下载模板并填写', desc: '空单元格 = 不修改' },
        { title: '预览校验', desc: '不写库，先看会改多少条' },
        { title: '确认导入', desc: '有错默认整批不入库' }
      ]
    }
  },
  computed: {
    errorCount () {
      return this.result && this.result.errors ? this.result.errors.length : 0
    },
    /** 错误清单加个稳定 key，避免同页两行重复导致渲染告警 */
    errorRows () {
      if (!this.result || !this.result.errors) return []
      return this.result.errors.map((item, index) => Object.assign({ key: index }, item))
    },
    /** 错误清单客户端分页（ScreenDataTable 不自带分页，一屏几十条错误要靠翻页看） */
    pagedErrors () {
      const start = (this.errorPage - 1) * this.errorPageSize
      return this.errorRows.slice(start, start + this.errorPageSize)
    },
    errorPageCount () {
      return Math.max(1, Math.ceil(this.errorCount / this.errorPageSize))
    },
    /** 有错误行时后端默认整批不入库，因此这里也默认禁用；勾「跳过错误行」才放行 */
    canImport () {
      return !this.errorCount || this.skipErrorRows
    },
    /** 本次会实际写入的行数（matchedRows 已经是「命中且有待写内容」的行） */
    importableCount () {
      if (!this.result) return 0
      if (this.errorCount && !this.skipErrorRows) return 0
      return this.result.matchedRows || 0
    }
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
      this.previewing = false
      this.importing = false
      this.imported = false
      this.skipErrorRows = false
      this.result = null
      this.errorPage = 1
      this.resetInput()
    },

    handleClose () {
      const changed = this.imported
      this.visible = false
      if (changed) {
        this.$emit('ok')
      }
    },

    handleDownloadTemplate () {
      downloadLedgerTemplate()
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
      // 只认 .xlsx：模板导出的就是 xlsx，.xls 的文件格式会让解析直接失败
      if (!/\.xlsx$/i.test(file.name)) {
        toast.error('只支持 .xlsx 文件（请用「下载补录模板」得到的表填写）')
        this.resetInput()
        return
      }
      this.file = file
      this.fileName = file.name
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
      if (!this.file) return
      this.previewing = true
      previewLedgerImport(this.file)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '预览失败')
            return
          }
          this.result = res.result || {}
          this.imported = false
          this.errorPage = 1
          this.step = 1
          if (this.errorCount) {
            toast.warning(`校验完成：发现 ${this.errorCount} 条错误，请先修正（或勾选「跳过错误行」）`)
          } else {
            toast.success(`校验通过：可更新 ${this.result.matchedRows || 0} 条台账`)
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
      confirmLedgerImport(this.file, this.skipErrorRows)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '导入失败')
            return
          }
          this.result = res.result || {}
          this.errorPage = 1
          if ((this.result.updatedRows || 0) > 0) {
            this.imported = true
            this.step = 2
            toast.success(`导入完成：已更新 ${this.result.updatedRows} 条台账`)
          } else {
            this.imported = false
            toast.warning('没有写入任何数据（存在错误行，且未勾选跳过）')
          }
        })
        .catch(() => {})
        .finally(() => {
          this.importing = false
        })
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-import {
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
  }

  // 隐藏的原生文件输入：1px + clip 裁到不可见，但仍在可访问树与可编程激活范围内
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

  &__error-text {
    color: var(--screen-danger);
  }

  &__errors-foot {
    display: flex;
    justify-content: flex-end;
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
  .ledger-import__steps {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
