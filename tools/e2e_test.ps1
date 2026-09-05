# 培训题库系统 E2E 冒烟测试 (后端 API)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers; ContentType = 'application/json; charset=utf-8' }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    $resp = $null
    try {
        $w = Invoke-WebRequest @params -UseBasicParsing
        $stream = $w.RawContentStream
        $stream.Position = 0
        $ms = [System.IO.MemoryStream]::new()
        $stream.CopyTo($ms)
        $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        $resp = $text | ConvertFrom-Json
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        throw "HTTP ERROR $Method $Path -> $msg"
    }
    if ($resp.code -ne 0) { throw "API ERROR $Method $Path -> code=$($resp.code) msg=$($resp.message)" }
    return $resp.data
}

function Invoke-Multipart {
    param([string]$Path, [string]$Token, [hashtable]$Fields, [System.IO.FileInfo]$File)
    $boundary = '----tqb' + [guid]::NewGuid().ToString('N')
    $nl = "`r`n"
    $ms = [System.IO.MemoryStream]::new()
    function W([string]$s) { $bytes = [System.Text.Encoding]::UTF8.GetBytes($s); $ms.Write($bytes, 0, $bytes.Length) }
    W("--$boundary$nl")
    W("Content-Disposition: form-data; name=`"file`"; filename=`"$($File.Name)`"$nl")
    W("Content-Type: application/octet-stream$nl$nl")
    $fileBytes = [System.IO.File]::ReadAllBytes($File.FullName)
    $ms.Write($fileBytes, 0, $fileBytes.Length)
    W("$nl")
    foreach ($key in $Fields.Keys) {
        W("--$boundary$nl")
        W("Content-Disposition: form-data; name=`"$key`"$nl$nl")
        W([string]$Fields[$key])
        W("$nl")
    }
    W("--$boundary--$nl")
    $headers = @{ Authorization = "Bearer $Token" }
    $resp = $null
    try {
        $w = Invoke-WebRequest -Method POST -Uri "$base$Path" -Headers $headers `
            -ContentType "multipart/form-data; boundary=$boundary" -Body $ms.ToArray() -UseBasicParsing
        $stream = $w.RawContentStream
        $stream.Position = 0
        $mb = [System.IO.MemoryStream]::new()
        $stream.CopyTo($mb)
        $text = [System.Text.Encoding]::UTF8.GetString($mb.ToArray())
        $resp = $text | ConvertFrom-Json
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        throw "HTTP ERROR $Path -> $msg"
    }
    if ($resp.code -ne 0) { throw "API ERROR $Path -> code=$($resp.code) msg=$($resp.message)" }
    return $resp.data
}

$results = [System.Collections.Generic.List[string]]::new()
function Check {
    param([string]$Name, [object]$Actual, $Expected)
    $ok = $false
    if ($Expected -is [array]) { $ok = $Actual -in $Expected } else { $ok = ($Actual -eq $Expected) }
    if ($ok) { Write-Host "[PASS] $Name" -ForegroundColor Green; $script:results.Add("PASS $Name") }
    else { Write-Host "[FAIL] $Name actual=$Actual expected=$Expected" -ForegroundColor Red; $script:results.Add("FAIL $Name actual=$Actual expected=$Expected") }
}

Write-Host "==== 1. 登录 ===="
$admin = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'admin123' }
$gen = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'generator'; password = 'gen123456' }
$rev = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'reviewer'; password = 'rev123456' }
Check 'admin登录' $admin.role 'ADMIN'
Check 'generator登录' $gen.role 'GENERATOR'
Check 'reviewer登录' $rev.role 'REVIEWER'
$adminToken = $admin.token; $genToken = $gen.token; $revToken = $rev.token

Write-Host "==== 2. 数据看板 ===="
$dash = Invoke-Api -Method GET -Path '/dashboard/summary' -Token $adminToken
Check '看板题目总数' $dash.total 150
Check '看板已上架数' $dash.totalPublished 150
Check '按状态统计存在' ($dash.byStatus.Count -gt 0) $true

Write-Host "==== 3. 知识点 ===="
$kps = Invoke-Api -Method GET -Path '/knowledge-points/all' -Token $genToken
$kp = $kps | Where-Object { $_.name -eq '党史党建' } | Select-Object -First 1
Check '党史党建知识点存在' ($null -ne $kp) $true
Check '知识点题目数=150' $kp.questionCount 150

Write-Host "==== 4. 检索使用(公开) ===="
$pub = Invoke-RestMethod -Uri "$base/quiz/random?type=SINGLE&count=5"
Check 'quiz随机抽5题' $pub.data.Count 5
$search = Invoke-RestMethod -Uri "$base/quiz/search?keyword=&page=0&size=3"
Check 'quiz检索公开可用' ($search.code) 0

Write-Host "==== 5. AI 出题(自动降级mock) ===="
$aiInfo = Invoke-Api -Method GET -Path '/ai/provider' -Token $genToken
Write-Host "    当前出题源: $($aiInfo.name) keyConfigured=$($aiInfo.apiKeyConfigured)"
$genResp = Invoke-Api -Method POST -Path '/ai/generate' -Token $genToken -Body @{
    knowledgePointId = $kp.id; type = 'SINGLE'; count = 3; difficulty = 3; extraInstruction = ''
}
Check '生成条数=3' $genResp.items.Count 3
Check 'mock全部通过校验' $genResp.validCount 3
$validItems = $genResp.items | Where-Object { $_.valid -and -not $_.duplicate }
$save = Invoke-Api -Method POST -Path '/ai/save' -Token $genToken -Body @{
    questions = @($validItems | ForEach-Object { $_.question }); batchLabel = 'E2E-SINGLE'
}
Check '保存AI题=3' $save.saved 3

$genResp2 = Invoke-Api -Method POST -Path '/ai/generate' -Token $genToken -Body @{
    knowledgePointId = $kp.id; type = $null; count = 4; difficulty = 3
}
Check '混合题型生成4道(四类均衡)' $genResp2.items.Count 4
$save2 = Invoke-Api -Method POST -Path '/ai/save' -Token $genToken -Body @{
    questions = @(($genResp2.items | Where-Object { $_.valid -and -not $_.duplicate }) | ForEach-Object { $_.question })
}
Check '保存混合题=3(1道与首批重复)' $save2.saved 3
Check '混合生成阶段识别出重复项' @($genResp2.items | Where-Object { $_.duplicate }).Count 1

Write-Host "==== 6. 生成-审核-上架 流程 ===="
$list = Invoke-Api -Method GET -Path '/questions?status=GENERATED&page=0&size=10' -Token $adminToken
$first = $list.list | Where-Object { $_.type -eq 'SINGLE' } | Select-Object -First 1
$submitResp = Invoke-Api -Method POST -Path "/questions/$($first.id)/submit" -Token $genToken
Check '提交审核后状态=PENDING' $submitResp.status 'PENDING'

$approve = Invoke-Api -Method POST -Path "/review/$($first.id)/approve" -Token $revToken -Body @{ comment = '内容规范, 通过' }
Check '审核通过后=已上架' $approve.status 'PUBLISHED'

$second = $list.list | Where-Object { $_.type -eq 'JUDGE' } | Select-Object -First 1
Invoke-Api -Method POST -Path "/questions/$($second.id)/submit" -Token $genToken | Out-Null
$reject = Invoke-Api -Method POST -Path "/review/$($second.id)/reject" -Token $revToken -Body @{ comment = '判断题表述需调整' }
Check '审核驳回后=已驳回' $reject.status 'REJECTED'
$rejectRec = @(Invoke-Api -Method GET -Path "/review-records/question/$($second.id)" -Token $adminToken)
Check '驳回产生审核记录' $rejectRec.Count 1

$offline = Invoke-Api -Method POST -Path "/questions/$($first.id)/offline" -Token $revToken
Check '下架后=OFFLINE' $offline.status 'OFFLINE'
$resubmit = Invoke-Api -Method POST -Path "/questions/$($first.id)/submit" -Token $genToken
Check '下架后可重新提交=PENDING' $resubmit.status 'PENDING'
$approve2 = Invoke-Api -Method POST -Path "/review/$($first.id)/approve" -Token $revToken -Body @{ comment = '复核通过' }
Check '再次上架=PUBLISHED' $approve2.status 'PUBLISHED'

Write-Host "==== 7. 手工录入 + 格式校验 ===="
$bad = $null
try {
    Invoke-Api -Method POST -Path '/questions' -Token $genToken -Body @{
        type = 'SINGLE'; stem = '校验测试-缺选项答案非法'; options = @('A', 'B'); answer = 'Z'; analysis = ''; difficulty = 3; knowledgePointId = $kp.id
    }
} catch { $bad = $_.Exception.Message }
Check '非法答案被拦截' ($null -ne $bad) $true

$manual = Invoke-Api -Method POST -Path '/questions' -Token $genToken -Body @{
    type = 'JUDGE'; stem = '手工录入判断题E2E测试, 1+1=2'; options = $null; answer = '对'; analysis = '基本事实'; difficulty = 1; knowledgePointId = $kp.id
}
Check '手工录入成功' $manual.status 'DRAFT'
$dup = $null
try { Invoke-Api -Method POST -Path '/questions' -Token $genToken -Body @{
        type = 'JUDGE'; stem = '手工录入判断题E2E测试, 1+1=2'; options = $null; answer = '对'; difficulty = 1; knowledgePointId = $kp.id
    } } catch { $dup = $_.Exception.Message }
Check '重复题干被拦截' ($null -ne $dup) $true

Write-Host "==== 8. 批量导入(.json) ===="
$form = @{ defaultStatus = 'PUBLISHED' }
$filePath = Join-Path $PWD 'data\templates\样例导入.json'
$upload = Invoke-Multipart -Path '/import' -Token $genToken -Fields @{ defaultStatus = 'PUBLISHED' } -File (Get-Item $filePath)
Check '导入4行全部成功' $upload.success 4
$upload2 = Invoke-Multipart -Path '/import' -Token $genToken -Fields @{ defaultStatus = 'PUBLISHED' } -File (Get-Item $filePath)
Check '重复导入全部失败(查重)' $upload2.failed 4

Write-Host "==== 9. 检索/详情 ===="
$q = Invoke-Api -Method GET -Path '/questions?status=PUBLISHED&type=SINGLE&page=0&size=5' -Token $genToken
Check '已上架单选检索>0' ($q.list.Count -gt 0) $true
$detail = Invoke-Api -Method GET -Path "/questions/$($first.id)" -Token $genToken
Check '题目详情有解析/答案' ([bool]$detail.analysis -or [bool]$detail.answer) $true

Write-Host "==== 10. 删除清理 ===="
$cleanup = Invoke-Api -Method GET -Path '/questions?page=0&size=100&mine=true' -Token $genToken
foreach ($row in $cleanup.list) {
    if ($row.status -in @('DRAFT', 'GENERATED', 'REJECTED')) {
        try { Invoke-Api -Method DELETE -Path "/questions/$($row.id)" -Token $genToken | Out-Null } catch { }
    }
}
Write-Host "清理完成: 剩余我的题目 $($cleanup.list.Count)"

Write-Host ""
Write-Host "==== 汇总 ====" -ForegroundColor Cyan
$fails = $results | Where-Object { $_ -like 'FAIL*' }
if ($fails.Count -eq 0) { Write-Host "ALL E2E PASSED ($($results.Count) checks)" -ForegroundColor Green } else { $fails | ForEach-Object { Write-Host $_ -ForegroundColor Red } }
