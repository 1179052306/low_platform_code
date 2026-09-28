import base64
import logging
import time
import os
import json
from typing import Optional, Dict, Any, Union
from io import BytesIO
import uvicorn
from fastapi import FastAPI, HTTPException, Request, File, UploadFile, Form, Header
from fastapi.responses import JSONResponse, HTMLResponse
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, Field
from PIL import Image

# 导入自定义模块
from config import Config
from preprocessor import SmartPreprocessor
from vllm_client import VisionModelClient, DataCleaner

# 初始化日志
logging.basicConfig(
    level=logging.INFO, 
    format='%(asctime)s - [%(levelname)s] - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="Book OCR API", description="Full-chain timing enabled")
templates = Jinja2Templates(directory="templates")

# 全局单例
preprocessor = SmartPreprocessor()
vision_client: Optional[VisionModelClient] = None
data_cleaner = DataCleaner()

# ================= 数据模型 =================
class ExtractRequest(BaseModel):
    image_path: Optional[str] = Field(None, description="图片路径")
    image_base64: Optional[str] = Field(None, description="Base64 图片")

class ExtractResponse(BaseModel):
    res: bool
    code: int
    message: str
    data: Optional[Dict[str, Any]] = None
    business_time: float = 0.0  # 纯业务逻辑耗时 (推理+清洗)
    total_time: float = 0.0     # 【新增】全链路总耗时 (下载+解码+推理+序列化)

# ================= 初始化逻辑 =================
def init_app_state():
    global vision_client
    try:
        vision_client = VisionModelClient()
        logger.info("✅ vLLM Client connected.")
    except Exception as e:
        logger.critical(f"⚠️ vLLM Client failed: {e}. OCR will be disabled.")
        vision_client = None

@app.on_event("startup")
async def startup_event():
    logger.info("🚀 Starting Book OCR API...")
    init_app_state()

# ================= 【核心】全链路计时中间件 =================
@app.middleware("http")
async def measure_total_time(request: Request, call_next):
    # 1. 请求到达瞬间开始计时 (包含网络接收、下载、解码等所有前置操作)
    start_time = time.time()
    
    # 2. 执行后续逻辑 (路由函数、业务处理)
    response = await call_next(request)
    
    # 3. 逻辑执行完毕，准备发送响应前结束计时
    end_time = time.time()
    total_duration = end_time - start_time
    
    # 4. 将总耗时写入响应头 (方便 curl 或 Postman 查看)
    response.headers["X-Total-Process-Time"] = f"{total_duration:.3f}"
    
    # 5. 【关键】如果响应是 JSON，尝试修改 Body 注入 total_time
    # 这样前端不需要自己算，直接拿后端算好的总时间
    if response.status_code == 200 and isinstance(response, JSONResponse):
        # 获取原始 Body
        body_bytes = b"".join([chunk async for chunk in response.body_iterator])
        try:
            body_str = body_bytes.decode("utf-8")
            data = json.loads(body_str)
            
            # 注入总耗时
            if isinstance(data, dict):
                data["total_time"] = round(total_duration, 3)
                
                # 重新构建响应
                new_response = JSONResponse(
                    content=data,
                    status_code=response.status_code,
                    headers=dict(response.headers) # 保留原有的 X-Total-Process-Time
                )
                return new_response
        except Exception as e:
            logger.warning(f"Failed to inject total_time into JSON: {e}")
            
    return response

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "res": False,
            "code": exc.status_code,
            "message": str(exc.detail),
            "data": None,
            "business_time": 0.0,
            "total_time": 0.0,
        },
    )

@app.exception_handler(Exception)
async def general_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "res": False,
            "code": 500,
            "message": "Internal server error",
            "data": None,
            "business_time": 0.0,
            "total_time": 0.0,
        },
    )

# ================= 路由 =================
@app.get("/", response_class=HTMLResponse)
async def get_home(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})

@app.post("/api/extract", response_model=ExtractResponse)
async def extract_post(request: ExtractRequest):
    return await process_extract(request.image_path, request.image_base64)

@app.post("/api/extract/upload", response_model=ExtractResponse)
async def extract_upload(
    file: UploadFile = File(...),
    pageid: Optional[str] = Form(None),
    pageid_header: Optional[str] = Header(None, alias="pageid")
):
    if vision_client is None:
        raise HTTPException(status_code=503, detail="vLLM Client not initialized.")

    filename = file.filename or "upload"
    ext = os.path.splitext(filename)[1].lower()
    if ext not in Config.ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Unsupported file extension: {ext}")

    try:
        file_bytes = await file.read()
        if not file_bytes:
            raise HTTPException(status_code=400, detail="Empty file upload.")

        img_pil = load_image_from_bytes(file_bytes)
        logger.info(f"Received upload {file.filename}, ext={ext}, size={len(file_bytes)} bytes")

        t_start = time.perf_counter()
        raw_data = vision_client.extract(img_pil)
        final_data = data_cleaner.clean(raw_data)
        t_end = time.perf_counter()

        return ExtractResponse(
            res=True,
            code=200,
            message="Success",
            data=final_data,
            business_time=round(t_end - t_start, 3),
            total_time=0.0
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error in upload endpoint: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal error: {str(e)}")

@app.get("/api/extract", response_model=ExtractResponse)
async def extract_get(image_path: str):
    return await process_extract(image_path, None)

def load_image_from_bytes(file_bytes: bytes) -> Image.Image:
    try:
        with Image.open(BytesIO(file_bytes)) as pil:
            pil = pil.convert("RGB")
            return preprocessor.process_image(pil)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid image file: {exc}")


def load_image_from_path(image_path: str) -> Image.Image:
    if not os.path.exists(image_path):
        raise HTTPException(status_code=404, detail=f"File not found: {image_path}")
    ext = os.path.splitext(image_path)[1].lower()
    if ext not in Config.ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {ext}")

    try:
        with Image.open(image_path) as pil:
            pil = pil.convert("RGB")
            return preprocessor.process_image(pil)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Processing error: {exc}")


def load_image_input(image_path: Optional[str], image_base64: Optional[str]) -> Image.Image:
    if image_base64:
        try:
            img_bytes = base64.b64decode(image_base64, validate=True)
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Invalid Base64 input: {exc}")
        return load_image_from_bytes(img_bytes)

    if image_path:
        return load_image_from_path(image_path)

    raise HTTPException(status_code=400, detail="Missing image input.")


async def process_extract(image_path: Optional[str], image_base64: Optional[str]):
    if vision_client is None:
        raise HTTPException(status_code=503, detail="vLLM Client not initialized.")

    t_start = time.perf_counter()
    try:
        img_pil = load_image_input(image_path, image_base64)

        logger.info(f"Processing image (size: {img_pil.size})...")
        raw_data = vision_client.extract(img_pil)
        final_data = data_cleaner.clean(raw_data)

        t_end = time.perf_counter()
        business_time = t_end - t_start

        logger.info(f"✅ Success | Business: {business_time:.2f}s")

        return ExtractResponse(
            res=True,
            code=200,
            message="Success",
            data=final_data,
            business_time=round(business_time, 3),
            total_time=0.0
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal error: {str(e)}")

@app.get("/health")
async def health_check():
    is_healthy = vision_client is not None
    return {
        "status": "healthy" if is_healthy else "degraded",
        "vllm_connected": is_healthy,
        "model": getattr(vision_client, 'model_id', 'unknown') if vision_client else None
    }

if __name__ == "__main__":
    print("="*50)
    print(f"🚀 Book OCR API Starting on http://{Config.HOST}:{Config.PORT}")
    print("⏱️ Full-chain timing enabled (Download + Decode + Inference)")
    print("="*50)
    uvicorn.run(app, host=Config.HOST, port=Config.PORT, log_level="info")