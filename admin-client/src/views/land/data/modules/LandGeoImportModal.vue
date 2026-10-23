<template>
  <a-modal
    title="导入地块图形（GeoJSON）"
    :width="860"
    :visible="visible"
    :footer="null"
    :maskClosable="false"
    @cancel="handleClose">
    <!-- ============ 步骤条 ============ -->
    <a-steps :current="step" size="small" class="geo-modal__steps">
      <a-step title="选择 GeoJSON 文件" description="shp 转出的 FeatureCollection" />
      <a-step title="预览校验" description="不写库，先看能匹配上多少宗地" />
      <a-step title="确认导入" description="有错误整批不写" />
    </a-steps>

    <!-- ============ 一、上传 ============ -->
    <div class="geo-modal__block">
      <div class="geo-modal__row">
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

      <div class="geo-modal__row">
        <span class="geo-modal__hint">
          按每个地块的 <code>CRZDBH</code>（出让宗地编号）匹配系统里的宗地，
          把宗地记录的<b>地块图形</b>替换为文件里的多边形。
          编号匹配前会自动归一（半角括号 → 中文括号、去空白、补「号」结尾），
          所以文件里的「津蓟（挂）2017-025」能匹配上库里「津蓟（挂）2017-025号」。
          同一编号出现多个地块会<b>自动合并</b>为一块多面图形；
          <b>导入即替换</b>：宗地图形以最新一次导入为准。编号匹配不上的地块只跳过并警告，不影响其它宗地。
        </span>
      </div>
    </div>

    <!-- ============ 二、结果 ============ -->
    <div v-if="result" class="geo-modal__block">
      <div class="geo-modal__stats">
        <span class="geo-modal__stat">解析地块 <b>{{ result.totalFeatures }}</b></span>
          <span class="geo-modal__stat">
            <template v-if="imported">已更新图形 <b class="is-ok">{{ result.updatedRows }}</b> 宗</template>
            <template v-else>将更新图形 <b class="is-primary">{{ result.willUpdateRows }}</b> 宗</template>
          </span>
        <span v-if="result.unchangedRows" class="geo-modal__stat">
          图形未变化 <b>{{ result.unchangedRows }}</b>
        </span>
        <span v-if="result.unmatchedCount" class="geo-modal__stat">
          未匹配 <b class="is-warn">{{ result.unmatchedCount }}</b>
        </span>
        <span class="geo-modal__stat">
          错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
        </span>
      </div>

      <a-alert
        v-if="imported"
        class="geo-modal__alert"
        type="success"
        showIcon
        :message="importedSummary" />
      <a-alert
        v-else-if="result.aborted"
        class="geo-modal__alert"
        type="warning"
        showIcon
        message="本次不会写入任何数据（存在错误），请修正文件后重传" />
      <a-alert
        v-else-if="!result.willUpdateRows"
        class="geo-modal__alert"
        type="info"
        showIcon
        message="没有需要更新的图形（匹配上的宗地图形已与文件一致）" />
      <a-alert
        v-else
        class="geo-modal__alert"
        type="info"
        showIcon
        :message="`校验通过：确认导入将更新 ${result.willUpdateRows} 个宗地的地块图形`" />

      <a-alert
        v-for="(item, index) in result.warnings || []"
        :key="'w' + index"
        class="geo-modal__alert"
        type="warning"
        showIcon
        :message="item" />
      <a-alert
        v-for="(item, index) in result.notices || []"
        :key="'n' + index"
        class="geo-modal__alert"
        type="info"
        showIcon
        :message="item" />

      <!-- 错误清单 -->
      <div v-if="errorCount" class="geo-modal__errors">
        <div class="geo-modal__errors-title">
          错误清单（<b>{{ errorCount }}</b> 条）
        </div>
        <a-table
          rowKey="key"
          size="small"
          :columns="errorColumns"
          :dataSource="result.errors"
          :pagination="errorPagination"
          :scroll="{ y: 220 }">
          <template slot="crzdbh" slot-scope="text">
            {{ text || '—' }}
          </template>
          <template slot="message" slot-scope="text">
            <span class="geo-modal__error-text">{{ text }}</span>
          </template>
        </a-table>
      </div>

      <!-- 确认导入 -->
      <div class="geo-modal__row geo-modal__confirm">
        <span v-if="imported" class="geo-modal__done-hint">
          导入完成。大屏上重新进入「数据管理」即可看到更新后的地块图形
        </span>
        <span v-else class="geo-modal__done-hint"></span>
        <a-button
          v-if="!imported"
          type="primary"
          icon="import"
          :loading="importing"
          :disabled="!canImport"
          @click="handleConfirm">
          确认导入{{ canImport && result.willUpdateRows ? '（' + result.willUpdateRows + ' 宗）' : '' }}
        </a-button>
        <a-button v-else type="primary" @click="handleClose">完成</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import {
    confirmLandGeoImport,
    previewLandGeoImport
  } from '@/api/land/landGeoImport'

  /**
   * 地块图形（GeoJSON）导入 - 弹窗
   *
   * 三步：选择文件 → 预览校验（不写库）→ 确认导入。
   * 结构与 LandImportModal（Excel 批量导入）一致，按本功能裁剪：
   *  - 没有「下载模板」：GeoJSON 由 GIS 成果（shp 转出）而来，没有可下发的模板；
   *  - 没有「重复处理策略」：本功能只做「按编号更新图形」，没有分支；
   *    文件内编号重复由后端报错，改文件重传即可；
   *  - 没有「跳过错误行」：错误只有编号缺失/重复/图形不合法三类，
   *    且未匹配本来就走警告不阻断 —— 要先救一部分时把有问题的地块从文件里删掉重传；
   *  - 没有「错误回执下载」：错误条数少且带序号+编号，弹窗表格足够定位。
   */
  export default {
    name: 'LandGeoImportModal',
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
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 220, scopedSlots: { customRender: 'crzdbh' } },
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
        const parts = [`已更新 ${result.updatedRows || 0} 个宗地的地块图形`]
        if (result.unchangedRows) {
          parts.push(`${result.unchangedRows} 个图形未变化`)
        }
        if (result.unmatchedCount) {
          parts.push(`${result.unmatchedCount} 个编号未匹配被跳过`)
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
      /**
       * 关闭弹窗。
       *
       * ★ 只有「真的导入了」才往页面上抛 ok 事件：
       *   只预览就关掉，不该让页面以为数据变了而刷新。
       */
      handleClose () {
        const imported = this.imported
        const payload = imported
          ? {
            result: this.result,
            fileName: this.file ? this.file.name : ''
          }
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
        // 阻止 a-upload 自动上传：由本组件手动调预览/入库接口
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
        const file = this.file
        previewLandGeoImport(file).then(res => {
          if (res.success) {
            this.result = res.result || {}
            this.imported = false
            this.step = 1
            if (this.errorCount) {
              this.$message.warning(`校验完成：存在 ${this.errorCount} 条错误，修正后重传`)
            } else if ((this.result.willUpdateRows || 0) > 0) {
              this.$message.success(`校验通过：将更新 ${this.result.willUpdateRows} 个宗地的地块图形`)
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
        confirmLandGeoImport(this.file).then(res => {
          if (res.success) {
            this.result = res.result || {}
            if ((this.result.updatedRows || 0) > 0) {
              this.imported = true
              this.step = 2
              this.$message.success(this.importedSummary)
            } else {
              this.imported = false
              this.$message.warning('没有写入任何数据（没有匹配到需要更新的宗地）')
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

  .geo-modal {
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
