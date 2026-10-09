<template>
  <!--
    FacilityImportModal 配套信息批量导入
    --------------------------------
    三步：下载模板 → 预览校验（不写库）→ 确认入库，
    结构照档案模块的 LedgerImportModal，但多出**配套特有的三件事**，
    这三件事才是本弹窗真正要解决的问题：

    ① 重复处理策略（三选一）
       判重键是 **(宗地编号, 配套项目名称) 两列一起** —— 不是宗地导入的「编号即唯一」。
       一块地上可以有很多配套，只要名称不重复。
       reject（默认，重复即报错，最安全）/ skip（跳过重复只导新增，等价旧系统行为）/
       update（覆盖更新，**空单元格不会清空库内原值** —— 否则拿一份只填进度的
       补丁表导进去，会把六方单位、资金来源、录入人全部抹空）。

    ② ★★ 孤儿清单（本页最核心的新东西）
       「孤儿」= 这一行的出让宗地编号在 `t_land` 里**查不到**，也就是这条配套
       在系统里没有归属的宗地。旧系统对此**完全不校验**，实测库里留下了 60 行
       孤儿配套（另有 60 行是编号书写不一致：少了末位的「号」、用了半角括号）。
       所以：
         · 默认（allowOrphan=false）孤儿算**错误**，整批不入库 —— 拦住它；
         · 勾上「允许挂到未登记宗地」后孤儿改记入**孤儿清单**并照常导入 ——
           宗地由另一个岗位维护，配套台账先到、宗地后录是真实存在的时序。
       ★ 无论哪种口径，孤儿清单都**单独渲染**、不与硬错误混在一起：
         它是「去找对应岗位催录宗地」的待办表，混进几百条错误里就没人看得出来了。

    ③ 跳过错误行
       与宗地导入一致：有错误时默认整批不入库，勾上才只导正确的行。

    ★ 文件选择用原生 input（1px 裁切、保留在可访问树里）而不是 ScreenUpload：
      ScreenUpload 会自己把文件 POST 到 action，拿到的是 jeecg 通用上传接口的
      存储路径；而批量导入要的是「把同一个 File 交给 preview / confirm / errorReport
      三个业务接口」，需要自己持有 File 对象。

    事件：
      ok  导入成功后抛出，面板据此刷新记录
  -->
  <screen-modal
    :visible.sync="visible"
    title="配套信息批量导入"
    :width="1080"
    :show-footer="false"
    :body-max-height="'calc(100vh - 160px)'"
    @cancel="handleClose"
  >
    <div class="facility-import">
      <!-- ================= 步骤条 ================= -->
      <ol class="facility-import__steps">
        <li
          v-for="(item, index) in steps"
          :key="item.title"
          class="facility-import__step"
          :class="{ 'is-active': step === index, 'is-done': step > index }"
        >
          <span class="facility-import__step-index">{{ index + 1 }}</span>
          <span class="facility-import__step-text">
            <b>{{ item.title }}</b>
            <em>{{ item.desc }}</em>
          </span>
        </li>
      </ol>

      <!-- ================= 一、模板与文件 ================= -->
      <section class="facility-import__block">
        <div class="facility-import__row">
          <screen-button icon="download" @click="handleDownloadTemplate">下载导入模板</screen-button>
          <span class="facility-import__hint">
            模板第 1 行是<b>英文字段名</b>（程序按它认列，例如 <code>crzdbh</code> / <code>ptxmmc</code>）、
            第 2 行是中文说明、数据从<b>第 3 行</b>开始；第 2 个 sheet 是逐字段的「填表说明」。
            必填 5 列：出让宗地编号、配套项目名称、录入单位、录入人、联系电话。
          </span>
        </div>

        <div class="facility-import__row">
          <input
            id="facility-import-file"
            ref="fileInput"
            class="facility-import__file"
            type="file"
            accept=".xlsx,.xls"
            aria-hidden="true"
            tabindex="-1"
            @change="handleFileChange"
          />
          <screen-button icon="upload" @click="handlePickFile">选择 Excel（.xlsx / .xls）</screen-button>
          <span v-if="fileName" class="facility-import__file-name" :title="fileName">
            <screen-icon name="file-text" :size="13" />
            {{ fileName }}
          </span>
          <span v-else class="facility-import__hint">尚未选择文件</span>

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
            按当前设置重新校验
          </screen-button>
        </div>
      </section>

      <!-- ================= 二、导入设置（★ 口径本身，改完要重新校验） ================= -->
      <section class="facility-import__block">
        <h4 class="facility-import__block-title">
          <screen-icon name="sliders" :size="14" />
          导入设置
          <span class="facility-import__block-sub">
            这两个开关会改变**校验口径**，改动后请点「按当前设置重新校验」
          </span>
        </h4>

        <div class="facility-import__row">
          <screen-field label="重复处理策略" label-width="130px" class="facility-import__strategy">
            <screen-select
              v-model="duplicateStrategy"
              :options="strategyOptions"
              aria-label="重复处理策略"
            />
          </screen-field>
          <span class="facility-import__hint">
            判重键是<b>宗地编号 + 配套项目名称</b>两列（同一块地上可以有多个配套）。
            「覆盖更新」按单元格有值才覆盖，<b>空单元格保持库内原值</b>。
          </span>
        </div>

        <div class="facility-import__row">
          <label class="facility-import__switch">
            <input
              class="facility-import__check"
              type="checkbox"
              :checked="allowOrphan"
              @change="handleAllowOrphanChange"
            />
            <span>
              允许挂到未登记宗地（记入<b>孤儿清单</b>）
            </span>
          </label>

          <label class="facility-import__switch" :class="{ 'is-disabled': !errorCount }">
            <input
              class="facility-import__check"
              type="checkbox"
              :checked="skipErrorRows"
              :disabled="!errorCount"
              @change="handleSkipChange"
            />
            <span>
              跳过错误行，只导入正确的行
              <template v-if="errorCount">（{{ importableCount }} 行）</template>
            </span>
          </label>
        </div>

        <p class="facility-import__hint is-block">
          默认**不允许**挂到未登记宗地：这些行会算作错误、整批不入库。
          如果这批配套对应的宗地确实还没录入（宗地由另一个岗位维护，
          配套台账先到是正常的），勾上上面的开关即可照常导入，但请先看一遍孤儿清单。
        </p>
      </section>

      <!-- ================= 三、结果 ================= -->
      <section v-if="result" class="facility-import__block">
        <div class="facility-import__stats">
          <span class="facility-import__stat">解析数据行 <b>{{ result.totalRows || 0 }}</b></span>
          <span class="facility-import__stat">校验通过 <b class="is-primary">{{ result.validRows || 0 }}</b></span>
          <span class="facility-import__stat">
            <template v-if="imported">已新增 <b class="is-ok">{{ result.insertedRows || 0 }}</b></template>
            <template v-else>将新增 <b class="is-primary">{{ result.willInsertRows || 0 }}</b></template>
          </span>
          <span class="facility-import__stat">
            <template v-if="imported">已覆盖 <b class="is-ok">{{ result.updatedRows || 0 }}</b></template>
            <template v-else>将覆盖 <b class="is-primary">{{ result.willUpdateRows || 0 }}</b></template>
          </span>
          <span v-if="result.skippedDuplicates" class="facility-import__stat">
            跳过重复 <b>{{ result.skippedDuplicates }}</b>
          </span>
          <span class="facility-import__stat">
            匹配到宗地 <b class="is-ok">{{ result.matchedLandRows || 0 }}</b>
          </span>
          <span class="facility-import__stat">
            错误 <b :class="errorCount ? 'is-error' : 'is-ok'">{{ errorCount }}</b> 条
          </span>
        </div>

        <p v-if="imported" class="facility-import__alert is-ok" role="status">
          <screen-icon name="check-circle" :size="14" />
          {{ summaryText }}
        </p>
        <p v-else-if="result.aborted" class="facility-import__alert is-warning" role="status">
          <screen-icon name="alert-triangle" :size="14" />
          本次不会写入任何数据（存在错误行）
        </p>
        <p v-else class="facility-import__alert is-info" role="status">
          <screen-icon name="info" :size="14" />
          校验通过：确认导入将新增 {{ result.willInsertRows || 0 }} 条、覆盖更新 {{ result.willUpdateRows || 0 }} 条
        </p>

        <p
          v-for="(item, index) in result.warnings || []"
          :key="'warn-' + index"
          class="facility-import__alert is-info"
        >
          <screen-icon name="info" :size="14" />
          {{ item }}
        </p>
        <p
          v-for="(item, index) in result.notices || []"
          :key="'notice-' + index"
          class="facility-import__alert is-warning"
        >
          <screen-icon name="alert-triangle" :size="14" />
          {{ item }}
        </p>

        <!-- ================= ★★ 孤儿清单（独立成块，绝不与错误混排） ================= -->
        <div v-if="orphanCount" class="facility-import__orphans">
          <div class="facility-import__block-head">
            <h4 class="facility-import__orphan-title">
              <screen-icon name="alert-triangle" :size="14" />
              孤儿清单：<b>{{ orphanCount }}</b> 行的出让宗地编号在系统里查不到
            </h4>
            <screen-tag :tone="allowOrphan ? 'warning' : 'danger'" size="sm">
              {{ allowOrphan ? '已允许导入' : '已阻断本次导入' }}
            </screen-tag>
          </div>

          <p class="facility-import__orphan-explain">
            <b>什么是「孤儿」</b>：这一行的出让宗地编号在宗地表里不存在，
            所以这条配套在系统里没有归属的地 —— 档案与收发文按宗地关联时，它永远挂不上。
            常见原因是<b>宗地还没录入</b>（宗地由另一个岗位维护）或<b>编号书写不一致</b>
            （少了末位的「号」字、用了半角括号）。这份清单可以直接当成一张待办表：
            按编号去找对应岗位催录宗地，或核对编号写法。
          </p>

          <!-- allowOrphan=false 时给出「怎么继续」的明确出路，而不是只报错 -->
          <p v-if="!allowOrphan" class="facility-import__alert is-warning" role="alert">
            <screen-icon name="alert-triangle" :size="14" />
            当前设置是「不允许挂到未登记宗地」，因此这 {{ orphanCount }} 行会作为错误**阻断整批导入**
            （除非勾选「跳过错误行」，那样它们会被跳过、其余行照常入库）。
            如果这些宗地确实还没录入，请勾上「允许挂到未登记宗地（记入孤儿清单）」再点「按当前设置重新校验」。
          </p>

          <screen-data-table
            :columns="orphanColumns"
            :data="pagedOrphans"
            row-key="key"
            :min-width="880"
            :animated="false"
          >
            <template #rowNum="{ row }">
              <span class="facility-import__row-num">第 {{ row.rowNum }} 行</span>
            </template>
            <template #crzdbh="{ row }">
              <span class="facility-import__mono">{{ row.crzdbh || '—' }}</span>
            </template>
            <template #ptxmmc="{ row }">
              <span class="facility-import__name" :title="row.ptxmmc || ''">{{ row.ptxmmc || '—' }}</span>
            </template>
            <template #reason="{ row }">
              <span class="facility-import__orphan-reason">{{ row.reason || '该宗地编号在系统中不存在' }}</span>
            </template>
          </screen-data-table>

          <div v-if="orphanPageCount > 1" class="facility-import__errors-foot">
            <screen-pagination
              :current="orphanPage"
              :page-size="orphanPageSize"
              :total="orphanCount"
              :page-size-options="[10, 20, 50]"
              @change="handleOrphanPageChange"
            />
          </div>
        </div>

        <!-- ================= 硬错误清单（按 Excel 行号聚合） ================= -->
        <div v-if="errorCount" class="facility-import__errors">
          <div class="facility-import__block-head">
            <h4 class="facility-import__errors-title">
              错误清单：共 {{ errorRows.length }} 行有问题、{{ errorCount }} 条原因
              <span class="facility-import__block-sub">与孤儿清单分开：这些是**必须改**的问题</span>
            </h4>
            <screen-button size="sm" icon="download" :loading="reporting" @click="handleDownloadReport">
              下载错误回执
            </screen-button>
          </div>

          <screen-data-table
            :columns="errorColumns"
            :data="pagedErrorRows"
            row-key="key"
            :min-width="880"
            :animated="false"
          >
            <template #rowNum="{ row }">
              <span class="facility-import__row-num">第 {{ row.rowNum }} 行</span>
            </template>
            <template #crzdbh="{ row }">
              <span class="facility-import__mono">{{ row.crzdbh || '—' }}</span>
            </template>
            <template #columnLabels="{ row }">
              <span class="facility-import__cols">
                <screen-tag v-for="item in row.columnLabels" :key="item" tone="warning" size="sm">
                  {{ item }}
                </screen-tag>
                <span v-if="!row.columnLabels.length" class="facility-import__mono">整行</span>
              </span>
            </template>
            <template #message="{ row }">
              <span class="facility-import__error-text">{{ row.message }}</span>
            </template>
          </screen-data-table>

          <div v-if="errorPageCount > 1" class="facility-import__errors-foot">
            <screen-pagination
              :current="errorPage"
              :page-size="errorPageSize"
              :total="errorRows.length"
              :page-size-options="[10, 20, 50]"
              @change="handleErrorPageChange"
            />
          </div>

          <p class="facility-import__errors-hint">
            回执里保留你填的原始内容，末尾追加 <code>__excel行号 / __错误字段 / __错误原因</code> 三列，
            <b>有孤儿时另附一个「孤儿清单」sheet</b>；改完删掉这三列即可当导入文件直接重传。
          </p>
        </div>

        <!-- ================= 确认入库 ================= -->
        <div v-if="!imported" class="facility-import__confirm">
          <span class="facility-import__confirm-hint">
            <template v-if="blockedByOrphan">
              孤儿行未被允许，导入被阻断 —— 请勾选「允许挂到未登记宗地」或「跳过错误行」。
            </template>
            <template v-else-if="!canImport">
              存在错误行，后端默认整批不入库 —— 请先修正，或勾选「跳过错误行」。
            </template>
            <template v-else>
              确认后将写入 {{ importableCount }} 行，其中孤儿 {{ allowOrphan ? orphanCount : 0 }} 行。
            </template>
          </span>

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

        <div v-else class="facility-import__confirm">
          <span class="facility-import__hint">导入完成，可关闭本窗口在「导入记录」里核对。</span>
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
  downloadFacilityTemplate,
  previewFacilityImport,
  confirmFacilityImport,
  downloadFacilityErrorReport,
  queryFacilityDuplicateStrategies,
  flattenOrphans,
  flattenErrors,
  importSummary,
  DUPLICATE_STRATEGIES_FALLBACK,
  DEFAULT_DUPLICATE_STRATEGY
} from '@/api/land/facilityImport'

/** 错误行按 Excel 行号聚合（同一行踩两个问题时合成一条，否则用户以为要改 10 行） */
function groupErrorsByRow (rows) {
  const map = {}
  rows.forEach((item) => {
    const row = item.rowNum
    if (!map[row]) {
      map[row] = {
        key: `row-${row}`,
        rowNum: row,
        crzdbh: item.crzdbh,
        columnLabels: [],
        message: item.message
      }
    }
    if (item.columnLabel && map[row].columnLabels.indexOf(item.columnLabel) < 0) {
      map[row].columnLabels.push(item.columnLabel)
    }
    if (map[row].crzdbh === null || map[row].crzdbh === undefined) {
      map[row].crzdbh = item.crzdbh
    }
    if (map[row].message !== item.message) {
      map[row].message += `；${item.message}`
    }
  })
  return Object.keys(map)
    .map((key) => map[key])
    .sort((a, b) => a.rowNum - b.rowNum)
}

export default {
  name: 'FacilityImportModal',
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
      /** 已经「按当前设置」校验过的文件（用于判断要不要显示「重新校验」） */
      loadedFile: null,
      previewing: false,
      importing: false,
      reporting: false,
      imported: false,
      /** ★ 这三个是「校验口径」，改动后必须重新预览才生效 */
      duplicateStrategy: DEFAULT_DUPLICATE_STRATEGY,
      allowOrphan: false,
      skipErrorRows: false,
      strategies: DUPLICATE_STRATEGIES_FALLBACK,
      result: null,
      errorPage: 1,
      errorPageSize: 10,
      orphanPage: 1,
      orphanPageSize: 10,
      steps: [
        { title: '下载模板并填写', desc: '第 1 行英文字段名 · 数据从第 3 行起' },
        { title: '预览校验', desc: '不写库，含孤儿清单' },
        { title: '确认导入', desc: '有错默认整批不入库' }
      ],
      orphanColumns: [
        { key: 'rowNum', title: 'Excel 行号', width: 110, type: 'slot' },
        { key: 'crzdbh', title: '出让宗地编号', width: 210, type: 'slot' },
        { key: 'ptxmmc', title: '配套项目名称', width: 240, type: 'slot' },
        { key: 'reason', title: '说明', type: 'slot' }
      ],
      errorColumns: [
        { key: 'rowNum', title: 'Excel 行号', width: 110, type: 'slot' },
        { key: 'crzdbh', title: '出让宗地编号', width: 190, type: 'slot' },
        { key: 'columnLabels', title: '出错字段', width: 210, type: 'slot' },
        { key: 'message', title: '错误原因', type: 'slot' }
      ]
    }
  },
  computed: {
    errorCount () {
      return this.result && this.result.errors ? this.result.errors.length : 0
    },
    /** ★ 孤儿数以后端的 orphanRows 为准；清单长度作为兜底 */
    orphanCount () {
      if (!this.result) return 0
      if (this.result.orphanRows !== undefined && this.result.orphanRows !== null) {
        return Number(this.result.orphanRows) || 0
      }
      return (this.result.orphans || []).length
    },
    orphanRows () {
      return flattenOrphans(this.result)
    },
    pagedOrphans () {
      const start = (this.orphanPage - 1) * this.orphanPageSize
      return this.orphanRows.slice(start, start + this.orphanPageSize)
    },
    orphanPageCount () {
      return Math.max(1, Math.ceil(this.orphanRows.length / this.orphanPageSize))
    },
    errorRows () {
      return groupErrorsByRow(flattenErrors(this.result))
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
    /** 孤儿未被允许 → 它们此刻是错误，导入被这一条卡住 */
    blockedByOrphan () {
      return this.orphanCount > 0 && !this.allowOrphan && !this.skipErrorRows
    },
    /**
     * 能否点「确认导入」。
     * ★ 两个独立条件：
     *   · 有硬错误 → 必须勾「跳过错误行」（后端默认整批不入库）；
     *   · 有孤儿且未允许 → 同上，因为此时孤儿也在 errors 里。
     */
    canImport () {
      if (!this.errorCount) return true
      return this.skipErrorRows
    },
    /** 本次会实际写入的行数（validRows 已剔除被跳过的行） */
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
    queryFacilityDuplicateStrategies()
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
      this.duplicateStrategy = DEFAULT_DUPLICATE_STRATEGY
      this.allowOrphan = false
      this.skipErrorRows = false
      this.result = null
      this.errorPage = 1
      this.orphanPage = 1
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
      downloadFacilityTemplate()
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
      this.orphanPage = 1
      this.step = 1
    },

    /**
     * 「允许挂到未登记宗地」开关。
     * ★ 它是**校验口径**：切换后必须重新预览才生效（后端 preview 也收这个参数）。
     *   所以这里明确提示「需要重新校验」，而不是悄悄在 confirm 时才带上 ——
     *   那会让用户按旧口径的预览结果去确认一批他从未看过的新数据。
     */
    handleAllowOrphanChange (event) {
      this.allowOrphan = !!(event && event.target && event.target.checked)
      this.skipErrorRows = false
      this.errorPage = 1
      this.orphanPage = 1
      if (this.loadedFile) {
        toast.info('已切换「允许挂到未登记宗地」，请点「按当前设置重新校验」以刷新校验结果')
      }
    },

    handleSkipChange (event) {
      this.skipErrorRows = !!(event && event.target && event.target.checked)
      this.errorPage = 1
    },

    handleErrorPageChange ({ current, pageSize }) {
      this.errorPage = current
      this.errorPageSize = pageSize
    },

    handleOrphanPageChange ({ current, pageSize }) {
      this.orphanPage = current
      this.orphanPageSize = pageSize
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
      previewFacilityImport(file, this.duplicateStrategy, this.allowOrphan)
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
          this.orphanPage = 1
          this.step = 1
          if (this.errorCount) {
            toast.warning(`校验完成：${this.errorRows.length} 行有问题（共 ${this.errorCount} 条），请先修正（或勾选「跳过错误行」）`)
          } else if (this.orphanCount && !this.allowOrphan) {
            toast.warning(`校验完成：有 ${this.orphanCount} 行找不到对应宗地（孤儿），已阻断导入，请查看孤儿清单`)
          } else {
            toast.success(`校验通过：将新增 ${this.result.willInsertRows || 0} 条、覆盖更新 ${this.result.willUpdateRows || 0} 条`)
          }
          if (rePreview) {
            toast.info('已按当前设置重新校验')
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
      confirmFacilityImport(this.file, {
        duplicateStrategy: this.duplicateStrategy,
        skipErrorRows: this.skipErrorRows,
        allowOrphan: this.allowOrphan
      })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '导入失败')
            return
          }
          this.result = res.result || {}
          this.errorPage = 1
          this.orphanPage = 1
          if ((this.result.insertedRows || 0) + (this.result.updatedRows || 0) > 0) {
            this.imported = true
            this.step = 2
            toast.success(this.summaryText)
            if (this.orphanCount) {
              // 孤儿入了库更要提醒：事后在库里发现一堆挂不上的配套最难查
              toast.warning(`本次有 ${this.orphanCount} 行是孤儿行（挂到了未登记宗地），请按孤儿清单跟进催录宗地`)
            }
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
      downloadFacilityErrorReport(file, {
        duplicateStrategy: this.duplicateStrategy,
        allowOrphan: this.allowOrphan
      })
        .then(() => {
          toast.success('错误回执已开始下载（含孤儿清单 sheet）')
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

.facility-import {
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

  &__block-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__block-sub {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__block-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
  }

  &__row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
  }

  &__strategy {
    flex: 0 0 330px;
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

    &.is-block {
      flex: 1 1 100%;
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

  /* ---------------- 开关 ---------------- */
  &__switch {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    cursor: pointer;

    b {
      color: var(--screen-accent);
      font-weight: 600;
    }

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
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
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

  /* ---------------- ★★ 孤儿清单 ---------------- */
  &__orphans {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-3);
    // 孤儿是「警告级」而不是「错误级」，用琥珀色把两者一眼分开
    background: rgba(245, 165, 36, 0.06);
    border: 1px dashed var(--screen-warning);
    border-radius: var(--screen-radius);
  }

  &__orphan-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-warning);

    b {
      font-family: var(--screen-font-number-family);
      font-size: var(--screen-font-lg);
    }
  }

  &__orphan-explain {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.8;
    color: var(--screen-text-sub);

    b {
      color: var(--screen-warning);
      font-weight: 600;
    }
  }

  &__orphan-reason {
    color: var(--screen-text-sub);
  }

  /* ---------------- 硬错误清单 ---------------- */
  &__errors {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding-top: var(--screen-space-2);
    border-top: 1px dashed var(--screen-border-soft);
  }

  &__errors-title {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-danger);
    flex-wrap: wrap;
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

  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
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

    b {
      color: var(--screen-warning);
    }

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

  &__confirm-hint {
    flex: 1 1 320px;
    min-width: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);
  }
}

@media (max-width: 1100px) {
  .facility-import__steps {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
