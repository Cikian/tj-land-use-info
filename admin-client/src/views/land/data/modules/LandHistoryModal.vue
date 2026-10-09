<template>
  <a-modal
    title="变更履历"
    :width="920"
    :visible="visible"
    :footer="null"
    :mask-closable="false"
    @cancel="handleClose">
    <div class="land-history__head">
      <span class="land-history__key">{{ landKey || '—' }}</span>
      <span class="land-history__hint">
        只记「真正变化了的字段」；<b>点任意一行</b>展开字段级明细（字段 / 改前 / 改后），
        改后的值标红加粗
      </span>
      <a-button size="small" icon="reload" :loading="loading" @click="load">刷新</a-button>
    </div>

    <!-- 没有履历不是错误，但必须说清原因：老数据不补记，否则用户会以为功能坏了 -->
    <a-alert
      v-if="!loading && !records.length"
      type="info"
      show-icon
      message="这条宗地还没有变更记录（留痕上线之前录入的历史数据不会补记履历）" />

    <a-table
      row-key="id"
      size="small"
      :columns="columns"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      :expanded-row-keys="expandedKeys"
      :custom-row="customRow"
      @expand="handleExpand">
      <template slot="action" slot-scope="text, record">
        <a-tag :color="actionColor(record.action)">{{ actionText(record.action) }}</a-tag>
      </template>
      <template slot="summary" slot-scope="text">
        <span class="land-history__summary">{{ text || '—' }}</span>
      </template>
      <template slot="changeCount" slot-scope="text">
        <a-tag v-if="text" color="blue">{{ text }} 项</a-tag>
        <span v-else>—</span>
      </template>
      <template slot="operator" slot-scope="text, record">
        {{ record.operatorName || record.operator || '—' }}
      </template>
      <template slot="expandedRowRender" slot-scope="record">
        <div v-if="!record.details || !record.details.length" class="land-history__empty">
          本次操作没有字段级变化（移除 / 恢复这类动作只改状态，履历用于留痕）
        </div>
        <a-table
          v-else
          row-key="field"
          size="small"
          bordered
          :columns="detailColumns"
          :data-source="record.details"
          :pagination="false">
          <template slot="before" slot-scope="text">
            <span class="land-history__before">{{ text || '（空）' }}</span>
          </template>
          <template slot="after" slot-scope="text">
            <span class="land-history__after">{{ text || '（空）' }}</span>
          </template>
        </a-table>
      </template>
    </a-table>
  </a-modal>
</template>

<script>
  import { queryLandHistory } from '@/api/land/landAdmin'
  import { actionColor, actionText } from '@/api/land/dataRecycle'

  /**
   * 经营性用地 - 变更履历弹窗
   *
   * 数据源是 `queryLandHistory(id)`，返回的是**字段级**明细：
   * [{action, actionText, actionColor, summary, changeCount, details, operatorName, createTime}]，
   * 其中 details 每项为 {field, label, before, after}。
   *
   * ★ 为什么用「可展开的表格」而不是时间轴（a-timeline）：
   *   时间轴适合展示「发生了哪些事」，但这里每一条都还挂着一份表格化的字段清单。
   *   时间轴里再嵌表格会出现三层嵌套（弹窗 → 时间轴 → 表格），窄屏下横向挤成一团。
   *   表格 + 展开行只有两层，且行号、操作时间、操作人天然对齐，便于扫读。
   *
   * ★ 为什么行点击也能展开（而不是只靠最左边那个小三角）：
   *   三角图标只有 8px 左右，鼠标要瞄准；履历是「想细看某一条」的场景，
   *   整行可点能少一次瞄准。点三角自身时交给表格内置行为处理，避免一次点击切换两次。
   *
   * ★ 动作的配色/文案一律取 `@/api/land/dataRecycle` 的 `actionColor` / `actionText`：
   *   回收站、变更留痕、宗地履历、配套履历至少四处要展示同一个动作，
   *   各写一份必然漂移（典型症状是同一个动作在两个页面颜色不同，用户以为是两种状态）。
   */

  /** 点的是表格自带的展开三角吗（它自己已负责切换，避免重复切换） */
  function isExpandIconClick (event) {
    let node = event ? event.target : null
    // 不用 Element.closest：本工程的 browserslist 仍包含 IE11，它没有 closest
    while (node && node.classList) {
      if (node.classList.contains('ant-table-row-expand-icon')) {
        return true
      }
      node = node.parentNode
    }
    return false
  }

  export default {
    name: 'LandHistoryModal',
    data () {
      return {
        visible: false,
        loading: false,
        landId: '',
        landKey: '',
        records: [],
        expandedKeys: [],
        pagination: {
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: total => `共 ${total} 条变更`
        },
        columns: [
          { title: '操作时间', dataIndex: 'createTime', width: 165, customRender: text => text || '—' },
          { title: '动作', dataIndex: 'action', width: 120, scopedSlots: { customRender: 'action' } },
          { title: '变更摘要', dataIndex: 'summary', scopedSlots: { customRender: 'summary' } },
          { title: '变更字段', dataIndex: 'changeCount', width: 100, scopedSlots: { customRender: 'changeCount' } },
          { title: '操作人', dataIndex: 'operatorName', width: 120, scopedSlots: { customRender: 'operator' } }
        ],
        detailColumns: [
          { title: '字段', dataIndex: 'label', width: 200, customRender: (text, row) => text || row.field },
          { title: '改前', dataIndex: 'before', width: 220, scopedSlots: { customRender: 'before' } },
          { title: '改后', dataIndex: 'after', scopedSlots: { customRender: 'after' } }
        ]
      }
    },
    methods: {
      actionColor: actionColor,
      actionText: actionText,

      /**
       * 打开履历。
       *
       * @param {string} id    宗地主键
       * @param {string} landKey 出让宗地编号（只用于标题，让用户确认看的是哪条）
       */
      open (id, landKey) {
        if (!id) {
          return
        }
        this.landId = id
        this.landKey = landKey || ''
        this.records = []
        this.expandedKeys = []
        this.visible = true
        this.load()
      },
      handleClose () {
        this.visible = false
      },
      load () {
        if (!this.landId) {
          return
        }
        this.loading = true
        queryLandHistory(this.landId).then(res => {
          if (res.success && res.result) {
            this.records = res.result
          } else {
            this.records = []
            if (res.message) {
              this.$message.warning(res.message)
            }
          }
        }).catch(e => {
          this.records = []
          this.$message.error((e && e.message) || '变更履历加载失败')
        }).finally(() => {
          this.loading = false
        })
      },

      // ---------------- 展开行 ----------------

      customRow (record) {
        return {
          on: {
            click: event => this.handleRowClick(record, event)
          }
        }
      },
      handleRowClick (record, event) {
        if (isExpandIconClick(event)) {
          return
        }
        this.toggleExpand(record.id)
      },
      /** 三角图标点击（展开/收起由表格抛事件，这里只同步受控的 expandedRowKeys） */
      handleExpand (expanded, record) {
        if (expanded) {
          if (this.expandedKeys.indexOf(record.id) < 0) {
            this.expandedKeys = this.expandedKeys.concat([record.id])
          }
        } else {
          this.expandedKeys = this.expandedKeys.filter(key => key !== record.id)
        }
      },
      toggleExpand (id) {
        if (this.expandedKeys.indexOf(id) < 0) {
          this.expandedKeys = this.expandedKeys.concat([id])
        } else {
          this.expandedKeys = this.expandedKeys.filter(key => key !== id)
        }
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .land-history {
    &__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      padding-bottom: 10px;
      margin-bottom: 10px;
      border-bottom: 1px solid @border-color;
    }

    &__key {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__hint {
      flex: 1 1 auto;
      min-width: 240px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;

      b {
        color: #0f172a;
      }
    }

    &__summary {
      font-size: 12px;
      color: @text-muted;
    }

    &__empty {
      padding: 8px 0;
      font-size: 12px;
      color: @text-weak;
    }

    &__before {
      color: @text-weak;
      text-decoration: line-through;
    }

    &__after {
      font-weight: 600;
      color: #cf1322;
    }

    /* 整行可点展开，给一个手型提示；三角图标与操作链接保持默认光标 */
    /deep/ .ant-table-tbody > tr {
      cursor: pointer;
    }

    /deep/ .ant-table-row-expand-icon {
      cursor: pointer;
    }
  }
</style>
