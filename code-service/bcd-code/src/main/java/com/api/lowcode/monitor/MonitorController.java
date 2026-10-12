package com.api.lowcode.monitor;

import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/monitor")
public class MonitorController {

    @Resource
    private GatewayMonitor gatewayMonitor;

    @GetMapping("/stats")
    public ResMsg stats() {
        ResMsg r = new ResMsg();
        r.setData(gatewayMonitor.getStats());
        return r;
    }

    @PostMapping("/reset")
    public ResMsg reset() {
        ResMsg r = new ResMsg();
        gatewayMonitor.reset();
        r.setMsg("监控数据已重置");
        return r;
    }
}