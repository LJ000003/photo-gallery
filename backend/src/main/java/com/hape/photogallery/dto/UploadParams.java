package com.hape.photogallery.dto;

import java.util.List;

/**
 * 上传参数封装（P4-#37）：upload/batchUpload 的 5 个可选参数收敛为单一对象，
 * 避免方法签名随字段增长。
 */
public record UploadParams(String name, String description, List<Long> tagIds,
                           Long categoryId, String watermark) {}
