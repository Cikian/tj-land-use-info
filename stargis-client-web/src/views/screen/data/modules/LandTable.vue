<template>
  <!--
    LandTable 经营性用地列表表格
    --------------------------------
    这是一个**纯展示**组件：不发任何请求，全部交互通过事件抛给父组件
    （与 ArchiveTable 同一取舍：维护页与后续的「只读」入口才能零成本复用）。

    ★ 三个计数列（配套数 / 附件数 / 变更次数）由父组件查出来挂在行上：
      后端的 `/land/data/landAdmin/list` 返回的就是 `t_land` 实体本身，
      **没有**这几个聚合字段（只有 `/land/data/land/options` 的
      LandOptionVO 带 facilityCount）。所以这里只负责渲染，
      查不到时显示「—」而不是 0 —— 0 是「确实没有」，— 是「还没查」，
      把两者混起来会让用户以为某条宗地没有配套。
      列表很长时父组件可以不查（表格里就是一片「—」），这是刻意的：
      宁可显示「未统计」，也不要假装统计过了。

    事件：
      detail  (row)  查看详情
      history (row)  查看变更履历
      edit    (row)  编辑
      remove  (row)  移除（父组件负责收集原因并发请求）
      facilities (row) 跳到该宗地的配套录入面板
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1340"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    :empty-text="emptyText"
    @select-change="handleSelectChange"
  >
    <!-- 出让宗地编号：主键列，点击看详情（不给它单独的「详情」按钮，省一列宽度） -->
    <template #crzdbh="{ row }">
      <button type="button" class="land-table__link" @click.stop="$emit('detail', row)">
        {{ row.crzdbh || '—' }}
      </button>
    </template>

    <!-- 地块名称：主标题 + 受让人副行（一行里同时给出「哪块地」和「谁受让」） -->
    <template #dkmc="{ row }">
      <span class="land-table__name" :title="row.dkmc || ''">{{ row.dkmc || '—' }}</span>
      <span v-if="row.srr" class="land-table__sub">{{ row.srr }}</span>
    </template>

    <template #xzqh="{ row }">
      <span>{{ row.xzqh || '—' }}</span>
    </template>

    <template #xmfl="{ row }">
      <screen-tag :tone="row.xmfl === '市级项目' ? 'success' : 'info'" size="sm">
        {{ row.xmfl || '—' }}
      </screen-tag>
    </template>

    <template #srr="{ row }">
      <span class="land-table__ellipsis" :title="row.srr || ''">{{ row.srr || '—' }}</span>
    </template>

    <template #crsj="{ row }">
      <span class="land-table__date">{{ row.crsj || '—' }}</span>
    </template>

    <template #crj="{ row }">
      <span class="land-table__num">{{ row.crj === null || row.crj === undefined ? '—' : row.crj }}</span>
    </template>

    <!-- 配套数：点击直接跳到「配套地块数据录入」，这是 1 宗地 N 配套的主入口 -->
    <template #facilityCount="{ row }">
      <button
        v-if="row.facilityCount !== undefined && row.facilityCount !== null"
        type="button"
        class="land-table__link"
        :title="`查看该宗地下的 ${row.facilityCount} 个配套项目`"
        @click.stop="$emit('facilities', row)"
      >
        {{ row.facilityCount }} 个
      </button>
      <span v-else class="land-table__mute">—</span>
    </template>

    <template #attachmentCount="{ row }">
      <span class="land-table__num">
        {{ row.attachmentCount === undefined || row.attachmentCount === null ? '—' : row.attachmentCount }}
      </span>
    </template>

    <template #changeCount="{ row }">
      <button
        v-if="row.changeCount"
        type="button"
        class="land-table__link"
        title="查看该宗地的字段级变更履历"
        @click.stop="$emit('history', row)"
      >
        {{ row.changeCount }} 次
      </button>
      <span v-else class="land-table__mute">—</span>
    </template>

    <!-- 操作列：用插槽而不是内置 actions，因为移除要挂二次确认气泡 -->
    <template #action="{ row }">
      <span class="land-table__actions">
        <button type="button" class="land-table__link" @click.stop="$emit('detail', row)">详情</button>
        <button type="button" class="land-table__link" @click.stop="$emit('history', row)">履历</button>

        <template v-if="!readonly">
          <button type="button" class="land-table__link" @click.stop="$emit('edit', row)">编辑</button>

          <screen-popconfirm
            title="移除后可在「数据更新与移除」里恢复，确定移除该宗地吗？"
            :description="row.crzdbh ? `宗地编号：${row.crzdbh}` : ''"
            width="300"
            @confirm="$emit('remove', row)"
          >
            <button type="button" class="land-table__link is-danger">移除</button>
          </screen-popconfirm>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag, ScreenPopconfirm } from '@/components/screen'

export default {
  name: 'LandTable',
  components: { ScreenDataTable, ScreenTag, ScreenPopconfirm },
  props: {
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    selectable: { type: Boolean, default: false },
    selectedRowKeys: { type: Array, default: () => [] },
    /** 只读模式：隐藏编辑/移除 */
    readonly: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无经营性用地' }
  },
  data () {
    return {
      columns: [
        { key: 'crzdbh', title: '出让宗地编号', width: 170, type: 'slot' },
        { key: 'dkmc', title: '地块名称 / 受让人', width: 200, type: 'slot' },
        { key: 'xzqh', title: '行政区划', width: 70, type: 'slot', align: 'center' },
        { key: 'xmfl', title: '项目分类', width: 100, type: 'slot', align: 'center' },
        { key: 'ghydxz', title: '规划用地性质', width: 140, ellipsis: true },
        { key: 'crj', title: '出让金(亿元)', width: 100, type: 'slot', align: 'center' },
        { key: 'crsj', title: '出让时间', width: 110, type: 'slot', align: 'center' },
        { key: 'ptsfqq', title: '配套齐全', width: 70, align: 'center' },
        { key: 'facilityCount', title: '配套数', width: 60, type: 'slot', align: 'center' },
        { key: 'attachmentCount', title: '附件数', width: 60, type: 'slot', align: 'center' },
        { key: 'changeCount', title: '变更次数', width: 60, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 200, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-table {
  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__sub {
    display: block;
    margin-top: 1px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__date {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__mute {
    color: var(--screen-text-mute);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    // 四个操作在窄列里一定会换行，允许换行避免横向溢出
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();
  }
}
</style>
