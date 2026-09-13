<template>
  <section class="yunshu-task-card" :class="`yunshu-task-card--${envelope.task.status}`" aria-live="polite">
    <header class="yunshu-task-card__header">
      <div>
        <span class="yunshu-task-card__eyebrow">云枢任务</span>
        <h4>{{ taskTitle }}</h4>
      </div>
      <el-tag :type="statusTone" effect="light" size="small">{{ statusLabel }}</el-tag>
    </header>

    <p v-if="interactionDescription" class="yunshu-task-card__description">{{ interactionDescription }}</p>

    <div v-if="hasDraft" class="yunshu-task-card__draft">
      <div class="yunshu-task-card__section-title">
        <span>单据草稿</span><small>版本 {{ envelope.draft.draftVersion }}</small>
      </div>
      <dl class="yunshu-task-card__fields">
        <div v-for="field in envelope.draft.fields" :key="field.fieldCode" :class="{ 'is-invalid': fieldIssue(field) }">
          <dt>{{ field.label || field.fieldCode }}<em v-if="field.required">*</em></dt>
          <dd>
            <el-input
              v-if="editingDraft && canEditField(field)"
              v-model="draftPatch[field.fieldCode]"
              size="small"
              :aria-label="field.label || field.fieldCode"
            />
            <span v-else>{{ presentValue(field.value) }}</span>
            <small v-if="fieldIssue(field)" class="yunshu-task-card__field-error">{{ fieldIssue(field) }}</small>
          </dd>
        </div>
      </dl>
      <div v-if="editingDraft" class="yunshu-task-card__actions">
        <el-button size="small" :loading="busy" @click="submitDraftPatch">保存修改</el-button>
      </div>
    </div>

    <div v-if="isAskUser" class="yunshu-task-card__questions">
      <div class="yunshu-task-card__section-title"><span>还需要您补充</span></div>
      <div v-for="question in envelope.interaction.questions" :key="question.id" class="yunshu-task-card__question">
        <label :for="inputId(question.id)">{{ question.prompt }}<em v-if="question.required">*</em></label>
        <el-select
          v-if="question.options?.length"
          :id="inputId(question.id)"
          v-model="answers[question.id]"
          class="yunshu-task-card__control"
          :placeholder="question.prompt"
        >
          <el-option v-for="option in question.options" :key="String(option.value)" :label="option.label" :value="option.value" :disabled="option.disabled" />
        </el-select>
        <el-input
          v-else
          :id="inputId(question.id)"
          v-model="answers[question.id]"
          class="yunshu-task-card__control"
          :type="question.type === 'textarea' ? 'textarea' : 'text'"
          :autosize="question.type === 'textarea' ? { minRows: 2, maxRows: 4 } : undefined"
          :placeholder="question.prompt"
        />
      </div>
      <div class="yunshu-task-card__actions">
        <el-button type="primary" size="small" :loading="busy" @click="submitAnswers">继续填写</el-button>
      </div>
    </div>

    <div v-if="hasValidationIssues" class="yunshu-task-card__validation" role="alert">
      <strong>请修正以下内容</strong>
      <ul><li v-for="issue in envelope.validation.issues" :key="`${issue.fieldCode || issue.slotRef}-${issue.message}`">{{ issue.message }}</li></ul>
    </div>

    <div v-if="isConfirmation" class="yunshu-task-card__confirmation">
      <strong>提交前，请核对上方业务信息</strong>
      <p v-if="confirmationPlanHash">本次计划已生成校验标识。</p>
      <div class="yunshu-task-card__actions">
        <el-button type="warning" size="small" :loading="busy" @click="confirm">确认并创建</el-button>
        <el-button size="small" :disabled="busy" @click="cancel">取消任务</el-button>
      </div>
    </div>

    <el-alert v-if="envelope.task.status === 'submitted'" type="success" :closable="false" show-icon title="业务记录已创建">
      <template #default>{{ completionMessage }}</template>
    </el-alert>
    <el-alert v-else-if="envelope.task.status === 'unknown'" type="warning" :closable="false" show-icon title="创建结果正在核对">
      <template #default>系统尚未收到明确结果，正在继续核对，请勿重复创建。</template>
    </el-alert>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { YunshuDraftField, YunshuTaskExperienceEnvelope } from '../../types/yunshuTask'

const props = defineProps<{ envelope: YunshuTaskExperienceEnvelope; busy?: boolean }>()
const emit = defineEmits<{
  input: [value: Record<string, unknown>]
  action: [value: { type: 'confirm' | 'update_draft' | 'cancel'; patch?: Record<string, unknown>; confirmationTicket?: string }]
}>()

const answers = reactive<Record<string, unknown>>({})
const draftPatch = reactive<Record<string, unknown>>({})
const statusLabels: Record<string, string> = {
  needs_input: '等待补充', draft_ready: '草稿已生成', validation_error: '需要修正', ready_to_submit: '可提交',
  confirmation_required: '等待确认', submitting: '正在提交', submitted: '已提交', unknown: '正在核对',
  failed: '创建未完成', cancelled: '已取消', manual_review: '需要人工处理'
}

const statusLabel = computed(() => statusLabels[props.envelope.task.status] || props.envelope.task.status)
const statusTone = computed(() => ({ submitted: 'success', failed: 'danger', validation_error: 'danger', unknown: 'warning', confirmation_required: 'warning' }[props.envelope.task.status] || 'info') as 'success' | 'danger' | 'warning' | 'info')
const taskTitle = computed(() => String(props.envelope.interaction.title || props.envelope.output.title || '云枢业务任务'))
const interactionDescription = computed(() => String(props.envelope.interaction.description || props.envelope.hostAction.payload?.description || props.envelope.output.summary || ''))
const hasDraft = computed(() => props.envelope.draft.fields.length > 0)
const editingDraft = computed(() => props.envelope.interaction.type === 'edit_draft' || props.envelope.hostAction.type === 'edit_draft' || props.envelope.task.status === 'validation_error')
const isAskUser = computed(() => (props.envelope.interaction.type === 'ask_user' || props.envelope.hostAction.type === 'ask_user') && props.envelope.interaction.questions.length > 0)
const isConfirmation = computed(() => props.envelope.interaction.type === 'request_confirmation' || props.envelope.hostAction.type === 'request_confirmation' || props.envelope.task.status === 'confirmation_required')
const hasValidationIssues = computed(() => props.envelope.validation.issues.length > 0)
const completionMessage = computed(() => String(props.envelope.output.message || props.envelope.output.recordRef || '云枢已返回本次操作结果。'))
const confirmationPlanHash = computed(() => String(props.envelope.interaction.planHash || props.envelope.hostAction.payload?.planHash || ''))
const confirmationTicket = computed(() => String(props.envelope.interaction.confirmationTicket || props.envelope.hostAction.payload?.confirmationTicket || ''))

watch(() => props.envelope.draft, (draft) => {
  Object.keys(draftPatch).forEach((key) => delete draftPatch[key])
  draft.fields.forEach((field) => { draftPatch[field.fieldCode] = field.value ?? '' })
}, { immediate: true, deep: true })

function inputId(questionId: string) { return `yunshu-task-${props.envelope.task.id}-${questionId}` }
function presentValue(value: unknown) { return value === undefined || value === null || value === '' ? '未填写' : Array.isArray(value) ? value.join('、') : String(value) }
function canEditField(field: YunshuDraftField) { return field.editable !== false }
function fieldIssue(field: YunshuDraftField) {
  return field.error || props.envelope.validation.issues.find((issue) => issue.fieldCode === field.fieldCode || issue.slotRef === field.fieldCode)?.message
}
function submitAnswers() {
  const missing = props.envelope.interaction.questions.find((question) => question.required && (answers[question.id] === undefined || answers[question.id] === ''))
  if (missing) { ElMessage.warning(`请补充：${missing.prompt}`); return }
  emit('input', { answers: props.envelope.interaction.questions.map((question) => ({ questionId: question.id, value: answers[question.id] })) })
}
function submitDraftPatch() { emit('action', { type: 'update_draft', patch: { ...draftPatch } }) }
function confirm() { emit('action', { type: 'confirm', confirmationTicket: confirmationTicket.value || undefined }) }
function cancel() { emit('action', { type: 'cancel' }) }
</script>

<style scoped>
.yunshu-task-card { margin: 14px 0 2px; padding: 15px; border: 1px solid #d0d5dd; border-radius: 12px; background: #fff; box-shadow: 0 1px 2px rgba(16, 24, 40, .04); }
.yunshu-task-card--confirmation_required { border-color: #fec84b; background: #fffcf5; }
.yunshu-task-card--validation_error { border-color: #fecdca; background: #fffbfa; }
.yunshu-task-card--submitted { border-color: #abefc6; background: #f6fef9; }
.yunshu-task-card--unknown { border-color: #fedf89; background: #fffaeb; }
.yunshu-task-card__header { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.yunshu-task-card__eyebrow, .yunshu-task-card__section-title small { color: #667085; font-size: 12px; }
.yunshu-task-card h4 { margin: 3px 0 0; color: #1d2939; font-size: 14px; }
.yunshu-task-card__description { margin: 9px 0 0; color: #475467; font-size: 13px; line-height: 1.6; }
.yunshu-task-card__draft, .yunshu-task-card__questions, .yunshu-task-card__validation, .yunshu-task-card__confirmation { margin-top: 13px; padding-top: 12px; border-top: 1px solid #eaecf0; }
.yunshu-task-card__section-title { display: flex; align-items: baseline; justify-content: space-between; color: #344054; font-size: 13px; font-weight: 650; }
.yunshu-task-card__fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px 16px; margin: 10px 0 0; }
.yunshu-task-card__fields div { min-width: 0; }.yunshu-task-card__fields dt { color: #667085; font-size: 12px; }.yunshu-task-card__fields dd { margin: 3px 0 0; color: #344054; font-size: 13px; word-break: break-word; }
.yunshu-task-card em { margin-left: 3px; color: #d92d20; font-style: normal; }.yunshu-task-card__field-error { display: block; margin-top: 4px; color: #d92d20; font-size: 12px; }.yunshu-task-card__question { display: grid; gap: 6px; margin-top: 10px; }.yunshu-task-card__question label { color: #344054; font-size: 13px; font-weight: 600; }.yunshu-task-card__control { width: 100%; }.yunshu-task-card__actions { display: flex; gap: 8px; margin-top: 12px; }.yunshu-task-card__validation { color: #b42318; font-size: 13px; }.yunshu-task-card__validation ul { margin: 7px 0 0; padding-left: 18px; line-height: 1.6; }.yunshu-task-card__confirmation p { margin: 6px 0 0; color: #667085; font-size: 12px; }
@media (max-width: 640px) { .yunshu-task-card__fields { grid-template-columns: 1fr; } }
</style>
