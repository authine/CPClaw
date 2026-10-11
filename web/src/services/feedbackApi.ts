import { requestJson } from './api'
export type MessageFeedbackType = 'like' | 'dislike'
export interface MessageFeedbackResult { messageId: string; feedbackType?: MessageFeedbackType | null; updatedAt?: string }
export function updateMessageFeedback(messageId: string, feedbackType?: MessageFeedbackType | null, reason?: string) {
  return requestJson<MessageFeedbackResult>(`/conversations/messages/${encodeURIComponent(messageId)}/feedback`, { method: 'PUT', body: JSON.stringify({ feedbackType: feedbackType ?? null, reason: reason || undefined }) })
}
export function submitFeedback(payload: {appCode?: string;category: string;title: string;content: string}) { return requestJson('/feedback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}) }
