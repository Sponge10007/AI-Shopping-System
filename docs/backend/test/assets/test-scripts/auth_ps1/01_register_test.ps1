<#
    01_register_test.ps1 - 注册接口测试
    测试 Auth 模块的 POST /api/v1/auth/register 接口
    
    测试用例：
    1. TC-R001: 正常注册 CUSTOMER 用户
    2. TC-R002: 正常注册 MERCHANT 用户
    3. TC-R003: 重复用户名注册（应返回错误）
    4. TC-R004: 重复手机号注册（应返回错误）
    5. TC-R005: 缺少必填字段（应返回错误）
#>

$BASE_URL = "http://localhost:8080"
$API_PATH = "/api/v1/auth/register"
$URL = "$BASE_URL$API_PATH"

$PASS_COUNT = 0
$FAIL_COUNT = 0
$RESULTS = @()

function Test-Step {
    param($Name, $Body, $ExpectedStatus, $ExpectedSuccess)
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "▶ 测试: $Name" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    try {
        $jsonBody = $Body | ConvertTo-Json -Compress
        Write-Host "请求体: $jsonBody" -ForegroundColor Gray
        
        $response = Invoke-RestMethod -Uri $URL -Method Post -Body $jsonBody -ContentType "application/json" -ErrorAction Stop
        
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
        $errorMsg = $_.Exception.Message
        
        Write-Host "HTTP 状态码: $statusCode" -ForegroundColor Yellow
        Write-Host "错误信息: $errorMsg" -ForegroundColor Yellow
        
        # 尝试读取响应体
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

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "  注册接口测试开始" -ForegroundColor Yellow
Write-Host "  接口: POST $API_PATH" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

# ============================================
# TC-R001: 正常注册 CUSTOMER 用户
# ============================================
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$r1 = Test-Step -Name "TC-R001: 正常注册 CUSTOMER 用户" -Body @{
    username = "test_customer_$timestamp"
    phone = "1380000$($timestamp.Substring($timestamp.Length-6))"
    password = "password123"
    role = "CUSTOMER"
} -ExpectedSuccess $true

# ============================================
# TC-R002: 正常注册 MERCHANT 用户
# ============================================
$r2 = Test-Step -Name "TC-R002: 正常注册 MERCHANT 用户" -Body @{
    username = "test_merchant_$timestamp"
    phone = "1390000$($timestamp.Substring($timestamp.Length-6))"
    password = "password123"
    role = "MERCHANT"
} -ExpectedSuccess $true

# ============================================
# TC-R003: 重复用户名注册
# ============================================
if ($r1 -and $r1.data) {
    $dupUsername = $r1.data.username
    Test-Step -Name "TC-R003: 重复用户名注册" -Body @{
        username = $dupUsername
        phone = "1370000$($timestamp.Substring($timestamp.Length-6))"
        password = "password123"
        role = "CUSTOMER"
    } -ExpectedSuccess $false
} else {
    Write-Host "⚠ 跳过 TC-R003: 未获取到已注册的用户名" -ForegroundColor Yellow
}

# ============================================
# TC-R004: 重复手机号注册
# ============================================
if ($r1 -and $r1.data) {
    Test-Step -Name "TC-R004: 重复手机号注册" -Body @{
        username = "another_user_$timestamp"
        phone = "1380000$($timestamp.Substring($timestamp.Length-6))"
        password = "password123"
        role = "CUSTOMER"
    } -ExpectedSuccess $false
} else {
    Write-Host "⚠ 跳过 TC-R004: 未获取到已注册的手机号" -ForegroundColor Yellow
}

# ============================================
# TC-R005: 缺少必填字段（无 username）
# ============================================
Test-Step -Name "TC-R005: 缺少必填字段（无 username）" -Body @{
    phone = "1360000$($timestamp.Substring($timestamp.Length-6))"
    password = "password123"
    role = "CUSTOMER"
} -ExpectedSuccess $false

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
    TestSuite = "注册接口测试"
    Timestamp = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    Total = $PASS_COUNT + $FAIL_COUNT
    Passed = $PASS_COUNT
    Failed = $FAIL_COUNT
    Results = $RESULTS
}
$resultObj | ConvertTo-Json | Out-File -FilePath "$PSScriptRoot\01_register_result.json" -Encoding utf8
Write-Host "`n结果已保存到: 01_register_result.json" -ForegroundColor Gray

if ($FAIL_COUNT -gt 0) {
    exit 1
} else {
    exit 0
}
