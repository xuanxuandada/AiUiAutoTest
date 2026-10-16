<template>
  <div class="page-header">
    <div class="page-title">
      <div class="title-text">{{ title }}</div>
      <div class="title-badge">{{ badge }}</div>
    </div>
    <div class="page-header-extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'PageHeader',
  props: {
    title: {
      type: String,
      required: true
    },
    badge: {
      type: String,
      required: true
    }
  }
}
</script>
