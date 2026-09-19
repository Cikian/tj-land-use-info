<template>
  <a-select
    :value="value"
    show-search
    allowClear
    :disabled="disabled"
    :placeholder="placeholder"
    :filterOption="false"
    :notFoundContent="loading ? '加载中…' : '无匹配用户'"
    @search="handleSearch"
    @focus="handleFocus"
    @change="handleChange">
    <a-select-option v-for="user in options" :key="user.username" :value="user.username">
      <div class="user-select__option">
        <span class="user-select__name">{{ user.realname || user.username }}</span>
        <span class="user-select__account">{{ user.username }}</span>
      </div>
    </a-select-option>
  </a-select>
</template>

<script>
  import { getAction } from '@/api/manage'

  /**
   * 用户选择器（按姓名 / 账号远程搜索）
   *
   * 用于收文的「承办人」与流转的「转办给谁」。
   * jeecg 的 /sys/user/list 没有加 @RequiresPermissions（类级注解被注释掉了），
   * 登录用户即可查询，因此不需要额外的业务权限码。
   *
   * v-model 绑定的是 <b>username</b>（不是 id）——因为流转记录、当前处理人字段
   * 都按账号存储，与旧 tj-sfw 的 current_flow 语义一致。
   */
  export default {
    name: 'UserSelect',
    props: {
      value: {
        type: [String, Array],
        default: undefined
      },
      placeholder: {
        type: String,
        default: '按姓名 / 账号搜索'
      },
      disabled: {
        type: Boolean,
        default: false
      },
      /** 是否排除某个账号（例如流转时排除自己） */
      excludeUsername: {
        type: String,
        default: ''
      }
    },
    data () {
      return {
        loading: false,
        options: [],
        loadedOnce: false
      }
    },
    methods: {
      handleFocus () {
        if (!this.loadedOnce) {
          this.load('')
        }
      },
      handleSearch (keyword) {
        this.load(keyword)
      },
      load (keyword) {
        this.loading = true
        return getAction('/sys/user/list', {
          pageNo: 1,
          pageSize: 50,
          username: keyword || undefined
        }).then(res => {
          if (res.success) {
            const page = res.result || {}
            const records = page.records || []
            this.options = records
              .filter(user => user.username !== this.excludeUsername)
              .map(user => ({ username: user.username, realname: user.realname }))
            this.loadedOnce = true
          }
        }).finally(() => {
          this.loading = false
        })
      },
      handleChange (value) {
        this.$emit('input', value)
        this.$emit('change', value)
      }
    }
  }
</script>

<style lang="less" scoped>
  .user-select {
    &__option {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    &__name {
      color: #0f172a;
    }

    &__account {
      font-size: 12px;
      color: #94a3b8;
    }
  }
</style>
