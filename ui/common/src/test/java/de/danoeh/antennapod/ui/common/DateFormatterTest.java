package de.danoeh.antennapod.ui.common;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class DateFormatterTest {

    @Test
    public void testAbbrevOld() {
        LocalDate localDate = LocalDate.of(2020, 1, 1);
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        String formatted = DateFormatter.formatAbbrev(null, date);
        assertEquals("Jan 1, 2020", formatted);
    }

    @Test
    public void testAbbrevNow() {
        LocalDate localDate = LocalDate.now();

        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        String formatted = DateFormatter.formatAbbrev(null, date);
        assertFalse(formatted.contains(Integer.toString(localDate.getYear())));
    }
}