<template>
  <!--
    HandoverSearchForm 道路交付及养护协议移交事项 · 检索条件
    --------------------------------
    清单 §6.3.6 明列的 8 个条件全部实现：
      道路名称 / 行政区划 / 出让宗地编号 / 关联配套项目 / 移交类型 / 状态 /
      协议签订日期区间 / 移交日期区间；
    另补 6 个实际会在用的条件：移交编号 / 道路等级 / 地块名称 / 接收管养单位 /
    养护截止日期区间 / 是否已关联档案（外加功能区与关键词）。

    默认只露出 4 个最常用的（道路名称 / 行政区划 / 宗地编号 / 移交状态），
    其余 12 个收在「更多条件」后面 —— 一进来就被 16 个输入框淹没没人愿意用。

    事件：
      search (query)  点「查询」或按回车时抛出，query 已剔除空值
      reset           点「重置」时抛出（同时也抛 search，父组件只需监听 search）

    公开方法：
      getQuery()      取当前条件（已剔除空值），供「导出 Excel」复用同一份条件
      setQuery(query) 用一组新条件覆盖表单（外部下钻时调用）
  -->
  <div class="handover-search">
    <div class="handover-search__grid">
      <!-- ---------- 常驻的 4 个核心条件 ---------- -->
      <screen-field label="道路名称" label-width="96px" html-for="hs-roadName">
        <screen-input
          id="hs-roadName"
          v-model="query.roadName"
          clearable
          placeholder="按道路名称模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="行政区划" label-width="96px">
        <screen-select
          v-model="query.xzqh"
          :options="xzqhOptions"
          placeholder="全部区划"
          aria-label="行政区划"
        />
      </screen-field>

      <screen-field label="宗地编号" label-width="96px" html-for="hs-crzdbh">
        <screen-input
          id="hs-crzdbh"
          v-model="query.crzdbh"
          clearable
          placeholder="出让宗地编号"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="移交状态" label-width="96px">
        <screen-select
          v-model="query.status"
          :options="statusOptions"
          placeholder="全部状态"
          aria-label="移交状态"
        />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="移交编号" label-width="96px" html-for="hs-handoverNo">
          <screen-input
            id="hs-handoverNo"
            v-model="query.handoverNo"
            clearable
            placeholder="例如 YJ-2026-0001"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="移交类型" label-width="96px">
          <screen-select
            v-model="query.handoverType"
            :options="typeOptions"
            placeholder="全部类型"
            aria-label="移交类型"
          />
        </screen-field>

        <screen-field label="关联配套项目" label-width="96px">
          <!--
            远程搜索：filter-local=false 时 ScreenSelect 不做本地过滤，
            而是把关键词通过 @search 抛出来，由这里去后端查（否则选项只有默认那几条，
            搜不到的项目等于选不上）。输入即查，不需要先选宗地。
          -->
          <screen-select
            v-model="query.facilityId"
            :options="facilityOptions"
            searchable
            :filter-local="false"
            :empty-text="facilityLoading ? '搜索中…' : '输入道路/项目名称搜索'"
            placeholder="输入项目名称搜索"
            aria-label="关联配套项目"
            @search="handleFacilitySearch"
          />
        </screen-field>

        <screen-field label="道路等级" label-width="96px">
          <screen-select
            v-model="query.dldj"
            :options="dldjOptions"
            placeholder="全部等级"
            aria-label="道路等级"
          />
        </screen-field>

        <screen-field label="功能区" label-width="96px">
          <screen-select
            v-model="query.gnq"
            :options="gnqOptions"
            placeholder="非功能区留空"
            aria-label="功能区"
          />
        </screen-field>

        <screen-field label="地块名称" label-width="96px" html-for="hs-dkmc">
          <screen-input
            id="hs-dkmc"
            v-model="query.dkmc"
            clearable
            placeholder="地块名称模糊检索"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="接收管养单位" label-width="96px" html-for="hs-receiveUnit">
          <screen-input
            id="hs-receiveUnit"
            v-model="query.receiveUnit"
            clearable
            placeholder="接收管养单位"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="协议签订日期" label-width="96px">
          <screen-date-input
            v-model="agreementRange"
            mode="range"
            @change="handleAgreementChange"
          />
        </screen-field>

        <screen-field label="移交日期" label-width="96px">
          <screen-date-input
            v-model="handoverRange"
            mode="range"
            @change="handleHandoverChange"
          />
        </screen-field>

        <screen-field label="养护截止日期" label-width="96px">
          <screen-date-input
            v-model="maintenanceRange"
            mode="range"
            @change="handleMaintenanceChange"
          />
        </screen-field>

        <screen-field label="是否关联档案" label-width="96px">
          <screen-select
            v-model="query.hasArchive"
            :options="archiveLinkOptions"
            placeholder="不限"
            aria-label="是否已关联档案"
          />
        </screen-field>

        <screen-field label="关键词" label-width="96px" html-for="hs-keyword">
          <screen-input
            id="hs-keyword"
            v-model="query.keyword"
            clearable
            placeholder="编号 / 道路 / 宗地 / 协议编号"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <div class="handover-search__foot">
      <button
        type="button"
        class="handover-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="handover-search__actions">
        <screen-button icon="rotate-ccw" @click="handleReset">重置</screen-button>
        <screen-button type="primary" icon="search" @click="handleSearch">查询</screen-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenButton,
  ScreenIcon
} from '@/components/screen'
import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
import { compactQuery } from '../../constants'
import {
  ARCHIVE_LINK_OPTIONS,
  DLDJ_OPTIONS,
  GNQ_OPTIONS,
  handoverStatusOptions,
  handoverTypeOptions
} from './constants'

/** 所有检索字段的初始空值，重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    handoverNo: '',
    roadName: '',
    roadCode: '',
    dldj: '',
    xzqh: '',
    gnq: '',
    crzdbh: '',
    dkmc: '',
    facilityId: '',
    /** 只为下拉回显用的展示名，不参与检索（getQuery 里会剔除） */
    facilityLabel: '',
    handoverType: '',
    status: '',
    receiveUnit: '',
    buildUnit: '',
    hasArchive: '',
    keyword: '',
    beginAgreementDate: '',
    endAgreementDate: '',
    beginHandoverDate: '',
    endHandoverDate: '',
    beginMaintenanceEnd: '',
    endMaintenanceEnd: ''
  }
}

/** 「更多条件」里的字段名：这些字段有值就说明需要展开面板 */
const HIDDEN_FIELDS = [
  'handoverNo', 'dldj', 'gnq', 'dkmc', 'facilityId', 'handoverType', 'receiveUnit',
  'buildUnit', 'hasArchive', 'keyword',
  'beginAgreementDate', 'endAgreementDate', 'beginHandoverDate', 'endHandoverDate',
  'beginMaintenanceEnd', 'endMaintenanceEnd'
]

export default {
  name: 'HandoverSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon
  },
  props: {
    /** 是否默认展开全部条件 */
    defaultExpanded: { type: Boolean, default: false },
    /** 初始条件（外部下钻时带进来，例如 { facilityId, status }） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: buildEmptyQuery(),
      agreementRange: [],
      handoverRange: [],
      maintenanceRange: [],
      xzqhOptions: [],
      facilityOptions: [],
      facilityLoading: false,
      statusOptions: handoverStatusOptions(),
      typeOptions: handoverTypeOptions(),
      dldjOptions: DLDJ_OPTIONS.map((item) => ({ value: item, label: item })),
      gnqOptions: GNQ_OPTIONS.map((item) => ({ value: item, label: item })),
      archiveLinkOptions: ARCHIVE_LINK_OPTIONS
    }
  },
  created () {
    this.loadXzqh()
    this.setQuery(this.initialQuery)
  },
  methods: {
    loadXzqh () {
      queryXzqhOptions()
        .then((res) => {
          if (!res || !res.success) return
          // 后端返回 [{ value, label }]，做一次兜底以兼容 [{ xzqh }] 形态
          this.xzqhOptions = (res.result || []).map((item) => {
            if (item && item.value !== undefined) return { value: item.value, label: item.label }
            const value = item && (item.xzqh || item.name)
            return { value, label: value }
          })
        })
        .catch(() => {
          // 区划只是辅助筛选条件，加载失败不阻塞主流程，静默降级为空选项
          this.xzqhOptions = []
        })
    },

    /** 配套项目远程搜索（大屏只有一个下拉，输入即查，不限宗地） */
    handleFacilitySearch (keyword) {
      const text = String(keyword || '').trim()
      if (!text) {
        this.facilityOptions = []
        return
      }
      this.facilityLoading = true
      searchFacilityOptions({ keyword: text, limit: 50 })
        .then((res) => {
          const list = res && res.success ? res.result || [] : []
          this.facilityOptions = list.map((item) => ({
            value: item.id,
            label: item.crzdbh ? `${item.ptxmmc}（${item.crzdbh}）` : item.ptxmmc
          }))
        })
        .catch(() => {
          this.facilityOptions = []
        })
        .finally(() => {
          this.facilityLoading = false
        })
    },

    /* ---------------- 三个日期区间 ---------------- */

    handleAgreementChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginAgreementDate = values[0] || ''
      this.query.endAgreementDate = values[1] || ''
    },

    handleHandoverChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginHandoverDate = values[0] || ''
      this.query.endHandoverDate = values[1] || ''
    },

    handleMaintenanceChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginMaintenanceEnd = values[0] || ''
      this.query.endMaintenanceEnd = values[1] || ''
    },

    /* ---------------- 查询 / 重置 ---------------- */

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.agreementRange = []
      this.handoverRange = []
      this.maintenanceRange = []
      this.facilityOptions = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态
      this.$emit('search', this.getQuery())
    },

    /* ---------------- 对外接口 ---------------- */

    /** 取当前条件（已剔除空值，并把布尔型条件转回真正的布尔） */
    getQuery () {
      const result = compactQuery(this.query)
      // facilityLabel 只是下拉回显用的展示名，不是后端条件，不能发出去
      delete result.facilityLabel
      // ScreenSelect 的 value 只接受 String/Number，界面上用 'true'/'false'，这里转回布尔
      if (result.hasArchive === 'true') {
        result.hasArchive = true
      } else if (result.hasArchive === 'false') {
        result.hasArchive = false
      } else {
        delete result.hasArchive
      }
      return result
    },

    /**
     * 用一组新条件覆盖当前表单（外部下钻时调用）。
     * 之所以需要这个方法：initialQuery 只在 created 里读一次，
     * 组件已挂载后就改不动了（面板被 keep-alive 缓存时尤其明显）。
     */
    setQuery (query) {
      const source = Object.assign({}, query || {})
      const next = Object.assign(buildEmptyQuery(), source)

      // 布尔 → 字符串，供 ScreenSelect 选中
      if (next.hasArchive === true) next.hasArchive = 'true'
      else if (next.hasArchive === false) next.hasArchive = 'false'

      this.query = next

      // 三个日期区间回显
      this.agreementRange = next.beginAgreementDate || next.endAgreementDate
        ? [next.beginAgreementDate || '', next.endAgreementDate || '']
        : []
      this.handoverRange = next.beginHandoverDate || next.endHandoverDate
        ? [next.beginHandoverDate || '', next.endHandoverDate || '']
        : []
      this.maintenanceRange = next.beginMaintenanceEnd || next.endMaintenanceEnd
        ? [next.beginMaintenanceEnd || '', next.endMaintenanceEnd || '']
        : []

      // 下钻带了配套项目：把它的展示名塞进下拉选项，否则下拉会显示空白
      if (next.facilityId) {
        this.facilityOptions = [{
          value: next.facilityId,
          label: next.facilityLabel || next.facilityId
        }]
      }

      // 带的是折叠区里的字段就把「更多条件」展开，否则用户会以为条件没生效
      if (HIDDEN_FIELDS.some((key) => {
        const value = next[key]
        return value !== '' && value !== undefined && value !== null
      })) {
        this.expanded = true
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.handover-search {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
  }

  &__toggle {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    background: none;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
  }
}

// 大屏常见宽度下的列数降级：保证每个控件仍有足够可用宽度
@media (max-width: 1800px) {
  .handover-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .handover-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .handover-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .handover-search__foot {
    flex-wrap: wrap;
  }
}
</style>
