import { javaPostAction } from '@/api/manageJava'

/**
 * 数据管理 · 配套地块图形（GeoJSON）导入接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.imports.facilitygeo.controller.FacilityGeoImportController
 * 权限码：land:data:facility（与「配套地块数据录入」面板同一码，入口是该面板工具条按钮）
 *
 * 【与经营性用地 landGeoImport.js 的关系】结构照抄，差异只有匹配键：
 * 配套按 properties.PTXMMC（配套项目名称，归一后）匹配 t_supporting_facilities.ptxmmc；
 * 同名称跨宗地多条记录全部更新（道路类配套的业务常态）。
 *
 * 关键约定：
 *  1. ★ 必须先「预览校验」再「确认导入」：预览绝不写库，有错误时整批不写；
 *  2. 接口无状态：预览与确认各传一次同一文件；
 *  3. 同名称多个 Feature 自动合并为一块多面图形；导入即替换；
 *  4. ★ 不写 t_facility_import_log——那是配套 Excel 导入的「导入记录」表，
 *     留痕走每条配套的变更履历（ACTION_IMPORT，含来源文件/时间/操作人）。
 */

export const facilityGeoImportUrl = {
  preview: '/land/data/facilityGeoImport/preview',
  confirm: '/land/data/facilityGeoImport/confirm'
}

/**
 * 预览校验（★ 不写库）。
 *
 * @param {File} file .geojson / .json 文件
 * @returns {Promise} result 含 totalFeatures / validFeatures / willUpdateRows /
 *   updatedRows / unchangedRows / unmatchedCount / multiRecordNames /
 *   errors / warnings / notices / aborted
 */
export function previewFacilityGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  // 37MB 文件解析 + 批量写远程库：远超全局 60s，单独放宽到 5 分钟
  return javaPostAction(facilityGeoImportUrl.preview, formData, { timeout: 300000 })
}

/**
 * 确认入库（重新解析文件后按名称匹配更新）。
 *
 * @param {File} file 与预览时相同的文件
 * @returns {Promise} result 含 updatedRows 等实际写入计数
 */
export function confirmFacilityGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return javaPostAction(facilityGeoImportUrl.confirm, formData, { timeout: 300000 })
}
