import os

class Config:
    # 服务配置
    HOST = "0.0.0.0"
    PORT = 8080
    
    # vLLM 配置
    VLLM_API_URL = os.getenv("VLLM_URL", "http://localhost:8000/v1")
    MODEL_PATH_KEYWORD = "Qwen"
    
    # 图像处理配置
    MAX_DIMENSION = 1024
    JPEG_QUALITY = 75
    ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".webp"}
    MAX_TOKENS = 800
    TEMPERATURE = 0.0
    OCR_FIELD_KEYS = [
        "ITEM_NAME", "ZZ", "BZ", "YZ", "ZZJJ", "CBSID", "KB", "YZS", "BC", "YC",
        "ZS", "YE", "YUZHONG", "BB", "KM", "NJ", "PRICE", "CBNY", "ZHUANGZ"
    ]
    SYSTEM_PROMPT = """你是一个专用于图书元数据结构化的 API 接口。你的唯一任务是接收 OCR 文本，解析并输出符合 Schema 定义的 JSON 对象。
禁止输出任何 Markdown 标记（如 ```json）、注释、解释性文字或前言后语。
如果输入文本为空或完全无法识别，请返回所有字段均为 null 的 JSON 对象。
严格遵守数据类型约束：数字字段严禁包含单位，字符串字段严禁包含无关的前后缀。。"""

    OCR_PROMPT = """# Role
你是一个资深的图书编目专家和数据清洗工程师。你需要从杂乱的 OCR 文本中提取标准化的图书元数据。
数据来源可能包含：CIP数据块（通常在顶部）、版权页主体信息（通常在中部或下部）、以及封面/勒口信息。

# Extraction Strategy (核心提取策略)
1. **区域优先级**：以“版权页主体信息”（通常包含出版社、开本、印张、定价的那一块）为最高优先级。CIP 数据仅作为书名、作者、ISBN 的校验源。
2. **去噪清洗**：
   - 去除作者/译者前的国籍标注（如 [美]、(德)、[日]）。
   - 去除责任方式后缀（如 “著”、“编”、“译”、“绘”），只保留人名。
   - 忽略“责任编辑”、“策划编辑”、“封面设计”等非核心元数据人员。
3. **防幻觉原则**：若文中未明确出现某字段信息，必须返回 null，严禁根据书名推测（例如：不要看到“英语”就自动填科目为“英语”，除非文中明确写了“科目：英语”或属于教材类明显标识）。

# Field Definitions (字段定义与清洗规则)

- **ITEM_NAME (书名)**: 提取主标题。若有副标题（通常在主标题下方或用冒号连接），拼接为 "主标题：副标题"。**排除**丛书名（如“动物小说大王沈石溪品藏书系”）。
- **ZZ (作者)**: 第一责任者（著者）。多人用逗号分隔。清洗掉“著/绘”字样。
- **BZ (编者)**: 仅限“主编”、“编”、“编著”。**注意**：教材类图书常只有主编没有作者，此时填入此项。
- **YZ (译者)**: 仅限引进版图书。清洗掉“译”字样。
- **CBSID (出版发行)**: 提取出版社全称（如“北京科学技术出版社”）。
- **KB (开本)**: 优先提取标准格式（如 787×1092 1/16）。若无尺寸，提取通俗格式（如 16开、大32开）。
- **YZS (印张)**: **关键**：必须寻找“印张”二字后的数字（如 10.5、12）。不要将“页数”填入此项。
- **BC (版次)**: 提取“第x版”中的 x。若是“第1版”可输出 1 或 "1版"。
- **YC (印次)**: 提取“第x次印刷”中的 x。
- **ZS (字数)**: 提取“字数”后的纯数字（单位通常为千字）。例如“22千字”提取为 22 或 22000（建议统一为千字单位或纯数字，视您下游需求而定，此处建议保留原文数字部分）。
- **YE (页数)**: 提取“页数”或“xx页”中的数字。注意区分“印张”和“页数”。
- **PRICE (定价)**: **强制数字格式**。去除“元”、“￥”、“定价：”等字符。保留两位小数（如 23.00）。
- **CBNY (出版日期)**: 标准化为 YYYY-MM-DD。若只有年月则为 YYYY-MM。若只有年份则为 YYYY。
- **KM (科目) / NJ (年级)**: **仅针对教材/教辅**。从书名或CIP分类中提取（如“八年级下册”-> NJ="八年级", KM="英语"）。普通图书若无明确标识，返回 null。
- **YUZHONG (语种)**: 默认为“中文”。若原文明确标注“英文版”、“影印版”等，则如实提取。
- **BB (版别)**: 通常指版本性质，如“修订版”、“第2版”、“重印版”。若与 BC 重复，可留空或保持一致。
- **ZHUANGZ (装帧)**: 如“平装”、“精装”、“套装”。若未提及，返回 null。

# Output Format (Strict JSON)
{
  "ITEM_NAME": "string | null",
  "ZZ": "string | null",
  "BZ": "string | null",
  "YZ": "string | null",
  "ZZJJ": "string | null",
  "CBSID": "string | null",
  "KB": "string | null",
  "YZS": "number | string | null",
  "BC": "string | number | null",
  "YC": "string | number | null",
  "ZS": "number | null",
  "YE": "number | null",
  "YUZHONG": "string | null",
  "BB": "string | null",
  "KM": "string | null",
  "NJ": "string | null",
  "PRICE": "number (2 decimals)",
  "CBNY": "string | null",
  "ZHUANGZ": "string | null"
}"""

