import cv2
import numpy as np
from PIL import Image
from typing import Union
import os

# 假设你的项目中存在 config.py，如果不存在请注释掉或定义 MAX_DIMENSION
try:
    from config import Config
except ImportError:
    class Config:
        MAX_DIMENSION = 2000  # 默认兜底值

class SmartPreprocessor:
    @staticmethod
    def extract_white_page(image: np.ndarray) -> np.ndarray:
        """
        提取书页区域（ROI裁剪）
        优化点：不再依赖严格的颜色阈值，而是寻找最大轮廓，防止切掉文字。
        """
        if image is None or image.size == 0:
            return image
        
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        # 使用简单的阈值找轮廓，比 HSV 更稳
        _, thresh = cv2.threshold(gray, 20, 255, cv2.THRESH_BINARY)
        
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return image
        
        # 找到最大的矩形区域（通常是纸张）
        largest_contour = max(contours, key=cv2.contourArea)
        x, y, w, h = cv2.boundingRect(largest_contour)
        
        # 稍微留一点边距，防止切到边缘文字
        pad = 5
        h_img, w_img = image.shape[:2]
        return image[max(0, y-pad):min(h_img, y+h+pad), 
                     max(0, x-pad):min(w_img, x+w+pad)]

    @staticmethod
    def process_image(source: Union[str, Image.Image]) -> Image.Image:
        """统一处理入口 - 专为 VL 模型优化"""
        img = None
        
        if isinstance(source, str):
            if not os.path.exists(source):
                raise FileNotFoundError(f"Image file not found: {source}")
            img = cv2.imread(source)
            if img is None:
                raise ValueError(f"Failed to read image: {source}")
        elif isinstance(source, Image.Image):
            img = cv2.cvtColor(np.array(source), cv2.COLOR_RGB2BGR)
        else:
            raise ValueError("Invalid image source type")

        # 1. 提取书页
        page_img = SmartPreprocessor.extract_white_page(img)
        
        # 失败兜底
        if page_img is None or page_img.size == 0:
            page_img = img

        # 2. 转灰度
        gray = cv2.cvtColor(page_img, cv2.COLOR_BGR2GRAY)

        # 3. 【关键】去除阴影和光照不均 (Background Subtraction)
        # 原理：用一个很大的核做闭运算，得到“只有背景没有字”的图，然后用原图除以它
        kernel_size = max(gray.shape[0], gray.shape[1]) // 10
        # 确保核是奇数
        if kernel_size % 2 == 0: kernel_size += 1
        
        bg = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, np.ones((kernel_size, kernel_size), np.uint8))
        # 归一化除法去背景
        div = cv2.divide(gray, bg, scale=255)

        # 4. 【关键】CLAHE 局部对比度增强
        # 这一步能让模糊的字变清晰，且不会像全局直方图那样过曝
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(div)

        # 5. 轻微锐化 (让文字边缘更实，利于模型识别)
        kernel_sharp = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
        final_gray = cv2.filter2D(enhanced, -1, kernel_sharp)

        # 6. 缩放 (保持长宽比)
        h, w = final_gray.shape
        max_side = max(h, w)
        
        if max_side > Config.MAX_DIMENSION:
            scale = Config.MAX_DIMENSION / max_side
            new_w = int(w * scale)
            new_h = int(h * scale)
            final_img = cv2.resize(final_gray, (new_w, new_h), interpolation=cv2.INTER_AREA)
        else:
            final_img = final_gray
            
        # 7. 转回 RGB 给 PIL (VL 模型通常接受 RGB 输入)
        final_rgb = cv2.cvtColor(final_img, cv2.COLOR_GRAY2RGB)
        return Image.fromarray(final_rgb)