package org.jwcore.adapter.alerting;

public interface BackoffSleeper {
    void sleep(long millis) throws InterruptedException;

    static BackoffSleeper real() {
        return Thread::sleep;
    }
}
