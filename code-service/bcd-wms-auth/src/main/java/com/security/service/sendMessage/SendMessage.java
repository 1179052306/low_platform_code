package com.security.service.sendMessage;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.security.oauth2.config.BasePage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lw
 * @date: 2025/1/16
 * @description:
 **/
@Service
public class SendMessage extends BasePage {

    public void sendMessageByListUser(String dbName, String title, String context, String endDate) throws Exception {

        List<GenerateSqlTran> tranList = new ArrayList<>();
        JSONArray filter = new JSONArray();
        this.generateSql.add(filter, "NEWS_TITLE", title);
        this.generateSql.add(filter, "END_DATE", "<=", endDate);
        GenerateSqlTran newsSqlTran = this.generateSql.sqlQuery("project" + dbName, "S_NEWS", filter);
        tranList.add(newsSqlTran);
//        GenerateSqlTran jurisdctionSqlTran = this.generateSql.sqlQuery("project" + dbName, "S_JURISDICTION");
//        tranList.add(jurisdctionSqlTran);
        JSONObject data = this.dbHelp.queryTranJsonObject("project" + dbName, tranList);
        JSONArray newsData = data.getJSONArray("S_NEWS");
        JSONArray jursdictionData = data.getJSONArray("S_JURISDICTION");
        if (newsData.size() > 0) {
            return;
        }
        List<GenerateSqlTran> editSqlTran = new ArrayList<>();
        List<Wheres>wheres=new ArrayList<>();
        wheres.add(new Wheres("NEWS_TITLE",title));
        editSqlTran.add(this.generateSql.executeDelete("project" + dbName, "S_NEWS", wheres));
        String newKey = this.dbValue.getSeqValue("project" + dbName, "S_NEWS");
        String startData = this.dbValue.getSysDateVal("project" + dbName, "yyyy-MM-dd");
        JSONObject formData = new JSONObject();
        formData.put("NEWS_KEY", newKey);
        formData.put("NEWS_TITLE", title);
        formData.put("NEWS_CONTENT", context);
        formData.put("NEWS_TYPE", "通知栏");
        formData.put("START_DATE",startData+" 00:00:01");
        formData.put("END_DATE", endDate);
        formData.put("IS_ALL", 1);

        editSqlTran.add(this.generateSql.executeInsert("project" + dbName, "S_NEWS", formData));
        this.dbHelp.executeSqlTran("project" + dbName,editSqlTran);
    }

    public void deleteMessage(String dbName, String title) throws Exception {
        List<GenerateSqlTran> editSqlTran = new ArrayList<>();
        List<Wheres>wheres=new ArrayList<>();
        wheres.add(new Wheres("NEWS_TITLE",title));
        editSqlTran.add(this.generateSql.executeDelete("project" + dbName, "S_NEWS", wheres));
        this.dbHelp.executeSqlTran("project" + dbName,editSqlTran);
    }
}
