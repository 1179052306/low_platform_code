package com.security.oauth2.config;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.utils.StringUtils;
import com.server.db.DbHelp;
import com.server.db.InterceptTenant;
import com.server.db.Tenant;
import com.server.generate.GenerateSqlImpl;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@EnableAspectJAutoProxy(exposeProxy = true)
public class UserService implements UserDetailsService {


    @Resource
    private DbHelp dbHelp;

    @Resource
    private GenerateSqlImpl generateSql;

    @Resource
    private ActivationConfig activationConfig;

    @Resource
    Tenant tenant;

    @Resource
    InterceptTenant interceptTenant;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User userDetails = new User();
        try {
            JSONArray json = new JSONArray();

            generateSql.add(json, "USER_ID", username);
            JSONArray data = dbHelp.queryJson("project", generateSql.sqlQuery("project", "S_USER", json));
            if (!data.isEmpty()) {
                JSONObject row = data.getJSONObject(0);
                userDetails.setUserKey(row.getString("USER_KEY"));
                userDetails.setUsername(row.getString("USER_ID"));
                userDetails.setUserNameDesc(row.getString("USER_NAME"));
                userDetails.setPassword(row.getString("PASSWORD"));
                userDetails.setJurisdiction(row.getString("JURISDICTION_KEY"));
                userDetails.setUserType(Integer.parseInt(row.getString("USER_TYPE")));
                userDetails.setSupplierKey(-1);
                if (!StringUtils.isBlank(row.getString("SUPPLIER_KEY"))) {
                    userDetails.setSupplierKey(Integer.parseInt(row.getString("SUPPLIER_KEY")));
                }
                if(tenant.getSingle()){
                    userDetails.setActivationInfo(activationConfig.getActivationData("").toString());
                }else{
                    userDetails.setTenantId(interceptTenant.getTenantHeader());
                    userDetails.setActivationInfo(activationConfig.getActivationData(interceptTenant.getTenantHeader()).toString());
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        return userDetails;
    }

    public void required() {
    }
}
