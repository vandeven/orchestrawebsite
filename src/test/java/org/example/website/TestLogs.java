package org.example.website;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Test only: collects what the application logs while an action runs. */
public final class TestLogs {

    private TestLogs() {}

    public static List<LogRecord> capture(Runnable action) {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(handler);
        try {
            action.run();
        } finally {
            root.removeHandler(handler);
        }
        return records;
    }

    /**
     * Everything in a record that could carry a secret: the message, its parameters and the error.
     */
    public static String everything(LogRecord record) {
        StringBuilder text = new StringBuilder(String.valueOf(record.getMessage()));
        if (record.getParameters() != null) {
            for (Object parameter : record.getParameters()) {
                text.append(' ').append(parameter);
            }
        }
        if (record.getThrown() != null) {
            text.append(' ').append(record.getThrown());
        }
        return text.toString();
    }
}
