package com.qyxa.java.tools.component.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * @author sunyuxuan <sunyuxuan@kuaishou.com>
 * Created on 2024-02-20
 */

@Slf4j
public class HttpUtils {

    @SuppressWarnings("checkstyle:MagicNumber")
    public static String doGet(String httpUrl) {
        StringBuffer result = new StringBuffer();
        HttpURLConnection connection = null; //链接
        try {
            URL url = new URL(httpUrl); //创建连接
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET"); //设置请求方式
            connection.setReadTimeout(15000); //设置连接超时时间
            connection.connect(); //开始连接
            if (connection.getResponseCode() == 200) { //获取响应数据
                InputStream is = connection.getInputStream(); //获取返回的数据
                if (null != is) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String temp = null;
                    while (null != (temp = br.readLine())) {
                        log.info("line str : {}", temp);
                        result.append(temp);
                    }
                    log.info("result: {}", result);
                    br.close();
                    is.close();
                }
            } else {
                log.info("url: {} request failed!", httpUrl);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect(); //关闭远程连接
            }
        }
        return result.toString();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static String doPost(String httpUrl, @Nullable String param) {
        StringBuffer result = new StringBuffer();
        HttpURLConnection connection = null;
        try {

            URL url = new URL(httpUrl); //创建连接对象
            connection = (HttpURLConnection) url.openConnection(); //创建连接
            connection.setRequestMethod("POST"); //设置请求方法
            connection.setConnectTimeout(3000); //设置连接超时时间
            //connection.setReadTimeout(15000); //设置读取超时时间
            //DoOutput设置是否向httpUrlConnection输出，DoInput设置是否从httpUrlConnection读入，发送post请求必须设置这两个
            connection.setDoOutput(true); //设置是否可以输出
            connection.setDoInput(true); //设置是否可以读入
            //设置缓存
            //connection.setUseCaches(false);
            //设置请求头(header)
            //connection.setRequestProperty("accept", "*/*");
            //connection.setRequestProperty("connection", "Keep-Alive");
            //connection.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            connection.setRequestProperty("Content-Type", "application/json");
            //设置权限...
            connection.connect(); //开启连接
            if (param != null) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                writer.write(param); // 设置请求体(body)
                writer.close();
            }
            if (connection.getResponseCode() == 200) { //读取响应
                InputStream is = connection.getInputStream();
                if (null != is) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "GBK"));
                    String temp = null;
                    while (null != (temp = br.readLine())) {
                        log.info("line str : {}", temp);
                        result.append(temp);
                        result.append("\r\n");
                    }
                    log.info("result: {}", result);
                    br.close();
                    is.close();
                }
            } else {
                log.info("url: {} request failed!", httpUrl);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect(); //关闭连接
            }
        }
        return result.toString();
    }
}
