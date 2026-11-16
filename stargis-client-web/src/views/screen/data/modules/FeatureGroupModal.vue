<template>
  <!--
    FeatureGroupModal 切片要素图形管理弹窗（经营性用地 / 配套项目共用）
    ==================================================================
    用户交互定稿（2026-11-15）：点「编辑」后在这里管理该记录名下的**全部小块**：
      · 每一块可以 定位（相机飞过去）/ 移除（弹窗内暂存，可反悔）；
      · 可以继续「添加小块」进绘制模式（复用与表单同款的全屏画笔）；
      · 关闭时把整组交回表单 —— ★ 本弹窗**没有独立保存按钮**，落库统一走
        表单的「保存」（属性与几何在一次保存里先后落定，新增记录也是同一条路）。

    ★ 为什么做成「纯暂存编辑器」：
      几何以唯一值（编号/名称）关联切片源表，没有行主键；如果每删一块立即落库，
      后端就得做行级删除、源表就得补主键。而「弹窗内暂存、保存时整组替换」
      让后端只需要一个 DELETE+INSERT 的整组接口，误删也随「取消」一并还原 ——
      这是把险路绕开的交互契约（与 ShpImportServiceImpl.saveSliceFeatureGroup
      的注释互为镜像）。

    ★ 预览面（previewPolygon）所有权：
      弹窗打开即接管（把整组画成常驻 MultiPolygon），编辑过程中实时刷新；
      「完成」后预览**不清**（表单还开着，图形要继续留在地图上陪填），
      由表单的提交/取消统一清；「取消 / X / Esc」则把预览恢复为打开时的组。
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="760"
    ok-text="完成"
    cancel-text="取消"
    :body-max-height="'calc(60vh - 160px)'"
    draggable
    :dimmed="false"
    @ok="handleFinish"
    @cancel="handleCancel"
  >
    <div class="feature-group">
      <p class="feature-group__hint">
        {{ tableLabel }}以「{{ uniqueFieldLabel }}」关联切片源表；这里的新增 / 移除先暂存，
        点「完成」回到表单、再点表单的「保存」后统一落库生效，取消弹窗即还原。
      </p>

      <ul class="feature-group__list" role="list">
        <li v-for="(part, index) in draft" :key="index" class="feature-group__item">
          <span class="feature-group__index">{{ index + 1 }}</span>
          <span class="feature-group__meta">
            第 {{ index + 1 }} 块 · {{ part.vertices }} 个顶点
            <em :class="'feature-group__tag is-' + (part.legacy ? 'legacy' : (part.saved ? 'saved' : 'draft'))">
              {{ part.legacy ? '旧字段数据' : (part.saved ? '切片库已存' : '本次新画') }}
            </em>
          </span>
          <span class="feature-group__actions">
            <button
              type="button"
              class="feature-group__btn"
              aria-label="定位到该块图形"
              @click="locatePart(index)"
            >定位</button>
            <button
              type="button"
              class="feature-group__btn is-danger"
              aria-label="移除该块图形"
              @click="removePart(index)"
            >移除</button>
          </span>
        </li>
      </ul>

      <p v-if="!draft.length" class="feature-group__empty">
        该{{ uniqueFieldLabel === '宗地编号' ? '宗地' : '配套项目' }}名下还没有任何图形；
        点下方「添加小块」在地图上绘制（左键加点，双击或右键结束）。
      </p>

      <div class="feature-group__footer">
        <button
          type="button"
          class="feature-group__add"
          :disabled="!mapEnabled || picking"
          :aria-label="mapEnabled ? '添加小块，在地图上绘制多边形' : '地图未加载，无法添加图形'"
          @click="handleAddPart"
        >
          <screen-icon name="plus" :size="14" />
          <span>添加小块</span>
        </button>
        <span class="feature-group__tip">
          {{ mapEnabled ? (picking ? '正在绘制：左键加点，双击或右键结束' : `当前共 ${draft.length} 块`) : '地图未加载（?map=0 预览模式），暂不能添加图形' }}
        </span>
      </div>
    </div>
  </screen-modal>
</template>

<script>
import { ScreenModal, ScreenIcon } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { uid } from '@/components/screen/utils'
// 绘制会话与表单同款（令牌机制保证同一时刻只有一个全屏画笔；见 pickSession.js）
import { startPick, endPick, isPickIssuer } from '@/views/screen/pickSession'
// 整组常驻预览面：弹窗打开即接管，编辑实时刷新（生命周期见文件头注释）
import { show as showPreviewPolygon, clear as clearPreviewPolygon } from '@/views/screen/previewPolygon'
import { countVertices, polygonBounds } from '@/views/screen/geoPolygons'

/**
 * 把整组图形合并成一个 MultiPolygon 常驻预览面。
 * ★ 保环结构：Polygon 整个 coordinates、MultiPolygon 逐面展开 —— 洞不丢。
 *   坏块跳过（一条脏图形不能把整组预览搞挂，与 geoPolygons.js 容错口径一致）。
 * @param {Array<{geojson: string}>} parts
 * @returns {string} MultiPolygon 字符串；一组有效图形都没有时返回空串
 */
function mergePartsGeojson (parts) {
  const coords = []
  ;(parts || []).forEach((part) => {
    try {
      const g = JSON.parse(part.geojson)
      if (g && g.type === 'Polygon' && Array.isArray(g.coordinates)) {
        coords.push(g.coordinates)
      } else if (g && g.type === 'MultiPolygon' && Array.isArray(g.coordinates)) {
        coords.push.apply(coords, g.coordinates)
      }
    } catch (e) { /* 坏块跳过 */ }
  })
  return coords.length ? JSON.stringify({ type: 'MultiPolygon', coordinates: coords }) : ''
}

export default {
  name: 'FeatureGroupModal',
  components: { ScreenModal, ScreenIcon },
  data () {
    return {
      visible: false,
      title: '管理地块图形',
      /** 发起方：'land' 经营性用地 / 'facility' 配套项目（决定绘制会话的事件名与文案） */
      sourceType: 'land',
      /** 唯一值（宗地编号 / 项目名称），提示文案里展示 */
      uniqueValue: '',
      /** 弹窗内的暂存组：[{ geojson, saved, legacy, vertices }]；「完成」才交回表单 */
      draft: [],
      /** 打开时的组快照：「取消 / X / Esc」据此把预览恢复回去（表单的组未被改动） */
      original: [],
      /** 地图是否可用（?map=0 预览时为 false，此时「添加小块」不给点） */
      mapEnabled: true,
      /** 本次绘制会话令牌（没进过绘制模式时为空串）；结束时要 verbatim 校验 */
      pickToken: '',
      /** 实例唯一 id（排查「谁开的绘制会话」用） */
      uid: uid('feature-group'),
      /** 完成关闭标记：watch(visible) 收尾时据此区分「完成」与「取消」两条路 */
      finished: false
    }
  },
  computed: {
    /** 唯一值字段的展示名（提示文案用） */
    uniqueFieldLabel () {
      return this.sourceType === 'facility' ? '项目名称' : '宗地编号'
    },
    tableLabel () {
      return this.sourceType === 'facility' ? '配套设施图形' : '宗地地块图形'
    },
    /** 绘制会话是否进行中（「添加小块」按钮态） */
    picking () {
      return !!this.pickToken && isPickIssuer(this.pickToken)
    }
  },
  watch: {
    /**
     * 弹窗任何途径关掉都要收尾：右上角 X / Esc 走 ScreenModal 的 update:visible，
     * **不会**经过 handleCancel，所以收尾必须挂这里兜底。
     * 「完成」路（finished=true）保持新组预览；「取消」路把预览恢复为打开时的组。
     */
    visible (v) {
      if (v) return
      this.closePick()
      if (this.finished) {
        this.finished = false
      } else {
        this.restoreOriginalPreview()
      }
    }
  },
  mounted () {
    // 绘制结束事件（覆盖层与本弹窗是 body 上的两棵平行节点，走 $bus 既有通道）。
    // ★ pickFinishedEvent 是方法，必须调用拿到事件名字符串（this.pickFinishedEvent
    //   只是函数引用，$on 会把它 toString 当事件名 —— 事件永远收不到）
    this.$bus.$on(this.pickFinishedEvent(), this.handlePickEvent)
  },
  beforeDestroy () {
    this.$bus.$off(this.pickFinishedEvent(), this.handlePickEvent)
    clearPreviewPolygon()
  },
  methods: {
    /** 与大屏壳层 finishPick 的事件名口径严格一致（land/facility 分事件名） */
    pickFinishedEvent () {
      return this.sourceType === 'facility' ? 'facilityMapPickFinished' : 'landMapPickFinished'
    },

    /**
     * 打开弹窗（表单持有组状态，本弹窗只是编辑器）。
     * @param {{sourceType?: string, parts?: Array<{geojson: string, saved?: boolean, legacy?: boolean}>, uniqueValue?: string, mapEnabled?: boolean}} options
     */
    open (options) {
      const opts = options || {}
      this.sourceType = opts.sourceType === 'facility' ? 'facility' : 'land'
      this.uniqueValue = String(opts.uniqueValue || '')
      this.mapEnabled = opts.mapEnabled !== false
      this.title = `管理${this.tableLabel}${this.uniqueValue ? `（${this.uniqueValue}）` : ''}`
      // 组快照 + 暂存组：顶点数在这里算好（geoPolygons 容错口径，脏数据按 0）
      const source = Array.isArray(opts.parts) ? opts.parts : []
      this.original = source.map((part) => ({
        geojson: part.geojson,
        saved: !!part.saved,
        legacy: !!part.legacy
      }))
      this.draft = this.original.map((part) => Object.assign({}, part, { vertices: countVertices(part.geojson) }))
      this.finished = false
      this.pickToken = ''
      this.visible = true
      // 预览接管：整组画成常驻 MultiPolygon（表单此前画的单面预览被整体替换）
      this.showDraftPreview()
    },

    /** 把当前暂存组画成常驻预览面（show 先清旧的，幂等） */
    showDraftPreview () {
      const merged = mergePartsGeojson(this.draft)
      if (merged) {
        showPreviewPolygon(merged)
      } else {
        clearPreviewPolygon()
      }
    },

    /** 取消路：把预览恢复为打开时的组（表单的组没有变，地图应该也回到原样） */
    restoreOriginalPreview () {
      const merged = mergePartsGeojson(this.original)
      if (merged) {
        showPreviewPolygon(merged)
      } else {
        clearPreviewPolygon()
      }
    },

    /**
     * 添加小块：进入绘制模式。令牌机制与表单同款 ——
     * 已有别人（或自己）的会话时不抢占，保证同一时刻只有一个全屏画笔。
     */
    handleAddPart () {
      if (!this.mapEnabled) {
        toast.warning('地图尚未加载，无法添加图形')
        return
      }
      if (this.pickToken && isPickIssuer(this.pickToken)) return
      // 上次令牌已不是当前会话（被 ended / 被表单接管）时先清掉，再开新会话
      if (this.pickToken && !isPickIssuer(this.pickToken)) this.pickToken = ''
      this.pickToken = startPick({
        source: this.sourceType,
        title: `正在添加${this.tableLabel}：左键加点，双击或右键结束，Esc 取消`,
        issuer: `FeatureGroupModal#${this.uid}`
      })
    },

    /**
     * 覆盖层抛回的结束事件（取消绘制 / 完成绘制都会走）。
     * token 校验是第二道保险：不是自己发起的会话一律忽略。
     */
    handlePickEvent (payload) {
      const data = payload || {}
      if (!this.pickToken || data.token !== this.pickToken) return
      const geometry = data.geometry
      if (geometry && geometry.geojson) {
        this.draft.push({
          geojson: geometry.geojson,
          saved: false,
          legacy: false,
          vertices: geometry.vertexCount || countVertices(geometry.geojson)
        })
        this.showDraftPreview()
        toast.success(`${this.tableLabel}已添加（${geometry.vertexCount} 个顶点）`)
      }
      // 取消绘制：什么都不加，暂存组保持原样（预览也是）
      this.pickToken = ''
    },

    /** 移除某一块（弹窗内暂存；「完成」交回表单、表单保存后才真正落库） */
    removePart (index) {
      if (index < 0 || index >= this.draft.length) return
      this.draft.splice(index, 1)
      this.showDraftPreview()
      toast.success('已移除该块（尚未生效；随表单保存后落库，取消即还原）')
    },

    /** 定位到某一块：前端用该块几何直接算范围飞行（比整组合并范围更准，不走后端） */
    locatePart (index) {
      const part = this.draft[index]
      if (!part) return
      const Cesium = window.Cesium
      if (!Cesium || !window.viewer) {
        toast.warning('地图未加载，无法定位')
        return
      }
      const bounds = polygonBounds(part.geojson)
      if (!bounds) {
        toast.warning('该块图形坐标异常，无法定位')
        return
      }
      window.viewer.camera.flyTo({
        destination: Cesium.Rectangle.fromDegrees(bounds[0], bounds[1], bounds[2], bounds[3]),
        duration: 1.2
      })
    },

    /** 「完成」：把整组交回表单（落库由表单保存统一做），预览保持新组陪填 */
    handleFinish () {
      this.finished = true
      this.closePick()
      this.$emit('change', this.draft.map((part) => ({
        geojson: part.geojson,
        saved: part.saved,
        legacy: part.legacy
      })))
      this.visible = false
    },

    /** 「取消」：改动丢弃（watch 收尾负责恢复预览），表单组未受影响 */
    handleCancel () {
      this.closePick()
      this.visible = false
    },

    /** 收掉可能残留的绘制会话（幂等；令牌不对时 endPick 自己会拒绝） */
    closePick () {
      const token = this.pickToken
      this.pickToken = ''
      if (!token) return
      endPick(token)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.feature-group {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__hint {
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
    min-height: 44px;
    padding: 6px 12px;
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__index {
    flex: 0 0 auto;
    min-width: 26px;
    height: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-accent);
    background: rgba(130, 198, 255, 0.12);
    border: 1px solid var(--screen-border-strong);
    border-radius: var(--screen-radius-pill);
  }

  &__meta {
    flex: 1 1 auto;
    min-width: 0;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__tag {
    flex: 0 0 auto;
    padding: 1px 8px;
    font-size: var(--screen-font-xs);
    font-style: normal;
    line-height: 1.6;
    border-radius: var(--screen-radius-sm);

    &.is-saved {
      color: var(--screen-accent);
      background: rgba(130, 198, 255, 0.1);
      border: 1px solid var(--screen-border-soft);
    }

    &.is-draft {
      color: #ffd591;
      background: rgba(255, 213, 145, 0.1);
      border: 1px solid rgba(255, 213, 145, 0.35);
    }

    &.is-legacy {
      color: var(--screen-text-mute);
      background: rgba(6, 20, 40, 0.4);
      border: 1px solid var(--screen-border-soft);
    }
  }

  &__actions {
    flex: 0 0 auto;
    display: inline-flex;
    gap: 6px;
  }

  &__btn {
    padding: 3px 12px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text);
    background: transparent;
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent);
      border-color: var(--screen-accent);
    }

    &.is-danger:hover {
      color: var(--screen-danger);
      border-color: var(--screen-danger);
    }
  }

  &__empty {
    margin: 0;
    padding: 16px 12px;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-mute);
    text-align: center;
    background: rgba(6, 20, 40, 0.35);
    border: 1px dashed var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: var(--screen-space-3);
  }

  &__add {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
    min-height: var(--screen-control-md);
    padding: 0 18px;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
    color: var(--screen-accent);
    background: rgba(130, 198, 255, 0.12);
    border: 1px solid var(--screen-border-strong);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(:disabled) {
      color: var(--screen-text-on-accent);
      background: var(--screen-accent);
      border-color: var(--screen-accent-bright);
    }

    &:disabled {
      color: var(--screen-text-mute);
      background: rgba(6, 20, 40, 0.38);
      border-color: var(--screen-border-soft);
      cursor: not-allowed;
    }
  }

  &__tip {
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
  }
}
</style>
