<template>
  <!--
    SHP 上传入库弹窗（MVT 空间表服务的"添加数据库"一体化流程）
    提交后后端自动：预检（库在不在/凭据对不对）→ 建库（如不存在）→ 启用 PostGIS →
    导入 → 替换式登记（land_mvt_source + sys_data_source，全在服务端完成）。
    成功后新库会出现在 MvtServiceList 的"数据库"下拉里。

    交互约定（2026-11-13）：
    - 提交先预检不上传文件：库不存在直接进导入；库已存在则让用户在
      连接导入（保留库，覆盖同名表）/ 覆盖重建（整库删除，仅本系统导入的库可选）间显式选择
    - 连接字段（地址/端口/账号/密码/库名）一旦修改，预检结果作废，需重新预检
    - 登记以本次连接信息为准（凭据预检时已验证），前端不再调用任何注册端点
    - 导入成功后表单整体替换为成功面板（3 秒倒计时自动关闭），失败时错误条置顶可改可重提
  -->
  <a-modal
    :visible="visible"
    width="720px"
    :mask-closable="false"
    @cancel="close"
  >
    <template slot="title">
      <div class="modal-title">添加数据库</div>
      <div class="modal-subtitle">上传 SHP 压缩包，自动建库、启用 PostGIS 并注册为 MVT 数据源</div>
    </template>

    <!-- 表单阶段：失败提示置顶（成功走下方成功面板，不再复用这条） -->
    <template v-if="phase === 'form'">
      <a-alert v-if="resultMsg && !resultOk" type="error" show-icon :message="resultMsg" class="error-alert" />
      <a-spin :spinning="submitting || prechecking">
        <a-form :form="form" @submit.prevent="handleSubmit">
          <div class="section-head">
            <span class="section-bar" />
            <span class="section-title">数据库连接</span>
            <span class="section-hint">可复用已有 PG，或导入时自动建库</span>
          </div>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="PG 地址" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input v-decorator="['pgHost', { initialValue: '127.0.0.1', rules: [{ required: true, message: '必填' }] }]" @change="resetPrecheck" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="端口" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-number v-decorator="['pgPort', { initialValue: 5432 }]" :min="1" :max="65535" style="width: 100%" @change="resetPrecheck" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="账号" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input v-decorator="['pgUser', { initialValue: 'postgres', rules: [{ required: true, message: '必填' }] }]" @change="resetPrecheck" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="密码" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input-password v-decorator="['pgPassword', { rules: [{ required: true, message: '必填' }] }]" @change="resetPrecheck" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item
            label="目标库名"
            :label-col="{ span: 4 }"
            :wrapper-col="{ span: 20 }"
            extra="导入后自动加入「数据库」下拉列表"
          >
            <a-input v-decorator="['dbName', { rules: [{ required: true, message: '必填（字母/数字/下划线/中文）' }] }]" placeholder="如 land_postgis" @change="resetPrecheck" />
          </a-form-item>

          <div class="section-head section-gap">
            <span class="section-bar" />
            <span class="section-title">SHP 数据</span>
            <span class="section-hint">导入完成即可发布为 MVT 服务</span>
          </div>
          <!-- 未选文件：拖拽上传区；已选文件：文件卡片（含大小与移除）。列表展示由自定义卡片负责，关掉默认列表防重复 -->
          <a-upload-dragger
            v-if="!fileList.length"
            accept=".zip"
            :show-upload-list="false"
            :before-upload="beforeUpload"
          >
            <a-icon type="inbox" class="upload-icon" />
            <p class="upload-main">点击或拖拽 zip 压缩包到此处</p>
            <p class="upload-sub">zip 内含且仅含一个 .shp，须包含同名 .dbf / .shx（.prj 建议一并打包）</p>
          </a-upload-dragger>
          <div v-else class="file-card">
            <span class="file-icon"><a-icon type="file-zip" /></span>
            <div class="file-info">
              <div class="file-name" :title="fileList[0].name">{{ fileList[0].name }}</div>
              <div class="file-size">{{ fileSizeText }}</div>
            </div>
            <a-button type="link" icon="delete" @click="handleRemove">移除</a-button>
          </div>
          <a-row :gutter="12" class="upload-after">
            <a-col :span="12">
              <a-form-item label="目标表名" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                <a-input v-decorator="['tableName']" placeholder="留空取 shp 文件名" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="SRID" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }" extra="填 0 表示从 .prj 自动识别">
                <a-input-number v-decorator="['srid', { initialValue: 4548 }]" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="导入引擎" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
            <!-- 说明跟在选项后面，替代原来挂在字段下方的统一注释 -->
            <a-radio-group v-decorator="['importEngine', { initialValue: 'shp2pgsql' }]">
              <div class="engine-option">
                <a-radio value="shp2pgsql">shp2pgsql<span class="engine-desc">（默认 · 需本机安装 PG 客户端）</span></a-radio>
              </div>
              <div class="engine-option">
                <a-radio value="geotools">geotools<span class="engine-desc">（纯 Java 实现 · 信创环境可用）</span></a-radio>
              </div>
            </a-radio-group>
          </a-form-item>
        </a-form>

        <!-- 预检发现目标库已存在：不再静默连接，让用户显式选择处理方式 -->
        <div v-if="pgExists" class="policy-box">
          <div class="policy-title">目标库「{{ precheckInfo.dbName }}」已存在</div>
          <div class="policy-tables" :title="policyTables">{{ policyTables }}</div>
          <a-radio-group v-model="policy" class="policy-options">
            <div class="engine-option">
              <a-radio value="connect">连接导入<span class="engine-desc">（保留现有库和其它表，仅同名表将被覆盖）</span></a-radio>
            </div>
            <div class="engine-option">
              <a-radio value="overwrite" :disabled="!allowOverwrite">覆盖重建<span class="engine-desc">（整库删除后重建，库内全部数据丢失）</span></a-radio>
              <div v-if="!allowOverwrite" class="policy-note">该库非本系统导入，不支持删除重建；请更换目标库名，或先在 PG 中手工处理</div>
            </div>
          </a-radio-group>
          <div class="policy-note">无论哪种方式，都将以本次填写的连接信息登记该库</div>
        </div>
      </a-spin>
    </template>

    <!-- 成功阶段：结果面板替换表单，摘要来自后端返回，不再 1.2 秒一闪而过 -->
    <div v-else class="success-panel">
      <a-icon type="check-circle" theme="filled" class="success-icon" />
      <div class="success-title">导入成功</div>
      <div class="success-summary">{{ successInfo.dbName }}.{{ successInfo.tableName }} · 共 {{ successInfo.rowCountText }} 行</div>
      <div class="success-sub">引擎 {{ successInfo.engine }} · 耗时 {{ successInfo.elapsedText }}</div>
      <div class="success-hint">新库已加入「数据库」下拉列表，关闭后可点击「刷新发现」立即看到新表</div>
    </div>

    <template slot="footer">
      <div v-if="phase === 'form'" class="footer-bar">
        <span class="footer-hint">{{ pgExists ? '请先选择已存在库的处理方式' : '导入过程约需数秒至数十秒，期间请勿关闭窗口' }}</span>
        <span class="footer-buttons">
          <a-button :disabled="submitting || prechecking" @click="close">取消</a-button>
          <a-button
            type="primary"
            :loading="submitting || prechecking"
            :disabled="!fileList.length"
            @click="handleSubmit"
          >
            {{ submitting ? '正在导入…' : prechecking ? '正在检查目标库…' : pgExists ? '确认导入' : '提交导入' }}
          </a-button>
        </span>
      </div>
      <div v-else class="footer-bar">
        <span class="footer-hint">{{ countdown }} 秒后自动关闭</span>
        <span class="footer-buttons">
          <a-button type="primary" @click="close">立即关闭</a-button>
        </span>
      </div>
    </template>
  </a-modal>
</template>

<script>
import { importShp, precheckDb } from '@/api/land/spatial'

export default {
  name: 'ShpImportModal',
  data () {
    return {
      visible: false,
      // form | success：成功后不再回到表单（要导新文件重新打开弹窗），避免倒计时里被误改
      phase: 'form',
      form: this.$form.createForm(this),
      fileList: [],
      // 预检状态：null=未预检；提交时先预检（不上传文件），库已存在才进选择
      prechecking: false,
      precheckInfo: null,
      policy: 'connect',
      submitting: false,
      resultMsg: '',
      resultOk: false,
      successInfo: { dbName: '', tableName: '', rowCountText: '0', engine: '', elapsedText: '' },
      countdown: 3,
      countdownTimer: null
    }
  },
  computed: {
    fileSizeText () {
      if (!this.fileList.length) return ''
      const size = this.fileList[0].size || 0
      return size >= 1024 * 1024 ? (size / 1024 / 1024).toFixed(2) + ' MB' : Math.max(1, Math.round(size / 1024)) + ' KB'
    },
    pgExists () {
      return !!(this.precheckInfo && this.precheckInfo.pgExists)
    },
    allowOverwrite () {
      return !!(this.precheckInfo && this.precheckInfo.ownImported)
    },
    policyTables () {
      const tables = (this.precheckInfo && this.precheckInfo.tables) || []
      if (!tables.length) return '库内暂无用户表'
      const shown = tables.slice(0, 8).join('、')
      return tables.length > 8 ? `${shown} 等，共 ${tables.length} 张表` : `${shown}（共 ${tables.length} 张表）`
    }
  },
  beforeDestroy () { this.stopCountdown() },
  methods: {
    open () {
      this.stopCountdown()
      this.form.resetFields()
      this.fileList = []
      this.resultMsg = ''
      this.resultOk = false
      this.phase = 'form'
      this.precheckInfo = null
      this.policy = 'connect'
      this.successInfo = { dbName: '', tableName: '', rowCountText: '0', engine: '', elapsedText: '' }
      this.visible = true
    },
    close () {
      // 预检/导入进行中不允许关闭（右上角 ✕ / 取消都走这里），否则进度反馈丢失
      if (this.submitting || this.prechecking) return
      this.stopCountdown()
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
    /** 连接信息一旦修改，预检结果（含覆盖/连接选择）作废——旧结论对新凭据不成立 */
    resetPrecheck () {
      if (this.precheckInfo) {
        this.precheckInfo = null
        this.policy = 'connect'
        this.resultMsg = ''
      }
    },
    handleSubmit () {
      this.form.validateFields((err, values) => {
        if (err) return
        if (!this.precheckInfo) {
          this.runPrecheck(values)
          return
        }
        this.doImport(values)
      })
    },
    /** 第一步：预检（不上传文件）。库不存在直接续跑导入；已存在则停在选择区等用户确认 */
    async runPrecheck (values) {
      this.prechecking = true
      this.resultMsg = ''
      try {
        const res = await precheckDb({
          pgHost: values.pgHost,
          pgPort: values.pgPort,
          pgUser: values.pgUser,
          pgPassword: values.pgPassword,
          dbName: values.dbName
        })
        if (!res.success) throw new Error(res.message || '预检失败')
        const info = res.result || {}
        info.dbName = values.dbName
        this.precheckInfo = info
        if (!info.pgExists) {
          await this.doImport(values)
        }
      } catch (e) {
        this.resultOk = false
        this.resultMsg = e.message || '预检失败'
      } finally {
        this.prechecking = false
      }
    },
    /** 第二步：真正导入。库已存在时带上用户选的策略（connect / overwrite） */
    async doImport (values) {
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
      if (this.pgExists) fd.append('existingPolicy', this.policy)

      this.submitting = true
      this.resultMsg = ''
      try {
        const res = await importShp(fd)
        if (res.success) {
          const r = res.result || {}
          this.resultOk = true
          this.successInfo = {
            dbName: r.dbName || values.dbName,
            tableName: r.tableName || '-',
            rowCountText: Number(r.rowCount || 0).toLocaleString(),
            engine: r.engine || '-',
            elapsedText: (Number(r.elapsedMs || 0) / 1000).toFixed(1) + ' 秒'
          }
          this.phase = 'success'
          this.startCountdown()
        } else {
          this.resultOk = false
          this.resultMsg = res.message || '导入失败'
        }
      } catch (e) {
        this.resultOk = false
        this.resultMsg = '导入失败：' + (e.message || e)
      } finally {
        this.submitting = false
      }
    },
    startCountdown () {
      this.stopCountdown()
      this.countdown = 3
      this.countdownTimer = setInterval(() => {
        this.countdown--
        if (this.countdown <= 0) this.close()
      }, 1000)
    },
    stopCountdown () {
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer)
        this.countdownTimer = null
      }
    }
  }
}
</script>

<style lang="less" scoped>
.modal-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.85);
}
.modal-subtitle {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: rgba(0, 0, 0, 0.45);
}
.error-alert {
  margin-bottom: 16px;
}
.section-head {
  display: flex;
  align-items: center;
  margin: 8px 0 16px;
}
.section-gap {
  margin-top: 16px;
}
.section-bar {
  flex-shrink: 0;
  width: 3px;
  height: 14px;
  margin-right: 8px;
  border-radius: 1.5px;
  background: #1890ff;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
.section-hint {
  margin-left: auto;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.upload-icon {
  font-size: 42px;
  color: #1890ff;
}
.upload-main {
  margin: 8px 0 4px;
  font-size: 15px;
  color: rgba(0, 0, 0, 0.85);
}
.upload-sub {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.file-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fafafa;
}
.file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 4px;
  background: #fff7e6;
  color: #fa8c16;
  font-size: 20px;
}
.file-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}
.file-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.85);
}
.file-size {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.upload-after {
  margin-top: 8px;
}
.engine-option {
  line-height: 28px;
}
.engine-option + .engine-option {
  margin-top: 4px;
}
.engine-desc {
  margin-left: 4px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.policy-box {
  margin-top: 8px;
  padding: 16px;
  border: 1px solid #ffd591;
  border-radius: 4px;
  background: #fffbe6;
}
.policy-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  overflow-wrap: anywhere;
}
.policy-tables {
  margin-top: 6px;
  font-size: 12px;
  line-height: 20px;
  color: rgba(0, 0, 0, 0.45);
  overflow-wrap: anywhere;
}
.policy-options {
  display: block;
  margin-top: 12px;
}
.policy-note {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.success-panel {
  padding: 24px 0 8px;
  text-align: center;
}
.success-icon {
  font-size: 56px;
  color: #52c41a;
}
.success-title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
.success-summary {
  margin-top: 12px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  overflow-wrap: anywhere;
}
.success-sub {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}
.success-hint {
  max-width: 440px;
  margin: 16px auto 0;
  padding: 8px 12px;
  font-size: 12px;
  text-align: left;
  color: rgba(0, 0, 0, 0.65);
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
}
.footer-bar {
  display: flex;
  align-items: center;
}
.footer-hint {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.footer-buttons .ant-btn + .ant-btn {
  margin-left: 8px;
}
</style>
