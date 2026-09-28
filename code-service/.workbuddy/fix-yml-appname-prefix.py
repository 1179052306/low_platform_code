"""
把三个项目所有 src/main/resources/**/*.yml 里的服务名注册字段
    name: bcd-xxx-@envName@
    service-name: bcd-xxx-@envName@
改成
    name: bcd-xxx@prefix@@envName@
    service-name: bcd-xxx@prefix@@envName@

理由：spring.application.name 注册到 nacos 的服务名也要带 prefix 后缀，
否则 FeignClient 拼出来的 bcd-xxx-prefix-envName 在 nacos 找不到对应服务。

只改 src/main/resources，跳过 bin/、target*/、serviceRelease*/ 等镜像副本与构建产物。
"""
import os
import re

ROOTS = [
    r"D:\Project\Ahwx\trunk\Wms\bcd-service\bcd-service-api",
    r"D:\Project\Ahwx\trunk\Yms\bcd-yms-new\bcd-service-api",
    r"D:\Project\Ahwx\trunk\Oms\bcd-service\bcd-service-api",
]

PATTERN = re.compile(r"-@envName@")
REPL = "@prefix@@envName@"

total = 0
files_changed = 0
files_seen = 0

for root in ROOTS:
    if not os.path.isdir(root):
        continue
    for dirpath, dirs, files in os.walk(root):
        # 规范化路径分隔符为 /，便于子串匹配
        norm = dirpath.replace("\\", "/")
        # 只处理 src/main/resources 下的 yml
        if "src/main/resources" not in norm:
            continue
        # 跳过 bin/ 镜像副本（路径里包含 /bin/）
        if "/bin/" in norm:
            continue
        for fname in files:
            if not (fname.endswith(".yml") or fname.endswith(".yaml")):
                continue
            fpath = os.path.join(dirpath, fname)
            files_seen += 1
            try:
                with open(fpath, "rb") as f:
                    raw = f.read()
            except Exception as e:
                print(f"SKIP (read err): {fpath}: {e}")
                continue
            # 探测编码（带 BOM 与否）
            if raw.startswith(b"\xef\xbb\xbf"):
                enc = "utf-8-sig"
                text = raw.decode("utf-8-sig")
            else:
                enc = "utf-8"
                try:
                    text = raw.decode("utf-8")
                except UnicodeDecodeError:
                    enc = "gbk"
                    text = raw.decode("gbk", errors="replace")
            new_text, n = PATTERN.subn(REPL, text)
            if n > 0:
                try:
                    with open(fpath, "w", encoding=enc) as f:
                        f.write(new_text)
                    files_changed += 1
                    total += n
                    print(f"  [{n}] {fpath}")
                except Exception as e:
                    print(f"SKIP (write err): {fpath}: {e}")

print()
print(f"Files seen: {files_seen}")
print(f"Files changed: {files_changed}")
print(f"Occurrences replaced: {total}")
