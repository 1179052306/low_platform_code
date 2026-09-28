package com.api.config.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbHelp;
import com.server.db.MultiDataSourceHolder;
import com.server.db.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lw
 * @date: 2023/12/12
 * @description:
 **/
@Slf4j
@Configuration
@Service
public class ClientVersionValidate {

    @Resource
    private DbHelp dbHelp;

    private String dbName = "project";

    public static JSONObject version = null;
    @Resource
    Tenant tenant;
    public static Map<String, JSONObject> versionList = new HashMap<>();

    public boolean redisValidate(String mobileVersion, JSONObject mobileVersionMode, String tenantId) {

        try {
            if (tenant.getSingle()) {
                if (version == null) {
                    setdbValidate(tenantId);
                }
                if (version != null) {
                    if (!version.getString("CLIENT_UPDATE_VERSION").equals(mobileVersion)) {
                        return dbValidate(mobileVersion, mobileVersionMode, tenantId);
                    }
                }
                return true;
            } else {

                if (versionList.get(tenantId) == null) {
                    setdbValidate(tenantId);

                }
                if (versionList.get(tenantId) != null) {
                    if (!versionList.get(tenantId).getString("CLIENT_UPDATE_VERSION").equals(mobileVersion)) {
                        return dbValidate(mobileVersion, mobileVersionMode, tenantId);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.error("redis出现异常！异常信息:" + e);
            return dbValidate(mobileVersion, mobileVersionMode, tenantId);
        }

    }

    public void setdbValidate(String tenantId) {
        if (!tenant.getSingle()) {

            MultiDataSourceHolder.setDatasource(tenantId);
        }
        GenerateSqlTran generateSqlTran = new GenerateSqlTran();
        generateSqlTran.set_strsql("SELECT CLIENT_UPDATE_NAME,CLIENT_UPDATE_VERSION,CLIENT_UPDATE_URL,CLIENT_UPDATE_CONTEXT,CLIENT_UPDATE_SIZE FROM S_CLIENT_UPDATE T WHERE 1=1 AND T.ACTIVE=1  ORDER BY T.CREATE_DATE DESC ");
        try {
            JSONArray data = dbHelp.queryJson(dbName, generateSqlTran);

            if (data.size() > 0) {
                if (tenant.getSingle()) {
                    version = data.getJSONObject(0);
                } else {
                    versionList.put(tenantId, data.getJSONObject(0));
                }
            }

        } catch (Exception e) {
            log.error("数据处理出现异常! 异常原因:" + e);
        }

    }

    public boolean dbValidate(String mobileVersion, JSONObject mobileVersionMode, String tenantId) {
        if (!tenant.getSingle()) {
            MultiDataSourceHolder.setDatasource(tenantId);
        }
        try {

            GenerateSqlTran generateSqlTran = new GenerateSqlTran();
            generateSqlTran.set_strsql("SELECT CLIENT_UPDATE_NAME,CLIENT_UPDATE_VERSION,CLIENT_UPDATE_URL,CLIENT_UPDATE_CONTEXT,CLIENT_UPDATE_SIZE FROM S_CLIENT_UPDATE T WHERE 1=1 AND T.ACTIVE=1  ORDER BY T.CREATE_DATE DESC ");
            JSONArray data = dbHelp.queryJson(dbName, generateSqlTran);
            if (data.size() == 0) {
                return true;
            }
            mobileVersionMode.put("CLIENT_UPDATE_NAME", data.getJSONObject(0).getString("CLIENT_UPDATE_NAME"));
            mobileVersionMode.put("CLIENT_UPDATE_VERSION", data.getJSONObject(0).getString("CLIENT_UPDATE_VERSION"));
            mobileVersionMode.put("CLIENT_UPDATE_URL", data.getJSONObject(0).getString("CLIENT_UPDATE_URL"));
            mobileVersionMode.put("CLIENT_UPDATE_CONTEXT", data.getJSONObject(0).getString("CLIENT_UPDATE_CONTEXT"));
            mobileVersionMode.put("CLIENT_UPDATE_SIZE", data.getJSONObject(0).getString("CLIENT_UPDATE_SIZE"));
            version = data.getJSONObject(0);
            if (tenant.getSingle()) {
                version = data.getJSONObject(0);
            } else {
                this.versionList.put(tenantId, data.getJSONObject(0));
            }
            if (!data.getJSONObject(0).getString("CLIENT_UPDATE_VERSION").equals(mobileVersion)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
