package com.hape.photogallery.exception;

import com.hape.photogallery.dto.PhotoResponse;

public class DuplicateException extends BusinessException {

    // transient：PhotoResponse 含 JPA 实体（懒加载代理不可序列化），异常不跨进程传输
    private final transient PhotoResponse existing;

    public DuplicateException(PhotoResponse existing) {
        super(409, "该照片已存在");
        this.existing = existing;
    }

    public PhotoResponse getExisting() { return existing; }
}
