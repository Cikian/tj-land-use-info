<template>
  <!--
    UserSelect 人员选择器（按姓名 / 账号远程搜索）
    --------------------------------
    用于收文的「承办人」和流转的「转办给谁」。

    v-model 绑定的是 **username（账号）** 而不是用户 id —— 后端流转记录
    （t_doc_receive_flow.handler）与主表的 current_handler 都按账号存储，
    与旧 tj-sfw 的 current_flow 语义保持一致。传 id 会导致流转记录对不上人。

    搜索走 Java 后端的 /sys/user/list（jeecg 自带接口），因此用 manageJava 的
    请求方法；该接口未做按钮级权限限制，登录用户即可查询。

    用法：
      <user-select v-model="form.toUsername" :exclude-username="currentUsername" />
      <user-select v-model="form.currentHandler" placeholder="选择承办人" clearable />
  -->
  <screen-select
    :value="value"
    :options="options"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :searchable="true"
    :filter-local="false"
    :empty-text="loading ? '加载中…' : '无匹配用户'"
    :aria-label="ariaLabel || placeholder"
    @input="$emit('input', $event)"
    @change="$emit('change', $event)"
    @open="handleOpen"
    @search="handleSearch"
  />
</template>

<script>
import { ScreenSelect } from '@/components/screen'
import { javaGetAction } from '@/api/manageJava'
import { toast } from '@/components/screen/toast'

/** 一次最多取多少人（后端分页） */
const PAGE_SIZE = 50

export default {
  name: 'DocUserSelect',
  components: { ScreenSelect },
  props: {
    /** 选中的账号（username） */
    value: { type: String, default: '' },
    placeholder: { type: String, default: '按姓名 / 账号搜索' },
    disabled: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    /** 需要排除的账号（流转时排除自己，避免把待办转给自己） */
    excludeUsername: { type: String, default: '' },
    ariaLabel: { type: String, default: '' },
  },
  data () {
    return {
      loading: false,
      options: [],
      loadedOnce: false,
    }
  },
  methods: {
    handleOpen () {
      if (!this.loadedOnce) this.load('')
    },
    handleSearch (keyword) {
      this.load(keyword)
    },
    load (keyword) {
      this.loading = true
      return javaGetAction('/sys/user/list', {
        pageNo: 1,
        pageSize: PAGE_SIZE,
        username: keyword || undefined,
      })
        .then((res) => {
          if (!res || !res.success) {
            // 人员列表拉取失败不弹错：这只是个辅助选择器，
            // 用户仍可手动输入账号（见下方 options 的兜底）
            return
          }
          const page = res.result || {}
          const records = page.records || []
          this.options = records
            .filter((user) => user.username !== this.excludeUsername)
            .map((user) => ({
              value: user.username,
              // 姓名 + 账号同时给出：重名时账号是唯一可区分的信息
              label: user.realname ? `${user.realname}（${user.username}）` : user.username,
            }))
          this.loadedOnce = true
          this.ensureCurrentInOptions()
        })
        .catch(() => {
          // 静默降级
        })
        .finally(() => {
          this.loading = false
        })
    },
    /**
     * 回显兜底：当前值不在候选里时补一条，否则下拉会显示空白，
     * 用户会以为承办人丢了（编辑已有收文时很常见）。
     */
    ensureCurrentInOptions () {
      if (!this.value) return
      if (this.options.some((option) => option.value === this.value)) return
      this.options = [{ value: this.value, label: this.value }, ...this.options]
    },
    /** 找不到用户时给出明确提示（搜索接口异常的场景） */
    notifyEmpty () {
      toast.warning('没有匹配的用户')
    },
  },
}
</script>
