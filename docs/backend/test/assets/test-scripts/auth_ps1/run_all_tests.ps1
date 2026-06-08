<#
    run_all_tests.ps1 - 一键运行所有 Auth 模块测试
    依次执行所有测试脚本并汇总结果
#>

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$TIMESTAMP = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$ALL_RESULTS = @()
$TOTAL_PASS = 0
$TOTAL_FAIL = 0
$TOTAL_TESTS = 0

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Auth 模块 - 全量自动化测试套件" -ForegroundColor Cyan
Write-Host "  开始时间: $TIMESTAMP" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 测试脚本列表
$testScripts = @(
    @{ Name = "注册接口测试"; File = "01_register_test.ps1"; ResultFile = "01_register_result.json" },
    @{ Name = "登录接口测试"; File = "02_login_test.ps1"; ResultFile = "02_login_result.json" },
    @{ Name = "登出接口测试"; File = "03_logout_test.ps1"; ResultFile = "03_logout_result.json" },
    @{ Name = "完整业务流程测试"; File = "04_auth_flow_test.ps1"; ResultFile = "04_auth_flow_result.json" }
)

foreach ($script in $testScripts) {
    $scriptPath = Join-Path $SCRIPT_DIR $script.File
    
    if (-not (Test-Path $scriptPath)) {
        Write-Host "⚠ 跳过: $($script.Name) - 文件不存在: $($script.File)" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "────────────────────────────────────────────────" -ForegroundColor Gray
    Write-Host "▶ 正在执行: $($script.Name) ($($script.File))" -ForegroundColor White
    Write-Host "────────────────────────────────────────────────" -ForegroundColor Gray
    
    $startTime = Get-Date
    
    try {
        # 执行测试脚本
        $output = & $scriptPath 2>&1
        $exitCode = $LASTEXITCODE
        
        $elapsed = (Get-Date) - $startTime
        
        # 输出脚本执行结果
        $output | ForEach-Object { Write-Host $_ }
        
        # 读取结果文件
        $resultFile = Join-Path $SCRIPT_DIR $script.ResultFile
        if (Test-Path $resultFile) {
            $resultData = Get-Content $resultFile -Raw | ConvertFrom-Json
            $ALL_RESULTS += $resultData
            $TOTAL_PASS += $resultData.Passed
            $TOTAL_FAIL += $resultData.Failed
            $TOTAL_TESTS += $resultData.Total
        } else {
            Write-Host "  ⚠ 结果文件未找到: $($script.ResultFile)" -ForegroundColor Yellow
        }
        
        if ($exitCode -eq 0) {
            Write-Host "  ✅ $($script.Name) 执行完毕 (耗时: $($elapsed.TotalSeconds.ToString('F2'))s)" -ForegroundColor Green
        } else {
            Write-Host "  ⚠ $($script.Name) 执行完毕，存在失败用例 (耗时: $($elapsed.TotalSeconds.ToString('F2'))s)" -ForegroundColor Yellow
        }
    }
    catch {
        Write-Host "  ❌ $($script.Name) 执行异常: $_" -ForegroundColor Red
    }
    
    Write-Host ""
}

# ============================================
# 最终汇总报告
# ============================================
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  最终测试汇总报告" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

$endTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
Write-Host "  完成时间: $endTime" -ForegroundColor Gray
Write-Host ""

if ($TOTAL_TESTS -gt 0) {
    $passRate = [math]::Round($TOTAL_PASS / $TOTAL_TESTS * 100, 1)
    Write-Host "  总用例数: $TOTAL_TESTS" -ForegroundColor White
    Write-Host "  通过: $TOTAL_PASS" -ForegroundColor Green
    Write-Host "  失败: $TOTAL_FAIL" -ForegroundColor $(if ($TOTAL_FAIL -eq 0) { "Green" } else { "Red" })
    Write-Host "  通过率: $passRate%" -ForegroundColor $(if ($passRate -eq 100) { "Green" } else { "Yellow" })
} else {
    Write-Host "  没有执行任何测试用例" -ForegroundColor Yellow
}

Write-Host ""

# 按测试套件输出
foreach ($result in $ALL_RESULTS) {
    $color = if ($result.Failed -eq 0) { "Green" } else { "Red" }
    Write-Host "  [$($result.TestSuite)] $($result.Passed)/$($result.Total) 通过" -ForegroundColor $color
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan

# 保存最终汇总结果
$summary = @{
    TestSuite = "Auth 模块全量测试"
    StartTime = $TIMESTAMP
    EndTime = $endTime
    Total = $TOTAL_TESTS
    Passed = $TOTAL_PASS
    Failed = $TOTAL_FAIL
    PassRate = if ($TOTAL_TESTS -gt 0) { [math]::Round($TOTAL_PASS / $TOTAL_TESTS * 100, 1) } else { 0 }
    Details = $ALL_RESULTS
}
$summary | ConvertTo-Json -Depth 3 | Out-File -FilePath "$SCRIPT_DIR\all_tests_summary.json" -Encoding utf8
Write-Host "`n汇总结果已保存到: all_tests_summary.json" -ForegroundColor Gray

if ($TOTAL_FAIL -gt 0) {
    exit 1
} else {
    exit 0
}
