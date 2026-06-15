"""
Upload/Behavior/Admin 模块全量测试运行脚本

执行流程：
1. 检查后端是否运行
2. 依次执行 4 个测试套件
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


def run_test_suite(module_name, test_func, results_dir):
    """运行单个测试套件并返回结果"""
    print(f"\n{'=' * 60}")
    print(f"🚀 运行 {module_name} 测试...")
    print(f"{'=' * 60}")

    try:
        results = test_func()
        return results
    except Exception as e:
        print(f"❌ {module_name} 测试执行失败: {e}")
        import traceback
        traceback.print_exc()
        return []


def main():
    parser = argparse.ArgumentParser(description="Upload/Behavior/Admin 模块测试")
    parser.add_argument("--skip-env", action="store_true", help="跳过环境检查")
    parser.add_argument("--upload", action="store_true", help="仅运行 Upload 测试")
    parser.add_argument("--behavior", action="store_true", help="仅运行 Behavior 测试")
    parser.add_argument("--admin", action="store_true", help="仅运行 Admin 测试")
    parser.add_argument("--flow", action="store_true", help="仅运行业务流程测试")
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
                               "..", "..", "test-results", "uba")
    os.makedirs(results_dir, exist_ok=True)

    all_results = []
    start_time = time.time()

    # 确定要运行的测试
    run_all = not (args.upload or args.behavior or args.admin or args.flow)

    if run_all or args.upload:
        from importlib import import_module
        upload_mod = import_module("01_upload_test")
        test = upload_mod.UploadTest(client, results_dir)
        results = test.run_all()
        all_results.extend(results)

    if run_all or args.behavior:
        behavior_mod = import_module("02_behavior_test")
        test = behavior_mod.BehaviorTest(client, results_dir)
        results = test.run_all()
        all_results.extend(results)

    if run_all or args.admin:
        admin_mod = import_module("03_admin_test")
        test = admin_mod.AdminTest(client, results_dir)
        results = test.run_all()
        all_results.extend(results)

    if run_all or args.flow:
        flow_mod = import_module("04_uba_flow_test")
        test = flow_mod.UbaFlowTest(client, results_dir)
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
