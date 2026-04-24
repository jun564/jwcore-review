package org.jwcore.adapter.alerting;

import org.jwcore.adapter.cq.ChronicleQueueEventJournal;
import org.jwcore.adapter.cq.ChronicleQueueJournalConfig;
import org.jwcore.core.ports.IEventJournal;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public final class AlertingMain {
    private static final Logger LOGGER = Logger.getLogger(AlertingMain.class.getName());

    private AlertingMain() {}

    public static void main(final String[] args) throws Exception {
        LOGGER.info("JWCore Alerting starting");

        final ChronicleQueueJournalConfig journalConfig = readJournalConfig(args);
        final IEventJournal journal = new ChronicleQueueEventJournal(journalConfig);

        final AlertFormatter formatter = new AlertFormatter();
        final TelegramAlertSinkConfig telegramConfig = TelegramAlertSinkConfig.fromEnvironment();
        final TelegramHttpTransport transport = new HttpTelegramHttpTransport();
        final TelegramAlertSink telegramSink = new TelegramAlertSink(telegramConfig, transport, formatter, BackoffSleeper.real());
        final JulPanelAlertSink panelSink = new JulPanelAlertSink();

        final AlertTailer tailer = new AlertTailer(journal, List.of(telegramSink, panelSink));
        tailer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("JWCore Alerting stopping");
            tailer.stop();
            try {
                if (journal instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Exception exception) {
                LOGGER.warning("Journal close failed: " + exception);
            }
        }));

        Thread.currentThread().join();
    }

    private static ChronicleQueueJournalConfig readJournalConfig(final String[] args) {
        final String baseDir = args.length > 0 ? args[0] : System.getenv().getOrDefault("JWCORE_CQ_BASE_DIR", "./build/cq");
        final String business = args.length > 1 ? args[1] : System.getenv().getOrDefault("JWCORE_CQ_BUSINESS_QUEUE", "events-business");
        final String market = args.length > 2 ? args[2] : System.getenv().getOrDefault("JWCORE_CQ_MARKET_QUEUE", "events-market");
        return new ChronicleQueueJournalConfig(Path.of(baseDir), business, market);
    }
}
