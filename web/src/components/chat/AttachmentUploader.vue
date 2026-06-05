<template>
  <el-upload action="#" :auto-upload="false" multiple :on-change="handleChange">
    <el-button>上传附件</el-button>
    <template #tip>
      <div class="upload-tip">支持发票、图片、PDF、Excel 等业务材料。MVP 阶段上传后进入待解析状态。</div>
    </template>
  </el-upload>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import { uploadAttachment } from '../../services/attachmentApi'
import type { AttachmentResponse } from '../../types/agent'

const emit = defineEmits<{
  uploaded: [attachment: AttachmentResponse]
}>()

async function handleChange(file: UploadFile) {
  if (!file.raw) {
    return
  }
  const attachment = await uploadAttachment(file.raw)
  emit('uploaded', attachment)
}
</script>

<style scoped>
.upload-tip {
  color: #667085;
  font-size: 12px;
  margin-top: 4px;
}
</style>
