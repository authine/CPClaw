<template>
  <div class="attachment-uploader">
    <input ref="fileInput" hidden type="file" multiple accept="image/*,.pdf,.xls,.xlsx,.csv" @change="uploadSelectedFiles" />
    <el-tooltip content="上传附件" placement="top" :show-after="300">
      <el-button class="attachment-button" circle text aria-label="上传附件" :loading="uploading" @click="selectFiles">
        <el-icon v-if="!uploading"><Paperclip /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { uploadAttachment } from '../../services/attachmentApi'
import type { AttachmentResponse } from '../../types/agent'

const emit = defineEmits<{
  uploaded: [attachment: AttachmentResponse]
}>()

const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)

function selectFiles() {
  if (!uploading.value) {
    fileInput.value?.click()
  }
}

async function uploadSelectedFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length) {
    return
  }

  uploading.value = true
  try {
    for (const file of files) {
      emit('uploaded', await uploadAttachment(file))
    }
    ElMessage.success('附件已上传，将随下一条消息发送')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<style scoped>
.attachment-uploader {
  display: inline-flex;
}

.attachment-button {
  width: 32px;
  height: 32px;
  color: var(--chat-text-secondary, #657084);
}

.attachment-button:hover {
  background: var(--chat-surface-soft, #f2f4f7);
  color: var(--chat-text, #171c2b);
}
</style>
