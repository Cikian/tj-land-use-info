<template>
  <!--
    数据管理
    进入后地图保持可见，只在左侧显示功能菜单。
    点击菜单项后，用弹窗打开对应的添加或查询页面。
  -->
  <div class="data-screen">
    <nav class="data-screen__nav stage-hit" aria-label="数据管理菜单">
      <button
        v-for="item in menus"
        :key="item.key"
        type="button"
        class="data-screen__item"
        :class="{ 'is-active': activeKey === item.key }"
        @click="open(item.key)"
      >
        <i class="data-screen__bar" aria-hidden="true" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <screen-modal
      :visible="activeKey === 'add'"
      title="经营性用地添加"
      :width="1180"
      :show-footer="false"
      @cancel="close"
    >
      <land-form v-if="activeKey === 'add'" ref="form" @saved="saved" />
    </screen-modal>

    <screen-modal
      :visible="activeKey === 'list'"
      title="经营性用地查询"
      :width="1280"
      :show-footer="false"
      @cancel="close"
    >
      <land-list v-if="activeKey === 'list'" @edit="edit" />
    </screen-modal>

    <screen-modal
      :visible="activeKey === 'facility-add'"
      title="配套项目添加"
      :width="1280"
      :show-footer="false"
      @cancel="close"
    >
      <facility-form v-if="activeKey === 'facility-add'" ref="facilityForm" @saved="saved" />
    </screen-modal>

    <screen-modal
      :visible="activeKey === 'facility-list'"
      title="配套项目查询"
      :width="1280"
      :show-footer="false"
      @cancel="close"
    >
      <facility-list v-if="activeKey === 'facility-list'" @edit="editFacility" />
    </screen-modal>
  </div>
</template>

<script>
import { ScreenModal } from '@/components/screen'
import FacilityForm from './FacilityForm.vue'
import FacilityList from './FacilityList.vue'
import LandForm from './LandForm.vue'
import LandList from './LandList.vue'

export default {
  name: 'DataScreen',
  components: { ScreenModal, FacilityForm, FacilityList, LandForm, LandList },
  data () {
    return {
      activeKey: '',
      editing: null,
      menus: [
        { key: 'add', label: '经营性用地添加' },
        { key: 'list', label: '经营性用地查询' },
        { key: 'facility-add', label: '配套项目添加' },
        { key: 'facility-list', label: '配套项目查询' },
      ],
    }
  },
  methods: {
    open (key) {
      this.editing = null
      this.activeKey = key
    },
    close () {
      this.activeKey = ''
      this.editing = null
    },
    edit (row) {
      this.editing = row
      this.activeKey = 'add'
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.fill(row)
      })
    },
    editFacility (row) {
      this.editing = row
      this.activeKey = 'facility-add'
      this.$nextTick(() => {
        if (this.$refs.facilityForm) this.$refs.facilityForm.fill(row)
      })
    },
    saved () {
      this.close()
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.data-screen {
  position: absolute;
  inset: 0;
  pointer-events: none;

  &__nav {
    position: absolute;
    left: 30px;
    top: 149px;
    width: 220px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px;
    pointer-events: auto;
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow), var(--screen-shadow-inset);
  }

  &__item {
    position: relative;
    height: 44px;
    padding: 0 12px 0 18px;
    font-size: var(--screen-font-md);
    line-height: 44px;
    color: var(--screen-text-sub);
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: var(--screen-radius-sm);
    cursor: pointer;

    &.is-active,
    &:hover {
      color: #ffffff;
      background: rgba(130, 198, 255, 0.14);
    }
  }

  &__bar {
    position: absolute;
    left: 6px;
    top: 13px;
    width: 3px;
    height: 18px;
    border-radius: var(--screen-radius-pill);
    background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    opacity: 0;
  }

  &__item.is-active &__bar {
    opacity: 1;
  }
}
</style>
