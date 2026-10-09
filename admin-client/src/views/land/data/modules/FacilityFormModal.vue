<template>
  <a-modal
    :title="title"
    :width="1000"
    :visible="visible"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleSave"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <a-form :form="form" layout="vertical">
        <a-collapse v-model="activeGroups" class="facility-form__groups">
          <!-- ============ 一、标识与属性 ============ -->
          <a-collapse-panel key="base">
            <template slot="header">
              <span class="group-title">一、标识与属性</span>
              <span class="group-hint">宗地编号与配套项目名称是必填，也是「同一宗地下不可重名」的判重键</span>
            </template>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="出让宗地编号" required>
                  <a-input v-model="model.crzdbh" read-only placeholder="由上方选择的宗地带出" />
                  <div class="form-item__hint">★ 由页面顶部选中的宗地决定，不接受手工输入（手输是孤儿配套的主要来源）</div>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="配套项目名称" required :validate-status="nameError ? 'error' : ''" :help="nameError">
                  <a-input
                    v-model="model.ptxmmc"
                    placeholder="例如：某某路道路工程"
                    @blur="handleCheckName" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="地块名称">
                  <a-input v-model="model.dkmc" placeholder="留空则继承宗地的地块名称" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="配套设施类别">
                  <a-select v-model="model.ptsslb" placeholder="请选择" allow-clear :options="categoryOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="行政区划">
                  <a-select v-model="model.xzqh" placeholder="留空则继承宗地" allow-clear :options="xzqhOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="项目分类">
                  <a-select v-model="model.xmfl" placeholder="留空则继承宗地" allow-clear :options="projectTypeOptions" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="建设性质">
                  <a-select v-model="model.jsxx" placeholder="请选择" allow-clear :options="buildNatureOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="道路等级">
                  <a-select v-model="model.dldj" placeholder="请选择" allow-clear :options="roadLevelOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="资金来源">
                  <a-select v-model="model.zjly" placeholder="请选择" allow-clear :options="fundSourceOptions" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="6">
                <a-form-item label="规划红线宽度(米)">
                  <a-input-number v-model="model.ghhxkd" :min="0" :precision="2" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="长度(米)">
                  <a-input-number v-model="model.cd" :min="0" :precision="2" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="投资估算(万元)">
                  <a-input-number v-model="model.tzgs" :min="0" :precision="2" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="承诺配套竣工时间">
                  <a-date-picker
                    v-model="model.dkcrscndptjgsj"
                    style="width: 100%"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="是否涉及提级论证">
                  <a-select v-model="model.sfzsjtjlz" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="提级论证是否通过">
                  <a-select v-model="model.tjlzsftg" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>

          <!-- ============ 二、参建单位 ============ -->
          <a-collapse-panel key="units">
            <template slot="header">
              <span class="group-title">二、参建单位</span>
              <span class="group-hint">六方单位</span>
            </template>
            <a-row :gutter="12">
              <a-col v-for="item in unitFields" :key="item.key" :span="8">
                <a-form-item :label="item.label">
                  <a-input v-model="model[item.key]" :placeholder="'请输入' + item.label" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>

          <!-- ============ 三、前期审批与进度 ============ -->
          <a-collapse-panel key="progress">
            <template slot="header">
              <span class="group-title">三、前期审批与进度</span>
              <span class="group-hint">
                这里的是「配套项目整体」的审批与开竣工情况；
                逐环节的进度请到「环节进度」里填写
              </span>
            </template>
            <a-row :gutter="12">
              <a-col v-for="item in triPhaseFields" :key="item.key" :span="8">
                <a-form-item :label="item.label">
                  <a-select
                    v-model="model[item.key]"
                    placeholder="请选择"
                    allow-clear
                    :options="item.options === 'status' ? pushStatusOptions : yesNoOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="概算批复金额(万元)">
                  <a-input-number v-model="model.gspfje" :min="0" :precision="2" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="资金落实情况">
                  <a-select v-model="model.zjlsqk" placeholder="请选择" allow-clear :options="fundStatusOptions" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="是否开工">
                  <a-select v-model="model.sfkg" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="开工状态">
                  <a-select v-model="model.kgzt" placeholder="请选择" allow-clear :options="pushStatusOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="是否竣工">
                  <a-select v-model="model.sfjg" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col v-for="item in dateFields" :key="item.key" :span="6">
                <a-form-item :label="item.label">
                  <a-date-picker
                    v-model="model[item.key]"
                    style="width: 100%"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="8">
                <a-form-item label="是否移交">
                  <a-select v-model="model.sfyj" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="核定用地与地籍调查">
                  <a-select v-model="model.ptxmhdydydjdc" placeholder="请选择" allow-clear :options="yesNoOptions" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>

          <!-- ============ 四、历史档案要件（只读） ============ -->
          <a-collapse-panel key="legacy">
            <template slot="header">
              <span class="group-title">四、历史档案要件（只读）</span>
              <span class="group-hint">
                ★ 这些字段在旧系统里只存「是/否」，文件本身存在磁盘目录上，目录一丢数据就没了。
                新系统改为附件管理（见「配套附件管理」），这里保留旧值仅供历史对照，不再作为录入入口
              </span>
            </template>
            <a-alert
              class="legacy-note"
              type="info"
              show-icon
              message="说明：旧库的「竣工文件(jgwj)」「移交文件(yjwj)」两列未纳入新实体（新数据统一走附件管理的「12 竣工与移交文件」），因此这里不展示，避免出现「填了写不进去」的假入口。" />
            <a-row :gutter="12">
              <a-col v-for="item in legacyFields" :key="item.key" :span="6">
                <a-form-item :label="item.label">
                  <a-input :value="model[item.key]" read-only placeholder="历史值" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>

          <!-- ============ 五、说明与录入 ============ -->
          <a-collapse-panel key="desc">
            <template slot="header">
              <span class="group-title">五、说明与录入信息</span>
            </template>
            <a-form-item label="具体问题">
              <a-textarea v-model="model.jtwt" :rows="2" :max-length="2500" placeholder="配套项目整体存在的问题" />
            </a-form-item>
            <a-form-item label="工作建议">
              <a-textarea v-model="model.gzjy" :rows="2" :max-length="1000" />
            </a-form-item>
            <a-form-item label="资料缺失内容及说明">
              <a-textarea v-model="model.zlqsnrjsm" :rows="2" :max-length="2500" />
            </a-form-item>
            <a-row :gutter="12">
              <a-col :span="6">
                <a-form-item label="备注">
                  <a-input v-model="model.bz" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="录入单位">
                  <a-input v-model="model.lrdw" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="录入人">
                  <a-input v-model="model.lrr" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="联系电话" :validate-status="phoneError ? 'error' : ''" :help="phoneError">
                  <a-input v-model="model.lxdh" placeholder="11 位手机号" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-panel>
        </a-collapse>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script>
  import {
    addFacility,
    editFacility,
    queryFacilityDetail,
    checkPtxmmc
  } from '@/api/land/facilityAdmin'
  import { queryXzqhOptions } from '@/api/land/landData'

  /**
   * 配套项目新增/编辑弹窗（53 个业务字段，分 5 组折叠）
   *
   * ★ 为什么必须分组、而不是一屏铺开 53 个输入框：
   *   53 个字段平铺大约是 3~4 屏高度，用户填到一半就分不清自己在填哪一类信息，
   *   而且「录入单位/联系电话」这类字段会被埋在最下面。
   *   分组后每组的语义是清晰的（参建单位 / 前期审批 / 档案要件…），
   *   折叠起来还能让用户先聚焦当前要改的那一组。
   *
   * ★ 为什么「出让宗地编号」是只读的：
   *   它由页面顶部选中的宗地决定。允许手输是**孤儿配套的主要来源** ——
   *   旧系统不校验宗地是否存在，设计文档实测产生了 60 行
   *   「配套表里有、宗地表里查不到」的孤儿数据，这些配套在按宗地关联的
   *   档案/收发文/台账里永远挂不上，也永远进不了宗地维度的统计。
   *
   * ★ 为什么 13 个「档案要件」字段是只读的：
   *   旧系统它们只存「是/否」，真正的文件在磁盘目录上（如「11-施工许可」），
   *   目录一丢数据就没了，而且无法记录大小/上传人/上传时间。
   *   新系统改为附件管理（`t_land_attachment`，见配套附件管理页），
   *   这里保留旧值仅供历史对照，不再作为录入入口。
   */
  export default {
    name: 'FacilityFormModal',
    data () {
      return {
        visible: false,
        loading: false,
        saving: false,
        isEdit: false,
        form: this.$form.createForm(this),
        model: this.emptyModel(),
        activeGroups: ['base'],
        nameError: '',
        phoneError: '',
        xzqhOptions: [],
        // ---- 枚举选项（与 sql/data/01_data_dict.sql 逐字一致） ----
        projectTypeOptions: [
          { value: '市级项目', label: '市级项目' },
          { value: '区级项目', label: '区级项目' }
        ],
        categoryOptions: [
          { value: '道路', label: '道路' },
          { value: '排水', label: '排水' },
          { value: '供水', label: '供水' },
          { value: '中水', label: '中水' },
          // ★ 旧录入页写「供气」而字典写「燃气」，新系统统一为「燃气」
          { value: '燃气', label: '燃气' },
          { value: '路灯', label: '路灯' },
          { value: '绿化', label: '绿化' },
          { value: '交通设施', label: '交通设施' }
        ],
        buildNatureOptions: [
          { value: '新建', label: '新建' },
          { value: '改建', label: '改建' },
          { value: '扩建', label: '扩建' },
          { value: '翻建', label: '翻建' },
          { value: '其他', label: '其他' }
        ],
        roadLevelOptions: [
          { value: '城市主干路', label: '城市主干路' },
          { value: '城市次干路', label: '城市次干路' },
          { value: '城市支路', label: '城市支路' }
        ],
        fundSourceOptions: [
          { value: '土地整理成本', label: '土地整理成本' },
          { value: '地块收益', label: '地块收益' },
          { value: '成本分摊', label: '成本分摊' },
          { value: '区内统筹', label: '区内统筹' },
          { value: '其它', label: '其它' }
        ],
        fundStatusOptions: [
          { value: '已落实', label: '已落实' },
          { value: '未落实', label: '未落实' }
        ],
        pushStatusOptions: [
          { value: '正常推进', label: '正常推进' },
          { value: '有问题', label: '有问题' }
        ],
        yesNoOptions: [
          { value: '是', label: '是' },
          { value: '否', label: '否' }
        ],
        unitFields: [
          { key: 'jsdw', label: '建设单位' },
          { key: 'sjdw', label: '设计单位' },
          { key: 'kcdw', label: '勘察单位' },
          { key: 'jldw', label: '监理单位' },
          { key: 'sgdw', label: '施工单位' },
          { key: 'jsgydw', label: '接收管养单位' }
        ],
        triPhaseFields: [
          { key: 'xjpfsfwc', label: '项建批复是否完成' },
          { key: 'xjpfzt', label: '项建批复状态', options: 'status' },
          { key: 'kypfsfwc', label: '可研批复是否完成' },
          { key: 'kypfzt', label: '可研批复状态', options: 'status' },
          { key: 'csjgspfsfwc', label: '初设及概算批复是否完成' },
          { key: 'csjgspfzt', label: '初设及概算批复状态', options: 'status' }
        ],
        dateFields: [
          { key: 'yjkgsj', label: '预计开工时间' },
          { key: 'sjkgsj', label: '实际开工时间' },
          { key: 'yjjgsj', label: '预计竣工时间' },
          { key: 'sjjgsj', label: '实际竣工时间' }
        ],
        legacyFields: [
          { key: 'xjpfwj', label: '项建批复文件' },
          { key: 'kypfwj', label: '可研批复文件' },
          { key: 'csjgspfwj', label: '初设及概算批复文件' },
          { key: 'dlgh', label: '道路规划' },
          { key: 'ghgcxk', label: '规划工程许可' },
          { key: 'gxzhslsj', label: '管线综合矢量数据' },
          { key: 'zyptfa', label: '专业配套方案' },
          { key: 'zyglyj', label: '专业管理意见' },
          { key: 'ghydxkyhbsxbl', label: '规划用地许可与划拨手续' },
          { key: 'sgxk', label: '施工许可' },
          { key: 'bdcdj', label: '不动产登记' }
        ]
      }
    },
    computed: {
      title () {
        return this.isEdit ? '编辑配套项目' : '新增配套项目'
      }
    },
    mounted () {
      queryXzqhOptions().then(res => {
        if (res.success && res.result) {
          this.xzqhOptions = res.result.map(item => ({ value: item.value, label: item.label }))
        }
      }).catch(() => { /* 区划下拉失败不阻断录入，用户可留空继承宗地 */ })
    },
    methods: {
      emptyModel () {
        return {
          id: null,
          crzdbh: null,
          ptxmmc: '',
          dkmc: '',
          ptsslb: undefined,
          xzqh: undefined,
          xmfl: undefined,
          jsxx: undefined,
          dldj: undefined,
          sfzsjtjlz: undefined,
          tjlzsftg: undefined,
          ghhxkd: null,
          cd: null,
          tzgs: null,
          zjly: undefined,
          dkcrscndptjgsj: null,
          jsdw: '',
sjdw: '',
kcdw: '',
jldw: '',
sgdw: '',
jsgydw: '',
          xjpfsfwc: undefined,
xjpfzt: undefined,
          kypfsfwc: undefined,
kypfzt: undefined,
          csjgspfsfwc: undefined,
csjgspfzt: undefined,
          gspfje: null,
zjlsqk: undefined,
          sfkg: undefined,
kgzt: undefined,
          yjkgsj: null,
sjkgsj: null,
sfjg: undefined,
          yjjgsj: null,
sjjgsj: null,
sfyj: undefined,
          ptxmhdydydjdc: undefined,
          xjpfwj: '',
kypfwj: '',
csjgspfwj: '',
dlgh: '',
ghgcxk: '',
gxzhslsj: '',
          zyptfa: '',
zyglyj: '',
ghydxkyhbsxbl: '',
sgxk: '',
bdcdj: '',
          jtwt: '',
gzjy: '',
zlqsnrjsm: '',
bz: '',
          lrdw: '',
lrr: '',
lxdh: ''
        }
      },

      /**
       * 打开弹窗。
       *
       * @param {string} id 配套项目ID（null/空 = 新增）
       * @param {object} land 当前宗地（用于带出编号与默认值）
       */
      open (id, land) {
        this.isEdit = !!id
        this.nameError = ''
        this.phoneError = ''
        this.model = this.emptyModel()
        if (land) {
          this.model.crzdbh = land.crzdbh
          this.model.dkmc = land.dkmc || ''
          this.model.xzqh = land.xzqh || undefined
          this.model.xmfl = land.xmfl || undefined
        }
        this.activeGroups = ['base']
        this.visible = true
        if (id) {
          this.loading = true
          queryFacilityDetail(id).then(res => {
            if (!res.success || !res.result) {
              this.$message.error(res.message || '配套项目详情加载失败')
              return
            }
            this.model = Object.assign(this.emptyModel(), res.result)
          }).catch(() => {
            this.$message.error('配套项目详情加载失败')
          }).finally(() => {
            this.loading = false
          })
        }
      },

      handleClose () {
        this.visible = false
      },

      /** 名称唯一性预校验（同一宗地下不可重名；跨宗地可以） */
      handleCheckName () {
        const name = (this.model.ptxmmc || '').trim()
        if (!name || !this.model.crzdbh) {
          this.nameError = ''
          return
        }
        checkPtxmmc(this.model.crzdbh, name, this.model.id).then(res => {
          if (res.success && res.result) {
            this.nameError = res.result.available ? '' : (res.result.message || '该名称不可用')
          }
        }).catch(() => { /* 预校验失败不阻断，提交时后端还会再校验一次 */ })
      },

      validate () {
        this.nameError = ''
        this.phoneError = ''
        if (!this.model.crzdbh) {
          this.$message.warning('缺少出让宗地编号，请先在页面上选择宗地')
          return false
        }
        if (!(this.model.ptxmmc || '').trim()) {
          this.$message.warning('配套项目名称不能为空')
          return false
        }
        const phone = (this.model.lxdh || '').trim()
        if (phone && !/^1[3456789]\d{9}$/.test(phone)) {
          this.phoneError = '联系电话格式不正确，应为 1 开头的 11 位手机号'
          this.$message.warning(this.phoneError)
          return false
        }
        // 竣工时间不能早于开工时间（旧系统不校验，库里存在「竣工早于开工」的脏数据）
        if (this.model.sjkgsj && this.model.sjjgsj && this.model.sjjgsj < this.model.sjkgsj) {
          this.$message.warning('实际竣工时间不能早于实际开工时间')
          return false
        }
        if (this.model.yjkgsj && this.model.yjjgsj && this.model.yjjgsj < this.model.yjkgsj) {
          this.$message.warning('预计竣工时间不能早于预计开工时间')
          return false
        }
        return true
      },

      handleSave () {
        if (!this.validate()) {
          return
        }
        this.saving = true
        const payload = Object.assign({}, this.model)
        const action = this.isEdit ? editFacility : addFacility
        action(payload).then(res => {
          if (!res.success) {
            this.$message.error(res.message || '保存失败')
            return
          }
          this.$message.success(this.isEdit ? '已保存' : '已新增')
          this.visible = false
          this.$emit('ok')
        }).catch(e => {
          this.$message.error((e && e.message) || '保存失败')
        }).finally(() => {
          this.saving = false
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .facility-form {
    &__groups {
      /deep/ .ant-collapse-header {
        padding: 8px 12px !important;
      }

      /deep/ .ant-collapse-content-box {
        padding: 12px 12px 0 !important;
      }

      /deep/ .ant-form-item {
        margin-bottom: 10px;
      }
    }

    .group-title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    .group-hint {
      margin-left: 10px;
      font-size: 12px;
      font-weight: 400;
      line-height: 18px;
      color: @text-weak;
    }

    .form-item__hint {
      margin-top: 4px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    .legacy-note {
      margin-bottom: 12px;
    }
  }
</style>
