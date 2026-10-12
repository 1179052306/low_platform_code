package com.api.lowcode.attachment;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AttachmentService {

    @Resource
    private SqlEngineJson helper;

    private static final String UPLOAD_DIR = "Uploads/attachments";

    public JSONObject upload(MultipartFile file, String bizCode, String bizId, String uploader) throws Exception {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String savedName = fileId + ext;
        File dest = new File(dir, savedName);
        file.transferTo(dest);

        JSONObject record = new JSONObject();
        record.put("id", fileId);
        record.put("biz_code", bizCode);
        record.put("biz_id", bizId);
        record.put("file_name", originalName);
        record.put("file_path", UPLOAD_DIR + "/" + savedName);
        record.put("file_size", file.getSize());
        record.put("file_type", file.getContentType());
        record.put("uploader", uploader);
        record.put("created_at", Instant.now().toString());
        ResMsg r = helper.insert("lc_attachment", record);
        SqlEngineJson.check(r);

        log.info("[AttachmentService] 上传附件: {} -> {}", originalName, savedName);
        return record;
    }

    public JSONArray listAttachments(String bizCode, String bizId) throws Exception {
        String[] cols = { "id", "file_name", "file_path", "file_size", "file_type", "uploader", "created_at" };
        JSONArray conditions = helper.eq("biz_code", bizCode);
        conditions.addAll(helper.eq("biz_id", bizId).subList(1, 2));
        return helper.query("lc_attachment", cols, conditions, "created_at", true);
    }

    public void deleteAttachment(String id) throws Exception {
        JSONArray rows = helper.query("lc_attachment", new String[]{"file_path"}, helper.eq("id", id), null, false);
        if (rows.size() > 0) {
            String path = rows.getJSONObject(0).getString("file_path");
            if (path != null) {
                new File(path).delete();
            }
        }
        ResMsg r = helper.delete("lc_attachment", helper.eq("id", id));
        SqlEngineJson.check(r);
    }
}