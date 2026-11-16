package za.co.neroland.neromaps.telemetry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Log4j2 appender that feeds {@link NeroMapsTelemetry}. Minecraft routes essentially every
 * failure through log4j — handled errors, event-bus listener exceptions, and the crash report
 * itself — so listening on the root logger catches NeroMaps failures without mixins. Filtering
 * (NeroMaps-only), de-dup, rate-limiting and PII scrubbing all happen in
 * {@link NeroMapsTelemetry}; this only selects candidate log events.
 */
final class SentryLogAppender extends AbstractAppender {

    SentryLogAppender() {
        super("NeroMapsSentry", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        if (!NeroMapsTelemetry.isActive()) {
            return;
        }
        Level level = event.getLevel();
        if (!level.isMoreSpecificThan(Level.ERROR)) {
            return;
        }
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            if (NeroMapsTelemetry.touchesNeroMaps(thrown)) {
                NeroMapsTelemetry.capture(thrown);
            }
        } else if (level == Level.FATAL) {
            String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
            if (message != null && message.contains("za.co.neroland.neromaps")) {
                NeroMapsTelemetry.captureMessage(message);
            }
        }
    }
}
