package com.api.lowcode.attachment;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/attachment")
public class AttachmentController {

    @Resource
    private AttachmentService attachmentService;

    @PostMapping("/upload")
    public ResMsg upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(required = false) String bizCode,
                         @RequestParam(required = false) String bizId,
                         @RequestParam(required = false) String uploader) {
        ResMsg r = new ResMsg();
        try {
            r.setData(attachmentService.upload(file, bizCode, bizId, uploader));
            r.setMsg("上传成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/list")
    public ResMsg list(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(attachmentService.listAttachments(data.getString("bizCode"), data.getString("bizId")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/delete")
    public ResMsg delete(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            attachmentService.deleteAttachment(data.getString("id"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}