<template>
  <a-modal
    title="配套项目详情"
    :width="900"
    :visible="visible"
    :footer="null"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <!-- ============ 头部摘要 ============ -->
        <div class="facility-detail__head">
          <div class="facility-detail__title">
            <span class="facility-detail__name">{{ detail.ptxmmc }}</span>
            <a-tag v-if="detail.xmfl" color="blue">{{ detail.xmfl }}</a-tag>
            <a-tag v-if="detail.ptsslb">{{ detail.ptsslb }}</a-tag>
            <a-tag v-if="detail.jsxx">{{ detail.jsxx }}</a-tag>
          </div>
          <div class="facility-detail__meta">
            <span>出让宗地编号：<b>{{ detail.crzdbh || '—' }}</b></span>
            <span>地块名称：{{ detail.dkmc || '—' }}</span>
            <span>行政区划：{{ detail.xzqh || '—' }}</span>
          </div>
        </div>

        <!-- ============ 关键指标 ============ -->
        <div class="facility-detail__stats">
          <div class="stat-item">
            <div class="stat-item__label">长度(米)</div>
            <div class="stat-item__value">{{ valueOr(detail.cd) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-item__label">规划红线宽度(米)</div>
            <div class="stat-item__value">{{ valueOr(detail.ghhxkd) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-item__label">投资估算(万元)</div>
            <div class="stat-item__value">{{ valueOr(detail.tzgs) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-item__label">概算批复金额(万元)</div>
            <div class="stat-item__value">{{ valueOr(detail.gspfje) }}</div>
          </div>
        </div>

        <!-- ============ 分组明细 ============ -->
        <a-collapse v-model="activeGroups" class="facility-detail__groups">
          <a-collapse-panel v-for="group in groups" :key="group.key">
            <template slot="header">
              <span class="group-title">{{ group.title }}</span>
            </template>
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item v-for="field in group.fields" :key="field.key" :label="field.label">
                <span :class="{ 'is-empty': isEmpty(detail[field.key]) }">
                  {{ isEmpty(detail[field.key]) ? '—' : detail[field.key] }}
                </span>
              </a-descriptions-item>
            </a-descriptions>
          </a-collapse-panel>
        </a-collapse>

        <!-- ============ 附件（按材料类型分组的目录树） ============ -->
        <div class="facility-detail__files">
          <div class="facility-detail__files-head">
            <span class="group-title">配套附件</span>
            <span class="facility-detail__files-meta">
              共 {{ attachmentTree.totalFiles || 0 }} 个 / {{ formatSize(attachmentTree.totalSize) }}
              · 按材料类型分组；上传/删除请到「配套附件管理」
            </span>
          </div>
          <!--
            ★ 详情页用目录树而不是平铺表格：这里回答的是「材料按类型齐不齐」，
              分组后一眼能看出缺哪类；平铺表格给不出这个结构。
            ★ editable=false：详情页保持只读，写操作统一在附件管理页，
              这样留痕与权限口径只有一处。
          -->
          <attachment-tree-list
            :tree="attachmentTree"
            :loading="attachmentLoading"
            :editable="false"
            empty-text="该配套项目还没有附件"
            @preview="handlePreview"
            @download="handleDownload" />
        </div>
      </template>
      <a-empty v-else-if="!loading" description="未取到配套项目详情" />
    </a-spin>

    <div class="facility-detail__footer">
      <a-button icon="partition" @click="handleOpenProcess">查看环节进度</a-button>
      <a-button @click="handleClose">关闭</a-button>
    </div>
  </a-modal>
</template>

<script>
  import { queryFacilityDetail } from '@/api/land/facilityAdmin'
  import { queryAttachmentTree, downloadAttachment, formatSize } from '@/api/land/attachment'
  import AttachmentTreeList from './AttachmentTreeList.vue'

  /**
   * 配套项目详情弹窗
   *
   * ★ 为什么详情页也要有一份分组结构，而不是复用表单弹窗：
   *   表单弹窗是**输入态**（有控件、有校验、53 个可编辑框），
   *   详情是**阅读态**。用只读描述列表能一屏看到更多字段（两列排布）、
   *   也不会让人误以为可以就地编辑。
   *
   * ★ 「查看环节进度」为什么由详情弹窗 emit 给父页面而不是自己弹一个：
   *   环节进度的编辑要联动父页面的第三步区块（保存后要刷新整棵树、
   *   要显示日历时效性提示）。弹窗各自持有一份状态会两边不同步 ——
   *   典型症状是「在详情里改完，回到主页面进度条还是旧的」。
   */
  export default {
    name: 'FacilityDetailModal',
    components: { AttachmentTreeList },
    data () {
      return {
        visible: false,
        loading: false,
        detail: null,
        /** 附件树（按材料类型分组）：{ groups:[...], totalFiles, totalSize, typeCount } */
        attachmentTree: { groups: [] },
        attachmentLoading: false,
        activeGroups: ['base', 'units'],
        groups: [
          {
            key: 'base',
            title: '标识与属性',
            fields: [
              { key: 'crzdbh', label: '出让宗地编号' },
              { key: 'ptxmmc', label: '配套项目名称' },
              { key: 'dkmc', label: '地块名称' },
              { key: 'ptsslb', label: '配套设施类别' },
              { key: 'xzqh', label: '行政区划' },
              { key: 'xmfl', label: '项目分类' },
              { key: 'jsxx', label: '建设性质' },
              { key: 'dldj', label: '道路等级' },
              { key: 'zjly', label: '资金来源' },
              { key: 'dkcrscndptjgsj', label: '承诺配套竣工时间' },
              { key: 'sfzsjtjlz', label: '是否涉及提级论证' },
              { key: 'tjlzsftg', label: '提级论证是否通过' }
            ]
          },
          {
            key: 'units',
            title: '参建单位',
            fields: [
              { key: 'jsdw', label: '建设单位' },
              { key: 'sjdw', label: '设计单位' },
              { key: 'kcdw', label: '勘察单位' },
              { key: 'jldw', label: '监理单位' },
              { key: 'sgdw', label: '施工单位' },
              { key: 'jsgydw', label: '接收管养单位' }
            ]
          },
          {
            key: 'progress',
            title: '前期审批与进度',
            fields: [
              { key: 'xjpfsfwc', label: '项建批复是否完成' },
              { key: 'xjpfzt', label: '项建批复状态' },
              { key: 'kypfsfwc', label: '可研批复是否完成' },
              { key: 'kypfzt', label: '可研批复状态' },
              { key: 'csjgspfsfwc', label: '初设及概算批复是否完成' },
              { key: 'csjgspfzt', label: '初设及概算批复状态' },
              { key: 'zjlsqk', label: '资金落实情况' },
              { key: 'sfkg', label: '是否开工' },
              { key: 'kgzt', label: '开工状态' },
              { key: 'yjkgsj', label: '预计开工时间' },
              { key: 'sjkgsj', label: '实际开工时间' },
              { key: 'sfjg', label: '是否竣工' },
              { key: 'yjjgsj', label: '预计竣工时间' },
              { key: 'sjjgsj', label: '实际竣工时间' },
              { key: 'sfyj', label: '是否移交' },
              { key: 'ptxmhdydydjdc', label: '核定用地与地籍调查' }
            ]
          },
          {
            key: 'desc',
            title: '说明与录入信息',
            fields: [
              { key: 'jtwt', label: '具体问题' },
              { key: 'gzjy', label: '工作建议' },
              { key: 'zlqsnrjsm', label: '资料缺失内容及说明' },
              { key: 'bz', label: '备注' },
              { key: 'lrdw', label: '录入单位' },
              { key: 'lrr', label: '录入人' },
              { key: 'lxdh', label: '联系电话' },
              { key: 'createAccount', label: '创建账号' },
              { key: 'createTime', label: '创建时间' }
            ]
          }
        ]
      }
    },
    methods: {
      formatSize: formatSize,

      open (id) {
        if (!id) {
          this.$message.warning('缺少配套项目ID')
          return
        }
        this.visible = true
        this.detail = null
        this.attachmentTree = { groups: [] }
        this.loading = true
        queryFacilityDetail(id).then(res => {
          if (!res.success || !res.result) {
            this.$message.error(res.message || '配套项目详情加载失败')
            return
          }
          this.detail = res.result
        }).catch(() => {
          this.$message.error('配套项目详情加载失败')
        }).finally(() => {
          this.loading = false
        })
        // 附件不阻塞主信息展示，并行拉取
        this.loadAttachments(id)
      },

      /** 附件树（按材料类型分组）：走 /attachment/tree，与附件管理页同一口径 */
      loadAttachments (bizId) {
        this.attachmentLoading = true
        queryAttachmentTree('facility', bizId).then(res => {
          this.attachmentTree = (res && res.success && res.result) ? res.result : { groups: [] }
        }).catch(() => {
          this.attachmentTree = { groups: [] }
        }).finally(() => {
          this.attachmentLoading = false
        })
      },

      handlePreview (file) {
        // 详情页不内嵌预览弹窗：复用下载地址，浏览器能渲染的当场显示
        this.handleDownload(file)
      },

      handleDownload (file) {
        if (!file || !file.id) {
          this.$message.warning('该附件还没有落库，无法下载')
          return
        }
        downloadAttachment(file.id, file.fileName)
      },
      handleClose () {
        this.visible = false
      },
      handleOpenProcess () {
        if (!this.detail) {
          return
        }
        this.$emit('open-process', this.detail.id)
        this.visible = false
      },
      isEmpty (value) {
        return value === null || value === undefined || value === ''
      },
      valueOr (value) {
        return this.isEmpty(value) ? '—' : value
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .facility-detail {
    &__head {
      padding: 10px 12px;
      margin-bottom: 12px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    &__title {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    &__name {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
    }

    &__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      margin-top: 6px;
      font-size: 12px;
      color: @text-muted;

      b {
        color: #0f172a;
      }
    }

    &__stats {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 12px;
    }

    &__groups {
      /deep/ .ant-collapse-header {
        padding: 8px 12px !important;
      }

      /deep/ .ant-collapse-content-box {
        padding: 12px !important;
      }

      /deep/ .ant-descriptions-item-label {
        width: 140px;
        font-size: 12px;
        color: @text-weak;
        background: #fafafa;
      }

      /deep/ .ant-descriptions-item-content {
        font-size: 13px;
      }
    }

    &__footer {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding-top: 12px;
      margin-top: 12px;
      border-top: 1px solid @border-color;
    }

    .stat-item {
      flex: 1 1 140px;
      padding: 8px 12px;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 6px;

      &__label {
        font-size: 12px;
        color: @text-weak;
      }

      &__value {
        margin-top: 2px;
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 18px;
        color: #0f172a;
      }
    }

    .group-title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    /* 附件区：与上面的字段分组用一条分隔线隔开，视觉上分成「字段」与「材料」两块 */
    &__files {
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid @border-color;
    }

    &__files-head {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: baseline;
      margin-bottom: 8px;
    }

    &__files-meta {
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    .is-empty {
      color: @text-weak;
    }
  }
</style>
