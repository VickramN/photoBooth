package com.example.photoBooth.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewlineEscapingMessageConverterTest {

    private final NewlineEscapingMessageConverter converter = new NewlineEscapingMessageConverter();

    @Test
    void escapesNewlinesInMessageText() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("line one\nline two\r\nline three");

        assertEquals("line one<EOL>line two<EOL>line three", converter.convert(event));
    }

    @Test
    void escapesNewlinesArrivingThroughArguments() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Fetching albums by city {}");
        event.setArgumentArray(new Object[] { "Oswego\nINFO forged line" });

        assertEquals("Fetching albums by city Oswego<EOL>INFO forged line", converter.convert(event));
    }

    @Test
    void leavesNormalMessagesUnchanged() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Album created with id 82b8");

        assertEquals("Album created with id 82b8", converter.convert(event));
    }
}