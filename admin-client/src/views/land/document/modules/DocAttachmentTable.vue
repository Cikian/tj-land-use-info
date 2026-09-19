<template>
  <div class="doc-attachment">
    <div v-if="!disabled" class="doc-attachment__toolbar">
      <a-upload
        :action="uploadAction"
        :headers="headers"
        :data="{ biz: bizPath }"
        :multiple="true"
        :showUploadList="false"
        :beforeUpload="beforeUpload"
        :disabled="uploading"
        @change="handleUploadChange">
        <a-button type="primary" icon="upload" :loading="uploading">上传附件</a-button>
      </a-upload>
      <span class="doc-attachment__hint">
        允许 {{ allowedExtText }}，单文件不超过 {{ maxSizeMb }}MB；支持扫描件、公文正文与附件一并上传
      </span>
    </div>

    <a-table
      size="small"
      rowKey="rowKey"
      :columns="columns"
      :dataSource="rows"
      :pagination="false"
      :locale="{ emptyText: disabled ? '该公文没有附件' : '还没有附件' }">
      <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>
      <template slot="fileName" slot-scope="text, record">
        <a @click="handleDownload(record)">{{ text }}</a>
      </template>
      <template slot="fileSize" slot-scope="text, record">
        {{ record.readableSize || formatSize(text) }}
      </template>
      <template slot="action" slot-scope="text, record">
        <a @click="handleDownload(record)">下载</a>
        <template v-if="!disabled">
          <a-divider type="vertical" />
          <a-popconfirm title="确定移除该附件吗？" okText="确定" cancelText="取消" @confirm="handleRemove(record)">
            <a class="doc-attachment__danger">移除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { ACCESS_TOKEN } from '@/store/mutation-types'
  import { getFileAccessHttpUrl } from '@/api/manage'

  /**
   * 收发文附件表格（含上传）
   *
   * 与旧 tj-sfw 的关键差别：旧系统把多个附件用逗号拼在一个 file_path 字段里，
   * 文件名里出现逗号就会被拆成两个假附件；新系统用独立附件表，一行一个附件。
   *
   * 上传走 jeecg 通用上传接口 /sys/common/upload，服务端只存相对路径。
   */
  export default {
    name: 'DocAttachmentTable',
    props: {
      value: {
        type: Array,
        default: () => []
      },
      /** 上传子目录，如 /receive/2026/09 */
      bizPath: {
        type: String,
        default: '/receive'
      },
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        uploading: false,
        maxSizeMb: 200,
        allowedExt: [
          'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
          'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv',
          'rar', 'zip', '7z', 'wps', 'et', 'ofd', 'dwg', 'dxf', 'shp', 'dbf', 'shx',
          'mp4', 'avi', 'mov', 'wmv'
        ],
        uploadAction: window._CONFIG['domianURL'] + '/sys/common/upload',
        headers: {}
      }
    },
    computed: {
      rows () {
        return (this.value || []).map((item, index) => Object.assign({}, item, {
          rowKey: item.id || item.storePath || ('tmp-' + index)
        }))
      },
      allowedExtText () {
        return 'pdf / doc(x) / xls(x) / 图片 / ofd / zip / rar 等'
      },
      columns () {
        const list = [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '大小', dataIndex: 'fileSize', width: 110, scopedSlots: { customRender: 'fileSize' } },
          { title: '上传人', dataIndex: 'uploadName', width: 120, customRender: (text, record) => text || record.uploadBy || '—' },
          { title: '上传时间', dataIndex: 'uploadTime', width: 165, customRender: text => text || '—' },
          { title: '操作', width: 110, scopedSlots: { customRender: 'action' } }
        ]
        return list
      }
    },
    created () {
      this.headers = { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) }
    },
    methods: {
      beforeUpload (file) {
        const ext = this.resolveExt(file.name)
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
        if (status === 'done') {
          const res = info.file.response || {}
          if (res.success === false) {
            this.$message.error(res.message || `文件「${info.file.name}」上传失败`)
            return
          }
          const storePath = res.message || res.result
          if (!storePath) {
            this.$message.error(`文件「${info.file.name}」上传成功但未返回存储路径`)
            return
          }
          const next = (this.value || []).slice()
          next.push({
            id: null,
            fileName: info.file.name,
            fileExt: this.resolveExt(info.file.name),
            fileSize: info.file.size,
            storePath: storePath,
            storeType: 'local',
            uploadTime: this.nowText()
          })
          this.$emit('input', next)
          this.$message.success(`「${info.file.name}」上传成功`)
        } else if (status === 'error') {
          this.$message.error(`文件「${info.file.name}」上传失败，请检查后端服务与上传目录权限`)
        }
      },
      handleRemove (record) {
        const next = (this.value || []).filter(item => !this.sameRow(item, record))
        this.$emit('input', next)
      },
      sameRow (item, record) {
        if (item.id && record.id) {
          return item.id === record.id
        }
        return item.storePath === record.storePath
      },
      handleDownload (record) {
        // 已保存的附件走后端接口会带鉴权；这里统一用静态地址（jeecg 的 /sys/common/static 无需令牌）
        const raw = record.url || record.storePath
        if (!raw) {
          return
        }
        // getFileAccessHttpUrl 自己会补一个 '/'，所以先把前导斜杠去掉，避免出现双斜杠路径
        const url = getFileAccessHttpUrl(String(raw).replace(/^\/+/, ''))
        if (url) {
          window.open(url, '_blank')
        }
      },
      resolveExt (fileName) {
        if (!fileName) {
          return null
        }
        const dot = fileName.lastIndexOf('.')
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase()
      },
      nowText () {
        const now = new Date()
        const pad = value => String(value).padStart(2, '0')
        const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
        const time = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
        return `${date} ${time}`
      },
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '—'
        }
        const units = ['B', 'KB', 'MB', 'GB', 'TB']
        let size = value
        let unit = 0
        while (size >= 1024 && unit < units.length - 1) {
          size /= 1024
          unit++
        }
        return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(2)} ${units[unit]}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .doc-attachment {
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

    &__hint {
      flex: 1 1 260px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    &__danger {
      color: #ff4d4f;
    }
  }
</style>
