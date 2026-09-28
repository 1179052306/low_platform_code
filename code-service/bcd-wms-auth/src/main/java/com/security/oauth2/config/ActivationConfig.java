package com.security.oauth2.config;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.encryption.DESUtils;
import com.common.file.FileHelp;
import com.common.returns.ResMsg;
import com.security.common.CrossPlatformHardwareInfo;
import com.security.model.ActivationModel;
import com.security.service.sendMessage.SendMessage;
import com.server.db.DbHelp;
import com.server.db.Tenant;
import com.server.generate.GenerateSqlImpl;
import com.server.tenant.TenantData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lw
 * @date: 2025/1/3
 * @description:
 **/
@Service
@Slf4j
public class ActivationConfig {
    @Autowired
    CrossPlatformHardwareInfo crossPlatformHardwareInfo;
    @Autowired
    GenerateSqlImpl generateSql;
    @Autowired
    DbHelp dbHelp;
    @Autowired
    SendMessage sendMessage;

    @Autowired
    Tenant tenant;
    @Autowired
    TenantData tenantData;

    public void init() throws Exception {
        List<String> list = new ArrayList<>();
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("5");
        list.add("6");
        if (tenant.getSingle()) {
            for (int i = 0; i < list.size(); i++) {
                load(list.get(i), "");
            }
        } else {
            
            JSONArray data = tenantData.getTenant();
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                for (int j = 0; j < list.size(); j++) {
                    load(list.get(j), item.getString("TENANT_ID"));
                }
            }


//            String path = System.getProperty("user.dir");
//            path = java.net.URLDecoder.decode(path, "utf-8");
//            File macFile = new File(path);
//            File[] files = macFile.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    if (file.isDirectory()) {
//                        for (int i = 0; i < list.size(); i++) {
//                            load(list.get(i), file.getName());
//                        }
//                    }
//                }
//            }
        }

    }

    public JSONArray getActivationData(String tenantId) throws Exception {
        expire(tenantId);
        JSONArray data = new JSONArray();
        if (tenant.getSingle()) {
            data=getActivationAllData(ActivationInfo.activationInfo);

        } else {
            if (ActivationInfo.tenantActivationInfo.get(tenantId) == null) {
                ActivationInfo.tenantActivationInfo.put(tenantId,new ArrayList<>());
            }
            data=getActivationAllData(ActivationInfo.tenantActivationInfo.get(tenantId));

        }
        return data;
    }
    public JSONArray getActivationAllData(List<ActivationModel> activationInfo) throws Exception {
        JSONArray data = new JSONArray();
        for (int i = 0; i < activationInfo.size(); i++) {
            JSONArray activationItem = activationInfo.get(i).getData();
            if (activationItem.size() == 1) {
                JSONObject row = activationItem.getJSONObject(0);
                data.add(row);
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                List<JSONObject> sortedList = activationItem.stream()
                        .map(obj -> (JSONObject) obj)
                        .sorted(Comparator.comparing((JSONObject obj) -> {
                            try {
                                return dateFormat.parse(obj.getString("ACTIVATION_DATE"));
                            } catch (ParseException e) {
                                throw new RuntimeException(e);
                            }
                        }).reversed())
                        .collect(Collectors.toList());
                data.add(sortedList.get(0));
            }
        }
        return data;
    }
    public void expire(String tenantId) {

        if (tenant.getSingle()) {
            expireItem(ActivationInfo.activationInfo);
        } else {
            if (ActivationInfo.tenantActivationInfo.get(tenantId) == null) {
                ActivationInfo.tenantActivationInfo.put(tenantId,new ArrayList<>());
            }
            expireItem(ActivationInfo.tenantActivationInfo.get(tenantId));

        }

    }

    public void expireItem(List<ActivationModel> activationInfo) {
        for (int i = 0; i < activationInfo.size(); i++) {
            ActivationModel system = activationInfo.get(i);
            JSONArray data = system.getData();
            for (int j = 0; j < data.size(); j++) {
                JSONObject row = data.getJSONObject(j);
                String activationDateString = row.getString("ACTIVATION_DATE");
                long useDay = row.getLong("USE_DAY");
                long renewDay = row.getLong("RENEW_DAY");
                String buysSystem = row.getString("BUYS_SYSTEM");
                LocalDateTime now = LocalDateTime.now();
                // 定义日期格式与解析的时间字符串相同
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .appendPattern("yyyy-MM-dd HH:mm:ss")
                        .optionalStart() // 开始一个可选部分
                        .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 9, true)
                        .optionalEnd() // 结束一个可选部分
                        .toFormatter();
                // 将时间字符串转换为 LocalDateTime 对象
                LocalDateTime parsedTime = LocalDateTime.parse(activationDateString, formatter);

                // 如果需要考虑时间和日期的完整差异（包括小时、分钟等），可以这样做：
                long totalDaysBetween = ChronoUnit.DAYS.between(parsedTime, now);
                String systemName = "";
                if (buysSystem.equals("2")) {
                    systemName = "WMS系统";
                }
                if (buysSystem.equals("3")) {
                    systemName = "TMS系统";
                }
                if (buysSystem.equals("1")) {
                    systemName = "供应商协同系统";
                }
                if (buysSystem.equals("5")) {
                    systemName = "OMS系统";
                }
                if (buysSystem.equals("6")) {
                    systemName = "YMS系统";
                }
                LocalDateTime futureDate = parsedTime.plusDays(useDay + renewDay);
                DateTimeFormatter futureFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String endDate = futureDate.format(futureFormatter);
                if ((useDay + renewDay) - totalDaysBetween <= 30) {
//                    sendMessage.deleteMessage("", systemName+"即将到期");
//                    sendMessage.sendMessageByListUser("", systemName+"即将到期", systemName+"剩余可用天数"+((useDay + renewDay) - totalDaysBetween)+"即将到期",endDate);
                }
                if ((useDay + renewDay) - totalDaysBetween <= 0) {
                    row.put("EXPIRE_FLAG", 1);
//                    sendMessage.sendMessageByListUser("", systemName+"已过期", "",endDate);
                } else {
                    row.put("EXPIRE_FLAG", 0);
//                    sendMessage.deleteMessage("", systemName+"已过期");
                }
                row.put("SURPLUS", useDay - totalDaysBetween);

            }

        }
    }

    public JSONArray load(String systemType, String tenantId) throws Exception {
        JSONArray data = new JSONArray();
        ResMsg resMsg = crossPlatformHardwareInfo.getSystemUuidData();
        if (!resMsg.getRes()) {
            log.error(resMsg.getData().toString());
            return new JSONArray();
        }
        String fileName = resMsg.getData().toString();
        String path = System.getProperty("user.dir");
        path = java.net.URLDecoder.decode(path, "utf-8");
        if (!tenant.getSingle()) {

            File macFile = new File(path + "/" + tenantId);
            if (!macFile.exists()) {
                log.warn("未找到租户文件夹");
                macFile.mkdir();
            }
            path += "/" + tenantId;
        }
        File macFile = new File(path + "/" + fileName);
        if (!macFile.exists()) {
            log.warn("系统" + systemType + "未注册，未找到注册相关文件夹");
            macFile.mkdir();
        } else {
            File macInfo = new File(path + "/" + fileName + "/" + fileName + ".ahbcd_" + systemType);
            if (macInfo.exists()) {
                try {
                    FileHelp fileHelp = new FileHelp();
                    String encryptionData = fileHelp.ReadFile(path + "/" + fileName + "/" + fileName + ".ahbcd_" + systemType + "");
                    try {
                        String decryptData = DESUtils.decryptByDES(encryptionData, fileName + tenantId);
                        try {
                            data = JSONArray.parseArray(decryptData);

                            if (tenant.getSingle()) {
                                resetActivationData(data, ActivationInfo.activationInfo, systemType);
                            } else {
                                if (ActivationInfo.tenantActivationInfo.get(tenantId) == null) {
                                    ActivationInfo.tenantActivationInfo.put(tenantId,new ArrayList<>());
                                }
                                resetActivationData(data, ActivationInfo.tenantActivationInfo.get(tenantId), systemType);
                            }

//                            Boolean isExist = false;
//                            for (int i = 0; i < ActivationInfo.activationInfo.size(); i++) {
//                                ActivationModel system = ActivationInfo.activationInfo.get(i);
//                                if (system.getSystemTpe().equals(systemType)) {
//                                    system.getData().clear();
//                                    system.getData().addAll(data);
//                                    isExist = true;
//                                }
//
//                            }
//                            if (!isExist) {
//                                ActivationModel system = new ActivationModel();
//                                system.setSystemTpe(systemType);
//                                system.setData(data);
//                                ActivationInfo.activationInfo.add(system);
//                            }
                            log.info("系统" + systemType + "注册文件读取成功");
                        } catch (Exception ex) {
                            log.error("系统" + systemType + "注册文件读取后转换数据异常" + ex);

                        }
                    } catch (Exception ex) {
                        log.error("系统" + systemType + "注册文件读取后解密异常" + ex);


                    }
                } catch (Exception ex) {
                    log.error("系统" + systemType + "注册文件读取异常" + ex);

                }
            } else {

                if (tenant.getSingle()) {
                    removeActivationData(ActivationInfo.activationInfo, systemType);
                } else {
                    if (ActivationInfo.tenantActivationInfo.get(tenantId) == null) {
                        ActivationInfo.tenantActivationInfo.put(tenantId,new ArrayList<>());
                    }
                    removeActivationData(ActivationInfo.tenantActivationInfo.get(tenantId), systemType);

                }
//                Boolean isExist = false;
//                int removeIndex = 0;
//                for (int i = 0; i < ActivationInfo.activationInfo.size(); i++) {
//                    ActivationModel system = ActivationInfo.activationInfo.get(i);
//                    if (system.getSystemTpe().equals(systemType)) {
//                        removeIndex = i;
//                        isExist = true;
//                    }
//
//                }
//                if (isExist) {
//                    ActivationInfo.activationInfo.remove(removeIndex);
//                }
//                List<GenerateSqlTran> tranList = new ArrayList<>();
//                List<Wheres> wheres = new ArrayList<>();
//                wheres.add(new Wheres("BUYS_SYSTEM", systemType));
//                tranList.add(this.generateSql.executeDelete("project", "S_ACTIVATION_INFO", wheres));
//                tranList.add(this.generateSql.executeDelete("project", "S_ACTIVATION_RENEW", wheres));
//                int res = this.dbHelp.executeSqlTran("project", tranList);
            }
        }
        expire(tenantId);
        return data;
    }

    public void removeActivationData(List<ActivationModel> activationInfo, String systemType) throws Exception {
        Boolean isExist = false;
        int removeIndex = 0;
        for (int i = 0; i < activationInfo.size(); i++) {
            ActivationModel system = activationInfo.get(i);
            if (system.getSystemTpe().equals(systemType)) {
                removeIndex = i;
                isExist = true;
            }

        }
        if (isExist) {
            activationInfo.remove(removeIndex);
        }
    }

    public void resetActivationData(JSONArray data, List<ActivationModel> activationInfo, String systemType) throws Exception {
        Boolean isExist = false;
        for (int i = 0; i < activationInfo.size(); i++) {
            ActivationModel system = activationInfo.get(i);
            if (system.getSystemTpe().equals(systemType)) {
                system.getData().clear();
                system.getData().addAll(data);
                isExist = true;
            }

        }
        if (!isExist) {
            ActivationModel system = new ActivationModel();
            system.setSystemTpe(systemType);
            system.setData(data);
            activationInfo.add(system);
        }
    }

    public ResMsg save(JSONObject activationData, String tenantId) throws Exception {
        ResMsg resMsg = new ResMsg();
        try {
            resMsg = crossPlatformHardwareInfo.getSystemUuidData();
            if (!resMsg.getRes()) {
                log.error(resMsg.getData().toString());
                return resMsg;
            }
            String fileName = resMsg.getData().toString();
            String path = System.getProperty("user.dir");
            path = java.net.URLDecoder.decode(path, "utf-8");
            if (!tenant.getSingle()) {

                File macFile = new File(path + "/" + tenantId);
                if (!macFile.exists()) {
                    log.warn("系统租户未注册，正在注册相关文件夹");
                    macFile.mkdir();
                }
                path += "/" + tenantId;
            }
            File macFile = new File(path + "/" + fileName);
            if (!macFile.exists()) {
                log.warn("系统未注册，正在注册相关文件夹");
                macFile.mkdir();
            }

            String systemType = activationData.getString("BUYS_SYSTEM");
            String customerActivationKey = activationData.getString("CUSTOMER_ACTIVATION_KEY");
            JSONArray historyActivationData = load(systemType, tenantId);
            boolean isExist = false;
            for (int i = 0; i < historyActivationData.size(); i++) {
                JSONObject system = historyActivationData.getJSONObject(i);
                if (system.getString("CUSTOMER_ACTIVATION_KEY").equals(customerActivationKey)) {
                    system.putAll(activationData);
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                historyActivationData.add(activationData);
            }
            String encryptData = DESUtils.encryptByDES(historyActivationData.toString(), fileName + tenantId);
            FileHelp fileHelp = new FileHelp();
            fileHelp.createFile(path + "/" + fileName, fileName + ".ahbcd_" + systemType + "", encryptData);
            historyActivationData = load(systemType, tenantId);
            JSONArray existData = new JSONArray(historyActivationData.stream().filter(item -> ((JSONObject) item).getString("CUSTOMER_ACTIVATION_KEY").equals(customerActivationKey)).collect(Collectors.toList()));
            if (existData.size() == 0) {
                resMsg.setRes(false);
                resMsg.setData("本地服务器激活写入失败");
                return resMsg;
            }
            resMsg.setData("激活成功");
            expire(tenantId);
            return resMsg;
        } catch (Exception e) {
            resMsg.setRes(false);
            resMsg.setData("本地服务器激活失败：" + e.toString());
            return resMsg;
        }

    }


}
