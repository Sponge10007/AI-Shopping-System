"""
环境管理器 - 负责：
1. 检查 PostgreSQL 和 Java 环境
2. 清除旧数据库（重建表）
3. Maven 编译构建后端（在 backend 目录下执行）
4. 启动后端服务并等待就绪
5. 停止后端服务

用法：
    python env_manager.py full       # 完整流程（推荐：等同于 run_all_tests.py）
    python env_manager.py reset-db   # 仅重置数据库
    python env_manager.py build      # 仅编译
    python env_manager.py start      # 仅启动
    python env_manager.py stop       # 仅停止
    python env_manager.py test       # 仅运行测试（调用 run_all_tests.py --skip-env）
"""

import os
import sys
import time
import json
import subprocess
import signal
import socket
import urllib.request
import urllib.error
from pathlib import Path

# ============ 路径配置 ============
PROJECT_ROOT = Path(__file__).resolve().parents[6]  # 回到 SE-Project 根目录
BACKEND_DIR = PROJECT_ROOT / "backend"
DB_MIGRATIONS_DIR = PROJECT_ROOT / "database" / "migrations"
TEST_DIR = Path(__file__).resolve().parent

# ============ 配置 ============
BACKEND_PORT = 8080
BACKEND_URL = f"http://localhost:{BACKEND_PORT}"
HEALTH_URL = f"{BACKEND_URL}/actuator/health"
DB_NAME = "ai_shop"
DB_USER = "ai_shop"
DB_PASS = "ai_shop"
DB_HOST = "localhost"
DB_PORT = 5432

# 后端进程对象
backend_process = None


def log(msg: str, level: str = "INFO"):
    print(f"[{level}] {msg}")


def run_cmd(cmd_list, **kwargs):
    """
    安全执行命令的封装。
    在 Windows 上，对于 .cmd/.bat 文件（如 mvn），自动添加 shell=True。
    """
    if sys.platform == "win32" and cmd_list and cmd_list[0] in ("mvn", "mvn.cmd"):
        kwargs["shell"] = True
        cmd_str = " ".join(cmd_list)
        return subprocess.run(cmd_str, **kwargs)
    return subprocess.run(cmd_list, **kwargs)


def popen_cmd(cmd_list, **kwargs):
    """安全执行 Popen 的封装，Windows 上 mvn 自动加 shell=True"""
    if sys.platform == "win32" and cmd_list and cmd_list[0] in ("mvn", "mvn.cmd"):
        kwargs["shell"] = True
        cmd_str = " ".join(cmd_list)
        return subprocess.Popen(cmd_str, **kwargs)
    return subprocess.Popen(cmd_list, **kwargs)


def check_prerequisites() -> bool:
    """检查前置条件：Java、Maven、Python requests"""
    log("检查前置环境...")

    # 检查 Java
    try:
        result = run_cmd(["java", "--version"], capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            first_line = result.stdout.strip().split("\n")[0]
            log(f"Java: {first_line}")
        else:
            log("Java 未找到，请安装 JDK 17+", "ERROR")
            return False
    except FileNotFoundError:
        log("Java 未找到，请安装 JDK 17+", "ERROR")
        return False

    # 检查 Maven
    try:
        result = run_cmd(["mvn", "--version"], capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            first_line = result.stdout.strip().split("\n")[0]
            log(f"Maven: {first_line}")
        else:
            log("Maven 未找到，请安装 Maven 3.8+", "ERROR")
            return False
    except FileNotFoundError:
        log("Maven 未找到，请安装 Maven 3.8+", "ERROR")
        return False

    # 检查 Python requests
    try:
        import requests
        log("Python requests 库: 已安装")
    except ImportError:
        log("Python requests 库未安装，正在安装...")
        result = subprocess.run(
            [sys.executable, "-m", "pip", "install", "requests"],
            capture_output=True, text=True, timeout=60
        )
        if result.returncode == 0:
            log("requests 库安装成功")
        else:
            log(f"requests 库安装失败: {result.stderr}", "ERROR")
            return False

    return True


def is_postgres_ready() -> bool:
    """检查 PostgreSQL 是否可连接"""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(3)
        result = sock.connect_ex((DB_HOST, DB_PORT))
        sock.close()
        if result == 0:
            log(f"PostgreSQL 端口 {DB_PORT} 已开放")
            return True
        else:
            log(f"PostgreSQL 端口 {DB_PORT} 未开放", "WARN")
            return False
    except Exception as e:
        log(f"检查 PostgreSQL 时出错: {e}", "WARN")
        return False


def reset_database():
    """重置数据库：通过 Docker 中的 psql 删除旧表并重新创建"""
    log("=" * 60)
    log("步骤 1/4: 重置数据库...")
    log("=" * 60)

    if not is_postgres_ready():
        log("PostgreSQL 未运行，跳过数据库重置", "WARN")
        log("请确保 PostgreSQL 已启动，数据库 ai_shop 已创建", "WARN")
        return False

    sql_file = DB_MIGRATIONS_DIR / "001_initial_schema.sql"
    if not sql_file.exists():
        log(f"SQL 迁移文件不存在: {sql_file}", "ERROR")
        return False

    log(f"找到 SQL 文件: {sql_file}")

    with open(sql_file, "r", encoding="utf-8") as f:
        sql_content = f.read()

    drop_tables = """
    DROP TABLE IF EXISTS audit_logs CASCADE;
    DROP TABLE IF EXISTS behavior_logs CASCADE;
    DROP TABLE IF EXISTS payments CASCADE;
    DROP TABLE IF EXISTS order_items CASCADE;
    DROP TABLE IF EXISTS orders CASCADE;
    DROP TABLE IF EXISTS product_images CASCADE;
    DROP TABLE IF EXISTS products CASCADE;
    DROP TABLE IF EXISTS user_profiles CASCADE;
    DROP TABLE IF EXISTS users CASCADE;
    """

    full_sql = drop_tables + "\n" + sql_content

    temp_sql = TEST_DIR / "_reset_temp.sql"
    with open(temp_sql, "w", encoding="utf-8") as f:
        f.write(full_sql)

    try:
        docker_cmd = [
            "docker", "exec", "-i", "ai-shop-postgres",
            "psql", "-U", DB_USER, "-d", DB_NAME
        ]
        result = subprocess.run(
            docker_cmd,
            input=full_sql,
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode == 0:
            log("数据库重置成功！（通过 Docker psql）")
            return True
        else:
            log(f"Docker psql 执行失败: {result.stderr}", "WARN")
            return _reset_db_via_local_psql(full_sql)

    except FileNotFoundError:
        log("Docker 命令未找到，尝试本地 psql...", "WARN")
        return _reset_db_via_local_psql(full_sql)
    except subprocess.TimeoutExpired:
        log("Docker psql 执行超时", "ERROR")
        return False
    finally:
        if temp_sql.exists():
            temp_sql.unlink()


def _reset_db_via_local_psql(sql_content: str) -> bool:
    """通过本地 psql 重置数据库"""
    try:
        env = os.environ.copy()
        env["PGPASSWORD"] = DB_PASS
        result = subprocess.run(
            ["psql", "-h", DB_HOST, "-p", str(DB_PORT), "-U", DB_USER, "-d", DB_NAME],
            input=sql_content,
            capture_output=True, text=True, timeout=30, env=env
        )
        if result.returncode == 0:
            log("数据库重置成功！（通过本地 psql）")
            return True
        else:
            log(f"本地 psql 执行失败: {result.stderr}", "WARN")
            return _reset_db_via_python(sql_content)
    except FileNotFoundError:
        return _reset_db_via_python(sql_content)


def _reset_db_via_python(sql_content: str) -> bool:
    """通过 Python psycopg2 重置数据库"""
    try:
        import psycopg2
        conn = psycopg2.connect(
            host=DB_HOST, port=DB_PORT,
            dbname=DB_NAME, user=DB_USER, password=DB_PASS
        )
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute(sql_content)
        cur.close()
        conn.close()
        log("数据库重置成功！（通过 psycopg2）")
        return True
    except ImportError:
        log("psycopg2 未安装，尝试安装...")
        result = subprocess.run(
            [sys.executable, "-m", "pip", "install", "psycopg2-binary"],
            capture_output=True, text=True, timeout=60
        )
        if result.returncode == 0:
            return _reset_db_via_python(sql_content)
        else:
            log("psycopg2 安装失败", "ERROR")
            log("请手动执行: docker exec -i ai-shop-postgres psql -U ai_shop -d ai_shop < database/migrations/001_initial_schema.sql", "INFO")
            return False
    except Exception as e:
        log(f"数据库重置失败: {e}", "ERROR")
        return False


def build_backend() -> bool:
    """在 backend 目录下执行 Maven 编译"""
    log("=" * 60)
    log("步骤 2/4: Maven 编译构建后端...")
    log("=" * 60)
    log(f"工作目录: {BACKEND_DIR}")
    log("正在执行: mvn clean compile")
    log("（首次编译可能需要 1-3 分钟，请耐心等待...）")

    result = run_cmd(
        ["mvn", "clean", "compile"],
        capture_output=True, text=True, timeout=300,
        cwd=str(BACKEND_DIR)
    )

    if result.returncode == 0:
        log("Maven 编译成功！")
        return True
    else:
        log("Maven 编译失败！", "ERROR")
        for line in result.stderr.split("\n"):
            if "ERROR" in line:
                log(f"  {line.strip()}", "ERROR")
        return False


def is_backend_running() -> bool:
    """检查后端是否正在运行"""
    try:
        req = urllib.request.Request(HEALTH_URL, method="GET")
        with urllib.request.urlopen(req, timeout=3) as resp:
            return resp.status == 200
    except Exception:
        return False


def stop_existing_backend():
    """停止已存在的后端进程（如果有）"""
    if is_backend_running():
        log("检测到后端已在运行，正在停止...")
        if sys.platform == "win32":
            try:
                result = subprocess.run(
                    ["netstat", "-ano"], capture_output=True, text=True, timeout=10
                )
                for line in result.stdout.split("\n"):
                    if ":8080" in line and "LISTENING" in line:
                        parts = line.strip().split()
                        if parts:
                            pid = parts[-1]
                            log(f"找到后端进程 PID: {pid}，正在终止...")
                            subprocess.run(
                                ["taskkill", "/F", "/PID", pid],
                                capture_output=True, timeout=10
                            )
                            log("后端进程已终止")
                            time.sleep(3)
                            return
            except Exception as e:
                log(f"停止后端时出错: {e}", "WARN")
        else:
            try:
                result = subprocess.run(
                    ["lsof", "-ti", f":{BACKEND_PORT}"],
                    capture_output=True, text=True, timeout=10
                )
                if result.stdout.strip():
                    pid = result.stdout.strip().split("\n")[0]
                    log(f"找到后端进程 PID: {pid}，正在终止...")
                    os.kill(int(pid), signal.SIGTERM)
                    time.sleep(3)
                    log("后端进程已终止")
            except Exception:
                pass


def start_backend() -> bool:
    """启动后端服务"""
    log("=" * 60)
    log("步骤 3/4: 启动后端服务...")
    log("=" * 60)

    global backend_process

    stop_existing_backend()

    log("正在启动后端服务（端口 8080）...")
    log("（启动可能需要 10-30 秒，请耐心等待...）")

    backend_process = popen_cmd(
        ["mvn", "spring-boot:run"],
        cwd=str(BACKEND_DIR),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    max_wait = 90
    start_time = time.time()
    last_log_time = 0

    log("等待后端就绪", end="")
    while time.time() - start_time < max_wait:
        elapsed = int(time.time() - start_time)

        if elapsed - last_log_time >= 5:
            print(f"\n  已等待 {elapsed} 秒...")
            last_log_time = elapsed

        if is_backend_running():
            print(f"\n后端已就绪！({BACKEND_URL})")
            log(f"启动耗时: {int(time.time() - start_time)} 秒")
            return True

        if backend_process.poll() is not None:
            print(f"\n后端进程意外退出，退出码: {backend_process.returncode}")
            try:
                stdout_data = backend_process.stdout.read() if backend_process.stdout else ""
                if stdout_data:
                    log(f"最后输出: {stdout_data[-500:]}", "ERROR")
            except:
                pass
            return False

        time.sleep(1)

    print(f"\n后端启动超时（{max_wait} 秒）")
    return False


def stop_backend():
    """停止后端服务"""
    log("=" * 60)
    log("步骤 4/4: 停止后端服务...")
    log("=" * 60)

    global backend_process

    if backend_process is not None:
        log("正在停止后端服务...")
        try:
            if sys.platform == "win32":
                subprocess.run(
                    ["taskkill", "/F", "/T", "/PID", str(backend_process.pid)],
                    capture_output=True, timeout=10
                )
            else:
                os.killpg(os.getpgid(backend_process.pid), signal.SIGTERM)
            backend_process.wait(timeout=15)
            log("后端服务已停止")
        except Exception as e:
            log(f"停止后端时出错: {e}", "WARN")
            try:
                backend_process.kill()
            except:
                pass
        backend_process = None
    else:
        stop_existing_backend()


def run_tests() -> bool:
    """运行所有测试（委托给 run_all_tests.py --skip-env）"""
    log("=" * 60)
    log("运行测试...")
    log("=" * 60)

    runner_script = TEST_DIR / "run_all_tests.py"
    if not runner_script.exists():
        log(f"测试运行脚本不存在: {runner_script}", "ERROR")
        return False

    result = subprocess.run(
        [sys.executable, str(runner_script), "--skip-env"],
        capture_output=True, text=True, timeout=300,
        cwd=str(TEST_DIR)
    )

    print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)

    return result.returncode == 0


def full_cycle():
    """完整流程：重置数据库 → 编译 → 启动 → 测试 → 停止"""
    log("=" * 60)
    log("订单模块自动化测试 - 完整流程")
    log("=" * 60)
    log(f"项目根目录: {PROJECT_ROOT}")
    log(f"后端目录: {BACKEND_DIR}")
    log(f"测试目录: {TEST_DIR}")
    log("")

    start_time = time.time()

    if not check_prerequisites():
        log("前置条件检查失败，终止流程", "ERROR")
        return False

    if not reset_database():
        log("数据库重置失败，终止流程", "ERROR")
        return False

    if not build_backend():
        log("后端编译失败，终止流程", "ERROR")
        return False

    if not start_backend():
        log("后端启动失败，终止流程", "ERROR")
        return False

    tests_passed = run_tests()

    stop_backend()

    total_time = int(time.time() - start_time)
    log("=" * 60)
    if tests_passed:
        log(f"✅ 全部测试通过！总耗时: {total_time} 秒")
    else:
        log(f"❌ 部分测试失败！总耗时: {total_time} 秒")
    log("=" * 60)

    return tests_passed


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="订单模块测试环境管理器")
    parser.add_argument("action", nargs="?", default="full",
                        choices=["full", "reset-db", "build", "start", "stop", "test"],
                        help="执行的操作")

    args = parser.parse_args()

    if args.action == "full":
        full_cycle()
    elif args.action == "reset-db":
        check_prerequisites()
        reset_database()
    elif args.action == "build":
        check_prerequisites()
        build_backend()
    elif args.action == "start":
        check_prerequisites()
        start_backend()
    elif args.action == "stop":
        stop_backend()
    elif args.action == "test":
        run_tests()
