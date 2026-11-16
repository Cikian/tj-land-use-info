<template>
  <!--
    ProjectPicker 出让宗地 → 配套项目 两级联动选择
    --------------------------------
    档案必须挂在「出让宗地 + 配套项目」上，所以新增/编辑档案的第一步就是选项目。
    两个下拉是**联动**的：先选宗地，配套项目下拉才可点，选项也只包含该宗地下的项目。

    v-model 契约（一个平铺的关联对象，与后端 Archive 实体的字段一一对应）：
      { landId, crzdbh, facilityId, ptxmmc, dkmc, xzqh, ptsslb }

    为什么不用 ScreenField 包一层：
      这里两个字段是并排的，且各自带摘要行，用 ScreenField 的「标签列 + 控件列」
      反而会挤压空间，所以改成自己一排两列、标签在控件上方（stacked）的排布。

    回显注意：编辑一条已有档案时，后端只给 id 与名称，不在下拉的候选列表里，
    所以 loadXxxOptions 之后要调用 ensureCurrentXxxInOptions() 把当前值补进选项，
    否则下拉会显示空白，用户以为项目丢了。

    ★ 为什么必须有 watcher（清单 P1-09 / P1-10）：
      编辑弹窗是「先打开、后回显」——created 时 value 里还没有 crzdbh，
      详情接口回来后父组件才把 value 整体替换。只在 created 里读一次的话，
      配套下拉永远是空的，表现就是「宗地下明明有配套，却提示暂无配套项目」。
      所以对 crzdbh / facilityId 各挂一个 watcher：宗地变了重拉配套，
      配套变了把当前项补进候选（当前项可能不在前 50 条里）。
  -->
  <div class="project-picker">
    <div class="project-picker__row">
      <!-- ---------- 出让宗地 ---------- -->
      <div class="project-picker__field">
        <span class="project-picker__label">
          出让宗地
          <span v-if="required" class="project-picker__required" aria-hidden="true">*</span>
        </span>
        <screen-select
          :id="landFieldId"
          :value="landId"
          :options="landOptions"
          :disabled="disabled"
          :invalid="required && !landId && showInvalid"
          :aria-label="'出让宗地' + (required ? '（必填）' : '')"
          placeholder="按宗地编号 / 地块名称搜索"
          searchable
          :filter-local="false"
          @change="handleLandChange"
          @search="handleLandSearch"
          @open="handleLandOpen"
        />
        <p class="project-picker__summary">
          <template v-if="landSummary !== '—'">{{ landSummary }}</template>
          <span v-else class="project-picker__hint">请选择该档案所属的出让宗地</span>
        </p>
      </div>

      <!-- ---------- 配套项目 ---------- -->
      <div class="project-picker__field">
        <span class="project-picker__label">
          配套项目
          <span v-if="required" class="project-picker__required" aria-hidden="true">*</span>
        </span>
        <screen-select
          :id="facilityFieldId"
          :value="facilityId"
          :options="facilityOptions"
          :disabled="disabled || !crzdbh"
          :invalid="required && !facilityId && showInvalid"
          :aria-label="'配套项目' + (required ? '（必填）' : '')"
          :placeholder="crzdbh ? '按项目名称搜索' : '请先选择出让宗地'"
          :empty-text="crzdbh ? '该宗地下暂无配套项目' : '请先选择出让宗地'"
          searchable
          :filter-local="false"
          @change="handleFacilityChange"
          @search="handleFacilitySearch"
          @open="handleFacilityOpen"
        />
        <p class="project-picker__summary">
          <template v-if="facilitySummary !== '—'">{{ facilitySummary }}</template>
          <span v-else-if="facilityLoadFailed" class="project-picker__warn">
            配套项目加载失败，请重试或到「数据管理」核对该宗地下的配套项目。
          </span>
          <span v-else-if="showEmptyFacilityWarn" class="project-picker__warn">
            该宗地下暂无配套项目，请先到「数据管理」录入配套项目后再建档案。
          </span>
          <span v-else-if="legacyCodeHint" class="project-picker__hint">
            {{ legacyCodeHint }}
          </span>
          <span v-else class="project-picker__hint">选择宗地后可选择配套项目</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
import { ScreenSelect } from '@/components/screen'
import { queryLandOptions, queryFacilityOptions } from '@/api/land/landData'
import { toast } from '@/components/screen/toast'
import { joinInfo } from '../constants'

/** 下拉一次拉取的最大条数（后端 limit 参数） */
const OPTION_LIMIT = 50

/**
 * 宗地编号的候选写法：原样 → 补/去末尾「号」。
 *
 * 为什么需要（清单 P1-10 第 5 条的「该宗地下暂无配套项目」误报）：
 *   `t_supporting_facilities.crzdbh = t_land.crzdbh` 是配套挂宗地的关联键，
 *   而历史数据里带不带末尾「号」两种写法都出现过。后端下拉接口是按
 *   `f.crzdbh = #{crzdbh}` **精确匹配**（FacilityMapper.xml selectOptions），
 *   所以档案里存的是「津北辰（挂）2018-015」、配套表里是「…015号」时，
 *   接口返回空数组，页面就报「暂无配套项目」——这属于历史数据的写法不一致。
 *
 * ★ 这里只做**读侧的一次补试**，不改写 value.crzdbh，也不做字符归一
 *   （写入侧归一是后端 DataSupport 的职责，见 AGENTS §2.4）。
 */
function codeVariants (code) {
  const raw = String(code || '').trim()
  if (!raw) return []
  const list = [raw]
  list.push(/号$/.test(raw) ? raw.slice(0, -1) : raw + '号')
  return list
}

export default {
  name: 'ProjectPicker',
  components: { ScreenSelect },
  props: {
    /** 关联对象（v-model），见文件头注释的字段契约 */
    value: { type: Object, default: () => ({}) },
    /** 是否必填（只影响标星与校验提示，实际校验在父组件） */
    required: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    /** 由父组件在校验失败时置 true，用于把两个下拉标红 */
    showInvalid: { type: Boolean, default: false }
  },
  data () {
    return {
      landOptions: [],
      facilityOptions: [],
      landLoading: false,
      facilityLoading: false,
      landLoadedOnce: false,
      /** 配套项目是否成功拉过一次：区分「还没加载」与「确实为空」 */
      facilityLoadedOnce: false,
      /** 配套项目拉取失败：失败要提示「加载失败」，不能显示成「暂无配套项目」 */
      facilityLoadFailed: false,
      /** 实际用于查询的宗地编号（历史数据可能缺末尾「号」，见 codeVariants） */
      facilityMatchCode: '',
      /** 正在请求中的宗地编号：用于去掉「父组件回写 value」带来的重复请求 */
      facilityPendingCode: '',
      /** 请求序号：切宗地后旧响应必须丢弃，否则慢请求会把新候选覆盖成旧宗地的 */
      facilitySeq: 0,
      landFieldId: 'archive-project-land',
      facilityFieldId: 'archive-project-facility'
    }
  },
  computed: {
    landId () {
      return this.value && this.value.landId ? this.value.landId : ''
    },
    facilityId () {
      return this.value && this.value.facilityId ? this.value.facilityId : ''
    },
    crzdbh () {
      return this.value && this.value.crzdbh ? this.value.crzdbh : ''
    },
    landSummary () {
      const data = this.value || {}
      return joinInfo(data.crzdbh, data.dkmc)
    },
    facilitySummary () {
      const data = this.value || {}
      return joinInfo(data.ptxmmc, data.ptsslb)
    },
    /**
     * 是否显示「该宗地下暂无配套项目」。
     * ★ 判空必须同时满足「已成功拉过一次」「没在加载」「候选确实为空」：
     *   原来只写 `crzdbh && !facilityOptions.length`，于是
     *   ① 编辑回显还没触发加载时 → 立刻误报；
     *   ② 请求失败时 → 也报成「暂无配套项目」（真实原因是接口没通）。
     *   这是清单 P1-10「明明有配套却提示暂无配套项目」的前端判空位置。
     */
    showEmptyFacilityWarn () {
      return !!this.crzdbh &&
        this.facilityLoadedOnce &&
        !this.facilityLoading &&
        !this.facilityLoadFailed &&
        !this.facilityOptions.length
    },
    /**
     * 历史数据写法不一致的提示。
     * 只有当「实际命中的宗地编号」与档案里存的写法不同时才出现，
     * 用来解释「为什么能选到配套、但编号看着不一样」，并指向数据清洗（不在前端转码）。
     */
    legacyCodeHint () {
      if (!this.facilityMatchCode) return ''
      if (this.facilityMatchCode === this.crzdbh) return ''
      return `按「${this.facilityMatchCode}」匹配到配套项目（档案里存的是「${this.crzdbh}」），建议清洗宗地编号。`
    }
  },
  watch: {
    /**
     * 编辑回显是异步的：打开弹窗时 crzdbh 还是空，详情回来后父组件才回写 value。
     * 不在这里重拉，配套下拉永远没有候选 —— 必须监听。
     */
    crzdbh (next, prev) {
      if ((next || '') === (prev || '')) return
      // 选宗地时本组件已经主动拉过一次，父组件回写 value 又触发 watcher：
      // 同一宗地同一时刻只保留一个请求，避免双发。
      if (this.facilityLoading && this.facilityPendingCode === (next || '')) return
      this.loadFacilityOptions('')
    },
    /** 当前配套项目变化（编辑回显 / 外部改写）：补进候选，否则下拉显示空白 */
    facilityId (next, prev) {
      if ((next || '') === (prev || '')) return
      this.ensureCurrentFacilityInOptions()
      // 候选还没加载过（例如详情回显早于宗地联动），顺手拉一次首批
      if (!this.facilityLoadedOnce && this.crzdbh) this.loadFacilityOptions('')
    }
  },
  created () {
    this.loadLandOptions('')
    // 编辑回显：已有宗地时要把配套项目也拉出来，否则下拉里没有当前项
    if (this.crzdbh) {
      this.loadFacilityOptions('')
    }
  },
  methods: {
    /* ---------------- 出让宗地 ---------------- */

    /** 首次展开时才加载，避免打开弹窗就发两次请求 */
    handleLandOpen () {
      if (!this.landLoadedOnce) this.loadLandOptions('')
    },
    handleLandSearch (keyword) {
      this.loadLandOptions(keyword)
    },
    loadLandOptions (keyword) {
      this.landLoading = true
      queryLandOptions({ keyword: keyword || undefined, limit: OPTION_LIMIT })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '出让宗地加载失败')
            return
          }
          this.landLoadedOnce = true
          this.landOptions = (res.result || []).map((item) => ({
            value: item.id,
            label: joinInfo(item.crzdbh, item.dkmc, ' · '),
            raw: item
          }))
          this.ensureCurrentLandInOptions()
        })
        .finally(() => {
          this.landLoading = false
        })
    },
    /**
     * 编辑回显：当前值不在候选里时补一条，保证下拉能显示宗地编号而不是空白。
     * facilityCount 未知，按 0 处理只用于展示。
     */
    ensureCurrentLandInOptions () {
      const data = this.value || {}
      if (!data.landId) return
      if (this.landOptions.some((option) => option.value === data.landId)) return
      this.landOptions = [
        {
          value: data.landId,
          label: joinInfo(data.crzdbh, data.dkmc, ' · '),
          raw: { id: data.landId, crzdbh: data.crzdbh, dkmc: data.dkmc, xzqh: data.xzqh }
        },
        ...this.landOptions
      ]
    },
    /** 选宗地：清空配套项目，并立刻按新宗地拉配套项目 */
    handleLandChange (landId) {
      if (!landId) {
        this.emitValue({
          landId: null,
          crzdbh: null,
          facilityId: null,
          ptxmmc: null,
          dkmc: null,
          xzqh: null,
          ptsslb: null
        })
        this.resetFacilityState()
        return
      }

      const option = this.landOptions.find((item) => item.value === landId)
      const raw = option ? option.raw || {} : {}
      this.emitValue({
        landId,
        crzdbh: raw.crzdbh || null,
        facilityId: null,
        ptxmmc: null,
        dkmc: raw.dkmc || null,
        xzqh: raw.xzqh || null,
        ptsslb: null
      })
      this.loadFacilityOptions('')
    },

    /* ---------------- 配套项目 ---------------- */

    handleFacilityOpen () {
      if (this.crzdbh) this.loadFacilityOptions('')
    },
    handleFacilitySearch (keyword) {
      if (!this.crzdbh) return
      this.loadFacilityOptions(keyword)
    },
    loadFacilityOptions (keyword) {
      if (!this.crzdbh) {
        this.resetFacilityState()
        return
      }
      const seq = ++this.facilitySeq
      this.facilityPendingCode = this.crzdbh
      this.facilityLoadFailed = false
      const codes = codeVariants(this.crzdbh)
      const attempt = (index) => {
        if (seq !== this.facilitySeq) return
        if (index >= codes.length) {
          // 两种写法都没命中：候选必须是空的（不能留着上一个宗地的旧候选），
          // 只把当前已选项补回来，保证下拉显示的是名称而不是空白
          this.finishFacilityLoad(seq, codes[0], [])
          return
        }
        const code = codes[index]
        this.facilityLoading = true
        queryFacilityOptions({ crzdbh: code, keyword: keyword || undefined, limit: OPTION_LIMIT })
          .then((res) => {
            if (seq !== this.facilitySeq) return
            if (!res || !res.success) {
              toast.error((res && res.message) || '配套项目加载失败')
              this.facilityLoadFailed = true
              this.finishFacilityLoad(seq, code, [])
              return
            }
            const list = res.result || []
            // 首个写法没命中时补试另一种写法（历史数据「号」差异），命中即用
            if (!list.length && index < codes.length - 1) {
              attempt(index + 1)
              return
            }
            this.finishFacilityLoad(seq, code, list)
          })
          .catch(() => {
            if (seq !== this.facilitySeq) return
            this.facilityLoadFailed = true
            this.finishFacilityLoad(seq, code, [])
          })
      }
      attempt(0)
    },
    /** 清空配套相关的全部状态（宗地被清空时） */
    resetFacilityState () {
      this.facilitySeq += 1
      this.facilityOptions = []
      this.facilityLoadedOnce = false
      this.facilityLoadFailed = false
      this.facilityMatchCode = ''
      this.facilityPendingCode = ''
      this.facilityLoading = false
    },
    /**
     * 收口一次加载的最终状态：候选整体替换（不留旧候选），再补当前已选项。
     * @param {number} seq 请求序号（过期响应直接丢弃）
     * @param {string} code 本次实际命中的宗地编号
     * @param {Array} list 后端返回的配套项目
     */
    finishFacilityLoad (seq, code, list) {
      if (seq !== this.facilitySeq) return
      this.facilityLoading = false
      this.facilityLoadedOnce = true
      this.facilityMatchCode = code || ''
      this.facilityOptions = (list || []).map((item) => ({
        value: item.id,
        label: joinInfo(item.ptxmmc, item.ptsslb, ' · '),
        raw: item
      }))
      this.ensureCurrentFacilityInOptions()
    },
    /**
     * 把当前已选项补进候选。
     * ★ label 只能用**可读名称**：名称缺失时退到宗地编号，绝不拿长数字 ID 当名称
     *   （清单 P1-09 明令：ID 冒充名称不算修复）。
     */
    ensureCurrentFacilityInOptions () {
      const data = this.value || {}
      if (!data.facilityId) return
      if (this.facilityOptions.some((option) => option.value === data.facilityId)) return
      const readable = joinInfo(data.ptxmmc, data.ptsslb, ' · ')
      const label = readable !== '—'
        ? readable
        : (data.crzdbh || '当前已选配套项目（名称待补）')
      this.facilityOptions = [
        {
          value: data.facilityId,
          label,
          raw: {
            id: data.facilityId,
            ptxmmc: data.ptxmmc,
            crzdbh: data.crzdbh,
            dkmc: data.dkmc,
            ptsslb: data.ptsslb,
            xzqh: data.xzqh
          }
        },
        ...this.facilityOptions
      ]
    },
    /** 选配套项目：把项目上的冗余字段一起回填到关联对象 */
    handleFacilityChange (facilityId) {
      const base = Object.assign({}, this.value || {})
      if (!facilityId) {
        this.emitValue(Object.assign(base, { facilityId: null, ptxmmc: null, ptsslb: null }))
        return
      }
      const option = this.facilityOptions.find((item) => item.value === facilityId)
      const raw = option ? option.raw || {} : {}
      this.emitValue(
        Object.assign(base, {
          facilityId,
          ptxmmc: raw.ptxmmc || null,
          crzdbh: raw.crzdbh || base.crzdbh || null,
          dkmc: raw.dkmc || base.dkmc || null,
          xzqh: raw.xzqh || base.xzqh || null,
          ptsslb: raw.ptsslb || null
        })
      )
    },

    /** 统一出口：v-model 双事件（input 供 v-model，change 供业务监听） */
    emitValue (value) {
      this.$emit('input', value)
      this.$emit('change', value)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.project-picker {
  &__row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-4);
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: 5px;
    min-width: 0;
  }

  &__label {
    display: flex;
    align-items: center;
    gap: 2px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__required {
    line-height: 1;
    color: var(--screen-danger);
  }

  &__summary {
    margin: 0;
    min-height: 22px;
    font-size: var(--screen-font-xs);
    line-height: 22px;
    color: var(--screen-accent-soft);
    .screen-ellipsis();
  }

  &__hint {
    color: var(--screen-text-mute);
  }

  &__warn {
    color: var(--screen-warning);
  }
}

// 窄屏（调试窗口 / 小屏大屏）两个字段改为上下排列
@media (max-width: 900px) {
  .project-picker__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
