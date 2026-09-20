<template>
  <!--
    ScreenUpload 大屏文件上传
    --------------------------------
    设计稿里档案管理需要「选择文件上传」入口：一个青绿描边按钮 + 一行弱化提示文案。
    本组件把 jeecg 通用上传接口（POST /sys/common/upload，multipart/form-data）
    封装成大屏观感的一个原子控件，**不依赖 antd**（不使用 a-upload / a-message）。

    为什么不用 a-upload：
      1. a-upload 的列表、进度、气泡都是浅色主题，与「青绿暗色」大屏不统一；
      2. 大屏需要把「校验失败」长期留在界面上（用户必须处理），而不是一闪而过的提示；
      3. 批量上传要按整批字节数聚合进度，antd 的进度粒度是单文件。

    上传位置由调用方决定（本组件不读 token、不拼域名）：
      action  = window._CONFIG['domianURL'] + '/sys/common/upload'
      headers = { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) }
      data    = { biz: '/archive/2026/09' }   （可选的上传子目录）

    用法：
      <screen-upload
        ref="upload"
        :action="uploadAction"
        :headers="uploadHeaders"
        :data="{ biz: bizPath }"
        :allowed-ext="allowedExt"
        allowed-ext-text="pdf / doc(x) / xls(x) / 图片 / dwg / shp / zip / rar 等"
        :max-size-mb="200"
        @success="handleUploaded"
        @reject="handleRejected"
        @error="handleUploadError"
      />

      // JS 里：this.$refs.upload.abort() / this.$refs.upload.clear()
  -->
  <div class="screen-upload" :class="{ 'is-uploading': uploading }">
    <!--
      隐藏的原生文件输入：
      刻意使用「1px + clip-path 裁切」而不是 display: none —— display: none 的输入框
      在部分浏览器里无法被 label 关联激活，且会被辅助技术整棵剪掉，
      保留在可访问树里（aria-hidden + tabindex="-1"，由按钮代其可达）更稳。
    -->
    <input
      :id="inputId"
      ref="fileInput"
      class="screen-upload__input"
      type="file"
      :accept="accept"
      :multiple="multiple"
      :disabled="disabled || uploading"
      aria-hidden="true"
      tabindex="-1"
      @change="handleFileChange"
    />

    <div class="screen-upload__bar">
      <screen-button
        icon="upload"
        :loading="uploading"
        :disabled="disabled"
        @click="handleTriggerClick"
      >
        {{ buttonText }}
      </screen-button>

      <span v-if="allowedExtText" class="screen-upload__hint">{{ allowedExtText }}</span>
    </div>

    <!-- 上传进度：进度条 + 数值文本，进度同时以 role="progressbar" 暴露给读屏 -->
    <div
      v-if="uploading"
      class="screen-upload__progress"
      role="progressbar"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="percent"
      :aria-valuetext="progressText"
      :aria-label="progressText"
    >
      <div class="screen-upload__track" aria-hidden="true">
        <i class="screen-upload__fill" :style="{ width: percent + '%' }" />
      </div>
      <span class="screen-upload__percent" aria-hidden="true">{{ percentText }}</span>
    </div>

    <!--
      校验失败清单：必须同时以「可见文本 + 实时区域」呈现。
      只变色或只弹一次 toast，用户回头就看不到要改什么了。
    -->
    <div
      v-if="errors.length"
      class="screen-upload__errors"
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <p class="screen-upload__errors-title">
        <screen-icon name="alert-triangle" :size="14" />
        <span>以下 {{ errors.length }} 个文件未通过校验，请重新选择：</span>
      </p>
      <ul class="screen-upload__error-list">
        <li v-for="(item, index) in errors" :key="`${item.name}-${index}`" class="screen-upload__error-item">
          <span class="screen-upload__error-name">{{ item.name }}</span>
          <span class="screen-upload__error-reason">{{ item.reason }}</span>
        </li>
      </ul>
    </div>

    <!--
      校验失败的第一时间播报：部分文件被拒收、其余仍在上传时，上面的 role="alert"
      不会重新挂载（errors 只是变长），所以用文本变化的 live region 补一次播报。
      视觉隐藏用标准的 1px + clip 技术，不用 display: none。
    -->
    <p class="screen-upload__sr-status" role="status" aria-live="polite">{{ rejectSummary }}</p>
  </div>
</template>

<script>
import { toNumber } from './utils'
import { toast } from './toast'
import ScreenButton from './ScreenButton.vue'
import ScreenIcon from './ScreenIcon.vue'

/**
 * 页面内序号：用于给隐藏 input 生成稳定且不冲突的 id。
 * 不引入 utils.uid，避免为了一个 id 增加耦合。
 */
let uploadSeed = 0

export default {
  name: 'ScreenUpload',
  components: { ScreenButton, ScreenIcon },
  props: {
    /**
     * 上传地址（完整 URL）。
     * 调用方传 window._CONFIG['domianURL'] + '/sys/common/upload'，
     * 组件内部不拼域名、不读 token，保证可测试与可复用。
     */
    action: { type: String, required: true },
    /**
     * 额外请求头，例如 { 'X-Access-Token': token }。
     * jeecg 的 JwtFilter 只认 X-Access-Token 这个头名，不要改写成 Authorization。
     */
    headers: { type: Object, default: () => ({}) },
    /**
     * 额外表单字段，例如 { biz: '/archive/2026/09' }。
     * biz 是后端的上传子目录，不传则落在 jeecg.path.upload 根目录。
     */
    data: { type: Object, default: () => ({}) },
    /** 是否允许一次选择多个文件 */
    multiple: { type: Boolean, default: true },
    /** 是否整体禁用（禁用时按钮不可点、输入框不可选） */
    disabled: { type: Boolean, default: false },
    /** 单文件体积上限（MB），超出则该文件被拒收 */
    maxSizeMb: { type: Number, default: 200 },
    /**
     * 允许的扩展名（小写、不带点），例如 ['pdf', 'dwg', 'zip']。
     * 传空数组表示不做扩展名校验（不是「什么都不允许」）。
     */
    allowedExt: { type: Array, default: () => [] },
    /** 按钮旁的提示文案，例如「pdf / doc(x) / 图片 / dwg / zip 等」 */
    allowedExtText: { type: String, default: '' },
    /** 按钮文案 */
    buttonText: { type: String, default: '选择文件上传' },
    /** 透传给原生 input 的 accept 属性（仅起过滤建议作用，真正的校验在 beforeUpload 逻辑里） */
    accept: { type: String, default: '' },
    /** 隐藏 input 的 id，留空自动生成（需要外部 label 关联时才传） */
    id: { type: String, default: '' },
  },
  data () {
    return {
      /** 是否正在上传（对外通过 uploading-change 暴露，也可由父组件 $refs 直接读） */
      uploading: false,
      /** 校验失败清单：[{ name, reason }]，下一次选择文件时清空 */
      errors: [],
      /** 整批聚合进度（0-100 的整数） */
      percent: 0,
      /** 整批字节总量 */
      totalBytes: 0,
      /** 已成功传完的文件字节量 */
      completedBytes: 0,
      /** 已传完的文件数（用于总量为 0 时兜底计算进度） */
      completedFiles: 0,
      /** 当前在传文件的本地对象，供 progress / error 事件回传 */
      currentFile: null,
      /** 等待队列（已通过校验的文件） */
      queue: [],
      /** 当前在飞的 XMLHttpRequest，供 abort() 使用 */
      currentXhr: null,
      /** 用户主动取消标记：置位后网络中断不再报「上传失败」 */
      aborted: false,
      /**
       * 自动生成的 input id（外部未传 id 时使用）。
       * 必须在这里一次性生成，不能放到 computed 里 —— 在 computed 内部给响应式属性赋值
       * 会让该 computed 自我失效，属于典型的副作用陷阱（eslint 的
       * vue/no-side-effects-in-computed-properties 也会报错）。
       */
      localId: `screen-upload-${(uploadSeed += 1)}`,
    }
  },
  computed: {
    /** 隐藏 input 的最终 id：优先用外部传入的 id */
    inputId () {
      return this.id || this.localId
    },
    /** 规范化后的允许扩展名（小写、去空白），空数组表示不做扩展名校验 */
    normalizedExt () {
      return (this.allowedExt || []).map(item => String(item).trim().toLowerCase()).filter(Boolean)
    },
    /** 进度数值文案 */
    percentText () {
      return `${this.percent}%`
    },
    /**
     * 校验失败的播报文本：内容随失败数量变化，读屏会朗读新值。
     * 失败清单被清空时置空串，保证下一次失败仍然是一次「变化」。
     */
    rejectSummary () {
      if (!this.errors.length) return ''
      const names = this.errors.map(item => item.name).join('、')
      return `有 ${this.errors.length} 个文件未通过校验：${names}`
    },
    /** progressbar 的读屏文案（数值 + 已传字节 / 总字节） */
    progressText () {
      const done = this.formatSize(this.completedBytes)
      const total = this.formatSize(this.totalBytes)
      return `已上传 ${this.percent}%（${done} / ${total}）`
    },
  },
  /**
   * 组件销毁：
   * 1. 丢弃 in-flight 请求的事件回调，避免销毁后回调仍触碰 this；
   * 2. 中止 in-flight 请求。当前实现除 XHR 外没有注册任何全局监听或定时器，
   *    因此这里是唯一的清理点（无内存泄漏）。
   */
  beforeDestroy () {
    const xhr = this.currentXhr
    this.currentXhr = null
    this.queue = []
    if (xhr) {
      xhr.onprogress = null
      xhr.onload = null
      xhr.onerror = null
      xhr.ontimeout = null
      xhr.onabort = null
      try {
        xhr.abort()
      } catch (e) {
        // 忽略：浏览器在连接已关闭时可能抛错
      }
    }
  },
  methods: {
    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------
    /**
     * 点击可见按钮：打开系统文件选择器。
     * 输入框本身 tabindex="-1" 且 aria-hidden，键盘可达性由按钮承担。
     */
    handleTriggerClick () {
      if (this.disabled || this.uploading) return
      const input = this.$refs.fileInput
      if (!input) return
      // 打开选择器前清空 value：同一批里连续选择同一文件也能再次触发 change
      input.value = ''
      input.click()
    },
    /**
     * 原生 change 的处理顺序：校验、入队、顺序上传。
     * @param {Event} event 原生 change 事件
     */
    handleFileChange (event) {
      const input = event && event.target ? event.target : this.$refs.fileInput
      const selected = input && input.files ? Array.prototype.slice.call(input.files) : []
      if (!selected.length) return

      // 每次重新选择都清空上一轮的错误清单，避免错误信息与新选择混杂
      this.errors = []

      // 单选模式下多选了文件：只取第一个，其余通过 exceed 告知父组件
      let files = selected
      if (!this.multiple && selected.length > 1) {
        files = selected.slice(0, 1)
        this.$emit('exceed', selected)
      }

      const accepted = []
      files.forEach(file => {
        const reason = this.validateFile(file)
        if (reason) {
          this.rejectFile(file, reason)
          return
        }
        accepted.push(file)
      })

      if (!accepted.length) {
        // 全部被拒收：立即复位，保证同一个文件重新选择也能触发 change
        this.resetInput()
        return
      }

      this.startBatch(accepted)
    },
    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------
    /**
     * 取扩展名（小写，不含点）
     * @param {string} fileName 文件名
     * @returns {string} 无扩展名时返回空串
     */
    resolveExt (fileName) {
      const name = String(fileName || '')
      const dot = name.lastIndexOf('.')
      // dot <= 0 覆盖「无扩展名」与「.gitignore 这类隐藏文件」
      return dot > 0 ? name.slice(dot + 1).toLowerCase() : ''
    },
    /**
     * 单文件校验
     * @param {File} file 待校验文件
     * @returns {string} 不通过时返回中文原因；通过时返回空串
     */
    validateFile (file) {
      const name = file && file.name ? file.name : '未命名文件'
      const size = toNumber(file && file.size, 0)
      if (size > this.maxSizeMb * 1024 * 1024) {
        return `文件「${name}」超过 ${this.maxSizeMb}MB 上限`
      }
      // 允许列表为空表示不做扩展名校验
      const allow = this.normalizedExt
      if (allow.length) {
        const ext = this.resolveExt(name)
        if (!ext || allow.indexOf(ext) === -1) {
          return `文件「${name}」的类型 .${ext || '未知'} 不在允许范围内`
        }
      }
      return ''
    },
    /**
     * 拒收单个文件：既发 reject 事件，也写进界面上的错误清单
     * （规范要求：验证失败不能只靠颜色或一次性 toast 传达）
     * @param {File} file 被拒收的文件
     * @param {string} reason 中文原因
     */
    rejectFile (file, reason) {
      this.errors.push({ name: file && file.name ? file.name : '未命名文件', reason })
      toast.warning(reason)
      this.$emit('reject', reason, file)
    },
    // ------------------------------------------------------------------
    // 上传（顺序执行，聚合整批进度）
    // ------------------------------------------------------------------
    /**
     * 开始一批上传：重置聚合进度并逐个串行发送
     * @param {File[]} files 已通过校验的文件
     */
    startBatch (files) {
      if (this.disabled || this.uploading) return
      this.aborted = false
      this.queue = files.slice()
      this.totalBytes = files.reduce((sum, file) => sum + toNumber(file && file.size, 0), 0)
      this.completedBytes = 0
      this.completedFiles = 0
      this.percent = 0
      this.setUploading(true)
      this.uploadNext()
    },
    /**
     * 取队首文件发一个 XMLHttpRequest；上一个结束后才发下一个
     */
    uploadNext () {
      if (this.aborted) {
        this.finishBatch()
        return
      }
      const file = this.queue.shift()
      if (!file) {
        this.finishBatch()
        return
      }
      this.currentFile = file

      const xhr = new XMLHttpRequest()
      this.currentXhr = xhr
      xhr.open('POST', this.action, true)

      // 额外请求头（如 X-Access-Token）
      const headers = this.headers || {}
      Object.keys(headers).forEach(key => {
        if (headers[key] !== undefined && headers[key] !== null) {
          xhr.setRequestHeader(key, headers[key])
        }
      })

      // 单文件进度折算为整批进度
      xhr.upload.onprogress = event => {
        // lengthComputable 为 false 时 event.total 不可信，跳过本次折算
        if (!event || !event.lengthComputable) return
        const previous = toNumber(file.loadedBytes, 0)
        file.loadedBytes = toNumber(event.loaded, 0)
        this.completedBytes += file.loadedBytes - previous
        this.emitProgress(file)
      }

      xhr.onload = () => {
        if (this.currentXhr === xhr) this.currentXhr = null
        this.setFileLoaded(file)
        this.handleXhrLoad(xhr, file)
      }

      xhr.onerror = () => {
        if (this.currentXhr === xhr) this.currentXhr = null
        this.handleXhrError(xhr, file)
      }

      xhr.ontimeout = () => {
        if (this.currentXhr === xhr) this.currentXhr = null
        this.handleXhrError(xhr, file)
      }

      const formData = new FormData()
      // 字段名固定为 file（jeecg 通用上传接口约定）
      formData.append('file', file, file.name)
      const extra = this.data || {}
      Object.keys(extra).forEach(key => {
        const value = extra[key]
        if (value !== undefined && value !== null) {
          formData.append(key, value)
        }
      })

      try {
        xhr.send(formData)
      } catch (e) {
        this.currentXhr = null
        this.handleXhrError(xhr, file, e)
      }
    },
    /**
     * 计算整批进度：已完成字节 + 当前文件已传字节 占 总字节 的比例。
     * 总字节为 0（空文件）时退化为按文件个数计进度，保证不会永远停在 0%。
     * @returns {number} 0-100 的整数
     */
    computePercent () {
      let ratio = 0
      if (this.totalBytes > 0) {
        ratio = this.completedBytes / this.totalBytes
      } else if (this.queue.length || this.currentFile) {
        const total = this.completedFiles + this.queue.length + 1
        ratio = total > 0 ? this.completedFiles / total : 0
      }
      return Math.max(0, Math.min(100, Math.round(ratio * 100)))
    },
    /**
     * 把当前文件的剩余字节一次性计入（服务端可能在收到最后一个 progress 后直接返回）
     * @param {File} file 当前文件
     */
    setFileLoaded (file) {
      if (!file) return
      const size = toNumber(file.size, 0)
      const loaded = toNumber(file.loadedBytes, 0)
      if (size > loaded) {
        file.loadedBytes = size
        this.completedBytes += size - loaded
      }
    },
    /**
     * 重算整批进度并广播 progress（进度变化后必须走这里，避免漏发事件）
     * @param {File} file 当前文件
     */
    emitProgress (file) {
      this.percent = this.computePercent()
      this.$emit('progress', this.percent, file)
    },
    /**
     * 响应到达：解析 jeecg 统一信封 { success, message, code, result }
     * @param {XMLHttpRequest} xhr 请求对象
     * @param {File} file 对应文件
     */
    handleXhrLoad (xhr, file) {
      // abort() 已经置位：整批已被 abort() 收尾，这里直接返回，不报失败也不续传
      if (this.aborted) return
      const name = file && file.name ? file.name : '未命名文件'

      // 服务端可能在最后一个 progress 事件之前就返回，这里把当前文件的剩余字节补记后
      // 必须重算一次进度，否则进度条会停在 100% 以下
      this.emitProgress(file)

      if (xhr.status < 200 || xhr.status >= 300) {
        this.failFile(`文件「${name}」上传失败（HTTP ${xhr.status}），请检查后端服务与上传目录权限`, file)
        return
      }

      let res = null
      try {
        res = JSON.parse(xhr.responseText)
      } catch (e) {
        res = null
      }
      if (!res || typeof res !== 'object') {
        // 常见原因：action 配错，打到了网关/登录页，返回 HTML 或空串
        this.failFile('上传响应无法解析，请检查上传接口地址', file)
        return
      }
      if (!res.success) {
        this.failFile(res.message || `文件「${name}」上传失败`, file)
        return
      }

      // jeecg 通用上传接口把相对路径放在 message 里，result 作为兜底
      const storePath = res.message || res.result
      if (!storePath) {
        this.failFile(`文件「${name}」上传成功但未返回存储路径`, file)
        return
      }

      this.$emit('success', { file, storePath, response: res })
      // 该文件已经计入 setFileLoaded，这里只累加计数（供总字节为 0 时兜底算进度）
      this.completedFiles += 1
      this.currentFile = null
      this.uploadNext()
    },
    /**
     * 网络层失败：区分「主动取消 / 超时 / 网络错误」三种情况，给出不同文案
     * @param {XMLHttpRequest} xhr 请求对象
     * @param {File} file 对应文件
     * @param {Error} [exception] send 阶段同步抛出的异常
     */
    handleXhrError (xhr, file, exception) {
      // 用户主动 abort()：不算失败。收尾由 abort() 负责，这里直接返回避免重复收尾
      if (this.aborted || (xhr && xhr.__screenAborted)) return
      const name = file && file.name ? file.name : '未命名文件'
      let message
      if (xhr && xhr.readyState !== 4) {
        // 未到 DONE 就报错，基本是超时（大文件 + 弱网）
        message = `文件「${name}」上传超时，请检查网络后重试`
      } else if (exception) {
        message = `文件「${name}」上传请求发送失败（${exception.message || '未知错误'}）`
      } else {
        message = `文件「${name}」上传失败，请检查网络与后端服务`
      }
      this.failFile(message, file)
    },
    /**
     * 统一失败出口：弹一条提示 + 发 error 事件，并终止当前批次
     * @param {string} message 中文错误文案
     * @param {File} file 对应文件
     */
    failFile (message, file) {
      toast.error(message)
      this.$emit('error', message, file)
      // 失败即终止整批：避免用户在「以为都传上去了」的情况下漏文件
      this.queue = []
      this.finishBatch()
    },
    /**
     * 收尾：复位进度与队列、恢复按钮、并清空 input.value
     * （清空 value 是必须的：否则重新选择同一个文件不会再触发 change）
     */
    finishBatch () {
      if (!this.uploading && !this.queue.length && !this.currentXhr) {
        this.resetInput()
        return
      }
      this.setUploading(false)
      this.currentFile = null
      this.currentXhr = null
      this.queue = []
      this.percent = 0
      this.completedBytes = 0
      this.totalBytes = 0
      this.completedFiles = 0
      this.resetInput()
    },
    /**
     * 清空隐藏 input 的 value，保证「再选同一文件」仍会触发 change
     */
    resetInput () {
      const input = this.$refs.fileInput
      if (input) input.value = ''
    },
    /**
     * 切换上传态并广播 uploading-change
     * @param {boolean} value 新的上传态
     */
    setUploading (value) {
      const next = !!value
      if (this.uploading === next) return
      this.uploading = next
      this.$emit('uploading-change', next)
    },
    // ------------------------------------------------------------------
    // 公开方法（通过 $refs 调用）
    // ------------------------------------------------------------------
    /**
     * 中止当前上传。
     * 主动取消不按失败上报：不会发 error 事件，也不会弹出失败提示。
     * @returns {{file: (File|null), completedBytes: number}} 被中止的文件与已传字节，便于父组件写操作日志
     */
    abort () {
      const xhr = this.currentXhr
      const file = this.currentFile
      const completedBytes = this.completedBytes
      if (!xhr) {
        // 没有在飞请求时只做状态复位，避免父组件重复调用出问题
        this.finishBatch()
        return { file: null, completedBytes }
      }
      // 先置位再 abort：abort() 会同步触发 onerror，必须让回调知道这是主动取消
      this.aborted = true
      xhr.__screenAborted = true
      this.currentXhr = null
      try {
        xhr.abort()
      } catch (e) {
        // 忽略：连接已关闭时浏览器可能抛错
      }
      this.queue = []
      this.currentFile = null
      this.percent = 0
      this.completedBytes = 0
      this.totalBytes = 0
      this.completedFiles = 0
      this.setUploading(false)
      // 结束整批，并复位 input 让用户可以立刻重新选同一个文件
      this.finishBatch()
      return { file, completedBytes }
    },
    /**
     * 清空内部队列与进度状态（不动错误清单，错误清单在下一次选择文件时自动清空）。
     * 用于父组件在关闭弹窗、切换业务对象时复位控件。
     */
    clear () {
      this.queue = []
      this.currentFile = null
      this.errors = []
      this.percent = 0
      this.completedBytes = 0
      this.totalBytes = 0
      this.completedFiles = 0
      this.setUploading(false)
      this.resetInput()
    },
    // ------------------------------------------------------------------
    // 展示辅助
    // ------------------------------------------------------------------
    /**
     * 字节数格式化（仅用于进度文案）
     * @param {number} bytes 字节数
     * @returns {string} 如 '1.20 MB'
     */
    formatSize (bytes) {
      const value = toNumber(bytes, 0)
      if (value <= 0) return '0 B'
      const units = ['B', 'KB', 'MB', 'GB', 'TB']
      let size = value
      let unit = 0
      while (size >= 1024 && unit < units.length - 1) {
        size /= 1024
        unit++
      }
      return unit === 0 ? `${size} B` : `${size.toFixed(2)} ${units[unit]}`
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-upload {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  width: 100%;

  // 隐藏的原生文件输入：1px + clip-path 裁到不可见，
  // 但仍在可访问树/可编程激活范围内（不用 display: none 的原因见模板注释）
  &__input {
    position: absolute;
    top: 0;
    left: 0;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    border: 0;
    overflow: hidden;
    white-space: nowrap;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
  }

  // 控制行：按钮 + 允许格式提示
  &__bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  // 允许格式提示：弱化文案，用 placeholder 混入统一色调
  &__hint {
    flex: 1 1 160px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    .screen-placeholder();
  }

  // 视觉隐藏但仍在可访问树内的播报区（不用 display: none）
  &__sr-status {
    position: absolute;
    top: 0;
    left: 0;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    border: 0;
    overflow: hidden;
    white-space: nowrap;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
  }

  // ---------- 进度 ----------
  &__progress {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__track {
    flex: 1 1 auto;
    min-width: 0;
    height: 4px;
    background: var(--screen-bar-track);
    border-radius: var(--screen-radius-pill);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    min-width: 2px;
    // 只改宽度不改高度，避免进度条所在行抖动
    transition: width var(--screen-duration) var(--screen-ease);
    .screen-bar-fill();
  }

  &__percent {
    flex: 0 0 auto;
    min-width: 42px;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    font-variant-numeric: tabular-nums;
    text-align: right;
    color: var(--screen-accent-soft);
  }

  // ---------- 校验失败清单 ----------
  &__errors {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    padding: var(--screen-space-2) 10px;
    // 该淡红底是令牌之外唯一被批准的语义底色
    background: rgba(255, 122, 107, 0.08);
    border: 1px solid var(--screen-danger);
    border-radius: var(--screen-radius-sm);
  }

  &__errors-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-danger);
  }

  &__error-list {
    margin: 0;
    padding: 0;
    max-height: 96px;
    overflow-y: auto;
    list-style: none;
    .screen-scrollbar();

    li + li {
      margin-top: 2px;
    }
  }

  &__error-item {
    display: flex;
    align-items: baseline;
    gap: 6px;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-sub);
  }

  // 条目必须以文件名打头，用户才能对上号
  &__error-name {
    flex: 0 1 auto;
    max-width: 40%;
    color: var(--screen-danger);
    .screen-ellipsis();
  }

  &__error-reason {
    flex: 1 1 auto;
    min-width: 0;
    .screen-ellipsis();
  }

  // 上传中/禁用时不做多余的视觉装饰：真正的禁用态由 ScreenButton 自己表达
  &.is-uploading &__hint {
    color: var(--screen-text-mute);
  }
}

// 尊重「减少动态效果」：令牌在 reduce 下已把时长置 0，这里再显式兜一层，
// 防止 token 未加载（例如单测环境）时仍有过渡动画
@media (prefers-reduced-motion: reduce) {
  .screen-upload__fill {
    transition: none;
  }
}
</style>
