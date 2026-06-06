<template>
  <div class="attachment-uploader">
    <el-upload v-model:file-list="fileList" action="#" :auto-upload="false" multiple>
      <el-button>选择附件</el-button>
      <template #tip>
        <div class="upload-tip">支持图片、PDF、Excel 等业务材料。选择后点击“上传附件”，MVP 阶段上传后进入待解析状态。</div>
      </template>
    </el-upload>
    <el-button type="primary" plain :disabled="uploading || pendingFiles.length === 0" :loading="uploading" @click="submitUploads">
      上传附件
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { UploadUserFile } from 'element-plus'
import { uploadAttachment } from '../../services/attachmentApi'
import type { AttachmentResponse } from '../../types/agent'

const emit = defineEmits<{
  uploaded: [attachment: AttachmentResponse]
}>()

const fileList = ref<UploadUserFile[]>([])
const uploading = ref(false)
const uploadedUids = ref(new Set<number>())

const pendingFiles = computed(() => fileList.value.filter((file) => file.raw && !uploadedUids.value.has(file.uid)))

async function submitUploads() {
  uploading.value = true
  try {
    for (const file of pendingFiles.value) {
      if (!file.raw) {
        continue
      }
      const attachment = await uploadAttachment(file.raw)
      uploadedUids.value.add(file.uid)
      emit('uploaded', attachment)
    }
    fileList.value = fileList.value.filter((file) => !uploadedUids.value.has(file.uid))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.attachment-uploader {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.upload-tip {
  color: #667085;
  font-size: 12px;
  margin-top: 4px;
}
</style>
