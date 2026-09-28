package com.server.db;

import org.springframework.stereotype.Service;

@Service
public class MultiDataSourceHolder {
    private static final ThreadLocal<String> threadLocal =new ThreadLocal<>();

    public static void setDatasource(String datasource){
        threadLocal.set(datasource);
    }

    public static String getDatasource(){
        return threadLocal.get();
    }

    public static void clearDataSource(){
        threadLocal.remove();
    }

}
