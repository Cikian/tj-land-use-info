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
            <a-card
              v-for="record in pagedTables"
              :key="rowKey(record)"
              class="service-card"
              :class="{ 'is-view': record.kind === 'v' || record.kind === 'm' }"
              :body-style="{ padding: 0 }"
              hoverable
            >
              <div class="service-card-main">
                <div class="service-card-heading">
                  <!-- 中控数据源不在本系统管理清单里（后端拒绝移除），不显示勾选框 -->
                  <a-checkbox
                    v-if="canManageSource(record.sourceCode)"
                    class="service-check"
                    :checked="checkedCodes.includes(record.sourceCode)"
                    @change="e => toggleChecked(record.sourceCode, e.target.checked)"
                  />
                  <span class="service-icon"><a-icon :type="record.kind === 'v' || record.kind === 'm' ? 'profile' : 'database'" /></span>
                  <div class="service-name">
                    <a class="service-title" :title="record.table" @click="openDetails(record)">{{ record.table }}</a>
                    <div class="service-subtitle">{{ record.schema }} · {{ kindLabel(record.kind) }}</div>
                  </div>
                  <a-button
                    class="service-copy"
                    type="link"
                    icon="copy"
                    :disabled="!record.available"
                    :title="record.available ? '复制 TileJSON 地址' : '请先声明有效坐标系'"
                    @click="copy(tileJsonUrl(record))"
                  />
                </div>
                <!-- 定义式信息表（设计稿方案四）；描述文字不进卡片，完整描述在详情抽屉 -->
                <div class="service-meta">
                  <div class="service-meta-row">
                    <span class="service-meta-key">几何列</span>
                    <span class="service-meta-val" :title="record.geometry">{{ record.geometry }}</span>
                  </div>
                  <div class="service-meta-row">
                    <span class="service-meta-key">几何类型</span>
                    <span class="service-meta-val" :title="record.geometryType">{{ record.geometryType }}</span>
                  </div>
                  <div class="service-meta-row">
                    <span class="service-meta-key">存储</span>
                    <span class="service-meta-val">{{ record.geography ? 'geography' : 'geometry' }}</span>
                  </div>
                  <div class="service-meta-row">
                    <span class="service-meta-key">坐标系</span>
                    <span class="service-meta-val">
                      <span class="service-pill" :class="record.available ? 'is-ok' : 'is-warn'">
                        <span class="service-pill-dot" />{{ record.available ? 'SRID ' + record.srid : '待声明' }}
                      </span>
                    </span>
                  </div>
                </div>
                <div class="service-owner">
                  <span class="service-source" :title="record.sourceName">
                    <a-icon type="database" class="service-source-icon" />
                    <span class="service-source-name">{{ record.sourceName }}</span>
                  </span>
                </div>
              </div>
              <div class="service-card-actions">
                <a-button type="link" class="action-primary" @click="openDetails(record)">查看详情</a-button>
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
          <a-descriptions-item label="数据库">{{ selectedTable.sourceName }}</a-descriptions-item>
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
    <!-- 移除确认：两档（仅取消登记 / 删除 PG 库）。删库是不可逆动作，三重闸：选档 + 勾选确认 + 输入"删除" -->
    <a-modal
      title="移除数据库"
      :width="560"
      :visible="removeModal.visible"
      :mask-closable="false"
      @cancel="removeModal.visible = false"
    >
      <div class="remove-targets">
        <div v-for="t in removeModal.targets" :key="t.id" class="remove-target">
          <span class="remove-target-name" :title="t.code">{{ t.name }}（{{ t.code }}）</span>
          <a-spin v-if="removeModal.statsLoading" size="small" />
          <a-tag v-else-if="!isOwnImported(t)" color="default">非本系统导入</a-tag>
          <a-tag v-else-if="statsOf(t).reachable" color="green">库内 {{ statsOf(t).tableCount }} 张表</a-tag>
          <a-tag v-else color="orange">库不可达</a-tag>
        </div>
      </div>
      <a-radio-group v-model="removeModal.mode" class="remove-options">
        <div class="remove-option">
          <a-radio value="unregister">仅取消登记<span class="remove-desc">（保留 PG 库和库内全部数据，之后可重新添加）</span></a-radio>
        </div>
        <div class="remove-option">
          <a-radio value="drop" :disabled="!droppableTargets.length">删除 PG 数据库<span class="remove-desc">（先断开所有连接再删库，不可恢复）</span></a-radio>
          <div v-if="!droppableTargets.length" class="remove-note">勾选的库都不是本系统导入的，只能取消登记</div>
        </div>
      </a-radio-group>
      <template v-if="removeModal.mode === 'drop'">
        <a-alert type="warning" show-icon message="删除后库内全部数据永久丢失且不可恢复" class="remove-alert" />
      </template>
      <template slot="footer">
        <a-button :disabled="removeModal.submitting" @click="removeModal.visible = false">取消</a-button>
        <a-button
          :type="removeModal.mode === 'drop' ? 'danger' : 'primary'"
          :loading="removeModal.submitting"
          @click="confirmRemove"
        >
          {{ removeModal.mode === 'drop' ? '删除' : '移除' }}
        </a-button>
      </template>
    </a-modal>
  </div>
</template>

<script>
import { getMvtSources, getMvtTables, removeMvtSource } from '@/api/land/mvt'
import { getSourceStats } from '@/api/land/spatial'
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
      // 服务地址基座：优先取 MVT 对外服务基座（部署后在 static/config.js 配置，
      // 保证复制出去的链接外机可达）；未配置时回退 domianURL——本地开发不配它，行为不变
      serviceBase: new URL(window._CONFIG.MVT_PUBLIC_BASE_URL || window._CONFIG.domianURL, window.location.origin).href.replace(/\/$/, ''),
      removeModal: { visible: false, mode: 'unregister', submitting: false, statsLoading: false, targets: [], stats: {} }
    }
  },
  computed: {
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
    },
    /** 允许删库的勾选库：仅本系统 SHP 导入的（remark 是登记的来源标记） */
    droppableTargets () {
      return this.removeModal.targets.filter(t => t.remark === 'SHP 上传入库')
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
      // 连接编码必须取行数据自带的 sourceCode：下拉选「全部」时 this.source 是
      // '__all__' 占位值，拼进地址后端无法按真实编码找到库（404）
      const code = row.sourceCode || this.source
      return this.serviceBase.trim().replace(/\/+$/, '') + '/land/mvt/public/' +
        [code, row.schema, row.table, row.geometry].map(encodeURIComponent).join('/')
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
    /** 中控数据源（stargis_metadata 联查出来的）不在本系统管理清单里，后端拒绝移除，前端不显示勾选框 */
    canManageSource (code) {
      const source = this.sources.find(item => item.code === code)
      return !source || source.remark !== '中控数据源'
    },
    /** 打开两档移除确认弹窗（默认"仅取消登记"；删库档在点击"删除"后再弹一次标准确认框） */
    removeCheckedSources () {
      if (!this.checkedCodes.length) { this.$message.warning('请先勾选要移除的数据库卡片'); return }
      const targets = this.sources.filter(item => this.checkedCodes.includes(item.code))
      this.removeModal = {
        visible: true,
        mode: 'unregister',
        submitting: false,
        statsLoading: true,
        targets,
        stats: {}
      }
      this.loadRemoveStats(targets)
    },
    /** 逐库探活统计"将丢失什么"（连接口令不出服务端，前端只拿到表数量） */
    async loadRemoveStats (targets) {
      for (const t of targets) {
        let stats = { reachable: false, tableCount: -1 }
        try {
          const res = await getSourceStats(t.id)
          if (res && res.success && res.result) stats = res.result
        } catch (e) { /* 探活失败按不可达展示，不阻塞确认 */ }
        this.$set(this.removeModal.stats, t.code, stats)
      }
      this.removeModal.statsLoading = false
    },
    statsOf (t) {
      return this.removeModal.stats[t.code] || { reachable: false, tableCount: -1 }
    },
    isOwnImported (t) {
      return t.remark === 'SHP 上传入库'
    },
    /** 确认移除：删库档先弹一次标准确认框（列出库名与不可恢复警示），通过才真正执行 */
    confirmRemove () {
      const drop = this.removeModal.mode === 'drop'
      const proceed = async () => {
        const targets = drop ? this.droppableTargets : this.removeModal.targets
        const skipped = drop ? this.removeModal.targets.length - targets.length : 0
        this.removeModal.submitting = true
        const failed = []
        for (const t of targets) {
          try {
            const res = await removeMvtSource(t.id, drop)
            if (res && res.success === false) failed.push(t.name || t.code)
          } catch (e) { failed.push(t.name || t.code) }
        }
        this.removeModal.submitting = false
        this.removeModal.visible = false
        if (failed.length) this.$message.error(`${failed.join('、')} 移除失败`)
        else this.$message.success(drop ? '已删除选中的 PG 数据库' : '已移除勾选的数据库登记')
        if (skipped > 0) this.$message.info(`${skipped} 个非本系统导入的库未处理（只支持取消登记）`)
        this.checkedCodes = []
        this.tables = []
        await this.loadSources()
      }
      if (!drop) { proceed(); return }
      const names = this.droppableTargets.map(t => `"${t.name}（${t.code}）"`).join('、')
      const skipped = this.removeModal.targets.length - this.droppableTargets.length
      this.$confirm({
        title: '确认删除',
        content: `将永久删除 ${names} 及库内全部数据（先断开所有连接再删库，不可恢复）` +
          (skipped ? `；另有 ${skipped} 个非本系统导入的库将保留` : ''),
        okText: '确认删除',
        okType: 'danger',
        onOk: proceed
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
  .service-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 20px; }
  // 卡片 = 设计稿方案四：发丝线框 + 顶部渐变色条（空间表=蓝青 / 视图=紫）+ 定义式信息表
  .service-card {
    position: relative;
    min-width: 0;
    border-radius: 10px;
    border: 1px solid #e2e8f0;
    overflow: hidden;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  }
  .service-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #1890ff, #36cfc9);
  }
  .service-card.is-view::before {
    background: linear-gradient(90deg, #7c5cff, #c084fc);
  }
  .service-card:hover {
    border-color: #91d5ff;
    box-shadow: 0 6px 20px rgba(24, 144, 255, 0.14);
    transform: translateY(-2px);
  }
  // 卡片纵向 flex：信息区撑满、操作条贴底，同排卡片等高时操作条不会悬在半空
  /deep/ .ant-card-body { display: flex; flex-direction: column; }
  .service-card-main { flex: 1; padding: 24px 22px 20px; }
  .service-card-heading { display: flex; align-items: center; gap: 12px; }
  .service-check { flex-shrink: 0; }
  .service-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 38px;
    height: 38px;
    border: 1.5px solid #dbe4ee;
    border-radius: 9px;
    background: #f8fbff;
    color: #2563eb;
    font-size: 18px;
  }
  .service-card.is-view .service-icon { border-color: #e4dcf5; background: #faf9ff; color: #7c3aed; }
  .service-name { flex: 1; min-width: 0; }
  .service-title {
    display: block;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: rgba(0, 0, 0, 0.85);
    font-size: 16px;
    font-weight: 700;
    line-height: 22px;
  }
  .service-title:hover { color: #1890ff; }
  .service-subtitle { margin-top: 4px; font-size: 12px; color: rgba(0, 0, 0, 0.45); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .service-copy { flex-shrink: 0; width: 28px; height: 28px; padding: 0; color: #a4b1c2; }
  .service-copy[disabled] { color: rgba(0, 0, 0, 0.25); }
  .service-copy:not([disabled]):hover { color: #1890ff; background: #e8f3ff; }
  // 定义式信息表：键列浅底 + 等宽字值列，行间发丝线
  .service-meta { margin-top: 16px; border: 1px solid #eef2f7; border-radius: 8px; overflow: hidden; }
  .service-meta-row { display: grid; grid-template-columns: 76px 1fr; }
  .service-meta-row + .service-meta-row { border-top: 1px solid #f0f4f8; }
  .service-meta-key {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    border-right: 1px solid #f0f4f8;
    background: #fafbfd;
    font-size: 11px;
    letter-spacing: 0.06em;
    color: #9aa8ba;
  }
  .service-meta-val {
    display: flex;
    align-items: center;
    min-width: 0;
    padding: 8px 12px;
    font-family: Consolas, Menlo, monospace;
    font-size: 12.5px;
    color: rgba(0, 0, 0, 0.75);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  // 坐标系渐变胶囊：绿=SRID 已声明 / 橙=待声明
  .service-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
    padding: 1px 10px;
    border-radius: 999px;
    font-family: Consolas, Menlo, monospace;
    font-size: 11.5px;
  }
  .service-pill.is-ok { color: #15803d; background: linear-gradient(135deg, #e8fbef, #dcf5e5); border: 1px solid #bfe8cb; }
  .service-pill.is-warn { color: #b45309; background: #fef3e2; border: 1px solid #f3d9a4; }
  .service-pill-dot { width: 6px; height: 6px; border-radius: 50%; }
  .service-pill.is-ok .service-pill-dot { background: #16a34a; }
  .service-pill.is-warn .service-pill-dot { background: #d97706; }
  // 数据源归属：软渐变标签
  .service-owner { margin-top: 14px; display: flex; align-items: center; min-width: 0; }
  .service-source {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    max-width: 100%;
    padding: 3px 10px;
    border: 1px solid #d5e8fb;
    border-radius: 6px;
    background: linear-gradient(135deg, #eef6ff, #e8f3ff);
    color: #145db4;
    font-size: 11px;
  }
  .service-source-icon { font-size: 11px; }
  .service-source-name { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  // 操作条：查看详情=主题色主操作，复制地址=次级
  .service-card-actions { display: flex; border-top: 1px solid #e8edf3; background: #fbfdff; }
  .service-card-actions .ant-btn { flex: 1; height: 42px; margin: 0; color: #52627a; }
  .service-card-actions .ant-btn.action-primary { color: #0e6fd8; font-weight: 600; }
  .service-card-actions .ant-btn:not([disabled]):hover { color: #1890ff; }
  .service-card-actions .ant-btn + .ant-btn { border-left: 1px solid #e8edf3; border-radius: 0; }
  .service-card-actions .ant-btn[disabled] { color: rgba(0, 0, 0, 0.25); }
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

// 移除确认弹窗：a-modal 挂到 body 层，选择器必须平铺（靠 data-v 属性匹配，不能嵌套在 .mvt-services 下）
.remove-targets {
  max-height: 160px;
  overflow-y: auto;
}
.remove-target {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.remove-target-name {
  flex: 1;
  min-width: 0;
  margin-right: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.85);
}
.remove-options {
  display: block;
  margin-top: 12px;
}
.remove-option {
  line-height: 28px;
}
.remove-option + .remove-option {
  margin-top: 6px;
}
.remove-desc {
  margin-left: 4px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.remove-note {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.remove-alert {
  margin-top: 12px;
}
</style>
