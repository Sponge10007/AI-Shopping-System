<#
    04_auth_flow_test.ps1 - 完整业务流程测试
    模拟真实用户操作流程：
    1. 注册新用户
    2. 使用用户名登录
    3. 使用手机号登录
    4. 使用 Token 访问需要认证的接口
    5. 使用过期/无效 Token 访问（应被拒绝）
    6. 登出
    7. 重复注册（应失败）
    8. 测试 MERCHANT 角色注册和登录
#>

$BASE_URL = "http://localhost:8080"
$REGISTER_URL = "$BASE_URL/api/v1/auth/register"
$LOGIN_URL = "$BASE_URL/api/v1/auth/login"
$LOGOUT_URL = "$BASE_URL/api/v1/auth/logout"

$PASS_COUNT = 0
$FAIL_COUNT = 0
$RESULTS = @()

$timestamp = Get-Date -Format "yyyyMMddHHmmss"

function Write-Step {
    param($Message)
    Write-Host "`n========================================" -ForegroundColor Magenta
    Write-Host "  $Message" -ForegroundColor Magenta
    Write-Host "========================================" -ForegroundColor Magenta
}

function Invoke-Api {
    param($Url, $Method = "Post", $Body, $Headers = @{}, $ExpectedSuccess)
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            ContentType = "application/json"
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) {
            $params["Body"] = $Body | ConvertTo-Json -Compress
        }
        
        $response = Invoke-RestMethod @params
        return @{ Success = $true; Response = $response }
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd() | ConvertFrom-Json
            $reader.Close()
            return @{ Success = $false; StatusCode = $statusCode; Response = $responseBody }
        }
        catch {
            return @{ Success = $false; StatusCode = $statusCode; Response = $null }
        }
    }
}

function Assert-Test {
    param($Name, $Condition, $Detail)
    
    if ($Condition) {
        Write-Host "  ✅ 通过: $Name" -ForegroundColor Green
        $script:PASS_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "PASS"; Detail = $Detail }
    } else {
        Write-Host "  ❌ 失败: $Name" -ForegroundColor Red
        $script:FAIL_COUNT++
        $script:RESULTS += [PSCustomObject]@{ Name = $Name; Status = "FAIL"; Detail = $Detail }
    }
}

Write-Host "================================================" -ForegroundColor Yellow
Write-Host "  Auth 模块完整业务流程测试" -ForegroundColor Yellow
Write-Host "================================================" -ForegroundColor Yellow

# ============================================
# 场景 1: 注册 CUSTOMER 用户
# ============================================
Write-Step "场景 1: 注册 CUSTOMER 用户"

$customerUser = "flow_customer_$timestamp"
$customerPhone = "1383333$($timestamp.Substring($timestamp.Length-6))"
$customerPwd = "password123"

$r1 = Invoke-Api -Url $REGISTER_URL -Body @{
    username = $customerUser
    phone = $customerPhone
    password = $customerPwd
    role = "CUSTOMER"
}

Assert-Test -Name "F1.1 CUSTOMER 注册成功" -Condition ($r1.Success -and $r1.Response.success -eq $true) -Detail "username=$customerUser"
$customerUserId = if ($r1.Success) { $r1.Response.data.userId } else { $null }
Assert-Test -Name "F1.2 返回 userId" -Condition ($customerUserId -ne $null) -Detail "userId=$customerUserId"
Assert-Test -Name "F1.3 返回 role=CUSTOMER" -Condition ($r1.Success -and $r1.Response.data.role -eq "CUSTOMER") -Detail "role=$($r1.Response.data.role)"
Assert-Test -Name "F1.4 userId 以 u 开头" -Condition ($customerUserId -and $customerUserId.StartsWith("u")) -Detail "userId=$customerUserId"

# ============================================
# 场景 2: 使用用户名登录
# ============================================
Write-Step "场景 2: 使用用户名登录"

$l1 = Invoke-Api -Url $LOGIN_URL -Body @{
    account = $customerUser
    password = $customerPwd
}

Assert-Test -Name "F2.1 用户名登录成功" -Condition ($l1.Success -and $l1.Response.success -eq $true) -Detail "account=$customerUser"

$accessToken = if ($l1.Success) { $l1.Response.data.accessToken } else { $null }
$refreshToken = if ($l1.Success) { $l1.Response.data.refreshToken } else { $null }
$expiresIn = if ($l1.Success) { $l1.Response.data.expiresIn } else { $null }

Assert-Test -Name "F2.2 返回 accessToken" -Condition ($accessToken -ne $null) -Detail "长度=$($accessToken.Length)"
Assert-Test -Name "F2.3 返回 refreshToken" -Condition ($refreshToken -ne $null) -Detail "长度=$($refreshToken.Length)"
Assert-Test -Name "F2.4 expiresIn = 7200" -Condition ($expiresIn -eq 7200) -Detail "expiresIn=$expiresIn"
Assert-Test -Name "F2.5 返回用户信息" -Condition ($l1.Success -and $l1.Response.data.user -ne $null) -Detail "user=$($l1.Response.data.user | ConvertTo-Json -Compress)"
Assert-Test -Name "F2.6 用户信息中 userId 匹配" -Condition ($l1.Success -and $l1.Response.data.user.userId -eq $customerUserId) -Detail "userId=$customerUserId"

# ============================================
# 场景 3: 使用手机号登录
# ============================================
Write-Step "场景 3: 使用手机号登录"

$l2 = Invoke-Api -Url $LOGIN_URL -Body @{
    account = $customerPhone
    password = $customerPwd
}

Assert-Test -Name "F3.1 手机号登录成功" -Condition ($l2.Success -and $l2.Response.success -eq $true) -Detail "account=$customerPhone"
Assert-Test -Name "F3.2 手机号登录返回相同 userId" -Condition ($l2.Success -and $l2.Response.data.user.userId -eq $customerUserId) -Detail "userId=$customerUserId"

# ============================================
# 场景 4: 使用 Token 访问登出接口
# 注意：登出接口受 Spring Security 保护
# ============================================
Write-Step "场景 4: 使用 Token 访问登出接口"

# 携带有效 Token 登出
$logoutWithToken = Invoke-Api -Url $LOGOUT_URL -Headers @{ "Authorization" = "Bearer $accessToken" }
Assert-Test -Name "F4.1 携带有效 Token 登出成功" -Condition ($logoutWithToken.Success -and $logoutWithToken.Response.success -eq $true) -Detail ""

# 不携带 Token 登出（被 Security 拦截，返回 401）
$logoutNoToken = Invoke-Api -Url $LOGOUT_URL
Assert-Test -Name "F4.2 不携带 Token 登出被拦截" -Condition ($logoutNoToken.Success -eq $false -and $logoutNoToken.StatusCode -eq 401) -Detail "statusCode=$($logoutNoToken.StatusCode)"

# 携带无效 Token 登出（被 Security 拦截，返回 401）
$logoutBadToken = Invoke-Api -Url $LOGOUT_URL -Headers @{ "Authorization" = "Bearer invalid.jwt.token" }
Assert-Test -Name "F4.3 携带无效 Token 登出被拦截" -Condition ($logoutBadToken.Success -eq $false -and $logoutBadToken.StatusCode -eq 401) -Detail "statusCode=$($logoutBadToken.StatusCode)"

# ============================================
# 场景 5: 错误密码登录
# ============================================
Write-Step "场景 5: 错误密码登录"

$wrongPwd = Invoke-Api -Url $LOGIN_URL -Body @{
    account = $customerUser
    password = "wrong_password"
}
Assert-Test -Name "F5.1 错误密码登录失败" -Condition ($wrongPwd.Success -eq $false) -Detail "statusCode=$($wrongPwd.StatusCode)"
if ($wrongPwd.Response) {
    Assert-Test -Name "F5.2 返回错误码 UNAUTHORIZED" -Condition ($wrongPwd.Response.code -eq "UNAUTHORIZED") -Detail "code=$($wrongPwd.Response.code)"
}

# ============================================
# 场景 6: 不存在的用户登录
# ============================================
Write-Step "场景 6: 不存在的用户登录"

$notExist = Invoke-Api -Url $LOGIN_URL -Body @{
    account = "nonexistent_user_$timestamp"
    password = "password123"
}
Assert-Test -Name "F6.1 不存在的用户登录失败" -Condition ($notExist.Success -eq $false) -Detail "statusCode=$($notExist.StatusCode)"
if ($notExist.Response) {
    Assert-Test -Name "F6.2 返回错误码 RESOURCE_NOT_FOUND" -Condition ($notExist.Response.code -eq "RESOURCE_NOT_FOUND") -Detail "code=$($notExist.Response.code)"
}

# ============================================
# 场景 7: 重复注册
# ============================================
Write-Step "场景 7: 重复注册"

$dupUser = Invoke-Api -Url $REGISTER_URL -Body @{
    username = $customerUser
    phone = "1394444$($timestamp.Substring($timestamp.Length-6))"
    password = "password123"
    role = "CUSTOMER"
}
Assert-Test -Name "F7.1 重复用户名注册失败" -Condition ($dupUser.Success -eq $false) -Detail "statusCode=$($dupUser.StatusCode)"
if ($dupUser.Response) {
    Assert-Test -Name "F7.2 返回错误码 DUPLICATE_RESOURCE" -Condition ($dupUser.Response.code -eq "DUPLICATE_RESOURCE") -Detail "code=$($dupUser.Response.code)"
}

$dupPhone = Invoke-Api -Url $REGISTER_URL -Body @{
    username = "another_user_$timestamp"
    phone = $customerPhone
    password = "password123"
    role = "CUSTOMER"
}
Assert-Test -Name "F7.3 重复手机号注册失败" -Condition ($dupPhone.Success -eq $false) -Detail "statusCode=$($dupPhone.StatusCode)"
if ($dupPhone.Response) {
    Assert-Test -Name "F7.4 返回错误码 DUPLICATE_RESOURCE" -Condition ($dupPhone.Response.code -eq "DUPLICATE_RESOURCE") -Detail "code=$($dupPhone.Response.code)"
}

# ============================================
# 场景 8: MERCHANT 角色注册和登录
# ============================================
Write-Step "场景 8: MERCHANT 角色注册和登录"

$merchantUser = "flow_merchant_$timestamp"
$merchantPhone = "1385555$($timestamp.Substring($timestamp.Length-6))"
$merchantPwd = "merchant123"

$m1 = Invoke-Api -Url $REGISTER_URL -Body @{
    username = $merchantUser
    phone = $merchantPhone
    password = $merchantPwd
    role = "MERCHANT"
}

Assert-Test -Name "F8.1 MERCHANT 注册成功" -Condition ($m1.Success -and $m1.Response.success -eq $true) -Detail "username=$merchantUser"
$merchantUserId = if ($m1.Success) { $m1.Response.data.userId } else { $null }
Assert-Test -Name "F8.2 MERCHANT 的 userId 以 m 开头" -Condition ($merchantUserId -and $merchantUserId.StartsWith("m")) -Detail "userId=$merchantUserId"
Assert-Test -Name "F8.3 MERCHANT 的 role=MERCHANT" -Condition ($m1.Success -and $m1.Response.data.role -eq "MERCHANT") -Detail "role=$($m1.Response.data.role)"

# MERCHANT 登录
$m2 = Invoke-Api -Url $LOGIN_URL -Body @{
    account = $merchantUser
    password = $merchantPwd
}
Assert-Test -Name "F8.4 MERCHANT 登录成功" -Condition ($m2.Success -and $m2.Response.success -eq $true) -Detail ""
Assert-Test -Name "F8.5 MERCHANT 登录返回 role=MERCHANT" -Condition ($m2.Success -and $m2.Response.data.user.role -eq "MERCHANT") -Detail "role=$($m2.Response.data.user.role)"

# ============================================
# 场景 9: 缺少必填字段
# ============================================
Write-Step "场景 9: 缺少必填字段"

$noUsername = Invoke-Api -Url $REGISTER_URL -Body @{
    phone = "1386666$($timestamp.Substring($timestamp.Length-6))"
    password = "password123"
    role = "CUSTOMER"
}
Assert-Test -Name "F9.1 注册缺少 username 失败" -Condition ($noUsername.Success -eq $false) -Detail "statusCode=$($noUsername.StatusCode)"

$noPassword = Invoke-Api -Url $REGISTER_URL -Body @{
    username = "no_pwd_$timestamp"
    phone = "1387777$($timestamp.Substring($timestamp.Length-6))"
    role = "CUSTOMER"
}
Assert-Test -Name "F9.2 注册缺少 password 失败" -Condition ($noPassword.Success -eq $false) -Detail "statusCode=$($noPassword.StatusCode)"

$noAccount = Invoke-Api -Url $LOGIN_URL -Body @{
    password = "password123"
}
Assert-Test -Name "F9.3 登录缺少 account 失败" -Condition ($noAccount.Success -eq $false) -Detail "statusCode=$($noAccount.StatusCode)"

# ============================================
# 场景 10: 验证 JWT Token 内容
# ============================================
Write-Step "场景 10: 验证 JWT Token 内容"

if ($accessToken) {
    # JWT 是 base64 编码的，可以解码查看 payload
    $parts = $accessToken.Split('.')
    if ($parts.Count -eq 3) {
        # 解码 payload（第二部分）
        $payload = $parts[1]
        # 添加 padding
        $padding = 4 - ($payload.Length % 4)
        if ($padding -ne 4) {
            $payload = $payload.PadRight($payload.Length + $padding, '=')
        }
        $payloadBytes = [Convert]::FromBase64String($payload)
        $payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
        Write-Host "  Token Payload: $payloadJson" -ForegroundColor Gray
        
        $payloadObj = $payloadJson | ConvertFrom-Json
        $hasSub = ![string]::IsNullOrEmpty($payloadObj.sub)
        $hasRole = ![string]::IsNullOrEmpty($payloadObj.role)
        $hasIat = $payloadObj.iat -gt 0
        $hasExp = $payloadObj.exp -gt 0
        
        Assert-Test -Name "F10.1 Token 包含 sub (userId)" -Condition $hasSub -Detail "sub=$($payloadObj.sub)"
        Assert-Test -Name "F10.2 Token 包含 role" -Condition $hasRole -Detail "role=$($payloadObj.role)"
        Assert-Test -Name "F10.3 Token 包含 iat (签发时间)" -Condition $hasIat -Detail "iat=$($payloadObj.iat)"
        Assert-Test -Name "F10.4 Token 包含 exp (过期时间)" -Condition $hasExp -Detail "exp=$($payloadObj.exp)"
        Assert-Test -Name "F10.5 Token 中 sub 与 userId 一致" -Condition ($payloadObj.sub -eq $customerUserId) -Detail "sub=$($payloadObj.sub), userId=$customerUserId"
        Assert-Test -Name "F10.6 Token 中 role 为 CUSTOMER" -Condition ($payloadObj.role -eq "CUSTOMER") -Detail "role=$($payloadObj.role)"
        
        # 验证过期时间 = 当前时间 + 7200秒（允许小误差）
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $expectedExp = $payloadObj.iat + 7200
        Assert-Test -Name "F10.7 Token 过期时间 = iat + 7200s" -Condition ($payloadObj.exp -eq $expectedExp) -Detail "exp=$($payloadObj.exp), iat+7200=$expectedExp"
    } else {
        Assert-Test -Name "F10 JWT 格式验证" -Condition $false -Detail "不是标准 JWT 三段式"
    }
}

# ============================================
# 汇总报告
# ============================================
Write-Host "`n`n================================================" -ForegroundColor Yellow
Write-Host "  完整业务流程测试汇总报告" -ForegroundColor Yellow
Write-Host "================================================" -ForegroundColor Yellow
Write-Host "总计: $($PASS_COUNT + $FAIL_COUNT)  |  通过: $PASS_COUNT  |  失败: $FAIL_COUNT" -ForegroundColor $(if ($FAIL_COUNT -eq 0) { "Green" } else { "Red" })
Write-Host ""

foreach ($r in $RESULTS) {
    $color = if ($r.Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "[$($r.Status)] $($r.Name) - $($r.Detail)" -ForegroundColor $color
}

# 保存结果到文件
$resultObj = @{
    TestSuite = "Auth 完整业务流程测试"
    Timestamp = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    Total = $PASS_COUNT + $FAIL_COUNT
    Passed = $PASS_COUNT
    Failed = $FAIL_COUNT
    Results = $RESULTS
}
$resultObj | ConvertTo-Json -Depth 3 | Out-File -FilePath "$PSScriptRoot\04_auth_flow_result.json" -Encoding utf8
Write-Host "`n结果已保存到: 04_auth_flow_result.json" -ForegroundColor Gray

if ($FAIL_COUNT -gt 0) {
    exit 1
} else {
    exit 0
}
