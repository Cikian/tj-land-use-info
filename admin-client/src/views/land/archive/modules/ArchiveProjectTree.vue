<template>
  <!--
    P2-07 档案维护「项目视图」：项目 → 案卷 → 材料类别 → 文件
    --------------------------------
    为什么用 a-table + expandedRowRender 而不是自己抽一套树：
      第一层要「像列表一样」比较（列宽对齐、数字右对齐、分页器同一个），
      第二/三层却是「案卷 / 文件」这种自由排版，表格装不下。
      用 a-table 承载第一层、展开区承载下面几层，既保住列表的可比性，
      又不必为树另造一套分页、空态与 loading。
    为什么不给 a-table 加 scroll.x：antd 1.7 里 expandedRowRender 与 scroll.x
      同时出现会在控制台告警（列宽会算错），展开区会被横向裁掉。
      因此列宽固定、窄屏靠「项目名 + 宗地编号」两行折行，不加横向滚动。
  -->
  <div class="project-tree">
    <a-table
      :rowKey="'projectKey'"
      :columns="columns"
      :dataSource="dataSource"
      :loading="loading"
      :pagination="pagination"
      :expandedRowKeys="expandedKeys"
      :customRow="customRow"
      size="middle"
      @change="handleTableChange"
      @expand="handleExpand">
      <!-- 第一层：项目名 + 宗地编号 / 行政区（点名字也能展开，不必非要点左侧小箭头） -->
      <template slot="projectName" slot-scope="text, record">
        <a class="project-tree__name" :title="text || '未关联项目'" @click="handleToggle(record)">
          {{ text || '未关联项目' }}
        </a>
        <div v-if="record.crzdbh || record.xzqh" class="project-tree__meta">
          <span v-if="record.crzdbh" :title="record.crzdbh">{{ record.crzdbh }}</span>
          <span v-if="record.xzqh">{{ record.xzqh }}</span>
        </div>
      </template>

      <!-- 材料类别摘要：多类用「、」连接，超过 3 类折成「等 N 类」，完整值放 title -->
      <template slot="categoryNames" slot-scope="text, record">
        <span v-if="categorySummary(record)" class="project-tree__category" :title="text">
          {{ categorySummary(record) }}
        </span>
        <span v-else class="project-tree__muted">未分类</span>
      </template>

      <template slot="archiveCount" slot-scope="text, record">
        <span class="project-tree__num">{{ record.archiveCount || 0 }}</span>
        <span class="project-tree__unit">卷</span>
      </template>

      <template slot="fileCount" slot-scope="text">
        <span class="project-tree__num">{{ text || 0 }}</span>
        <span class="project-tree__unit">个</span>
      </template>

      <template slot="totalSize" slot-scope="text">
        <span class="project-tree__num">{{ formatSize(text) }}</span>
      </template>

      <template slot="status" slot-scope="text, record">
        <a-tag :color="projectStatus(record).color">{{ projectStatus(record).text }}</a-tag>
      </template>

      <template slot="action" slot-scope="text, record">
        <a v-has="'land:archive:export'" @click="$emit('export-project', record)">导出该项目</a>
      </template>

      <!-- 展开区：案卷（第二层）→ 材料类别（第三层）→ 文件（第四层） -->
      <template slot="expandedRowRender" slot-scope="record">
        <div class="project-tree__panel">
          <a-spin :spinning="!!caseLoading[record.projectKey]" size="small">
            <a-empty
              v-if="!caseLoading[record.projectKey] && !(caseFiles[record.projectKey] || []).length"
              :description="emptyCaseText"
              class="project-tree__empty" />

            <section
              v-for="caseFile in caseFiles[record.projectKey] || []"
              :key="caseFile.id"
              class="project-tree__case">
              <header class="project-tree__case-head">
                <a
                  class="project-tree__case-title"
                  :title="caseTitle(caseFile)"
                  @click="handleDetail(caseFile)">
                  {{ caseFile.archiveNo || '（无档案号）' }} {{ caseFile.archiveName || '' }}
                </a>
                <a-tag :color="caseStatusColor(caseFile.status)">{{ caseFile.status || '—' }}</a-tag>
                <span class="project-tree__muted">
                  {{ (caseFile.files || []).length }} 个文件 · {{ formatSize(caseSize(caseFile)) }}
                </span>
                <a class="project-tree__link" @click="handleDetail(caseFile)">详情</a>
              </header>

              <div
                v-for="group in groupFilesByCategory(caseFile)"
                :key="group.name"
                class="project-tree__group">
                <span class="project-tree__group-name" :title="group.name">{{ group.name }}</span>
                <span class="project-tree__group-count">{{ group.files.length }}</span>
                <ul class="project-tree__files">
                  <li v-for="file in group.files" :key="file.id" :title="file.fileName">
                    <span class="project-tree__file-name">{{ file.fileName }}</span>
                    <em class="project-tree__file-size">{{ formatSize(file.fileSize) }}</em>
                  </li>
                </ul>
              </div>

              <p v-if="!(caseFile.files || []).length" class="project-tree__muted">该案卷还没有卷内文件</p>
            </section>
          </a-spin>
        </div>
      </template>
    </a-table>
  </div>
</template>

<script>
  import { queryArchiveProjectCaseFiles } from '@/api/land/archive'

  /** 材料类别摘要最多显示几类，超出折成「等 N 类」（N 用后端的 categoryCount） */
  const CATEGORY_LIMIT = 3

  /**
   * 从事件目标向上找到命中条件的节点。
   * 不用 Element.closest：本工程的 browserslist 仍包含 IE11，它没有 closest。
   */
  function hitTest (event, test) {
    let node = event ? event.target : null
    while (node && node.classList) {
      if (test(node)) {
        return true
      }
      node = node.parentNode
    }
    return false
  }

  /** 点的是表格自带的展开三角吗（它自己已切换过，行点击再切一次会「展开又立刻收起」） */
  function isExpandIconClick (event) {
    return hitTest(event, node => node.classList.contains('ant-table-row-expand-icon'))
  }

  /** 点的是行内链接吗（项目名 = 切换展开、导出该项目 = 下载，都有各自的动作） */
  function isLinkClick (event) {
    return hitTest(event, node => node.tagName === 'A')
  }

  /**
   * 档案 · 项目视图（P2-07）
   *
   * 三层结构：项目（表格行）→ 案卷（展开区）→ 材料类别 → 文件。
   *
   * 几个刻意的取舍：
   *  1. **分页在父组件按项目算**：本组件只把页码变化往上抛，total 由后端返回的项目总数决定。
   *     （用案卷数当 total 会出现「翻到第 3 页是空的」——后端注释里也写了这个坑。）
   *  2. **案卷懒加载**：一个项目可能挂着十几卷、上百个文件，首屏把整页项目的案卷都拉下来
   *     会明显拖慢接口与渲染，而用户一次只关心一两个项目。
   *  3. **按 projectKey 缓存**：收起再展开不重复请求；但父组件一刷新列表（检索 / 翻页 /
   *     保存后），项目行的文件数与总大小都会变，缓存必须同步作废，否则「上面数字是新的、
   *     展开里还是旧案卷」。
   *  4. **不做前端再聚合**：/list 全量拉回来自己分组在数据量上撑不住，聚合口径只在后端一处。
   */
  export default {
    name: 'ArchiveProjectTree',
    props: {
      /** 项目行（后端 /land/archive/projectPage 的 records） */
      dataSource: {
        type: Array,
        default: () => []
      },
      loading: {
        type: Boolean,
        default: false
      },
      /** 项目口径的分页器（total = 项目总数） */
      pagination: {
        type: [Object, Boolean],
        default: () => ({})
      },
      /** 当前生效的检索条件，原样带给 /projectCaseFiles，保证「项目行」与「展开内容」同一口径 */
      query: {
        type: Object,
        default: () => ({})
      }
    },
    data () {
      return {
        /** 已展开的项目 key（受控，和 a-table 的 expandedRowKeys 双向对齐） */
        expandedKeys: [],
        /** projectKey → 案卷数组（含各自卷内文件） */
        caseFiles: {},
        /** projectKey → 是否正在加载案卷 */
        caseLoading: {},
        columns: [
          { title: '项目', dataIndex: 'ptxmmc', width: 260, scopedSlots: { customRender: 'projectName' } },
          { title: '材料类别', dataIndex: 'categoryNames', width: 300, scopedSlots: { customRender: 'categoryNames' } },
          { title: '案卷数', dataIndex: 'archiveCount', width: 96, align: 'right', scopedSlots: { customRender: 'archiveCount' } },
          { title: '文件数', dataIndex: 'fileCount', width: 96, align: 'right', scopedSlots: { customRender: 'fileCount' } },
          { title: '总大小', dataIndex: 'totalSize', width: 110, align: 'right', scopedSlots: { customRender: 'totalSize' } },
          { title: '状态', key: 'status', width: 120, scopedSlots: { customRender: 'status' } },
          { title: '操作', width: 110, scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    computed: {
      /** 有筛选条件时的空态要说「没有符合条件的」，否则用户会以为数据丢了 */
      emptyCaseText () {
        const hasFilter = Object.keys(this.query || {}).length > 0
        return hasFilter ? '该项目下没有符合当前条件的案卷' : '该项目下还没有案卷'
      }
    },
    watch: {
      /**
       * 列表被重新拉取（检索 / 翻页 / 新增或删除后刷新）时，已展开项目的案卷缓存必然过期。
       * 这里只重拉「当前页仍然展开」的项目（通常 1~2 个），不做整页预取。
       */
      dataSource () {
        this.caseFiles = {}
        this.caseLoading = {}
        this.dataSource.forEach(row => {
          const key = row && row.projectKey
          if (key && this.expandedKeys.indexOf(key) !== -1) {
            this.loadCases(key)
          }
        })
      }
    },
    methods: {
      /** 字节数转人类可读（KB/MB/GB）；口径与 ArchiveTable / 详情弹窗保持一致 */
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '0 B'
        }
        const units = ['B', 'KB', 'MB', 'GB', 'TB']
        let size = value
        let unit = 0
        while (size >= 1024 && unit < units.length - 1) {
          size /= 1024
          unit++
        }
        return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(1)} ${units[unit]}`
      },
      /**
       * 材料类别摘要。
       * 后端的 categoryNames 已经用「、」拼好，这里只负责**长度可控**：
       * 超过 CATEGORY_LIMIT 类就折成「前 3 类 等 N 类」，完整值挂在 title 上看得到。
       * N 取后端的 categoryCount（不是 split 出来的长度），因为类别名里也可能含「、」。
       */
      categorySummary (record) {
        const text = (record && record.categoryNames) || ''
        if (!text) {
          return ''
        }
        const names = text.split('、').filter(name => name)
        if (names.length <= CATEGORY_LIMIT) {
          return names.join('、')
        }
        const total = Number(record.categoryCount) || names.length
        return `${names.slice(0, CATEGORY_LIMIT).join('、')} 等 ${total} 类`
      },
      /**
       * 项目行的「状态」。
       *
       * ★ 后端 ArchiveProjectVO 没有项目级状态列，只给了 archivedCount / archiveCount，
       *   所以这里说的是「已归档卷数 / 总卷数」，不虚构一个项目状态字段，
       *   也不把多卷的不同状态合并成一句假状态。
       */
      projectStatus (record) {
        const total = Number(record && record.archiveCount) || 0
        const archived = Number(record && record.archivedCount) || 0
        if (!total) {
          return { text: '—', color: undefined }
        }
        if (archived >= total) {
          return { text: '已归档', color: 'green' }
        }
        if (archived > 0) {
          return { text: `归档中 ${archived}/${total}`, color: 'blue' }
        }
        return { text: '未归档', color: undefined }
      },
      caseStatusColor (status) {
        if (status === '已归档') {
          return 'green'
        }
        if (status === '审核中') {
          return 'orange'
        }
        if (status === '归档中') {
          return 'blue'
        }
        // 兜底返回 undefined：'default' 不是 antd 预设色，会被当自定义色 → 白底白字
        return undefined
      },
      caseTitle (caseFile) {
        return `${caseFile.archiveNo || ''} ${caseFile.archiveName || ''}`.trim()
      },
      /** 一份案卷的卷内文件总大小（项目行的总大小含全部卷，单卷要自己算） */
      caseSize (caseFile) {
        return ((caseFile && caseFile.files) || []).reduce((sum, file) => sum + (Number(file.fileSize) || 0), 0)
      },
      /** 把一份案卷的卷内文件按「材料类别」分组 —— 类别挂在文件上，这是第三层的分组依据 */
      groupFilesByCategory (caseFile) {
        const files = (caseFile && caseFile.files) || []
        const groups = []
        const indexByName = {}
        files.forEach(file => {
          const name = file.categoryName || '未分类'
          if (indexByName[name] === undefined) {
            indexByName[name] = groups.length
            groups.push({ name, files: [] })
          }
          groups[indexByName[name]].files.push(file)
        })
        return groups
      },
      handleTableChange (pagination) {
        this.$emit('change', pagination)
      },
      /** 整行可点：点任意一格都展开/收起该项目的案卷（需求「点项目行查看该项目下案卷」） */
      customRow (record) {
        return {
          on: {
            click: event => this.handleRowClick(record, event)
          }
        }
      },
      handleRowClick (record, event) {
        // 展开三角与行内链接各有自己的动作，行点击不要重复触发
        if (isExpandIconClick(event) || isLinkClick(event)) {
          return
        }
        this.handleToggle(record)
      },
      /**
       * a-table 的展开/收起（受控）。
       * 展开时若没有缓存过案卷就懒加载；收起只摘 key，不清缓存。
       */
      handleExpand (expanded, record) {
        const key = record && record.projectKey
        if (!key) {
          return
        }
        if (expanded) {
          if (this.expandedKeys.indexOf(key) === -1) {
            this.expandedKeys = this.expandedKeys.concat([key])
          }
          if (!this.caseFiles[key]) {
            this.loadCases(key)
          }
          return
        }
        this.expandedKeys = this.expandedKeys.filter(item => item !== key)
      },
      /** 点项目名展开/收起（等价于点左侧的展开箭头） */
      handleToggle (record) {
        const key = record && record.projectKey
        if (!key) {
          return
        }
        this.handleExpand(this.expandedKeys.indexOf(key) === -1, record)
      },
      /**
       * 拉某个项目的案卷。
       *
       * 并发控制：同一个 projectKey 正在请求时直接返回。
       * 否则「连点展开箭头」「收起又立刻展开」会各发一次请求，
       * 慢响应还可能乱序覆盖（后到的旧结果盖掉新结果）。
       */
      loadCases (projectKey) {
        if (this.caseLoading[projectKey]) {
          return Promise.resolve()
        }
        // Vue 2 里给响应式对象新增键必须走 $set，直接赋值不会触发渲染
        this.$set(this.caseLoading, projectKey, true)
        return queryArchiveProjectCaseFiles(this.query, projectKey).then(res => {
          if (!res || !res.success) {
            this.$message.warning((res && res.message) || '该项目的案卷加载失败')
            return
          }
          this.$set(this.caseFiles, projectKey, res.result || [])
        }).catch(() => {
          this.$message.warning('该项目的案卷加载失败，请稍后重试')
        }).finally(() => {
          this.$set(this.caseLoading, projectKey, false)
        })
      },
      handleDetail (caseFile) {
        if (!caseFile || !caseFile.id) {
          return
        }
        this.$emit('detail', caseFile)
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .project-tree {
    /* 整行可点展开：行是 antd 自己渲染的 tr，scoped 选择器够不着，只能走 /deep/。
       只给主行（.ant-table-row）加，展开出来的内容行不加，否则会被误以为也能点。 */
    /deep/ tbody > tr.ant-table-row {
      cursor: pointer;
    }

    &__name {
      display: block;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 500;
      color: #0f172a;
      cursor: pointer;

      &:hover {
        color: #1d4ed8;
      }
    }

    &__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      font-size: 12px;
      color: #94a3b8;

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 180px;
      }
    }

    &__category {
      display: block;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #334155;
    }

    &__num {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #0f172a;
    }

    &__unit {
      margin-left: 2px;
      font-size: 12px;
      color: #94a3b8;
    }

    &__muted {
      font-size: 12px;
      color: #94a3b8;
    }

    &__empty {
      padding: 12px 0;
    }

    /* ---------------- 展开区 ---------------- */
    &__panel {
      padding: 4px 8px 8px;
    }

    &__case {
      padding: 8px 12px;
      border: 1px solid @border-color;
      border-radius: 6px;

      & + & {
        margin-top: 8px;
      }
    }

    &__case-head {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      padding-bottom: 6px;
      border-bottom: 1px dashed @border-color;
    }

    &__case-title {
      min-width: 0;
      max-width: 460px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #1d4ed8;
    }

    &__link {
      margin-left: auto;
    }

    /* 材料类别（第三层）：左侧竖线代替缩进，比加编号更省空间 */
    &__group {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 4px 8px;
      margin-top: 6px;
      padding-left: 10px;
      border-left: 2px solid @border-color;
    }

    &__group-name {
      flex: 0 0 auto;
      max-width: 240px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
      color: #334155;
    }

    &__group-count {
      flex: 0 0 auto;
      font-size: 12px;
      color: #1d4ed8;
    }

    /* 文件（第四层）：横向排、超出换行，避免展开区越拉越长 */
    &__files {
      display: flex;
      flex-wrap: wrap;
      gap: 2px 16px;
      flex: 1 1 auto;
      min-width: 0;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: inline-flex;
        align-items: baseline;
        gap: 5px;
        max-width: 300px;
        font-size: 12px;
        color: #475569;
      }
    }

    &__file-name {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__file-size {
      flex: 0 0 auto;
      font-style: normal;
      color: #94a3b8;
    }
  }
</style>
