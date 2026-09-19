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
        <div
          v-else-if="crzdbh && !facilityOptions.length && !facilityLoading"
          class="project-picker__summary project-picker__summary--warn">
          该宗地下暂无配套项目，请先到「数据管理」录入配套项目后再建档案。
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import { queryLandOptions, queryFacilityOptions } from '@/api/land/landData'

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
        landLoadedOnce: false
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
          this.facilityOptions = []
          return Promise.resolve()
        }
        this.facilityLoading = true
        return queryFacilityOptions({ crzdbh: this.crzdbh, keyword: keyword || undefined, limit: 50 })
          .then(res => {
            if (res.success) {
              this.facilityOptions = res.result || []
              this.ensureCurrentFacilityInOptions()
            }
          }).finally(() => {
            this.facilityLoading = false
          })
      },
      ensureCurrentFacilityInOptions () {
        const v = this.value || {}
        if (!v.facilityId || !v.ptxmmc) {
          return
        }
        if (this.facilityOptions.some(item => item.id === v.facilityId)) {
          return
        }
        this.facilityOptions = [{
          id: v.facilityId,
          ptxmmc: v.ptxmmc,
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
