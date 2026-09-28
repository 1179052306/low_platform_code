import logging
import base64
import json
import re
from io import BytesIO
from PIL import Image
from openai import OpenAI
from config import Config

logger = logging.getLogger(__name__)

class VisionModelClient:
    def __init__(self):
        self.client = None
        self.model_id = None
        self._init_client()

    def _init_client(self):
        try:
            self.client = OpenAI(api_key="EMPTY", base_url=Config.VLLM_API_URL)
            models = self.client.models.list()
            if not getattr(models, "data", None):
                raise RuntimeError("No models returned from vLLM server.")

            self.model_id = next((m.id for m in models.data if Config.MODEL_PATH_KEYWORD in m.id), None)
            if not self.model_id:
                self.model_id = models.data[0].id
                logger.warning(f"Keyword '{Config.MODEL_PATH_KEYWORD}' not found. Using: {self.model_id}")

            logger.info(f"✅ VisionModelClient initialized with model: {self.model_id}")
        except Exception as e:
            logger.error(f"❌ Failed to initialize VisionModelClient: {e}")
            raise e

    def extract(self, image_pil: Image.Image) -> dict:
        buf = BytesIO()
        image_pil.save(buf, format="JPEG", quality=Config.JPEG_QUALITY, optimize=True)
        b64 = base64.b64encode(buf.getvalue()).decode("utf-8")

        try:
            resp = self.client.chat.completions.create(
                model=self.model_id,
                messages=[
                    {"role": "system", "content": Config.SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": [
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                            {"type": "text", "text": Config.OCR_PROMPT}
                        ]
                    }
                ],
                max_tokens=Config.MAX_TOKENS,
                temperature=Config.TEMPERATURE,
            )
            content = resp.choices[0].message.content.strip()
            return self._parse_json_safe(content)
        except Exception as e:
            logger.error(f"Inference error: {e}")
            raise e

    def _parse_json_safe(self, text: str) -> dict:
        if not isinstance(text, str):
            return {}

        text = text.strip().replace("“", '"').replace("”", '"').replace("‘", "'").replace("’", "'")
        text = re.sub(r"\r\n", "\n", text)

        code_match = re.search(r"```json(.*?)```", text, re.S)
        if not code_match:
            code_match = re.search(r"```(.*?)```", text, re.S)
        if code_match:
            text = code_match.group(1).strip()

        json_match = re.search(r"\{.*\}", text, re.S)
        if json_match:
            text = json_match.group(0)

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            cleaned = text
            cleaned = cleaned.replace("，", ",").replace("：", ":").replace("；", ";")
            cleaned = re.sub(r"\,(\s*[\}\]])", r"\1", cleaned)
            cleaned = re.sub(r"\"?([A-Za-z0-9_]+)\"?\s*:\s*", r'"\1": ', cleaned)
            try:
                return json.loads(cleaned)
            except Exception:
                pass

        return {"error": "Failed to parse JSON", "raw_text": text[:200]}

class DataCleaner:
    KEY_MAP = {
        "书名": "ITEM_NAME",
        "作者": "ZZ",
        "编者": "BZ",
        "译者": "YZ",
        "作者简介": "ZZJJ",
        "出版发行": "CBSID",
        "出版单位": "CBSID",
        "开本": "KB",
        "印张": "YZS",
        "版次": "BC",
        "印次": "YC",
        "字数": "ZS",
        "页数": "YE",
        "语种": "YUZHONG",
        "版别": "BB",
        "科目": "KM",
        "年级": "NJ",
        "定价": "PRICE",
        "价格": "PRICE",
        "出版日期": "CBNY",
        "印刷日期": "CBNY",
        "装帧": "ZHUANGZ",
    }

    @staticmethod
    def clean(data: dict) -> dict:
        result = {key: None for key in Config.OCR_FIELD_KEYS}
        if not isinstance(data, dict):
            return result

        for raw_key, raw_value in data.items():
            normalized_key = DataCleaner._normalize_key(raw_key)
            if normalized_key not in result:
                continue
            result[normalized_key] = DataCleaner._normalize_value(normalized_key, raw_value)

        return result

    @staticmethod
    def _normalize_key(raw_key: str) -> str:
        if raw_key is None:
            return ""
        key = str(raw_key).strip()
        if key in Config.OCR_FIELD_KEYS:
            return key
        if key in DataCleaner.KEY_MAP:
            return DataCleaner.KEY_MAP[key]
        for expected in Config.OCR_FIELD_KEYS:
            if key.upper() == expected.upper():
                return expected
        return key

    @staticmethod
    def _normalize_value(key: str, value):
        if value is None:
            return None
        if isinstance(value, str):
            value = value.strip().strip('"').strip("'")
            if not value:
                return None

        if key == "PRICE":
            return DataCleaner._normalize_price(value)
        if key == "CBNY":
            return DataCleaner._normalize_date(value)
        if key in {"ZS", "YE"}:
            return DataCleaner._normalize_int(value)
        if key == "YZS":
            return DataCleaner._normalize_yzs(value)
        return value

    @staticmethod
    def _normalize_price(value):
        if value is None:
            return None
        if isinstance(value, (int, float)):
            return round(float(value), 2)
        text = str(value).strip()
        text = text.replace("￥", "").replace("元", "").replace(",", "")
        match = re.search(r"-?\d+[\d\.]*", text)
        if not match:
            return None
        try:
            return round(float(match.group(0)), 2)
        except ValueError:
            return None

    @staticmethod
    def _normalize_int(value):
        if value is None:
            return None
        if isinstance(value, int):
            return value
        text = str(value).strip()
        match = re.search(r"\d+", text)
        if not match:
            return None
        try:
            return int(match.group(0))
        except ValueError:
            return None

    @staticmethod
    def _normalize_yzs(value):
        if value is None:
            return None
        text = str(value).strip()
        if not text:
            return None
        numeric_match = re.search(r"\d+(?:\.\d+)?", text)
        if numeric_match and text.startswith(numeric_match.group(0)):
            return text
        return text

    @staticmethod
    def _normalize_date(value):
        if value is None:
            return None
        text = str(value).strip()
        if not text:
            return None
        text = text.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-")
        text = re.sub(r"第\d+版", "", text)
        text = re.sub(r"第\d+次印刷", "", text)
        date_match = re.search(r"(\d{4})(?:[-年](\d{1,2}))?(?:[-月](\d{1,2}))?", text)
        if date_match:
            year = date_match.group(1)
            month = date_match.group(2)
            day = date_match.group(3)
            if month and day:
                return f"{year}-{int(month):02d}-{int(day):02d}"
            if month:
                return f"{year}-{int(month):02d}"
            return year
        return text
