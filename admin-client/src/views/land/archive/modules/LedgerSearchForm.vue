<template>
  <div class="ledger-search">
    <a-form layout="inline" class="ledger-search__form">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="道路名称">
            <a-input v-model="query.roadName" placeholder="道路/配套项目名称，模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="台账编号">
            <a-input v-model="query.ledgerNo" placeholder="如 YS-2026-0001" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="行政区划">
            <a-select v-model="query.xzqh" allowClear placeholder="全部" style="width: 100%">
              <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="状态">
            <a-select v-model="query.status" allowClear placeholder="全部" style="width: 100%">
              <a-select-option v-for="item in statuses" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="验收类型">
              <a-select v-model="query.acceptanceType" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in acceptanceTypes" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="验收结果">
              <a-select v-model="query.acceptanceResult" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in acceptanceResults" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="功能区">
              <a-select v-model="query.gnq" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in gnqOptions" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="设施类别">
              <a-select v-model="query.ptsslb" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in ptsslbOptions" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="关联配套项目">
              <a-select
                show-search
                allowClear
                placeholder="输入道路/配套项目名称搜索"
                :filterOption="false"
                :defaultActiveFirstOption="false"
                :notFoundContent="facilityLoading ? '搜索中…' : '未找到配套项目'"
                @search="handleFacilitySearch"
                @change="handleFacilityChange">
                <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
                  {{ item.ptxmmc }}
                  <span v-if="item.crzdbh" class="ledger-search__option-extra">（{{ item.crzdbh }}）</span>
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="出让宗地编号">
              <a-input v-model="query.crzdbh" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="地块名称">
              <a-input v-model="query.dkmc" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="验收日期">
              <a-range-picker
                v-model="acceptanceRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleAcceptanceDateChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="竣工日期">
              <a-range-picker
                v-model="completeRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleCompleteDateChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="移交日期">
              <a-range-picker
                v-model="handoverRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleHandoverDateChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="缺少资料">
              <a-select v-model="query.missingMaterial" allowClear placeholder="不限（筛出缺该资料的道路）" style="width: 100%">
                <a-select-option v-for="item in materials" :key="item.key" :value="item.key">
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="资料归集数">
              <a-input-group compact>
                <a-input-number
                  v-model="query.beginMaterialCount"
                  :min="0"
                  :max="13"
                  placeholder="下限"
                  style="width: calc(50% - 11px)" />
                <a-input
                  style="width: 22px; border-left: 0; pointer-events: none; background-color: #fff"
                  placeholder="~"
                  disabled />
                <a-input-number
                  v-model="query.endMaterialCount"
                  :min="0"
                  :max="13"
                  placeholder="上限"
                  style="width: calc(50% - 11px)" />
              </a-input-group>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="关键词">
              <a-input v-model="query.keyword" placeholder="道路/台账编号/宗地/地块" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="ledger-search__actions">
          <a-button type="primary" icon="search" @click="handleSearch">查询</a-button>
          <a-button icon="reload" @click="handleReset">重置</a-button>
          <a-button type="link" @click="expanded = !expanded">
            {{ expanded ? '收起' : '更多条件' }}
            <a-icon :type="expanded ? 'up' : 'down'" />
          </a-button>
        </a-col>
      </a-row>
    </a-form>
  </div>
</template>

<script>
  import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
  import {
    DICT,
    GNQ_OPTIONS,
    LEDGER_MATERIALS_FALLBACK,
    LEDGER_STATUS,
    PTSSLB_OPTIONS,
    loadDictItems
  } from '@/api/land/ledger'

  /**
   * 道路设施验收及移交资料台账 - 查询条件面板（方案 2.3.2 第 7 项）
   *
   * 原文要求：「实现对道路设施验收及移交资料台账的管理，可进行快速检索查询」，
   * 清单 §6.3.7 明列的检索条件为：道路名称、行政区、验收类型、验收日期区间、验收结果、状态。
   * 这里把 6 项基本条件全部实现，另按台账的实际用法补了 6 组
   * （宗地编号/地块名称/设施类别、缺少某类资料、资料归集数区间、移交与竣工日期区间、关键词）。
   *
   * 默认只显示 4 个最常用的条件，点「更多条件」展开其余，避免台账首屏被表单占满。
   * 其中「缺少资料」是本模块最有业务价值的筛选项 —— 可一键筛出「还缺竣工文件」的道路。
   */
  export default {
    name: 'LedgerSearchForm',
    props: {
      /** 是否默认展开全部条件 */
      defaultExpanded: {
        type: Boolean,
        default: false
      },
      /** 初始条件（从统计图下钻或从档案/配套页跳转时带入），只在 created 时应用一次 */
      initialQuery: {
        type: Object,
        default: () => ({})
      }
    },
    data () {
      return {
        expanded: this.defaultExpanded,
        acceptanceRange: [],
        completeRange: [],
        handoverRange: [],
        query: Object.assign(this.buildEmptyQuery(), this.initialQuery || {}),
        xzqhOptions: [],
        statuses: LEDGER_STATUS,
        gnqOptions: GNQ_OPTIONS,
        ptsslbOptions: PTSSLB_OPTIONS,
        materials: LEDGER_MATERIALS_FALLBACK,
        acceptanceTypes: [],
        acceptanceResults: [],
        facilityOptions: [],
        facilityLoading: false
      }
    },
    created () {
      this.loadXzqh()
      this.loadDicts()
      // 初始条件里可能带了日期区间，还原到 range picker 的显示
      if (this.query.beginAcceptanceDate || this.query.endAcceptanceDate) {
        this.acceptanceRange = [this.query.beginAcceptanceDate, this.query.endAcceptanceDate]
      }
      if (this.query.beginCompleteDate || this.query.endCompleteDate) {
        this.completeRange = [this.query.beginCompleteDate, this.query.endCompleteDate]
      }
      if (this.query.beginHandoverDate || this.query.endHandoverDate) {
        this.handoverRange = [this.query.beginHandoverDate, this.query.endHandoverDate]
      }
    },
    methods: {
      buildEmptyQuery () {
        return {
          ledgerNo: '',
          roadName: '',
          xzqh: undefined,
          gnq: undefined,
          status: undefined,
          acceptanceType: undefined,
          acceptanceResult: undefined,
          ptsslb: undefined,
          facilityId: undefined,
          crzdbh: '',
          dkmc: '',
          missingMaterial: undefined,
          keyword: '',
          beginMaterialCount: undefined,
          endMaterialCount: undefined,
          beginAcceptanceDate: undefined,
          endAcceptanceDate: undefined,
          beginCompleteDate: undefined,
          endCompleteDate: undefined,
          beginHandoverDate: undefined,
          endHandoverDate: undefined
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        }).catch(() => {
          this.xzqhOptions = []
        })
      },
      loadDicts () {
        loadDictItems(DICT.acceptanceType).then(items => {
          this.acceptanceTypes = items || []
        })
        loadDictItems(DICT.acceptanceResult).then(items => {
          this.acceptanceResults = items || []
        })
      },
      handleAcceptanceDateChange (values) {
        this.query.beginAcceptanceDate = values && values.length === 2 ? values[0] : undefined
        this.query.endAcceptanceDate = values && values.length === 2 ? values[1] : undefined
      },
      handleCompleteDateChange (values) {
        this.query.beginCompleteDate = values && values.length === 2 ? values[0] : undefined
        this.query.endCompleteDate = values && values.length === 2 ? values[1] : undefined
      },
      handleHandoverDateChange (values) {
        this.query.beginHandoverDate = values && values.length === 2 ? values[0] : undefined
        this.query.endHandoverDate = values && values.length === 2 ? values[1] : undefined
      },
      handleSearch () {
        this.$emit('search', this.normalize(this.query))
      },
      /**
       * 由父组件写入若干查询条件（用于「13 类资料归集情况」点『筛出缺失』、
       * 以及从档案/配套项目深链下钻后同步面板显示），写完不自动查询。
       *
       * @param {object} patch 要覆盖的条件
       */
      setQuery (patch) {
        Object.keys(patch || {}).forEach(key => {
          this.$set(this.query, key, patch[key])
        })
        // 有条件写入时自动展开更多条件，否则用户看不到刚被设上的条件
        if (patch && Object.keys(patch).length) {
          this.expanded = true
        }
      },
      /** 配套项目远程搜索（与新增/编辑弹窗同一套数据源） */
      handleFacilitySearch (keyword) {
        if (!keyword) {
          this.facilityOptions = []
          return
        }
        this.facilityLoading = true
        searchFacilityOptions({ keyword, limit: 50 }).then(res => {
          this.facilityOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.facilityOptions = []
        }).finally(() => {
          this.facilityLoading = false
        })
      },
      handleFacilityChange (facilityId) {
        this.query.facilityId = facilityId
        // 选中后把配套项目名回填到关键词以外的展示字段，便于用户确认选的是哪个
        const hit = this.facilityOptions.filter(item => item.id === facilityId)[0]
        if (hit && !this.query.crzdbh) {
          this.query.crzdbh = hit.crzdbh
        }
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.acceptanceRange = []
        this.completeRange = []
        this.handoverRange = []
        this.$emit('search', this.normalize(this.query))
        this.$emit('reset')
      },
      /** 去掉空值，避免拼出一堆空参数 */
      normalize (query) {
        const result = {}
        Object.keys(query || {}).forEach(key => {
          const value = query[key]
          if (value !== undefined && value !== null && value !== '') {
            result[key] = value
          }
        })
        return result
      },
      /** 供父组件读取当前条件（导出台账时用） */
      getQuery () {
        return this.normalize(this.query)
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .ledger-search {
    padding: 16px 16px 0;
    margin-bottom: 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__form {
      /deep/ .ant-form-item {
        display: flex;
        margin-bottom: 16px;
      }

      /deep/ .ant-form-item-label {
        flex: none;
        width: 96px;
        text-align: right;
      }

      /deep/ .ant-form-item-control-wrapper {
        flex: 1 1 auto;
        min-width: 0;
      }

      /deep/ .ant-form-item-children {
        display: block;
      }
    }

    &__actions {
      text-align: right;
      margin-bottom: 16px;

      /deep/ .ant-btn {
        margin-left: 8px;
      }
    }

    &__option-extra {
      color: #94a3b8;
      font-size: 12px;
    }
  }
</style>
