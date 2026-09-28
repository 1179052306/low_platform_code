// 报表服务配置 - 集中管理报表后端地址等常量
// 请在 reportUrl 处填写实际的报表后端服务地址
export const REPORT_CONFIG = {
  // 报表后端服务地址（host），请在此填写实际报表后端服务地址
  // 例如 'http://localhost:50950/' 或生产环境地址
  reportUrl: 'http://localhost:50950/',
  // 后端调用动作名（ASP.NET Core 后端为 'DXXRDV'）
  invokeAction: 'DXXRDV',
}