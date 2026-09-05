/**
 * UI 冒烟: 用系统 Chrome 无头驱动前端, 登录后逐页截图。
 * 用法: node run_ui_qa.mjs [baseUrl]
 */
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import path from 'node:path'

const base = process.argv[2] || 'http://localhost:5173'
const chrome =
  process.env.CHROME_PATH ||
  'C:/Program Files/Google/Chrome/Application/chrome.exe'

const shotsDir = path.resolve('shots')
fs.mkdirSync(shotsDir, { recursive: true })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function shot(page, name) {
  await sleep(700)
  await page.screenshot({ path: path.join(shotsDir, `${name}.png`), fullPage: false })
  console.log('shot:', name)
}

async function fillInputs(page, values) {
  const inputs = await page.$$('input[type=text], input[type=password], input:not([type])')
  let used = 0
  for (const v of values) {
    if (used < inputs.length) {
      await inputs[used].click({ clickCount: 3 })
      await inputs[used].type(v, { delay: 20 })
      used++
    }
  }
  return used
}

async function clickButton(page, text) {
  const btns = await page.$$('button')
  for (const b of btns) {
    const t = (await b.evaluate((el) => el.textContent || '')).replace(/\s+/g, '')
    if (t.includes(text)) {
      await b.click()
      return true
    }
  }
  return false
}

const browser = await puppeteer.launch({
  executablePath: chrome,
  headless: true,
  defaultViewport: { width: 1440, height: 900 },
  args: ['--no-sandbox', '--disable-gpu']
})

const page = await browser.newPage()
page.on('console', (m) => { if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 300)) })
page.on('pageerror', (e) => console.log('[pageerror]', String(e).slice(0, 300)))
page.on('response', (r) => { if (r.status() >= 500) console.log('[http>=500]', r.status(), r.url().slice(0, 200)) })

console.log('open login...')
await page.goto(`${base}/login`, { waitUntil: 'networkidle0', timeout: 60000 })
await shot(page, '01-login')

// 登录 admin
await fillInputs(page, ['admin', 'admin123'])
await clickButton(page, '登')
await page.waitForFunction(() => location.pathname !== '/login', { timeout: 20000 }).catch(() => {})
console.log('after login path =', page.url())
await shot(page, '02-dashboard')

const routes = [
  ['03-knowledge-points', '/knowledge-points'],
  ['04-question-list', '/questions'],
  ['05-ai-generate', '/ai-generate'],
  ['06-review-center', '/review-center'],
  ['07-review-records', '/review-records'],
  ['08-import', '/import'],
  ['09-quiz', '/quiz'],
  ['10-profile', '/profile']
]
for (const [name, pathname] of routes) {
  await page.goto(`${base}${pathname}`, { waitUntil: 'networkidle0', timeout: 60000 }).catch(() => {})
  await shot(page, name)
}

// 手工录入页
await page.goto(`${base}/questions/edit`, { waitUntil: 'networkidle0', timeout: 60000 }).catch(() => {})
await shot(page, '11-question-edit')

await browser.close()
console.log('DONE, shots in', shotsDir)
