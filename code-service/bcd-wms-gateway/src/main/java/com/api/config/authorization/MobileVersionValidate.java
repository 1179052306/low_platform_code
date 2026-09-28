package com.api.config.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbHelp;
import com.server.db.MultiDataSourceHolder;
import com.server.db.Tenant;
import com.server.redis.RedisService;
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
public class MobileVersionValidate {

    @Resource
    private DbHelp dbHelp;
    @Resource
    private RedisService redisService;

    private String dbName = "project";

    @Resource
    Tenant tenant;

    public static JSONObject version=null;
    public static  Map<String, JSONObject> versionList=new HashMap<>();

    public boolean redisValidate(String mobileVersion, JSONObject mobileVersionMode,  String tenantId) {

        try {

            if(tenant.getSingle()){

                if (version == null) {
                    setdbValidate(tenantId);
                }
                if(version!=null){

                    if (!version.getString("PDA_UPDATE_VERSION").equals(mobileVersion)) {

                        return dbValidate(mobileVersion, mobileVersionMode,tenantId);
                    }
                }
            }else{

                if(versionList.get(tenantId)==null){
                    setdbValidate(tenantId);

                }
                if(versionList.get(tenantId)!=null){
                    if (!versionList.get(tenantId).getString("PDA_UPDATE_VERSION").equals(mobileVersion)) {
                        return dbValidate(mobileVersion, mobileVersionMode,tenantId);
                    }
                }


            }

            return true;

        } catch (Exception e) {
            log.error("redis出现异常！异常信息:" + e);
            return dbValidate(mobileVersion, mobileVersionMode,tenantId);
        }

    }

    public void setdbValidate(String tenantId) {
        if(!tenant.getSingle()){

            MultiDataSourceHolder.setDatasource(tenantId);
        }
        GenerateSqlTran generateSqlTran = new GenerateSqlTran();
        generateSqlTran.set_strsql("SELECT PDA_UPDATE_NAME,PDA_UPDATE_VERSION,PDA_UPDATE_URL,PDA_UPDATE_CONTEXT,PDA_UPDATE_SIZE FROM S_PDA_UPDATE T WHERE 1=1 AND T.ACTIVE=1  ORDER BY T.CREATE_DATE DESC ");
        try {
            JSONArray data = dbHelp.queryJson(dbName, generateSqlTran);
            if (data.size() > 0) {
                if(tenant.getSingle()) {
                    version = data.getJSONObject(0);
                }else {
                    versionList.put(tenantId,data.getJSONObject(0));
                }
            }

        } catch (Exception e) {
            log.error("数据处理出现异常! 异常原因:" + e);
        }finally {
            MultiDataSourceHolder.clearDataSource();
        }

    }

    public boolean dbValidate(String mobileVersion, JSONObject mobileVersionMode,  String tenantId) {
        if(!tenant.getSingle()){
            MultiDataSourceHolder.setDatasource(tenantId);
        }
        try {

            GenerateSqlTran generateSqlTran = new GenerateSqlTran();
            generateSqlTran.set_strsql("SELECT PDA_UPDATE_NAME,PDA_UPDATE_VERSION,PDA_UPDATE_URL,PDA_UPDATE_CONTEXT,PDA_UPDATE_SIZE FROM S_PDA_UPDATE T WHERE 1=1 AND T.ACTIVE=1  ORDER BY T.CREATE_DATE DESC ");
            JSONArray data = dbHelp.queryJson(dbName, generateSqlTran);
            if (data.size() == 0) {
                return true;
            }
            mobileVersionMode.put("PDA_UPDATE_NAME",data.getJSONObject(0).getString("PDA_UPDATE_NAME"));
            mobileVersionMode.put("PDA_UPDATE_VERSION",data.getJSONObject(0).getString("PDA_UPDATE_VERSION"));
            mobileVersionMode.put("PDA_UPDATE_URL",data.getJSONObject(0).getString("PDA_UPDATE_URL"));
            mobileVersionMode.put("PDA_UPDATE_CONTEXT",data.getJSONObject(0).getString("PDA_UPDATE_CONTEXT"));
            mobileVersionMode.put("PDA_UPDATE_SIZE",data.getJSONObject(0).getString("PDA_UPDATE_SIZE"));
            if(tenant.getSingle()){
                version = data.getJSONObject(0);
            }else{
                this.versionList.put(tenantId,data.getJSONObject(0));
            }

            if (!data.getJSONObject(0).getString("PDA_UPDATE_VERSION").equals(mobileVersion)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            MultiDataSourceHolder.clearDataSource();
        }
        return false;
    }

}
