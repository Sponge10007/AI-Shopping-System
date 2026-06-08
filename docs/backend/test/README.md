# 后端模块测试

> 本项目后端模块的自动化测试框架、测试脚本与测试报告汇总目录。

---

## 📂 目录结构

```
docs/backend/test/
├── README.md                          # 本文件 — 测试目录总览
├── Auth模块测试报告.md                 # Auth 模块测试报告
├── Product模块测试报告.md              # Product 模块测试报告
├── Order模块测试报告.md                # Order 模块测试报告
├── Upload模块测试报告.md               # Upload 模块测试报告
├── Behavior模块测试报告.md             # Behavior 模块测试报告
├── Admin模块测试报告.md                # Admin 模块测试报告
├── Internal模块测试报告.md             # Internal 模块测试报告
└── assets/
    ├── test-scripts/                  # 测试脚本
    │   ├── auth_ps1/                  #   Auth 模块 PowerShell 测试脚本
    │   ├── product_python/            #   Product 模块 Python 测试框架
    │   ├── order_python/              #   Order 模块 Python 测试框架
    │   ├── uba_python/                #   Upload/Behavior/Admin 模块 Python 测试框架
    │   └── internal_python/           #   Internal 模块 Python 测试框架
    └── test-results/                  # 测试结果（JSON 数据）
        ├── auth/                      #   Auth 模块测试结果
        ├── product/                   #   Product 模块测试结果
        ├── order/                     #   Order 模块测试结果
        ├── uba/                       #   Upload/Behavior/Admin 模块测试结果
        └── internal/                  #   Internal 模块测试结果
```

---

## 📊 测试总览

| 模块 | 测试脚本语言 | 测试用例数 | 通过率 | 最新测试时间 |
|:----:|:----------:|:---------:|:-----:|:----------:|
| **Auth** | PowerShell | **53** | **100%** | 2026-06-07 |
| **Product** | Python | **121** | **100%** | 2026-06-07 |
| **Order** | Python | **121** | **100%** | 2026-06-08 |
| **Upload** | Python | **7** | **100%** | 2026-06-08 |
| **Behavior** | Python | **7** | **100%** | 2026-06-08 |
| **Admin** | Python | **10** | **100%** | 2026-06-08 |
| **UBA 流程** | Python | **~30** | **100%** | 2026-06-08 |
| **Internal** | Python | **20** | **100%** | 2026-06-08 |
| **合计** | — | **~369** | **100%** | — |

---

## 🚀 快速运行

### Auth 模块（PowerShell）

```powershell
cd assets\test-scripts\auth_ps1
.\run_all_tests.ps1
```

### Product 模块（Python）

```bash
cd assets\test-scripts\product_python
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python run_all_tests.py
```

### Order 模块（Python）

```bash
cd assets\test-scripts\order_python
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python run_all_tests.py
```

### Upload/Behavior/Admin 模块（Python）

```bash
cd assets\test-scripts\uba_python
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python run_all_tests.py

# 跳过环境管理（后端已在运行）
python run_all_tests.py --skip-env

# 运行单个测试
python run_all_tests.py --upload
python run_all_tests.py --behavior
python run_all_tests.py --admin
python run_all_tests.py --flow
```

### Internal 模块（Python）

```bash
cd assets\test-scripts\internal_python
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python run_all_tests.py

# 跳过环境管理（后端已在运行）
python run_all_tests.py --skip-env
```

---

## 📋 测试模块说明

### 1️⃣ Auth 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `01_register_test.ps1` | 用户注册接口测试 | 15 |
| `02_login_test.ps1` | 用户登录接口测试 | 14 |
| `03_logout_test.ps1` | 用户登出接口测试 | 8 |
| `04_auth_flow_test.ps1` | 注册→登录→登出完整流程测试 | 16 |
| **合计** | | **53** |

### 2️⃣ Product 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `01_create_product_test.py` | 商品创建接口测试 | 15 |
| `02_query_product_test.py` | 商品查询接口测试 | 20 |
| `03_update_product_test.py` | 商品更新/下架/补货测试 | 21 |
| `04_product_flow_test.py` | 完整业务流程测试 | 65 |
| **合计** | | **121** |

### 3️⃣ Order 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `01_create_order_test.py` | 订单创建接口测试 | 25 |
| `02_query_order_test.py` | 订单查询接口测试 | 23 |
| `03_pay_order_test.py` | 订单支付接口测试 | 17 |
| `04_order_flow_test.py` | 完整业务流程测试 | 56 |
| **合计** | | **121** |

### 4️⃣ Upload 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `01_upload_test.py` | 文件上传接口测试 | 7 |
| **合计** | | **7** |

### 5️⃣ Behavior 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `02_behavior_test.py` | 行为记录接口测试 | 7 |
| **合计** | | **7** |

### 6️⃣ Admin 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `03_admin_test.py` | 管理员功能接口测试 | 10（含 6 个 SKIP） |
| **合计** | | **10** |

### 7️⃣ 完整业务流程测试

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `04_uba_flow_test.py` | Upload/Behavior/Admin 完整流程测试 | 10 场景 ~30 断言 |
| **合计** | | **~30** |

### 8️⃣ Internal 模块

| 测试文件 | 测试内容 | 断言数 |
|---------|---------|:-----:|
| `01_internal_ai_summary_test.py` | AI 摘要生成接口测试（含 Token 鉴权、商品存在性、摘要内容校验、批量查询、错误场景） | 20 |
| **合计** | | **20** |

---

## 🧪 测试框架架构

### Python 测试框架（通用）

```
run_all_tests.py          # 入口：环境管理 + 测试调度
├── env_manager.py        # 环境管理器
├── api_client.py         # 通用 API 客户端
├── 01_xxx_test.py        # 测试套件 1
├── 02_xxx_test.py        # 测试套件 2
├── 03_xxx_test.py        # 测试套件 3
└── 04_xxx_flow_test.py   # 测试套件 4（完整业务流程）
```

---

## 📁 测试结果说明

每次运行测试后，结果以 JSON 格式保存在 `assets/test-results/<module>/` 目录下：

| 文件 | 内容 |
|------|------|
| `all_tests_summary.json` | 汇总信息（总用例数、通过数、失败数、通过率、运行时间） |
| `01_xxx_result.json` | 各测试套件的详细结果 |
| `02_xxx_result.json` | 同上 |
| `03_xxx_result.json` | 同上 |
| `04_xxx_flow_result.json` | 同上 |

---

## 📖 相关文档

| 文档 | 说明 |
|------|------|
| `Auth模块测试报告.md` | Auth 模块详细测试报告 |
| `Product模块测试报告.md` | Product 模块详细测试报告 |
| `Order模块测试报告.md` | Order 模块详细测试报告 |
| `Upload模块测试报告.md` | Upload 模块详细测试报告 |
| `Behavior模块测试报告.md` | Behavior 模块详细测试报告 |
| `Admin模块测试报告.md` | Admin 模块详细测试报告 |
| `Internal模块测试报告.md` | Internal 模块详细测试报告 |
| `docs/工作过程记录/` | 各模块问题分析与修复历程 |

---

## ⚙️ 环境要求

| 工具 | 版本要求 | 用途 |
|:----:|:--------:|:----:|
| Java | JDK 17+ | 运行后端服务 |
| Maven | 3.8+ | 编译后端代码 |
| Python | 3.8+ | 运行 Python 测试脚本 |
| PostgreSQL | 14+ | 数据库 |
| PowerShell | 5.1+ | 运行 Auth 测试脚本 |
