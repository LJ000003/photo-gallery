package com.hape.photogallery.dto;

public class TransformRequest {

    private int rotate;
    private String mirror = "none";
    private Double cx;
    private Double cy;
    private Double cw;
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
