<template>
  <div class="handover-search">
    <a-form layout="inline" class="handover-search__form">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="道路名称">
            <a-input v-model="query.roadName" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="移交编号">
            <a-input v-model="query.handoverNo" placeholder="如 YJ-2026-0001" allowClear @pressEnter="handleSearch" />
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

        <!-- ============ 更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="移交类型">
              <a-select v-model="query.handoverType" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in handoverTypes" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="道路等级">
              <a-select v-model="query.dldj" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in dldjOptions" :key="item" :value="item">{{ item }}</a-select-option>
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
            <a-form-item label="关联配套项目">
              <a-select
                :value="query.facilityId"
                show-search
                allowClear
                placeholder="输入道路/配套项目名称搜索"
                :filterOption="false"
                :defaultActiveFirstOption="false"
                :notFoundContent="facilityLoading ? '搜索中…' : '未找到配套项目'"
                @search="handleFacilitySearch"
                @focus="handleFacilityFocus"
                @change="handleFacilityChange">
                <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
                  {{ item.ptxmmc }}
                  <span v-if="item.crzdbh" class="handover-search__option-extra">（{{ item.crzdbh }}）</span>
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="接收管养单位">
              <a-input v-model="query.receiveUnit" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="协议签订日期">
              <a-range-picker
                v-model="agreementRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleAgreementChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="移交日期">
              <a-range-picker
                v-model="handoverRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleHandoverChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="养护截止日期">
              <a-range-picker
                v-model="maintenanceRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleMaintenanceChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="是否已关联档案">
              <a-select v-model="query.hasArchive" allowClear placeholder="不限" style="width: 100%">
                <a-select-option :value="true">已关联</a-select-option>
                <a-select-option :value="false">未关联</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="关键词">
              <a-input v-model="query.keyword" placeholder="编号/道路/宗地/协议编号" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="handover-search__actions">
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
    DLDJ_OPTIONS,
    GNQ_OPTIONS,
    HANDOVER_STATUS,
    loadDictItems
  } from '@/api/land/handover'

  /**
   * 道路交付及养护协议移交事项 - 查询条件面板（方案 2.3.2 第 6 项）
   *
   * 原文：「实现对道路交付养护协议及移交事项的管理，可进行快速检索查询。」
   * 清单 §6.3.6 明列的 8 个检索条件全部实现：
   *   道路名称、行政区划、宗地编号、配套项目、移交类型、状态、协议签订日期区间、移交日期区间；
   * 另补：道路等级、地块名称、接收管养单位、养护截止日期区间、是否已关联档案、关键词。
   */
  export default {
    name: 'HandoverSearchForm',
    props: {
      defaultExpanded: {
        type: Boolean,
        default: false
      },
      initialQuery: {
        type: Object,
        default: () => ({})
      }
    },
    data () {
      return {
        expanded: this.defaultExpanded,
        agreementRange: [],
        handoverRange: [],
        maintenanceRange: [],
        query: Object.assign(this.buildEmptyQuery(), this.initialQuery || {}),
        xzqhOptions: [],
        statuses: HANDOVER_STATUS,
        gnqOptions: GNQ_OPTIONS,
        dldjOptions: DLDJ_OPTIONS,
        handoverTypes: [],
        facilityOptions: [],
        facilityLoading: false
      }
    },
    created () {
      this.loadXzqh()
      // 打开即加载首批：原来只在输入关键词后才请求，一进来下拉是空的，
      // 下钻只带 facilityId 时更会显示空白（清单 P1-09 / P1-10）
      this.loadFacilityOptions('')
      loadDictItems(DICT.handoverType).then(items => { this.handoverTypes = items || [] })
      if (this.query.beginAgreementDate || this.query.endAgreementDate) {
        this.agreementRange = [this.query.beginAgreementDate, this.query.endAgreementDate]
      }
      if (this.query.beginHandoverDate || this.query.endHandoverDate) {
        this.handoverRange = [this.query.beginHandoverDate, this.query.endHandoverDate]
      }
      if (this.query.beginMaintenanceEnd || this.query.endMaintenanceEnd) {
        this.maintenanceRange = [this.query.beginMaintenanceEnd, this.query.endMaintenanceEnd]
      }
    },
    methods: {
      buildEmptyQuery () {
        return {
          handoverNo: '',
          roadName: '',
          xzqh: undefined,
          gnq: undefined,
          dldj: undefined,
          status: undefined,
          handoverType: undefined,
          crzdbh: '',
          dkmc: '',
          facilityId: undefined,
          /**
           * 只为下拉回显用的可读名称（档案详情下钻时带进来），**不是**检索条件
           * —— normalize 里会剔除。绝不用它顶替 facilityId，也绝不拿 id 当名称。
           */
          facilityLabel: undefined,
          receiveUnit: '',
          hasArchive: undefined,
          keyword: '',
          beginAgreementDate: undefined,
          endAgreementDate: undefined,
          beginHandoverDate: undefined,
          endHandoverDate: undefined,
          beginMaintenanceEnd: undefined,
          endMaintenanceEnd: undefined
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        }).catch(() => { this.xzqhOptions = [] })
      },
      handleAgreementChange (values) {
        this.query.beginAgreementDate = values && values.length === 2 ? values[0] : undefined
        this.query.endAgreementDate = values && values.length === 2 ? values[1] : undefined
      },
      handleHandoverChange (values) {
        this.query.beginHandoverDate = values && values.length === 2 ? values[0] : undefined
        this.query.endHandoverDate = values && values.length === 2 ? values[1] : undefined
      },
      handleMaintenanceChange (values) {
        this.query.beginMaintenanceEnd = values && values.length === 2 ? values[0] : undefined
        this.query.endMaintenanceEnd = values && values.length === 2 ? values[1] : undefined
      },
      /** 展开下拉时若还没有候选就补首批（不让用户先输关键词才看到可选项） */
      handleFacilityFocus () {
        if (!this.facilityOptions.length) {
          this.loadFacilityOptions('')
        }
      },
      /**
       * 配套项目远程搜索。
       * ★ 关键词为空时加载**首批**而不是清空：清空会让当前已选项一起消失。
       */
      handleFacilitySearch (keyword) {
        this.loadFacilityOptions(keyword)
      },
      /**
       * 拉取配套项目候选（整体替换）。
       *
       * ★ `roadFirst: true`（清单 P1-10）：移交检索条件的首要对象是道路，
       *   接口原来按固定顺序取前 50 条，首批里常常全是排水 / 供水 / 绿化。
       *   该参数让后端把 ptsslb=道路 的排在前面；后端未上线时会被忽略
       *   （Spring 忽略未知查询串参数），前端不必加开关。首批与关键词搜索共用本方法。
       *
       * @param {string} [keyword] 关键词；空 = 首批
       */
      loadFacilityOptions (keyword) {
        this.facilityLoading = true
        searchFacilityOptions({ keyword: keyword || undefined, limit: 50, roadFirst: true }).then(res => {
          this.facilityOptions = res && res.success ? (res.result || []) : []
          // 当前已选项可能不在前 50 条里，补回去否则下拉显示空白、用户以为条件丢了
          this.ensureCurrentFacilityOption()
        }).catch(() => {
          this.facilityOptions = []
          this.ensureCurrentFacilityOption()
        }).finally(() => {
          this.facilityLoading = false
        })
      },
      /**
       * 把当前条件里的配套项目补进候选。
       * ★ label 顺序：facilityLabel（下钻带的可读名）→ crzdbh → 占位文案，
       *   **绝不**退到 facilityId（长数字 ID 会被当成乱码，清单 P1-09 明令禁止）。
       */
      ensureCurrentFacilityOption () {
        const id = this.query.facilityId
        if (!id) {
          return
        }
        if (this.facilityOptions.some(item => item.id === id)) {
          return
        }
        const name = this.query.facilityLabel || this.query.crzdbh || '当前已选配套项目（名称待补）'
        this.facilityOptions = [{
          id,
          ptxmmc: name,
          crzdbh: this.query.crzdbh
        }].concat(this.facilityOptions)
      },
      handleFacilityChange (facilityId) {
        this.query.facilityId = facilityId
        const hit = this.facilityOptions.filter(item => item.id === facilityId)[0]
        if (hit && !this.query.crzdbh) {
          this.query.crzdbh = hit.crzdbh
        }
        // 换选项目后旧的展示名不再成立，改用新项目名，避免下拉显示上一项的名称
        this.query.facilityLabel = hit ? (hit.ptxmmc || '') : undefined
      },
      handleSearch () {
        this.$emit('search', this.normalize(this.query))
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.agreementRange = []
        this.handoverRange = []
        this.maintenanceRange = []
        // 重置只清条件，候选回到首批（清空会让下拉变成「没有可选项」）
        this.loadFacilityOptions('')
        this.$emit('search', this.normalize(this.query))
        this.$emit('reset')
      },
      /** 供父组件从外部写入条件（统计图下钻 / 详情联动） */
      setQuery (patch) {
        Object.keys(patch || {}).forEach(key => {
          this.$set(this.query, key, patch[key])
        })
        if (patch && Object.keys(patch).length) {
          this.expanded = true
        }
      },
      normalize (query) {
        const result = {}
        Object.keys(query || {}).forEach(key => {
          const value = query[key]
          if (value !== undefined && value !== null && value !== '') {
            result[key] = value
          }
        })
        // facilityLabel 只是下拉回显用的名称，后端 HandoverQueryDTO 里没有这个字段
        delete result.facilityLabel
        return result
      },
      getQuery () {
        return this.normalize(this.query)
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .handover-search {
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
        width: 104px;
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
