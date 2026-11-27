package com.genomiskdiagnostik.e2player.captionprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SubtitleSessionTest {
    @Test
    public void combinesRepeatedObservationsAndClosesTheLastCueOnStop() {
        var session = new SubtitleSession(1000);
        assertNull(session.observe("Første tekst", 1250));
        assertNull(session.observe("Første tekst", 1500));
        assertEquals(new SubtitleSession.Cue(250, 750, "Første tekst"), session.observe("Næste tekst", 1750));
        assertEquals(new SubtitleSession.Cue(750, 1250, "Næste tekst"), session.observe("", 2250));
        assertNull(session.observe("", 2500));
    }

    @Test
    public void preservesMissingFrameGapsAndIgnoresOldObservations() {
        var session = new SubtitleSession(1000);
        assertNull(session.observe("Samme tekst", 1500));
        assertEquals(new SubtitleSession.Cue(500, 2500, "Samme tekst"), session.observe("Samme tekst", 8000));
        assertNull(session.observe("Old frame", 7000));
        assertEquals(new SubtitleSession.Cue(7000, 7250, "Samme tekst"), session.observe("", 8250));
    }

    @Test
    public void exportsNumberedSrtAndPlainTextWithUnicode() {
        var cue = new SubtitleSession.Cue(3_723_004, 3_725_012, "Rødgrød med fløde.");
        assertEquals("5\n01:02:03,004 --> 01:02:05,012\nRødgrød med fløde.\n\n",
                SubtitleSession.Format.SRT.render(5, cue));
        assertEquals("Rødgrød med fløde.\n", SubtitleSession.Format.TXT.render(5, cue));
    }
}
