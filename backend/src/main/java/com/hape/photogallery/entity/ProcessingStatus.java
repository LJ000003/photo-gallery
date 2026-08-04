package com.hape.photogallery.entity;

/**
 * 图片异步处理状态。
 * 以 EnumType.STRING 落库（VARCHAR 值不变），JSON 序列化输出枚举名
 * （"PROCESSING"/"DONE"/"FAILED"），前端按字符串比较，无感知。
 */
public enum ProcessingStatus {
    PROCESSING, DONE, FAILED
}
