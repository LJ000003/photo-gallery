package com.hape.photogallery.dto;

import java.util.List;

/**
 * 统计面板聚合响应。
 * 注意：必须是普通非 final 类（非 record）——Redis 缓存反序列化依赖
 * ObjectMapper 的 DefaultTyping.NON_FINAL 写入的 @class 类型信息，
 * final 类型（record）不写类型信息，缓存命中会反序列化成 LinkedHashMap。
 */
public class StatsResponse {

    private long totalPhotos;
    private long totalSize;
    private List<MonthlyTrend> monthlyTrend;
    private List<TopTag> topTags;

    public StatsResponse() {
    }

    public StatsResponse(long totalPhotos, long totalSize,
                         List<MonthlyTrend> monthlyTrend, List<TopTag> topTags) {
        this.totalPhotos = totalPhotos;
        this.totalSize = totalSize;
        this.monthlyTrend = monthlyTrend;
        this.topTags = topTags;
    }

    public long getTotalPhotos() {
        return totalPhotos;
    }

    public void setTotalPhotos(long totalPhotos) {
        this.totalPhotos = totalPhotos;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public List<MonthlyTrend> getMonthlyTrend() {
        return monthlyTrend;
    }

    public void setMonthlyTrend(List<MonthlyTrend> monthlyTrend) {
        this.monthlyTrend = monthlyTrend;
    }

    public List<TopTag> getTopTags() {
        return topTags;
    }

    public void setTopTags(List<TopTag> topTags) {
        this.topTags = topTags;
    }

    /** 单月上传量 */
    public static class MonthlyTrend {
        private String month;
        private long count;

        public MonthlyTrend() {
        }

        public MonthlyTrend(String month, long count) {
            this.month = month;
            this.count = count;
        }

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    /** 热门标签（含颜色与照片数） */
    public static class TopTag {
        private String name;
        private String color;
        private long count;

        public TopTag() {
        }

        public TopTag(String name, String color, long count) {
            this.name = name;
            this.color = color;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}
