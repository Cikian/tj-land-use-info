<template>
  <!--
    CompletionDetailModal 竣工验收历史档案 · 详情
    ---------------------------------------------------------------
    三块内容：
      1. 历史项目与参建单位 —— 这份档案是哪个项目、谁建设谁施工、三个关键日期
      2. 数字化信息 —— 状态、加工单位与完成日期、DPI、页数/文件数、关联扫描件数
      3. 关联扫描件 —— 按「配套项目ID 优先、出让宗地编号兜底」反查档案（只读 t_archive），
         可把其中一条设为本档案的关联档案，也可取消关联

    ★ 扫描件本体不在本模块：「档案维护」负责上传与卷内文件管理，本模块只保存一个
      archive_id 指针（表里还有 archive_count 冗余计数）。因此这里的「关联」按钮
      只调 /land/archive/completion/linkArchive，绝不改档案表。

    公开方法：open(record)
    事件：edit(detail) 点「编辑」；changed 关联/取消关联成功后（父组件据此刷新列表与统计）
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1120"
    :show-footer="false"
    :body-max-height="'calc(100vh - 180px)'"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="completion-detail__tags">
        <screen-tag v-if="detail" :tone="digitizeTone(detail.digitizeStatus)" size="sm">
          {{ detail.digitizeStatus || '未数字化' }}
        </screen-tag>
        <screen-tag v-if="detail && detail.projectType" tone="info" size="sm">
          {{ detail.projectType }}
        </screen-tag>
        <screen-tag v-if="detail && detail.retention" tone="muted" size="sm">
          保管期限：{{ detail.retention }}
        </screen-tag>
        <screen-tag v-if="detail" tone="muted" size="sm">
          扫描件 {{ detail.archiveCount || 0 }} 份
        </screen-tag>
      </div>

      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>

      <screen-button
        size="sm"
        icon="link"
        :disabled="!detail"
        :loading="pickerLoading"
        @click="togglePicker"
      >
        关联档案
      </screen-button>

      <screen-button
        v-if="detail && detail.archiveId"
        size="sm"
        icon="x"
        @click="handleUnlink"
      >
        取消关联
      </screen-button>

      <screen-button size="sm" icon="reload" :loading="loading" @click="loadDetail">刷新</screen-button>
    </template>

    <div class="completion-detail">
      <!-- 标题行：档案编号 + 历史项目名称 -->
      <header class="completion-detail__head">
        <span class="completion-detail__no">{{ (detail && detail.archiveNo) || '—' }}</span>
        <h3 class="completion-detail__name">{{ (detail && detail.projectName) || '加载中…' }}</h3>
        <span v-if="detail && detail.projectCode" class="completion-detail__code">
          原编号 {{ detail.projectCode }}
        </span>
      </header>

      <screen-loading :loading="loading" text="正在加载历史档案详情…">
        <!-- ================= 1. 历史项目与参建单位 ================= -->
        <section class="completion-detail__section">
          <h4 class="completion-detail__section-title">历史项目与参建单位</h4>
          <screen-descriptions
            variant="flat"
            :items="projectItems"
            :columns="2"
            label-width="104px"
          />
        </section>

        <!-- ================= 2. 数字化信息 ================= -->
        <section class="completion-detail__section">
          <h4 class="completion-detail__section-title">
            数字化信息
            <span class="completion-detail__kicker">
              进度 {{ progressPercent }}%
            </span>
          </h4>
          <screen-descriptions
            variant="flat"
            :items="digitizeItems"
            :columns="2"
            label-width="104px"
          />
        </section>

        <!-- ================= 3. 关联扫描件 ================= -->
        <section class="completion-detail__section">
          <h4 class="completion-detail__section-title">
            关联扫描件
            <span class="completion-detail__kicker">
              共 {{ relatedArchives.length }} 份候选归档，已指定关联 {{ detail && detail.archiveId ? 1 : 0 }} 份
            </span>
          </h4>
          <p class="completion-detail__hint">
            扫描件本体存放在「档案维护」里，本模块只记录关联指针。下方列出与本项目
            （配套项目ID 优先、出让宗地编号兜底）相关的归档；
            <strong>输入关键词并搜索即可跨项目全局查找</strong>。
          </p>

          <!-- 挑档案 -->
          <div v-if="pickerVisible" class="completion-detail__picker">
            <div class="completion-detail__picker-bar">
              <screen-input
                id="cd-picker-keyword"
                v-model="pickerKeyword"
                clearable
                placeholder="按档案号 / 档案名称 / 配套项目名称搜索（留空则只看本项目）"
                @enter="searchCandidates"
              />
              <screen-button type="primary" icon="search" :loading="pickerLoading" @click="searchCandidates">
                搜索
              </screen-button>
              <screen-button icon="x" @click="pickerVisible = false">收起</screen-button>
            </div>

            <screen-data-table
              :columns="candidateColumns"
              :data="candidateRows"
              row-key="id"
              :loading="pickerLoading"
              :min-width="880"
              :animated="false"
              empty-text="没有找到可关联的档案"
            >
              <template #archiveStatus="{ row }">
                <screen-tag :tone="archiveStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
              </template>

              <template #candidateAction="{ row }">
                <button type="button" class="completion-detail__link" @click="handleLink(row)">关联为扫描件</button>
              </template>
            </screen-data-table>
          </div>

          <!-- 本项目相关归档 -->
          <screen-data-table
            v-if="relatedArchives.length"
            :columns="relatedColumns"
            :data="relatedArchives"
            row-key="id"
            :min-width="880"
            :animated="false"
          >
            <template #archiveStatus="{ row }">
              <screen-tag :tone="archiveStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
            </template>

            <template #fileTotal="{ row }">
              <span class="completion-detail__files">
                <em>{{ row.fileCount || 0 }}</em>
                <span class="completion-detail__files-size">{{ formatSize(row.totalSize) }}</span>
              </span>
            </template>

            <template #isLinked="{ row }">
              <screen-tag v-if="detail && row.id === detail.archiveId" tone="success" size="sm">本档案关联</screen-tag>
              <span v-else class="completion-detail__muted">—</span>
            </template>

            <template #relatedAction="{ row }">
              <button
                v-if="!detail || row.id !== detail.archiveId"
                type="button"
                class="completion-detail__link"
                @click="handleLink(row)"
              >
                设为本档案关联
              </button>
              <span v-else class="completion-detail__muted">已关联</span>
            </template>
          </screen-data-table>

          <screen-empty
            v-else
            size="sm"
            text="本项目下还没有归档记录"
            description="输入关键词全局搜索，或先到「档案维护」上传扫描件后再回来关联"
          />
        </section>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenLoading,
  ScreenDescriptions,
  ScreenDataTable,
  ScreenTag,
  ScreenEmpty,
  ScreenInput
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { formatSize, statusTone } from '../../constants'
import {
  queryCompletionById,
  pickArchives,
  linkCompletionArchive,
  unlinkCompletionArchive
} from '@/api/land/completion'
import { digitizePercent, digitizeTone, formatAmount, formatCount, formatDpi } from './constants'

/** 空值统一显示为「—」，避免把「没填」和「填了 0」看混 */
function textOr (value) {
  return value === null || value === undefined || value === '' ? '—' : value
}

export default {
  name: 'CompletionDetailModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenLoading,
    ScreenDescriptions,
    ScreenDataTable,
    ScreenTag,
    ScreenEmpty,
    ScreenInput
  },
  data () {
    return {
      visible: false,
      loading: false,
      detail: null,
      relatedArchives: [],
      pickerVisible: false,
      pickerLoading: false,
      pickerKeyword: '',
      candidateRows: [],
      candidateColumns: [
        { key: 'archiveNo', title: '档案号', width: 150 },
        { key: 'archiveName', title: '档案名称', width: 260, ellipsis: true },
        { key: 'archiveYear', title: '年度', width: 80, align: 'center' },
        { key: 'archiveDate', title: '归档日期', width: 106 },
        { key: 'fileCount', title: '卷内文件', width: 90, align: 'center' },
        { key: 'archiveStatus', title: '状态', width: 96, type: 'slot' },
        { key: 'candidateAction', title: '操作', width: 140, type: 'slot', align: 'center' }
      ],
      relatedColumns: [
        { key: 'archiveNo', title: '档案号', width: 150 },
        { key: 'archiveName', title: '档案名称', width: 240, ellipsis: true },
        { key: 'archiveYear', title: '年度', width: 80, align: 'center' },
        { key: 'archiveDate', title: '归档日期', width: 106 },
        { key: 'fileTotal', title: '卷内文件', width: 120, type: 'slot' },
        { key: 'archiveStatus', title: '状态', width: 96, type: 'slot' },
        { key: 'isLinked', title: '关联情况', width: 120, type: 'slot' },
        { key: 'relatedAction', title: '操作', width: 150, type: 'slot', align: 'center' }
      ]
    }
  },
  computed: {
    title () {
      return this.detail && this.detail.archiveNo
        ? `历史档案 ${this.detail.archiveNo}`
        : '历史档案详情'
    },
    /**
     * 数字化进度。
     * 优先用后端下发的 digitizePercent；若该字段缺失（非表字段，个别返回路径可能不带），
     * 按状态兜底算 0 / 50 / 100，避免一份已数字化的档案显示成 0%。
     */
    progressPercent () {
      const data = this.detail || {}
      if (data.digitizePercent !== null && data.digitizePercent !== undefined) {
        return Number(data.digitizePercent) || 0
      }
      return digitizePercent(data.digitizeStatus)
    },
    /** 历史项目与参建单位 */
    projectItems () {
      const data = this.detail || {}
      return [
        { key: 'projectName', label: '历史项目名称', value: textOr(data.projectName), stack: true },
        { key: 'projectCode', label: '项目原编号', value: textOr(data.projectCode) },
        { key: 'xzqh', label: '行政区划', value: textOr(data.xzqh) },
        { key: 'projectType', label: '项目类型', value: textOr(data.projectType) },
        { key: 'ptsslb', label: '配套设施类别', value: textOr(data.ptsslb) },
        { key: 'retention', label: '保管期限', value: textOr(data.retention) },
        { key: 'buildUnit', label: '建设单位', value: textOr(data.buildUnit) },
        { key: 'constructUnit', label: '施工单位', value: textOr(data.constructUnit) },
        { key: 'designUnit', label: '设计单位', value: textOr(data.designUnit) },
        { key: 'superviseUnit', label: '监理单位', value: textOr(data.superviseUnit) },
        { key: 'startDate', label: '开工日期', value: textOr(data.startDate) },
        { key: 'completeDate', label: '竣工日期', value: textOr(data.completeDate) },
        { key: 'acceptanceDate', label: '验收日期', value: textOr(data.acceptanceDate) },
        { key: 'investAmount', label: '投资额(万元)', value: formatAmount(data.investAmount) },
        { key: 'ptxmmc', label: '关联配套项目', value: textOr(data.ptxmmc) },
        { key: 'crzdbh', label: '出让宗地编号', value: textOr(data.crzdbh) },
        { key: 'dkmc', label: '地块名称', value: textOr(data.dkmc) },
        {
          key: 'updateTime',
          label: '最近更新',
          value: textOr(data.updateTime || data.createTime)
        },
        { key: 'remark', label: '备注', value: textOr(data.remark), stack: true }
      ]
    },
    /** 数字化信息 */
    digitizeItems () {
      const data = this.detail || {}
      return [
        { key: 'digitizeStatus', label: '数字化状态', value: data.digitizeStatus || '未数字化', tone: digitizeTone(data.digitizeStatus) },
        { key: 'digitizeDate', label: '完成日期', value: textOr(data.digitizeDate) },
        { key: 'digitizeOrg', label: '加工单位', value: textOr(data.digitizeOrg) },
        { key: 'scanDpi', label: '扫描分辨率', value: formatDpi(data.scanDpi) },
        { key: 'pageCount', label: '总页数', value: formatCount(data.pageCount) },
        { key: 'fileCount', label: '文件数', value: formatCount(data.fileCount) },
        { key: 'archiveCount', label: '关联扫描件数', value: `${data.archiveCount || 0} 份` },
        { key: 'archiveId', label: '关联档案ID', value: textOr(data.archiveId) }
      ]
    }
  },
  methods: {
    digitizeTone,
    archiveStatusTone: statusTone,
    formatSize,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少档案 ID，无法查看详情')
        return
      }
      this.visible = true
      this.pickerVisible = false
      this.pickerKeyword = ''
      this.candidateRows = []
      // 先用列表行的数据把标题与标签铺上，避免打开瞬间是空的
      this.detail = Object.assign({}, record)
      this.relatedArchives = record.relatedArchives || []
      this.loadDetail(record.id)
    },

    loadDetail (id) {
      const targetId = id || (this.detail && this.detail.id)
      if (!targetId) return
      this.loading = true
      queryCompletionById(targetId)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案详情加载失败')
            return
          }
          this.detail = res.result || {}
          this.relatedArchives = (this.detail.relatedArchives || []).slice()
        })
        .catch(() => {
          // 请求层已提示，保持当前内容即可
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 挑档案 ---------------- */

    togglePicker () {
      this.pickerVisible = !this.pickerVisible
      if (this.pickerVisible) {
        this.searchCandidates()
      }
    },

    /**
     * 拉候选档案。
     * 口径：有关键词时**全局搜**（不叠加项目/宗地条件，否则会一直在项目内打转）；
     * 没关键词时按「配套项目ID 优先、出让宗地编号兜底」列本项目相关归档。
     */
    searchCandidates () {
      const data = this.detail || {}
      const params = { limit: 50 }
      if (this.pickerKeyword) {
        params.keyword = this.pickerKeyword
      } else if (data.facilityId) {
        params.facilityId = data.facilityId
      } else if (data.crzdbh) {
        params.crzdbh = data.crzdbh
      }
      this.pickerLoading = true
      pickArchives(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案检索失败')
            return
          }
          this.candidateRows = res.result || []
        })
        .catch(() => {
          this.candidateRows = []
        })
        .finally(() => {
          this.pickerLoading = false
        })
    },

    handleLink (row) {
      if (!this.detail || !row || !row.id) return
      linkCompletionArchive(this.detail.id, row.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '关联失败')
            return
          }
          toast.success('已关联扫描件')
          this.pickerVisible = false
          this.loadDetail(this.detail.id)
          this.$emit('changed')
        })
        .catch(() => {})
    },

    handleUnlink () {
      if (!this.detail) return
      unlinkCompletionArchive(this.detail.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '取消关联失败')
            return
          }
          toast.success('已取消关联')
          this.loadDetail(this.detail.id)
          this.$emit('changed')
        })
        .catch(() => {})
    },

    /* ---------------- 其它 ---------------- */

    handleEdit () {
      if (this.detail) this.$emit('edit', this.detail)
    },

    handleClose () {
      this.visible = false
      this.pickerVisible = false
      this.detail = null
      this.relatedArchives = []
    },

    /** 供父组件关闭（例如点「编辑」后先把详情收起来，不要直接改子组件的 $data） */
    close () {
      this.handleClose()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.completion-detail {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  // ScreenLoading 的遮罩是 position: absolute，直接父级必须有定位（README 第 6 节第 9 条）
  position: relative;

  &__tags {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__head {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__no {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-md);
    color: var(--screen-accent);
  }

  &__name {
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__code {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__section-title {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);

    strong {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }
  }

  &__picker {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-2);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
  }

  &__picker-bar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
    }

    /deep/ .screen-btn {
      flex: 0 0 auto;
    }
  }

  &__link {
    .screen-link-action();
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  // 卷内文件列：数量 + 体积（DetailModal 两个数都要给，体积能反映扫描质量）
  &__files {
    display: inline-flex;
    align-items: baseline;
    gap: 5px;
  }

  &__files-size {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}

@media (max-width: 1200px) {
  .completion-detail__picker-bar {
    flex-wrap: wrap;
  }
}
</style>
