package com.api.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;

/**
 * @author lw
 * @date: 2025/2/28
 * @description:
 **/
public class MapCommon {

    public static List<Map<String, Object>> convertMap(JSONArray data){

        List<Map<String, Object>> mapList=new ArrayList<>();

        for (int i = 0; i <data.size() ; i++) {

            JSONObject item=data.getJSONObject(i);
            Iterator<String> sIterator =item.keySet().iterator();
            Map<String, Object> map = new HashMap<>();
            //循环并得到key列表
            while (sIterator.hasNext()) {
                // 获得key
                String key = sIterator.next();
                Object value = item.get(key);

                map.put(key.toUpperCase(Locale.ROOT), value);
            }
            mapList .add(map);
        }
        return mapList;
    }
}
