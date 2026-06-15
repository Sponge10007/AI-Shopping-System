# 商品模块 Python 自动化测试框架

## 概述

本框架使用 Python 替代了原有的 PowerShell 测试脚本，解决了 PowerShell 5 中 `Invoke-WebRequest` 异常处理不可靠的问题。测试框架会自动管理环境（重置数据库 → Maven编译 → 启动后端 → 运行测试 → 停止后端）。

**测试结果**：✅ **121/121 全部通过（通过率 100%）**

---

## 环境要求

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| Java | JDK 17+ | 运行后端服务 |
| Maven | 3.8+ | 编译后端代码 |
| Python | 3.8+ | 运行测试脚本 |
| PostgreSQL | 14+ | 数据库 |

---

## 快速开始

### 1. 创建虚拟环境（推荐）

```bash
# Windows
python -m venv venv
venv\Scripts\activate

# Linux/Mac
python3 -m venv venv
source venv/bin/activate
```

### 2. 安装依赖

```bash
pip install -r requirements.txt
```

### 3. 运行测试

```bash
# 默认：完整流程（重置数据库 → 编译 → 启动 → 测试 → 停止）
python run_all_tests.py

# 跳过环境重置，直接测试（假设后端已在运行）
python run_all_tests.py --skip-env

# 只运行特定测试套件
python run_all_tests.py --create    # 只运行创建测试
python run_all_tests.py --query     # 只运行查询测试
python run_all_tests.py --update    # 只运行更新/下架/补货测试
python run_all_tests.py --flow      # 只运行完整流程测试
```

---

## 项目结构

```
docs/backend/test/
├── product模块测试报告.md           # 商品模块测试报告
├── auth模块测试报告.md              # Auth 模块测试报告
├── assets/
│   ├── test-scripts/
│   │   ├── product_python/          # 商品模块 Python 测试框架
│   │   │   ├── api_client.py        # 通用 API 客户端
│   │   │   ├── env_manager.py       # 环境管理器
│   │   │   ├── run_all_tests.py     # 全量测试运行脚本
│   │   │   ├── 01_create_product_test.py
│   │   │   ├── 02_query_product_test.py
│   │   │   ├── 03_update_product_test.py
│   │   │   ├── 04_product_flow_test.py
│   │   │   ├── requirements.txt
│   │   │   └── README.md
│   │   └── auth_ps1/               # Auth 模块 PowerShell 测试脚本
│   └── test-results/
│       ├── product/                 # 商品模块测试结果（JSON）
│       └── auth/                    # Auth 模块测试结果
docs/工作过程记录/
└── product模块工作报告.md           # 问题分析与修复历程
```

---

## 测试套件

| 文件 | 测试内容 | 断言数 | 覆盖范围 |
|------|---------|:-----:|---------|
| `01_create_product_test.py` | 商品创建接口测试 | **15** | TC-PC001 ~ TC-PC007 |
| `02_query_product_test.py` | 商品查询接口测试 | **20** | TC-PQ001 ~ TC-PQ008 |
| `03_update_product_test.py` | 商品更新/下架/补货测试 | **21** | TC-PU001 ~ TC-PR003 |
| `04_product_flow_test.py` | 完整业务流程测试 | **65** | F1 ~ F10 |
| **总计** | | **121** | **全部通过 ✅** |

---

## 自动化流程

```
run_all_tests.py 执行流程（默认模式）：
┌─────────────────────────────────────────────────────┐
│  ① 检查环境 (Java, Maven, Python, requests)         │
│  ② 重置数据库 (DROP表 → 重建表)                      │
│  ③ Maven编译 (mvn clean compile)                    │
│  ④ 启动后端 (mvn spring-boot:run)                   │
│  ⑤ 等待就绪 (轮询 /actuator/health, 超时90秒)        │
│  ⑥ 运行测试 (4个测试套件, 121个断言)                  │
│  ⑦ 停止后端 (taskkill / 发送SIGTERM)                 │
└─────────────────────────────────────────────────────┘
```

---

## 相关文档

| 文档 | 位置 | 说明 |
|------|------|------|
| 商品模块测试报告 | `docs/backend/test/product模块测试报告.md` | 详细的测试结果和验证内容 |
| 商品模块工作报告 | `docs/工作过程记录/product模块工作报告.md` | 问题分析与修复历程 |
| Auth模块测试报告 | `docs/backend/test/auth模块测试报告.md` | Auth 模块测试结果参考 |
