import { h } from 'vue'
import { apiForm } from '../../js/adminApi'

export const navItems = [
  { key: 'knowledge', label: '知识库管理', href: '/admin/knowledge', icon: DatabaseIcon },
  { key: 'survey', label: '问卷调查', href: '/admin/survey', icon: FileIcon },
  { key: 'vector', label: '向量检索调试', href: '/admin/vector', icon: SearchIcon },
  { key: 'dashboard', label: '数据统计', href: '/admin/dashboard', icon: ChartIcon },
  { key: 'academic', label: '学业帮扶', href: '/admin/academic', icon: CapIcon },
  { key: 'chat', label: '人工客服', href: '/admin/chat', icon: HeadsetIcon }
]

export function AdminSidebar(props) {
  return h('aside', { class: 'admin-sidebar' }, [
    h('div', { class: 'admin-brand' }, [
      h('div', { class: 'brand-mark' }, [h(SparkIcon)]),
      h('div', [h('h1', '智答后台'), h('p', 'Admin Console')])
    ]),
    h('nav', { class: 'admin-nav' }, navItems.map(item => h('a', {
      href: item.href,
      class: { active: props.active === item.key }
    }, [h(item.icon), item.label]))),
    h('div', { class: 'admin-foot' }, ['大连东软信息学院', h('br'), '智能问答 · v1.0'])
  ])
}

AdminSidebar.props = ['active']

export function AdminTopbar(props) {
  const userInfo = getStoredUserInfo()
  const displayName = userInfo?.realName || userInfo?.username || '未登录用户'
  const account = userInfo?.username ? `账号：${userInfo.username}` : '账号：-'
  const avatarText = (displayName || 'U').trim().slice(0, 1).toUpperCase()

  return h('header', { class: 'admin-topbar' }, [
    h('div', [h('h2', props.title), h('p', { class: 'admin-subtitle' }, '管理员视图')]),
    h('div', { class: 'admin-user' }, [
      h('div', { class: 'admin-user-meta' }, [h('div', displayName), h('div', account)]),
      h('button', {
        class: 'avatar avatar-button',
        type: 'button',
        title: '退出账号',
        onClick: confirmAdminLogout
      }, avatarText)
    ])
  ])
}

AdminTopbar.props = ['title']

function getStoredUserInfo() {
  const raw = localStorage.getItem('user_info') || sessionStorage.getItem('user_info')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function confirmAdminLogout() {
  if (window.confirm('确定要退出当前账号吗？')) {
    handleAdminLogout()
  }
}

async function handleAdminLogout() {
  try {
    await apiForm('/api/auth/logout')
  } catch {
    // Local cleanup should still happen even if the backend session endpoint is unavailable.
  }
  localStorage.removeItem('token')
  localStorage.removeItem('jwt')
  localStorage.removeItem('user_info')
  localStorage.removeItem('user_role')
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('jwt')
  sessionStorage.removeItem('user_info')
  sessionStorage.removeItem('user_role')
  window.location.href = '/'
}

function svg(paths, attrs = {}) {
  return h('svg', { viewBox: '0 0 24 24', class: 'icon', ...attrs }, paths.map(path => h('path', path)))
}

export function SparkIcon() {
  return svg([
    { d: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z' },
    { d: 'M19 3v4' },
    { d: 'M21 5h-4' },
    { d: 'M5 17v3' },
    { d: 'M6.5 18.5h-3' }
  ])
}

export function DatabaseIcon() {
  return svg([{ d: 'M4 6c0 1.7 3.6 3 8 3s8-1.3 8-3-3.6-3-8-3-8 1.3-8 3z' }, { d: 'M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6' }, { d: 'M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3' }])
}

export function SearchIcon() {
  return svg([{ d: 'M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z' }, { d: 'M21 21l-4.3-4.3' }])
}

export function ChartIcon() {
  return svg([{ d: 'M4 19V5' }, { d: 'M4 19h18' }, { d: 'M8 16V9' }, { d: 'M13 16V6' }, { d: 'M18 16v-4' }])
}

export function CapIcon() {
  return svg([{ d: 'M22 10L12 5 2 10l10 5 10-5z' }, { d: 'M6 12v5c3 2 9 2 12 0v-5' }])
}

export function HeadsetIcon() {
  return svg([{ d: 'M4 14v-2a8 8 0 0 1 16 0v2' }, { d: 'M4 14h4v6H6a2 2 0 0 1-2-2v-4z' }, { d: 'M20 14h-4v6h2a2 2 0 0 0 2-2v-4z' }])
}

export function UploadIcon(props = {}) {
  return svg([{ d: 'M12 16V4' }, { d: 'M7 9l5-5 5 5' }, { d: 'M5 20h14' }], props)
}

export function FileIcon() {
  return svg([{ d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }, { d: 'M14 2v6h6' }, { d: 'M8 13h8' }, { d: 'M8 17h6' }])
}

export function PlusIcon() {
  return svg([{ d: 'M12 5v14' }, { d: 'M5 12h14' }])
}

export function EditIcon() {
  return svg([{ d: 'M12 20h9' }, { d: 'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z' }])
}

export function TrashIcon() {
  return svg([{ d: 'M3 6h18' }, { d: 'M8 6V4h8v2' }, { d: 'M6 6l1 16h10l1-16' }, { d: 'M10 11v6' }, { d: 'M14 11v6' }])
}

export function RefreshIcon() {
  return svg([{ d: 'M21 12a9 9 0 0 1-15.5 6.2' }, { d: 'M3 12a9 9 0 0 1 15.5-6.2' }, { d: 'M18 3v4h-4' }, { d: 'M6 21v-4h4' }])
}

export function SendIcon() {
  return svg([{ d: 'M22 2L11 13' }, { d: 'M22 2l-7 20-4-9-9-4 20-7z' }])
}

export function AlertIcon() {
  return svg([{ d: 'M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z' }, { d: 'M12 9v4' }, { d: 'M12 17h.01' }])
}

export function CheckIcon() {
  return svg([{ d: 'M20 6L9 17l-5-5' }])
}
