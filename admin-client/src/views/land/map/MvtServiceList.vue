<template>
  <div class="mvt-services">
    <a-card :bordered="false">
      <div class="toolbar-scroll">
        <div class="catalog-toolbar">
          <div class="toolbar-field source-field">
            <label>数据库</label>
            <a-select v-model="source" aria-label="数据库" :loading="sourcesLoading" placeholder="请选择数据库" @change="onSourceChange">
              <a-select-option value="__all__">全部</a-select-option>
              <a-select-option v-for="item in sources" :key="item.code" :value="item.code">
                {{ item.name }}（{{ item.dbName || item.code }}）
              </a-select-option>
            </a-select>
          </div>
          <div class="toolbar-field keyword-field">
            <label for="mvt-keyword">关键字</label>
            <a-input id="mvt-keyword" v-model="search" placeholder="表名、schema、几何列" allow-clear @pressEnter="searchQuery" />
          </div>
          <div class="toolbar-actions">
            <a-button type="primary" icon="search" @click="searchQuery">查询</a-button>
            <a-button icon="reload" @click="searchReset">重置</a-button>
            <span class="toolbar-divider" />
            <a-button type="primary" icon="plus" @click="addDatabase">添加数据库</a-button>
            <a-button icon="reload" :disabled="!source" :loading="loading" @click="loadTables(true)">刷新发现</a-button>
            <a-button type="danger" icon="delete" :disabled="!checkedCodes.length" @click="removeCheckedSources">移除勾选库</a-button>
          </div>
        </div>
      </div>
      <a-alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 16px" />
      <a-spin :spinning="loading || sourcesLoading">
        <div class="catalog-content">
          <div v-if="pagedTables.length" class="service-grid">
            <a-card v-for="record in pagedTables" :key="rowKey(record)" class="service-card" :body-style="{ padding: 0 }" hoverable>
              <div class="service-card-main">
                <div class="service-card-heading">
                  <a-checkbox class="service-check" :checked="checkedCodes.includes(record.sourceCode)" @change="e => toggleChecked(record.sourceCode, e.target.checked)" />
                  <span class="service-icon"><a-icon :type="record.kind === 'v' || record.kind === 'm' ? 'profile' : 'database'" /></span>
                  <div class="service-name">
                    <a class="service-title" :title="record.table" @click="openDetails(record)">{{ record.table }}</a>
                    <div class="service-schema">{{ record.schema }}</div>
                  </div>
                  <a-tag color="blue">{{ record.sourceName }}</a-tag>
                  <a-tag>{{ kindLabel(record.kind) }}</a-tag>
                </div>
                <p class="service-description" :title="record.comment || ''">{{ record.comment || '暂无描述' }}</p>
                <div class="service-meta">
                  <div><span>几何列</span><strong :title="record.geometry">{{ record.geometry }}</strong></div>
                  <div><span>几何类型</span><strong :title="record.geometryType">{{ record.geometryType }}</strong></div>
                </div>
                <div class="service-status">
                  <a-tag :color="record.available ? 'green' : 'orange'">{{ record.available ? 'SRID ' + record.srid : '待声明坐标系' }}</a-tag>
                  <span>{{ record.geography ? 'geography' : 'geometry' }}</span>
                </div>
              </div>
              <div class="service-card-actions">
                <a-button type="link" icon="profile" @click="openDetails(record)">查看详情</a-button>
                <a-button type="link" icon="copy" :disabled="!record.available" @click="copy(tileJsonUrl(record))">复制地址</a-button>
              </div>
            </a-card>
          </div>
          <a-empty v-else-if="!loading && !sourcesLoading" :description="emptyDescription" />
        </div>
      </a-spin>
      <a-pagination
        v-if="filteredTables.length"
        class="catalog-pagination"
        :current="currentPage"
        :page-size="pageSize"
        :total="filteredTables.length"
        :page-size-options="['12', '24', '48']"
        show-size-changer
        :show-total="total => `共 ${total} 个图层`"
        @change="changePage"
        @showSizeChange="changePageSize"
      />
    </a-card>
    <a-drawer title="服务详情" width="640" :visible="detailVisible" @close="detailVisible = false">
      <div v-if="selectedTable" class="service-detail">
        <h2>{{ selectedTable.table }}</h2>
        <a-tag :color="selectedTable.available ? 'green' : 'orange'">{{ selectedTable.available ? 'SRID ' + selectedTable.srid : '待声明坐标系' }}</a-tag>
        <a-tag>{{ kindLabel(selectedTable.kind) }}</a-tag>
        <a-descriptions bordered size="small" :column="1" class="detail-metadata">
          <a-descriptions-item label="数据库">{{ sourceName }}</a-descriptions-item>
          <a-descriptions-item label="Schema">{{ selectedTable.schema }}</a-descriptions-item>
          <a-descriptions-item label="表名">{{ selectedTable.table }}</a-descriptions-item>
          <a-descriptions-item label="几何列">{{ selectedTable.geometry }}</a-descriptions-item>
          <a-descriptions-item label="几何类型">{{ selectedTable.geometryType }}</a-descriptions-item>
          <a-descriptions-item label="存储类型">{{ selectedTable.geography ? 'geography' : 'geometry' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ selectedTable.comment || '暂无描述' }}</a-descriptions-item>
        </a-descriptions>
        <template v-if="selectedTable.available">
          <div class="detail-address">
            <div class="detail-address-heading">
              <h3>TileJSON 地址</h3>
              <a-button type="link" icon="copy" @click="copy(tileJsonUrl(selectedTable))">复制</a-button>
            </div>
            <a :href="tileJsonUrl(selectedTable)" target="_blank" rel="noopener noreferrer">{{ tileJsonUrl(selectedTable) }}</a>
          </div>
          <div class="detail-address">
            <div class="detail-address-heading">
              <h3>XYZ 瓦片地址</h3>
              <a-button type="link" icon="copy" @click="copy(tileUrl(selectedTable))">复制</a-button>
            </div>
            <code>{{ tileUrl(selectedTable) }}</code>
          </div>
        </template>
        <a-alert v-else type="warning" show-icon :message="selectedTable.reason || '请先声明有效坐标系'" class="detail-metadata" />
      </div>
    </a-drawer>
    <sys-data-source-modal ref="sourceModal" @ok="loadSources" />
    <shp-import-modal ref="shpModal" @success="onShpImported" />
  </div>
</template>

<script>
import { getMvtSources, getMvtTables, removeMvtSource } from '@/api/land/mvt'
import SysDataSourceModal from '@/views/system/modules/SysDataSourceModal'
import ShpImportModal from '@/views/land/map/ShpImportModal'

export default {
  name: 'MvtServiceList',
  components: { SysDataSourceModal, ShpImportModal },
  data() {
    return {
      sources: [],
      source: '__all__',
      checkedCodes: [],
      tables: [],
      search: '',
      appliedSearch: '',
      currentPage: 1,
      pageSize: 12,
      selectedTable: null,
      detailVisible: false,
      error: '',
      sourcesLoading: false,
      loading: false,
      requestSequence: 0,
      serviceBase: new URL(window._CONFIG.domianURL, window.location.origin).href.replace(/\/$/, '')
    }
  },
  computed: {
    sourceName() {
      const current = this.sources.find(item => item.code === this.source)
      return current ? `${current.name}（${current.dbName || current.code}）` : this.source
    },
    filteredTables() {
      const query = this.appliedSearch.trim().toLowerCase()
      return this.tables.filter(row => [row.schema, row.table, row.geometry].join(' ').toLowerCase().includes(query))
    },
    pagedTables() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.filteredTables.slice(start, start + this.pageSize)
    },
    emptyDescription() {
      if (!this.sources.length) return '尚无 PostgreSQL 连接，请先添加数据库'
      if (!this.source) return '请选择数据库'
      if (this.error) return '目录读取失败，请检查连接后刷新'
      return this.appliedSearch ? '没有匹配的空间表' : '未发现空间表，请确认该库已安装 PostGIS 且连接账号有读取权限'
    }
  },
  created() {
    this.loadSources()
  },
  beforeDestroy() { this.requestSequence++ },
  methods: {
    searchQuery() { this.appliedSearch = this.search; this.currentPage = 1 },
    searchReset() { this.search = ''; this.appliedSearch = ''; this.currentPage = 1 },
    changePage(page) { this.currentPage = page },
    changePageSize(page, size) { this.pageSize = size; this.currentPage = 1 },
    openDetails(record) { this.selectedTable = record; this.detailVisible = true },
    rowKey(row) { return [row.schema, row.table, row.geometry].join('\u0000') },
    kindLabel(kind) { return { r: '空间表', v: '视图', m: '物化视图', p: '分区表' }[kind] || kind },
    tileJsonUrl(row) {
      return this.serviceBase.trim().replace(/\/+$/, '') + '/land/mvt/public/' +
        [this.source, row.schema, row.table, row.geometry].map(encodeURIComponent).join('/')
    },
    tileUrl(row) { return this.tileJsonUrl(row) + '/{z}/{x}/{y}.pbf' },
    async loadSources() {
      this.sourcesLoading = true
      this.error = ''
      try {
        const result = await getMvtSources()
        if (!result.success) throw new Error(result.message || '数据库列表读取失败')
        this.sources = result.result || []
        if (!this.sources.some(row => row.code === this.source)) this.source = '__all__'
        this.checkedCodes = []
        await this.loadTables(true)
      } catch (e) { this.error = e.message || '数据库列表读取失败' } finally { this.sourcesLoading = false }
    },
    async loadTables(refresh) {
      const sequence = ++this.requestSequence
      this.detailVisible = false
      this.selectedTable = null
      this.currentPage = 1
      this.tables = []
      this.error = ''
      if (!this.source) { this.loading = false; return }
      this.loading = true
      try {
        if (this.source === '__all__') {
          // 全部：逐库聚合（单库失败不影响其它），并给每行打上所属库标注
          let merged = []
          for (const src of this.sources) {
            try {
              const result = await getMvtTables(src.code, refresh)
              if (sequence !== this.requestSequence) return
              if (result.success) {
                merged = merged.concat((result.result || []).map(row => ({
                  ...row, sourceCode: src.code, sourceName: src.name || src.code
                })))
              }
            } catch (e) { /* 单库失败跳过 */ }
          }
          if (sequence !== this.requestSequence) return
          this.tables = merged
        } else {
          const result = await getMvtTables(this.source, refresh)
          if (sequence !== this.requestSequence) return
          if (!result.success) throw new Error(result.message || '空间目录读取失败')
          const src = this.sources.find(item => item.code === this.source) || {}
          this.tables = (result.result || []).map(row => ({ ...row, sourceCode: this.source, sourceName: src.name || this.source }))
        }
      } catch (e) {
        if (sequence === this.requestSequence) this.error = e.message || '空间目录读取失败'
      } finally { if (sequence === this.requestSequence) this.loading = false }
    },
    /** 卡片复选框：勾选即选中该库（用于批量移除） */
    toggleChecked(code, checked) {
      const set = new Set(this.checkedCodes)
      if (checked) set.add(code)
      else set.delete(code)
      this.checkedCodes = [...set]
    },
    /** 数据库下拉切换时清空勾选（跨库勾选容易误删） */
    onSourceChange() {
      this.checkedCodes = []
      this.loadTables(true)
    },
    /** 批量移除勾选的库（不删除 PG 库本身），并刷新下拉与表目录 */
    removeCheckedSources() {
      if (!this.checkedCodes.length) { this.$message.warning('请先勾选要移除的数据库卡片'); return }
      const targets = this.sources.filter(item => this.checkedCodes.includes(item.code))
      this.$confirm({
        title: '移除数据库',
        content: `将 ${targets.map(t => `"${t.name}（${t.code}）"`).join('、')} 从 MVT 管理清单移除？PG 库本身不会被删除。`,
        okText: '移除',
        okType: 'danger',
        onOk: async () => {
          for (const target of targets) {
            try { await removeMvtSource(target.id) } catch (e) { this.$message.error(`${target.code} 移除失败`) }
          }
          this.$message.success('已移除勾选的数据库')
          this.checkedCodes = []
          this.tables = []
          await this.loadSources()
        }
      })
    },
    addDatabase() {
      // 一体化流程：表单里上传 SHP 压缩包，提交后自动建库、入 PostGIS、注册数据源
      this.$refs.shpModal.open()
    },
    onShpImported() {
      // 新库已注册为数据源，刷新"数据库"下拉与表目录
      this.loadSources()
    },
    async copy(text) {
      try {
        if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text)
        else this.copyFallback(text)
        this.$message.success('服务地址已复制')
      } catch (e) {
        try { this.copyFallback(text); this.$message.success('服务地址已复制') } catch (error) { this.$message.error('复制失败，请选中服务地址手动复制') }
      }
    },
    copyFallback(text) {
      const input = document.createElement('textarea')
      input.value = text
      input.style.cssText = 'position:fixed;left:-9999px;top:0'
      document.body.appendChild(input)
      try { input.select(); if (!document.execCommand('copy')) throw new Error('复制失败') } finally { document.body.removeChild(input) }
    }
  }
}
</script>

<style lang="less" scoped>
.mvt-services {
  .toolbar-scroll { overflow-x: auto; margin-bottom: 24px; }
  .catalog-toolbar { display: flex; align-items: center; gap: 16px; min-width: 940px; padding-bottom: 2px; }
  .toolbar-field { display: flex; align-items: center; gap: 12px; min-width: 0; }
  .toolbar-field label { white-space: nowrap; color: rgba(0, 0, 0, 0.85); }
  .source-field { flex: 1.25; max-width: 520px; }
  .keyword-field { flex: 1; max-width: 420px; }
  .toolbar-field .ant-select, .toolbar-field .ant-input-affix-wrapper { flex: 1; min-width: 0; }
  .toolbar-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; margin-left: auto; }
  .toolbar-divider { height: 20px; border-left: 1px solid #e8e8e8; margin: 0 4px; }
  .catalog-content { min-height: 272px; }
  .service-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
  .service-card { min-width: 0; border-radius: 4px; overflow: hidden; }
  .service-card:hover { border-color: #91d5ff; }
  .service-card-main { padding: 20px 20px 16px; }
  .service-card-heading { display: flex; align-items: center; gap: 12px; }
  .service-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 40px; height: 40px; border-radius: 4px; background: #e6f7ff; color: #1890ff; font-size: 21px; }
  .service-name { flex: 1; min-width: 0; }
  .service-title { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; color: rgba(0, 0, 0, 0.85); font-size: 16px; font-weight: 500; }
  .service-title:hover { color: #1890ff; }
  .service-schema { margin-top: 3px; font-size: 12px; color: rgba(0, 0, 0, 0.45); overflow-wrap: anywhere; }
  .service-card-heading .ant-tag { flex-shrink: 0; margin-right: 0; }
  .service-description { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; height: 44px; margin: 18px 0; line-height: 22px; color: rgba(0, 0, 0, 0.45); overflow-wrap: anywhere; }
  .service-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .service-meta > div { min-width: 0; }
  .service-meta span { display: block; margin-bottom: 4px; font-size: 12px; color: rgba(0, 0, 0, 0.45); }
  .service-meta strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 400; color: rgba(0, 0, 0, 0.85); }
  .service-status { display: flex; align-items: center; margin-top: 18px; font-size: 12px; color: rgba(0, 0, 0, 0.45); }
  .service-card-actions { display: flex; padding: 8px 0; border-top: 1px solid #e8e8e8; background: #fafafa; }
  .service-card-actions .ant-btn { flex: 1; height: 28px; }
  .service-card-actions .ant-btn + .ant-btn { border-left: 1px solid #e8e8e8; border-radius: 0; }
  .catalog-pagination { display: flex; justify-content: flex-end; margin-top: 24px; }
  .ant-empty { padding: 48px 0; }
}

.service-detail {
  h2 { margin-bottom: 12px; font-size: 20px; color: rgba(0, 0, 0, 0.85); overflow-wrap: anywhere; }
  .detail-metadata { margin-top: 24px; }
  /deep/ .ant-descriptions-item-label { width: 104px; white-space: nowrap; }
  /deep/ .ant-descriptions-item-content { overflow-wrap: anywhere; }
  .detail-address { margin-top: 24px; padding: 16px; border: 1px solid #e8e8e8; border-radius: 4px; background: #fafafa; }
  .detail-address-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
  h3 { margin: 0; font-size: 14px; font-weight: 500; }
  .detail-address a, .detail-address code { overflow-wrap: anywhere; line-height: 24px; }
  .detail-address code { font-family: inherit; }
}
</style>
