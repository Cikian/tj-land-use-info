import { javaPostAction } from '@/api/manageJava'

/**
 * 数据管理 · 地块图形（GeoJSON）导入接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.imports.geojson.controller.LandGeoImportController
 * 权限码：land:data:land（与「经营性用地信息录入」面板同一码，入口是该面板工具条按钮）
 *
 * 【与 admin-client/src/api/land/landGeoImport.js 的关系】
 * 结构照抄那一份，只把请求层从 `@/api/manage` 换成 manageJava ——
 * 这些接口在 Java 业务后端（VUE_DATA_JAVA_URL），走中台的 domianURL 会 404。
 *
 * 功能：上传 GeoJSON 文件（shp 转出的 FeatureCollection），按每个 Feature 的
 * properties.CRZDBH 匹配 t_land.crzdbh，把匹配记录的 dk_geojson 更新为该 Feature 的 geometry。
 *
 * 关键约定：
 *  1. ★ 必须先「预览校验」再「确认导入」：预览绝不写库，有错误时整批不写；
 *  2. 接口无状态：预览与确认各传一次同一文件（确认时后端重新解析，
 *     保证写进去的就是此刻文件里的内容）；
 *  3. 编号在匹配前按库内口径归一（半角括号→中文括号、去空白、补「号」结尾），
 *     所以文件里的「津蓟（挂）2017-025」能匹配上库里的「津蓟（挂）2017-025号」；
 *  4. 未匹配的 Feature 只警告跳过，不影响其它地块导入；
 *     文件内同一编号出现多个 Feature 会报错（图形只能有一个）。
 */

export const landGeoImportUrl = {
  preview: '/land/data/geoImport/preview',
  confirm: '/land/data/geoImport/confirm'
}

/**
 * 预览校验（★ 不写库）。
 *
 * 【为什么可以走 javaPostAction 发 multipart】
 * manageJava 的 javaPostAction 不设 Content-Type，传 FormData 时 axios 会
 * 自己带上 boundary（与 landImport.js 的 previewLandImport 同一做法）。
 *
 * @param {File} file .geojson / .json 文件
 * @returns {Promise} result 含 totalFeatures / validFeatures / willUpdateRows /
 *   updatedRows / unchangedRows / unmatchedCount / errors / warnings / notices / aborted
 */
export function previewLandGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return javaPostAction(landGeoImportUrl.preview, formData)
}

/**
 * 确认入库（重新解析文件后按 CRZDBH 匹配更新）。
 *
 * @param {File} file 与预览时相同的文件
 * @returns {Promise} result 含 updatedRows 等实际写入计数
 */
export function confirmLandGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return javaPostAction(landGeoImportUrl.confirm, formData)
}
