import Vue from 'vue'

const preventReClick = Vue.directive('preventReClick', {
  inserted: function(el, binding) {
    el.addEventListener('click', (e) => {
      if (!el.disabled) {
        el.style.pointerEvents = 'none'
        setTimeout(() => {
          el.style.pointerEvents = 'auto'
        }, binding.value || 2000)
      } else {
        // disabled为true时，阻止默认的@click事件
        e.preventDefault()
        e.stopPropagation()
      }
    }, true)
  }
});

export { preventReClick }