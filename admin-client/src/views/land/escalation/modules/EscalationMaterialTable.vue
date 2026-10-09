<template>
  <div class="escalation-material">
    <!-- 工具条 -->
    <div v-if="!disabled" class="escalation-material__toolbar">
      <div class="escalation-material__toolbar-item">
        <span class="escalation-material__label">材料类型 <i class="escalation-material__required">*</i></span>
        <j-dict-select-tag
          v-model="uploadType"
          :dictCode="DICT.materialType"
          placeholder="先选类型，再上传" />
      </div>
      <a-checkbox v-model="forceSupplement" class="escalation-material__check">作为补充材料</a-checkbox>
      <a-upload
        :action="uploadAction"
        :headers="headers"
        :data="{ biz: bizPath }"
        :multiple="true"
        :showUploadList="false"
        :beforeUpload="beforeUpload"
        :disabled="uploading"
        @change="handleUploadChange">
        <a-button type="primary" icon="upload" :loading="uploading">上传材料</a-button>
      </a-upload>
      <span class="escalation-material__hint">
        允许 {{ allowedExtText }}，单文件不超过 {{ maxSizeMb }}MB；同一材料类型的第 2 次上传自动记为「补充材料」并升版本号
      </span>
    </div>

    <a-table
      size="small"
      rowKey="rowKey"
      :columns="columns"
      :dataSource="rows"
      :pagination="false"
      :locale="{ emptyText: disabled ? '该项目还没有上传材料' : '还没有材料，请先选择材料类型再上传' }">
      <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>

      <template slot="materialType" slot-scope="text">
        <span v-if="text">{{ dictLabel(DICT.materialType, text) }}</span>
        <span v-else class="escalation-material__danger">未选类型</span>
      </template>

      <template slot="fileName" slot-scope="text, record">
        <a class="escalation-material__file" @click="handlePreview(record)">{{ text }}</a>
      </template>

      <template slot="fileSize" slot-scope="text, record">
        {{ record.readableSize || formatSize(text) }}
      </template>

      <!-- 版本号 + 补充材料标记 -->
      <template slot="version" slot-scope="text, record">
        <span class="escalation-material__version">v{{ text || 1 }}</span>
        <a-tag v-if="record.isSupplement" color="orange" class="escalation-material__supplement">补充材料</a-tag>
      </template>

      <template slot="uploadTime" slot-scope="text, record">
        {{ record.uploadTime || text || '—' }}
      </template>

      <template slot="action" slot-scope="text, record">
        <a v-has="'land:escalation:download'" @click="handleDownload(record)">下载</a>
        <span v-has="'land:escalation:download'">
          <a-divider type="vertical" />
          <a @click="handlePreview(record)">预览</a>
        </span>
        <template v-if="!disabled">
          <span v-has="'land:escalation:edit'">
            <a-divider type="vertical" />
            <a-popconfirm title="确定删除该材料吗？" okText="确定" cancelText="取消" @confirm="handleRemove(record)">
              <a class="escalation-material__danger">删除</a>
            </a-popconfirm>
          </span>
        </template>
      </template>
    </a-table>

    <div class="escalation-material__footer">
      共 <b>{{ rows.length }}</b> 个材料，合计 <b>{{ totalSizeText }}</b>
      <span v-if="missingTypeCount" class="escalation-material__warn">
        · 有 {{ missingTypeCount }} 个材料还没选择材料类型
      </span>
      <span v-else-if="supplementCount" class="escalation-material__muted">
        · 其中补充材料 {{ supplementCount }} 个
      </span>
    </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { ACCESS_TOKEN } from '@/store/mutation-types'
  import { getFileAccessHttpUrl } from '@/api/manage'
  import JDictSelectTag from '@/components/dict/JDictSelectTag'
  import {
    DICT,
    MATERIAL_ALLOWED_EXT,
    MATERIAL_ALLOWED_EXT_TEXT,
    MATERIAL_MAX_SIZE_MB,
    deleteMaterial,
    downloadMaterial,
    formatSize,
    dictText,
    previewMaterial,
    queryMaterialList,
    resolveExt,
    saveMaterial
  } from '@/api/land/escalation'

  /**
   * 提级论证材料表格（设计文档 5.2 / 5.4 第三步）
   *
   * 两种形态，靠 projectId 是否传入自动切换（调用方不用关心）：
   *  1. 录入向导内（projectId 为空）—— 纯本地数组 + v-model，材料随主表一起提交；
   *  2. 已落库项目（审批页 / 详情页传入 projectId）—— 自己拉取列表，上传/删除直接调材料接口。
   *
   * 约定（设计文档 5.4）：材料类型必选、多文件、至少 1 个；同一类型第 2 次上传自动
   * 记 is_supplement=1 且 version+1，也可手工勾「作为补充材料」强制标记。
   */
  export default {
    name: 'EscalationMaterialTable',
    components: { JDictSelectTag },
    props: {
      /** v-model：本地模式下的材料数组 */
      value: {
        type: Array,
        default: () => []
      },
      /** 已落库项目 ID；传入即进入「远程模式」 */
      projectId: {
        type: String,
        default: ''
      },
      /** 只读（详情页 / 无 edit 权限时） */
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        DICT,
        uploading: false,
        uploadType: undefined,
        forceSupplement: false,
        remoteRows: [],
        maxSizeMb: MATERIAL_MAX_SIZE_MB,
        allowedExt: MATERIAL_ALLOWED_EXT,
        allowedExtText: MATERIAL_ALLOWED_EXT_TEXT,
        uploadAction: window._CONFIG['domianURL'] + '/sys/common/upload',
        headers: {}
      }
    },
    computed: {
      remoteMode () {
        return !!this.projectId
      },
      rows () {
        const list = this.remoteMode ? this.remoteRows : (this.value || [])
        return list.map((item, index) => Object.assign({}, item, {
          rowKey: item.id || item.storePath || ('tmp-' + index)
        }))
      },
      columns () {
        return [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '材料类型', dataIndex: 'materialType', width: 140, scopedSlots: { customRender: 'materialType' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '大小', dataIndex: 'fileSize', width: 100, scopedSlots: { customRender: 'fileSize' } },
          { title: '版本', dataIndex: 'version', width: 150, scopedSlots: { customRender: 'version' } },
          { title: '上传人', dataIndex: 'uploadBy', width: 110, customRender: text => text || '—' },
          { title: '上传时间', dataIndex: 'uploadTime', width: 160, scopedSlots: { customRender: 'uploadTime' } },
          { title: '操作', width: 160, scopedSlots: { customRender: 'action' } }
        ]
      },
      totalSizeText () {
        const total = this.rows.reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
        return formatSize(total)
      },
      missingTypeCount () {
        return this.rows.filter(item => !item.materialType).length
      },
      supplementCount () {
        return this.rows.filter(item => item.isSupplement).length
      },
      /** 上传子目录：/escalation/{yyyy}/{MM} */
      bizPath () {
        const now = new Date()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        return `/escalation/${now.getFullYear()}/${month}`
      }
    },
    watch: {
      projectId: {
        immediate: true,
        handler () {
          if (this.remoteMode) {
            this.loadRemote()
          }
        }
      }
    },
    created () {
      this.headers = { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) }
    },
    methods: {
      formatSize,
      dictLabel (dictCode, value) {
        return dictText(dictCode, value) || value
      },
      // ------------------------------------------------------------------
      // 远程模式
      // ------------------------------------------------------------------
      loadRemote () {
        if (!this.projectId) {
          return Promise.resolve()
        }
        return queryMaterialList(this.projectId).then(res => {
          if (res.success) {
            this.remoteRows = res.result || []
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      /** 供父组件刷新（例如审批页登记意见后材料数变化） */
      refresh () {
        return this.remoteMode ? this.loadRemote() : Promise.resolve()
      },
      // ------------------------------------------------------------------
      // 上传
      // ------------------------------------------------------------------
      beforeUpload (file) {
        if (!this.uploadType) {
          this.$message.warning('请先选择「材料类型」，再上传材料')
          return false
        }
        const ext = resolveExt(file.name)
        if (!ext || this.allowedExt.indexOf(ext) === -1) {
          this.$message.error(`不支持的文件格式「${ext || '未知'}」，请上传 ${this.allowedExtText}`)
          return false
        }
        if (file.size > this.maxSizeMb * 1024 * 1024) {
          this.$message.error(`文件「${file.name}」超过 ${this.maxSizeMb}MB 限制`)
          return false
        }
        this.uploading = true
        return true
      },
      handleUploadChange (info) {
        const status = info.file && info.file.status
        if (status === 'uploading') {
          return
        }
        this.uploading = false
        if (status === 'error') {
          this.$message.error(`文件「${info.file.name}」上传失败，请检查后端服务与上传目录权限`)
          return
        }
        if (status !== 'done') {
          return
        }
        const res = info.file.response || {}
        if (res.success === false) {
          this.$message.error(res.message || `文件「${info.file.name}」上传失败`)
          return
        }
        // jeecg 通用上传接口把相对路径放在 message 里
        const storePath = res.message || res.result
        if (!storePath) {
          this.$message.error(`文件「${info.file.name}」上传成功但未返回存储路径`)
          return
        }
        const file = this.buildMaterial(info.file, storePath)
        if (this.remoteMode) {
          saveMaterial(Object.assign({ projectId: this.projectId }, file)).then(saveRes => {
            if (saveRes.success) {
              this.$message.success(`「${info.file.name}」上传成功`)
              this.loadRemote()
              this.$emit('change')
            } else {
              this.$message.warning(saveRes.message)
            }
          })
          return
        }
        this.$emit('input', (this.value || []).concat([file]))
        this.$message.success(`「${info.file.name}」上传成功`)
        this.$emit('change')
      },
      /** 版本号与补充标记：同一材料类型第 n 次上传 → version=n，n>1 记为补充材料 */
      buildMaterial (file, storePath) {
        const sameType = this.rows.filter(item => item.materialType === this.uploadType)
        const version = sameType.length + 1
        return {
          id: null,
          materialType: this.uploadType,
          fileName: file.name,
          fileExt: resolveExt(file.name),
          fileSize: file.size,
          storeType: 'local',
          storePath: storePath,
          previewPath: null,
          version: version,
          isSupplement: this.forceSupplement || version > 1 ? 1 : 0,
          uploadBy: this.currentRealname(),
          uploadTime: this.nowText()
        }
      },
      // ------------------------------------------------------------------
      // 行内操作
      // ------------------------------------------------------------------
      handleDownload (record) {
        if (record.id) {
          downloadMaterial(record.id)
          return
        }
        const url = getFileAccessHttpUrl(record.storePath)
        if (url) {
          window.open(url, '_blank')
        }
      },
      handlePreview (record) {
        if (record.id) {
          previewMaterial(record.id)
          return
        }
        const url = getFileAccessHttpUrl(record.previewPath || record.storePath)
        if (url) {
          window.open(url, '_blank')
        }
      },
      handleRemove (record) {
        if (this.remoteMode) {
          if (!record.id) {
            return
          }
          deleteMaterial(record.id).then(res => {
            if (res.success) {
              this.$message.success(res.message || '删除成功')
              this.loadRemote()
              this.$emit('change')
            } else {
              this.$message.warning(res.message)
            }
          })
          return
        }
        const next = (this.value || []).filter(item => {
          if (item.id && record.id) {
            return item.id !== record.id
          }
          return item.storePath !== record.storePath
        })
        this.$emit('input', next)
        this.$emit('change')
      },
      // ------------------------------------------------------------------
      // 校验（父组件提交前调用）
      // ------------------------------------------------------------------
      /**
       * @returns {string} 校验不通过的原因；通过时返回空串
       */
      validate () {
        const list = this.rows
        if (!list.length) {
          return '请至少上传一个提级论证材料'
        }
        const missing = list.filter(item => !item.materialType)
        if (missing.length) {
          return `有 ${missing.length} 个材料还没选择材料类型，请补齐后再提交`
        }
        return ''
      },
      /** 汇总给主表提交的材料数组（本地模式） */
      collect () {
        return (this.value || []).map(item => ({
          id: item.id || null,
          materialType: item.materialType,
          fileName: item.fileName,
          fileExt: item.fileExt || resolveExt(item.fileName),
          fileSize: item.fileSize,
          fileMd5: item.fileMd5 || null,
          storeType: item.storeType || 'local',
          storePath: item.storePath,
          previewPath: item.previewPath || null,
          version: item.version || 1,
          isSupplement: item.isSupplement ? 1 : 0
        }))
      },
      // ------------------------------------------------------------------
      // 工具
      // ------------------------------------------------------------------
      currentRealname () {
        const info = this.$store.getters.userInfo || {}
        return info.realname || info.username || ''
      },
      nowText () {
        const d = new Date()
        const pad = n => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .escalation-material {
    &__toolbar {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
      padding: 12px;
      margin-bottom: 12px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 6px;
    }

    &__toolbar-item {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 300px;
    }

    &__label {
      flex-shrink: 0;
      font-size: 13px;
      color: #475569;
    }

    &__required {
      color: #ff4d4f;
      font-style: normal;
    }

    &__check {
      font-size: 13px;
      color: #475569;
    }

    &__hint {
      flex: 1 1 240px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    &__file {
      font-weight: 500;
    }

    &__version {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      color: #0f172a;
    }

    &__supplement {
      margin-left: 6px;
    }

    &__muted {
      color: @text-weak;
    }

    &__warn {
      color: #d48806;
    }

    &__danger {
      color: #ff4d4f;
    }

    &__footer {
      margin-top: 8px;
      font-size: 12px;
      color: #475569;

      b {
        color: #0f172a;
      }
    }
  }
</style>
