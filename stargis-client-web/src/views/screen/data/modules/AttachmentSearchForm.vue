<template>
  <!--
    AttachmentSearchForm 附件检索条件
    --------------------------------
    条件字段与后端 `AttachmentQueryDTO` 逐个对应（后端不用 QueryGenerator，
    没声明的字段名会被静默忽略）：
      bizType            归属类型（land / facility / process）
      bizId              归属对象的唯一 id（精确）—— ★ 只由下钻预置，**不给用户填**
      bizKey             所属对象（模糊：出让宗地编号 / 配套项目名称）
      fileType           材料类型码（精确：01~13、99）
      keyword            ★ 文件名模糊 —— 后端字段名是 keyword 不是 fileName
      uploadBy           上传人账号（精确）
      beginDate / endDate 上传时间区间

    ★ 界面文案原则（2026-10-09 按反馈统一）：**不出现「id / 主键 / 可读键」这类数据库概念**。
      用户看到的是「归属类型」「所属对象（名称）」；bizId 只是下钻时的一个隐形过滤条件，
      需要让用户知道「当前正筛着哪个对象」时，回显的是**名称**而不是 id。

    ★ 为什么「所属对象」用名称模糊检索就能满足需求：
      落库时 biz_key 存的就是可读名称（宗地编号 / 配套项目名称），后端对该列 LIKE，
      所以用户输入「凯苑路」或「津西青(挂)2024-01号」都能搜到 ——
      原来的「业务主键」文本框要求用户粘贴数据库 id，对用户毫无意义。

    ★ 为什么「上传人」是**文本输入**而不是人员下拉：
      后端要的是账号（uploadBy）精确匹配，而这个接口没有人员列表可查；
      做成下拉要么再引一个用户接口（越权面变大），要么只能搜当前登录人。
      文本输入 + placeholder 说明「填账号」，是这里最诚实的做法。

    事件：
      search (query)  点击「查询」或按回车时抛出，query 已剔除空值
      reset           点击「重置」时抛出（同时也会抛 search，父组件只需监听 search）

    公开方法：
      getQuery()  取当前条件（已剔除空值）
      setQuery(q) 用一组新条件覆盖（供下钻 / applyDrill 使用）
  -->
  <div class="attachment-search">
    <div class="attachment-search__grid">
      <screen-field label="归属类型" label-width="96px">
        <screen-select
          v-model="query.bizType"
          :options="bizTypeOptions"
          placeholder="全部"
          aria-label="归属类型"
        />
      </screen-field>

      <screen-field label="文件名" label-width="96px" html-for="as-keyword">
        <screen-input
          id="as-keyword"
          v-model="query.keyword"
          clearable
          placeholder="按文件名搜索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="材料类型" label-width="96px">
        <screen-select
          v-model="query.fileType"
          :options="typeOptions"
          placeholder="全部"
          aria-label="材料类型"
        />
      </screen-field>

      <screen-field label="所属对象" label-width="96px" html-for="as-bizKey">
        <screen-input
          id="as-bizKey"
          v-model="query.bizKey"
          clearable
          placeholder="出让宗地编号或配套项目名称"
          @enter="handleSearch"
        />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="上传时间" label-width="96px">
          <screen-date-input v-model="dateRange" mode="range" @change="handleDateChange" />
        </screen-field>

        <screen-field label="上传人" label-width="96px" html-for="as-uploadBy">
          <screen-input
            id="as-uploadBy"
            v-model="query.uploadBy"
            clearable
            placeholder="登录账号"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <div class="attachment-search__foot">
      <button
        type="button"
        class="attachment-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="attachment-search__actions">
        <screen-button icon="rotate-ccw" @click="handleReset">重置</screen-button>
        <screen-button type="primary" icon="search" @click="handleSearch">查询</screen-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenButton,
  ScreenIcon
} from '@/components/screen'
import { ATTACHMENT_BIZ_TYPES, queryAllowedAttachmentTypes, ATTACHMENT_TYPES_FALLBACK } from '@/api/land/attachment'
import { queryLandDictItems } from '@/api/land/landAdmin'
import { dictToOptions, compactQuery, dictDefinitions } from '../constants'

/** 所有检索字段的初始空值，重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    bizType: '',
    bizId: '',
    bizKey: '',
    fileType: '',
    keyword: '',
    uploadBy: '',
    beginDate: '',
    endDate: ''
  }
}

export default {
  name: 'AttachmentSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon
  },
  props: {
    defaultExpanded: { type: Boolean, default: false },
    /**
     * 下钻条件。★ 用户看到的是 bizKey/bizName（可读名称，落在「所属对象」输入框里），
     * bizId 只是跟随它的精确过滤；两者由下面 watch 保持同步。
     */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      /**
       * 下钻带入的「所属对象」名称原值。
       * 用它判断用户是否动过名称框（见 watch）：名称仍是这个值时，
       * 跟着它的 bizId 过滤就是有效的，应当保留。
       */
      presetBizKey: (this.initialQuery || {}).bizKey || '',
      dateRange: [],
      bizTypeOptions: ATTACHMENT_BIZ_TYPES,
      // 先用本地兜底（01~13、99），created 里再用字典 / allowedTypes 覆盖
      typeOptions: ATTACHMENT_TYPES_FALLBACK.map((item) => ({ value: item.value, label: item.text }))
    }
  },
  watch: {
    /**
     * ★ bizId 必须跟着「所属对象」输入框走，否则会出现**用户看不见却仍在生效**的过滤。
     *
     * 背景：下钻进来时父组件会带上 {@code bizId}（精确 id，只列这个对象的附件）。
     * 但 id 是数据库内部值、界面上不显示；用户唯一能看到的相关控件就是「所属对象」
     * 文本框（对应 bizKey，落库时存的就是可读名称）。若是两者脱钩：
     *   ① 用户清空「所属对象」→ bizId 还留着，列表仍被悄悄过滤（用户以为清空了）；
     *   ② 用户改成别的名称 → 变成了 {@code biz_id = 旧对象 AND biz_key LIKE 新名称}，
     *      这个组合**永远搜不到东西**，用户会以为「系统坏了」。
     *
     * 处理规则：**名称框一旦偏离下钻带入的那个值（改了或清空），就撤掉隐性 id 过滤**，
     * 让「界面上看到什么，就按什么筛」始终成立。
     * 只有名称仍是下钻那个值时才保留 bizId —— 那时它是对的（同一个对象，精确比模糊更准）。
     */
    'query.bizKey' (value) {
      if (String(value || '').trim() === String(this.presetBizKey || '').trim()) {
        return
      }
      this.query.bizId = ''
    }
  },
  created () {
    this.loadTypes()
    // 回显日期区间：initialQuery 里是分开的 beginDate / endDate，这里合成一个数组
    if (this.query.beginDate || this.query.endDate) {
      this.dateRange = [this.query.beginDate || '', this.query.endDate || '']
    }
    if (this.query.bizId || this.query.bizKey) {
      // 从配套详情跳过来时这些条件藏起来了，直接展开，否则用户看不到生效中的过滤
      this.expanded = true
    }
  },
  methods: {
    /**
     * 附件类型选项。
     * ★ 优先用专用接口 `/land/data/attachment/allowedTypes`（它给的是
     *   { value, text }，与字典同构），失败再退到字典，最后是本地兜底。
     *   三层兜底的原因：附件类型是 13 类且会扩，用户一旦选不到想要的类型
     *   就只能归到「其他」，那等于数据分类失效。
     */
    loadTypes () {
      queryAllowedAttachmentTypes()
        .then((res) => {
          if (res && res.success && res.result && res.result.length) {
            this.typeOptions = dictToOptions(res.result)
            return true
          }
          return false
        })
        .catch(() => false)
        .then((done) => {
          if (done) return
          queryLandDictItems(dictDefinitions()).then((dicts) => {
            if (dicts.attachType) this.typeOptions = dicts.attachType
          })
        })
    },

    handleDateChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginDate = values[0] || ''
      this.query.endDate = values[1] || ''
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.dateRange = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态（与档案模块行为一致）
      this.$emit('search', this.getQuery())
    },

    getQuery () {
      return compactQuery(this.query)
    },

    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), query || {})
      this.query = next
      this.dateRange = next.beginDate || next.endDate ? [next.beginDate || '', next.endDate || ''] : []
      const hidden = ['beginDate', 'endDate', 'uploadBy', 'bizId']
      if (hidden.some((key) => next[key] !== '' && next[key] !== undefined && next[key] !== null)) {
        this.expanded = true
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.attachment-search {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
  }
}

@media (max-width: 1800px) {
  .attachment-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .attachment-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .attachment-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .attachment-search__foot {
    flex-wrap: wrap;
  }
}
</style>
