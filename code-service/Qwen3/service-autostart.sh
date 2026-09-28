#!/bin/bash

set -e

echo "🚀 开始部署 Qwen3-VL + OCR 最终修复版..."
echo "   当前时间: $(date)"
echo "   目标：修复 vLLM JSON 参数 & 消除 OCR 203/EXEC 错误"

# ================= 配置区域 =================
PYENV_ROOT="/root/.pyenv"
ENV_NAME="vllmenv"
WORK_DIR="/Qwen3"
MODEL_PATH="/root/.cache/modelscope/hub/models/Qwen/Qwen3-VL-4B-Instruct"
VLLM_PORT=8000
OCR_SCRIPT_NAME="main.py"

# 路径计算
REAL_PYTHON="${PYENV_ROOT}/versions/${ENV_NAME}/bin/python"
VLLM_BIN_DIR="${PYENV_ROOT}/versions/${ENV_NAME}/bin"
PYENV_BIN="${PYENV_ROOT}/bin"

# 1. 基础检查
echo "🔍 检查环境..."
if [ ! -f "$REAL_PYTHON" ]; then
    echo "❌ 错误：找不到 Python: $REAL_PYTHON"
    exit 1
fi
if [ ! -f "${WORK_DIR}/${OCR_SCRIPT_NAME}" ]; then
    echo "⚠️ 警告：未找到 OCR 脚本: ${WORK_DIR}/${OCR_SCRIPT_NAME}"
    echo "   请确认文件名是否正确 (当前假设为 main.py)"
    # 不退出，尝试继续
fi

# ================= 1. 生成 vLLM Service (JSON 修复版) =================
echo "📝 生成 vLLM Service (已验证的 JSON 传参)..."

cat > /etc/systemd/system/vllm-qwen3-vl.service <<EOF
[Unit]
Description=Qwen3-VL vLLM Service
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=${WORK_DIR}

Environment="PYENV_ROOT=${PYENV_ROOT}"
Environment="PYENV_VERSION=${ENV_NAME}"
Environment="PATH=${VLLM_BIN_DIR}:${PYENV_BIN}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
Environment="LD_LIBRARY_PATH=${VLLM_BIN_DIR}/../lib"

# 【核心修复】环境变量定义为纯 JSON 字符串 (无外层单引号)
Environment="VLLM_LIMIT_MM={\"image\": 4}"

# 【核心修复】Bash 展开时使用双引号包裹
ExecStart=/bin/bash -c 'exec ${REAL_PYTHON} -m vllm.entrypoints.openai.api_server \
    --model ${MODEL_PATH} \
    --tokenizer-mode slow \
    --trust-remote-code \
    --dtype bfloat16 \
    --max-model-len 8192 \
    --limit-mm-per-prompt "\$VLLM_LIMIT_MM" \
    --port ${VLLM_PORT} \
    --host 0.0.0.0'

Restart=always
RestartSec=15
StandardOutput=journal
StandardError=journal
SyslogIdentifier=vllm-qwen3-vl
LimitNOFILE=65535
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
EOF

# ================= 2. 生成 OCR Service (内嵌等待逻辑，无外部脚本依赖) =================
echo "📝 生成 OCR Service (内嵌健康检查，解决 203/EXEC)..."

cat > /etc/systemd/system/book-ocr-api.service <<EOF
[Unit]
Description=Book OCR API Service
After=vllm-qwen3-vl.service
Requires=vllm-qwen3-vl.service

[Service]
Type=simple
User=root
WorkingDirectory=${WORK_DIR}

Environment="PYENV_ROOT=${PYENV_ROOT}"
Environment="PYENV_VERSION=${ENV_NAME}"
Environment="PATH=${VLLM_BIN_DIR}:${PYENV_BIN}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# 【核心修复】移除 ExecStartPre，将等待逻辑直接写入 ExecStart
# 这样就不需要外部 wait-for-vllm.sh 文件，彻底避免 203/EXEC 错误
ExecStart=/bin/bash -c '\
    echo "⏳ [OCR] 正在等待 vLLM 服务就绪..."; \
    for i in {1..36}; do \
        HTTP_CODE=\$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 http://127.0.0.1:${VLLM_PORT}/health 2>/dev/null); \
        if [ "\$HTTP_CODE" = "200" ]; then \
            echo "✅ [OCR] vLLM 已就绪 (HTTP 200)! 启动 OCR..."; \
            break; \
        fi; \
        if [ \$((i % 6)) -eq 0 ]; then echo "   ... 等待中 (\$((i*5))s), 状态: \$HTTP_CODE"; fi; \
        sleep 5; \
    done; \
    if [ "\$HTTP_CODE" != "200" ]; then \
        echo "❌ [OCR] 等待超时，vLLM 未启动成功。"; \
        exit 1; \
    fi; \
    exec ${REAL_PYTHON} ${OCR_SCRIPT_NAME} --vllm-url http://127.0.0.1:${VLLM_PORT}/v1'

Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=book-ocr-api

[Install]
WantedBy=multi-user.target
EOF

# ================= 3. 清理旧文件 (可选) =================
if [ -f "${WORK_DIR}/wait-for-vllm.sh" ]; then
    echo "🗑️ 检测到旧的等待脚本，已不再需要，将其重命名备份..."
    mv "${WORK_DIR}/wait-for-vllm.sh" "${WORK_DIR}/wait-for-vllm.sh.bak"
fi

# ================= 4. 重载并启动 =================
echo "🔄 重载 Systemd..."
sudo systemctl daemon-reload

echo "🛑 停止并重置服务状态..."
sudo systemctl stop vllm-qwen3-vl 2>/dev/null || true
sudo systemctl stop book-ocr-api 2>/dev/null || true
sudo systemctl reset-failed vllm-qwen3-vl 2>/dev/null || true
sudo systemctl reset-failed book-ocr-api 2>/dev/null || true

echo "🚀 启动 vLLM (加载模型中...)..."
sudo systemctl start vllm-qwen3-vl

# 等待 vLLM 初步启动
sleep 5

echo ""
echo "📊 vLLM 状态:"
sudo systemctl status vllm-qwen3-vl --no-pager -l

if sudo systemctl is-active --quiet vllm-qwen3-vl; then
    echo ""
    echo "✅ vLLM 运行正常。现在启动 OCR (它将自动等待 vLLM 完全就绪)..."
    sudo systemctl start book-ocr-api
    
    sleep 3
    echo ""
    echo "📊 OCR 状态:"
    sudo systemctl status book-ocr-api --no-pager -l
    
    if sudo systemctl is-active --quiet book-ocr-api; then
        echo ""
        echo "🎉🎉🎉 成功！所有服务已启动！"
        echo "------------------------------------------------"
        echo "💡 常用命令:"
        echo "  查看 vLLM 日志: sudo journalctl -u vllm-qwen3-vl -f"
        echo "  查看 OCR 日志:  sudo journalctl -u book-ocr-api -f"
        echo "  测试接口:       curl http://localhost:8000/health"
        echo "------------------------------------------------"
    else
        echo ""
        echo "⚠️ OCR 启动失败，请查看日志:"
        echo "  sudo journalctl -u book-ocr-api -n 20 --no-pager"
    fi
else
    echo ""
    echo "⚠️ vLLM 启动似乎有问题，请先解决 vLLM 问题:"
    echo "  sudo journalctl -u vllm-qwen3-vl -n 20 --no-pager"
fi