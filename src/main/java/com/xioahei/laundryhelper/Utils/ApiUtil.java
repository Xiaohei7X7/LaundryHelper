package com.xioahei.laundryhelper.Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Component
public class ApiUtil {

    @Value("${api.llm.url}")
    private String OCR_API_URL;
    @Value("${api.llm.key}")
    private String API_KEY;
    @Value("${api.yolo.url}")
    private String YOLO_API_URL;
    public List<String> OCR(String base64Image) throws Exception {
        // 1. 读取图片并转成base64

        // 2. 构建请求体
        String requestBody = String.format("""
            {
                "model": "PaddlePaddle/PaddleOCR-VL",
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": "data:image/jpeg;base64,%s"
                                }
                            },
                            {
                                "type": "text",
                                "text": "<image>\\n<|grounding|>OCR this image."
                            }
                        ]
                    }
                ]
            }
            """, base64Image);

        // 3. 发送HTTP请求
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OCR_API_URL))
                .header("Authorization", "Bearer "+ API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body();
        //System.out.println(responseBody);
        // 4. 解析响应
        if (response.statusCode() == 200) {
           // String content = parseContent(response.body());
            // 提取<|ref|>标签中的文本
            List<String> texts = new ArrayList<>();
//            Pattern pattern = Pattern.compile("<\\|ref\\|>(.*?)<\\|/ref\\|>");
//            Matcher matcher = pattern.matcher(content);
//            while (matcher.find()) {
//                texts.add(matcher.group(1));
//            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);

            // 提取 content 内容
            String content = rootNode
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
            return Collections.singletonList(content);
        } else {
            System.out.println("请求失败，状态码: " + response.statusCode());
            return null;
        }
    }
    public String Yolo(String base64Image) throws Exception {
        // 1. 读取图片并转成base64

        // 2. 构建请求体 (参数名: image_base64)
        String requestBody = String.format("{\"image_base64\": \"%s\"}", base64Image);

        // 3. 创建HTTP客户端
        HttpClient client = HttpClient.newHttpClient();

        // 4. 构建POST请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(YOLO_API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 5. 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper objectMapper = new ObjectMapper();

        // 解析JSON字符串为JsonNode
        JsonNode rootNode = objectMapper.readTree(response.body());

        // 获取detections数组
        JsonNode detectionsArray = rootNode.get("detections");
        // 影射英文-中文
        Map<String, String> results = StringMapper.readPropertiesToMap("ClassesMapper.properties");
        // 存储所有class_name的列表
        List<String> classNames = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        // 遍历detections数组
        if (detectionsArray.isArray()) {
            for (JsonNode detection : detectionsArray) {
                // 获取每个检测对象的class_name字段
                String className = detection.get("class_name").asText();
                classNames.add(className);
            }
        }
        // 6. 返回结果
        for( String className : classNames ) {
            labels.add(results.get(className));
        }
        return String.join(",", labels);
    }

    private String parseContent(String jsonResponse) {
        // 简单解析JSON获取content字段
        String contentKey = "\"content\":\"";
        int start = jsonResponse.indexOf(contentKey) + contentKey.length();
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    public JsonNode sendChatRequest(String userMessage) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        // 1. 构建请求体 (对应fetch的body)
        String requestBody = buildRequestBody(userMessage);

        // 2. 创建HttpRequest (对应fetch的配置)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OCR_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        //System.out.println("发送请求到: " + OCR_API_URL);
        //System.out.println("请求体: " + requestBody);

        // 3. 发送请求并获取响应 (对应fetch的fetch().then())
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        // 4. 检查响应状态
        if (response.statusCode() != 200) {
            throw new IOException("HTTP请求失败，状态码: " + response.statusCode());
        }

        // 5. 解析JSON (对应response.json())
        String responseBody = response.body();
        return objectMapper.readTree(responseBody);
    }

    /**
     * 构建请求体 - 使用Jackson构建JSON
     */
    private String buildRequestBody(String userMessage) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "deepseek-ai/DeepSeek-V3");

        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是洗衣助手，给出指定的洗衣建议，完整的洗衣流程");

        ObjectNode userMessageNode = messages.addObject();
        userMessageNode.put("role", "user");
        userMessageNode.put("content", userMessage);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * 只获取content内容
     */
    public String getContent(String userMessage) {
        try {
            JsonNode response = sendChatRequest(userMessage);

            // 提取content: choices[0].message.content
            String content = response
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText();

            return content;

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
            return null;
        }
    }

}
