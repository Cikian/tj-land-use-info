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
 * 从 MVT 管理清单移除一个 GIS 库（不删除 PG 库本身）。
 * 后端：DELETE /land/spatial/source/{id}
 */
export function removeMvtSource (id) {
  return deleteAction('/land/spatial/source/' + id)
}
