<template>
  <!--
    FacilityGeoImportModal 配套地块图形（GeoJSON）导入
    --------------------------------
    上传配套矢量成果 GeoJSON，按每个 Feature 的 properties.PTXMMC（配套项目名称）
    匹配配套记录，把记录的 dk_geojson 更新为该 Feature 的 geometry。
    结构与 LandGeoImportModal（经营性用地版）同一套心智，差异只有匹配键与业务说明：
      · 匹配键是 PTXMMC（配套项目名称，归一后）——配套 shp 成果属性里没有 CRZDBH；
      · 同名称跨宗地多条记录是业务常态（一条道路在多个宗地各记一条），
        导入时全部更新同一份图形，预览里逐名称注明；
      · 同名称多个 Feature 自动合并为一块多面图形；导入即替换。
    事件：ok 导入成功后抛出，面板据此刷新列表与几何图层。
  -->
  <screen-modal
    :visible.sync="visible"
    title="导入配套地块图形（GeoJSON）"
    :width="880"
    :show-footer="false"
    :body-max-height="'calc(100vh - 180px)'"
    @cancel="handleClose"
  >
    <div class="facility-geo-import">
      <!-- ================= 步骤条 ================= -->
      <ol class="facility-geo-import__steps">
        <li
          v-for="(item, index) in steps"
          :key="item.title"
          class="facility-geo-import__step"
          :class="{ 'is-active': step === index, 'is-done': step > index }"
        >
          <span class="facility-geo-import__step-index">{{ index + 1 }}</span>
          <span class="facility-geo-import__step-text">
            <b>{{ item.title }}</b>
            <em>{{ item.desc }}</em>
          </span>
        </li>
      </ol>

      <!-- ================= 一、选择文件 ================= -->
      <section class="facility-geo-import__block">
        <div class="facility-geo-import__row">
          <input
            id="facility-geo-import-file"
            ref="fileInput"
            class="facility-geo-import__file"
            type="file"
            accept=".geojson,.json"
            aria-hidden="true"
            tabindex="-1"
            @change="handleFileChange"
          />
          <screen-button icon="upload" @click="handlePickFile">选择 GeoJSON（.geojson / .json）</screen-button>
          <span v-if="fileName" class="facility-geo-import__file-name" :title="fileName">
            <screen-icon name="file-text" :size="13" />
            {{ fileName }}
          </span>
          <span v-else class="facility-geo-import__hint">尚未选择文件</span>

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

        <div class="facility-geo-import__row">
          <span class="facility-geo-import__hint">
            按每个地块的 <code>PTXMMC</code>（配套项目名称）匹配系统里的配套记录，
            把记录的<b>地块图形</b>替换为文件里的多边形。
            名称匹配前会自动归一（半角括号 → 中文括号、连字符、去空白）。
            同一名称出现多个地块会<b>自动合并</b>为一块多面图形；
            <b>同名跨宗地的多条记录会全部更新</b>为同一份图形（预览中逐名称注明）；
            <b>导入即替换</b>：图形以最新一次导入为准。名称匹配不上的只跳过并警告，不影响其它。
          </span>
        </div>
      </section>

      <!-- ================= 二、结果 ================= -->
      <section v-if="result" class="facility-geo-import__block">
        <div class="facility-geo-import__stats">
          <span class="facility-geo-import__stat">解析地块 <b>{{ result.totalFeatures || 0 }}</b></span>
          <span class="facility-geo-import__stat">
            <template v-if="imported">已更新图形 <b class="is-ok">{{ result.updatedRows || 0 }}</b> 条</template>
            <template v-else>将更新图形 <b class="is-primary">{{ result.willUpdateRows || 0 }}</b> 条</template>
          </span>
          <span v-if="result.unchangedRows" class="facility-geo-import__stat">
            图形未变化 <b>{{ result.unchangedRows }}</b>
          </span>
          <span v-if="result.unmatchedCount" class="facility-geo-import__stat">
            未匹配名称 <b class="is-warn">{{ result.unmatchedCount }}</b>
          </span>
          <span class="facility-geo-import__stat">
            错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
          </span>
        </div>

        <p v-if="imported" class="facility-geo-import__alert is-ok" role="status">
          <screen-icon name="check-circle" :size="14" />
          {{ summaryText }}
        </p>
        <p v-else-if="result.aborted" class="facility-geo-import__alert is-warning" role="status">
          <screen-icon name="alert-triangle" :size="14" />
          本次不会写入任何数据（存在错误），请修正文件后重传
        </p>
        <p v-else-if="!result.willUpdateRows" class="facility-geo-import__alert is-info" role="status">
          <screen-icon name="info" :size="14" />
          没有需要更新的图形（匹配上的配套图形已与文件一致）
        </p>
        <p v-else class="facility-geo-import__alert is-info" role="status">
          <screen-icon name="info" :size="14" />
          校验通过：确认导入将更新 {{ result.willUpdateRows }} 条配套的地块图形
        </p>

        <p
          v-for="(item, index) in result.warnings || []"
          :key="'warn-' + index"
          class="facility-geo-import__alert is-warning"
        >
          <screen-icon name="alert-triangle" :size="14" />
          {{ item }}
        </p>
        <p
          v-for="(item, index) in result.notices || []"
          :key="'notice-' + index"
          class="facility-geo-import__alert is-info"
        >
          <screen-icon name="info" :size="14" />
          {{ item }}
        </p>

        <!-- 错误清单 -->
        <div v-if="errorCount" class="facility-geo-import__errors">
          <h4 class="facility-geo-import__errors-title">
            错误清单（{{ errorCount }} 条）
          </h4>

          <screen-data-table
            :columns="errorColumns"
            :data="pagedErrors"
            row-key="key"
            :min-width="560"
            :animated="false"
          >
            <template #featureIndex="{ row }">
              <span class="facility-geo-import__mono">第 {{ row.featureIndex }} 个</span>
            </template>
            <template #ptxmmc="{ row }">
              <span class="facility-geo-import__mono">{{ row.ptxmmc || '—' }}</span>
            </template>
            <template #message="{ row }">
              <span class="facility-geo-import__error-text">{{ row.message }}</span>
            </template>
          </screen-data-table>

          <div v-if="errorPageCount > 1" class="facility-geo-import__errors-foot">
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
        <div class="facility-geo-import__confirm">
          <template v-if="!imported">
            <span class="facility-geo-import__hint"></span>
            <screen-button
              type="primary"
              icon="check"
              :loading="importing"
              :disabled="!canImport"
              @click="handleConfirm"
            >
              确认导入{{ canImport && result.willUpdateRows ? '（' + result.willUpdateRows + ' 条）' : '' }}
            </screen-button>
          </template>
          <template v-else>
            <span class="facility-geo-import__hint">导入完成，关闭后地图上的配套图形即按新数据重铺。</span>
            <screen-button type="primary" @click="handleClose">完成</screen-button>
          </template>
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
import {
  previewFacilityGeoImport,
  confirmFacilityGeoImport
} from '@/api/land/facilityGeoImport'

export default {
  name: 'FacilityGeoImportModal',
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
      result: null,
      errorPage: 1,
      errorPageSize: 10,
      steps: [
        { title: '选择 GeoJSON 文件', desc: '配套矢量成果（shp 转出）' },
        { title: '预览校验', desc: '不写库，先看能匹配上多少配套' },
        { title: '确认导入', desc: '有错误整批不写' }
      ],
      errorColumns: [
        { key: 'featureIndex', title: '位置', width: 96, type: 'slot' },
        { key: 'ptxmmc', title: '配套项目名称', width: 260, type: 'slot' },
        { key: 'message', title: '错误原因', type: 'slot' }
      ]
    }
  },
  computed: {
    errorCount () {
      return this.result && this.result.errors ? this.result.errors.length : 0
    },
    pagedErrors () {
      const start = (this.errorPage - 1) * this.errorPageSize
      return (this.result && this.result.errors ? this.result.errors : []).slice(start, start + this.errorPageSize)
    },
    errorPageCount () {
      return Math.max(1, Math.ceil(this.errorCount / this.errorPageSize))
    },
    canImport () {
      if (!this.result) return false
      return !this.result.aborted && (this.result.willUpdateRows || 0) > 0
    },
    summaryText () {
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
      this.fileName = ''
      this.previewing = false
      this.importing = false
      this.imported = false
      this.result = null
      this.errorPage = 1
      this.resetInput()
    },

    /** 只有「真的导入了」才抛 ok：只预览就关掉，面板不该重铺图层 */
    handleClose () {
      const changed = this.imported
      this.visible = false
      if (changed) {
        this.$emit('ok')
      }
    },

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
      if (!/\.(geojson|json)$/i.test(file.name)) {
        toast.error('只支持 .geojson / .json 文件（shp 转 GeoJSON 的导出成果）')
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

    handleErrorPageChange ({ current, pageSize }) {
      this.errorPage = current
      this.errorPageSize = pageSize
    },

    handlePreview () {
      if (!this.file) return
      this.previewing = true
      const file = this.file
      previewFacilityGeoImport(file)
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
            toast.warning(`校验完成：存在 ${this.errorCount} 条错误，修正后重传`)
          } else if ((this.result.willUpdateRows || 0) > 0) {
            toast.success(`校验通过：将更新 ${this.result.willUpdateRows} 条配套的地块图形`)
          } else {
            toast.info('没有需要更新的图形')
          }
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.previewing = false
        })
    },

    handleConfirm () {
      if (!this.file || !this.canImport) return
      this.importing = true
      confirmFacilityGeoImport(this.file)
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
            toast.success(this.summaryText)
          } else {
            this.imported = false
            toast.warning('没有写入任何数据（没有匹配到需要更新的配套）')
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

.facility-geo-import {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

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

    code {
      padding: 0 4px;
      font-family: var(--screen-font-number-family);
      font-size: var(--screen-font-xs);
      color: var(--screen-accent);
      background: rgba(6, 20, 40, 0.72);
      border-radius: var(--screen-radius-sm);
    }
  }

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

    b.is-warn {
      color: var(--screen-warning);
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

  &__mono {
    font-family: var(--screen-font-number-family);
    color: var(--screen-text-sub);
  }

  &__error-text {
    color: var(--screen-danger);
  }

  &__errors-foot {
    display: flex;
    justify-content: flex-end;
  }

  &__confirm {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    padding-top: var(--screen-space-3);
    border-top: 1px solid var(--screen-border-soft);
  }
}
</style>
