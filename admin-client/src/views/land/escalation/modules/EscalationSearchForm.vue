<template>
  <div class="escalation-search">
    <a-form layout="inline" class="escalation-search__form">
      <a-row :gutter="16" type="flex">
        <!-- ============ 常用条件（4 个） ============ -->
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="项目名称">
            <a-input v-model="query.projectName" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="申报单位">
            <a-input v-model="query.declareDept" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="申报时间">
            <a-range-picker
              v-model="declareRange"
              style="width: 100%"
              valueFormat="YYYY-MM-DD"
              :placeholder="['开始日期', '结束日期']"
              @change="handleDeclareRangeChange" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="论证结果">
            <!-- ★ 方案明确要求「按论证结果多维查询」，这里用 jeecg 字典下拉 -->
            <j-dict-select-tag
              v-model="query.argResult"
              :dictCode="DICT.argResult"
              placeholder="全部" />
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的其余 5 个条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="项目类型">
              <j-dict-select-tag
                v-model="query.projectType"
                :dictCode="DICT.projectType"
                placeholder="全部" />
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
            <a-form-item label="办理状态">
              <a-select v-model="query.status" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="土地整理">
              <j-dict-select-tag v-model="query.tdzlProject" :dictCode="DICT.yn" placeholder="全部" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="总投资">
              <div class="escalation-search__range">
                <a-input-number
                  v-model="query.minInvestment"
                  :min="0"
                  :precision="4"
                  placeholder="下限"
                  style="width: 100%" />
                <span class="escalation-search__range-sep">—</span>
                <a-input-number
                  v-model="query.maxInvestment"
                  :min="0"
                  :precision="4"
                  placeholder="上限"
                  style="width: 100%" />
                <span class="escalation-search__range-unit">亿元</span>
              </div>
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="escalation-search__actions">
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
  import JDictSelectTag from '@/components/dict/JDictSelectTag'
  import { queryXzqhOptions } from '@/api/land/landData'
  import { DICT, STATUS_OPTIONS } from '@/api/land/escalation'

  /**
   * 提级论证 · 查询条件面板（设计文档 5.5 的 9 个条件）
   *
   * 写法与 ArchiveSearchForm.vue 保持一致：简单 / 全量两模式。
   *   常用 4 个：项目名称、申报单位、申报时间区间、**论证结果**（方案明确要求）；
   *   展开 5 个：项目类型、行政区划、办理状态、是否土地整理项目、投资额区间。
   *
   * 条件键名与后端 EscalationQueryDTO 对齐：申=declare，论证=arg，地块=dk。
   */
  export default {
    name: 'EscalationSearchForm',
    components: { JDictSelectTag },
    props: {
      /** 是否默认展开全部条件（查询页传 true，录入页保持精简） */
      defaultExpanded: {
        type: Boolean,
        default: false
      },
      /**
       * 初始条件（例如从统计图下钻台账、或台账跳查询时带的 argResult）。
       * 只在 data 初始化时应用一次，之后完全由本组件自己维护。
       */
      initialQuery: {
        type: Object,
        default: () => ({})
      }
    },
    data () {
      return {
        expanded: this.defaultExpanded,
        declareRange: [],
        query: Object.assign(this.buildEmptyQuery(), this.initialQuery || {}),
        xzqhOptions: [],
        statusOptions: STATUS_OPTIONS,
        DICT
      }
    },
    created () {
      this.loadXzqh()
      // 初始条件里带了申报时间区间时，同步到 range picker 的显示
      if (this.query.beginDeclareDate && this.query.endDeclareDate) {
        this.declareRange = [this.query.beginDeclareDate, this.query.endDeclareDate]
      }
    },
    methods: {
      buildEmptyQuery () {
        return {
          projectName: '',
          declareDept: '',
          beginDeclareDate: undefined,
          endDeclareDate: undefined,
          argResult: undefined,
          projectType: undefined,
          xzqh: undefined,
          status: undefined,
          tdzlProject: undefined,
          minInvestment: undefined,
          maxInvestment: undefined
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        })
      },
      handleDeclareRangeChange (values) {
        if (values && values.length === 2) {
          this.query.beginDeclareDate = values[0]
          this.query.endDeclareDate = values[1]
        } else {
          this.query.beginDeclareDate = undefined
          this.query.endDeclareDate = undefined
        }
      },
      handleSearch () {
        this.$emit('search', this.normalize(this.query))
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.declareRange = []
        this.$emit('search', this.normalize(this.query))
        this.$emit('reset')
      },
      /** 去掉空值，避免拼出一堆空参数（后端 DTO 也不必逐个判空） */
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

  .escalation-search {
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
        width: 92px;
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

    &__range {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    &__range-sep {
      flex: none;
      color: #94a3b8;
    }

    &__range-unit {
      flex: none;
      font-size: 12px;
      color: #94a3b8;
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
