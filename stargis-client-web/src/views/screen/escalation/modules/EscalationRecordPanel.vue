<template>
  <!--
    EscalationRecordPanel 审核意见登记面板
    --------------------------------
    设计文档 5.2 / 5.3：「提级论证审批」页右栏与项目详情弹窗**共用同一个面板**。

    四个验收会被点名的硬性要素：
      ① 意见正文 1000 字上限 + **实时计数**（0/1000）；
      ② 5 个快捷短语按钮：点击回填到意见框，**回填后仍可编辑**（不覆盖用户已写的内容）；
      ③ 附件上传：白名单 pdf/doc/docx/jpg/jpeg/png，单个 ≤ 50MB；
      ④ 「同时更新办理状态」**默认不勾选**，勾选后才出现状态下拉。

    ④ 为什么不用复选框控件：本工程的 Screen 组件库里没有 Checkbox，
    用 ScreenRadioGroup（分段单选）表达同样的语义，默认值就是「仅登记意见」= 不勾选，
    比自绘一个复选框更不容易与组件库的视觉漂移。

    附件口径：走后端 record/add 的 attachmentIds 字段（逗号分隔的**存储路径**）。
    jeecg 通用上传接口返回的是相对路径而不是业务 ID，所以直接把 storePath 存进去；
    已保存记录的附件用 Java 后端的静态资源地址回看（记录接口没有单独的下载端点）。

    事件：
      ok (payload)  登记成功后抛出，父组件据此刷新意见列表 / 材料数 / 项目状态
  -->
  <div class="record-panel">
    <!-- 顶部说明：既满足验收措辞，又不会让用户误以为系统会推进流程 -->
    <p class="record-panel__notice">
      <screen-icon name="info" :size="13" />
      <span>流程在线下办理，此处仅登记论证结论与审核意见（登记只追加、不修改历史记录）。</span>
    </p>

    <screen-field label="记录类型" label-width="72px">
      <screen-select
        v-model="form.recordType"
        :options="recordTypeOptions"
        placeholder="请选择记录类型"
        aria-label="记录类型"
      />
    </screen-field>

    <screen-field label="意见正文" required label-width="72px" :error="error">
      <div class="record-panel__opinion">
        <screen-input
          v-model="form.opinion"
          type="textarea"
          :rows="5"
          :maxlength="maxLength"
          :invalid="!!error"
          placeholder="请填写论证结论与审核意见，不超过 1000 字"
        />
        <!-- ★ 原型硬性要素：0/1000 实时计数 -->
        <span class="record-panel__count" :class="{ 'is-full': isFull }">{{ opinionLength }}/{{ maxLength }}</span>
      </div>
    </screen-field>

    <screen-field label="快捷短语" label-width="72px">
      <div class="record-panel__phrases">
        <screen-button
          v-for="phrase in quickPhrases"
          :key="phrase"
          size="sm"
          :type="form.action === phrase ? 'primary' : 'default'"
          @click="applyPhrase(phrase)"
        >
          {{ phrase }}
        </screen-button>
      </div>
      <p class="record-panel__tip">
        点击短语回填到意见框（结论短语记为「{{ form.action || '未选择' }}」），回填后可继续编辑
      </p>
    </screen-field>

    <screen-field label="附件" label-width="72px">
      <div class="record-panel__attach">
        <screen-upload
          :action="uploadAction"
          :headers="uploadHeaders"
          :data="{ biz: bizPath }"
          :allowed-ext="MATERIAL_ALLOWED_EXT"
          :max-size-mb="MATERIAL_MAX_SIZE_MB"
          :disabled="uploading"
          button-text="上传附件"
          @success="handleUploadSuccess"
          @error="handleUploadError"
          @reject="handleUploadReject"
          @uploading-change="uploading = $event"
        />
        <span class="record-panel__hint">
          {{ MATERIAL_ALLOWED_EXT_TEXT }}，单文件不超过 {{ MATERIAL_MAX_SIZE_MB }}MB
        </span>
      </div>

      <ul v-if="attachments.length" class="record-panel__files">
        <li v-for="(item, index) in attachments" :key="item.storePath || index">
          <screen-icon name="paperclip" :size="13" />
          <button type="button" class="record-panel__file-name" @click="openAttachment(item)">
            {{ item.fileName }}
          </button>
          <span class="record-panel__file-size">{{ formatSize(item.fileSize) }}</span>
          <button type="button" class="record-panel__file-remove" @click="removeAttachment(index)">移除</button>
        </li>
      </ul>
    </screen-field>

    <screen-field label="办理状态" label-width="72px">
      <div class="record-panel__status">
        <screen-radio-group
          v-model="syncStatus"
          :options="syncOptions"
          size="sm"
          aria-label="是否同时更新办理状态"
        />
        <screen-select
          v-if="syncStatus === '1'"
          v-model="targetStatus"
          class="record-panel__status-select"
          :options="statusOptions"
          placeholder="选择目标办理状态"
          aria-label="目标办理状态"
        />
      </div>
      <p class="record-panel__tip">
        当前办理状态：<b>{{ currentStatus || '未办理' }}</b>；默认「仅登记意见」——
        登记意见不会自动改办理状态（保持「系统只存不推」的口径）
      </p>
    </screen-field>

    <div class="record-panel__submit">
      <screen-button
        type="primary"
        icon="save"
        :loading="saving"
        :disabled="!projectId || !opinionLength"
        @click="handleSubmit"
      >
        保存登记
      </screen-button>
      <screen-button @click="reset">清空</screen-button>
    </div>
  </div>
</template>

<script>
import Vue from 'vue'
import {
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenRadioGroup,
  ScreenButton,
  ScreenUpload,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { JEECG_ACCESS_TOKEN } from '@/store/mutation-types'
import { javaUploadUrl, getJavaFileAccessHttpUrl } from '@/api/manageJava'
import { addRecord } from '@/api/land/escalation'
import {
  STATUS_OPTIONS,
  RECORD_TYPES,
  RECORD_MAX_LENGTH,
  RECORD_QUICK_PHRASES,
  MATERIAL_ALLOWED_EXT,
  MATERIAL_ALLOWED_EXT_TEXT,
  MATERIAL_MAX_SIZE_MB,
  toOptions,
  formatSize,
  buildBizPath,
} from '../constants'

export default {
  name: 'EscalationRecordPanel',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenButton,
    ScreenUpload,
    ScreenIcon,
  },
  props: {
    /** 当前登记的目标项目（为空时不允许提交） */
    projectId: { type: String, default: '' },
    /** 项目当前办理状态（仅展示用；勾选同步后才作为目标状态提交） */
    currentStatus: { type: String, default: '' },
  },
  data () {
    return {
      MATERIAL_ALLOWED_EXT,
      MATERIAL_ALLOWED_EXT_TEXT,
      MATERIAL_MAX_SIZE_MB,
      maxLength: RECORD_MAX_LENGTH,
      quickPhrases: RECORD_QUICK_PHRASES,
      saving: false,
      uploading: false,
      /** 校验错误（目前只有「意见正文为空」一种，仍保留对象外的字符串便于扩展） */
      error: '',
      /** '0' 仅登记意见（默认，等价于「不勾选」） / '1' 同时更新办理状态 */
      syncStatus: '0',
      targetStatus: '已办结',
      attachments: [],
      syncOptions: [
        { value: '0', label: '仅登记意见' },
        { value: '1', label: '同时更新办理状态' },
      ],
      recordTypeOptions: toOptions(RECORD_TYPES),
      statusOptions: toOptions(STATUS_OPTIONS),
      /**
       * 上传地址指向 Java 业务后端；令牌必须是 jeecg 自己的
       * JEECG_ACCESS_TOKEN（中台的 ACCESS_TOKEN 在 Java 端是无效的）。
       */
      uploadAction: javaUploadUrl(),
      uploadHeaders: { 'X-Access-Token': Vue.ls.get(JEECG_ACCESS_TOKEN) },
      form: this.buildEmptyForm(),
    }
  },
  computed: {
    opinionLength () {
      return (this.form.opinion || '').length
    },
    isFull () {
      return this.opinionLength >= this.maxLength
    },
    /** 附件子目录：/escalation/opinion/{yyyy}/{MM} */
    bizPath () {
      return buildBizPath(new Date(), 'opinion')
    },
  },
  watch: {
    /** 切换项目时清空草稿：否则会把 A 项目的意见顺手登记到 B 项目上 */
    projectId () {
      this.reset()
    },
  },
  methods: {
    formatSize,
    buildEmptyForm () {
      return {
        recordType: '审核意见',
        action: '',
        opinion: '',
      }
    },

    /* ---------------- 快捷短语 / 计数 ---------------- */

    /**
     * 点击快捷短语：记为结论短语，并把短语文本**追加**到意见框。
     * 意见框已有内容时按行追加而不覆盖 —— 原型要求「回填后可继续编辑」，
     * 覆盖掉用户已经写好的内容是最容易挨骂的一种"聪明"实现。
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

    /* ---------------- 附件 ---------------- */

    handleUploadSuccess ({ file, storePath }) {
      const name = (file && file.name) || ''
      if (!storePath) {
        toast.error(`附件「${name}」上传成功但没有返回存储路径，请检查上传接口`)
        return
      }
      this.attachments = this.attachments.concat([
        { fileName: name, fileSize: (file && file.size) || 0, storePath },
      ])
      toast.success(`附件「${name}」上传成功`)
    },

    handleUploadError (message) {
      toast.error(message || '附件上传失败')
    },

    handleUploadReject (reason) {
      toast.warning(reason || '附件不符合上传要求')
    },

    removeAttachment (index) {
      this.attachments = this.attachments.filter((item, position) => position !== index)
    },

    /** 未落库附件走 Java 后端的静态资源地址（上传接口已把文件放到 jeecg.path.upload 下） */
    openAttachment (item) {
      const url = getJavaFileAccessHttpUrl(item.storePath)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该附件还没有可访问的地址')
      }
    },

    /* ---------------- 提交 ---------------- */

    reset () {
      this.form = this.buildEmptyForm()
      this.attachments = []
      this.syncStatus = '0'
      this.targetStatus = '已办结'
      this.error = ''
    },

    handleSubmit () {
      if (!this.projectId) {
        toast.warning('请先在左侧选择一个项目')
        return
      }
      const opinion = (this.form.opinion || '').trim()
      if (!opinion) {
        this.error = '请填写意见正文'
        toast.warning('请填写意见正文')
        return
      }
      if (opinion.length > this.maxLength) {
        this.error = `意见正文不能超过 ${this.maxLength} 字`
        toast.warning(this.error)
        return
      }
      this.error = ''

      const payload = {
        projectId: this.projectId,
        recordType: this.form.recordType || '审核意见',
        action: this.form.action || undefined,
        opinion,
        attachmentIds: this.attachments.length
          ? this.attachments.map((item) => item.storePath).join(',')
          : undefined,
      }
      // ★ 只有选了「同时更新办理状态」才传 status：否则 = 不改办理状态
      if (this.syncStatus === '1' && this.targetStatus) {
        payload.status = this.targetStatus
      }

      this.saving = true
      addRecord(payload)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '登记失败')
            return
          }
          toast.success(res.message || '登记成功')
          this.reset()
          this.$emit('ok', payload)
        })
        .finally(() => {
          this.saving = false
        })
    },

    /** 供父组件在切换项目时清空草稿 */
    clearDraft () {
      this.reset()
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.record-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  min-width: 0;

  &__notice {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-warning);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-left: 3px solid var(--screen-warning);
    border-radius: var(--screen-radius);
  }

  &__opinion {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    min-width: 0;
  }

  /* 实时计数：满额时同时变色 + 文案仍是 n/1000，颜色不是唯一线索 */
  &__count {
    align-self: flex-end;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);

    &.is-full {
      color: var(--screen-danger);
    }
  }

  &__phrases {
    display: flex;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
  }

  &__tip {
    margin: 6px 0 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);

    b {
      color: var(--screen-text-sub);
      font-weight: 500;
    }
  }

  &__attach {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
  }

  &__hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__files {
    margin: var(--screen-space-2) 0 0;
    padding: 0;
    list-style: none;

    li {
      display: flex;
      align-items: center;
      gap: var(--screen-space-2);
      padding: 3px 0;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-sub);
    }
  }

  &__file-name {
    flex: 1 1 auto;
    min-width: 0;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    text-align: left;
    background: none;
    border: 0;
    cursor: pointer;
    .screen-ellipsis();
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  &__file-size {
    flex: none;
    color: var(--screen-text-mute);
  }

  &__file-remove {
    flex: none;
    .screen-link-action();
    color: var(--screen-danger);
  }

  &__status {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
  }

  &__status-select {
    width: 150px;
  }

  &__submit {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }
}
</style>
