package com.dinehub.controller.admin;

import com.dinehub.result.Result;
import com.dinehub.utils.AliOssUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CommonControllerTest {

    @TempDir
    Path uploadDirectory;

    @Test
    void localDevelopmentUploadReencodesAndPersistsImage() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);

        AliOssUtil oss = mock(AliOssUtil.class);
        CommonController controller = new CommonController();
        ReflectionTestUtils.setField(controller, "aliOssUtil", oss);
        ReflectionTestUtils.setField(controller, "localUploadEnabled", true);
        ReflectionTestUtils.setField(controller, "localUploadDirectory", uploadDirectory.toString());

        Result<String> result = controller.upload(new MockMultipartFile(
                "file", "dish.png", "image/png", bytes.toByteArray()));

        assertEquals(1, result.getCode());
        assertTrue(result.getData().startsWith("/uploads/"));
        assertTrue(Files.exists(uploadDirectory.resolve(
                result.getData().substring("/uploads/".length()))));
        verifyNoInteractions(oss);
    }
}
