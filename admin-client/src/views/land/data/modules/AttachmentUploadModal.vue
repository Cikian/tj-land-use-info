<template>
  <a-modal
    title="上传附件"
    :width="720"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    @cancel="handleClose">
    <!-- ============ 两步流程（失败时能直接看出卡在哪一步） ============ -->
    <a-steps :current="currentStep" :status="stepStatus" size="small" class="attach-upload__steps">
      <a-step title="第 1 步 · 文件落盘" description="字节流上传到文件服务，返回相对存储路径" />
      <a-step title="第 2 步 · 登记入库" description="把路径与业务信息登记到附件表" />
    </a-steps>

    <div class="attach-upload__block">
      <div class="attach-upload__row">
        <span class="attach-upload__label">业务类型</span>
        <a-select
          v-model="bizType"
          placeholder="必选：这份附件挂在哪种业务上"
          style="width: 260px"
          :options="bizTypeOptions"
          @change="handleBizTypeChange" />
        <span class="attach-upload__hint">
          业务类型决定「附件挂在谁身上」：先选类型，再选具体的宗地 / 配套项目
        </span>
      </div>

      <div class="attach-upload__row">
        <span class="attach-upload__label">业务对象</span>
        <a-select
          v-model="bizId"
          show-search
          allow-clear
          :disabled="!bizType || bizType === 'process'"
          :placeholder="bizPlaceholder"
          style="width: 360px"
          :filter-option="false"
          :default-active-first-option="false"
          :not-found-content="bizLoading ? '搜索中…' : '输入编号 / 名称后回车搜索'"
          @search="handleBizSearch"
          @change="handleBizChange">
          <a-select-option v-for="item in bizOptions" :key="item.id" :value="item.id">
            {{ optionLabel(item) }}
          </a-select-option>
        </a-select>
        <span class="attach-upload__hint">
          ★ 必须输入关键词后再选（一次拉回几百条既慢又容易选错）；
          选中的「宗地编号 / 配套名称」会作为附件列表里的业务可读键存下来
        </span>
      </div>

      <div class="attach-upload__row">
        <span class="attach-upload__label">附件类型</span>
        <a-select
          v-model="fileType"
          placeholder="必选：这份文件属于哪一类资料"
          style="width: 260px"
          :options="fileTypeOptions" />
        <span class="attach-upload__hint">
          类型清单由后端白名单给出（与字典 land_attach_type 同源），
          前端另写一份必然漂移 —— 漂移的后果是「下拉里能选、提交被拒」
        </span>
      </div>

      <div class="attach-upload__row">
        <span class="attach-upload__label">选择文件</span>
        <a-upload
          :file-list="fileList"
          :before-upload="handleBeforeUpload"
          :remove="handleRemove"
          multiple
          directory
          :disabled="!canPickFile || uploading">
          <a-button icon="upload" :disabled="!canPickFile || uploading">
            {{ canPickFile ? '选择文件 / 文件夹上传' : '请先填完上面三项' }}
          </a-button>
        </a-upload>
        <span class="attach-upload__hint">
          可一次选多个文件，也可直接<b>选整个文件夹</b>；文件夹里的文件会<b>不分层级</b>归到上面选的材料类型下
        </span>
      </div>

      <div class="attach-upload__row attach-upload__row--top">
        <span class="attach-upload__label">备注</span>
        <a-textarea
          v-model="remark"
          :rows="2"
          :max-length="500"
          style="width: 520px"
          placeholder="选填；例如「同一份规划条件函同时支撑本项目两期」" />
      </div>
    </div>

    <!-- ============ 结果 ============ -->
    <a-alert
      v-if="errorMessage"
      class="attach-upload__alert"
      type="error"
      show-icon
      :message="errorStep === 'bytes' ? '第 1 步失败：文件没有上传成功（没有产生任何存储路径，也没有登记）' : '第 2 步失败：文件已经在服务器上，但没有登记到附件表'"
      :description="errorMessage" />
    <a-alert
      v-if="done"
      class="attach-upload__alert"
      type="success"
      show-icon
      :message="`上传成功：${lastFileName}`"
      description="已登记到附件列表；本页刷新后即可看到这条记录（可预览 / 下载 / 删除）。" />

    <div class="attach-upload__foot">
      <span v-if="errorStep === 'meta'" class="attach-upload__foot-hint">
        ★ 重试登记只重跑第 2 步：不会重复占用存储、也不会产生第二份文件
      </span>
      <span v-else-if="errorStep === 'bytes'" class="attach-upload__foot-hint">
        ★ 重试上传会重新走完整的两步（第 1 步失败时服务器上没有留下文件）
      </span>
      <span v-else class="attach-upload__foot-hint"></span>
      <a-button v-if="errorStep === 'bytes'" icon="reload" :loading="uploading" @click="handleRetryUpload">重试上传</a-button>
      <a-button v-if="errorStep === 'meta'" icon="reload" :loading="uploading" @click="handleRetryMeta">重试登记</a-button>
      <a-button type="primary" @click="handleClose">{{ done ? '完成' : '关闭' }}</a-button>
    </div>
  </a-modal>
</template>

<script>
  import {
    ATTACH_TYPES_FALLBACK,
    buildUploadBiz,
    queryAllowedTypes,
    saveAttachmentMeta,
    uploadFileBytes
  } from '@/api/land/attachment'
  import { BIZ_TYPE_REQUIRED_OPTIONS } from '@/api/land/dataRecycle'
  import { queryLandOptions, searchFacilityOptions } from '@/api/land/landData'

  /** 取小写扩展名（服务端也会兜底推导，这里显式给出，避免库里出现 "JPG" 与 "jpg" 两种写法） */
  function extractExt (fileName) {
    const name = fileName || ''
    const index = name.lastIndexOf('.')
    return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
  }

  /**
   * 配套附件 - 上传弹窗
   *
   * ★★★ 上传是**两步**，这是本弹窗与「一个接口搞定上传」的常见做法最大的差别：
   *
   *   第 1 步 `uploadFileBytes(file, buildUploadBiz(bizType))`
   *           → POST /sys/common/upload，把**文件字节**落到文件服务，
   *             返回一个相对存储路径（jeecg 把它放在响应的 message 字段里，不是 result）。
   *             这一步失败意味着：服务器上什么都没有，重试就是从头再传一次。
   *   第 2 步 `saveAttachmentMeta({...})`
   *           → POST /land/data/attachment/save，把「路径 + 业务信息」登记进附件表，
   *             后端在这一步校验业务对象是否存在、类型是否在白名单内、路径是否是安全相对路径。
   *             这一步失败意味着：**文件已经在服务器上了，只是没登记** ——
   *             用户看到的列表里不会有它，但磁盘上确实多了一个文件。
   *
   *   为什么要拆两步（不是本页面能决定的，但必须解释给用户）：
   *   同一份文件（例如一份规划条件函）可能同时支撑多个地块，把「存文件」与「挂业务」
   *   耦合在一个接口里就没法复用；而且两步的失败原因完全不同，
   *   拆开后前端才能准确告诉用户「是没传上去，还是传上去了没登记」。
   *
   *   ★ 因此错误提示分成两种口径（见 errorStep）：
   *     bytes → 「重试上传」：重跑完整两步；
   *     meta  → 「重试登记」：只重跑第 2 步，复用已经落盘的路径，不会产生第二份文件。
   *
   * ★ 为什么选文件就立刻上传，而不是先「保存」再上传：
   *   附件没有「草稿」概念 —— 没登记成功的字节流对用户毫无意义。
   *   所以要求先填齐业务类型 / 业务对象 / 附件类型（未填齐时文件选择框是禁用的），
   *   选完文件即走两步流程，成功就是成功，失败就明确告诉用户卡在哪一步。
   */
  export default {
    name: 'AttachmentUploadModal',
    data () {
      return {
        visible: false,
        uploading: false,
        done: false,
        // ---- 表单 ----
        bizType: undefined,
        bizId: undefined,
        bizKey: '',
        fileType: undefined,
        remark: '',
        file: null,
        fileList: [],
        // ---- 远程业务对象 ----
        bizOptions: [],
        bizLoading: false,
        // ---- 附件类型 ----
        allowedTypes: [],
        // ---- 两步结果 ----
        storePath: '',
        bytesFileName: '',
        bytesFileSize: null,
        lastFileName: '',
        errorStep: '',
        errorMessage: '',
        // ---- 批量（含文件夹）上传 ----
        /** 本批已成功份数 */
        uploadedCount: 0,
        /** 本批失败的文件名（末尾汇总提示用） */
        failedFiles: []
      }
    },
    computed: {
      /**
       * 业务类型下拉。
       *
       * ★ 用 `BIZ_TYPE_REQUIRED_OPTIONS`（不含「全部」）而不是 BIZ_TYPE_OPTIONS：
       *   表单里第一项是「全部」会让人以为可以不选，结果提交一个空 bizType 被后端拒绝。
       * ★ 「环节进度」在这里置灰：本页能拿到的下拉数据源只有宗地（queryLandOptions）
       *   与配套项目（searchFacilityOptions），没有可靠的环节进度选项接口。
       *   与其让用户手抄一个 id 挂错对象，不如明确指向正确的入口。
       */
      bizTypeOptions () {
        return BIZ_TYPE_REQUIRED_OPTIONS.map(item => ({
          value: item.value,
          label: item.label,
          disabled: item.value === 'process'
        }))
      },
      fileTypeOptions () {
        if (this.allowedTypes.length) {
          return this.allowedTypes
        }
        return ATTACH_TYPES_FALLBACK
      },
      bizPlaceholder () {
        if (!this.bizType) {
          return '请先选择业务类型'
        }
        if (this.bizType === 'process') {
          return '环节进度附件请在「配套信息录入」的环节进度里上传'
        }
        return this.bizType === 'land' ? '输入出让宗地编号 / 地块名称搜索' : '输入配套项目名称搜索'
      },
      canPickFile () {
        return !!(this.bizType && this.bizId && this.fileType)
      },
      /** 0 = 第 1 步进行中；1 = 第 1 步已完成、正在第 2 步；2 = 两步都完成 */
      currentStep () {
        if (this.done) {
          return 2
        }
        if (this.errorStep === 'meta' || this.storePath) {
          return 1
        }
        return 0
      },
      stepStatus () {
        if (this.errorStep) {
          return 'error'
        }
        return this.done ? 'finish' : 'process'
      }
    },
    mounted () {
      this.loadAllowedTypes()
    },
    methods: {
      /**
       * 打开弹窗。
       *
       * @param {object} [preset] 预置条件（树上的「上传到该类型 / 上传到该项目」传进来）
       *   { bizType, bizId, bizKey, fileType, fileName? }
       * ★ 传了 fileType 就把它定死：用户是在某个材料类型下点的上传，
       *   不该再让他选一次（这是本次需求的核心便利点）。
       */
      open (preset) {
        const record = preset || {}
        this.reset()
        if (record.bizType) {
          this.bizType = record.bizType
        }
        if (record.bizId) {
          this.bizId = record.bizId
          this.bizKey = record.bizKey || ''
          // 预置的业务对象不在远程搜索结果里，补一个选项让下拉能显示出来
          if (record.bizKey) {
            this.bizOptions = [{ id: record.bizId, crzdbh: record.bizKey, dkmc: '', ptxmmc: record.bizKey }]
          }
        }
        if (record.fileType) {
          this.fileType = record.fileType
        }
        this.visible = true
      },
      reset () {
        this.uploading = false
        this.done = false
        this.bizType = undefined
        this.bizId = undefined
        this.bizKey = ''
        this.fileType = undefined
        this.remark = ''
        this.file = null
        this.fileList = []
        this.bizOptions = []
        this.storePath = ''
        this.bytesFileName = ''
        this.bytesFileSize = null
        this.lastFileName = ''
        this.errorStep = ''
        this.errorMessage = ''
      },
      handleClose () {
        this.visible = false
      },
      loadAllowedTypes () {
        // ★ 材料类型清单按 bizType 取对应那套（宗地 5 类 / 配套 13 类）
        queryAllowedTypes(this.bizType || undefined).then(res => {
          if (res.success && res.result && res.result.length) {
            // 后端给的是 {value, text}（与 land_attach_type 字典同源的 {value,label} 不同）
            this.allowedTypes = res.result.map(item => ({ value: item.value, label: item.text || item.label }))
          }
        }).catch(() => { /* 接口不可用时用本地兜底清单 */ })
      },

      // ---------------- 业务对象远程搜索 ----------------

      handleBizTypeChange () {
        // 换类型后原来选中的业务对象已经不属于当前类型，必须清掉，
        // 否则会把一份附件挂到「上一个类型 + 新的 bizId」这种不存在的组合上
        this.bizId = undefined
        this.bizKey = ''
        this.bizOptions = []
        // ★ 材料类型清单也要换，并清掉已选的旧码值（旧码值在新清单里可能不存在）
        this.fileType = undefined
        this.loadAllowedTypes()
      },
      handleBizSearch (keyword) {
        this.bizOptions = []
        if (!this.bizType || this.bizType === 'process') {
          return
        }
        if (!keyword) {
          return
        }
        this.bizLoading = true
        const fetcher = this.bizType === 'land' ? queryLandOptions : searchFacilityOptions
        fetcher({ keyword, limit: 50 }).then(res => {
          this.bizOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.bizOptions = []
        }).finally(() => {
          this.bizLoading = false
        })
      },
      handleBizChange (value) {
        const hit = this.bizOptions.filter(item => item.id === value)[0]
        if (!hit) {
          this.bizId = value
          return
        }
        this.bizId = hit.id
        // 业务可读键：宗地用编号、配套用名称 —— 列表里直接展示，避免每次联表
        this.bizKey = this.bizType === 'facility' ? hit.ptxmmc : hit.crzdbh
      },
      optionLabel (item) {
        if (this.bizType === 'facility') {
          return item.ptxmmc + (item.crzdbh ? `（${item.crzdbh}）` : '')
        }
        return (item.crzdbh || '') + (item.dkmc ? `（${item.dkmc}）` : '')
      },

      // ---------------- 选文件 → 两步上传 ----------------

      /**
       * 选中文件（支持一次多个 / 选择整个文件夹）。
       *
       * ★ 文件夹上传：把每个文件的相对路径去掉文件名作为它的目录 —— 不过当前
       *   业务口径是**拉平**：文件夹里的文件不分层级，全部归到当前材料类型下，
       *   所以这里只记路径用于展示，不参与归属计算。
       */
      handleBeforeUpload (file) {
        if (!this.canPickFile) {
          this.$message.warning('请先选择业务类型、业务对象与附件类型')
          return false
        }
        const next = this.fileList.slice()
        next.push(file)
        this.fileList = next
        this.done = false
        this.errorStep = ''
        this.errorMessage = ''
        // 立刻开始逐份上传（a-upload 的自动上传已被 return false 阻止，
        // 两步流程由本组件按顺序手动调用）
        this.uploadQueue(next.slice())
        return false
      },
      handleRemove (file) {
        if (!file) {
          this.fileList = []
          return
        }
        this.fileList = this.fileList.filter(item => item.uid !== file.uid)
      },

      /**
       * 逐份串行上传整个队列。
       *
       * ★ 为什么串行而不是并发：每份都要跑两步（落盘 + 登记），
       *   并发会让进度条与失败定位都变得不可解释；而且服务端的下载计数、
       *   变更留痕是按条写的，串行更容易对账。
       * ★ 单份失败不中断整批：用户选的是一整个文件夹，因为其中一个文件
       *   格式不对就把其余全部丢弃，代价太大。失败的会汇总在末尾提示。
       */
      uploadQueue (queue) {
        if (this.uploading) {
          // 上一批还在跑：提示而不是静默排队（否则用户以为没反应）
          this.$message.warning('上一批还在上传中，请稍候再选')
          return
        }
        const pending = queue.slice()
        this.uploadedCount = 0
        this.failedFiles = []
        this.uploading = true
        this.errorStep = ''
        this.errorMessage = ''
        const step = () => {
          const file = pending.shift()
          if (!file) {
            this.uploading = false
            this.finishBatch()
            return
          }
          this.uploadOne(file).then(ok => {
            if (ok) {
              this.uploadedCount += 1
            } else {
              this.failedFiles.push(file.name)
            }
          }).then(step)
        }
        step()
      },

      /** 整批结束：给出可读结论（成功几条 / 失败哪几个） */
      finishBatch () {
        const total = this.uploadedCount + this.failedFiles.length
        if (total === 0) {
          return
        }
        if (!this.failedFiles.length) {
          this.done = true
          this.lastFileName = total === 1 ? this.fileList[0].name : `共 ${total} 个文件`
          return
        }
        this.errorStep = this.uploadedCount > 0 ? 'meta' : 'bytes'
        this.errorMessage = `成功 ${this.uploadedCount} 个，失败 ${this.failedFiles.length} 个：` +
          this.failedFiles.join('、') +
          '。失败的文件可重新选择上传（已成功的不会重复登记）。'
      },

      /**
       * 单份文件的两步上传。
       *
       * @returns {Promise<boolean>} true = 该份成功
       */
      uploadOne (file) {
        this.storePath = ''
        this.bytesFileName = ''
        this.bytesFileSize = null
        return uploadFileBytes(file, buildUploadBiz(this.bizType)).then(uploaded => {
          this.storePath = uploaded.storePath
          this.bytesFileName = uploaded.fileName || file.name
          this.bytesFileSize = uploaded.fileSize
          return this.doSaveMeta(file, true)
        }).catch(e => {
          // 第 1 步失败：没有产生存储路径，也没有登记
          this.errorMessage = `${file.name}：${(e && e.message) || '文件上传失败'}`
          return false
        })
      },

      /** 只重跑第 2 步（复用已落盘的路径） */
      handleRetryMeta () {
        if (!this.storePath) {
          this.$message.warning('没有可复用的存储路径，请重新上传文件')
          this.errorStep = 'bytes'
          return
        }
        this.uploading = true
        this.errorMessage = ''
        this.doSaveMeta(null, false).finally(() => {
          this.uploading = false
        })
      },
      handleRetryUpload () {
        this.uploadQueue(this.fileList.slice())
      },
      /**
       * 第 2 步：把路径与业务信息登记到附件表。
       *
       * @param {File} [file]        当前这一份文件（批量时逐份传入）
       * @param {boolean} [inBatch]  true = 批量中的一份：出错只返回 false，
       *                             由调用方汇总提示；不在这里写 errorStep
       * @returns {Promise<boolean>} true = 登记成功
       */
      doSaveMeta (file, inBatch) {
        const source = file || this.file
        const fileName = this.bytesFileName || (source ? source.name : '')
        const meta = {
          bizType: this.bizType,
          bizId: this.bizId,
          bizKey: this.bizKey,
          fileType: this.fileType,
          fileName,
          fileSize: this.bytesFileSize,
          fileExt: extractExt(fileName),
          contentType: source ? source.type : '',
          storePath: this.storePath,
          remark: this.remark
        }
        return saveAttachmentMeta(meta).then(res => {
          if (!res.success) {
            if (!inBatch) {
              this.errorStep = 'meta'
              this.errorMessage = `文件已上传成功（存储路径 ${this.storePath}），但登记失败：` +
                `${res.message || '后端未说明原因'}。可点「重试登记」只重跑第 2 步，不必重新上传文件。`
            }
            return false
          }
          if (!inBatch) {
            this.done = true
            this.lastFileName = fileName
            this.$message.success('附件上传成功')
          }
          // ★ 每成功一份就通知父组件刷新：批量上传中途关掉弹窗，
          //   已经传上去的那些也应该出现在列表里
          this.$emit('ok')
          return true
        }).catch(e => {
          if (!inBatch) {
            this.errorStep = 'meta'
            this.errorMessage = `文件已上传成功（存储路径 ${this.storePath}），但登记失败：` +
              `${(e && e.message) || '网络异常'}。可点「重试登记」只重跑第 2 步，不必重新上传文件。`
          }
          return false
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .attach-upload {
    &__steps {
      margin-bottom: 14px;
    }

    &__block {
      padding: 12px 14px;
      margin-bottom: 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    &__row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      margin-bottom: 12px;

      &:last-child {
        margin-bottom: 0;
      }

      &--top {
        align-items: flex-start;
      }
    }

    &__label {
      flex: 0 0 72px;
      font-size: 13px;
      color: #0f172a;
    }

    &__hint {
      flex: 1 1 auto;
      min-width: 220px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    &__alert {
      margin-bottom: 12px;
    }

    &__foot {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      justify-content: flex-end;
      padding-top: 10px;
      border-top: 1px solid @border-color;
    }

    &__foot-hint {
      flex: 1 1 auto;
      min-width: 200px;
      font-size: 12px;
      line-height: 18px;
      color: @text-muted;
    }
  }
</style>
