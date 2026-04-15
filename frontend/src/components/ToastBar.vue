<template>
  <transition name="toast-fade">
    <div v-if="message" class="toast-bar">{{ message }}</div>
  </transition>
</template>

<script>
export default {
  name: 'ToastBar',
  props: {
    message: { type: String, default: '' }
  }
}
</script>
