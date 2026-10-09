<template>
  <!--
    LedgerDetailModal 台账详情
    --------------------------------
    全屏弹窗（与档案详情同一版式语言），四块内容：
      1. 业务提示 —— 资料与状态矛盾、宗地待核对（有才显示，黄色描边条）
      2. 基本信息 —— ScreenDescriptions 扁平三列（左主栏）+ 右栏两组短信息
      3. 资料归集情况 —— 13 类逐项 √/×（点开源说明），并给出「待补资料」清单
      4. 关联档案 —— 只读列出同一配套项目 / 宗地下的档案，可搜索并设为关联档案

    ★ 附件口径：13 类资料的原件统一存放在档案管理模块，台账只记 archive_id 指针，
      所以这里不做上传，只做「挑一条档案挂上」。

    公开方法：
      open(record)  按列表行打开（内部再按 id 拉全量详情）
    事件：
      edit(detail)        点「编辑」时抛出，由面板接手打开编辑弹窗
      open-archive(row)   点档案号 / 「查看档案」时抛出，由面板（或页面接线）跳转到档案查询
      changed             关联关系变化时抛出，面板据此刷新列表（archive_count 变了）
  -->
  <screen-modal
    :visible.sync="visible"
    title="台账详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="ledger-detail__tags">
        <screen-tag :tone="statusTone(detail && detail.status)" size="sm">
          {{ (detail && detail.status) || '未验收' }}
        </screen-tag>
        <screen-tag :tone="materialTone(materialCount)" size="sm">
          资料 {{ materialCount }}/{{ materialTotal }}
        </screen-tag>
        <screen-tag v-if="detail && detail.acceptanceResult" :tone="acceptanceResultTone(detail.acceptanceResult)" size="sm">
          验收{{ detail.acceptanceResult }}
        </screen-tag>
        <screen-tag v-if="isMigrated(detail)" tone="muted" size="sm">旧系统迁移</screen-tag>
        <screen-tag v-if="isOrphan(detail)" tone="warning" size="sm">出让宗地待核对</screen-tag>
      </div>

      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>
    </template>

    <div class="ledger-detail">
      <!-- 标题行：台账编号 + 道路名称 -->
      <header class="ledger-detail__head">
        <span class="ledger-detail__no">{{ (detail && detail.ledgerNo) || '—' }}</span>
        <h3 class="ledger-detail__name">{{ (detail && detail.roadName) || '加载中…' }}</h3>
      </header>

      <screen-loading
        class="ledger-detail__content"
        :loading="loading"
        text="正在加载台账详情…"
        :overlay="false"
      >
        <!-- ================= 1. 业务提示 ================= -->
        <p v-if="warning" class="ledger-detail__warning" role="status">
          <screen-icon name="alert-triangle" :size="14" />
          <span>{{ warning }}</span>
        </p>

        <!-- ================= 2. 基本信息 ================= -->
        <div class="ledger-detail__two-col">
          <section class="ledger-detail__section ledger-detail__section--main">
            <h4 class="ledger-detail__section-title">
              道路与宗地信息
              <span class="ledger-detail__section-kicker">共 {{ baseItems.length }} 项</span>
            </h4>
            <screen-descriptions
              variant="flat"
              allow-wrap
              :items="baseItems"
              :columns="2"
              label-width="112px"
            />
          </section>

          <div class="ledger-detail__sidebar">
            <section class="ledger-detail__section">
              <h4 class="ledger-detail__section-title">
                验收信息
                <span class="ledger-detail__section-kicker">共 {{ acceptanceItems.length }} 项</span>
              </h4>
              <screen-descriptions
                variant="flat"
                allow-wrap
                :items="acceptanceItems"
                :columns="1"
                label-width="104px"
              />
            </section>

            <section class="ledger-detail__section">
              <h4 class="ledger-detail__section-title">
                移交与其他
                <span class="ledger-detail__section-kicker">共 {{ handoverItems.length }} 项</span>
              </h4>
              <screen-descriptions
                variant="flat"
                allow-wrap
                :items="handoverItems"
                :columns="1"
                label-width="104px"
              />
            </section>
          </div>
        </div>

        <!-- ================= 3. 资料归集情况 ================= -->
        <section class="ledger-detail__section">
          <h4 class="ledger-detail__section-title">
            资料归集情况
            <span class="ledger-detail__section-kicker">
              已归集 {{ materialCount }}/{{ materialTotal }} 类
            </span>
          </h4>

          <div class="ledger-detail__materials">
            <div
              v-for="item in materialList"
              :key="item.key"
              class="ledger-detail__material"
              :class="{ 'is-on': isCollected(item.key) }"
              :title="item.source || item.label"
            >
              <span class="ledger-detail__material-seq">{{ padSeq(item.seq) }}</span>
              <span class="ledger-detail__material-label">{{ item.label }}</span>
              <screen-tag :tone="isCollected(item.key) ? 'success' : 'muted'" size="sm">
                {{ isCollected(item.key) ? '√ 已归集' : '× 未归集' }}
              </screen-tag>
            </div>
          </div>

          <p class="ledger-detail__pending">
            <template v-if="pendingLabels.length">
              待补资料（{{ pendingLabels.length }} 类）：{{ pendingLabels.join('、') }}
            </template>
            <template v-else>13 类资料已全部归集。</template>
          </p>
        </section>

        <!-- ================= 4. 关联档案 ================= -->
        <section class="ledger-detail__section">
          <h4 class="ledger-detail__section-title">
            关联档案
            <span class="ledger-detail__section-kicker">
              共 {{ archives.length }} 个 · 协议与验收原件存放在档案管理模块
            </span>
          </h4>

          <div v-if="detail && detail.archiveId" class="ledger-detail__linked">
            <screen-icon name="paperclip" :size="14" />
            <span>本台账已关联档案：{{ linkedArchiveText }}</span>
            <button type="button" class="ledger-detail__link is-danger" @click="handleUnlink">取消关联</button>
          </div>

          <screen-data-table
            :columns="archiveColumns"
            :data="archives"
            row-key="id"
            :min-width="1020"
            :max-height="260"
            :animated="false"
            empty-text="该配套项目 / 宗地下暂无档案；资料原件请先到「档案维护」上传"
          >
            <template #archiveNo="{ row }">
              <button type="button" class="ledger-detail__link" @click="$emit('open-archive', row)">
                {{ row.archiveNo || '—' }}
              </button>
            </template>

            <template #archiveName="{ row }">
              <span class="ledger-detail__ellipsis" :title="row.archiveName || ''">
                {{ row.archiveName || '—' }}
              </span>
            </template>

            <template #archiveStatus="{ row }">
              <screen-tag :tone="archiveStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
            </template>

            <template #isPrimary="{ row }">
              <screen-tag v-if="row.id === (detail && detail.archiveId)" tone="success" size="sm">已关联</screen-tag>
              <span v-else class="ledger-detail__muted">—</span>
            </template>

            <template #archiveAction="{ row }">
              <button
                type="button"
                class="ledger-detail__link"
                :disabled="linking"
                @click="handleLink(row)"
              >
                {{ row.id === (detail && detail.archiveId) ? '重新关联' : '设为本台账关联档案' }}
              </button>
            </template>
          </screen-data-table>

          <!-- 挑档案：按关键词在全部档案里再搜一遍（默认只看同项目/同宗地） -->
          <div class="ledger-detail__picker">
            <screen-field label="查找档案" label-width="76px" html-for="ld-archive-keyword">
              <div class="ledger-detail__picker-row">
                <screen-input
                  id="ld-archive-keyword"
                  v-model="keyword"
                  clearable
                  placeholder="按档案号 / 档案名称 / 项目名称搜索"
                  @enter="loadPicker"
                />
                <screen-button size="sm" icon="search" :loading="pickerLoading" @click="loadPicker">
                  搜索
                </screen-button>
                <screen-button size="sm" icon="reload" @click="resetPicker">只看同项目</screen-button>
              </div>
            </screen-field>

            <screen-data-table
              :columns="pickerColumns"
              :data="pickerOptions"
              row-key="id"
              :min-width="860"
              :max-height="220"
              :animated="false"
              :loading="pickerLoading"
              loading-text="正在搜索档案…"
              empty-text="没有找到可关联的档案（可清空关键词后重试）"
            >
              <template #pickName="{ row }">
                <span class="ledger-detail__ellipsis" :title="row.archiveName || ''">
                  {{ row.archiveName || '—' }}
                </span>
              </template>

              <template #pickAction="{ row }">
                <button
                  type="button"
                  class="ledger-detail__link"
                  :disabled="linking"
                  @click="handleLink(row)"
                >
                  关联
                </button>
              </template>
            </screen-data-table>
          </div>
        </section>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenButton,
  ScreenTag,
  ScreenIcon,
  ScreenLoading,
  ScreenDescriptions,
  ScreenDataTable,
  ScreenField,
  ScreenInput
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  queryLedgerById,
  queryRelatedArchives,
  pickArchives,
  linkLedgerArchive,
  unlinkLedgerArchive
} from '@/api/land/ledger'
import {
  LEDGER_MATERIALS_FALLBACK,
  MATERIAL_TOTAL,
  archiveStatusTone,
  acceptanceResultTone,
  isMigrated,
  isOrphan,
  materialTone,
  missingMaterialLabels,
  padSeq,
  statusMaterialWarning,
  statusTone
} from './constants'

/** 关联档案表列（档案号 / 名称 / 年度 / 归档日期 / 文件数 / 状态 / 关联标记 / 操作） */
const ARCHIVE_COLUMNS = [
  { key: 'archiveNo', title: '档案号', width: 140, type: 'slot' },
  { key: 'archiveName', title: '档案名称', width: 240, type: 'slot' },
  { key: 'archiveYear', title: '档案年度', width: 96 },
  { key: 'archiveDate', title: '归档日期', width: 108 },
  { key: 'fileCount', title: '文件数', width: 84, align: 'center' },
  { key: 'status', title: '档案状态', width: 96, type: 'slot' },
  { key: 'isPrimary', title: '本台账关联', width: 110, type: 'slot', align: 'center' },
  { key: 'archiveAction', title: '操作', width: 180, type: 'slot' }
]

/** 挑档案表列（少一列「本台账关联」，避免与上面的列表重复表达） */
const PICKER_COLUMNS = [
  { key: 'archiveNo', title: '档案号', width: 140 },
  { key: 'pickName', title: '档案名称', width: 260, type: 'slot' },
  { key: 'ptxmmc', title: '配套项目', width: 220, ellipsis: true },
  { key: 'archiveYear', title: '档案年度', width: 96 },
  { key: 'fileCount', title: '文件数', width: 84, align: 'center' },
  { key: 'pickAction', title: '操作', width: 100, type: 'slot', align: 'center' }
]

export default {
  name: 'LedgerDetailModal',
  components: {
    ScreenModal,
    ScreenButton,
    ScreenTag,
    ScreenIcon,
    ScreenLoading,
    ScreenDescriptions,
    ScreenDataTable,
    ScreenField,
    ScreenInput
  },
  props: {
    /** 13 类资料定义（面板已拉过就传进来） */
    materials: { type: Array, default: () => [] }
  },
  data () {
    return {
      visible: false,
      loading: false,
      linking: false,
      detail: null,
      archives: [],
      keyword: '',
      pickerLoading: false,
      pickerOptions: [],
      localMaterials: LEDGER_MATERIALS_FALLBACK.slice(),
      archiveColumns: ARCHIVE_COLUMNS,
      pickerColumns: PICKER_COLUMNS
    }
  },
  computed: {
    materialList () {
      return this.materials && this.materials.length ? this.materials : this.localMaterials
    },
    materialTotal () {
      return this.materialList.length || MATERIAL_TOTAL
    },
    /** 已归集类数：detail.materialCount 优先（后端算好的） */
    materialCount () {
      if (!this.detail) return 0
      if (this.detail.materialCount !== null && this.detail.materialCount !== undefined) {
        return Number(this.detail.materialCount)
      }
      return this.materialList.reduce((sum, item) => sum + (this.isCollected(item.key) ? 1 : 0), 0)
    },
    warning () {
      return statusMaterialWarning(this.detail, this.materialList)
    },
    pendingLabels () {
      return missingMaterialLabels(this.detail, this.materialList)
    },
    baseItems () {
      const data = this.detail || {}
      return [
        { label: '台账编号', value: data.ledgerNo || '—' },
        { label: '道路名称', value: data.roadName || '—' },
        { label: '道路等级', value: data.dldj || '—' },
        { label: '行政区划', value: data.xzqh || '—' },
        { label: '功能区', value: data.gnq || '—' },
        { label: '配套设施类别', value: data.ptsslb || '—' },
        { label: '出让宗地编号', value: data.crzdbh || '—' },
        { label: '地块名称', value: data.dkmc || '—' },
        { label: '配套项目名称', value: data.ptxmmc || '—' },
        { label: '移交单位', value: data.handoverUnit || '—' },
        { label: '接收管养单位', value: data.receiveUnit || '—' },
        { label: '移交日期', value: data.handoverDate || '—' }
      ]
    },
    acceptanceItems () {
      const data = this.detail || {}
      return [
        { label: '状态', value: data.status || '未验收' },
        { label: '验收类型', value: data.acceptanceType || '—' },
        { label: '验收结果', value: data.acceptanceResult || '—' },
        { label: '验收日期', value: data.acceptanceDate || '—' },
        { label: '验收单编号', value: data.acceptanceNo || '—' },
        { label: '验收组织单位', value: data.acceptanceOrg || '—' },
        { label: '实际竣工日期', value: data.completeDate || '—' }
      ]
    },
    handoverItems () {
      const data = this.detail || {}
      return [
        { label: '关联档案数', value: data.archiveCount === null || data.archiveCount === undefined ? 0 : data.archiveCount },
        { label: '数据来源', value: isMigrated(data) ? '旧系统迁移' : '人工录入' },
        { label: '备注', value: data.remark || '—' },
        { label: '创建人', value: data.createBy || '—' },
        { label: '创建时间', value: data.createTime || '—' },
        { label: '更新时间', value: data.updateTime || '—' }
      ]
    },
    /** 已关联档案的可读描述（详情里只给档案号+名称，避免用户还要去翻列表） */
    linkedArchiveText () {
      const id = this.detail && this.detail.archiveId
      if (!id) return '—'
      const hit = this.archives.filter((item) => item.id === id)[0]
      if (!hit) return id
      return `${hit.archiveNo || id}${hit.archiveName ? ' · ' + hit.archiveName : ''}`
    }
  },
  methods: {
    statusTone,
    materialTone,
    acceptanceResultTone,
    archiveStatusTone,
    isMigrated,
    isOrphan,
    padSeq,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少台账 ID，无法查看详情')
        return
      }
      this.visible = true
      this.keyword = ''
      this.pickerOptions = []
      this.detail = Object.assign({}, record)
      this.archives = []
      this.loadDetail(record.id)
    },

    handleClose () {
      this.visible = false
    },

    loadDetail (id) {
      this.loading = true
      return queryLedgerById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '台账详情加载失败')
            return
          }
          this.detail = res.result || {}
          return this.loadArchives(id)
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 同一配套项目 / 宗地下的档案（只读；后端按 facility_id 优先、crzdbh 兜底） */
    loadArchives (id) {
      return queryRelatedArchives(id, 50)
        .then((res) => {
          this.archives = res && res.success ? res.result || [] : []
        })
        .catch(() => {
          this.archives = []
        })
    },

    /* ---------------- 关联档案 ---------------- */

    isCollected (key) {
      return !!(this.detail && Number(this.detail[key]) === 1)
    },

    /** 按关键词在全部档案里搜；关键词为空时退化为「同配套项目 / 同宗地」 */
    loadPicker () {
      const params = { limit: 30 }
      if (this.keyword) {
        params.keyword = this.keyword
      } else if (this.detail) {
        if (this.detail.facilityId) params.facilityId = this.detail.facilityId
        else if (this.detail.crzdbh) params.crzdbh = this.detail.crzdbh
      }
      this.pickerLoading = true
      return pickArchives(params)
        .then((res) => {
          this.pickerOptions = res && res.success ? res.result || [] : []
        })
        .catch(() => {
          this.pickerOptions = []
        })
        .finally(() => {
          this.pickerLoading = false
        })
    },

    resetPicker () {
      this.keyword = ''
      return this.loadPicker()
    },

    handleLink (row) {
      if (!this.detail || !row || !row.id) return
      this.linking = true
      linkLedgerArchive(this.detail.id, row.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '关联档案失败')
            return
          }
          toast.success('已设为本台账的关联档案')
          // 重新拉详情：archive_id 与 archive_count 都由服务端维护，本地不改
          this.loadDetail(this.detail.id)
          this.$emit('changed')
        })
        .catch(() => {})
        .finally(() => {
          this.linking = false
        })
    },

    handleUnlink () {
      if (!this.detail) return
      this.linking = true
      unlinkLedgerArchive(this.detail.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '取消关联失败')
            return
          }
          toast.success('已取消关联（档案本身不会被删除）')
          this.loadDetail(this.detail.id)
          this.$emit('changed')
        })
        .catch(() => {})
        .finally(() => {
          this.linking = false
        })
    },

    handleEdit () {
      if (!this.detail) return
      this.$emit('edit', this.detail)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-detail {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__tags {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin-right: var(--screen-space-2);
  }

  &__head {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-bottom: var(--screen-space-3);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__no {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-sm);
    letter-spacing: 1px;
    color: var(--screen-accent);
  }

  &__name {
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  // ScreenLoading 的根节点不是弹性子项，必须显式撑开，
  // 否则里面的表格拿不到确定高度而塌陷（README 第 6 节第 9 条）
  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    flex: 1 1 auto;
    min-height: 0;
  }

  &__warning {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    color: var(--screen-warning);
    background: rgba(245, 165, 36, 0.08);
    border: 1px solid var(--screen-warning);
    border-radius: var(--screen-radius-sm);
  }

  /* 左主栏 + 右侧栏：窄容器自动降为单栏 */
  &__two-col {
    display: grid;
    grid-template-columns: 1.5fr 1fr;
    gap: var(--screen-space-4);
    align-items: start;
  }

  &__sidebar {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    min-width: 0;
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__section--main {
    min-width: 0;
  }

  &__section-title {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin: 0;
    padding-left: 8px;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);
    border-left: 3px solid var(--screen-accent);
  }

  &__section-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* ---------------- 13 类资料 ---------------- */
  &__materials {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-2) var(--screen-space-3);
  }

  &__material {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    background: var(--screen-elevate);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);

    // 已归集：描边强调；同时右侧标签写的是「√ 已归集」，不靠颜色单独表意
    &.is-on {
      color: var(--screen-text);
      border-color: var(--screen-accent);
    }

    /deep/ .screen-tag {
      margin-left: auto;
      flex: 0 0 auto;
    }
  }

  &__material-seq {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__material-label {
    min-width: 0;
    .screen-ellipsis();
  }

  &__pending {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-sub);
  }

  /* ---------------- 关联档案 ---------------- */
  &__linked {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: var(--screen-space-2);
    font-size: var(--screen-font-sm);
    color: var(--screen-accent-soft);
  }

  &__picker {
    margin-top: var(--screen-space-3);
    padding-top: var(--screen-space-3);
    border-top: 1px dashed var(--screen-border-soft);
  }

  &__picker-row {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
    }
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  &__link {
    .screen-link-action();

    &:disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        text-decoration: underline;
      }
    }
  }
}

@media (max-width: 1500px) {
  .ledger-detail__two-col {
    grid-template-columns: minmax(0, 1fr);
  }

  .ledger-detail__materials {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .ledger-detail__materials {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
