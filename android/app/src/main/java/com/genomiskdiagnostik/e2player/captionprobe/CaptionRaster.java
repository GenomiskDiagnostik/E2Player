package com.genomiskdiagnostik.e2player.captionprobe;

import java.nio.ByteBuffer;

record CaptionRaster(int[] pixels, int width, int height) {
    static final int STANDARD_CROP = -1;
    static final int STANDARD_TOP_PERCENT = 58;
    static final int MAX_IGNORED_PERCENT = 99;
    private static final int WHITE_TEXT_THRESHOLD = 205;

    static boolean isCustomCrop(int ignoredTopPercent) {
        return ignoredTopPercent >= 0 && ignoredTopPercent <= MAX_IGNORED_PERCENT;
    }

    static CaptionRaster fromRgba(ByteBuffer frame, int stride, int frameWidth, int frameHeight, int ignoredTopPercent) {
        int left = frameWidth / 10;
        boolean custom = isCustomCrop(ignoredTopPercent);
        int top = frameHeight * (custom ? ignoredTopPercent : STANDARD_TOP_PERCENT) / 100;
        int bottom = custom ? frameHeight : frameHeight * 96 / 100;
        int width = frameWidth - 2 * left;
        int height = bottom - top;
        if (width <= 0 || height <= 0 || stride < frameWidth * 4) {
            throw new IllegalArgumentException("Unexpected frame dimensions");
        }

        // Treat bright white glyphs as black ink on white for Tesseract.
        int[] pixels = new int[width * height];
        int firstRow = frame.position() + top * stride + left * 4;
        for (int row = 0; row < height; row++) {
            int start = firstRow + row * stride;
            for (int column = 0; column < width; column++) {
                int offset = start + column * 4;
                boolean white = (frame.get(offset) & 0xff) > WHITE_TEXT_THRESHOLD
                        && (frame.get(offset + 1) & 0xff) > WHITE_TEXT_THRESHOLD
                        && (frame.get(offset + 2) & 0xff) > WHITE_TEXT_THRESHOLD;
                pixels[row * width + column] = white ? 0xff000000 : 0xffffffff;
            }
        }
        return new CaptionRaster(pixels, width, height);
    }
}
