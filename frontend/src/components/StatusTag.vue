<template>
  <el-tag :type="tagType" size="small">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: string }>()

const map: Record<string, { label: string; type: string }> = {
  CONFIRMED: { label: '已确认', type: 'success' },
  CANDIDATE: { label: '候选', type: 'info' },
  CONFLICT: { label: '来源冲突', type: 'danger' },
  INSUFFICIENT_EVIDENCE: { label: '证据不足', type: 'warning' },
  PROVEN: { label: '已证实', type: 'success' },
  REJECTED: { label: '不可证实', type: 'danger' },
  ACTIVE: { label: '活跃', type: 'success' },
  SUSPENDED: { label: '已暂停', type: 'warning' },
  ISSUED: { label: '已签发', type: 'success' },
  VALID: { label: '有效', type: 'success' },
  REVOKED: { label: '已撤销', type: 'danger' },
  RECEIVED: { label: '已接收', type: 'info' },
  PARSED: { label: '已解析', type: 'success' },
  PARSED_WITH_WARNINGS: { label: '解析有警告', type: 'warning' },
  REJECTED_INGEST: { label: '已拒绝', type: 'danger' },
  RESOLVED: { label: '解析证据', type: 'success' },
  INFERRED: { label: '推断', type: 'info' },
  SIGNATURE: { label: '签名', type: 'warning' }
}

const label = computed(() => map[props.value]?.label || props.value)
const tagType = computed(() => (map[props.value]?.type || 'info') as any)
</script>
