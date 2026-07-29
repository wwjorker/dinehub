package com.dinehub.controller.admin;

import com.dinehub.constant.MessageConstant;
import com.dinehub.result.Result;
import com.dinehub.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Common administration APIs.
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Value("${dinehub.upload.local-enabled:false}")
    private boolean localUploadEnabled;

    @Value("${dinehub.upload.local-directory:./uploads}")
    private String localUploadDirectory;

    /**
     * Accepts JPEG/PNG images only. The server decodes and re-encodes the
     * image, so the object uploaded to OSS cannot retain disguised payloads.
     */
    @PostMapping("/upload")
    @ApiOperation("上传菜品图片")
    public Result<String> upload(MultipartFile file) {
        log.info("image upload requested, bytes={}", file == null ? 0 : file.getSize());

        try {
            if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }

            String format;
            String extension;
            if ("image/jpeg".equalsIgnoreCase(file.getContentType())) {
                format = "jpg";
                extension = ".jpg";
            } else if ("image/png".equalsIgnoreCase(file.getContentType())) {
                format = "png";
                extension = ".png";
            } else {
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }

            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }

            ByteArrayOutputStream sanitized = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, sanitized)) {
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }

            String objectName = UUID.randomUUID() + extension;
            if (localUploadEnabled) {
                Path uploadRoot = Paths.get(localUploadDirectory).toAbsolutePath().normalize();
                Files.createDirectories(uploadRoot);
                Path target = uploadRoot.resolve(objectName).normalize();
                if (!target.getParent().equals(uploadRoot)) {
                    return Result.error(MessageConstant.UPLOAD_FAILED);
                }
                Files.write(target, sanitized.toByteArray());
                return Result.success("/uploads/" + objectName);
            }
            return Result.success(aliOssUtil.upload(sanitized.toByteArray(), objectName));
        } catch (IOException | RuntimeException ex) {
            log.error("image upload failed", ex);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
