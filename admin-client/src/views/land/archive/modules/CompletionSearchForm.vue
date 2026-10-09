<template>
  <div class="completion-search">
    <a-form layout="inline" class="completion-search__form">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="项目名称">
            <a-input v-model="query.projectName" placeholder="历史项目名称，模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="档案编号">
            <a-input v-model="query.archiveNo" placeholder="如 JG-2026-0001" allowClear @pressEnter="handleSearch" />
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
          <a-form-item label="数字化状态">
            <a-select v-model="query.digitizeStatus" allowClear placeholder="全部" style="width: 100%">
              <a-select-option v-for="item in statuses" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="项目编号">
              <a-input v-model="query.projectCode" placeholder="历史项目原编号" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="项目类型">
              <a-select v-model="query.projectType" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in projectTypes" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="保管期限">
              <a-select v-model="query.retention" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in retentions" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="建设单位">
              <a-input v-model="query.buildUnit" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="施工单位">
              <a-input v-model="query.constructUnit" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
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
            <a-form-item label="开工日期">
              <a-range-picker
                v-model="startRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleStartDateChange" />
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
            <a-form-item label="数字化单位">
              <a-input v-model="query.digitizeOrg" placeholder="数字化加工单位，模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="数字化日期">
              <a-range-picker
                v-model="digitizeRange"
                style="width: 100%"
                valueFormat="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
                @change="handleDigitizeDateChange" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="总页数">
              <a-input-group compact>
                <a-input-number
                  v-model="query.beginPageCount"
                  :min="0"
                  placeholder="下限"
                  style="width: calc(50% - 11px)" />
                <a-input
                  style="width: 22px; border-left: 0; pointer-events: none; background-color: #fff"
                  placeholder="~"
                  disabled />
                <a-input-number
                  v-model="query.endPageCount"
                  :min="0"
                  placeholder="上限"
                  style="width: calc(50% - 11px)" />
              </a-input-group>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="扫描分辨率">
              <a-select v-model="query.scanDpi" allowClear placeholder="全部（DPI）" style="width: 100%">
                <a-select-option v-for="dpi in scanDpiOptions" :key="dpi" :value="dpi">{{ dpi }} DPI</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="扫描件">
              <a-select v-model="query.hasArchive" allowClear placeholder="全部" style="width: 100%">
                <a-select-option :value="true">已挂扫描件</a-select-option>
                <a-select-option :value="false">未挂扫描件</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="投资额(万元)">
              <a-input-group compact>
                <a-input-number
                  v-model="query.beginInvestAmount"
                  :min="0"
                  placeholder="下限"
                  style="width: calc(50% - 11px)" />
                <a-input
                  style="width: 22px; border-left: 0; pointer-events: none; background-color: #fff"
                  placeholder="~"
                  disabled />
                <a-input-number
                  v-model="query.endInvestAmount"
                  :min="0"
                  placeholder="上限"
                  style="width: calc(50% - 11px)" />
              </a-input-group>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="出让宗地编号">
              <a-input v-model="query.crzdbh" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="关键词">
              <a-input v-model="query.keyword" placeholder="档案编号/项目/单位" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="completion-search__actions">
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
  import { queryXzqhOptions } from '@/api/land/landData'
  import {
    DICT,
    DIGITIZE_STATUS,
    SCAN_DPI_OPTIONS,
    loadDictItems
  } from '@/api/land/completion'

  /**
   * 竣工验收项目历史工程资料数字化档案 - 查询条件面板（方案 2.3.2 第 8 项）
   *
   * 需求原文只写了「可进行快速检索查询，可快速导出档案信息」，清单 §6.3.8 也没明列检索条件，
   * 因此这里按「历史档案盘点」的实际用法设计 16 个条件，分三组：
   *   · 项目维度：项目名称、档案编号、项目编号、行政区划、项目类型、建设/施工单位；
   *   · 时间维度：竣工、开工、验收三段日期区间；
   *   · ★数字化维度（本模块重点）：数字化状态、数字化加工单位、数字化完成日期区间、
   *     总页数区间、扫描分辨率、**是否已挂扫描件**。
   *
   * 「是否已挂扫描件 = 未挂 + 数字化状态 = 已数字化」这一组合能一键筛出
   * 「状态说做完了、但扫描件没挂上来」的矛盾记录，是数字化推进时最常用的自查方式。
   *
   * 默认只显示 4 个最常用的条件，点「更多条件」展开其余，避免档案首屏被表单占满。
   */
  export default {
    name: 'CompletionSearchForm',
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
        startRange: [],
        completeRange: [],
        acceptanceRange: [],
        digitizeRange: [],
        query: Object.assign(this.buildEmptyQuery(), this.initialQuery || {}),
        xzqhOptions: [],
        statuses: DIGITIZE_STATUS,
        scanDpiOptions: SCAN_DPI_OPTIONS,
        projectTypes: [],
        retentions: []
      }
    },
    created () {
      this.loadXzqh()
      this.loadDicts()
      // 初始条件里可能带了日期区间，还原到 range picker 的显示
      if (this.query.beginCompleteDate || this.query.endCompleteDate) {
        this.completeRange = [this.query.beginCompleteDate, this.query.endCompleteDate]
      }
      if (this.query.beginStartDate || this.query.endStartDate) {
        this.startRange = [this.query.beginStartDate, this.query.endStartDate]
      }
      if (this.query.beginAcceptanceDate || this.query.endAcceptanceDate) {
        this.acceptanceRange = [this.query.beginAcceptanceDate, this.query.endAcceptanceDate]
      }
      if (this.query.beginDigitizeDate || this.query.endDigitizeDate) {
        this.digitizeRange = [this.query.beginDigitizeDate, this.query.endDigitizeDate]
      }
    },
    methods: {
      buildEmptyQuery () {
        return {
          archiveNo: '',
          projectName: '',
          projectCode: '',
          xzqh: undefined,
          projectType: undefined,
          buildUnit: '',
          constructUnit: '',
          designUnit: '',
          superviseUnit: '',
          digitizeStatus: undefined,
          digitizeOrg: '',
          scanDpi: undefined,
          hasArchive: undefined,
          retention: undefined,
          crzdbh: '',
          keyword: '',
          beginPageCount: undefined,
          endPageCount: undefined,
          beginInvestAmount: undefined,
          endInvestAmount: undefined,
          beginStartDate: undefined,
          endStartDate: undefined,
          beginCompleteDate: undefined,
          endCompleteDate: undefined,
          beginAcceptanceDate: undefined,
          endAcceptanceDate: undefined,
          beginDigitizeDate: undefined,
          endDigitizeDate: undefined
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
        loadDictItems(DICT.projectType).then(items => { this.projectTypes = items || [] })
        loadDictItems(DICT.retention).then(items => { this.retentions = items || [] })
      },
      handleStartDateChange (values) {
        this.query.beginStartDate = values && values.length === 2 ? values[0] : undefined
        this.query.endStartDate = values && values.length === 2 ? values[1] : undefined
      },
      handleCompleteDateChange (values) {
        this.query.beginCompleteDate = values && values.length === 2 ? values[0] : undefined
        this.query.endCompleteDate = values && values.length === 2 ? values[1] : undefined
      },
      handleAcceptanceDateChange (values) {
        this.query.beginAcceptanceDate = values && values.length === 2 ? values[0] : undefined
        this.query.endAcceptanceDate = values && values.length === 2 ? values[1] : undefined
      },
      handleDigitizeDateChange (values) {
        this.query.beginDigitizeDate = values && values.length === 2 ? values[0] : undefined
        this.query.endDigitizeDate = values && values.length === 2 ? values[1] : undefined
      },
      handleSearch () {
        this.$emit('search', this.normalize(this.query))
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.startRange = []
        this.completeRange = []
        this.acceptanceRange = []
        this.digitizeRange = []
        this.$emit('search', this.normalize(this.query))
        this.$emit('reset')
      },
      /** 去掉空值，避免拼出一堆空参数（★ 注意 false 必须保留：hasArchive=false 是有意义的条件） */
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
      /** 供父组件读取当前条件（导出时用） */
      getQuery () {
        return this.normalize(this.query)
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .completion-search {
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
        width: 100px;
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
  }
</style>
