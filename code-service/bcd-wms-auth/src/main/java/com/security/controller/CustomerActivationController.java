package com.security.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.http.HttpClient;
import com.common.returns.ResMsg;
import com.common.utils.StringUtils;
import com.security.oauth2.config.ActivationConfig;
import com.security.oauth2.config.BasePage;
import com.server.db.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author lw
 * @date: 2025/1/6
 * @description:
 **/
@Slf4j
@RestController
@RequestMapping("/customerActivation")
public class CustomerActivationController extends BasePage {

    @Autowired
    ActivationConfig activationConfig;

    @Autowired
    Tenant tenant;

    @PostMapping("/activation")
    public ResMsg activation() throws Exception {
        ResMsg resMsg = new ResMsg();
        String requestJson = this.getRequestValues("formdata");
        String tenantId ="";
        if(!tenant.getSingle()){
             tenantId = this.getHeader("tenant");
            if(StringUtils.isBlank(tenantId)){
                resMsg.setRes(false);
                resMsg.setData("租户号不能为空！");
                return resMsg;
            }
        }
        JSONObject requestData = JSON.parseObject(requestJson);
        GenerateSqlTran generateSqlTran = this.generateSql.sqlQuery("project", "S_ACTIVATION_URL");
        JSONArray activationData = this.dbHelp.queryJson("project", generateSqlTran);
        String requestUrl = "";
        if (activationData.size() > 0) {
            requestUrl = activationData.getJSONObject(0).getString("ACTIVATION_URL");
        }
        HttpClient httpClients = new HttpClient(requestUrl + "/customerActivation/activation", false);
        try {

            httpClients.addParameter("licenseCode", requestData.getString("LICENSE_CODE"));
            httpClients.addParameter("socialCreditCode", requestData.getString("SOCIAL_CREDIT_CODE"));
            httpClients.addParameter("buysSystem", requestData.getString("BUYS_SYSTEM"));
            httpClients.addParameter("environmentType", requestData.getString("ENVIRONMENT_TYPE"));
            httpClients.post();
            JSONObject returnData = JSONObject.parseObject(httpClients.getContent());
            if (!returnData.getBoolean("res")) {
                resMsg.setRes(false);
                resMsg.setData(returnData.getString("data"));
                return resMsg;
            }

            List<GenerateSqlTran> tranList = new ArrayList<>();
            JSONObject data = JSONObject.parseObject(returnData.getString("data"));
            JSONObject activadata = data.getJSONObject("ACTIVATION_DATA");
            JSONArray renewData = data.getJSONArray("RENEW_DATE");
            List<Wheres> wheres = new ArrayList<>();
            wheres.add(new Wheres("CUSTOMER_ACTIVATION_KEY", activadata.getString("CUSTOMER_ACTIVATION_KEY")));
            tranList.add(this.generateSql.executeDelete("project", "S_ACTIVATION_INFO", wheres));
            tranList.add(this.generateSql.executeDelete("project", "S_ACTIVATION_RENEW", wheres));
            JSONObject saveData = new JSONObject();
            String activationKey = UUID.randomUUID().toString();
            saveData.put("ACTIVATION_KEY", activationKey);
            saveData.put("ACTIVATION_DATE", activadata.getString("ACTIVATION_DATE"));
            saveData.put("LICENSE_CODE", activadata.getString("LICENSE_CODE"));
            saveData.put("USE_DAY", activadata.getString("USE_DAY"));
            saveData.put("ENVIRONMENT_TYPE", activadata.getString("ENVIRONMENT_TYPE"));
            saveData.put("IS_ACTIVATION", activadata.getString("IS_ACTIVATION"));
            saveData.put("SOCIAL_CREDIT_CODE", activadata.getString("SOCIAL_CREDIT_CODE"));
            saveData.put("BUYS_SYSTEM", activadata.getString("BUYS_SYSTEM"));
            saveData.put("BUYS_TYPE", activadata.getString("BUYS_TYPE"));
            saveData.put("BUYS_USER_NUM", activadata.getString("BUYS_USER_NUM"));
            saveData.put("CUSTOMER_ACTIVATION_KEY", activadata.getString("CUSTOMER_ACTIVATION_KEY"));
            saveData.put("RENEW_DAY", activadata.getString("RENEW_DAY"));
            GenerateSqlTran generateSql = this.generateSql.executeInsert("project", "S_ACTIVATION_INFO", saveData);
            tranList.add(generateSql);
            tranList.add(new GenerateSqlTran("UPDATE S_ACTIVATION_INFO T SET EXPIRE_DATE=ACTIVATION_DATE+" + dbValue.getDateInterval("project", activadata.getInteger("USE_DAY")+ activadata.getInteger("RENEW_DAY"), DateIntervalType.day) + " WHERE 1=1 AND T.ACTIVATION_KEY='" + activationKey + "'"));
            for (int i = 0; i < renewData.size(); i++) {
                renewData.getJSONObject(i).put("ACTIVATION_KEY", activationKey);
            }
            this.generateSql.executeListInsert("project", "S_ACTIVATION_RENEW", tranList, renewData);
            int res = this.dbHelp.executeSqlTran("project", tranList);
            if (res == 0) {
                resMsg.setRes(false);
                resMsg.setData("平台请求成功本地数据初始化失败！");
                return resMsg;
            }

            resMsg = activationConfig.save(saveData,tenantId);
            if (!resMsg.getRes()) {
                resMsg.setRes(false);
                resMsg.setData("本地激活失败："+resMsg.getData());
                return resMsg;
            }
            httpClients.setUrl(httpClients.getUrl()+"Success");
            httpClients.post();
        } catch (Exception ex) {
            httpClients.close();
            resMsg.setRes(false);
            resMsg.setData("请求激活异常：" + ex);
            return resMsg;
        } finally {
            httpClients.close();
        }
        return resMsg;
    }

    @PostMapping("/getActivationData")
    public ResMsg getActivationData() throws Exception {
        ResMsg resMsg = new ResMsg();
        String tenantId="";

        if(!tenant.getSingle()){
            tenantId=this.getHeader("tenant");
        }
        resMsg.setData(activationConfig.getActivationData(tenantId));
        return resMsg;
    }

}
