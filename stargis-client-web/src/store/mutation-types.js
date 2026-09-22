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
export const INDEX_MAIN_PAGE_PATH = '/dashboard/analysis'
export const OAUTH2_LOGIN_PAGE_PATH = '/oauth2-app/login'
export const TENANT_ID = 'TENANT_ID'
export const ONL_AUTH_FIELDS = 'ONL_AUTH_FIELDS'
//路由缓存问题，关闭了tab页时再打开就不刷新 #842
export const CACHE_INCLUDED_ROUTES = 'CACHE_INCLUDED_ROUTES'
export const CONTENT_WIDTH_TYPE = {
  Fluid: 'Fluid',
  Fixed: 'Fixed'
}