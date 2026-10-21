<template>
  <!--
    LandFacilityDetailModal 地块配套详情
    --------------------------------
    高保真的「地块配套详情（点击地块查看）」：点结果表里的宗地编号，
    看这块地下挂了哪些配套项目、各自走到哪个环节。

    ★ 列名为什么从 `columns` 传进来而不是写死：
      「配套项目名称」「项建批复是否完成」这些中文名与字段 key 的对应关系
      只在后端 `QueryFieldRegistry` 维护一份（见 api 里的 /columns 接口），
      前端再抄一份中文名，改一个字段就要改两处，早晚会有一个不准。
  -->
  <screen-modal
    :visible="visible"
    :title="`地块配套详情 · ${crzdbh || ''}`"
    :width="1080"
    :show-footer="false"
    @cancel="$emit('close')"
  >
    <div class="land-detail">
      <div class="land-detail__bar">
        <span>
          共 <b>{{ facilities.length }}</b> 条配套
        </span>
        <span class="land-detail__hint">
          <screen-icon name="info" :size="13" />
          这里列的是该宗地下<b>全部</b>配套（不受查询条件影响）
        </span>
      </div>

      <screen-loading v-if="loading" inline text="正在加载配套…" />

      <screen-empty
        v-else-if="!facilities.length"
        text="该宗地下没有配套记录"
        description="可在「数据管理 · 配套地块数据录入」里补录"
      />

      <screen-data-table
        v-else
        :columns="tableColumns"
        :data="facilities"
        :min-width="1040"
      />
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenDataTable,
  ScreenEmpty,
  ScreenIcon,
  ScreenLoading,
  ScreenModal
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryLandDetail } from '@/api/land/statQuery'

/** 详情表固定展示的字段（顺序即列序） */
const DETAIL_KEYS = [
  'ptxmmc', 'ptsslb', 'jsxx', 'dldj', 'tzgs', 'zjly',
  'xjpfsfwc', 'kypfsfwc', 'csjgspfsfwc', 'sfkg', 'sfjg', 'sfyj'
]

export default {
  name: 'LandFacilityDetailModal',
  components: { ScreenDataTable, ScreenEmpty, ScreenIcon, ScreenLoading, ScreenModal },
  props: {
    visible: { type: Boolean, default: false },
    crzdbh: { type: String, default: '' },
    /** 配套字段元数据（后端 /columns?dataType=facility） */
    columns: { type: Array, default: () => [] }
  },
  data () {
    return {
      facilities: [],
      loading: false
    }
  },
  computed: {
    tableColumns () {
      const meta = {}
      this.columns.forEach((column) => { meta[column.key] = column })
      const keys = DETAIL_KEYS.filter((key) => meta[key])
      return keys.map((key) => ({
        key,
        title: meta[key].unit ? `${meta[key].label}（${meta[key].unit}）` : meta[key].label,
        width: key === 'ptxmmc' ? 200 : (key === 'tzgs' ? 120 : 110),
        type: meta[key].type === 'NUMBER' ? 'number' : 'text',
        precision: 2,
        align: meta[key].type === 'NUMBER' ? 'right' : 'left'
      }))
    }
  },
  watch: {
    visible (value) {
      if (value) {
        this.load()
      }
    },
    crzdbh () {
      if (this.visible) {
        this.load()
      }
    }
  },
  methods: {
    async load () {
      if (!this.crzdbh) {
        return
      }
      this.loading = true
      try {
        const res = await queryLandDetail(this.crzdbh)
        if (!res || !res.success) {
          toast.error((res && res.message) || '地块配套详情加载失败')
          this.facilities = []
          return
        }
        this.facilities = (res.result && res.result.facilities) || []
      } catch (e) {
        toast.error('地块配套详情加载失败：' + (e && e.message ? e.message : '网络异常'))
        this.facilities = []
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-detail {
  &__bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);

    b {
      color: var(--screen-text);
    }
  }

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__status {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }
}
</style>
