package com.xioahei.laundryhelper.Service;

import com.xioahei.laundryhelper.Utils.ApiUtil;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

import java.util.List;
@org.springframework.stereotype.Service
public class Service {
    @Resource
    ApiUtil apiUtil;



    public String OCR(String base64Image) {

        try {
            List<String> texts = apiUtil.OCR(base64Image);
            if (texts != null && !texts.isEmpty()) {
                return String.join("\n", texts);
            } else {
                return "未识别到文本";
            }
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
        return "失败";
    }
    public String YOLO(String base64Image)
    {
        try {
            String texts = apiUtil.Yolo(base64Image);

            if (texts != null && !texts.isEmpty()) {
               return String.join(",", texts);
            } else {
                return "未识别到文本";
            }
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
        return "失败";
    }
    public String Msg(String msg)
    {
        String texts = apiUtil.getContent(msg);
        return texts;
    }
}
