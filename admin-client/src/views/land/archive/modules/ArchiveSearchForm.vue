<template>
  <div class="archive-search">
    <a-form layout="inline" class="archive-search__form">
      <a-row :gutter="16" type="flex">
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="配套项目">
            <a-input v-model="query.ptxmmc" placeholder="项目名称，模糊匹配" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="出让宗地编号">
            <a-input v-model="query.crzdbh" placeholder="如 津西体（挂）2023-003" allowClear @pressEnter="handleSearch" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="档案类别">
            <category-picker
              ref="category"
              :value="query.categoryId"
              :leafOnly="false"
              placeholder="可选父类别（含子类）"
              @change="handleCategoryChange" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12" :md="8" :lg="6">
          <a-form-item label="归档日期">
            <a-range-picker
              v-model="dateRange"
              style="width: 100%"
              valueFormat="YYYY-MM-DD"
              :placeholder="['开始日期', '结束日期']"
              @change="handleDateChange" />
          </a-form-item>
        </a-col>

        <!-- ============ 展开后的更多条件 ============ -->
        <template v-if="expanded">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="档案号">
              <a-input v-model="query.archiveNo" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="档案名称">
              <a-input v-model="query.archiveName" placeholder="案卷题名，模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="文件名">
              <a-input v-model="query.fileName" placeholder="卷内文件名，模糊匹配" allowClear @pressEnter="handleSearch" />
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
            <a-form-item label="档案年度">
              <a-select v-model="query.archiveYear" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="year in yearOptions" :key="year" :value="year">{{ year }} 年</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="责任部门">
              <a-select v-model="query.responsibleDept" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in depts" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="配套负责人">
              <a-input v-model="query.responsibleUser" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="状态">
              <a-select v-model="query.status" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in statuses" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="密级">
              <a-select v-model="query.secretLevel" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="来源">
              <a-select v-model="query.sourceType" allowClear placeholder="全部" style="width: 100%">
                <a-select-option value="manual">手工录入</a-select-option>
                <a-select-option value="doc_receive">收文归档</a-select-option>
                <a-select-option value="doc_send">发文归档</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </template>

        <a-col :xs="24" class="archive-search__actions">
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
  import CategoryPicker from './CategoryPicker'
  import { queryXzqhOptions } from '@/api/land/landData'

  /**
   * 档案查询条件面板（对应方案 2.3.2 第 3 项）
   *
   * 原文要求：「各配套负责人可以根据项目名称、时间、档案类型等进行数据检索」。
   * 这里把设计文档 6.3.3 列出的 11 个条件都实现了：
   * 项目名称 / 宗地编号 / 档案类别 / 时间范围 / 档案年度 / 行政区划 /
   * 责任部门 / 配套负责人 / 状态 / 密级 / 文件名。
   *
   * 默认只显示 4 个最常用的条件，点「更多条件」展开其余，
   * 避免档案维护页首屏被表单占满；档案查询页可以传 simple=false 时默认展开。
   */
  export default {
    name: 'ArchiveSearchForm',
    components: { CategoryPicker },
    props: {
      /** 是否默认展开全部条件 */
      defaultExpanded: {
        type: Boolean,
        default: false
      },
      /**
       * 初始条件（例如从档案统计页跳转过来时带的 ptxmmc）。
       * 只在 created 时应用一次，之后完全由本组件自己维护。
       */
      initialQuery: {
        type: Object,
        default: () => ({})
      }
    },
    data () {
      const now = new Date().getFullYear()
      const years = []
      for (let y = now; y >= now - 12; y--) {
        years.push(y)
      }
      return {
        expanded: this.defaultExpanded,
        dateRange: [],
        query: Object.assign(this.buildEmptyQuery(), this.initialQuery || {}),
        xzqhOptions: [],
        yearOptions: years,
        depts: ['发改（行政审批）', '规划', '财政', '住建', '其他'],
        statuses: ['未归档', '归档中', '审核中', '已归档'],
        secretLevels: ['一般', '内部', '秘密', '机密']
      }
    },
    created () {
      this.loadXzqh()
      // 初始条件里可能带了日期区间，同步到 range picker 的显示
      if (this.query.beginDate && this.query.endDate) {
        this.dateRange = [this.query.beginDate, this.query.endDate]
      }
      // 日期区间用两个独立字段存储，初始值需要还原成 range picker 的数组
      if (this.query.archived === 'true') {
        this.query.archived = true
      } else if (this.query.archived === 'false') {
        this.query.archived = false
      }
    },
    methods: {
      buildEmptyQuery () {
        return {
          archiveNo: '',
          archiveName: '',
          ptxmmc: '',
          crzdbh: '',
          dkmc: '',
          fileName: '',
          categoryId: undefined,
          categoryPath: undefined,
          xzqh: undefined,
          archiveYear: undefined,
          responsibleDept: undefined,
          responsibleUser: '',
          status: undefined,
          secretLevel: undefined,
          sourceType: undefined,
          beginDate: undefined,
          endDate: undefined
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        })
      },
      handleCategoryChange (categoryId) {
        this.query.categoryId = categoryId
        const node = this.$refs.category ? this.$refs.category.findNode(categoryId) : null
        this.query.categoryPath = node ? node.path : undefined
      },
      handleDateChange (values) {
        if (values && values.length === 2) {
          this.query.beginDate = values[0]
          this.query.endDate = values[1]
        } else {
          this.query.beginDate = undefined
          this.query.endDate = undefined
        }
      },
      handleSearch () {
        this.$emit('search', this.normalize(this.query))
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.dateRange = []
        if (this.$refs.category) {
          // tree-select 是受控的，重置后要把内部值一起清掉
          this.$refs.category.handleChange(undefined)
        }
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
      /** 供父组件读取当前条件（导出时用） */
      getQuery () {
        return this.normalize(this.query)
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .archive-search {
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

    &__actions {
      text-align: right;
      margin-bottom: 16px;

      /deep/ .ant-btn {
        margin-left: 8px;
      }
    }
  }
</style>
