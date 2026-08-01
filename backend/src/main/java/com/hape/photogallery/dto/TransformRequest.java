package com.hape.photogallery.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public class TransformRequest {

    @Min(value = 0, message = "旋转角度不能为负数")
    @Max(value = 360, message = "旋转角度不能超过 360 度")
    private int rotate;

    @Pattern(regexp = "none|horizontal|vertical", message = "镜像类型只能为 none/horizontal/vertical")
    private String mirror = "none";

    @Min(value = 0, message = "裁剪参数必须 >= 0")
    @Max(value = 1, message = "裁剪参数必须 <= 1")
    private Double cx;

    @Min(value = 0, message = "裁剪参数必须 >= 0")
    @Max(value = 1, message = "裁剪参数必须 <= 1")
    private Double cy;

    @Min(value = 0, message = "裁剪参数必须 >= 0")
    @Max(value = 1, message = "裁剪参数必须 <= 1")
    private Double cw;

    @Min(value = 0, message = "裁剪参数必须 >= 0")
    @Max(value = 1, message = "裁剪参数必须 <= 1")
    private Double ch;

    public int getRotate() { return rotate; }
    public void setRotate(int rotate) { this.rotate = rotate; }
    public String getMirror() { return mirror; }
    public void setMirror(String mirror) { this.mirror = mirror; }
    public Double getCx() { return cx; }
    public void setCx(Double cx) { this.cx = cx; }
    public Double getCy() { return cy; }
    public void setCy(Double cy) { this.cy = cy; }
    public Double getCw() { return cw; }
    public void setCw(Double cw) { this.cw = cw; }
    public Double getCh() { return ch; }
    public void setCh(Double ch) { this.ch = ch; }
}
