package com.xioahei.laundryhelper.Controller;

import com.xioahei.laundryhelper.Utils.ApiUtil;
import com.xioahei.laundryhelper.Service.Service;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
@CrossOrigin
@RestController
public class ApiController {
    @Resource
    Service service;
    @Resource
    ApiUtil apiUtil;

    @PostMapping("/upload")
    public String answer(@RequestParam("images") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return "请选择要上传的文件";
        }
        StringBuilder TempQuestion = new StringBuilder();
        for (MultipartFile file : files) {
            // 处理每个文件
            String fileName = file.getOriginalFilename();
            long fileSize = file.getSize();
            try{
                byte[] bytes = file.getBytes();
                String ImageBase64 = Base64.getEncoder().encodeToString(bytes);
                TempQuestion.append(service.OCR(ImageBase64));
                TempQuestion.append("\r\n");
                TempQuestion.append(service.YOLO(ImageBase64));
                TempQuestion.append("\r\n");
            }
            catch (IOException e)
            {
                System.out.println("读取失败");
            }
            System.out.println("文件名：" + fileName + "，大小：" + fileSize);
            // 保存文件逻辑
            // file.transferTo(new File("存储路径/" + fileName));
        }
        String answer = apiUtil.getContent(TempQuestion.toString());
        System.out.println(answer);
        return answer;
    }
}
