package com.atguigu.guliai.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class FileUtil {

    /**
     * 从文件中读取文本内容
     * @param file MultipartFile
     * @return
     */
    public static String getContentFromFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        try {
            // 优先使用文件名后缀判断类型
            if (originalFilename.endsWith(".txt")) {
                return getContentFromText(file);
            } else if (originalFilename.endsWith(".pdf")) {
                return getContentFromPdf(file);
            } else if (originalFilename.endsWith(".doc") || originalFilename.endsWith(".docx")) {
                return getContentFromWord(file);
            } else {
                log.error("不支持的文件类型: {}", originalFilename);
                throw new RuntimeException("仅支持txt、pdf、doc/docx文件");
            }
        } catch (Exception e) {
            log.error("文件解析异常: {}", e.getMessage());
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 读取普通文本的内容
     * @param file
     * @return
     */
    public static String getContentFromText(MultipartFile file) {
        Resource resource = file.getResource();
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取word的文本内容
     * @param file
     * @return
     */
    private static String getContentFromWord(MultipartFile file) {
        try (HWPFDocument document = new HWPFDocument(file.getInputStream())) {
            WordExtractor extractor = new WordExtractor(document);
            return String.join("\n", extractor.getParagraphText());
        } catch (Exception e) {
            log.error("Word解析失败: {}", e.getMessage());
            throw new RuntimeException("Word文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 读取pdf的文本内容
     * @param file
     * @return
     */
    public static String getContentFromPdf(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            } else {
                log.error("加密PDF文件不支持解析: {}", file.getOriginalFilename());
                return null;
            }
        } catch (Exception e) {
            log.error("PDF解析失败: {}", e.getMessage());
            throw new RuntimeException("PDF文件解析失败: " + e.getMessage());
        }
    }
}
