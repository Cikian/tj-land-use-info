/** 只用于读取匹配旧切片编号，不修改业务库的编号。 */
export function estimateLandCode (value) {
  const code = String(value == null ? '' : value)
    .replace(/\(/g, '（').replace(/\)/g, '）')
    .replace(/[_—–―‐‑‒−－﹣～〜]/g, '-')
    .replace(/[\s\u200b-\u200d\ufeff]/g, '')
    .replace(/【[^】]*】/g, '')
  return code && !code.endsWith('号') ? code + '号' : code
}

/** 切片与三维要素的字段可能采用英文编码或中文别名。金额统一成亿元。 */
export function readEstimateLand (props) {
  const fields = Object.keys(props || {}).map(key => ({
    key,
    name: key.toLowerCase().replace(/[_\s（）()]/g, ''),
    value: props[key]
  }))
  const text = names => {
    for (const name of names) {
      const field = fields.find(item => item.name === name && item.value != null && String(item.value).trim())
      if (field) return String(field.value).trim()
    }
    return ''
  }
  let crj = null
  // 只识别金额本身，避免误把“出让金缴纳比例”等字段当作总金额。
  for (const name of ['crj', 'crzj', '出让金', '出让金亿元', '出让金万元', '出让金元', '土地出让金', '土地出让金亿元', '土地出让金万元', '土地出让金元']) {
    const field = fields.find(item => item.name === name && item.value != null && item.value !== '')
    if (!field || (typeof field.value !== 'string' && typeof field.value !== 'number')) continue
    const raw = String(field.value).replace(/[,，\s]/g, '')
    const match = raw.match(/^([+-]?(?:\d+(?:\.\d*)?|\.\d+))(亿元|万元|元)?$/)
    if (!match) continue
    const unit = match[2] || (field.name.endsWith('万元') ? '万元' : field.name.endsWith('亿元') ? '亿元' : field.name.endsWith('元') ? '元' : '亿元')
    const amount = Number(match[1]) / (unit === '万元' ? 10000 : unit === '元' ? 100000000 : 1)
    if (Number.isFinite(amount) && amount > 0 && Number.isFinite(amount * 10000)) {
      crj = amount
      break
    }
  }
  return {
    crzdbh: text(['crzdbh', '出让宗地编号', '宗地编号']),
    dkmc: text(['dkmc', '地块名称']),
    crj
  }
}
