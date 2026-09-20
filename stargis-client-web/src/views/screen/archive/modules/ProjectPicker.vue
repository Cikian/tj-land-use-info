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
          <span v-else-if="crzdbh" class="project-picker__warn">
            该宗地下暂无配套项目，请先到「数据管理」录入配套项目后再建档案。
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
    showInvalid: { type: Boolean, default: false },
  },
  data () {
    return {
      landOptions: [],
      facilityOptions: [],
      landLoading: false,
      facilityLoading: false,
      landLoadedOnce: false,
      landFieldId: 'archive-project-land',
      facilityFieldId: 'archive-project-facility',
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
            raw: item,
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
          raw: { id: data.landId, crzdbh: data.crzdbh, dkmc: data.dkmc, xzqh: data.xzqh },
        },
        ...this.landOptions,
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
          ptsslb: null,
        })
        this.facilityOptions = []
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
        ptsslb: null,
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
        this.facilityOptions = []
        return
      }
      this.facilityLoading = true
      queryFacilityOptions({ crzdbh: this.crzdbh, keyword: keyword || undefined, limit: OPTION_LIMIT })
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '配套项目加载失败')
            return
          }
          this.facilityOptions = (res.result || []).map((item) => ({
            value: item.id,
            label: joinInfo(item.ptxmmc, item.ptsslb, ' · '),
            raw: item,
          }))
          this.ensureCurrentFacilityInOptions()
        })
        .finally(() => {
          this.facilityLoading = false
        })
    },
    ensureCurrentFacilityInOptions () {
      const data = this.value || {}
      if (!data.facilityId) return
      if (this.facilityOptions.some((option) => option.value === data.facilityId)) return
      this.facilityOptions = [
        {
          value: data.facilityId,
          label: joinInfo(data.ptxmmc, data.ptsslb, ' · '),
          raw: { id: data.facilityId, ptxmmc: data.ptxmmc, crzdbh: data.crzdbh, dkmc: data.dkmc, ptsslb: data.ptsslb, xzqh: data.xzqh },
        },
        ...this.facilityOptions,
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
          ptsslb: raw.ptsslb || null,
        })
      )
    },

    /** 统一出口：v-model 双事件（input 供 v-model，change 供业务监听） */
    emitValue (value) {
      this.$emit('input', value)
      this.$emit('change', value)
    },
  },
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
    min-height: 16px;
    font-size: var(--screen-font-xs);
    line-height: 16px;
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
