<template>
  <div class="facility-list">
    <div class="facility-list__query">
      <screen-field label="出让宗地编号" html-for="facility-query-crzdbh" label-width="112px">
        <screen-input id="facility-query-crzdbh" v-model="query.crzdbh" placeholder="请输入出让宗地编号" @enter="search" />
      </screen-field>
      <screen-field label="配套项目名称" html-for="facility-query-name" label-width="112px">
        <screen-input id="facility-query-name" v-model="query.ptxmmc" placeholder="请输入配套项目名称" @enter="search" />
      </screen-field>
      <div class="facility-list__actions">
        <screen-button type="primary" icon="search" @click="search">查询</screen-button>
        <screen-button icon="reload" @click="reset">重置</screen-button>
      </div>
    </div>
    <screen-data-table :columns="columns" :data="rows" :loading="loading" row-key="id" empty-text="暂无配套项目">
      <template #action="{ row }">
        <span class="facility-list__links">
          <button type="button" class="facility-list__link" @click.stop="$emit('edit', row)">编辑</button>
          <screen-popconfirm title="确定删除该配套项目吗？" :description="row.ptxmmc" width="240" @confirm="remove(row)">
            <button type="button" class="facility-list__link is-danger">删除</button>
          </screen-popconfirm>
        </span>
      </template>
    </screen-data-table>
    <screen-pagination :current="pageNo" :page-size="pageSize" :total="total" @change="handlePage" />
  </div>
</template>

<script>
import {
  ScreenButton, ScreenDataTable, ScreenField, ScreenInput, ScreenPagination, ScreenPopconfirm,
} from '@/components/screen'
import { deleteFacility, queryFacilityList } from '@/api/land/landData'

export default {
  name: 'FacilityList',
  components: { ScreenButton, ScreenDataTable, ScreenField, ScreenInput, ScreenPagination, ScreenPopconfirm },
  data () {
    return {
      query: { crzdbh: '', ptxmmc: '' },
      rows: [],
      loading: false,
      pageNo: 1,
      pageSize: 10,
      total: 0,
      columns: [
        { key: 'crzdbh', title: '出让宗地编号', width: 170 },
        { key: 'ptxmmc', title: '配套项目名称', width: 240 },
        { key: 'ptsslb', title: '配套设施类别', width: 130 },
        { key: 'xzqh', title: '行政区划', width: 110 },
        { key: 'xmfl', title: '项目分类', width: 110 },
        { key: 'jsdw', title: '建设单位', width: 160 },
        { key: 'action', title: '操作', type: 'slot', width: 140 },
      ],
    }
  },
  mounted () {
    this.load()
  },
  methods: {
    load () {
      this.loading = true
      queryFacilityList({
        pageNo: this.pageNo,
        pageSize: this.pageSize,
        crzdbh: this.query.crzdbh ? `*${this.query.crzdbh.trim()}*` : undefined,
        ptxmmc: this.query.ptxmmc ? `*${this.query.ptxmmc.trim()}*` : undefined,
      }).then((response) => {
        if (!response || !response.success) {
          this.rows = []
          this.total = 0
          this.$screenToast.error((response && response.message) || '配套项目加载失败')
          return
        }
        const page = response.result || {}
        this.rows = page.records || []
        this.total = Number(page.total || 0)
      }).catch((error) => {
        this.rows = []
        this.total = 0
        this.$screenToast.error((error && error.message) || '配套项目加载失败')
      }).finally(() => {
        this.loading = false
      })
    },
    search () {
      this.pageNo = 1
      this.load()
    },
    reset () {
      this.query = { crzdbh: '', ptxmmc: '' }
      this.search()
    },
    handlePage ({ current, pageSize }) {
      this.pageNo = current
      this.pageSize = pageSize
      this.load()
    },
    remove (row) {
      deleteFacility(row.id).then((response) => {
        if (!response || !response.success) {
          this.$screenToast.error((response && response.message) || '删除失败')
          return
        }
        this.$screenToast.success(response.message || '删除成功')
        if (this.rows.length === 1 && this.pageNo > 1) this.pageNo -= 1
        this.load()
      }).catch((error) => {
        this.$screenToast.error((error && error.message) || '删除失败')
      })
    },
  },
}
</script>

<style scoped lang="less">
.facility-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 68vh;

  &__query { display: grid; grid-template-columns: 1fr 1fr auto; gap: 12px; align-items: center; }
  &__actions, &__links { display: flex; gap: 8px; }
  &__link { padding: 0; color: var(--screen-accent); background: transparent; border: 0; cursor: pointer; }
  &__link.is-danger { color: #ff8d8d; }
}
</style>
