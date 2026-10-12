/**
 * 审批操作逻辑（FE-S6 审批流）。
 * 封装审批 API 调用：approve/reject/transfer/countersign。
 */
import { httpRequest } from '@/utils/request'
import type { ApprovalActionType, ApprovalRecord } from '@/types/schema'

export function useApproval() {
  async function approvalAction(
    bizCode: string,
    bizId: string,
    action: ApprovalActionType,
    comment?: string,
    extra?: Record<string, unknown>,
  ): Promise<boolean> {
    const res = await httpRequest('/api/lowcode/approval/action', {
      method: 'POST',
      body: JSON.stringify({ bizCode, bizId, action, comment, ...extra }),
    })
    if (!res.ok) {
      const errData = res.data as { msg?: string; message?: string } | undefined
      throw new Error(errData?.msg || errData?.message || `审批操作失败 (HTTP ${res.status})`)
    }
    const body = res.data as { res?: boolean; msg?: string }
    if (body && body.res === false) {
      throw new Error(body.msg || '审批操作失败')
    }
    return true
  }

  async function getApprovalHistory(bizCode: string, bizId: string): Promise<ApprovalRecord[]> {
    const res = await httpRequest('/api/lowcode/approval/history', {
      method: 'POST',
      body: JSON.stringify({ bizCode, bizId }),
    })
    if (!res.ok) {
      const errData = res.data as { msg?: string; message?: string } | undefined
      throw new Error(errData?.msg || errData?.message || `加载审批记录失败 (HTTP ${res.status})`)
    }
    const body = res.data as { res?: boolean; msg?: string; data?: ApprovalRecord[] }
    if (body && body.res === false) {
      throw new Error(body.msg || '加载审批记录失败')
    }
    return body.data || []
  }

  return {
    approvalAction,
    getApprovalHistory,
  }
}