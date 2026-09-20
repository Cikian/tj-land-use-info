<template>
  <!--
    CategoryFormModal 档案类别新增 / 编辑弹窗
    --------------------------------
    类别树是一个「主键路径（path）」结构的树：新增时可以指定上级类别，
    编辑时可以移动整棵树（后端会重算子孙节点的 path 与 level）。

    公开方法：
      show(record, parentRecord)
        record      有 id 时为「编辑」，否则为「新增」
        parentRecord 新增时用来预置上级类别（在某个节点下点「新增子类别」）
      事件：ok(payload) —— 保存成功后抛出，父组件据此刷新类别树
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="620"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="category-form">
      <screen-field
        label="上级类别"
        :label-width="'84px'"
        tip="留空表示新建一级类别；选择某个类别则作为它的子类别，可随时再调整。"
      >
        <screen-tree-select
          :value="model.parentId"
          :nodes="parentNodes"
          :disabled="parentLoading"
          placeholder="留空 = 一级类别"
          aria-label="上级类别"
          :keyword="parentKeyword"
          @input="model.parentId = $event"
        />
      </screen-field>

      <screen-field
        label="类别名称"
        required
        :label-width="'84px'"
        :error="errors.name"
        html-for="cat-name"
      >
        <screen-input
          id="cat-name"
          v-model="model.name"
          :maxlength="50"
          :invalid="!!errors.name"
          placeholder="例如：项目建议书批复"
          @blur="validateName"
          @enter="validateName"
        />
      </screen-field>

      <screen-field label="类别编码" :label-width="'84px'" html-for="cat-code" tip="按档案规范填写的检索码或分类号，可选">
        <screen-input id="cat-code" v-model="model.code" :maxlength="50" placeholder="可选" />
      </screen-field>

      <screen-field label="别名/拼音码" :label-width="'84px'" html-for="cat-alias" tip="便于按简称或拼音检索，可选">
        <screen-input id="cat-alias" v-model="model.aliasName" :maxlength="100" placeholder="可选" />
      </screen-field>

      <screen-field
        label="同级排序"
        :label-width="'84px'"
        html-for="cat-sort"
        tip="数字越小越靠前；留空时后端会追加到同级末尾。"
      >
        <screen-input id="cat-sort" v-model="model.sortNo" type="number" :min="1" placeholder="可选" />
      </screen-field>

      <screen-field label="状态" :label-width="'84px'" tip="停用会级联停用整棵子树；停用后该类别不能再挂新的卷内文件。">
        <screen-radio-group
          v-model="model.status"
          :options="statusOptions"
          aria-label="类别状态"
        />
      </screen-field>

      <screen-field label="类别说明" :label-width="'84px'" html-for="cat-note">
        <screen-input
          id="cat-note"
          v-model="model.note"
          type="textarea"
          :rows="2"
          :maxlength="500"
          placeholder="说明该类别的归档范围，可选"
        />
      </screen-field>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenTreeSelect,
  ScreenRadioGroup,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  addArchiveCategory,
  editArchiveCategory,
  checkArchiveCategoryName,
  queryArchiveCategoryTree,
} from '@/api/land/archiveCategory'

export default {
  name: 'CategoryFormModal',
  components: { ScreenModal, ScreenField, ScreenInput, ScreenTreeSelect, ScreenRadioGroup },
  data () {
    return {
      visible: false,
      title: '新增档案类别',
      saving: false,
      parentLoading: false,
      /** 上级类别候选树（已排除自身及其子树，避免把类别挂到自己下面） */
      parentNodes: [],
      parentKeyword: '',
      errors: {},
      model: this.buildEmptyModel(),
      statusOptions: [
        { value: 1, label: '启用' },
        { value: 0, label: '停用' },
      ],
    }
  },
  computed: {
    isEdit () {
      return !!this.model.id
    },
  },
  methods: {
    buildEmptyModel () {
      return {
        id: undefined,
        parentId: '',
        name: '',
        code: '',
        aliasName: '',
        note: '',
        sortNo: '',
        status: 1,
      }
    },

    /* ---------------- 对外入口 ---------------- */

    /**
     * @param {object|null} record 编辑时传入现有类别；新增时传 null
     * @param {object|null} parentRecord 新增子类别时传入上级类别
     */
    show (record, parentRecord) {
      const editing = !!(record && record.id)
      this.title = editing ? '编辑档案类别' : (parentRecord ? '新增子类别' : '新增一级类别')
      this.errors = {}

      if (editing) {
        this.model = {
          id: record.id,
          parentId: record.parentId || '',
          name: record.name || '',
          code: record.code || '',
          aliasName: record.aliasName || '',
          note: record.note || '',
          sortNo: record.sortNo === undefined || record.sortNo === null ? '' : String(record.sortNo),
          status: record.status === 0 ? 0 : 1,
        }
      } else {
        this.model = this.buildEmptyModel()
        if (parentRecord && parentRecord.id) {
          this.model.parentId = parentRecord.id
        }
      }

      this.visible = true
      this.loadParentTree()
    },

    /**
     * 上级类别候选：用 excludeId 让后端剪掉自身及其子树。
     * 这样即使不额外做本地校验，用户也不可能把类别挂到自己的后代下面。
     */
    loadParentTree () {
      this.parentLoading = true
      queryArchiveCategoryTree(this.model.id ? { excludeId: this.model.id } : {})
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '上级类别加载失败')
            return
          }
          this.parentNodes = res.result || []
        })
        .finally(() => {
          this.parentLoading = false
        })
    },

    /* ---------------- 校验 ---------------- */

    /** 同级重名校验（后端接口，编辑时排除自身） */
    validateName () {
      const name = String(this.model.name || '').trim()
      if (!name) {
        this.errors = Object.assign({}, this.errors, { name: '请输入类别名称' })
        return Promise.resolve(false)
      }
      return checkArchiveCategoryName({
        parentId: this.normalizeParent(),
        name,
        id: this.model.id,
      })
        .then((res) => {
          if (res && res.success) {
            this.clearError('name')
            return true
          }
          this.errors = Object.assign({}, this.errors, { name: (res && res.message) || '同级下名称重复' })
          return false
        })
        .catch(() => {
          // 校验接口异常时不阻断保存：最终一致性由后端的唯一约束保证
          this.clearError('name')
          return true
        })
    },

    clearError (key) {
      const next = Object.assign({}, this.errors)
      delete next[key]
      this.errors = next
    },

    /**
     * 统一 parentId 形态。
     * 后端约定：null / 空串 = 一级类别；编辑时把一级类别写成 '0'（后端按移动处理）。
     */
    normalizeParent () {
      const parentId = this.model.parentId
      if (parentId === null || parentId === undefined || parentId === '' || parentId === 0 || parentId === '0') {
        return this.isEdit ? '0' : null
      }
      return parentId
    },

    /* ---------------- 保存 ---------------- */

    handleOk () {
      const name = String(this.model.name || '').trim()
      if (!name) {
        this.errors = { name: '请输入类别名称' }
        toast.warning('请填写类别名称')
        return
      }

      // 保存前再跑一次重名校验，避免用户输入后没触发 blur 就按了保存
      this.validateName().then((valid) => {
        if (!valid) {
          toast.warning(this.errors.name || '同级下名称重复')
          return
        }
        this.submit(name)
      })
    },

    submit (name) {
      const sortNo = parseInt(this.model.sortNo, 10)
      const payload = {
        id: this.model.id,
        parentId: this.normalizeParent(),
        name,
        code: String(this.model.code || '').trim(),
        aliasName: String(this.model.aliasName || '').trim(),
        note: String(this.model.note || '').trim(),
        sortNo: Number.isFinite(sortNo) ? sortNo : null,
        status: this.model.status,
      }

      this.saving = true
      const request = this.isEdit ? editArchiveCategory(payload) : addArchiveCategory(payload)

      request
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '保存失败')
            return
          }
          toast.success(res.message || '保存成功')
          this.visible = false
          this.$emit('ok', payload)
        })
        .catch(() => {
          // 错误提示已由请求层处理，这里只需保证不残留 loading 状态
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      this.visible = false
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.category-form {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
}
</style>
