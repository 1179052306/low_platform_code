import type { LocationStatus } from '@/types/warehouse-3d'

export const STATUS_COLORS: Record<LocationStatus, { hex: number; label: string }> = {
  empty: { hex: 0x3a3d44, label: '空库位' },
  partial: { hex: 0x66bb6a, label: '部分占用' },
  full: { hex: 0x2e7d32, label: '满库位' },
  locked: { hex: 0xff9800, label: '锁定' },
  reserved: { hex: 0x42a5f5, label: '预留' },
  disabled: { hex: 0x616161, label: '禁用' },
  error: { hex: 0xef5350, label: '异常' },
}

export const SELECTED_COLOR = 0xFFD700
export const HIGHLIGHT_COLOR = 0x00BCD4