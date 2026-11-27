package com.genomiskdiagnostik.e2player.captionprobe;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CaptionRasterTest {
    @Test
    public void isolatesWhiteTextInCaptionAreaWithPaddedRows() {
        int stride = 48;
        int base = 8;
        var rgba = ByteBuffer.allocate(base + 10 * stride);
        rgba.position(base);
        rgba.putInt(base + 4, 0xffffffff); // Bright text outside the area.
        rgba.putInt(base + 5 * stride + 4, 0xffffffff);
        rgba.putInt(base + 6 * stride + 4, 0xffff00ff); // Yellow video detail.
        rgba.putInt(base + 8 * stride + 8 * 4, 0xf4f4f4ff);

        var raster = CaptionRaster.fromRgba(rgba, stride, 10, 10, CaptionRaster.STANDARD_CROP);
        assertEquals(8, raster.width());
        assertEquals(4, raster.height());
        int[] expected = new int[32];
        Arrays.fill(expected, 0xffffffff);
        expected[0] = 0xff000000;
        expected[31] = 0xff000000;
        assertArrayEquals(expected, raster.pixels());
    }

    @Test
    public void customCropIgnoresOverlaysAboveTheSelectedAreaAndReadsThroughTheBottom() {
        int stride = 48;
        var rgba = ByteBuffer.allocate(10 * stride);
        rgba.putInt(6 * stride + 4, 0xffffffff); // Video overlay above the 70% line.
        rgba.putInt(7 * stride, 0xffffffff); // Bright detail outside the horizontal crop.
        rgba.putInt(7 * stride + 4, 0xffffffff);
        rgba.putInt(9 * stride + 8 * 4, 0xffffffff);

        var raster = CaptionRaster.fromRgba(rgba, stride, 10, 10, 70);
        assertEquals(8, raster.width());
        assertEquals(3, raster.height());
        var expected = new int[24];
        Arrays.fill(expected, 0xffffffff);
        expected[0] = 0xff000000;
        expected[23] = 0xff000000;
        assertArrayEquals(expected, raster.pixels());
    }

    @Test
    public void customCropUsesTheCurrentFrameHeightAndKeepsAtLeastOneRow() {
        var rgba = ByteBuffer.allocate(20 * 40);
        assertEquals(6, CaptionRaster.fromRgba(rgba, 40, 10, 20, 70).height());
        assertEquals(20, CaptionRaster.fromRgba(rgba, 40, 10, 20, 0).height());
        assertEquals(1, CaptionRaster.fromRgba(rgba, 40, 10, 20, 99).height());
        assertEquals(8, CaptionRaster.fromRgba(rgba, 40, 10, 20, 100).height());
    }
}
