<template>
  <a-modal
    title="批量补录资料"
    :width="920"
    :visible="visible"
    :footer="null"
    :maskClosable="false"
    @cancel="handleClose">
    <!-- ============ 一、准备与上传 ============ -->
    <a-steps :current="step" size="small" class="ledger-import__steps">
      <a-step title="下载模板并填写" description="空单元格 = 不修改" />
      <a-step title="预览校验" description="不写库，先看会改多少条" />
      <a-step title="确认导入" description="有错默认整批不入库" />
    </a-steps>

    <div class="ledger-import__block">
      <div class="ledger-import__row">
        <a-button icon="download" @click="handleDownloadTemplate">下载补录模板</a-button>
        <span class="ledger-import__hint">
          模板第 2 个 sheet 是「填表说明」：匹配键是<b>台账编号</b>，道路名称只用于核对；
          资料列填 √/是/1（已归集）或 ×/否/0（未归集）。
        </span>
      </div>

      <div class="ledger-import__row">
        <a-upload
          :fileList="fileList"
          :beforeUpload="handleBeforeUpload"
          :remove="handleRemove"
          accept=".xlsx"
          :maxCount="1">
          <a-button icon="upload">选择 Excel（.xlsx）</a-button>
        </a-upload>
        <a-button
          type="primary"
          icon="check-circle"
          :loading="previewing"
          :disabled="!file"
          @click="handlePreview">预览校验</a-button>
      </div>
    </div>

    <!-- ============ 二、结果 ============ -->
    <div v-if="result" class="ledger-import__block">
      <div class="ledger-import__stats">
        <span class="ledger-import__stat">
          解析数据行 <b>{{ result.totalRows }}</b>
        </span>
        <span class="ledger-import__stat">
          命中台账 <b>{{ result.matchedRows }}</b>
        </span>
        <span class="ledger-import__stat">
          <template v-if="imported">已更新 <b class="is-ok">{{ result.updatedRows }}</b></template>
          <template v-else>可更新 <b class="is-primary">{{ result.matchedRows - errorCount }}</b></template>
        </span>
        <span class="ledger-import__stat">
          新增资料 <b class="is-ok">{{ result.materialFilled }}</b> 项
        </span>
        <span class="ledger-import__stat">
          错误 <b :class="errorCount ? 'is-error' : ''">{{ errorCount }}</b> 条
        </span>
        <span v-if="result.skippedRows" class="ledger-import__stat">
          跳过 <b>{{ result.skippedRows }}</b> 行（无可写内容）
        </span>
      </div>

      <a-alert
        v-if="imported"
        class="ledger-import__alert"
        type="success"
        showIcon
        :message="`导入完成：已更新 ${result.updatedRows} 条台账，新增归集资料 ${result.materialFilled} 项`" />
      <a-alert
        v-else-if="result.aborted"
        class="ledger-import__alert"
        type="warning"
        showIcon
        message="本次不会写入任何数据（存在错误行）" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="ledger-import__alert"
        type="info"
        showIcon
        :message="item" />

      <div v-if="errorCount" class="ledger-import__errors">
        <div class="ledger-import__errors-title">
          错误清单（共 {{ errorCount }} 条，行号与 Excel 左侧行号一致）
        </div>
        <a-table
          rowKey="key"
          size="small"
          :columns="errorColumns"
          :dataSource="errorRows"
          :pagination="errorPagination"
          :scroll="{ y: 220 }">
          <template slot="message" slot-scope="text">
            <span class="ledger-import__error-text">{{ text }}</span>
          </template>
        </a-table>
      </div>

      <div v-if="!imported" class="ledger-import__row ledger-import__confirm">
        <a-checkbox v-model="skipErrorRows" :disabled="!errorCount">
          跳过错误行，只导入正确的行（{{ Math.max(0, result.matchedRows - errorCount) }} 行）
        </a-checkbox>
        <a-button
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="errorCount > 0 && !skipErrorRows"
          @click="handleConfirm">
          确认导入{{ canImport ? '（' + importableCount + ' 行）' : '' }}
        </a-button>
      </div>
      <div v-if="imported" class="ledger-import__row ledger-import__confirm">
        <a-button type="primary" @click="handleClose">完成</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    confirmLedgerImport,
    downloadLedgerTemplate,
    previewLedgerImport
  } from '@/api/land/ledger'

  /**
   * 道路设施验收及移交资料台账 - 批量补录弹窗（模块 C）
   *
   * 三步：下载模板 → 预览校验（不写库）→ 确认导入。
   *
   * 为什么必须「先预览」这一步：迁移过来的台账里还有 713 条一类资料都没勾、8 类资料全为 0，
   * 中心会成批补录。批量写库是**不可逆**的（虽然逻辑删除还在，但错了很难逐条回退），
   * 所以先让用户看到「会改多少条、错在哪几行、道路名称对不对得上」，再决定是否落库。
   *
   * 交互上的几个刻意设计：
   *  1. 有错误行时**默认禁用「确认导入」**（因为后端默认也是整批不入库）；
   *     用户确实想先救一部分时，勾「跳过错误行」才可点；
   *  2. 错误清单分页展示，行号用 Excel 的 1 基行号，可照着直接跳过去改；
   *  3. 道路名称不一致只给警告（不阻断），因为那是「核对信息」而不是错误。
   */
  export default {
    name: 'LedgerImportModal',
    data () {
      return {
        visible: false,
        step: 0,
        file: null,
        fileList: [],
        previewing: false,
        importing: false,
        imported: false,
        skipErrorRows: false,
        result: null,
        errorPagination: {
          pageSize: 10,
          showSizeChanger: false,
          size: 'small',
          showTotal: total => `共 ${total} 条`
        },
        errorColumns: [
          { title: 'Excel 行号', dataIndex: 'rowNum', width: 90 },
          { title: '台账编号', dataIndex: 'ledgerNo', width: 150, customRender: text => text || '—' },
          { title: '原因', dataIndex: 'message', scopedSlots: { customRender: 'message' } }
        ]
      }
    },
    computed: {
      errorCount () {
        return this.result && this.result.errors ? this.result.errors.length : 0
      },
      /** 错误清单加个稳定 key，避免同页两行重复导致渲染告警 */
      errorRows () {
        if (!this.result || !this.result.errors) {
          return []
        }
        return this.result.errors.map((item, index) => Object.assign({ key: index }, item))
      },
      canImport () {
        // 有错误行时后端默认整批不入库，因此这里也默认禁用；勾「跳过错误行」才放行
        return !this.errorCount || this.skipErrorRows
      },
      importableCount () {
        // matchedRows 已经是「命中且有待写内容」的行数（错误行不在其中）
        if (!this.result) {
          return 0
        }
        if (this.errorCount && !this.skipErrorRows) {
          return 0
        }
        return this.result.matchedRows
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
        this.fileList = []
        this.previewing = false
        this.importing = false
        this.imported = false
        this.skipErrorRows = false
        this.result = null
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
        this.$message.success('已开始下载模板，请稍候…')
      },
      handleBeforeUpload (file) {
        if (!/\.xlsx$/i.test(file.name)) {
          this.$message.error('只支持 .xlsx 文件（请用「下载补录模板」得到的表填写）')
          return false
        }
        this.file = file
        this.fileList = [file]
        this.result = null
        this.imported = false
        this.step = 1
        return false // 阻止 a-upload 自动上传：由本组件手动调预览/入库接口
      },
      handleRemove () {
        this.file = null
        this.fileList = []
        this.result = null
        this.step = 0
      },
      handlePreview () {
        if (!this.file) {
          return
        }
        this.previewing = true
        previewLedgerImport(this.file).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.imported = false
            this.step = 1
            if (this.errorCount) {
              this.$message.warning(`校验完成：发现 ${this.errorCount} 条错误，请先修正（或勾选「跳过错误行」）`)
            } else {
              this.$message.success(`校验通过：可更新 ${this.result.matchedRows} 条台账`)
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
        confirmLedgerImport(this.file, this.skipErrorRows).then(res => {
          if (res.success) {
            this.result = res.result || {}
            if (this.result.updatedRows > 0) {
              this.imported = true
              this.step = 2
              this.$message.success(`导入完成：已更新 ${this.result.updatedRows} 条台账`)
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
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .ledger-import {
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
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 12px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    &__hint {
      flex: 1 1 auto;
      min-width: 240px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
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
      }

      b.is-primary {
        color: #2e7cf6;
      }

      b.is-ok {
        color: #10b981;
      }

      b.is-error {
        color: #ef4444;
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

    &__errors-title {
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #d48806;
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
  }
</style>
