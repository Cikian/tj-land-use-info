<template>
  <!--
    SHP 上传入库弹窗（MVT 空间表服务的"添加数据库"一体化流程）
    提交后后端自动：建库（如不存在）→ 启用 PostGIS → 导入 → 注册多数据源。
    成功后新库会出现在 MvtServiceList 的"数据库"下拉里。
  -->
  <a-modal
    :visible="visible"
    title="添加数据库（上传 SHP 入库）"
    width="640px"
    :mask-closable="false"
    @cancel="close"
  >
    <a-form :form="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" @submit.prevent="handleSubmit">
      <a-divider orientation="left">数据库</a-divider>
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="PG 地址" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
            <a-input v-decorator="['pgHost', { initialValue: '127.0.0.1', rules: [{ required: true, message: '必填' }] }]" size="small" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="端口" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
            <a-input-number v-decorator="['pgPort', { initialValue: 5432 }]" :min="1" :max="65535" style="width: 100%" size="small" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="账号" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
            <a-input v-decorator="['pgUser', { initialValue: 'postgres', rules: [{ required: true, message: '必填' }] }]" size="small" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="密码" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
            <a-input-password v-decorator="['pgPassword', { rules: [{ required: true, message: '必填' }] }]" size="small" />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="目标库名" extra="不存在则自动创建，并加入上方数据库下拉列表">
            <a-input v-decorator="['dbName', { rules: [{ required: true, message: '必填（字母/数字/下划线/中文）' }] }]" placeholder="如 land_postgis" size="small" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider orientation="left">SHP 数据</a-divider>
      <a-form-item label="SHP 压缩包" extra="zip 内含且仅含一个 .shp，并须包含同名 .dbf/.shx（.prj 建议一并打包）">
        <a-upload-dragger
          :file-list="fileList"
          :before-upload="beforeUpload"
          :remove="handleRemove"
          accept=".zip"
        >
          <p class="ant-upload-text" style="font-size: 13px">点击或拖拽 zip 到此</p>
        </a-upload-dragger>
      </a-form-item>
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="目标表名" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }" extra="留空取文件名">
            <a-input v-decorator="['tableName']" placeholder="留空取 shp 文件名" size="small" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="SRID" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }" extra="0 表示从 .prj 识别">
            <a-input-number v-decorator="['srid', { initialValue: 4548 }]" :min="0" style="width: 100%" size="small" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="导入引擎" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" extra="shp2pgsql 需本机安装 PG 客户端；geotools 为纯 Java（信创）">
        <a-select v-decorator="['importEngine', { initialValue: 'shp2pgsql' }]" size="small">
          <a-select-option value="shp2pgsql">shp2pgsql（默认）</a-select-option>
          <a-select-option value="geotools">geotools（纯 Java，信创）</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>

    <a-alert v-if="resultMsg" :type="resultOk ? 'success' : 'error'" :message="resultMsg" show-icon style="margin-bottom: 8px" />

    <template slot="footer">
      <a-button @click="close">取消</a-button>
      <a-button type="primary" :loading="submitting" :disabled="fileList.length === 0" @click="handleSubmit">提交导入</a-button>
    </template>
  </a-modal>
</template>

<script>
import { importShp } from '@/api/land/spatial'
import { postAction } from '@/api/manage'

export default {
  name: 'ShpImportModal',
  data () {
    return {
      visible: false,
      form: this.$form.createForm(this),
      fileList: [],
      submitting: false,
      resultMsg: '',
      resultOk: false,
      lastValues: null
    }
  },
  methods: {
    open () {
      this.form.resetFields()
      this.fileList = []
      this.resultMsg = ''
      this.visible = true
    },
    close () {
      this.visible = false
      if (this.resultOk) this.$emit('success')
    },
    beforeUpload (file) {
      if (!/\.zip$/i.test(file.name)) {
        this.$message.error('只能上传 .zip 压缩包')
        return false
      }
      this.fileList = [file]
      return false
    },
    handleRemove () {
      this.fileList = []
    },
    /**
     * 注册为 jeecg 多数据源（MVT 服务的"数据库"下拉即 sys_data_source）。
     * ★ 走 spatial 自己的注册端点直插明文密码——不走 /sys/dataSource/add，
     *   那个接口会对密码做解密，明文会被搅成乱码密文导致连接失败。
     * code 重复（重复导入同库）时后端 INSERT IGNORE 静默跳过。
     */
    registerDataSource (values, dbName) {
      const fd = new FormData()
      fd.append('code', dbName)
      fd.append('pgHost', values.pgHost)
      fd.append('pgPort', values.pgPort)
      fd.append('pgUser', values.pgUser)
      fd.append('pgPassword', values.pgPassword)
      fd.append('dbName', dbName)
      return postAction('/land/spatial/source/register', fd).catch(() => { /* 已存在不阻塞 */ })
    },
    handleSubmit () {
      this.form.validateFields((err, values) => {
        if (err) return
        const fd = new FormData()
        fd.append('file', this.fileList[0])
        fd.append('pgHost', values.pgHost)
        fd.append('pgPort', values.pgPort)
        fd.append('pgUser', values.pgUser)
        fd.append('pgPassword', values.pgPassword)
        fd.append('dbName', values.dbName)
        if (values.tableName) fd.append('tableName', values.tableName)
        fd.append('srid', values.srid || 0)
        if (values.importEngine) fd.append('importEngine', values.importEngine)

        this.submitting = true
        this.resultMsg = ''
        importShp(fd).then(res => {
          if (res.success) {
            const r = res.result || {}
            this.resultOk = true
            this.resultMsg = `导入成功：${r.dbName}.${r.tableName} 共 ${r.rowCount} 行（引擎 ${r.engine}，耗时 ${r.elapsedMs}ms）`
            // 注册数据源后关闭（关闭时 emit success 触发父页刷新下拉）
            this.registerDataSource(values, values.dbName).finally(() => {
              setTimeout(() => { this.close() }, 1200)
            })
          } else {
            this.resultOk = false
            this.resultMsg = res.message || '导入失败'
          }
        }).catch(e => {
          this.resultOk = false
          this.resultMsg = '导入失败：' + (e.message || e)
        }).finally(() => {
          this.submitting = false
        })
      })
    }
  }
}
</script>
