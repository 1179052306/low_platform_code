package com.security.feign;

import com.common.returns.ResMsg;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author zbw
 * @date: 2023/5/29 2:56 PM
 * @description: 用于微服务远程调用
 */
@FeignClient(value = "bcd-wms-websocket${app.prefix}${app.env}")
public interface WebSocketFeignService {

    @PostMapping("/sendMessage/sendMessageByListUser")
    ResMsg sendMessageByListUser(@RequestHeader("UserInfo")String userInfo,@RequestParam("tenantId") String tenantId, @RequestParam("userList") List<String> userList, @RequestParam("message") String message);

    @PostMapping("/sendMessage/sendMessageByUser")
    ResMsg sendMessageByUser(@RequestHeader("UserInfo")String userInfo,@RequestParam("tenantId") String tenantId, @RequestParam("userId") String userId, @RequestParam("message") String message);

}