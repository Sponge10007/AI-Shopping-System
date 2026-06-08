<#
    02_login_test.ps1 - 登录接口测试
    测试 Auth 模块的 POST /api/v1/auth/login 接口
    
    测试用例：
    1. TC-L001: 使用用户名正常登录
    2. TC-L002: 使用手机号正常登录
    3. TC-L003: 密码错误登录（应返回错误）
    4. TC-L004: 用户不存在登录（应返回错误）
    5. TC-L005: 缺少必填字段（应返回错误）
    6. TC-L006: 验证登录返回的 Token 格式和响应结构
#>

$BASE_URL = "http://localhost:8080"
$REGISTER_PATH = "/api/v1/auth/register"
$LOGIN_PATH = "/api/v1/auth/login"
$REGISTER_URL = "$BASE_URL$REGISTER_PATH"
$LOGIN_URL = "$BASE_URL$LOGIN_PATH"

$PASS_COUNT = 0
$FAIL_COUNT = 0
$RESULTS = @()

# 先注册一个测试用户用于登录测试
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testUsername = "login_test_$timestamp"
$testPhone = "1381111$($timestamp.Substring($timestamp.Length-6))"
$testPassword = "password123"

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "  准备测试用户..." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$registerBody = @{
    username = $testUsername
    phone = $testPhone
    password = $testPassword
    role = "CUSTOMER"
} | ConvertTo-Json -Compress

try {
    $regResp = Invoke-RestMethod -Uri $REGISTER_URL -Method Post -Body $registerBody -ContentType "application/json" -ErrorAction Stop
    Write-Host "✅ 测试用户注册成功: username=$testUsername, userId=$($regResp.data.userId)" -ForegroundColor Green
    $testUserId = $regResp.data.userId
} catch {
    Write-Host "❌ 测试用户注册失败，后续登录测试可能受影响" -ForegroundColor Red
    $testUserId = $null
}

function Test-Step {
    param($Name, $Body, $ExpectedSuccess)
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "▶ 测试: $Name" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    try {
        $jsonBody = $Body | ConvertTo-Json -Compress
        Write-Host "请求体: $jsonBody" -ForegroundColor Gray
        
        $response = Invoke-RestMethod -Uri $LOGIN_URL -Method Post -Body $jsonBody -ContentType "application/json" -ErrorAction Stop
        
        $success = $response.success
        $code = $response.code
        
        Write-Host "响应: success=$success, code=$code" -ForegroundColor Gray
        
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
        
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd() | ConvertFrom-Json
            $reader.Close()
            Write-Host "响应体: success=$($responseBody.success), code=$($responseBody.code)" -ForegroundColor Yellow
            
            if ($responseBody.success -eq $ExpectedSuccess) {
                Write-Host "✅ 通过: 期望 success=$ExpectedSuccess, 实际 success=$($responseBody.success)" -ForegroundColor Green
                $script:PASS_COUNT++
                $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "PASS"; Detail = "success=$($responseBody.success), code=$($responseBody.code)" }
            } else {
                Write-Host "❌ 失败: 期望 success=$ExpectedSuccess, 实际 success=$($responseBody.success)" -ForegroundColor Red
                $script:FAIL_COUNT++
                $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = "期望 success=$ExpectedSuccess, 实际 success=$($responseBody.success)" }
            }
            return $responseBody
        }
        catch {
            if ($ExpectedSuccess -eq $false) {
                Write-Host "✅ 通过: 请求失败（期望的错误场景）" -ForegroundColor Green
                $script:PASS_COUNT++
                $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "PASS"; Detail = "HTTP $statusCode" }
            } else {
                Write-Host "❌ 失败: 请求失败但期望成功" -ForegroundColor Red
                $script:FAIL_COUNT++
                $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = "HTTP $statusCode" }
            }
            return $null
        }
    }
}

Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "  登录接口测试开始" -ForegroundColor Yellow
Write-Host "  接口: POST $LOGIN_PATH" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

# ============================================
# TC-L001: 使用用户名正常登录
# ============================================
$l1 = Test-Step -Name "TC-L001: 使用用户名正常登录" -Body @{
    account = $testUsername
    password = $testPassword
} -ExpectedSuccess $true

# 验证 Token 格式
if ($l1 -and $l1.data -and $l1.data.accessToken) {
    $token = $l1.data.accessToken
    $tokenParts = $token.Split('.')
    if ($tokenParts.Count -eq 3) {
        Write-Host "  ✅ Token 格式正确（JWT 三段式）" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ Token 格式异常，不是标准 JWT" -ForegroundColor Yellow
    }
    Write-Host "  过期时间: $($l1.data.expiresIn) 秒" -ForegroundColor Gray
    if ($l1.data.user) {
        Write-Host "  用户信息: userId=$($l1.data.user.userId), username=$($l1.data.user.username), role=$($l1.data.user.role)" -ForegroundColor Gray
    }
}

# ============================================
# TC-L002: 使用手机号正常登录
# ============================================
$l2 = Test-Step -Name "TC-L002: 使用手机号正常登录" -Body @{
    account = $testPhone
    password = $testPassword
} -ExpectedSuccess $true

# ============================================
# TC-L003: 密码错误登录
# ============================================
Test-Step -Name "TC-L003: 密码错误登录" -Body @{
    account = $testUsername
    password = "wrong_password_123"
} -ExpectedSuccess $false

# ============================================
# TC-L004: 用户不存在登录
# ============================================
Test-Step -Name "TC-L004: 用户不存在登录" -Body @{
    account = "nonexistent_user_$timestamp"
    password = $testPassword
} -ExpectedSuccess $false

# ============================================
# TC-L005: 缺少必填字段（无 account）
# ============================================
Test-Step -Name "TC-L005: 缺少必填字段（无 account）" -Body @{
    password = $testPassword
} -ExpectedSuccess $false

# ============================================
# TC-L006: 验证登录返回的 Token 格式和响应结构
# ============================================
if ($l1 -and $l1.data) {
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "▶ TC-L006: 验证 Token 格式和响应结构" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    $hasAccessToken = ![string]::IsNullOrEmpty($l1.data.accessToken)
    $hasRefreshToken = ![string]::IsNullOrEmpty($l1.data.refreshToken)
    $hasExpiresIn = $l1.data.expiresIn -gt 0
    $hasUserInfo = $l1.data.user -ne $null
    
    # 验证 JWT 格式（三段式）
    $token = $l1.data.accessToken
    $isValidJwt = ($token.Split('.').Count -eq 3)
    
    # 验证用户信息字段
    $hasUserId = $l1.data.user -and ![string]::IsNullOrEmpty($l1.data.user.userId)
    $hasUsername = $l1.data.user -and ![string]::IsNullOrEmpty($l1.data.user.username)
    $hasRole = $l1.data.user -and ![string]::IsNullOrEmpty($l1.data.user.role)
    
    Write-Host "  accessToken: $(if($hasAccessToken){'✅'}else{'❌'})" -ForegroundColor $(if($hasAccessToken){'Green'}else{'Red'})
    Write-Host "  refreshToken: $(if($hasRefreshToken){'✅'}else{'❌'})" -ForegroundColor $(if($hasRefreshToken){'Green'}else{'Red'})
    Write-Host "  JWT 格式: $(if($isValidJwt){'✅'}else{'❌'})" -ForegroundColor $(if($isValidJwt){'Green'}else{'Red'})
    Write-Host "  expiresIn: $(if($hasExpiresIn){'✅'}else{'❌'})" -ForegroundColor $(if($hasExpiresIn){'Green'}else{'Red'})
    Write-Host "  user.userId: $(if($hasUserId){'✅'}else{'❌'})" -ForegroundColor $(if($hasUserId){'Green'}else{'Red'})
    Write-Host "  user.username: $(if($hasUsername){'✅'}else{'❌'})" -ForegroundColor $(if($hasUsername){'Green'}else{'Red'})
    Write-Host "  user.role: $(if($hasRole){'✅'}else{'❌'})" -ForegroundColor $(if($hasRole){'Green'}else{'Red'})
    
    $allPass = $hasAccessToken -and $hasRefreshToken -and $isValidJwt -and $hasExpiresIn -and $hasUserId -and $hasUsername -and $hasRole
    
    if ($allPass) {
        Write-Host "✅ 通过: 登录响应包含所有必要字段，Token 格式正确" -ForegroundColor Green
        $script:PASS_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = "TC-L006: 验证 Token 格式和响应结构"; Status = "PASS"; Detail = "所有字段完整，JWT 格式正确" }
    } else {
        Write-Host "❌ 失败: 登录响应缺少必要字段" -ForegroundColor Red
        $script:FAIL_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = "TC-L006: 验证 Token 格式和响应结构"; Status = "FAIL"; Detail = "缺少字段" }
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
    TestSuite = "登录接口测试"
    Timestamp = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    Total = $PASS_COUNT + $FAIL_COUNT
    Passed = $PASS_COUNT
    Failed = $FAIL_COUNT
    Results = $RESULTS
}
$resultObj | ConvertTo-Json | Out-File -FilePath "$PSScriptRoot\02_login_result.json" -Encoding utf8
Write-Host "`n结果已保存到: 02_login_result.json" -ForegroundColor Gray

if ($FAIL_COUNT -gt 0) {
    exit 1
} else {
    exit 0
}
