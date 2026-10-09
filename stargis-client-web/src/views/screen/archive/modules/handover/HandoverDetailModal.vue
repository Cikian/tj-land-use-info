<template>
  <!--
    HandoverDetailModal 道路交付及养护协议移交事项 · 详情
    --------------------------------
    用全屏弹窗而不是抽屉：详情里有一组描述 + 两张表（关联档案 / 可挑选的档案），
    抽屉宽度不够会到处折行。

    版式（自上而下）：
      标题行（移交编号 + 道路名称）
      状态切换条（3 值分段单选，切换即保存）★ 与列表「状态」列的同一个动作
      业务提示（协议待补录 / 养护期到期 / 出让宗地待核对，黄色，非阻断）
      三组描述（道路信息 / 协议与移交 / 其他信息）
      关联档案（可搜、可设为关联、可取消关联）

    公开方法：
      open(record)  按列表行打开详情（内部再按 id 拉全量数据 + 关联档案）
    事件：
      edit(detail)          点「编辑」时抛出，由面板接手打开编辑弹窗
      status-change(row,s)  状态变更成功后抛出，面板据此刷新列表与统计
  -->
  <screen-modal
    :visible.sync="visible"
    title="移交事项详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="handover-detail__tags">
        <screen-tag v-if="detail" :tone="statusTone(detail.status)" size="sm">
          {{ detail.status || '待移交' }}
        </screen-tag>
        <screen-tag v-if="detail && detail.handoverType" tone="muted" size="sm">
          {{ detail.handoverType }}
        </screen-tag>
        <screen-tag v-if="detail && detail.archiveId" tone="success" size="sm">已关联档案</screen-tag>
        <screen-tag v-if="detail && detail.xzqh" tone="info" size="sm">{{ detail.xzqh }}</screen-tag>
        <screen-tag v-else-if="detail && detail.gnq" tone="info" size="sm">{{ detail.gnq }}</screen-tag>
        <screen-tag v-if="detail" tone="muted" size="sm">{{ archives.length }} 个可关联档案</screen-tag>
      </div>

      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>
      <screen-button size="sm" icon="reload" :disabled="!detail" :loading="loading" @click="reload">
        刷新
      </screen-button>
    </template>

    <div class="handover-detail">
      <!-- 标题行 -->
      <header class="handover-detail__head">
        <span class="handover-detail__no">{{ (detail && detail.handoverNo) || '—' }}</span>
        <h3 class="handover-detail__name">{{ (detail && detail.roadName) || '加载中…' }}</h3>
      </header>

      <screen-loading
        class="handover-detail__content"
        :loading="loading"
        text="正在加载移交事项详情…"
        :overlay="false"
      >
        <!-- ================= 状态切换条 ================= -->
        <!-- 与列表「状态」列是同一个动作（都打 /land/archive/handover/status），
             这里用 3 值分段单选，因为弹窗里横向空间足够、也不需要挤在表格单元格里 -->
        <section class="handover-detail__status-bar">
          <span class="handover-detail__status-label">
            <screen-icon name="check-circle" :size="13" />
            移交状态
          </span>
          <screen-radio-group
            v-model="statusValue"
            :options="statusOptions"
            aria-label="移交状态"
            @change="handleStatusChange"
          />
          <span class="handover-detail__status-hint">
            {{ statusSaving ? '正在保存…' : '切换后立即保存' }}
          </span>
        </section>

        <!-- ================= 业务提示 ================= -->
        <div v-if="warnings.length" class="handover-detail__warnings">
          <p v-for="(item, index) in warnings" :key="index" class="handover-detail__warning">
            <screen-icon name="alert-triangle" :size="13" />
            <span>{{ item }}</span>
          </p>
        </div>

        <!-- ================= 三组描述 ================= -->
        <div class="handover-detail__grid">
          <section class="handover-detail__section">
            <h4 class="handover-detail__section-title">
              道路信息
              <span class="handover-detail__section-kicker">共 {{ roadItems.length }} 项</span>
            </h4>
            <screen-descriptions
              variant="flat"
              allow-wrap
              :items="roadItems"
              :columns="2"
              label-width="104px"
            />
          </section>

          <section class="handover-detail__section">
            <h4 class="handover-detail__section-title">
              协议与移交
              <span class="handover-detail__section-kicker">共 {{ protocolItems.length }} 项</span>
            </h4>
            <screen-descriptions
              variant="flat"
              allow-wrap
              :items="protocolItems"
              :columns="2"
              label-width="104px"
            />
          </section>

          <section class="handover-detail__section">
            <h4 class="handover-detail__section-title">
              其他信息
              <span class="handover-detail__section-kicker">共 {{ otherItems.length }} 项</span>
            </h4>
            <screen-descriptions
              variant="flat"
              allow-wrap
              :items="otherItems"
              :columns="2"
              label-width="104px"
            />
          </section>
        </div>

        <!-- ================= 关联档案 ================= -->
        <section class="handover-detail__section handover-detail__section--archives">
          <h4 class="handover-detail__section-title">
            关联档案
            <span class="handover-detail__section-kicker">
              协议扫描件与移交单统一存放于「档案维护」，本事项只记录关联
            </span>
          </h4>

          <div class="handover-detail__picker">
            <screen-input
              v-model="archiveKeyword"
              clearable
              placeholder="按档案号 / 档案名称 / 配套项目搜索"
              icon="search"
              @enter="loadArchives"
            />
            <screen-button icon="search" :loading="archiveLoading" @click="loadArchives">搜索</screen-button>
            <screen-button v-if="detail && detail.archiveId" icon="close" @click="handleUnlink">
              取消关联
            </screen-button>
          </div>
          <p class="handover-detail__picker-hint">
            不填关键词时列出同一配套项目 / 出让宗地下的档案；填关键词则在全部档案里搜。
          </p>

          <screen-data-table
            :columns="archiveColumns"
            :data="archives"
            :loading="archiveLoading"
            row-key="id"
            :min-width="1080"
            :empty-text="archiveKeyword ? '没有找到匹配的档案' : '该项目下暂无档案；请先到「档案维护」上传，再回来关联'"
          >
            <template #archiveName="{ row }">
              <span class="handover-detail__archive-name" :title="row.archiveName || ''">
                {{ row.archiveName || '—' }}
              </span>
            </template>

            <template #archiveYear="{ row }">
              {{ numberOrDash(row.archiveYear) }}
            </template>

            <template #fileCount="{ row }">
              {{ numberOrDash(row.fileCount) }}
            </template>

            <template #linked="{ row }">
              <screen-tag v-if="detail && row.id === detail.archiveId" tone="success" size="sm">已关联</screen-tag>
              <span v-else class="handover-detail__mute">—</span>
            </template>

            <template #action="{ row }">
              <button
                type="button"
                class="handover-detail__link"
                :disabled="!!(detail && row.id === detail.archiveId)"
                @click="handleLink(row)"
              >
                设为本事项关联档案
              </button>
            </template>
          </screen-data-table>
        </section>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenTag,
  ScreenButton,
  ScreenIcon,
  ScreenLoading,
  ScreenRadioGroup,
  ScreenDescriptions,
  ScreenDataTable,
  ScreenInput
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  changeHandoverStatus,
  linkHandoverArchive,
  pickHandoverArchives,
  queryHandoverArchives,
  queryHandoverById,
  unlinkHandoverArchive
} from '@/api/land/handover'
import {
  MISSING_AGREEMENT_TEXT,
  ORPHAN_LAND_TEXT,
  handoverStatusOptions,
  maintenanceWarning,
  missingAgreement,
  numberOrDash,
  orphanLand,
  statusTone,
  textOrDash
} from './constants'

export default {
  name: 'HandoverDetailModal',
  components: {
    ScreenModal,
    ScreenTag,
    ScreenButton,
    ScreenIcon,
    ScreenLoading,
    ScreenRadioGroup,
    ScreenDescriptions,
    ScreenDataTable,
    ScreenInput
  },
  data () {
    return {
      visible: false,
      loading: false,
      statusSaving: false,
      detail: null,
      statusValue: '',
      /** 关联档案候选（不填关键词 = 本项目/宗地下的档案；填了 = 全局搜索） */
      archives: [],
      archiveKeyword: '',
      archiveLoading: false,
      statusOptions: handoverStatusOptions(),
      archiveColumns: [
        { key: 'archiveNo', title: '档案号', width: 150 },
        { key: 'archiveName', title: '档案名称', width: 260, type: 'slot' },
        { key: 'xzqh', title: '行政区划', width: 92 },
        { key: 'archiveYear', title: '年度', width: 84, type: 'slot' },
        { key: 'archiveDate', title: '归档日期', width: 110 },
        { key: 'fileCount', title: '文件数', width: 84, type: 'slot' },
        { key: 'linked', title: '本事项关联', width: 110, type: 'slot' },
        { key: 'action', title: '操作', width: 190, type: 'slot' }
      ]
    }
  },
  computed: {
    /** 业务提示（可能同时命中多条） */
    warnings () {
      const list = []
      const data = this.detail
      if (!data) return list
      if (missingAgreement(data)) {
        list.push(`${MISSING_AGREEMENT_TEXT}：协议编号与协议签订日期都为空（迁移自旧库「是否移交=是」，旧库没有协议字段）`)
      }
      const maintenance = maintenanceWarning(data)
      if (maintenance) list.push(maintenance)
      if (orphanLand(data)) {
        list.push(`${ORPHAN_LAND_TEXT}：该出让宗地编号在 t_land 中无匹配记录`)
      }
      return list
    },

    roadItems () {
      const data = this.detail || {}
      return [
        { key: 'roadName', label: '道路名称', value: textOrDash(data.roadName), stack: true },
        { key: 'roadCode', label: '道路编号', value: textOrDash(data.roadCode) },
        { key: 'dldj', label: '道路等级', value: textOrDash(data.dldj) },
        { key: 'ptsslb', label: '配套设施类别', value: textOrDash(data.ptsslb) },
        { key: 'startPoint', label: '起点', value: textOrDash(data.startPoint) },
        { key: 'endPoint', label: '终点', value: textOrDash(data.endPoint) },
        { key: 'lengthM', label: '长度(米)', value: numberOrDash(data.lengthM), tone: 'number' },
        { key: 'redLineWidth', label: '红线宽度(米)', value: numberOrDash(data.redLineWidth), tone: 'number' },
        { key: 'xzqh', label: '行政区划', value: textOrDash(data.xzqh) },
        { key: 'gnq', label: '功能区', value: textOrDash(data.gnq) },
        { key: 'crzdbh', label: '出让宗地编号', value: textOrDash(data.crzdbh) },
        { key: 'dkmc', label: '地块名称', value: textOrDash(data.dkmc) }
      ]
    },

    protocolItems () {
      const data = this.detail || {}
      return [
        { key: 'handoverType', label: '移交类型', value: textOrDash(data.handoverType) },
        { key: 'agreementNo', label: '协议编号', value: textOrDash(data.agreementNo) },
        { key: 'agreementDate', label: '协议签订日期', value: textOrDash(data.agreementDate), tone: 'number' },
        { key: 'agreementName', label: '协议名称', value: textOrDash(data.agreementName), stack: true },
        { key: 'buildUnit', label: '建设单位', value: textOrDash(data.buildUnit) },
        { key: 'receiveUnit', label: '接收管养单位', value: textOrDash(data.receiveUnit) },
        { key: 'handoverDate', label: '实际移交日期', value: textOrDash(data.handoverDate), tone: 'number' },
        { key: 'maintenanceStart', label: '养护起始日期', value: textOrDash(data.maintenanceStart), tone: 'number' },
        { key: 'maintenanceEnd', label: '养护截止日期', value: textOrDash(data.maintenanceEnd), tone: 'number' }
      ]
    },

    otherItems () {
      const data = this.detail || {}
      return [
        { key: 'handoverNo', label: '移交编号', value: textOrDash(data.handoverNo), tone: 'accent' },
        { key: 'status', label: '移交状态', value: textOrDash(data.status) },
        { key: 'archiveCount', label: '可关联档案', value: `${numberOrDash(data.archiveCount)} 个`, tone: 'number' },
        { key: 'source', label: '数据来源', value: data.sourceFacilityId ? '迁移生成' : '页面录入' },
        { key: 'remark', label: '备注', value: textOrDash(data.remark), stack: true },
        { key: 'createTime', label: '创建信息', value: this.joinInfo(data.createTime, data.createBy) },
        { key: 'updateTime', label: '最后更新', value: this.joinInfo(data.updateTime, data.updateBy) }
      ]
    }
  },
  methods: {
    statusTone,
    numberOrDash,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少移交事项 ID，无法打开详情')
        return
      }
      this.visible = true
      this.archiveKeyword = ''
      // 先用列表行垫一下，避免标题行闪空；loadDetail 回来会整条覆盖
      this.detail = Object.assign({}, record)
      this.statusValue = record.status || '待移交'
      this.loadDetail(record.id)
      this.loadArchives()
    },

    reload () {
      if (!this.detail || !this.detail.id) return
      this.loadDetail(this.detail.id)
      this.loadArchives()
    },

    loadDetail (id) {
      this.loading = true
      queryHandoverById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '移交事项详情加载失败')
            return
          }
          this.detail = res.result || null
          this.statusValue = (this.detail && this.detail.status) || '待移交'
        })
        .catch(() => {
          // 请求层已提示，这里不再重复弹
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 状态变更 ---------------- */

    handleStatusChange (status) {
      const target = typeof status === 'string' ? status : this.statusValue
      const current = this.detail && this.detail.status
      if (!target || !this.detail || target === current) return

      this.statusSaving = true
      changeHandoverStatus(this.detail.id, target)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '状态变更失败')
            // 失败要把单选退回原值，否则界面显示的和库里不一致
            this.statusValue = current || '待移交'
            return
          }
          toast.success(`状态已变更为「${target}」`)
          const changed = Object.assign({}, this.detail, { status: target })
          this.detail = changed
          this.$emit('status-change', changed, target)
        })
        .catch(() => {
          this.statusValue = current || '待移交'
        })
        .finally(() => {
          this.statusSaving = false
        })
    },

    /* ---------------- 关联档案 ---------------- */

    /**
     * 取候选档案。
     * 不填关键词 → relatedArchives（本项目 / 宗地下的档案，附带「是否已关联」的判断依据）；
     * 填了关键词 → archive/pick（跨项目全局搜，方便关联到别的项目下的扫描件）。
     */
    loadArchives () {
      const keyword = String(this.archiveKeyword || '').trim()
      const id = this.detail && this.detail.id
      if (!keyword && !id) return

      this.archiveLoading = true
      const request = keyword
        ? pickHandoverArchives({ keyword, limit: 50 })
        : queryHandoverArchives(id, 50)

      request
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '关联档案加载失败')
            this.archives = []
            return
          }
          this.archives = res.result || []
        })
        .catch(() => {
          this.archives = []
        })
        .finally(() => {
          this.archiveLoading = false
        })
    },

    handleLink (row) {
      if (!this.detail || !row || !row.id) return
      linkHandoverArchive(this.detail.id, row.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '关联失败')
            return
          }
          toast.success('关联成功')
          // 关联数是由后端按项目重算的，这里重新拉一次详情而不是本地 +1
          this.loadDetail(this.detail.id)
          this.loadArchives()
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    handleUnlink () {
      if (!this.detail || !this.detail.archiveId) return
      unlinkHandoverArchive(this.detail.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '取消关联失败')
            return
          }
          toast.success('已取消关联')
          this.loadDetail(this.detail.id)
          this.loadArchives()
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    /* ---------------- 其它 ---------------- */

    handleEdit () {
      if (!this.detail) return
      this.$emit('edit', this.detail)
    },

    handleClose () {
      this.visible = false
      this.detail = null
      this.archives = []
      this.archiveKeyword = ''
    },

    /** '2026-09-30 10:00:00 · admin' 形态的合并文案 */
    joinInfo (time, user) {
      const parts = []
      if (time) parts.push(String(time))
      if (user) parts.push(String(user))
      return parts.length ? parts.join(' · ') : '—'
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.handover-detail {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__tags {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-3);
    flex: 0 0 auto;
    min-width: 0;
  }

  &__no {
    flex: 0 0 auto;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-sm);
    letter-spacing: 1px;
    color: var(--screen-accent);
  }

  &__name {
    margin: 0;
    min-width: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  // ScreenLoading 的根节点不是弹性子项，必须显式拉伸（README 第 6 节第 9 条）
  &__content {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
  }

  /* ---------------- 状态切换条 ---------------- */

  &__status-bar {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex: 0 0 auto;
    flex-wrap: wrap;
    padding: var(--screen-space-2) var(--screen-space-3);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__status-label {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__status-hint {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* ---------------- 业务提示 ---------------- */

  &__warnings {
    display: flex;
    flex-direction: column;
    gap: 6px;
    flex: 0 0 auto;
  }

  &__warning {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-warning);
    // 底色留给父级表面，只靠令牌色的描边 + 文字表达警告（不写死颜色）`r`n    background: transparent;
    border: 1px solid var(--screen-warning);
    border-radius: var(--screen-radius-sm);

    /deep/ .screen-icon {
      flex: 0 0 auto;
      margin-top: 2px;
    }
  }

  /* ---------------- 描述分组 ---------------- */

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3);
    flex: 0 0 auto;
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    padding: var(--screen-space-3);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  // 「其他信息」单独占一行，避免与本行两个分组挤在一起高低不齐
  &__section--archives {
    flex: 1 1 auto;
    min-height: 0;
  }

  &__section-title {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 8px;
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__section-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* ---------------- 关联档案 ---------------- */

  &__picker {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex: 0 0 auto;

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
      max-width: 420px;
    }
  }

  &__picker-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__archive-name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__mute {
    color: var(--screen-text-mute);
  }

  &__link {
    .screen-link-action();

    &:disabled {
      color: var(--screen-text-mute);
      cursor: not-allowed;
    }
  }
}

// 第三组（其他信息）占满整行；两列栅格在窄容器下降为单列
.handover-detail__grid > .handover-detail__section:last-child {
  grid-column-start: 1;
  grid-column-end: -1;
}

@media (max-width: 1280px) {
  .handover-detail__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
