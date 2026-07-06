package com.hape.photogallery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordUtilTest {

    @Test
    void insideChina_shouldTransform() {
        // 北京天安门 WGS84
        double[] result = CoordUtil.wgs84ToGcj02(116.397428, 39.909204);

        // 转换后应有偏移（GCJ-02 相对 WGS-84 在中国境内偏移 100-700m）
        assertThat(result[0]).isNotEqualTo(116.397428);
        assertThat(result[1]).isNotEqualTo(39.909204);
        // 偏移量应在合理范围（< 0.01 度 ≈ 1km）
        assertThat(Math.abs(result[0] - 116.397428)).isLessThan(0.01);
        assertThat(Math.abs(result[1] - 39.909204)).isLessThan(0.01);
        // 方向：中国境内 GCJ-02 经度偏东（正偏移）
        assertThat(result[0]).isGreaterThan(116.397428);
    }

    @Test
    void outsideChina_shouldReturnUnchanged() {
        // 东京
        double[] result = CoordUtil.wgs84ToGcj02(139.6917, 35.6895);
        assertThat(result[0]).isEqualTo(139.6917);
        assertThat(result[1]).isEqualTo(35.6895);

        // 纽约
        result = CoordUtil.wgs84ToGcj02(-74.006, 40.7128);
        assertThat(result[0]).isEqualTo(-74.006);
        assertThat(result[1]).isEqualTo(40.7128);

        // 伦敦
        result = CoordUtil.wgs84ToGcj02(-0.1276, 51.5074);
        assertThat(result[0]).isEqualTo(-0.1276);
        assertThat(result[1]).isEqualTo(51.5074);
    }

    @Test
    void boundary_insideChina_shouldTransform() {
        // 中国境内边界附近的点
        double[] result = CoordUtil.wgs84ToGcj02(121.4737, 31.2304); // 上海
        assertThat(result[0]).isNotEqualTo(121.4737);
    }

    @Test
    void boundary_outsideChina_shouldNotTransform() {
        // 中国境外但靠近边界的点
        assertThat(CoordUtil.wgs84ToGcj02(72.003, 35.0)[0]).isEqualTo(72.003); // 西边界外
        assertThat(CoordUtil.wgs84ToGcj02(137.835, 35.0)[0]).isEqualTo(137.835); // 东边界外
    }

    @Test
    void zeroCoordinate_shouldTransform() {
        // (0,0) 不在中国境内，应保持原值
        double[] result = CoordUtil.wgs84ToGcj02(0, 0);
        assertThat(result[0]).isEqualTo(0);
        assertThat(result[1]).isEqualTo(0);
    }
}
