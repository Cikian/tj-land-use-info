<template>
  <a-modal
    title="导入配套地块图形（GeoJSON）"
    :width="860"
    :visible="visible"
    :footer="null"
    :maskClosable="false"
    @cancel="handleClose">
    <!-- ============ 步骤条 ============ -->
    <a-steps :current="step" size="small" class="fgeo-modal__steps">
      <a-step title="选择 GeoJSON 文件" description="配套矢量成果（shp 转出）" />
      <a-step title="预览校验" description="不写库，先看能匹配上多少配套" />
      <a-step title="确认导入" description="有错误整批不写" />
    </a-steps>

    <!-- ============ 一、上传 ============ -->
    <div class="fgeo-modal__block">
      <div class="fgeo-modal__row">
        <a-upload
          :fileList="fileList"
          :beforeUpload="handleBeforeUpload"
          :remove="handleRemove"
          accept=".geojson,.json"
          :maxCount="1">
          <a-button icon="upload">选择 GeoJSON（.geojson / .json）</a-button>
        </a-upload>
        <a-button
          type="primary"
          icon="eye"
          :loading="previewing"
          :disabled="!file"
          @click="handlePreview">预览校验</a-button>
      </div>

      <div class="fgeo-modal__row">
        <span class="fgeo-modal__hint">
          按每个地块的 <code>PTXMMC</code>（配套项目名称）匹配系统里的配套记录，
          把记录的<b>地块图形</b>替换为文件里的多边形。
          名称匹配前会自动归一（半角括号 → 中文括号、连字符、去空白）。
          同一名称出现多个地块会<b>自动合并</b>为一块多面图形；
          <b>同名跨宗地的多条记录会全部更新</b>为同一份图形（预览中逐名称注明）；
          <b>导入即替换</b>：图形以最新一次导入为准。名称匹配不上的只跳过并警告，不影响其它。
        </span>
      </div>
    </div>

    <!-- ============ 二、结果 ============ -->
    <div v-if="result" class="fgeo-modal__block">
      <div class="fgeo-modal__stats">
        <span class="fgeo-modal__stat">解析地块 <b>{{ result.totalFeatures }}</b></span>
        <span class="fgeo-modal__stat">
          <template v-if="imported">已更新图形 <b class="is-ok">{{ result.updatedRows }}</b> 条</template>
          <template v-else>将更新图形 <b class="is-primary">{{ result.willUpdateRows }}</b> 条</template>
        </span>
        <span v-if="result.unchangedRows" class="fgeo-modal__stat">
          图形未变化 <b>{{ result.unchangedRows }}</b>
        </span>
        <span v-if="result.unmatchedCount" class="fgeo-modal__stat">
          未匹配名称 <b class="is-warn">{{ result.unmatchedCount }}</b>
        </span>
        <span class="fgeo-modal__stat">
          错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
        </span>
      </div>

      <a-alert
        v-if="imported"
        class="fgeo-modal__alert"
        type="success"
        showIcon
        :message="importedSummary" />
      <a-alert
        v-else-if="result.aborted"
        class="fgeo-modal__alert"
        type="warning"
        showIcon
        message="本次不会写入任何数据（存在错误），请修正文件后重传" />
      <a-alert
        v-else-if="!result.willUpdateRows"
        class="fgeo-modal__alert"
        type="info"
        showIcon
        message="没有需要更新的图形（匹配上的配套图形已与文件一致）" />
      <a-alert
        v-else
        class="fgeo-modal__alert"
        type="info"
        showIcon
        :message="`校验通过：确认导入将更新 ${result.willUpdateRows} 条配套的地块图形`" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="fgeo-modal__alert"
        type="warning"
        showIcon
        :message="item" />
      <a-alert
        v-for="(item, index) in result.notices || []"
        :key="'n' + index"
        class="fgeo-modal__alert"
        type="info"
        showIcon
        :message="item" />

      <!-- 错误清单 -->
      <div v-if="errorCount" class="fgeo-modal__errors">
        <div class="fgeo-modal__errors-title">
          错误清单（<b>{{ errorCount }}</b> 条）
        </div>
        <a-table
          rowKey="key"
          size="small"
          :columns="errorColumns"
          :dataSource="result.errors"
          :pagination="errorPagination"
          :scroll="{ y: 220 }">
          <template slot="ptxmmc" slot-scope="text">
            {{ text || '—' }}
          </template>
          <template slot="message" slot-scope="text">
            <span class="fgeo-modal__error-text">{{ text }}</span>
          </template>
        </a-table>
      </div>

      <!-- 确认导入 -->
      <div class="fgeo-modal__row fgeo-modal__confirm">
        <span v-if="imported" class="fgeo-modal__done-hint">
          导入完成。大屏上重新进入「配套地块数据录入」即可看到更新后的图形
        </span>
        <span v-else class="fgeo-modal__done-hint"></span>
        <a-button
          v-if="!imported"
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="!canImport"
          @click="handleConfirm">
          确认导入{{ canImport && result.willUpdateRows ? '（' + result.willUpdateRows + ' 条）' : '' }}
        </a-button>
        <a-button v-else type="primary" @click="handleClose">完成</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    confirmFacilityGeoImport,
    previewFacilityGeoImport
  } from '@/api/land/facilityAdmin'

  /**
   * 配套地块图形（GeoJSON）导入 - 弹窗
   * 与 LandGeoImportModal（经营性用地版）同一结构，差异只有匹配键（PTXMMC）
   * 与业务说明（同名跨宗地多条记录全部更新）。
   */
  export default {
    name: 'FacilityGeoImportModal',
    data () {
      return {
        visible: false,
        step: 0,
        file: null,
        fileList: [],
        previewing: false,
        importing: false,
        imported: false,
        result: null,
        errorPagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 条`
        },
        errorColumns: [
          { title: '序号', dataIndex: 'featureIndex', width: 70 },
          { title: '配套项目名称', dataIndex: 'ptxmmc', width: 260, scopedSlots: { customRender: 'ptxmmc' } },
          { title: '错误原因', dataIndex: 'message', scopedSlots: { customRender: 'message' } }
        ]
      }
    },
    computed: {
      errorCount () {
        return this.result && this.result.errors ? this.result.errors.length : 0
      },
      canImport () {
        if (!this.result) {
          return false
        }
        return !this.result.aborted && (this.result.willUpdateRows || 0) > 0
      },
      importedSummary () {
        const result = this.result || {}
        const parts = [`已更新 ${result.updatedRows || 0} 条配套的地块图形`]
        if (result.unchangedRows) {
          parts.push(`${result.unchangedRows} 条图形未变化`)
        }
        if (result.unmatchedCount) {
          parts.push(`${result.unmatchedCount} 个名称未匹配被跳过`)
        }
        return parts.join('，')
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
        this.result = null
      },
      handleClose () {
        const imported = this.imported
        const payload = imported
          ? { result: this.result, fileName: this.file ? this.file.name : '' }
          : null
        this.visible = false
        if (payload) {
          this.$emit('ok', payload)
        }
      },
      handleBeforeUpload (file) {
        if (!/\.(geojson|json)$/i.test(file.name)) {
          this.$message.error('只支持 .geojson / .json 文件（shp 转 GeoJSON 的导出成果）')
          return false
        }
        this.file = file
        this.fileList = [file]
        this.result = null
        this.imported = false
        this.step = 1
        return false
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
        previewFacilityGeoImport(this.file).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.imported = false
            this.step = 1
            if (this.errorCount) {
              this.$message.warning(`校验完成：存在 ${this.errorCount} 条错误，修正后重传`)
            } else if ((this.result.willUpdateRows || 0) > 0) {
              this.$message.success(`校验通过：将更新 ${this.result.willUpdateRows} 条配套的地块图形`)
            } else {
              this.$message.info('没有需要更新的图形')
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
        confirmFacilityGeoImport(this.file).then(res => {
          if (res.success) {
            this.result = res.result || {}
            if ((this.result.updatedRows || 0) > 0) {
              this.imported = true
              this.step = 2
              this.$message.success(this.importedSummary)
            } else {
              this.imported = false
              this.$message.warning('没有写入任何数据（没有匹配到需要更新的配套）')
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
  @primary: #2e7cf6;

  .fgeo-modal {
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

        &.is-warn {
          color: #d48806;
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

    &__errors-title {
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #d48806;

      b {
        color: #cf1322;
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
