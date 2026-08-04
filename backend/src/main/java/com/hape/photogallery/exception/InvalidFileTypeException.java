package com.hape.photogallery.exception;

/** 上传文件格式不被支持（400）——并入 BusinessException 体系，单一 handler 处理 */
public class InvalidFileTypeException extends BusinessException {
    public InvalidFileTypeException(String message) {
        super(400, message);
    }
}
