#!/usr/bin/env python3
"""
run_all_tests.py - 订单模块全量测试运行脚本

【核心设计】脚本不负责启动 Maven，而是：
  1. 检查后端是否已在运行（用 curl）
  2. 如果未运行 → 提示用户在另一个终端手动启动，脚本进入等待检测模式
  3. 检测到后端就绪后自动开始测试
  4. 测试完成后提示用户可手动停止后端

用法：
    python run_all_tests.py              # 默认：等待后端就绪 → 运行全部测试
    python run_all_tests.py --skip-env   # 跳过等待，直接测试（假设后端已在运行）
    python run_all_tests.py --create     # 只运行创建测试
    python run_all_tests.py --query      # 只运行查询测试
    python run_all_tests.py --pay        # 只运行支付测试
    python run_all_tests.py --flow       # 只运行完整流程测试
"""
import sys
import os
import json
import subprocess
import time
from datetime import datetime
from pathlib import Path

# ============ 路径配置 ============
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[5]  # 回到 SE-Project 根目录
BACKEND_DIR = PROJECT_ROOT / "backend"

# ============ 配置 ============
BACKEND_PORT = 8080
BACKEND_URL = f"http://localhost:{BACKEND_PORT}"
HEALTH_URL = f"{BACKEND_URL}/actuator/health"

# 测试脚本列表
TEST_SCRIPTS = [
    {
        "name": "订单创建接口测试",
        "file": "01_create_order_test.py",
        "result_file": "01_create_order_result.json"
    },
    {
        "name": "订单查询接口测试",
        "file": "02_query_order_test.py",
        "result_file": "02_query_order_result.json"
    },
    {
        "name": "订单支付接口测试",
        "file": "03_pay_order_test.py",
        "result_file": "03_pay_order_result.json"
    },
    {
        "name": "订单模块完整业务流程测试",
        "file": "04_order_flow_test.py",
        "result_file": "04_order_flow_result.json"
    }
]


# ============================================================
#  工具函数
# ============================================================

def log(msg, level="INFO"):
    timestamp = datetime.now().strftime("[%H:%M:%S]")
    prefix = {"INFO": "", "WARN": "⚠️ ", "ERROR": "❌ "}.get(level, "")
    print(f"{timestamp} {prefix}{msg}")


def print_banner(text):
    width = 60
    print("\n" + "=" * width)
    print(f"  {text}")
    print("=" * width)


# ============================================================
#  后端检测函数（核心：使用 curl，不依赖 Python 库）
# ============================================================

def is_backend_running() -> bool:
    """
    使用 curl 检查后端是否正在运行。
    在 Windows 上必须使用 shell=True，因为 curl 不是 exe 而是通过 PATH 查找。
    """
    try:
        if sys.platform == "win32":
            cmd = f'curl -s -o NUL -w "%{{http_code}}" {HEALTH_URL}'
            result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=5)
            if result.returncode == 0:
                code = result.stdout.strip()
                return code == "200"
            return False
        else:
            result = subprocess.run(
                ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", HEALTH_URL],
                capture_output=True, text=True, timeout=5
            )
            return result.returncode == 0 and result.stdout.strip() == "200"
    except Exception:
        return False


def wait_for_backend(max_wait=120, check_interval=3) -> bool:
    """
    等待后端就绪。
    每 check_interval 秒用 curl 检查一次，最多等待 max_wait 秒。
    如果后端未运行，会提示用户手动启动。
    """
    if is_backend_running():
        log(f"后端已在运行 ({BACKEND_URL})")
        return True

    print()
    log("=" * 60)
    log("后端未检测到运行状态！")
    log("=" * 60)
    log("请在新终端中执行以下命令启动后端：")
    log("")
    log(f"  cd {BACKEND_DIR}")
    log("  mvn spring-boot:run")
    log("")
    log(f"脚本将每 {check_interval} 秒检测一次，最多等待 {max_wait} 秒...")
    log("=" * 60)
    print()

    start_time = time.time()
    while time.time() - start_time < max_wait:
        elapsed = int(time.time() - start_time)

        if is_backend_running():
            log(f"后端已就绪！({BACKEND_URL})")
            log(f"等待耗时: {elapsed} 秒")
            return True

        if elapsed % 6 == 0 or elapsed < 6:
            log(f"已等待 {elapsed} 秒，后端尚未就绪，继续等待...（每 {check_interval} 秒检测一次）")

        time.sleep(check_interval)

    log(f"等待超时（{max_wait} 秒），后端仍未就绪", "ERROR")
    log("请检查后端启动日志，确认是否有编译错误或端口冲突", "ERROR")
    return False


# ============================================================
#  测试运行函数
# ============================================================

def run_test(test_info):
    """运行单个测试脚本"""
    script_path = SCRIPT_DIR / test_info["file"]
    result_path = SCRIPT_DIR / test_info["result_file"]

    # 清理旧的测试结果文件
    if result_path.exists():
        result_path.unlink()

    print(f"\n▶ 正在执行: {test_info['name']}")
    print(f"  脚本: {test_info['file']}")
    print("-" * 40)

    start_time = time.time()

    try:
        result = subprocess.run(
            [sys.executable, str(script_path)],
            cwd=str(SCRIPT_DIR),
            capture_output=True,
            text=True,
            timeout=120
        )

        elapsed = time.time() - start_time

        # 打印测试输出
        if result.stdout:
            print(result.stdout)
        if result.stderr:
            print(f"  [stderr] {result.stderr}")

        # 读取测试结果文件
        suite_result = None
        if result_path.exists():
            try:
                with open(result_path, "r", encoding="utf-8") as f:
                    suite_result = json.load(f)
            except json.JSONDecodeError:
                pass

        passed = suite_result["Passed"] if suite_result else 0
        failed = suite_result["Failed"] if suite_result else 0
        total = suite_result["Total"] if suite_result else 0

        status = "✅ 通过" if result.returncode == 0 else "❌ 失败"
        print(f"\n  [{status}] {test_info['name']}")
        print(f"  耗时: {elapsed:.2f}s  |  总计: {total}  |  通过: {passed}  |  失败: {failed}")

        return {
            "name": test_info["name"],
            "status": "PASS" if result.returncode == 0 else "FAIL",
            "total": total,
            "passed": passed,
            "failed": failed,
            "elapsed": round(elapsed, 2),
            "result": suite_result
        }

    except subprocess.TimeoutExpired:
        print(f"\n  ⏰ 超时: {test_info['name']} (超过120秒)")
        return {
            "name": test_info["name"],
            "status": "TIMEOUT",
            "total": 0, "passed": 0, "failed": 0,
            "elapsed": 120, "result": None
        }
    except Exception as e:
        print(f"\n  💥 异常: {test_info['name']} - {str(e)}")
        return {
            "name": test_info["name"],
            "status": "ERROR",
            "total": 0, "passed": 0, "failed": 0,
            "elapsed": 0, "result": None
        }


def run_all_tests(selected_names=None):
    """运行所有（或选中的）测试"""
    results = []
    total_passed = 0
    total_failed = 0
    total_all = 0

    for test_info in TEST_SCRIPTS:
        if selected_names and test_info["name"] not in selected_names:
            continue
        result = run_test(test_info)
        results.append(result)
        if result["result"]:
            total_passed += result["passed"]
            total_failed += result["failed"]
            total_all += result["total"]

    return results, total_passed, total_failed, total_all


def print_summary(results, total_passed, total_failed, total_all, elapsed):
    """打印汇总报告"""
    print_banner("全量测试汇总报告")

    print(f"{'测试套件':<30} {'状态':<8} {'总计':<6} {'通过':<6} {'失败':<6} {'耗时':<8}")
    print("-" * 64)

    for r in results:
        status_icon = "✅" if r["status"] == "PASS" else ("⏰" if r["status"] == "TIMEOUT" else "❌")
        print(f"{r['name']:<30} {status_icon:<8} {r['total']:<6} {r['passed']:<6} {r['failed']:<6} {r['elapsed']:<8}s")

    print("-" * 64)
    pass_rate = round(total_passed / total_all * 100, 1) if total_all > 0 else 0
    print(f"{'总计':<30} {'':<8} {total_all:<6} {total_passed:<6} {total_failed:<6} {elapsed:<8.2f}s")
    print(f"\n通过率: {pass_rate}%")

    summary = {
        "TestSuite": "订单模块全量测试",
        "EndTime": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total_all,
        "Passed": total_passed,
        "Failed": total_failed,
        "PassRate": pass_rate,
        "Details": results
    }

    summary_path = SCRIPT_DIR / "all_tests_summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n汇总报告已保存到: all_tests_summary.json")


# ============================================================
#  主流程
# ============================================================

def main():
    skip_env = "--skip-env" in sys.argv
    run_all = True
    selected_tests = []

    arg_map = {
        "--create": "订单创建接口测试",
        "--query": "订单查询接口测试",
        "--pay": "订单支付接口测试",
        "--flow": "订单模块完整业务流程测试"
    }

    for arg in sys.argv[1:]:
        if arg == "--skip-env":
            continue
        if arg in arg_map:
            run_all = False
            selected_tests.append(arg_map[arg])

    overall_start = time.time()

    # ========== 模式1：跳过等待，直接测试 ==========
    if skip_env:
        print_banner("订单模块测试（跳过环境检测，直接测试）")

        if not is_backend_running():
            log("后端未运行！请先启动后端服务", "ERROR")
            log("提示: 使用 python run_all_tests.py（不带参数）会自动等待后端就绪", "INFO")
            return 1

        results, total_passed, total_failed, total_all = run_all_tests(
            selected_tests if not run_all else None
        )
        elapsed = time.time() - overall_start
        print_summary(results, total_passed, total_failed, total_all, elapsed)
        return 0 if total_failed == 0 else 1

    # ========== 模式2：默认 - 等待后端就绪 → 运行测试 ==========
    print_banner("订单模块自动化测试")
    log(f"项目根目录: {PROJECT_ROOT}")
    log(f"后端目录: {BACKEND_DIR}")
    log(f"测试目录: {SCRIPT_DIR}")
    log("")

    # 步骤 1: 等待后端就绪（提示用户手动启动 Maven）
    if not wait_for_backend():
        log("后端未就绪，终止流程", "ERROR")
        return 1

    # 步骤 2: 运行测试
    log("后端已就绪，开始执行测试...")
    results, total_passed, total_failed, total_all = run_all_tests(
        selected_tests if not run_all else None
    )

    total_time = int(time.time() - overall_start)
    print_summary(results, total_passed, total_failed, total_all, total_time)

    log(f"总耗时: {total_time} 秒")
    if total_failed == 0:
        log("✅ 全部测试通过！")
    else:
        log(f"❌ {total_failed} 个测试失败")

    # 提示用户可手动停止后端
    print()
    log("=" * 60)
    log("如需停止后端，请在运行后端的终端中按 Ctrl+C")
    log("=" * 60)

    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
