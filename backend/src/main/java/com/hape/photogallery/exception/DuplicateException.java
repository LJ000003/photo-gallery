package com.hape.photogallery.exception;

import com.hape.photogallery.dto.PhotoResponse;

public class DuplicateException extends BusinessException {

    private final PhotoResponse existing;

    public DuplicateException(PhotoResponse existing) {
        super(409, "该照片已存在");
        this.existing = existing;
    }

    public PhotoResponse getExisting() { return existing; }
}
