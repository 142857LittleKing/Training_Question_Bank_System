// 批量删除 UI 冒烟: 登录 generator, 勾选两行 -> 批量删除按钮出现 -> 打开确认框后取消
import puppeteer from 'puppeteer-core'
const base = 'http://localhost:5173'
const chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ['--no-sandbox'] })
const page = await browser.newPage()
const errors = []
page.on('pageerror', (e) => errors.push(String(e).slice(0, 300)))
page.on('console', (m) => { if (m.type() === 'error' && !/favicon/.test(m.text())) errors.push(m.text().slice(0, 300)) })

// 登录 generator
await page.goto(`${base}/login`, { waitUntil: 'networkidle0', timeout: 60000 })
const inputs = await page.$$('input')
await inputs[0].type('generator'); await inputs[1].type('gen123456')
await page.evaluate(() => { const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent || '').includes('登')); b && b.click() })
await page.waitForFunction(() => location.pathname !== '/login', { timeout: 20000 })

await page.goto(`${base}/questions`, { waitUntil: 'networkidle0', timeout: 60000 })
await sleep(2500)

const checks = []
function check(name, ok, extra = '') { checks.push({ name, pass: !!ok }); console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name} ${extra}`) }

// 1. 工具栏按钮存在且禁用
const btnInfo = await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('button')).find(b => (b.textContent || '').includes('批量删除'))
  if (!btn) return null
  return { text: btn.textContent.replace(/\s+/g, ''), disabled: btn.disabled }
})
check('存在“批量删除”按钮(默认禁用)', !!btnInfo && btnInfo.disabled, JSON.stringify(btnInfo))

// 2. 勾选前两行 -> 按钮启用且计数
const selCol = await page.$('.el-table__body-wrapper tbody tr .el-checkbox')
if (selCol) await selCol.click()
await sleep(400)
const selCol2 = await page.$('.el-table__body-wrapper tbody tr:nth-child(2) .el-checkbox')
if (selCol2) await selCol2.click()
await sleep(600)
const btnAfter = await page.evaluate(() => {
  const btn = Array.from(document.querySelectorAll('button')).find(b => (b.textContent || '').includes('批量删除'))
  return btn ? { text: btn.textContent.replace(/\s+/g, ''), disabled: btn.disabled } : null
})
check('勾选2行后按钮启用且计数为2', !!btnAfter && !btnAfter.disabled && (btnAfter.text || '').includes('2'), JSON.stringify(btnAfter))

// 3. 点击弹出确认框后取消(不真正删除数据)
let dialogShown = false
if (btnAfter && !btnAfter.disabled) {
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button')).find(b => (b.textContent || '').includes('批量删除'))
    btn && btn.click()
  })
  await sleep(800)
  dialogShown = await page.evaluate(() => {
    const box = document.querySelector('.el-message-box')
    if (!box) return false
    // 点取消
    const cancel = Array.from(box.querySelectorAll('button')).find(b => (b.textContent || '').includes('取'))
    cancel && cancel.click()
    return true
  })
}
check('点击后弹出确认框(可取消)', dialogShown, '')

// 4. 页面无 JS 错误
check('无页面运行错误', errors.length === 0, errors.join(' | '))

const fails = checks.filter(c => !c.pass)
await browser.close()
process.exit(fails.length ? 1 : 0)
