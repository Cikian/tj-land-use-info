<template>
  <!--
    FacilityDetailModal 配套项目详情（查询统计 · 用地配套项目页签点行打开）
    --------------------------------
    与「数据管理」里的 FacilityDetailModal 的区别：那个是**录入视角**
    （带编辑、环节进度录入、附件上传、变更履历），本页是**查询视角**，
    只读、一次看完「基本信息 / 环节进展 / 时间节点 / 责任单位 / 其他」。

    ★ 为什么单独一个弹窗而不复用数据管理那个：
      ① 那个弹窗依赖 `land:data:facility` 权限下的录入接口，而查询页不挂权限；
      ② 它是 5 个页签 + 写操作，对「点一行看一眼进展」来说太重。
    字段中文名仍复用后端 `/columns?dataType=facility` 的元数据，不另抄一份。
  -->
  <screen-modal
    :visible="visible"
    :title="`配套项目详情 · ${title}`"
    :width="980"
    :show-footer="false"
    @cancel="$emit('close')"
  >
    <screen-loading v-if="loading" inline text="正在加载配套详情…" />

    <screen-empty
      v-else-if="!detail"
      text="未找到该配套项目"
      description="可能已被移除（软删），可在「数据管理 · 数据更新与移除」里恢复"
    />

    <div v-else class="facility-detail">
      <section
        v-for="group in groups"
        :key="group.title"
        class="facility-detail__group"
      >
        <h4 class="facility-detail__title">{{ group.title }}</h4>
        <screen-descriptions
          variant="flat"
          :items="group.items"
          :columns="group.columns || 2"
          label-width="150px"
        />
      </section>

      <p class="facility-detail__foot">
        <screen-icon name="info" :size="13" />
        只读视图；要修改配套信息或录入环节进度，请到「数据管理 · 配套地块数据录入」
      </p>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenDescriptions,
  ScreenEmpty,
  ScreenIcon,
  ScreenLoading,
  ScreenModal
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryFacilityDetail } from '@/api/land/statQuery'

/**
 * 分组与展示顺序（只读详情的信息架构）：
 *   基本信息 → 环节进展 → 时间节点 → 责任单位 → 其他
 * 与旧系统 `attributeDetailsForQuery.html` 的分块思路一致，但按「进展查询」的主线重排过。
 */
const GROUPS = [
  {
    title: '基本信息',
    keys: ['ptxmmc', 'crzdbh', 'dkmc', 'ptsslb', 'jsxx', 'xzqh', 'xmfl', 'dldj',
      'ghhxkd', 'cd', 'tzgs', 'zjly', 'sfzsjtjlz', 'tjlzsftg']
  },
  {
    title: '环节进展',
    keys: ['xjpfsfwc', 'xjpfzt', 'kypfsfwc', 'kypfzt', 'csjgspfsfwc', 'csjgspfzt',
      'gspfje', 'zjlsqk', 'sfkg', 'kgzt', 'sfjg', 'sfyj']
  },
  {
    title: '时间节点',
    keys: ['dkcrscndptjgsj', 'yjkgsj', 'sjkgsj', 'yjjgsj', 'sjjgsj']
  },
  {
    title: '责任单位',
    keys: ['jsdw', 'sjdw', 'kcdw', 'jldw', 'sgdw', 'jsgydw']
  },
  {
    title: '其他',
    keys: ['jtwt', 'gzjy', 'zlqsnrjsm', 'bz', 'lrdw', 'lrr', 'lxdh', 'createTime'],
    // 长文本独占整行（否则会被省略号截断，看详情就没意义了）
    stackKeys: ['jtwt', 'gzjy', 'zlqsnrjsm', 'bz']
  }
]

export default {
  name: 'FacilityDetailModal',
  components: { ScreenDescriptions, ScreenEmpty, ScreenIcon, ScreenLoading, ScreenModal },
  props: {
    visible: { type: Boolean, default: false },
    /** 配套项目 id（结果行的行标识） */
    facilityId: { type: String, default: '' },
    /** 配套字段元数据（后端 /columns?dataType=facility），用于中文名 */
    columns: { type: Array, default: () => [] }
  },
  data () {
    return {
      detail: null,
      loading: false
    }
  },
  computed: {
    labels () {
      const map = {}
      this.columns.forEach((column) => {
        map[column.key] = column.unit ? `${column.label}（${column.unit}）` : column.label
      })
      return map
    },
    title () {
      return (this.detail && this.detail.ptxmmc) || this.facilityId || ''
    },
    groups () {
      if (!this.detail) {
        return []
      }
      const detail = this.detail
      return GROUPS.map((group) => {
        const items = []
        group.keys.forEach((key) => {
          const value = detail[key]
          if (value === null || value === undefined || String(value).trim() === '') {
            // 空值也显示（写明「—」）：详情页看不到某个字段和「字段是空的」是两回事
            items.push({ key, label: this.labels[key] || key, value: '—' })
            return
          }
          items.push({
            key,
            label: this.labels[key] || key,
            value,
            stack: (group.stackKeys || []).indexOf(key) > -1
          })
        })
        return { title: group.title, items }
      }).filter((group) => group.items.length)
    }
  },
  watch: {
    visible (value) {
      if (value) {
        this.load()
      }
    },
    facilityId () {
      if (this.visible) {
        this.load()
      }
    }
  },
  methods: {
    async load () {
      if (!this.facilityId) {
        return
      }
      this.loading = true
      this.detail = null
      try {
        const res = await queryFacilityDetail(this.facilityId)
        if (!res || !res.success) {
          toast.error((res && res.message) || '配套详情加载失败')
          return
        }
        this.detail = res.result || null
      } catch (e) {
        toast.error('配套详情加载失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-detail {
  max-height: calc(100vh - 240px);
  overflow-y: auto;

  &__group {
    margin-bottom: 14px;
  }

  &__title {
    margin: 0 0 6px;
    padding-left: 8px;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);
    line-height: 1.4;
    border-left: 3px solid var(--screen-accent);
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 4px 0 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
