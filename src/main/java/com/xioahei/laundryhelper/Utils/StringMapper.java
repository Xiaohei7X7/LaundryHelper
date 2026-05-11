package com.xioahei.laundryhelper.Utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class StringMapper {
    public static Map<String, String> readPropertiesToMap(String fileName) {
        Map<String, String> map = new HashMap<>();
        Properties properties = new Properties();

        try (InputStream inputStream = StringMapper.class.getClassLoader()
                .getResourceAsStream(fileName)) {

            if (inputStream == null) {
                System.err.println("找不到文件: " + fileName);
                return map;
            }

            // 加载properties文件
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            // 将Properties转换为Map
            for (String key : properties.stringPropertyNames()) {
                map.put(key, properties.getProperty(key));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }
}
