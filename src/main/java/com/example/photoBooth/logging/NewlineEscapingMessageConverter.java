package com.example.photoBooth.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class NewlineEscapingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return message == null ? null : message.replaceAll("\r\n|\r|\n", "<EOL>");
    }

}
