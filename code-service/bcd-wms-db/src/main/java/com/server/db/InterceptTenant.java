package com.server.db;

import com.server.tenant_http.TenantHttpContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author lw
 * @date: 2025/2/26
 * @description:
 **/

@Service
public class InterceptTenant {





    public  String getTenantHeader()  {

        String tenant="";
//        tenantName= TenantHttpContext.getHeader("tenant");
        try{
            if(TenantHttpContext.getHeader("tenant")!=null){
                tenant= TenantHttpContext.getHeader("tenant");
            }else{
                tenant= MultiDataSourceHolder.getDatasource();
            }

        }catch (Exception exception){
            try {
                tenant= MultiDataSourceHolder.getDatasource();;
            }catch (Exception ex){

            }
        }

        return  tenant;
    }
}
