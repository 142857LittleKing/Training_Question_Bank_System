/**
 * 浏览器级功能冒烟: 真实走 UI 流程
 *  generator: 智能出题 -> 保存 -> 题库列表提交审核
 *  reviewer : 审核中心 通过 -> 题库列表出现"已上架"
 * 用法: node flow_test.mjs [baseUrl]
 */
import puppeteer from 'puppeteer-core'

const base = process.argv[2] || 'http://localhost:5173'
const chrome =
  process.env.CHROME_PATH ||
  'C:/Program Files/Google/Chrome/Application/chrome.exe'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const bodyText = (page) =>
  page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '))

async function waitText(page, text, timeout = 25000) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeout) {
    const t = await bodyText(page)
    if (t.includes(text)) return true
    await sleep(600)
  }
  return false
}

async function fillInput(page, value) {
  // 取当前可聚焦文本输入
  const input = await page.$('input[type=text], input:not([type])')
  if (!input) throw new Error('no text input found')
  await input.click({ clickCount: 3 })
  await input.type(value, { delay: 15 })
}

async function clickText(page, text, tag = 'button') {
  const clicked = await page.evaluate(
    (t, tagSel) => {
      const els = document.querySelectorAll(tagSel)
      for (const el of els) {
        const own = el.childNodes.length
          ? Array.from(el.childNodes)
              .map((n) => n.textContent || '')
              .join('')
          : el.textContent
        if (own.replace(/\s+/g, '').includes(t.replace(/\s+/g, ''))) {
          el.click()
          return true
        }
      }
      return false
    },
    text,
    tag
  )
  if (!clicked) throw new Error(`clickText not found: ${text}`)
}

/** 等待出现 success 类 toast(Element 消息 3s 自动消失, 轮询捕获) */
async function waitSuccessToast(page, timeout = 15000) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeout) {
    const found = await page.evaluate(() =>
      !!document.querySelector('.el-message--success'))
    if (found) return true
    await sleep(300)
  }
  return false
}

/** 处理 Element 消息/确认框: 点“确定” */
async function confirmDialog(page) {
  await page.evaluate(() => {
    const btns = document.querySelectorAll('.el-message-box__btns button, .el-dialog button')
    for (const b of btns) {
      const t = (b.textContent || '').replace(/\s+/g, '')
      if (t.includes('确定') || t.includes('确认') || t === 'OK') { b.click(); return }
    }
  }).catch(() => {})
}

/** 通过 API 清掉当前用户可删除的 AI生成/草稿/驳回 题目, 保证出题结果无重复 */
async function cleanupGeneratorData(page) {
  const removed = await page.evaluate(async () => {
    const token = localStorage.getItem('tqb_token')
    const headers = { Authorization: `Bearer ${token}` }
    let removed = 0
    for (let pageNo = 0; pageNo < 3; pageNo++) {
      const res = await fetch(`/api/questions?mine=true&page=${pageNo}&size=100`, { headers })
      const body = await res.json()
      const rows = body.data?.list || []
      for (const row of rows) {
        if (['GENERATED', 'DRAFT', 'REJECTED'].includes(row.status)) {
          const r = await fetch(`/api/questions/${row.id}`, { method: 'DELETE', headers })
          const rb = await r.json()
          if (rb.code === 0) removed++
        }
      }
      if (rows.length < 100) break
    }
    return removed
  })
  console.log('cleanup removed:', removed)
}

async function selectFirstOption(page, optionTextContains) {
  // 页面上第一个 el-select
  const sel = await page.$('.el-select')
  if (!sel) throw new Error('no el-select')
  await sel.click()
  await sleep(700)
  const clicked = await page.evaluate((t) => {
    const items = document.querySelectorAll('.el-select-dropdown__item')
    for (const it of items) {
      if ((it.textContent || '').includes(t)) {
        it.click()
        return true
      }
    }
    return false
  }, optionTextContains)
  if (!clicked) {
    // 回退: 点击第一个选项
    const first = await page.$('.el-select-dropdown__item')
    if (first) { await first.click(); return }
    throw new Error(`select option not found: ${optionTextContains}`)
  }
}

async function login(page, username, password) {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle0', timeout: 60000 })
  const inputs = await page.$$('input')
  if (inputs.length >= 2) {
    await inputs[0].click({ clickCount: 3 }); await inputs[0].type(username, { delay: 15 })
    await inputs[1].click({ clickCount: 3 }); await inputs[1].type(password, { delay: 15 })
  }
  await clickText(page, '登录', 'button')
  await page.waitForFunction(() => location.pathname !== '/login', { timeout: 20000 })
}

const results = []
function check(name, cond, extra = '') {
  results.push({ name, pass: !!cond })
  console.log(`[${cond ? 'PASS' : 'FAIL'}] ${name} ${extra}`)
}

const browser = await puppeteer.launch({
  executablePath: chrome,
  headless: true,
  defaultViewport: { width: 1440, height: 900 },
  args: ['--no-sandbox', '--disable-gpu']
})

// ================== generator 流程 ==================
const page = await browser.newPage()
const errors = []
page.on('pageerror', (e) => errors.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error' && !/favicon/.test(m.text())) errors.push(m.text().slice(0, 200)) })

await login(page, 'generator', 'gen123456')
check('generator 登录进入系统', page.url().includes('/dashboard'))
await cleanupGeneratorData(page)

// 智能出题
await page.goto(`${base}/ai-generate`, { waitUntil: 'networkidle0', timeout: 60000 })
await selectFirstOption(page, '党史党建')
// 选 单选
await page.evaluate(() => {
  const radios = document.querySelectorAll('.el-radio-button')
  for (const r of radios) {
    if (r.textContent.includes('单选题')) { r.click(); return }
  }
})
await clickText(page, '开始生成', 'button')
const genOk = await waitText(page, '通过格式校验')
check('智能出题生成并显示校验结果', genOk, (await bodyText(page)).includes('本次请求生成'))
await sleep(500)

// 保存通过校验的题目
await clickText(page, '保存通过校验的题目', 'button')
const savedMsg = await waitSuccessToast(page)
check('保存生成题为“AI生成待处理”', savedMsg, '')
await sleep(400)

// 题库管理: 最新一条(GENERATED)提交审核
await page.goto(`${base}/questions`, { waitUntil: 'networkidle0', timeout: 60000 })
await sleep(1200)
const rows = await page.$$('.el-table__body-wrapper tbody tr')
check('题库列表有数据行', rows.length > 0, `rows=${rows.length}`)
let submitted = false
if (rows.length > 0) {
  // 在第一个含“提交审核”按钮的行里点击
  submitted = await page.evaluate(() => {
    const trs = document.querySelectorAll('.el-table__body-wrapper tbody tr')
    for (const tr of trs) {
      const btn = Array.from(tr.querySelectorAll('button')).find((b) =>
        (b.textContent || '').includes('提交审核'))
      if (btn) { btn.click(); return true }
    }
    return false
  })
}
check('提交审核按钮可用', submitted, '')
if (submitted) {
  await confirmDialog(page)
  const ok = await waitSuccessToast(page)
  check('提交审核动作成功提示', ok, '')
}
await sleep(800)

// ================== reviewer 流程 ==================
const page2 = await browser.newPage()
page2.on('pageerror', (e) => errors.push(String(e)))
await login(page2, 'reviewer', 'rev123456')
await page2.goto(`${base}/review-center`, { waitUntil: 'networkidle0', timeout: 60000 })
await sleep(1200)
const pendingRows = await page2.$$('.el-table__body-wrapper tbody tr')
check('审核中心有待审核题目', pendingRows.length > 0, `rows=${pendingRows.length}`)

if (pendingRows.length > 0) {
  // 第一行 通过审核
  const approved = await page2.evaluate(() => {
    const tr = document.querySelector('.el-table__body-wrapper tbody tr')
    if (!tr) return false
    const btn = Array.from(tr.querySelectorAll('button')).find((b) =>
      (b.textContent || '').includes('通过'))
    if (btn) { btn.click(); return true }
    return false
  })
  check('点击“通过审核”', approved, '')
  await sleep(900)
  // 弹出对话框(通过/驳回), 点确定提交
  await confirmDialog(page2)
  const passMsg = await waitSuccessToast(page2, 15000)
  check('审核通过成功提示', passMsg, '')
  await sleep(700)
  // 查看题库: 列表应包含已上架题
  await page2.goto(`${base}/questions`, { waitUntil: 'networkidle0', timeout: 60000 })
  await sleep(1500)
  const publishedVisible = (await bodyText(page2)).includes('已上架')
  check('题库列表包含已上架题', publishedVisible, '')
}

// 抽题检索使用页(公开检索 UI)
await page2.goto(`${base}/quiz`, { waitUntil: 'networkidle0', timeout: 60000 })
await sleep(800)
check('抽题页渲染', (await bodyText(page2)).includes('抽题'))
const randomBtn = await page2.evaluate(() => {
  const btns = Array.from(document.querySelectorAll('button'))
  const b = btns.find((x) => (x.textContent || '').includes('抽题') || (x.textContent || '').includes('开始'))
  if (b) { b.click(); return true }
  return false
})
if (randomBtn) {
  const got = await waitText(page2, '参考答案', 15000) || await waitText(page2, '解析', 15000)
  check('抽题结果渲染(含答案/解析)', got, '')
}

console.log('--- console/page errors ---')
for (const e of errors) console.log('  ERR:', e)
console.log('--- summary ---')
const fails = results.filter((r) => !r.pass)
console.log(fails.length === 0 ? `ALL UI FLOW CHECKS PASSED (${results.length})` : `${fails.length} FAILED`)
await browser.close()
process.exit(fails.length === 0 ? 0 : 1)
