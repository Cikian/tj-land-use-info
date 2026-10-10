<template>
  <a-modal
    :title="null"
    :width="1000"
    :visible="visible"
    :footer="null"
    wrap-class-name="land-detail-modal"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <div v-if="detail" class="land-detail">
        <!-- ============ 页头 ============ -->
        <header class="land-detail__head">
          <div class="land-detail__head-main">
            <div class="land-detail__no">{{ detail.crzdbh || '—' }}</div>
            <h3 class="land-detail__name">{{ detail.dkmc || '（未填地块名称）' }}</h3>
          </div>
          <div class="land-detail__head-bar">
            <div class="land-detail__tags">
              <a-tag v-if="detail.xmfl" :color="detail.xmfl === '市级项目' ? 'blue' : 'green'">
                {{ detail.xmfl }}
              </a-tag>
              <a-tag v-if="detail.xzqh">{{ detail.xzqh }}</a-tag>
              <a-tag v-if="detail.ghydxz">{{ detail.ghydxz }}</a-tag>
              <!-- 配套是否齐全是「这套宗地还要不要跟进」的第一眼判断，放到页头 -->
              <a-tag :color="detail.ptsfqq === '是' ? 'green' : undefined">
                配套：{{ detail.ptsfqq || '未填' }}
              </a-tag>
            </div>
            <div class="land-detail__head-actions">
              <a-button size="small" icon="history" @click="handleHistory">变更履历</a-button>
              <a-button size="small" icon="reload" :loading="loading" @click="load">刷新</a-button>
            </div>
          </div>
        </header>

        <!-- ============ 一、标识与分类 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">标识与分类</div>
          <!--
            ★ a-descriptions 用数字 column，且所有 item 都不写 :span。
              原因见《档案管理-实现说明》7.6：column 写成响应式对象时首次渲染会按 3 列切，
              而末项写 :span 对渲染毫无影响却会让记账溢出并抛出 antd 告警。
          -->
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="出让宗地编号">{{ detail.crzdbh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地块名称">{{ detail.dkmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="行政区划">{{ detail.xzqh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目分类">{{ detail.xmfl || '—' }}</a-descriptions-item>
            <a-descriptions-item label="规划用地性质">{{ detail.ghydxz || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 二、出让与面积 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">出让与面积</div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="出让金（亿元）">{{ numberText(detail.crj) }}</a-descriptions-item>
            <a-descriptions-item label="出让时间">{{ detail.crsj || '—' }}</a-descriptions-item>
            <a-descriptions-item label="可建设用地面积（平方米）">{{ numberText(detail.kjsydmj) }}</a-descriptions-item>
            <a-descriptions-item label="总用地面积（平方米）">{{ numberText(detail.zydmj) }}</a-descriptions-item>
            <a-descriptions-item label="建设面积（平方米）">{{ numberText(detail.jsmj) }}</a-descriptions-item>
            <a-descriptions-item label="纳入成本的配套费（万元）">{{ numberText(detail.nrcbdptf) }}</a-descriptions-item>
            <a-descriptions-item label="完成度">{{ numberText(detail.wcd) }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 三、受让人与交付 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">受让人与交付</div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="受让人">{{ detail.srr || '—' }}</a-descriptions-item>
            <a-descriptions-item label="合同约定交付时间">{{ detail.htydjfsj || '—' }}</a-descriptions-item>
            <a-descriptions-item label="楼盘名称">{{ detail.lpmc || '—' }}</a-descriptions-item>
            <a-descriptions-item label="楼盘交付时间">{{ detail.lpjfsj || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 四、四至 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">四至</div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="东至">{{ detail.dz || '—' }}</a-descriptions-item>
            <a-descriptions-item label="西至">{{ detail.xz || '—' }}</a-descriptions-item>
            <a-descriptions-item label="南至">{{ detail.nz || '—' }}</a-descriptions-item>
            <a-descriptions-item label="北至">{{ detail.bz || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 五、土地整理 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">土地整理</div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="土地整理单位">{{ detail.tdzldw || '—' }}</a-descriptions-item>
            <a-descriptions-item label="土地整理计划下达文件号">{{ detail.tdzljhxdwjh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="土地整理计划">{{ detail.tdzljh || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 六、配套与图形 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">
            配套与图形
            <span class="land-detail__block-hint">
              配套项目明细请到「档案管理 / 收发文管理」按宗地查看；
              这里的「图形数据」只存 shp 相对路径，文件本体在附件管理里
            </span>
          </div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="配套是否齐全">{{ detail.ptsfqq || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套情况函">{{ detail.ptqkh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套筹备函">{{ detail.ptcbh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="出让宗地图形数据（shp）">{{ detail.crzdtxsj || '—' }}</a-descriptions-item>
            <a-descriptions-item label="配套建设内容">{{ detail.ptjsnr || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 七、录入信息 ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">录入信息</div>
          <a-descriptions :column="2" bordered size="small" class="land-detail__desc">
            <a-descriptions-item label="录入单位">{{ detail.lrdw || '—' }}</a-descriptions-item>
            <a-descriptions-item label="录入单位简称">{{ detail.xzqh2 || '—' }}</a-descriptions-item>
            <a-descriptions-item label="录入人">{{ detail.lrr || '—' }}</a-descriptions-item>
            <a-descriptions-item label="联系电话">{{ detail.lxdh || '—' }}</a-descriptions-item>
            <a-descriptions-item label="资料缺失内容及说明">{{ detail.zlqsnrsm || '—' }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ detail.beizhu || '—' }}</a-descriptions-item>
            <a-descriptions-item label="创建信息">{{ joinInfo(detail.createBy, detail.createTime) }}</a-descriptions-item>
            <a-descriptions-item label="最后更新">{{ joinInfo(detail.updateBy, detail.updateTime) }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- ============ 八、附件（按材料类型分组的目录树） ============ -->
        <section class="land-detail__block">
          <div class="land-detail__block-title">
            宗地附件
            <span class="land-detail__block-sub">
              共 {{ attachmentTree.totalFiles || 0 }} 个 / {{ formatSize(attachmentTree.totalSize) }}
              · 按材料类型分组；上传/删除请到「配套附件管理」
            </span>
          </div>
          <!--
            ★ 与配套详情同一口径：按材料类型分组，一眼看出缺哪类材料。
            ★ editable=false：详情页只读，写操作统一在附件管理页。
          -->
          <attachment-tree-list
            :tree="attachmentTree"
            :loading="attachmentLoading"
            :editable="false"
            empty-text="该宗地还没有附件"
            @preview="handlePreview"
            @download="handleDownload" />
        </section>
      </div>

      <a-empty v-else-if="!loading" description="未取到宗地详情（可能已被移除）" />
    </a-spin>
  </a-modal>
</template>

<script>
  import { queryLandDetail } from '@/api/land/landAdmin'
  import { queryAttachmentTree, downloadAttachment, formatSize } from '@/api/land/attachment'
  import AttachmentTreeList from './AttachmentTreeList.vue'

  /**
   * 经营性用地 - 详情弹窗
   *
   * ★ 为什么分组与「新增/编辑」弹窗逐字一致（标识与分类 / 出让与面积 / 受让人与交付 /
   *   四至 / 土地整理 / 配套与图形 / 录入信息）：
   *   录入和查看是同一批字段的两个视角。分组不一致时，用户在详情里记住的位置
   *   （「出让金在上面第二组」）到编辑里就对不上了，改一个字段要多找一遍。
   *
   * ★ 为什么详情里的「变更履历」只 emit 事件、不自己打开履历弹窗：
   *   履历弹窗是页面级的（页面上从列表也能直接看履历），若嵌在详情弹窗内部，
   *   就变成「弹窗里再弹一层」，关闭顺序、遮罩层级都要额外照顾。
   *   这里把 id 抛给页面，由页面统一打开，层级始终只有两层。
   *
   * ★ 数值列为什么不用 `|| '—'` 而走 numberText()：
   *   出让金/面积/完成度 是 BigDecimal，合法值 0 用 `||` 会被当成空值显示成「—」，
   *   用户会以为数据没落库（0 与「没填」是两件不同的事）。
   */
  export default {
    name: 'LandDetailModal',
    components: { AttachmentTreeList },
    data () {
      return {
        visible: false,
        loading: false,
        landId: '',
        detail: null,
        /** 附件树（按材料类型分组）：{ groups:[...], totalFiles, totalSize, typeCount } */
        attachmentTree: { groups: [] },
        attachmentLoading: false
      }
    },
    methods: {
      formatSize: formatSize,

      open (id) {
        if (!id) {
          return
        }
        this.landId = id
        this.detail = null
        this.attachmentTree = { groups: [] }
        this.visible = true
        this.load()
        // 附件不阻塞主信息展示，并行拉取
        this.loadAttachments(id)
      },

      /** 附件树（按材料类型分组）：走 /attachment/tree，与附件管理页同一口径 */
      loadAttachments (bizId) {
        this.attachmentLoading = true
        queryAttachmentTree('land', bizId).then(res => {
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
      load () {
        if (!this.landId) {
          return
        }
        this.loading = true
        queryLandDetail(this.landId).then(res => {
          if (res.success && res.result) {
            this.detail = res.result
          } else {
            this.detail = null
            if (res.message) {
              // 后端在详情查不到时会说明「可能已被移除」——原样展示，不要盖成「加载失败」
              this.$message.warning(res.message)
            }
          }
        }).catch(e => {
          this.detail = null
          this.$message.error((e && e.message) || '宗地详情加载失败')
        }).finally(() => {
          this.loading = false
        })
      },
      handleHistory () {
        this.$emit('history', { id: this.landId, crzdbh: this.detail ? this.detail.crzdbh : '' })
      },
      /** 数值展示：null / undefined / 空串 → 「—」，0 照实显示 */
      numberText (value) {
        if (value === null || value === undefined || value === '') {
          return '—'
        }
        return value
      },
      joinInfo (by, time) {
        if (!by && !time) {
          return '—'
        }
        return `${by || '—'} · ${time || '—'}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .land-detail {
    &__head {
      padding-bottom: 10px;
      margin-bottom: 12px;
      border-bottom: 1px solid @border-color;
    }

    &__head-main {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: baseline;
    }

    &__no {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
    }

    &__name {
      margin: 0;
      font-size: 14px;
      font-weight: 400;
      color: #475569;
    }

    &__head-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin-top: 8px;
    }

    &__tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    &__head-actions {
      display: flex;
      gap: 8px;
    }

    &__block {
      margin-bottom: 14px;
    }

    &__block-title {
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    &__block-hint {
      margin-left: 8px;
      font-size: 12px;
      font-weight: 400;
      line-height: 18px;
      color: @text-weak;
    }

    /* 标题右侧的一句统计（附件数 / 大小） */
    &__block-sub {
      margin-left: 8px;
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    /* a-descriptions：固定表格布局，避免长文本（土地整理计划文件号）挤坏标签列 */
    /deep/ .land-detail__desc {
      table-layout: fixed;

      .ant-descriptions-item-label {
        width: 150px;
        color: #64748b;
        background: #f8fafc;
      }

      .ant-descriptions-item-content {
        word-break: break-all;
        white-space: normal;
      }
    }
  }
</style>
