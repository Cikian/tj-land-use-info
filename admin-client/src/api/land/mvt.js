import { deleteAction } from '@/api/manage'
// 对应 Java 后端 MvtAdminController；目录扫描可能等待数据库连接，单独放宽超时。
import { axios } from '@/utils/request'

export function getMvtSources() {
  return axios({ url: '/land/mvt/sources', method: 'get', timeout: 45000 })
}

export function getMvtTables(source, refresh = false) {
  return axios({ url: '/land/mvt/tables', method: 'get', params: { source, refresh }, timeout: 45000 })
}

/**
 * 从 MVT 管理清单移除一个 GIS 库。
 * dropDatabase=false（默认）：仅取消登记，PG 库及其数据保留；
 * dropDatabase=true：连 PG 库一起删除（先断连接再 DROP，不可恢复）——
 * 仅限本系统 SHP 导入的库，后端会按 remark 二次校验，非本系统导入直接拒绝。
 * 后端：DELETE /land/spatial/source/{id}?dropDatabase=
 */
export function removeMvtSource (id, dropDatabase = false) {
  return deleteAction('/land/spatial/source/' + id, { dropDatabase })
}
