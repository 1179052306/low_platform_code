#!/bin/bash

# setup-jar-autostart.sh (智能 loader.path + XML 参数清洗版 - root 专用)
# 功能：部署当前目录 .jar 文件为 systemd 服务，强制以 root 运行
# 核心修复：
#   - 【新增】自动从 XML 参数中剔除 "-jar xxx" 片段，防止命令重复
#   - 自动读取同级 XML 配置文件中的 JVM 参数 (排除 -Dloader.path)
#   - 仅支持 root 运行
#   - 自动检测上级目录是否存在 lib/，决定是否添加 -Dloader.path=../lib
#   - 支持重复执行：自动重载配置并重启服务

set -e

# === 强制 root 检查 ===
if [ "$EUID" -ne 0 ]; then
    echo "[ERROR] 此脚本必须以 root 或 sudo 运行！"
    echo "      例如: sudo bash ./setup-jar-autostart.sh"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATTERN="*.jar"
XML_PATTERN="*.xml"

echo "[INFO] 脚本目录: $SCRIPT_DIR"

# === 1. 查找 JAR 文件 ===
shopt -s nullglob
JAR_FILES=("$SCRIPT_DIR"/$JAR_PATTERN)
shopt -u nullglob

if [ ${#JAR_FILES[@]} -eq 0 ]; then
    echo "[ERROR] 未在 $SCRIPT_DIR 下找到 .jar 文件！"
    exit 1
elif [ ${#JAR_FILES[@]} -gt 1 ]; then
    echo "[WARN] 找到多个 JAR 文件，将使用: ${JAR_FILES[0]}"
fi

JAR_PATH="${JAR_FILES[0]}"
JAR_NAME=$(basename "$JAR_PATH")
echo "[INFO] 使用 JAR: $JAR_NAME"

# === 2. 自动生成服务名 ===
SERVICE_NAME_RAW="${JAR_NAME%.jar}"
SERVICE_NAME=$(echo "$SERVICE_NAME_RAW" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g; s/--*/-/g; s/^-//; s/-$//')
[ -z "$SERVICE_NAME" ] && SERVICE_NAME="java-app"

echo "[INFO] 服务名: $SERVICE_NAME"

# === 3. 强制使用 root 用户 ===
RUN_USER="root"
echo "[INFO] 运行用户: $RUN_USER"

# === 4. 检查 Java ===
if ! JAVA_BIN=$(which java 2>/dev/null); then
    echo "[ERROR] 未找到 java，请先安装 JDK/JRE"
    exit 1
fi
echo "[INFO] Java 路径: $JAVA_BIN"

# === 5. 智能参数构建 ===

# 5.1 初始化 JVM 参数字符串
JVM_OPTS=""

# 5.2 尝试从 XML 文件提取参数
shopt -s nullglob
XML_FILES=("$SCRIPT_DIR"/$XML_PATTERN)
shopt -u nullglob

XML_OPT=""
if [ ${#XML_FILES[@]} -gt 0 ]; then
    # 如果有多个 XML，取第一个
    XML_PATH="${XML_FILES[0]}"
    echo "[INFO] 发现配置文件: $(basename "$XML_PATH")，尝试提取并清洗 JVM 参数..."
    
    # 提取 <arguments>...</arguments> 之间的内容
    RAW_ARGS=$(sed -n '/<arguments>/,/<\/arguments>/p' "$XML_PATH" | \
               grep -v "<arguments>" | grep -v "</arguments>" | \
               tr '\n' ' ' | \
               sed 's/  */ /g' | \
               sed 's/^ //; s/ $//')

    if [ -n "$RAW_ARGS" ]; then
        # === 核心清洗逻辑 ===
        # 1. 移除 -Dloader.path=... (防止与脚本逻辑冲突)
        # 2. 移除 -jar ... (防止出现双重 -jar 导致启动失败)
        # 使用扩展正则表达式 (-E)
        
        CLEANED_ARGS=$(echo "$RAW_ARGS" | sed -E \
            -e 's/-Dloader\.path=[^ ]* ?//g' \
            -e 's/-jar\s+[^ ]* ?//g')
        
        # 再次清理可能产生的首尾空格和多余中间空格
        CLEANED_ARGS=$(echo "$CLEANED_ARGS" | sed 's/  */ /g' | sed 's/^ //; s/ $//')

        if [ -n "$CLEANED_ARGS" ]; then
            XML_OPT="$CLEANED_ARGS"
            echo "[INFO] 成功从 XML 提取参数 (已自动剔除 loader.path 和 -jar): $XML_OPT"
        else
            echo "[WARN] XML 中未找到有效参数，或所有参数均被过滤。"
        fi
    else
        echo "[WARN] 未在 XML 中找到 <arguments> 标签。"
    fi
else
    echo "[INFO] 未发现 XML 配置文件，跳过 XML 参数提取。"
fi

# 5.3 检查上级目录 lib/ (智能 loader.path)
PARENT_LIB_PATH="$SCRIPT_DIR/../lib"
LOADER_OPT=""
if [ -d "$PARENT_LIB_PATH" ]; then
    LOADER_OPT="-Dloader.path=../lib"
    echo "[INFO] 检测到上级目录 lib/，启用: $LOADER_OPT"
else
    echo "[INFO] 未检测到上级目录 lib/，不使用 loader.path"
fi

# 5.4 合并最终参数
# 顺序：XML 参数 + Loader 参数
if [ -n "$XML_OPT" ] && [ -n "$LOADER_OPT" ]; then
    FINAL_JVM_OPTS="$XML_OPT $LOADER_OPT"
elif [ -n "$XML_OPT" ]; then
    FINAL_JVM_OPTS="$XML_OPT"
elif [ -n "$LOADER_OPT" ]; then
    FINAL_JVM_OPTS="$LOADER_OPT"
else
    FINAL_JVM_OPTS=""
fi

# === 6. 生成 systemd 服务文件 ===
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

# 注意：ExecStart 中直接嵌入变量
# 此时 FINAL_JVM_OPTS 绝对不包含 -jar，所以这里拼接是安全的
cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Java Application - $JAR_NAME
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$SCRIPT_DIR
ExecStart=$JAVA_BIN $FINAL_JVM_OPTS -jar $JAR_PATH
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE_NAME

[Install]
WantedBy=multi-user.target
EOF

chmod 644 "$SERVICE_FILE"

# === 7. 重载并重启服务 ===
echo "🔄 重载 systemd..."
systemctl daemon-reload

echo "✅ 启用开机自启..."
systemctl enable "$SERVICE_NAME"

echo "🚀 重启服务（若未运行则启动）..."
systemctl restart "$SERVICE_NAME"

# === 8. 成功提示 ===
echo
echo "✅ 服务已安装并启动！"
echo "   服务名: ${SERVICE_NAME}.service"
echo "   JAR 路径: $JAR_PATH"
echo "   运行用户: $RUN_USER"
echo "   工作目录: $SCRIPT_DIR"
if [ -n "$FINAL_JVM_OPTS" ]; then
    if [ ${#FINAL_JVM_OPTS} -gt 100 ]; then
        echo "   JVM 参数: ${FINAL_JVM_OPTS:0:100}... (参数较长)"
    else
        echo "   JVM 参数: $FINAL_JVM_OPTS"
    fi
fi
echo
echo "🔧 后续管理命令："
echo "   日志:   journalctl -u ${SERVICE_NAME} -f --no-pager"
echo "   状态:   systemctl status ${SERVICE_NAME}"