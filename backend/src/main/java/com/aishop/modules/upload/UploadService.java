package com.aishop.modules.upload;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.modules.upload.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务 — 处理商品图片和搜索图片的上传
 *
 * 四层文件校验机制：
 * 第 1 层：文件大小（由 Spring 的 multipart 配置控制，默认 10MB）
 * 第 2 层：MIME 类型（Content-Type 校验）
 * 第 3 层：文件扩展名（防止 MIME 类型伪造）
 * 第 4 层：文件头魔数（Magic Number，防止扩展名伪造）
 *
 * 存储策略：
 * - 按日期分目录存储（uploads/images/2026/06/）
 * - 使用 UUID 生成唯一文件名
 * - 保留原始文件名用于前端展示
 *
 * 修复记录（2026-06-08）：
 * - 修复文件保存路径问题：使用 user.dir 系统属性获取项目根目录，
 *   避免 Tomcat 临时目录导致的 FileNotFoundException
 *
 * 修复记录（2026-06-08 v2）：
 * - 增强 IOException 的捕获粒度，区分"文件读取失败"和"文件保存失败"
 * - 增加日志记录文件大小、MIME 类型等调试信息
 * - 修复空文件校验逻辑：file.isEmpty() 在部分实现中可能抛出异常，
 *   改为先检查 file == null 再检查 getSize() == 0
 *
 * 修复记录（2026-06-08 v3）：
 * - 修复第 2 层 MIME 校验：getContentType() 可能为 null，增加空指针保护
 * - 修复第 4 层魔数校验：读取流后未重置，导致后续 Files.copy 写入空内容。
 *   改用 mark/reset 机制，或重新获取 InputStream
 * - 修复空文件场景：空文件 getContentType() 返回 null，降级到后续校验层
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /**
     * 允许的 MIME 类型白名单
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * 允许的文件扩展名白名单
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    /**
     * 文件头魔数映射（扩展名 → 魔数字节数组）
     * 用于验证文件真实类型
     */
    private static final java.util.Map<String, byte[]> MAGIC_NUMBERS = java.util.Map.of(
            ".jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            ".jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            ".png", new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47},
            ".gif", new byte[]{(byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38},
            ".webp", new byte[]{(byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46}
    );

    /**
     * 最大文件大小（10MB）
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 上传根目录，从配置读取
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * 项目根目录（使用 user.dir 系统属性，避免 Tomcat 临时目录问题）
     */
    private String baseDir;

    @PostConstruct
    public void init() {
        baseDir = System.getProperty("user.dir", ".");
        log.info("上传服务初始化: baseDir={}, uploadDir={}", baseDir, uploadDir);

        File dir = new File(baseDir, uploadDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("上传根目录已创建: {}", dir.getAbsolutePath());
            } else {
                log.warn("上传根目录创建失败: {}", dir.getAbsolutePath());
            }
        }
    }

    /**
     * 上传商品图片
     */
    public UploadResponse uploadProductImage(MultipartFile file) {
        return uploadFile(file, "products");
    }

    /**
     * 上传搜索图片
     */
    public UploadResponse uploadSearchImage(MultipartFile file) {
        return uploadFile(file, "search");
    }

    /**
     * 通用文件上传方法
     */
    private UploadResponse uploadFile(MultipartFile file, String subDir) {
        // 第 1 层校验：文件是否为空
        if (file == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件不能为空");
        }
        if (file.getSize() == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件不能为空");
        }

        log.debug("文件上传请求: subDir={}, originalFilename={}, size={}, contentType={}",
                subDir, file.getOriginalFilename(), file.getSize(), file.getContentType());

        // 第 1 层校验：文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小超过限制（最大 10MB），当前大小: " + (file.getSize() / 1024 / 1024) + "MB");
        }

        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unknown";
        }

        // 第 2 层校验：MIME 类型
        // 【修复 v3】getContentType() 可能为 null（如空文件或某些客户端），
        // 此时降级到后续校验层，不直接抛异常
        String mimeType = file.getContentType();
        if (mimeType != null) {
            if (!ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "不支持的文件类型: " + mimeType + "，仅支持 JPEG、PNG、GIF、WebP 格式");
            }
        } else {
            log.warn("MIME 类型为 null，跳过第 2 层校验，依赖后续校验层: filename={}", originalFilename);
        }

        // 第 3 层校验：文件扩展名
        String extension = getExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "不支持的文件扩展名: " + extension + "，仅支持 .jpg、.jpeg、.png、.gif、.webp");
        }

        // 第 4 层校验：文件头魔数
        // 【修复 v3】使用 byte[] 缓存文件内容，避免多次读取 InputStream 导致流耗尽
        byte[] fileBytes;
        try (InputStream inputStream = file.getInputStream()) {
            fileBytes = inputStream.readAllBytes();
        } catch (IOException e) {
            log.error("读取文件内容失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件读取失败: " + e.getMessage());
        }

        if (fileBytes.length < 4) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "文件内容不足以验证类型（读取到 " + fileBytes.length + " 字节）");
        }

        if (!validateMagicNumber(fileBytes, extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "文件内容与扩展名不匹配，疑似伪装文件");
        }

        // 生成唯一文件名
        String newFilename = UUID.randomUUID().toString() + extension;

        // 按日期分目录存储
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relativePath = uploadDir + "/images/" + subDir + "/" + datePath + "/" + newFilename;

        Path targetPath = Paths.get(baseDir, relativePath);
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            log.error("创建上传目录失败: baseDir={}, relativePath={}, error={}",
                    baseDir, relativePath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建目录失败: " + e.getMessage());
        }

        // 【修复 v3】使用已读取的 fileBytes 写入文件，避免重新获取 InputStream
        try {
            Files.write(targetPath, fileBytes);
            log.info("文件保存成功: targetPath={}, size={}", targetPath, fileBytes.length);
        } catch (IOException e) {
            log.error("保存文件失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        }

        // 构建可访问的 URL
        String url = "/" + relativePath.replace("\\", "/");

        log.info("文件上传成功: url={}, original={}, size={}, mime={}",
                url, originalFilename, fileBytes.length, mimeType);

        return new UploadResponse(
                UUID.randomUUID().toString(),
                url,
                originalFilename,
                (long) fileBytes.length
        );
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return filename.substring(lastDot).toLowerCase();
    }

    /**
     * 验证文件头魔数
     */
    private boolean validateMagicNumber(byte[] fileBytes, String extension) {
        byte[] expected = MAGIC_NUMBERS.get(extension);
        if (expected == null) {
            return false;
        }

        // 对于 WebP，检查 RIFF...WEBP 格式
        if (".webp".equals(extension)) {
            if (fileBytes.length < 12) return false;
            if (fileBytes[0] != 0x52 || fileBytes[1] != 0x49 || fileBytes[2] != 0x46 || fileBytes[3] != 0x46) {
                return false;
            }
            return fileBytes[8] == 0x57 && fileBytes[9] == 0x45
                    && fileBytes[10] == 0x42 && fileBytes[11] == 0x50;
        }

        // 对于其他格式，比较前 N 个字节
        for (int i = 0; i < expected.length && i < fileBytes.length; i++) {
            if (fileBytes[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
