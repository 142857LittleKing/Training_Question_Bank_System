/** 通用格式化小工具 */

/** 时间/日期字符串 -> 本地时间(24小时制); 空值返回 '-' */
export function fmtTime(v) {
  if (v === null || v === undefined || v === '') return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString('zh-CN', { hour12: false })
}

/** 索引 -> 选项字母 A/B/C... */
export function letterOf(index) {
  return String.fromCharCode(65 + index)
}

/** 选项字母字符串 -> 字母数组 ('A,C' -> ['A','C']) */
export function splitLetters(s) {
  if (!s) return []
  return String(s)
    .split(/[,，;；、\s]+/)
    .map((t) => t.trim())
    .filter(Boolean)
}
