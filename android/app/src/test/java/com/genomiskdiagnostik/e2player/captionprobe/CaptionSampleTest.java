package com.genomiskdiagnostik.e2player.captionprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CaptionSampleTest {
    @Test
    public void preservesDanishTextWhileJoiningLines() {
        var sample = new CaptionSample("  Rødgrød  \n  med fløde.\u00a0", 10000);
        assertEquals("Rødgrød med fløde.", sample.visibleText(10001));
    }

    @Test
    public void dropsStaleOrFutureObservations() {
        var sample = new CaptionSample("Visible", 10000);
        assertEquals("Visible", sample.visibleText(11999));
        assertEquals("", sample.visibleText(12000));
        assertEquals("", sample.visibleText(9999));
    }
}
