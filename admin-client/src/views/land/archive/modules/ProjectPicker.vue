<template>
  <div class="project-picker">
    <div class="project-picker__row">
      <!-- 第一步：出让宗地 -->
      <div class="project-picker__field">
        <label class="project-picker__label">
          出让宗地
          <span v-if="required" class="project-picker__required">*</span>
        </label>
        <a-select
          :value="landId"
          show-search
          allowClear
          :disabled="disabled"
          placeholder="按宗地编号 / 地块名称搜索"
          :filterOption="false"
          :notFoundContent="landLoading ? '加载中…' : '无匹配宗地'"
          @search="handleLandSearch"
          @focus="handleLandFocus"
          @change="handleLandChange">
          <a-select-option v-for="item in landOptions" :key="item.id" :value="item.id">
            <div class="project-picker__option">
              <span class="project-picker__option-main">{{ item.crzdbh }}</span>
              <span class="project-picker__option-sub">{{ item.dkmc || '—' }}</span>
              <a-tag class="project-picker__option-tag" :color="facilityTagColor(item.facilityCount)">
                {{ item.facilityCount }} 个配套
              </a-tag>
            </div>
          </a-select-option>
        </a-select>
        <div v-if="landSummary" class="project-picker__summary">{{ landSummary }}</div>
      </div>

      <!-- 第二步：配套项目（联动） -->
      <div class="project-picker__field">
        <label class="project-picker__label">
          配套项目
          <span v-if="required" class="project-picker__required">*</span>
        </label>
        <a-select
          :value="facilityId"
          show-search
          allowClear
          :disabled="disabled || !crzdbh"
          :placeholder="crzdbh ? '选择该宗地下的配套项目' : '请先选择出让宗地'"
          :filterOption="false"
          :notFoundContent="facilityLoading ? '加载中…' : (crzdbh ? '该宗地下没有配套项目' : '请先选择出让宗地')"
          @search="handleFacilitySearch"
          @change="handleFacilityChange">
          <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
            <div class="project-picker__option">
              <span class="project-picker__option-main">{{ item.ptxmmc }}</span>
              <span class="project-picker__option-sub">{{ item.ptsslb || '—' }}</span>
            </div>
          </a-select-option>
        </a-select>
        <div v-if="facilitySummary" class="project-picker__summary">{{ facilitySummary }}</div>
        <!--
          ★ 判空必须同时满足「已成功拉过一次 + 没在加载 + 候选确实为空」：
            只写 `crzdbh && !facilityOptions.length` 会在编辑回显触发加载之前就误报
            「该宗地下暂无配套项目」，也会把接口失败说成「没有数据」（清单 P1-10）。
        -->
        <div
          v-else-if="facilityLoadFailed"
          class="project-picker__summary project-picker__summary--warn">
          配套项目加载失败，请重试或到「数据管理」核对该宗地下的配套项目。
        </div>
        <div
          v-else-if="showEmptyFacilityWarn"
          class="project-picker__summary project-picker__summary--warn">
          该宗地下暂无配套项目，请先到「数据管理」录入配套项目后再建档案。
        </div>
        <!-- 补/去末尾「号」才命中时的一行说明，理由见 legacyCodeHint 的注释 -->
        <div v-else-if="legacyCodeHint" class="project-picker__summary project-picker__summary--warn">
          {{ legacyCodeHint }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import { queryLandOptions, queryFacilityOptions } from '@/api/land/landData'

  /**
   * 宗地编号的候选写法：原样 → 补/去末尾「号」（清单 P1-09，与大屏同名组件等价）。
   *
   * ★ 为什么需要补试：`t_supporting_facilities.crzdbh = t_land.crzdbh` 是配套挂宗地的
   *   关联键，而后端下拉接口（FacilityMapper.xml selectOptions）是按
   *   `f.crzdbh = #{crzdbh}` **精确匹配**。历史数据里「带号 / 不带号」两种写法都出现过
   *   （AGENTS §2.4 实测 1366 条缺「号」、58 条配套因此挂不上宗地），
   *   于是档案里存「津北辰（挂）2018-015」、配套表里是「…015号」时，
   *   接口返回空数组 → 页面报「该宗地下暂无配套项目」，用户以为数据丢了。
   *
   * ★ 只做**读侧的一次补试**：不改写 value.crzdbh、不做字符归一
   *   （写入侧归一是后端 DataSupport 的职责，前端重复做会造出第二套口径）。
   *   也不改本组件的 props / 对外事件 —— 补试命中的编号只用于本次查询与提示。
   */
  function codeVariants (code) {
    const raw = String(code || '').trim()
    if (!raw) {
      return []
    }
    const list = [raw]
    list.push(/号$/.test(raw) ? raw.slice(0, -1) : raw + '号')
    return list
  }

  /**
   * 出让宗地 → 配套项目 两级联动选择器
   *
   * 需求约定：「项目来自 xj_kjkfb_supporting_facilities」，且要先选宗地再联动选项目
   * （宗地表 t_land 由 nutzwk_ywk.xj_kjkfb_commercial_land 迁移而来，
   *  配套表按 crzdbh 与宗地关联，实测关联率 95.8%）。
   *
   * 对外用 v-model 绑定一个对象：
   *   { landId, crzdbh, facilityId, ptxmmc, dkmc, xzqh, ptsslb }
   * 父组件提交时直接把这个对象展开到表单即可，服务端会用 facilityId 再校验一次。
   *
   * ★ 刻意<b>不使用 a-form-model 包裹</b>：本组件总是被放进新增/编辑弹窗的表单里，
   *   再套一层 a-form-model 会渲染出嵌套的 &lt;form&gt; 标签，
   *   浏览器会把内层 form 丢弃，导致布局与校验行为不可预期。
   *   因此这里用普通 div + label 排版，与父表单的表单项视觉保持一致。
   *
   * ★ 两个关联链不得互换（清单 P1-09 第 4 条）：
   *   `t_archive.facility_id`            —— 档案/台账属于哪个配套项目（项目归属，本组件维护）；
   *   `t_completion_archive.archive_id`  —— 竣工历史档案指定的扫描件档案，与本组件无关。
   *
   * ★ 为什么必须有 watcher（清单 P1-09 / P1-10）：编辑弹窗是「先打开、后回显」——
   *   created 时 value 里还没有 crzdbh，详情接口回来后父组件才整体替换 value。
   *   只在 created 读一次的话配套下拉永远是空的，表现就是「宗地下明明有配套，
   *   却提示该宗地下暂无配套项目」。
   */
  export default {
    name: 'ProjectPicker',
    props: {
      /** v-model：关联对象 */
      value: {
        type: Object,
        default: () => ({})
      },
      required: {
        type: Boolean,
        default: true
      },
      /** 是否禁用（详情只读） */
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        landOptions: [],
        facilityOptions: [],
        landLoading: false,
        facilityLoading: false,
        landLoadedOnce: false,
        /** 配套项目是否成功拉过一次：用来区分「还没加载」和「确实为空」 */
        facilityLoadedOnce: false,
        /** 配套项目拉取失败：失败要提示加载失败，不能显示成「暂无配套项目」 */
        facilityLoadFailed: false,
        /** 实际命中配套的宗地编号（可能补/去了末尾「号」，见 codeVariants） */
        facilityMatchCode: '',
        /** 正在请求中的宗地编号：去掉「父组件回写 value」带来的重复请求 */
        facilityPendingCode: '',
        /** 请求序号：切宗地后旧响应必须丢弃，否则慢请求会把新候选覆盖成旧宗地的 */
        facilitySeq: 0
      }
    },
    computed: {
      landId () {
        return this.value && this.value.landId ? this.value.landId : undefined
      },
      facilityId () {
        return this.value && this.value.facilityId ? this.value.facilityId : undefined
      },
      crzdbh () {
        return this.value && this.value.crzdbh ? this.value.crzdbh : ''
      },
      landSummary () {
        const v = this.value || {}
        if (!v.crzdbh) {
          return ''
        }
        return [v.crzdbh, v.dkmc, v.xzqh].filter(Boolean).join(' · ')
      },
      facilitySummary () {
        const v = this.value || {}
        if (!v.ptxmmc) {
          return ''
        }
        return [v.ptxmmc, v.ptsslb].filter(Boolean).join(' · ')
      },
      /** 是否显示「该宗地下暂无配套项目」（见模板上的注释） */
      showEmptyFacilityWarn () {
        return !!this.crzdbh &&
          this.facilityLoadedOnce &&
          !this.facilityLoading &&
          !this.facilityLoadFailed &&
          !this.facilityOptions.length
      },
      /**
       * 补试命中时说明「按哪个编号取到的配套」。
       * ★ 为什么要把这件事显示出来：补试成功意味着**档案上的编号与配套表不一致**，
       *   这是数据要清洗的信号（AGENTS §2.4）。只静默补试，问题会一直藏着；
       *   报错又太重 —— 用户此刻只想把档案建完，所以只给一行提示。
       */
      legacyCodeHint () {
        if (!this.facilityMatchCode) {
          return ''
        }
        if (this.facilityMatchCode === this.crzdbh) {
          return ''
        }
        return `按「${this.facilityMatchCode}」匹配到配套项目（档案里存的是「${this.crzdbh}」），建议清洗宗地编号。`
      }
    },
    watch: {
      /**
       * 编辑回显是异步的：打开弹窗时 value 里还没有 crzdbh，详情回来后父组件才回写。
       * 不在这里重拉，配套下拉就永远是空的（清单 P1-09 第 2 条）。
       */
      crzdbh (next, prev) {
        if ((next || '') === (prev || '')) {
          return
        }
        // 选宗地时本组件已主动拉过一次，父组件回写 value 会再触发 watcher：同宗地只保留一个请求
        if (this.facilityLoading && this.facilityPendingCode === (next || '')) {
          return
        }
        this.loadFacilityOptions('')
      },
      /** 当前配套项目变化（编辑回显 / 外部改写）：补进候选，否则下拉显示空白 */
      facilityId (next, prev) {
        if ((next || '') === (prev || '')) {
          return
        }
        this.ensureCurrentFacilityInOptions()
        // 候选还没加载过（例如详情回显早于宗地联动）时顺手拉一次首批
        if (!this.facilityLoadedOnce && this.crzdbh) {
          this.loadFacilityOptions('')
        }
      }
    },
    created () {
      // 编辑回显：先把两个下拉的数据补齐，否则 a-select 只显示 id 而不是名称
      this.loadLandOptions('')
      if (this.crzdbh) {
        this.loadFacilityOptions('')
      }
    },
    methods: {
      handleLandFocus () {
        if (!this.landLoadedOnce) {
          this.loadLandOptions('')
        }
      },
      handleLandSearch (keyword) {
        this.loadLandOptions(keyword)
      },
      loadLandOptions (keyword) {
        this.landLoading = true
        return queryLandOptions({ keyword: keyword || undefined, limit: 50 }).then(res => {
          if (res.success) {
            this.landOptions = res.result || []
            this.landLoadedOnce = true
            this.ensureCurrentLandInOptions()
          }
        }).finally(() => {
          this.landLoading = false
        })
      },
      ensureCurrentLandInOptions () {
        const v = this.value || {}
        if (!v.landId || !v.crzdbh) {
          return
        }
        if (this.landOptions.some(item => item.id === v.landId)) {
          return
        }
        this.landOptions = [{
          id: v.landId,
          crzdbh: v.crzdbh,
          dkmc: v.dkmc,
          xzqh: v.xzqh,
          xmfl: v.xmfl,
          facilityCount: 0
        }].concat(this.landOptions)
      },
      handleLandChange (landId) {
        if (!landId) {
          this.facilityOptions = []
          this.emitValue({
            landId: null,
            crzdbh: null,
            facilityId: null,
            ptxmmc: null,
            dkmc: null,
            xzqh: null,
            ptsslb: null
          })
          return
        }
        const land = this.landOptions.find(item => item.id === landId)
        const next = {
          landId: landId,
          crzdbh: land ? land.crzdbh : null,
          facilityId: null,
          ptxmmc: null,
          dkmc: land ? land.dkmc : null,
          xzqh: land ? land.xzqh : null,
          ptsslb: null
        }
        this.facilityOptions = []
        if (next.crzdbh) {
          this.loadFacilityOptions('')
        }
        this.emitValue(next)
      },
      handleFacilitySearch (keyword) {
        this.loadFacilityOptions(keyword)
      },
      loadFacilityOptions (keyword) {
        if (!this.crzdbh) {
          this.resetFacilityState()
          return Promise.resolve()
        }
        const seq = ++this.facilitySeq
        this.facilityPendingCode = this.crzdbh
        this.facilityLoadFailed = false
        const codes = codeVariants(this.crzdbh)
        // 逐个写法尝试：原样查不到再补/去末尾「号」试一次（历史数据写法不一致，见 codeVariants）
        const attempt = index => {
          // 过期响应直接丢弃：慢请求不能把新宗地的候选覆盖成旧宗地的
          if (seq !== this.facilitySeq) {
            return
          }
          if (index >= codes.length) {
            // 两种写法都没命中：候选必须清空（不能留着上一个宗地的旧候选），
            // 只把当前已选项补回来，保证下拉显示的是名称而不是空白
            this.finishFacilityLoad(seq, codes[0], [])
            return
          }
          const code = codes[index]
          this.facilityLoading = true
          return queryFacilityOptions({ crzdbh: code, keyword: keyword || undefined, limit: 50 })
            .then(res => {
              if (seq !== this.facilitySeq) {
                return
              }
              if (!res || !res.success) {
                this.facilityLoadFailed = true
                this.finishFacilityLoad(seq, code, [])
                return
              }
              const list = res.result || []
              // 首个写法没命中时补试另一种写法，命中即用
              if (!list.length && index < codes.length - 1) {
                return attempt(index + 1)
              }
              this.finishFacilityLoad(seq, code, list)
            })
            .catch(() => {
              if (seq !== this.facilitySeq) {
                return
              }
              this.facilityLoadFailed = true
              this.finishFacilityLoad(seq, code, [])
            })
        }
        return attempt(0)
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
        if (seq !== this.facilitySeq) {
          return
        }
        this.facilityLoading = false
        this.facilityLoadedOnce = true
        this.facilityMatchCode = code || ''
        this.facilityOptions = list || []
        this.ensureCurrentFacilityInOptions()
      },
      /**
       * 把当前已选项补进候选。
       * ★ label 只用**可读名称**：名称缺失时退到宗地编号，最后给占位文案，
       *   **绝不**拿长数字 ID 当名称（清单 P1-09 明令：ID 冒充名称不算修复）。
       *   原来要求 `v.ptxmmc` 才补，名称字段缺失时下拉直接空白 —— 一并修掉。
       */
      ensureCurrentFacilityInOptions () {
        const v = this.value || {}
        if (!v.facilityId) {
          return
        }
        if (this.facilityOptions.some(item => item.id === v.facilityId)) {
          return
        }
        this.facilityOptions = [{
          id: v.facilityId,
          ptxmmc: v.ptxmmc || v.crzdbh || '当前已选配套项目（名称待补）',
          crzdbh: v.crzdbh,
          dkmc: v.dkmc,
          ptsslb: v.ptsslb,
          xzqh: v.xzqh
        }].concat(this.facilityOptions)
      },
      handleFacilityChange (facilityId) {
        const base = Object.assign({}, this.value)
        if (!facilityId) {
          this.emitValue(Object.assign(base, {
            facilityId: null,
            ptxmmc: null,
            ptsslb: null
          }))
          return
        }
        const facility = this.facilityOptions.find(item => item.id === facilityId)
        this.emitValue(Object.assign(base, {
          facilityId: facilityId,
          ptxmmc: facility ? facility.ptxmmc : null,
          crzdbh: facility ? facility.crzdbh : base.crzdbh,
          dkmc: facility ? facility.dkmc : base.dkmc,
          xzqh: facility ? facility.xzqh : base.xzqh,
          ptsslb: facility ? facility.ptsslb : null
        }))
      },
      emitValue (value) {
        this.$emit('input', value)
        this.$emit('change', value)
      },
      facilityTagColor (count) {
        if (!count) {
          // ★ 返回 undefined 走 .ant-tag 默认灰；不能写 'default'（非 antd 预设色 → 白底白字看不见）
          return undefined
        }
        return count > 1 ? 'blue' : 'green'
      }
    }
  }
</script>

<style lang="less" scoped>
  .project-picker {
    &__row {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
    }

    &__field {
      flex: 1 1 320px;
      min-width: 0;
    }

    &__label {
      display: block;
      margin-bottom: 6px;
      font-size: 14px;
      line-height: 20px;
      color: #475569;
    }

    &__required {
      margin-left: 2px;
      color: #f5222d;
    }

    &__option {
      display: flex;
      align-items: center;
      gap: 8px;
      overflow: hidden;
    }

    &__option-main {
      flex: 0 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 500;
      color: #0f172a;
    }

    &__option-sub {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
      color: #94a3b8;
    }

    &__option-tag {
      flex-shrink: 0;
      margin: 0;
      font-size: 11px;
      line-height: 16px;
    }

    &__summary {
      margin-top: 4px;
      font-size: 12px;
      line-height: 18px;
      color: #64789a;

      &--warn {
        color: #d48806;
      }
    }
  }
</style>
