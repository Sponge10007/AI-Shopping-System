"""
Internal 模块全量测试运行脚本

执行流程：
1. 检查后端是否运行
2. 执行 AI 摘要接口测试
3. 汇总所有测试结果
4. 输出汇总报告
"""
import os
import sys
import json
import time
import argparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient
from env_manager import EnvManager


def main():
    parser = argparse.ArgumentParser(description="Internal 模块测试")
    parser.add_argument("--skip-env", action="store_true", help="跳过环境检查")
    args = parser.parse_args()

    # 环境检查
    if not args.skip_env:
        env = EnvManager()
        if not env.is_backend_running():
            print("⏳ 后端未运行，等待就绪...")
            if not env.wait_for_backend():
                print("❌ 后端未就绪，请先启动后端服务")
                sys.exit(1)
        else:
            print("✅ 后端已在运行")

    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "internal")
    os.makedirs(results_dir, exist_ok=True)

    all_results = []
    start_time = time.time()

    # 运行 AI 摘要接口测试
    from importlib import import_module
    internal_mod = import_module("01_internal_ai_summary_test")
    test = internal_mod.InternalAiSummaryTest(client, results_dir)
    results = test.run_all()
    all_results.extend(results)

    # 汇总结果
    elapsed = time.time() - start_time
    total = len(all_results)
    passed = sum(1 for r in all_results if r["passed"])
    failed = total - passed

    print("\n" + "=" * 60)
    print("📊 最终测试汇总")
    print("=" * 60)
    print(f"  总用例数: {total}")
    print(f"  通过: {passed}")
    print(f"  失败: {failed}")
    print(f"  通过率: {(passed/total*100):.1f}%" if total > 0 else "  N/A")
    print(f"  运行时间: {elapsed:.2f} 秒")

    # 保存汇总结果
    summary = {
        "test_time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total": total,
        "passed": passed,
        "failed": failed,
        "pass_rate": f"{(passed/total*100):.1f}%" if total > 0 else "N/A",
        "elapsed_seconds": round(elapsed, 2),
        "results": all_results
    }
    summary_path = os.path.join(results_dir, "all_tests_summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n📁 汇总结果已保存: {summary_path}")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
