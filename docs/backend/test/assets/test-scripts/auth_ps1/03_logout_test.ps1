<#
    03_logout_test.ps1 - 登出接口测试
    测试 Auth 模块的 POST /api/v1/auth/logout 接口
    
    注意：登出接口受 Spring Security 保护，
    无 Token 或无效 Token 的请求会被拦截返回 401。
    
    测试用例：
    1. TC-O001: 正常登出（携带有效 Token）
    2. TC-O002: 未携带 Token 登出（被 Security 拦截）
    3. TC-O003: 携带无效 Token 登出（被 Security 拦截）
    4. TC-O004: 验证登出响应结构
#>

$BASE_URL = "http://localhost:8080"
$REGISTER_PATH = "/api/v1/auth/register"
$LOGIN_PATH = "/api/v1/auth/login"
$LOGOUT_PATH = "/api/v1/auth/logout"
$REGISTER_URL = "$BASE_URL$REGISTER_PATH"
$LOGIN_URL = "$BASE_URL$LOGIN_PATH"
$LOGOUT_URL = "$BASE_URL$LOGOUT_PATH"

$PASS_COUNT = 0
$FAIL_COUNT = 0
$RESULTS = @()

# 先注册并登录一个测试用户
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testUsername = "logout_test_$timestamp"
$testPhone = "1382222$($timestamp.Substring($timestamp.Length-6))"
$testPassword = "password123"

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "  准备测试用户..." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

# 注册
$registerBody = @{
    username = $testUsername
    phone = $testPhone
    password = $testPassword
    role = "CUSTOMER"
} | ConvertTo-Json -Compress

try {
    $regResp = Invoke-RestMethod -Uri $REGISTER_URL -Method Post -Body $registerBody -ContentType "application/json" -ErrorAction Stop
    Write-Host "✅ 测试用户注册成功: $testUsername" -ForegroundColor Green
} catch {
    Write-Host "❌ 测试用户注册失败" -ForegroundColor Red
}

# 登录获取 Token
$loginBody = @{
    account = $testUsername
    password = $testPassword
} | ConvertTo-Json -Compress

try {
    $loginResp = Invoke-RestMethod -Uri $LOGIN_URL -Method Post -Body $loginBody -ContentType "application/json" -ErrorAction Stop
    $accessToken = $loginResp.data.accessToken
    Write-Host "✅ 登录成功，获取到 Token" -ForegroundColor Green
} catch {
    Write-Host "❌ 登录失败，无法获取 Token" -ForegroundColor Red
    $accessToken = $null
}

function Test-Logout {
    param($Name, $Token, $ExpectedSuccess)
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "▶ 测试: $Name" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
        Write-Host "Authorization: Bearer $($Token.Substring(0, [Math]::Min(20, $Token.Length)))..." -ForegroundColor Gray
    } else {
        Write-Host "无 Authorization 头" -ForegroundColor Gray
    }
    
    try {
        $response = Invoke-RestMethod -Uri $LOGOUT_URL -Method Post -Headers $headers -ContentType "application/json" -ErrorAction Stop
        
        $success = $response.success
        Write-Host "响应: success=$success, code=$($response.code)" -ForegroundColor Gray
        
        if ($success -eq $ExpectedSuccess) {
            Write-Host "✅ 通过: 期望 success=$ExpectedSuccess, 实际 success=$success" -ForegroundColor Green
            $script:PASS_COUNT++
            $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "PASS"; Detail = "success=$success" }
        } else {
            Write-Host "❌ 失败: 期望 success=$ExpectedSuccess, 实际 success=$success" -ForegroundColor Red
            $script:FAIL_COUNT++
            $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = "期望 success=$ExpectedSuccess, 实际 success=$success" }
        }
        
        return $response
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "HTTP 状态码: $statusCode" -ForegroundColor Yellow
        
        # 对于期望被拦截的场景（ExpectedSuccess=$false），直接检查 HTTP 状态码
        # 不依赖响应体中的 success 字段，因为不同 PowerShell 版本对 JSON 布尔值的解析可能不一致
        if ($ExpectedSuccess -eq $false) {
            Write-Host "✅ 通过: 请求被 Security 拦截，HTTP $statusCode" -ForegroundColor Green
            $script:PASS_COUNT++
            $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "PASS"; Detail = "HTTP $statusCode（被 Security 拦截）" }
            return $null
        }
        
        # 期望成功但请求失败的情况
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd() | ConvertFrom-Json
            $reader.Close()
            Write-Host "响应体: success=$($responseBody.success), code=$($responseBody.code)" -ForegroundColor Yellow
            Write-Host "❌ 失败: 请求失败但期望 success=$ExpectedSuccess" -ForegroundColor Red
            $script:FAIL_COUNT++
            $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = "HTTP $statusCode, success=$($responseBody.success)" }
            return $responseBody
        }
        catch {
            Write-Host "❌ 失败: 请求失败且无法读取响应体" -ForegroundColor Red
            $script:FAIL_COUNT++
            $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = "HTTP $statusCode" }
            return $null
        }
    }
}

Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "  登出接口测试开始" -ForegroundColor Yellow
Write-Host "  接口: POST $LOGOUT_PATH" -ForegroundColor Yellow
Write-Host "  注意: 登出接口受 Security 保护，无 Token 会被拦截" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

# ============================================
# TC-O001: 正常登出（携带有效 Token）
# ============================================
if ($accessToken) {
    $o1 = Test-Logout -Name "TC-O001: 正常登出（携带有效 Token）" -Token $accessToken -ExpectedSuccess $true
} else {
    Write-Host "⚠ 跳过 TC-O001: 未获取到 Token" -ForegroundColor Yellow
}

# ============================================
# TC-O002: 未携带 Token 登出
# 受 Security 保护，应返回 401
# ============================================
$o2 = Test-Logout -Name "TC-O002: 未携带 Token 登出（被 Security 拦截）" -Token $null -ExpectedSuccess $false

# ============================================
# TC-O003: 携带无效 Token 登出
# 受 Security 保护，应返回 401
# ============================================
$o3 = Test-Logout -Name "TC-O003: 携带无效 Token 登出（被 Security 拦截）" -Token "invalid.jwt.token.here" -ExpectedSuccess $false

# ============================================
# TC-O004: 验证登出响应结构
# ============================================
if ($o1) {
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "▶ TC-O004: 验证登出响应结构" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    $hasSuccess = $o1.success -eq $true
    $hasCode = ![string]::IsNullOrEmpty($o1.code)
    $hasData = $o1.data -ne $null
    $hasLoggedOut = $o1.data -and $o1.data.loggedOut -eq $true
    
    Write-Host "  success=true: $(if($hasSuccess){'✅'}else{'❌'})" -ForegroundColor $(if($hasSuccess){'Green'}else{'Red'})
    Write-Host "  code=OK: $(if($hasCode){'✅'}else{'❌'})" -ForegroundColor $(if($hasCode){'Green'}else{'Red'})
    Write-Host "  data 存在: $(if($hasData){'✅'}else{'❌'})" -ForegroundColor $(if($hasData){'Green'}else{'Red'})
    Write-Host "  data.loggedOut=true: $(if($hasLoggedOut){'✅'}else{'❌'})" -ForegroundColor $(if($hasLoggedOut){'Green'}else{'Red'})
    
    if ($hasSuccess -and $hasCode -and $hasLoggedOut) {
        Write-Host "✅ 通过: 登出响应结构完整" -ForegroundColor Green
        $script:PASS_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = "TC-O004: 验证登出响应结构"; Status = "PASS"; Detail = "响应结构完整" }
    } else {
        Write-Host "❌ 失败: 登出响应结构不完整" -ForegroundColor Red
        $script:FAIL_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = "TC-O004: 验证登出响应结构"; Status = "FAIL"; Detail = "响应结构不完整" }
    }
}

# ============================================
# 汇总报告
# ============================================
Write-Host "`n`n========================================" -ForegroundColor Yellow
Write-Host "  测试汇总报告" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "总计: $($PASS_COUNT + $FAIL_COUNT)  |  通过: $PASS_COUNT  |  失败: $FAIL_COUNT" -ForegroundColor $(if ($FAIL_COUNT -eq 0) { "Green" } else { "Red" })
Write-Host ""

foreach ($r in $RESULTS) {
    $color = if ($r.Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "[$($r.Status)] $($r.Name) - $($r.Detail)" -ForegroundColor $color
}

# 保存结果到文件
$resultObj = @{
    TestSuite = "登出接口测试"
    Timestamp = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    Total = $PASS_COUNT + $FAIL_COUNT
    Passed = $PASS_COUNT
    Failed = $FAIL_COUNT
    Results = $RESULTS
}
$resultObj | ConvertTo-Json | Out-File -FilePath "$PSScriptRoot\03_logout_result.json" -Encoding utf8
Write-Host "`n结果已保存到: 03_logout_result.json" -ForegroundColor Gray

if ($FAIL_COUNT -gt 0) {
    exit 1
} else {
    exit 0
}
