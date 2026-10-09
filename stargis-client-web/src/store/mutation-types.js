// 【中台】令牌：/app/** 接口用，见 src/utils/request.js、src/api/manage.js
export const ACCESS_TOKEN = 'Access-Token'
// 【stargis 改造】Java 业务后端（jeecg）的令牌与用户信息，与中台令牌分开存。
// 中台令牌走 query/body 的 access_token，jeecg 令牌走请求头 X-Access-Token，
// 两者是两套完全独立的会话，绝对不能互相覆盖（详见 src/api/manageJava.js）。
export const JEECG_ACCESS_TOKEN = 'Jeecg-Access-Token'
export const JEECG_USER_INFO = 'Jeecg-User-Info'
export const JEECG_DICT_ITEMS = 'Jeecg-Dict-Items'
export const SIDEBAR_TYPE = 'SIDEBAR_TYPE'
export const DEFAULT_THEME = 'DEFAULT_THEME'
export const DEFAULT_LAYOUT_MODE = 'DEFAULT_LAYOUT_MODE'
export const DEFAULT_COLOR = 'DEFAULT_COLOR'
export const DEFAULT_COLOR_WEAK = 'DEFAULT_COLOR_WEAK'
export const DEFAULT_FIXED_HEADER = 'DEFAULT_FIXED_HEADER'
export const DEFAULT_FIXED_SIDEMENU= 'DEFAULT_FIXED_SIDEMENU'
export const DEFAULT_FIXED_HEADER_HIDDEN = 'DEFAULT_FIXED_HEADER_HIDDEN'
export const DEFAULT_CONTENT_WIDTH_TYPE = 'DEFAULT_CONTENT_WIDTH_TYPE'
export const DEFAULT_MULTI_PAGE = 'DEFAULT_MULTI_PAGE'
export const USER_NAME = 'Login_Username'
export const USER_INFO = 'Login_Userinfo'
export const USER_AUTH = 'LOGIN_USER_BUTTON_AUTH'
export const SYS_BUTTON_AUTH = 'SYS_BUTTON_AUTH'
export const ENCRYPTED_STRING = 'ENCRYPTED_STRING'
export const ENHANCE_PRE = 'enhance_'
export const UI_CACHE_DB_DICT_DATA = 'UI_CACHE_DB_DICT_DATA'
/**
 * 【大屏改造】登录成功 / 登录态有效时要落的首页。
 *
 * 本工程已改造成「地图大屏」，真正的首页是 constantRouterMap 里的 `/`
 * （component = views/screen/index，即带完整浮层的工作站）。
 * 两个历史取值都不能用：
 *   · '/dashboard/analysis' —— 该路由**未注册**；
 *   · '/stargis' —— 只出现在 asyncRouterMap（死配置，GenerateRoutes 从未被 dispatch），
 *     运行时同样**不存在**；views/stargis/index 是「裸地图页」，没有大屏浮层。
 * 跳到未注册路由不会有任何组件渲染（只看到一张地图），所以这里统一收口。
 *
 * ⚠ 新增登录入口请一律用本常量，不要再写裸字符串。
 */
export const HOME_PAGE_PATH = '/'
/**
 * 历史常量，语义是「登录后的主页面」。
 * 它原先指向 /dashboard/analysis（本工程未注册），保留名字只为兼容，
 * 取值已跟随 HOME_PAGE_PATH —— 指向一个不存在的路由是纯粹的陷阱。
 */
export const INDEX_MAIN_PAGE_PATH = HOME_PAGE_PATH
export const OAUTH2_LOGIN_PAGE_PATH = '/oauth2-app/login'
export const TENANT_ID = 'TENANT_ID'
export const ONL_AUTH_FIELDS = 'ONL_AUTH_FIELDS'
//路由缓存问题，关闭了tab页时再打开就不刷新 #842
export const CACHE_INCLUDED_ROUTES = 'CACHE_INCLUDED_ROUTES'
export const CONTENT_WIDTH_TYPE = {
  Fluid: 'Fluid',
  Fixed: 'Fixed'
}