"""
环境管理器
检查后端是否运行，等待后端就绪
"""
import time
import requests


class EnvManager:
    """环境管理工具"""

    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url

    def is_backend_running(self) -> bool:
        """检查后端是否运行"""
        try:
            resp = requests.get(f"{self.base_url}/actuator/health", timeout=5)
            return resp.status_code == 200
        except requests.exceptions.ConnectionError:
            return False
        except requests.exceptions.Timeout:
            return False
        except Exception:
            return False

    def wait_for_backend(self, timeout_sec: int = 120, interval_sec: int = 3) -> bool:
        """等待后端就绪"""
        print(f"⏳ 等待后端就绪（超时 {timeout_sec} 秒）...")
        start = time.time()
        while time.time() - start < timeout_sec:
            if self.is_backend_running():
                elapsed = int(time.time() - start)
                print(f"✅ 后端已就绪（等待 {elapsed} 秒）")
                return True
            print(".", end="", flush=True)
            time.sleep(interval_sec)
        print()
        print("❌ 后端未能在超时时间内就绪")
        return False
