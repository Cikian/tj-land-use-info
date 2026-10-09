<template>
  <div class="record-panel">
    <!-- 顶部说明：既满足验收措辞，又不会让用户误以为系统会推进流程 -->
    <div class="record-panel__notice">
      <a-icon type="info-circle" />
      <span>流程在线下办理，此处仅登记论证结论与审核意见（登记只追加、不修改历史记录）。</span>
    </div>

    <a-form-model ref="form" :model="form" :label-col="labelCol" :wrapper-col="wrapperCol">
      <a-form-model-item label="记录类型">
        <a-select v-model="form.recordType" style="width: 100%">
          <a-select-option v-for="item in recordTypes" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
      </a-form-model-item>

      <a-form-model-item label="意见正文" required>
        <div class="record-panel__opinion">
          <a-textarea
            v-model="form.opinion"
            :rows="5"
            :maxLength="maxLength"
            placeholder="请填写论证结论与审核意见，不超过 1000 字" />
          <!-- ★ 原型硬性要素：0/1000 实时计数 -->
          <div class="record-panel__count" :class="{ 'record-panel__count--full': opinionLength >= maxLength }">
            {{ opinionLength }}/{{ maxLength }}
          </div>
        </div>
      </a-form-model-item>

      <a-form-model-item label="快捷短语">
        <div class="record-panel__phrases">
          <a-button
            v-for="phrase in quickPhrases"
            :key="phrase"
            size="small"
            :type="form.action === phrase ? 'primary' : 'default'"
            @click="applyPhrase(phrase)">{{ phrase }}</a-button>
        </div>
        <div class="record-panel__tip">
          点击短语回填到意见框（结论短语记为「{{ form.action || '未选择' }}」），回填后可继续编辑
        </div>
      </a-form-model-item>

      <a-form-model-item label="附件">
        <div class="record-panel__attach">
          <a-upload
            :action="uploadAction"
            :headers="headers"
            :data="{ biz: bizPath }"
            :multiple="true"
            :showUploadList="false"
            :beforeUpload="beforeUpload"
            :disabled="uploading"
            @change="handleUploadChange">
            <a-button icon="paper-clip" :loading="uploading">上传附件</a-button>
          </a-upload>
          <span class="record-panel__hint">
            {{ allowedExtText }}，单文件不超过 {{ maxSizeMb }}MB
          </span>
        </div>
        <ul v-if="attachments.length" class="record-panel__files">
          <li v-for="(item, index) in attachments" :key="item.storePath || index">
            <a-icon type="paper-clip" />
            <a class="record-panel__file-name" @click="openAttachment(item)">{{ item.fileName }}</a>
            <span class="record-panel__file-size">{{ formatSize(item.fileSize) }}</span>
            <a class="record-panel__file-remove" @click="removeAttachment(index)">移除</a>
          </li>
        </ul>
      </a-form-model-item>

      <a-form-model-item label="办理状态">
        <a-checkbox v-model="syncStatus">同时更新办理状态</a-checkbox>
        <a-select v-model="targetStatus" :disabled="!syncStatus" size="small" class="record-panel__status">
          <a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <div class="record-panel__tip">
          当前办理状态：<b>{{ currentStatus || '未办理' }}</b>；默认不勾选 —— 登记意见不会自动改办理状态（保持「系统只存不推」的口径）
        </div>
      </a-form-model-item>

      <a-form-model-item :wrapper-col="{ span: 24 }" class="record-panel__submit">
        <a-button
          v-has="'land:escalation:edit'"
          type="primary"
          icon="save"
          :loading="saving"
          :disabled="!projectId || !opinionLength"
          @click="handleSubmit">保存登记</a-button>
        <a-button class="record-panel__reset" @click="reset">清空</a-button>
      </a-form-model-item>
    </a-form-model>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { ACCESS_TOKEN } from '@/store/mutation-types'
  import { getFileAccessHttpUrl } from '@/api/manage'
  import {
    MATERIAL_ALLOWED_EXT,
    MATERIAL_ALLOWED_EXT_TEXT,
    MATERIAL_MAX_SIZE_MB,
    RECORD_MAX_LENGTH,
    RECORD_QUICK_PHRASES,
    RECORD_TYPES,
    STATUS_OPTIONS,
    addRecord,
    formatSize,
    resolveExt
  } from '@/api/land/escalation'

  /**
   * ★ 审核意见登记面板（设计文档 5.2 / 5.3「审批页与详情页共用」）
   *
   * 三个验收会点的硬性要素：
   *  ① 意见正文 1000 字上限 + 实时计数（0/1000）；
   *  ② 5 个快捷短语按钮：点击回填到意见框，**回填后仍可编辑**；
   *  ③ 附件上传 ≤50MB 且格式白名单与材料一致；
   * 另外：「同时更新办理状态」**默认不勾选**（设计文档 2.3：登记意见不自动改状态）。
   *
   * 附件口径：走后端 record/add 的 attachment_ids 字段（逗号分隔）。jeecg 通用上传接口
   * 返回的是相对存储路径而非业务 ID，因此这里直接把 storePath 作为附件标识存进
   * attachment_ids；已保存记录的附件用 jeecg 静态资源地址回看（记录接口不含下载端点）。
   */
  export default {
    name: 'EscalationRecordPanel',
    props: {
      /** 当前登记的目标项目 */
      projectId: {
        type: String,
        default: ''
      },
      /** 项目当前办理状态（仅展示用，勾选同步后可被覆盖） */
      currentStatus: {
        type: String,
        default: ''
      }
    },
    data () {
      return {
        saving: false,
        uploading: false,
        labelCol: { xs: { span: 24 }, sm: { span: 5 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 19 } },
        maxLength: RECORD_MAX_LENGTH,
        maxSizeMb: MATERIAL_MAX_SIZE_MB,
        allowedExt: MATERIAL_ALLOWED_EXT,
        allowedExtText: MATERIAL_ALLOWED_EXT_TEXT,
        quickPhrases: RECORD_QUICK_PHRASES,
        recordTypes: RECORD_TYPES,
        statusOptions: STATUS_OPTIONS,
        syncStatus: false,
        targetStatus: '已办结',
        attachments: [],
        uploadAction: window._CONFIG['domianURL'] + '/sys/common/upload',
        headers: {},
        form: this.buildEmptyForm()
      }
    },
    computed: {
      opinionLength () {
        return (this.form.opinion || '').length
      },
      bizPath () {
        const now = new Date()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        return `/escalation/opinion/${now.getFullYear()}/${month}`
      }
    },
    created () {
      this.headers = { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) }
    },
    methods: {
      formatSize,
      buildEmptyForm () {
        return {
          recordType: '审核意见',
          action: undefined,
          opinion: ''
        }
      },
      // ------------------------------------------------------------------
      // 快捷短语 / 计数
      // ------------------------------------------------------------------
      /**
       * 点击快捷短语：记为结论短语，并把短语文本回填到意见框（可再编辑）。
       * 意见框已有内容时按行追加，不覆盖用户已写的内容。
       */
      applyPhrase (phrase) {
        this.form.action = phrase
        const current = (this.form.opinion || '').trim()
        if (!current) {
          this.form.opinion = phrase
          return
        }
        if (current.indexOf(phrase) === -1) {
          this.form.opinion = `${current}\n${phrase}`
        }
      },
      // ------------------------------------------------------------------
      // 附件
      // ------------------------------------------------------------------
      beforeUpload (file) {
        const ext = resolveExt(file.name)
        if (!ext || this.allowedExt.indexOf(ext) === -1) {
          this.$message.error(`不支持的文件格式「${ext || '未知'}」，请上传 ${this.allowedExtText}`)
          return false
        }
        if (file.size > this.maxSizeMb * 1024 * 1024) {
          this.$message.error(`附件「${file.name}」超过 ${this.maxSizeMb}MB 限制`)
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
          this.$message.error(`附件「${info.file.name}」上传失败，请检查后端服务与上传目录权限`)
          return
        }
        if (status !== 'done') {
          return
        }
        const res = info.file.response || {}
        if (res.success === false) {
          this.$message.error(res.message || `附件「${info.file.name}」上传失败`)
          return
        }
        const storePath = res.message || res.result
        if (!storePath) {
          this.$message.error(`附件「${info.file.name}」上传成功但未返回存储路径`)
          return
        }
        this.attachments.push({
          fileName: info.file.name,
          fileSize: info.file.size,
          storePath: storePath
        })
        this.$message.success(`附件「${info.file.name}」上传成功`)
      },
      removeAttachment (index) {
        this.attachments.splice(index, 1)
      },
      /** 未落库附件走静态资源地址（上传接口已把文件放到 jeecg.path.upload 下） */
      openAttachment (item) {
        const url = getFileAccessHttpUrl(item.storePath)
        if (url) {
          window.open(url, '_blank')
        }
      },
      // ------------------------------------------------------------------
      // 提交
      // ------------------------------------------------------------------
      reset () {
        this.form = this.buildEmptyForm()
        this.attachments = []
        this.syncStatus = false
        this.targetStatus = '已办结'
      },
      handleSubmit () {
        if (!this.projectId) {
          this.$message.warning('请先在左侧选择一个项目')
          return
        }
        const opinion = (this.form.opinion || '').trim()
        if (!opinion) {
          this.$message.warning('请填写意见正文')
          return
        }
        if (opinion.length > this.maxLength) {
          this.$message.warning(`意见正文不能超过 ${this.maxLength} 字`)
          return
        }
        const payload = {
          projectId: this.projectId,
          recordType: this.form.recordType || '审核意见',
          action: this.form.action || undefined,
          opinion: opinion,
          attachmentIds: this.attachments.length
            ? this.attachments.map(item => item.storePath).join(',')
            : undefined
        }
        // ★ 只有勾选了才传 status：默认不勾选 = 不改办理状态
        if (this.syncStatus && this.targetStatus) {
          payload.status = this.targetStatus
        }
        this.saving = true
        addRecord(payload).then(res => {
          if (res.success) {
            this.$message.success(res.message || '登记成功')
            this.reset()
            this.$emit('ok', payload)
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.saving = false
        })
      },
      /** 供父组件在切换项目时清空草稿 */
      clearDraft () {
        this.reset()
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .record-panel {
    &__notice {
      display: flex;
      align-items: flex-start;
      gap: 6px;
      padding: 10px 12px;
      margin-bottom: 14px;
      font-size: 12px;
      line-height: 18px;
      color: #92400e;
      background: #fffbeb;
      border: 1px solid #fde68a;
      border-radius: 6px;
    }

    &__opinion {
      position: relative;
    }

    &__count {
      margin-top: 4px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 12px;
      text-align: right;
      color: @text-weak;

      &--full {
        color: #ff4d4f;
      }
    }

    &__phrases {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      /deep/ .ant-btn {
        margin: 0;
      }
    }

    &__tip {
      margin-top: 6px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    &__attach {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 10px;
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }

    &__files {
      margin: 10px 0 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 3px 0;
        font-size: 12px;
        color: #475569;
      }
    }

    &__file-name {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__file-size {
      flex: none;
      color: @text-weak;
    }

    &__file-remove {
      flex: none;
      color: #ff4d4f;
    }

    &__status {
      width: 130px;
      margin-left: 10px;
    }

    &__submit {
      margin-bottom: 0;

      /deep/ .ant-form-item-children {
        display: flex;
        gap: 8px;
      }
    }

    &__reset {
      margin-left: 8px;
    }

    /deep/ .ant-form-item {
      margin-bottom: 14px;
    }
  }
</style>
