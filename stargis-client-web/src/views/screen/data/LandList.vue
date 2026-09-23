<template>
  <div class="land-list">
    <div class="land-list__query">
      <screen-field label="出让宗地编号" html-for="land-query-crzdbh" label-width="112px">
        <screen-input id="land-query-crzdbh" v-model="query.crzdbh" placeholder="请输入出让宗地编号" @enter="search" />
      </screen-field>
      <screen-field label="地块名称" html-for="land-query-dkmc" label-width="80px">
        <screen-input id="land-query-dkmc" v-model="query.dkmc" placeholder="请输入地块名称" @enter="search" />
      </screen-field>
      <screen-field label="行政区划" label-width="80px">
        <screen-select v-model="query.xzqh" :options="districts" placeholder="全部" clearable />
      </screen-field>
      <div class="land-list__query-actions">
        <screen-button type="primary" icon="search" @click="search">查询</screen-button>
        <screen-button icon="reload" @click="reset">重置</screen-button>
      </div>
    </div>

    <screen-data-table
      :columns="columns"
      :data="rows"
      :loading="loading"
      row-key="id"
      empty-text="暂无经营性用地"
    >
      <template #action="{ row }">
        <span class="land-list__actions">
          <button type="button" class="land-list__link" @click.stop="$emit('edit', row)">编辑</button>
          <screen-popconfirm
            title="确定删除该经营性用地吗？"
            :description="row.crzdbh"
            width="240"
            @confirm="remove(row)"
          >
            <button type="button" class="land-list__link is-danger">删除</button>
          </screen-popconfirm>
        </span>
      </template>
    </screen-data-table>
    <screen-pagination
      :current="pageNo"
      :page-size="pageSize"
      :total="total"
      @change="handlePage"
    />
  </div>
</template>

<script>
import {
  ScreenButton,
  ScreenDataTable,
  ScreenField,
  ScreenInput,
  ScreenPagination,
  ScreenPopconfirm,
  ScreenSelect,
} from '@/components/screen'
import { deleteLand, queryLandList } from '@/api/land/landData'

const DISTRICTS = ['和平区', '红桥区', '河东区', '南开区', '河西区', '河北区', '东丽区', '津南区', '西青区', '北辰区']

export default {
  name: 'LandList',
  components: {
    ScreenButton,
    ScreenDataTable,
    ScreenField,
    ScreenInput,
    ScreenPagination,
    ScreenPopconfirm,
    ScreenSelect,
  },
  data () {
    return {
      districts: DISTRICTS,
      query: { crzdbh: '', dkmc: '', xzqh: '' },
      rows: [],
      loading: false,
      pageNo: 1,
      pageSize: 10,
      total: 0,
      columns: [
        { key: 'crzdbh', title: '出让宗地编号', width: 180 },
        { key: 'dkmc', title: '地块名称', width: 220 },
        { key: 'xzqh', title: '行政区划', width: 110 },
        { key: 'xmfl', title: '项目分类', width: 110 },
        { key: 'srr', title: '受让人', width: 160 },
        { key: 'crsj', title: '出让时间', width: 120 },
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
      const params = {
        pageNo: this.pageNo,
        pageSize: this.pageSize,
        crzdbh: this.query.crzdbh ? `*${this.query.crzdbh.trim()}*` : undefined,
        dkmc: this.query.dkmc ? `*${this.query.dkmc.trim()}*` : undefined,
        xzqh: this.query.xzqh || undefined,
      }
      queryLandList(params)
        .then((response) => {
          if (!response || !response.success) {
            this.rows = []
            this.total = 0
            this.$screenToast.error((response && response.message) || '经营性用地加载失败')
            return
          }
          const page = response.result || {}
          this.rows = page.records || []
          this.total = Number(page.total || 0)
        })
        .catch((error) => {
          this.rows = []
          this.total = 0
          this.$screenToast.error((error && error.message) || '经营性用地加载失败')
        })
        .finally(() => {
          this.loading = false
        })
    },
    search () {
      this.pageNo = 1
      this.load()
    },
    reset () {
      this.query = { crzdbh: '', dkmc: '', xzqh: '' }
      this.search()
    },
    handlePage ({ current, pageSize }) {
      this.pageNo = current
      this.pageSize = pageSize
      this.load()
    },
    remove (row) {
      deleteLand(row.id)
        .then((response) => {
          if (!response || !response.success) {
            this.$screenToast.error((response && response.message) || '删除失败')
            return
          }
          this.$screenToast.success(response.message || '删除成功')
          if (this.rows.length === 1 && this.pageNo > 1) this.pageNo -= 1
          this.load()
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '删除失败')
        })
    },
  },
}
</script>

<style scoped lang="less">
.land-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;

  &__query {
    display: grid;
    grid-template-columns: 1.2fr 1fr 0.8fr auto;
    gap: 12px;
    align-items: center;
  }

  &__query-actions {
    display: flex;
    gap: 8px;
  }

  &__actions {
    display: flex;
    gap: 12px;
  }

  &__link {
    padding: 0;
    color: var(--screen-accent);
    background: transparent;
    border: 0;
    cursor: pointer;

    &.is-danger {
      color: #ff8d8d;
    }
  }
}
</style>
