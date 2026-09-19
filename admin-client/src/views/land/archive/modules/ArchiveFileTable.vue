<template>
  <div class="archive-file-table">
    <!-- 工具条 -->
    <div v-if="!disabled" class="archive-file-table__toolbar">
      <div class="archive-file-table__toolbar-item">
        <span class="archive-file-table__label">批量上传默认类别</span>
        <category-picker
          ref="defaultCategory"
          v-model="defaultCategoryId"
          :allowClear="false"
          placeholder="先选类别，再上传文件" />
      </div>
      <a-upload
        :action="uploadAction"
        :headers="headers"
        :data="{ biz: bizPath }"
        :multiple="true"
        :showUploadList="false"
        :beforeUpload="beforeUpload"
        :disabled="uploading"
        @change="handleUploadChange">
        <a-button type="primary" icon="upload" :loading="uploading">上传档案文件</a-button>
      </a-upload>
      <span class="archive-file-table__hint">
        允许 {{ allowedExtText }}，单文件不超过 {{ maxSizeMb }}MB；每个文件必须选择一个档案类别（末级）
      </span>
    </div>

    <a-table
      size="small"
      rowKey="rowKey"
      :columns="columns"
      :dataSource="rows"
      :pagination="false"
      :locale="{ emptyText: disabled ? '该档案还没有卷内文件' : '还没有文件，请先选择默认类别再上传' }">
      <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>

      <template slot="fileName" slot-scope="text, record">
        <a class="archive-file-table__file" @click="handlePreview(record)">{{ text }}</a>
        <div v-if="record.fileTitle && record.fileTitle !== text" class="archive-file-table__title">
          题名：{{ record.fileTitle }}
        </div>
      </template>

      <template slot="category" slot-scope="text, record">
        <span v-if="disabled" class="archive-file-table__category">{{ text || '—' }}</span>
        <category-picker
          v-else
          :value="record.categoryId"
          :allowClear="false"
          placeholder="选择类别"
          @change="value => handleCategoryChange(record, value)" />
      </template>

      <template slot="fileSize" slot-scope="text, record">
        {{ record.readableSize || formatSize(text) }}
      </template>

      <template slot="status" slot-scope="text">
        <a-tag :color="statusColor(text)">{{ text || '已归档' }}</a-tag>
      </template>

      <template slot="action" slot-scope="text, record">
        <a v-has="'land:archive:download'" @click="handlePreview(record)">下载</a>
        <template v-if="!disabled">
          <span v-has="'land:archive:delete'">
            <a-divider type="vertical" />
            <a-popconfirm title="确定移除该文件吗？" okText="确定" cancelText="取消" @confirm="handleRemove(record)">
              <a class="archive-file-table__danger">移除</a>
            </a-popconfirm>
          </span>
        </template>
      </template>
    </a-table>

    <div class="archive-file-table__footer">
      共 <b>{{ rows.length }}</b> 个文件，合计 <b>{{ totalSizeText }}</b>
      <span v-if="missingCategoryCount" class="archive-file-table__warn">
        · 有 {{ missingCategoryCount }} 个文件还没选择档案类别
      </span>
    </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { ACCESS_TOKEN } from '@/store/mutation-types'
  import { getFileAccessHttpUrl } from '@/api/manage'
  import { archiveUrl, buildDownloadUrl } from '@/api/land/archive'
  import CategoryPicker from './CategoryPicker'

  /**
   * 档案卷内文件表格（含上传）
   *
   * 需求约定：「新增时需要重点是需要上传文件，每个文件需要关联一个档案类别」。
   * 因此这里做了两件事：
   *  1. 上传前先选一个「批量上传默认类别」，一次传多个文件时都先归到这个类别；
   *  2. 每一行都可以单独改类别，最终以行上的类别为准（服务端逐条校验必须是叶子类别）。
   *
   * 上传走 jeecg 通用上传接口 /sys/common/upload，落盘在 jeecg.path.upload 下，
   * 服务端只存相对路径（storePath），下载时由后端按 id 查库解析路径。
   */
  export default {
    name: 'ArchiveFileTable',
    components: { CategoryPicker },
    props: {
      /** v-model：文件数组 */
      value: {
        type: Array,
        default: () => []
      },
      /** 业务上传子目录，如 /archive/2026/09 */
      bizPath: {
        type: String,
        default: '/archive'
      },
      /** 只读模式（详情页） */
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        uploading: false,
        defaultCategoryId: undefined,
        maxSizeMb: 200,
        allowedExt: [
          'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
          'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv',
          'rar', 'zip', '7z', 'dwg', 'dxf', 'shp', 'dbf', 'shx', 'prj', 'kml', 'kmz',
          'mp4', 'avi', 'mov', 'wmv'
        ],
        uploadAction: window._CONFIG['domianURL'] + '/sys/common/upload',
        headers: {}
      }
    },
    computed: {
      rows () {
        return (this.value || []).map((item, index) => {
          return Object.assign({}, item, {
            rowKey: item.id || item.storePath || ('tmp-' + index)
          })
        })
      },
      allowedExtText () {
        return 'pdf / doc(x) / xls(x) / ppt(x) / 图片 / dwg / shp / zip / rar 等'
      },
      totalSizeText () {
        const total = (this.value || []).reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
        return this.formatSize(total)
      },
      missingCategoryCount () {
        return (this.value || []).filter(item => !item.categoryId).length
      },
      columns () {
        const list = [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '档案类别', dataIndex: 'categoryName', width: 240, scopedSlots: { customRender: 'category' } },
          { title: '大小', dataIndex: 'fileSize', width: 100, scopedSlots: { customRender: 'fileSize' } },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
          { title: '操作', width: 120, scopedSlots: { customRender: 'action' } }
        ]
        return list
      }
    },
    created () {
      this.headers = { 'X-Access-Token': Vue.ls.get(ACCESS_TOKEN) }
    },
    methods: {
      // ------------------------------------------------------------------
      // 上传
      // ------------------------------------------------------------------
      beforeUpload (file) {
        if (!this.defaultCategoryId) {
          this.$message.warning('请先选择「批量上传默认类别」，再上传文件')
          return false
        }
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
          // jeecg 通用上传接口把相对路径放在 message 里
          const storePath = res.message || res.result
          if (!storePath) {
            this.$message.error(`文件「${info.file.name}」上传成功但未返回存储路径`)
            return
          }
          this.appendFile({
            id: null,
            categoryId: this.defaultCategoryId,
            fileName: info.file.name,
            fileTitle: this.stripExt(info.file.name),
            fileExt: this.resolveExt(info.file.name),
            fileSize: info.file.size,
            storePath: storePath,
            storeType: 'local',
            status: '已归档'
          })
          this.$message.success(`「${info.file.name}」上传成功`)
        } else if (status === 'error') {
          this.$message.error(`文件「${info.file.name}」上传失败，请检查后端服务与上传目录权限`)
        }
      },
      appendFile (file) {
        const next = (this.value || []).slice()
        next.push(file)
        this.$emit('input', next)
      },
      // ------------------------------------------------------------------
      // 行内操作
      // ------------------------------------------------------------------
      handleCategoryChange (record, categoryId) {
        const next = (this.value || []).map(item => {
          if (this.sameRow(item, record)) {
            return Object.assign({}, item, { categoryId: categoryId, categoryName: null })
          }
          return item
        })
        this.$emit('input', next)
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
      handlePreview (record) {
        // 已保存的文件走后端下载接口（会写「下载」操作记录）；新上传的还没落库，走静态地址
        if (record.id) {
          window.open(buildDownloadUrl(archiveUrl.fileDownload, { id: record.id }), '_blank')
          return
        }
        const url = record.url ? getFileAccessHttpUrl(record.url) : getFileAccessHttpUrl(record.storePath)
        if (url) {
          window.open(url, '_blank')
        }
      },
      // ------------------------------------------------------------------
      // 校验（父组件提交前调用）
      // ------------------------------------------------------------------
      /**
       * @returns {string} 校验不通过的原因；通过时返回空串
       */
      validate () {
        const files = this.value || []
        if (!files.length) {
          return '请至少上传一个档案文件'
        }
        const missing = files.filter(item => !item.categoryId)
        if (missing.length) {
          return `有 ${missing.length} 个文件还没选择档案类别，请补齐后再提交`
        }
        return ''
      },
      /** 生成上传子目录：/archive/{yyyy}/{MM} */
      buildBizPath () {
        const now = new Date()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        return `/archive/${now.getFullYear()}/${month}`
      },
      // ------------------------------------------------------------------
      // 工具
      // ------------------------------------------------------------------
      resolveExt (fileName) {
        if (!fileName) {
          return null
        }
        const dot = fileName.lastIndexOf('.')
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase()
      },
      stripExt (fileName) {
        if (!fileName) {
          return null
        }
        const dot = fileName.lastIndexOf('.')
        return dot > 0 ? fileName.substring(0, dot) : fileName
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
      },
      statusColor (status) {
        if (status === '审核中') {
          return 'orange'
        }
        if (status === '归档中') {
          return 'blue'
        }
        return 'green'
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .archive-file-table {
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

    &__hint {
      flex: 1 1 240px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    &__file {
      font-weight: 500;
    }

    &__title {
      font-size: 12px;
      color: @text-weak;
    }

    &__category {
      color: #334155;
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

    &__warn {
      color: #d48806;
    }
  }
</style>
