$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'

function Invoke-ApiJson {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 12 -Compress))
    }
    try {
        $w = Invoke-WebRequest @params -UseBasicParsing
    } catch {
        return @{ __httpError = $_.Exception.Response.StatusCode.value__; __message = $_.ErrorDetails.Message }
    }
    $stream = $w.RawContentStream; $stream.Position = 0
    $ms = [System.IO.MemoryStream]::new(); $stream.CopyTo($ms)
    $resp = ([System.Text.Encoding]::UTF8.GetString($ms.ToArray())) | ConvertFrom-Json
    if ($resp.code -ne 0) { throw "API err: $($resp.message)" }
    return $resp.data
}

$results = [System.Collections.Generic.List[string]]::new()
function Check {
    param([string]$Name, $Actual, $Expected)
    if ($Actual -eq $Expected) { Write-Host "[PASS] $Name" -ForegroundColor Green } else { Write-Host "[FAIL] $Name actual=$Actual expected=$Expected" -ForegroundColor Red }
}

$gen = Invoke-ApiJson 'POST' '/auth/login' @{ username = 'generator'; password = 'gen123456' }
$admin = Invoke-ApiJson 'POST' '/auth/login' @{ username = 'admin'; password = 'admin123' }
$rev = Invoke-ApiJson 'POST' '/auth/login' @{ username = 'reviewer'; password = 'rev123456' }
$tag = (Get-Date -Format 'HHmmssfff')

$d1 = Invoke-ApiJson 'POST' '/questions' -Token $gen.token -Body @{ type = 'JUDGE'; stem = "批量删除测试A-$tag 1+1=2"; options = $null; answer = '对'; analysis = ''; difficulty = 1; knowledgePointId = 1 }
$d2 = Invoke-ApiJson 'POST' '/questions' -Token $gen.token -Body @{ type = 'SHORT'; stem = "批量删除测试B-$tag 简述作用"; options = $null; answer = '参考答案'; analysis = ''; difficulty = 2; knowledgePointId = 1 }
$d3 = Invoke-ApiJson 'POST' '/questions' -Token $gen.token -Body @{ type = 'JUDGE'; stem = "批量删除测试C-$tag 会被管理员删"; options = $null; answer = '对'; analysis = ''; difficulty = 1; knowledgePointId = 1 }
Write-Host "created drafts: $($d1.id), $($d2.id), $($d3.id)"

# 1) generator 批量: 2 个本人草稿 + 1 个他人已上架题(1号=seed) -> 部分成功
$seed = Invoke-ApiJson 'GET' '/questions?status=PUBLISHED&page=0&size=1' -Token $gen.token
$otherId = $seed.list[0].id
$r1 = Invoke-ApiJson 'POST' '/questions/batch-delete' -Token $gen.token -Body @{ ids = @($d1.id, $d2.id, $otherId) }
Check '批量删除requested=3' $r1.requested 3
Check '本人草稿删除成功2' $r1.deleted 2
$failedItem = @($r1.items | Where-Object { -not $_.deleted })
Check '他人题被拒1' $failedItem.Count 1
Check '拒绝原因含“自己录入”' ([bool]($failedItem[0].reason -match '自己录入')) $true

# 2) admin 删除第三个草稿(任意)
$r2 = Invoke-ApiJson 'POST' '/questions/batch-delete' -Token $admin.token -Body @{ ids = @($d3.id) }
Check 'admin批量删除1' $r2.deleted 1

# 3) 已删除的不存在 -> 返回原因而非崩溃
$r3 = Invoke-ApiJson 'POST' '/questions/batch-delete' -Token $gen.token -Body @{ ids = @($d1.id) }
Check '重复删除返回不存在' ($r3.items[0].reason -match '不存在') $true

# 4) 空 ids -> 参数校验 400
$emptyResp = Invoke-ApiJson 'POST' '/questions/batch-delete' -Token $gen.token -Body @{ ids = @() }
Check '空ids被拦截(400)' $emptyResp.__httpError 400

# 5) reviewer 无权限 -> 403
$r5 = Invoke-ApiJson 'POST' '/questions/batch-delete' -Token $rev.token -Body @{ ids = @(1) }
Check '审核员无权限(403)' $r5.__httpError 403

# 6) 库里确认已删除
$mine = Invoke-ApiJson 'GET' "/questions?status=DRAFT&mine=true&page=0&size=50" -Token $gen.token
$left = @($mine.list | Where-Object { $_.stem -match $tag })
Check '三个测试草稿均已删除' $left.Count 0

Write-Host "batch-delete 后端测试完成"
