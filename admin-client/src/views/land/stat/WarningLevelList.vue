<template>
  <a-card :bordered="false" class="warning-level-page">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">预警等级维护</h2>
        <p class="page-head__desc">
          维护蓝 / 黄 / 橙 / 红四级预警的逾期工作日天数区间与着色别名。
          区间为闭区间（含两端）、从 1 天起、各级之间不得重叠，改这里直接决定分级结果：
          下一次重算（保存环节进度时自动触发，或在预警查询页手动「重新计算」）就按新区间落等级，
          已经生成的预警记录不会被回填，需要重算后才跟着变。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:stat:warningLevel:edit'" type="primary" icon="plus" @click="handleAdd">
          新增等级
        </a-button>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>
    </div>

    <!--
      区间体检：只提示、不拦截（真正拦截由保存接口做，见契约 §5.6 第 4 条）。
      为什么要在页面上先摆出来：匹配算法对「空洞」是向下就近、对「重叠」是取第一条命中的，
      两种情况下列表写的内容和用户预期的分级可能不一致，事后从预警结果反查代价很高。
    -->
    <div v-if="rangeIssues.length" class="range-tip">
      <a-icon type="exclamation-circle" class="range-tip__icon" />
      <div class="range-tip__text">
        <div v-for="(issue, index) in rangeIssues" :key="index">{{ issue }}</div>
      </div>
    </div>

    <!-- ============ 列表 ============ -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">
          共 <b>{{ dataSource.length }}</b> 个等级<span v-if="coverageText">，当前覆盖 {{ coverageText }}</span>
        </span>
        <span class="list-panel__hint">
          等级别名（blue / yellow / orange / red）是前端着色令牌，与等级名称一样不能重复
        </span>
      </div>

      <a-table
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '还没有配置预警等级，点击右上角「新增等级」开始' }">
        <template slot="levelCode" slot-scope="text">
          <a-tag :color="levelColor(text)" class="level-tag">{{ text || '—' }}</a-tag>
        </template>
        <template slot="daysRange" slot-scope="text, record">
          <span class="days-range">{{ daysRangeText(record) }}</span>
        </template>
        <template slot="action" slot-scope="text, record">
          <a v-has="'land:stat:warningLevel:edit'" @click="handleEdit(record)">编辑</a>
          <!-- 竖线与删除按钮放在同一个 v-has 容器里：没权限时一起摘掉，否则会剩一条孤零零的「|」 -->
          <span v-has="'land:stat:warningLevel:edit'">
            <a-divider type="vertical" />
            <a-popconfirm
              title="删除后该等级不再参与分级，确定删除吗？"
              okText="确定"
              cancelText="取消"
              @confirm="handleDelete(record)">
              <a class="danger-link">删除</a>
            </a-popconfirm>
          </span>
        </template>
      </a-table>
    </div>

    <warning-level-modal ref="modal" :defaultSortNo="nextSortNo" @ok="loadData" />
  </a-card>
</template>

<script>
  import { listWarningLevel, deleteWarningLevel } from '@/api/land/statWarning'
  import WarningLevelModal from './modules/WarningLevelModal'

  /**
   * level_code → antd 标签色。
   *
   * 这是**前端着色令牌**（契约 §2.1）：levelCode 是唯一决定颜色的字段，
   * 不要按「等级名称包含『红』」之类的字符串去猜色 —— 用户可以把名称改成任何文案。
   * 未登记的别名（允许自定义成第五级）回落默认灰标签，不报错。
   */
  const LEVEL_TAG_COLORS = {
    blue: 'blue',
    yellow: 'gold',
    orange: 'orange',
    red: 'red'
  }

  /**
   * 预警等级维护（方案 2.3.1（五）· 差异清单 D-603）
   *
   * 这张表就是**分级预警的阈值表**（逾期工作日天数 → 等级，契约 §3.6），
   * 页面只负责维护，不参与计算；算出来的结果落在大屏端「查询统计 → 预警信息查询」。
   * 等级是配置量（正常 4 条），所以列表不分页，一次拉全量。
   */
  export default {
    name: 'WarningLevelList',
    components: { WarningLevelModal },
    data () {
      return {
        loading: false,
        /** 删除请求进行中：防止连点把同一条删两次（第二次会因已软删而返回「不存在」的误导提示） */
        deleting: false,
        dataSource: [],
        columns: [
          { title: '等级名称', dataIndex: 'levelName', width: 170 },
          { title: '等级别名', dataIndex: 'levelCode', width: 160, scopedSlots: { customRender: 'levelCode' } },
          { title: '逾期天数区间', dataIndex: 'minDays', width: 200, scopedSlots: { customRender: 'daysRange' } },
          { title: '排序号', dataIndex: 'sortNo', width: 90, align: 'center' },
          { title: '备注', dataIndex: 'remark', ellipsis: true },
          { title: '操作', width: 160, align: 'center', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    computed: {
      /** 两端都填了天数、能参与区间体检的行 */
      validRows () {
        return (this.dataSource || []).filter(row => this.isFilled(row.minDays) && this.isFilled(row.maxDays))
      },
      /** 空洞 / 重叠的提示文案，按 minDays 升序逐对比较 */
      rangeIssues () {
        const rows = this.validRows.slice().sort((a, b) => Number(a.minDays) - Number(b.minDays))
        const issues = []
        rows.forEach((row, index) => {
          if (index === 0) {
            return
          }
          const prev = rows[index - 1]
          const prevMax = Number(prev.maxDays)
          const curMin = Number(row.minDays)
          if (curMin <= prevMax) {
            issues.push(
              `「${prev.levelName}」与「${row.levelName}」的区间重叠（${prevMax} 天 / ${curMin} 天）：保存接口会拒绝，` +
              '请先把其中一段改开'
            )
          } else if (curMin > prevMax + 1) {
            issues.push(
              `「${prev.levelName}」与「${row.levelName}」之间有 ${prevMax + 1}-${curMin - 1} 天的空档：` +
              `逾期落在这几天会向下就近取「${prev.levelName}」`
            )
          }
        })
        return issues
      },
      coverageText () {
        const rows = this.validRows
        if (!rows.length) {
          return ''
        }
        const mins = rows.map(row => Number(row.minDays))
        const maxs = rows.map(row => Number(row.maxDays))
        return `${Math.min.apply(null, mins)} - ${Math.max.apply(null, maxs)} 天`
      },
      /**
       * 新增时的建议排序号 = 现有最大排序号 + 1。
       * 不这样做的后果：留空按 0 存，新等级会排在最前面（蓝色预警之前），
       * 而与区间由小到大不一致的排序会让「取最后一条作为最高级」的兜底逻辑读起来像 bug。
       */
      nextSortNo () {
        const values = (this.dataSource || [])
          .map(row => Number(row.sortNo))
          .filter(value => !isNaN(value))
        return values.length ? Math.max.apply(null, values) + 1 : 1
      }
    },
    created () {
      this.loadData()
    },
    methods: {
      loadData () {
        this.loading = true
        return listWarningLevel().then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          // 后端已按 sortNo 升序返回，这里不再排序，避免「页面顺序」与「匹配顺序」两套口径
          this.dataSource = res.result || []
        }).catch(() => {
          // 具体错误提示由请求拦截器统一给出
        }).finally(() => {
          this.loading = false
        })
      },
      handleAdd () {
        this.$refs.modal.show(null)
      },
      handleEdit (record) {
        if (!record || !record.id) {
          return
        }
        this.$refs.modal.show(record)
      },
      handleDelete (record) {
        if (this.deleting || !record || !record.id) {
          return
        }
        this.deleting = true
        deleteWarningLevel(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.loadData()
          } else {
            // 例如「该等级下还有决策意见」这类业务拦截，原样提示后端原因
            this.$message.warning(res.message || '删除失败')
          }
        }).catch(() => {
          // 同上
        }).finally(() => {
          this.deleting = false
        })
      },
      /** level_code → 标签色，未登记的别名回落默认灰标签 */
      levelColor (levelCode) {
        return LEVEL_TAG_COLORS[levelCode] || undefined
      },
      /** 区间统一按「min - max 天」展示；缺任意一端都显示 —，避免出现「null - 30 天」 */
      daysRangeText (record) {
        if (!record || !this.isFilled(record.minDays) || !this.isFilled(record.maxDays)) {
          return '—'
        }
        return `${record.minDays} - ${record.maxDays} 天`
      },
      /** 0 是合法下界（契约允许 0 <= minDays），所以不能用真值判断 */
      isFilled (value) {
        return value !== null && value !== undefined && value !== ''
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-strong: #0f172a;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .warning-level-page {
    /deep/ .ant-card-body {
      padding: 20px 24px 24px;
    }
  }

  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
    border-bottom: 1px solid @border-color;

    &__title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      color: @text-strong;
    }

    &__desc {
      margin: 0;
      max-width: 880px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  .range-tip {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin: 16px 0 0;
    padding: 10px 14px;
    font-size: 13px;
    line-height: 20px;
    color: #ad6800;
    background: #fffbe6;
    border: 1px solid #ffe58f;
    border-radius: 8px;

    &__icon {
      margin-top: 3px;
      color: #faad14;
    }

    &__text {
      min-width: 0;
    }
  }

  .list-panel {
    margin-top: 16px;

    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
      font-size: 13px;
      color: @text-muted;

      b {
        color: @text-strong;
      }
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }
  }

  /* 让四色标签宽度一致，列里不会因为文案长短跳动 */
  .level-tag {
    min-width: 70px;
    margin: 0;
    text-align: center;
  }

  .days-range {
    font-variant-numeric: tabular-nums;
    color: @text-strong;
  }

  .danger-link {
    color: #f5222d;

    &:hover {
      color: #ff4d4f;
    }
  }
</style>
