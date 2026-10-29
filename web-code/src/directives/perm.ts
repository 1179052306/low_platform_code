/**
 * v-perm 按钮权限指令（功能权限：permCode 如 system:user:create，管界面显隐）。
 * 与业务权限 hasActionPermission（bizCode×action，管业务数据）区分：功能权限管界面、业务权限管数据。
 * ADMIN（'*'）短路放行；无权限移除元素。
 */
import type { App, Directive, DirectiveBinding } from 'vue'
import { hasPerm } from '@/store/auth'

const permDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
    if (!binding.value) return
    if (!hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupPermDirective(app: App): void {
  app.directive('perm', permDirective)
}

export default permDirective