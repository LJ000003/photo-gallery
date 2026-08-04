package com.hape.photogallery.exception;

/** 上传文件超过大小限制（400）——并入 BusinessException 体系，单一 handler 处理 */
public class FileSizeExceededException extends BusinessException {
    public FileSizeExceededException(String message) {
        super(400, message);
    }
}
