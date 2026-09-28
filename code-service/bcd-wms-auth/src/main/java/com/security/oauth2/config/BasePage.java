package com.security.oauth2.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.common.utils.StringUtils;
import com.server.db.DbHelp;
import com.server.generate.GenerateSqlImpl;
import com.server.getdata.GetTableData;
import com.server.syntax.val.DbValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.util.HtmlUtils; // 【新增】引入 Spring 的转义工具

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class BasePage {
    @Autowired
    protected HttpServletRequest request;

    @Autowired
    protected HttpServletResponse response;

    @Autowired
    protected HttpSession session;

    @Autowired
    protected ServletContext servletContext;

    @Resource
    protected DbHelp dbHelp;
    @Resource
    protected GetTableData getTableData;
    @Resource
    protected GenerateSqlImpl generateSql;

    @Resource
    protected DbValue dbValue;

    /**
     * 获取请求头中的数据
     */
    protected String getHeader(String headerName) throws UnsupportedEncodingException {
        String headValue = this.request.getHeader(headerName);
        if ("user_key".equals(headerName) && StringUtils.isBlank(headValue)) {
            return "-1";
        } else if (StringUtils.isBlank(headValue)) {
            return "";
        } else {
            // 建议：这里如果输出到页面也需要转义，但通常 Header 用于 API 认证，风险较低
            return URLDecoder.decode(headValue, "utf-8");
        }
    }

    /**
     * 获取请求中的数据
     *
     * @param Key
     * @return String
     **/
    protected String getRequestValues(String Key) {


        if (request.getParameterMap().get(Key) != null) {

            if ("null".equals(request.getParameterMap().get(Key)[0].toString())) {
                return getRequestRawValues(Key);
            }
            if ("".equals(request.getParameterMap().get(Key)[0].toString())) {
                return null;
            }
            return request.getParameterMap().get(Key)[0].toString();
        } else {

            return getRequestRawValues(Key);
        }

    }

    /**
     * 获取请求Raw的数据
     *
     * @param key
     * @return String
     **/
    protected String getRequestRawValues(String key) {

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream.available() > 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                    StringBuilder context = new StringBuilder();
                    String line;

                    while ((line = br.readLine()) != null) {
                        context.append(line);
                    }

                    JSONObject formData = JSON.parseObject(context.toString());

                    if (formData == null || formData.get(key) == null) {
//                    br.close();
                        return null;
                    }
//                br.close();
                    return formData.get(key).toString();
                } catch (IOException e) {

                    e.printStackTrace();
                    return null;
                }
            }
        } catch (Exception ex) {

        }

        return null;
    }


    // 下面的文件处理方法风险较低，保持原样即可
    protected MultipartFile getFileRequestValues(String Key) {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        MultipartHttpServletRequest multipartRequest = resolver.resolveMultipart(request);
        return multipartRequest.getFile(Key);
    }

    protected List<MultipartFile> getFileRequestArrValues(String Key) {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        MultipartHttpServletRequest multipartRequest = resolver.resolveMultipart(request);
        return multipartRequest.getFiles(Key);
    }
}