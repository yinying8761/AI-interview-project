package com.iflytek.interview.file.util;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传校验器：类型 + 大小。在业务层做二次"安检"，避免只依赖前端。
 */
@Component
public class FileValidator {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "webp",
            "mp4", "webm", "mp3", "wav", "txt"
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "文件名为空");
        }

        String ext = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型: " + ext);
        }

        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "文件不能超过 20MB");
        }
    }
}
