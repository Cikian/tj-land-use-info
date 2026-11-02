<template>
  <!--
    WarningFilterBar 预警信息查询 · 筛选条
    ===============================================================
    条件（契约 §6.1 顺序）：预警分类 / 预警等级 / 行政区划 / 地块名称 /
    配套项目名称 / 宗地编号 / 关键词，动作：查询 / 重置 / 重新计算 / 导出。

    ★ 条件状态放在本组件内部、以「已拍平的条件对象」向上抛（`search`），
      而不是用 v-model 把 conditions 同步给父组件：
      父组件只关心「这批条件要查什么」，多一份双向同步就多一处「条件显示是 A、
      实际请求是 B」的可能（查询统计页也是同一个思路，条件与请求必须同源）。

    ★ 行政区划用输入框而不是下拉：`/options` 只返回等级 / 分类 / 问题类型三类，
      没有区划清单；而契约 §5.1 里 xzqh 是**精确匹配**，输入框反而与后端语义一致
      （拿下拉候选去猜精确值，猜错了就是查不到）。

    ★ 「重置」清空后**立刻按空条件重查**：只清空不查的话，表格还停在上一次的结果上，
      与条件区显示的「全部」自相矛盾。

    ★ 「重新计算」是危险动作（会按当前库里的数据重算并增删预警行），
      所以走 ScreenPopconfirm 二次确认，而不是点一下就发请求。
  -->
  <screen-panel class="warning-filter" title="查询条件">
    <div class="warning-filter__grid">
      <screen-field label="预警分类" :label-width="104" html-for="wq-yj-type">
        <screen-select
          id="wq-yj-type"
          v-model="conditions.yjType"
          :options="typeOptions"
          placeholder="全部"
          :searchable="false"
          aria-label="预警分类"
        />
      </screen-field>

      <screen-field label="预警等级" :label-width="104" html-for="wq-level">
        <screen-select
          id="wq-level"
          v-model="conditions.levelId"
          :options="levelOptions"
          placeholder="全部等级"
          :searchable="false"
          aria-label="预警等级"
        />
      </screen-field>

      <screen-field label="行政区划" :label-width="104" html-for="wq-xzqh">
        <screen-input
          id="wq-xzqh"
          v-model="conditions.xzqh"
          placeholder="精确匹配，如：滨海新区"
          clearable
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="地块名称" :label-width="104" html-for="wq-dkmc">
        <screen-input
          id="wq-dkmc"
          v-model="conditions.dkmc"
          placeholder="模糊匹配，回车查询"
          clearable
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="配套项目名称" :label-width="104" html-for="wq-ptxmmc">
        <screen-input
          id="wq-ptxmmc"
          v-model="conditions.ptxmmc"
          placeholder="模糊匹配，回车查询"
          clearable
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="宗地编号" :label-width="104" html-for="wq-crzdbh">
        <screen-input
          id="wq-crzdbh"
          v-model="conditions.crzdbh"
          placeholder="精确匹配，如：津北辰（挂）2018-015号"
          clearable
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="关键词" :label-width="104" html-for="wq-keyword">
        <screen-input
          id="wq-keyword"
          v-model="conditions.keyword"
          placeholder="预警名称 / 配套项目 / 环节"
          clearable
          @enter="handleSearch"
        />
      </screen-field>
    </div>

    <template #footer>
      <div class="warning-filter__actions">
        <screen-button size="sm" type="primary" icon="search" :loading="loading" @click="handleSearch">
          查询
        </screen-button>

        <screen-button size="sm" icon="rotate-ccw" @click="handleReset">重置</screen-button>

        <screen-popconfirm
          title="重新计算会按当前数据全量重算预警，确定继续吗？"
          description="重算会新增 / 更新 / 删除预警记录，完成后列表与统计会一起刷新。"
          tone="default"
          ok-text="开始重算"
          :confirm-loading="recalcLoading"
          @confirm="handleRecalc"
        >
          <screen-button size="sm" icon="reload" :loading="recalcLoading">重新计算</screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="download" :disabled="exportDisabled" @click="handleExport">
          导出 Excel
        </screen-button>

        <span class="warning-filter__tip">
          预警值 = 实际历时 ÷ 标准历时；逾期天数按工作日计（含头不含尾）
        </span>
      </div>
    </template>
  </screen-panel>
</template>

<script>
import {
  ScreenButton,
  ScreenField,
  ScreenInput,
  ScreenPanel,
  ScreenPopconfirm,
  ScreenSelect
} from '@/components/screen'

export default {
  name: 'WarningFilterBar',
  components: {
    ScreenButton,
    ScreenField,
    ScreenInput,
    ScreenPanel,
    ScreenPopconfirm,
    ScreenSelect
  },
  props: {
    /** 预警等级候选（来源 /options 的 levels，value 用 levelId） */
    levelOptions: { type: Array, default: () => [] },
    /** 预警分类候选（来源 /options 的 yjTypes，缺省用兜底三分类） */
    typeOptions: { type: Array, default: () => [] },
    /** 列表查询中（查询按钮的 loading） */
    loading: { type: Boolean, default: false },
    /** 重算进行中（按钮与确认框同时 loading） */
    recalcLoading: { type: Boolean, default: false },
    /** 没有结果时禁止导出：导出一个空 Excel 只会让人以为导错了 */
    exportDisabled: { type: Boolean, default: false }
  },
  data () {
    return {
      /**
       * ★ 每个条件的 key 都先初始化成空串。
       *   Vue 2 对「对象上原本不存在的 key」赋值不触发响应，而 ScreenSelect 的显示值
       *   来自 `:value="conditions[key]"` —— 没初始化的话，用户选完下拉会「选了但显示不出来」，
       *   可是条件已经进了请求（查询统计页踩过同一个坑）。
       */
      conditions: {
        yjType: '',
        levelId: '',
        xzqh: '',
        dkmc: '',
        ptxmmc: '',
        crzdbh: '',
        keyword: ''
      }
    }
  },
  methods: {
    /** 条件 → 请求参数（空值不下发，后端把「不传」当「全部」） */
    buildParams () {
      const params = {}
      Object.keys(this.conditions).forEach((key) => {
        const value = this.conditions[key]
        if (value !== null && value !== undefined && String(value).trim() !== '') {
          params[key] = String(value).trim()
        }
      })
      return params
    },
    handleSearch () {
      this.$emit('search', this.buildParams())
    },
    handleReset () {
      const cleared = {}
      Object.keys(this.conditions).forEach((key) => { cleared[key] = '' })
      this.conditions = cleared
      this.$emit('search', {})
    },
    handleRecalc () {
      this.$emit('recalc')
    },
    handleExport () {
      this.$emit('export', this.buildParams())
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.warning-filter {
  /* 条件区用 4 列网格：7 个条件正好两行，屏幕窄时退成 3 列（与查询统计页同一档位） */
  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px 16px;

    @media (max-width: 1600px) {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__tip {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
