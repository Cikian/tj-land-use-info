<template>
  <a-modal
    :title="title"
    :width="640"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :destroyOnClose="true"
    okText="保存"
    cancelText="取消"
    @ok="handleOk()"
    @cancel="handleCancel()">
    <a-spin :spinning="confirmLoading">
      <a-form-model ref="form" :model="model" :rules="validatorRules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-form-model-item label="上级类别" prop="parentId">
          <a-tree-select
            v-model="model.parentId"
            :treeData="parentTreeData"
            :dropdownStyle="{ maxHeight: '320px', overflow: 'auto' }"
            placeholder="不选则创建为一级类别"
            allowClear
            treeDefaultExpandAll
            :loading="parentLoading"
            :getPopupContainer="getPopupContainer" />
          <div class="form-item-tip">变更上级类别即为「移动」该类别及其全部子类别。</div>
        </a-form-model-item>

        <a-form-model-item label="类别名称" prop="name" hasFeedback>
          <a-input v-model="model.name" :maxLength="50" placeholder="例如：基本建设手续" allowClear />
        </a-form-model-item>

        <a-form-model-item label="类别编码" prop="code">
          <a-input v-model="model.code" :maxLength="50" placeholder="预留对接档案标准，可留空" allowClear />
        </a-form-model-item>

        <a-form-model-item label="别名/拼音码" prop="aliasName">
          <a-input v-model="model.aliasName" :maxLength="100" placeholder="便于检索，可留空" allowClear />
        </a-form-model-item>

        <a-form-model-item label="同级排序" prop="sortNo">
          <a-input-number v-model="model.sortNo" :min="1" :precision="0" style="width: 160px" placeholder="数值越小越靠前" />
        </a-form-model-item>

        <a-form-model-item label="状态" prop="status">
          <a-radio-group v-model="model.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
          <div class="form-item-tip">停用后不可在其下新建类别；停用父类别会连同子类别一起停用。</div>
        </a-form-model-item>

        <a-form-model-item label="类别说明" prop="note">
          <a-textarea v-model="model.note" :rows="3" :maxLength="500" placeholder="该类别收纳哪些档案要件，便于后续录入时对照" />
        </a-form-model-item>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { addArchiveCategory, editArchiveCategory, checkArchiveCategoryName, queryArchiveCategoryTree } from '@/api/land/archiveCategory'

  /**
   * 档案类别 新建/编辑 弹窗（方案 2.3.2 第 1 项）
   */
  export default {
    name: 'ArchiveCategoryModal',
    data () {
      return {
        title: '新增档案类别',
        visible: false,
        confirmLoading: false,
        parentLoading: false,
        model: this.buildEmptyModel(),
        parentTreeData: [],
        labelCol: { xs: { span: 24 }, sm: { span: 6 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 16 } }
      }
    },
    computed: {
      validatorRules () {
        const that = this
        return {
          name: [
            { required: true, message: '请输入类别名称', trigger: 'blur' },
            { validator: that.validateNameUnique, trigger: 'blur' }
          ]
        }
      }
    },
    methods: {
      buildEmptyModel () {
        return {
          id: undefined,
          parentId: undefined,
          name: '',
          code: '',
          aliasName: '',
          note: '',
          sortNo: undefined,
          status: 1
        }
      },
      /**
       * 打开弹窗：record 为空表示新建；parentRecord 用于新建子类别。
       *
       * 这里只认「带 id 的类别记录」——调用方如果写成 @click="handleEdit"（不带括号），
       * Vue 会把手事件对象传进来，直接 Object.assign 到 model 上就会得到一张空表单。
       */
      show (record, parentRecord) {
        const target = (record && record.id) ? record : null
        const parent = (parentRecord && parentRecord.id) ? parentRecord : null

        this.model = Object.assign(this.buildEmptyModel(), target || {})
        if (target) {
          this.title = '编辑档案类别'
          if (this.model.status === undefined || this.model.status === null) {
            this.model.status = 1
          }
        } else {
          this.title = parent ? '新增子类别' : '新增一级类别'
          if (parent) {
            this.model.parentId = parent.id
          }
        }
        this.visible = true
        this.$nextTick(() => {
          this.$refs.form && this.$refs.form.clearValidate()
        })
        this.loadParentTree()
      },
      /** 上级类别下拉数据：不带过滤条件；编辑时剪掉自己这棵子树，避免把类别移到自己的下级 */
      loadParentTree () {
        this.parentLoading = true
        queryArchiveCategoryTree({ excludeId: this.model.id })
          .then(res => {
            if (res.success) {
              this.parentTreeData = this.buildSelectTree(res.result || [])
            } else {
              this.$message.warning(res.message)
            }
          })
          .finally(() => {
            this.parentLoading = false
          })
      },
      buildSelectTree (list) {
        return (list || []).map(node => {
          const item = {
            title: node.name,
            value: node.id,
            key: node.id,
            // 停用的类别不允许作为新的上级
            disabled: node.status === 0
          }
          if (node.children && node.children.length) {
            item.children = this.buildSelectTree(node.children)
          }
          return item
        })
      },
      /** 同级重名校验走后端，规则与后端完全一致 */
      validateNameUnique (rule, value, callback) {
        const name = (value || '').trim()
        if (!name) {
          callback()
          return
        }
        checkArchiveCategoryName({
          parentId: this.model.parentId,
          name: name,
          id: this.model.id
        }).then(res => {
          if (res.success) {
            callback()
          } else {
            callback(new Error(res.message || '同级下名称重复'))
          }
        }).catch(() => {
          // 网络异常时不拦截，交由后端在保存时兜底校验
          callback()
        })
      },
      handleOk () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          this.confirmLoading = true
          const isEdit = !!this.model.id
          const payload = {
            id: this.model.id,
            // 编辑时用 '0' 显式表示「移动到顶级」；新增时留空即为顶级
            parentId: this.model.parentId ? this.model.parentId : (isEdit ? '0' : null),
            name: (this.model.name || '').trim(),
            code: (this.model.code || '').trim(),
            aliasName: (this.model.aliasName || '').trim(),
            note: (this.model.note || '').trim(),
            sortNo: this.model.sortNo,
            status: this.model.status
          }
          const request = payload.id ? editArchiveCategory(payload) : addArchiveCategory(payload)
          request.then(res => {
            if (res.success) {
              this.$message.success(res.message || '保存成功')
              this.$emit('ok', payload)
              this.close()
            } else {
              this.$message.warning(res.message)
            }
          }).finally(() => {
            this.confirmLoading = false
          })
        })
      },
      handleCancel () {
        this.close()
      },
      close () {
        this.visible = false
        this.model = this.buildEmptyModel()
        this.parentTreeData = []
      },
      /** 弹窗挂到 body 上，避免被卡片容器裁剪 */
      getPopupContainer (triggerNode) {
        return triggerNode ? triggerNode.parentNode : document.body
      }
    }
  }
</script>

<style lang="less" scoped>
  .form-item-tip {
    margin-top: 4px;
    color: #8c9aae;
    font-size: 12px;
    line-height: 18px;
  }
</style>
