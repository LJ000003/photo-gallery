package com.hape.photogallery.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

/**
 * 失败封禁存储单测（P1-#14/#11：5 次失败封 15 分钟的口径支撑）。
 * 纯 JUnit 5 无 Spring；Caffeine 过期（15min）无法伪造时钟（store 硬编码 builder 无 ticker
 * 注入），该行为由 Caffeine 库保证，不为此加构造参数（过度设计）——诚实口径见 §6.6 #11 卡。
 */
class FailedAttemptStoreTest {

    private final FailedAttemptStore store = new FailedAttemptStore();

    @Test
    void initialState_shouldAllowFiveAttempts() {
        assertThat(store.isBlocked("1.1.1.1")).isFalse();
        assertThat(store.remainingAttempts("1.1.1.1")).isEqualTo(5);
    }

    @Test
    void fourFailures_shouldNotBlock() {
        for (int i = 0; i < 4; i++) {
            store.recordFailure("1.1.1.1");
        }
        assertThat(store.isBlocked("1.1.1.1")).isFalse();
        assertThat(store.remainingAttempts("1.1.1.1")).isEqualTo(1);
    }

    @Test
    void fifthFailure_shouldBlock() {
        for (int i = 0; i < 5; i++) {
            store.recordFailure("1.1.1.1");
        }
        assertThat(store.isBlocked("1.1.1.1")).isTrue();
        assertThat(store.remainingAttempts("1.1.1.1")).isZero();
    }

    @Test
    void failuresBeyondMax_shouldNeverGoNegative() {
        for (int i = 0; i < 7; i++) {
            store.recordFailure("1.1.1.1");
        }
        assertThat(store.isBlocked("1.1.1.1")).isTrue();
        assertThat(store.remainingAttempts("1.1.1.1")).isZero();
    }

    @Test
    void reset_shouldUnblock() {
        for (int i = 0; i < 5; i++) {
            store.recordFailure("1.1.1.1");
        }
        assertThat(store.isBlocked("1.1.1.1")).isTrue();

        store.reset("1.1.1.1");

        assertThat(store.isBlocked("1.1.1.1")).isFalse();
        assertThat(store.remainingAttempts("1.1.1.1")).isEqualTo(5);
    }

    @Test
    void resetMidway_shouldRestartCounting() {
        store.recordFailure("1.1.1.1");
        store.recordFailure("1.1.1.1");
        store.reset("1.1.1.1");

        for (int i = 0; i < 5; i++) {
            store.recordFailure("1.1.1.1");
        }
        assertThat(store.isBlocked("1.1.1.1")).isTrue();
    }

    @Test
    void concurrentFailures_shouldNotLoseCount() throws Exception {
        // 4 线程 × 各 1 次 → 精确计数必须为 4（remaining == 1）。
        // 不用 8 线程：remainingAttempts 被 Math.max 钳制，count 5 与 8 无法区分。
        // 多轮循环提升非原子实现被「恰好串行化」放过的检出概率（每轮新建 store）。
        for (int round = 0; round < 20; round++) {
            FailedAttemptStore s = new FailedAttemptStore();
            CountDownLatch start = new CountDownLatch(1);
            int threads = 4;
            Thread[] pool = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                pool[i] = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    s.recordFailure("2.2.2.2");
                });
                pool[i].start();
            }
            start.countDown();
            for (Thread t : pool) {
                t.join();
            }
            // 若实现丢失更新（count < 4），remaining 会 > 1
            assertThat(s.remainingAttempts("2.2.2.2"))
                    .as("round %d: 4 次并发失败不得丢计数", round)
                    .isEqualTo(1);
            assertThat(s.isBlocked("2.2.2.2")).isFalse();
        }
    }

    @Test
    void concurrentFailuresBeyondMax_shouldBlock() throws Exception {
        FailedAttemptStore s = new FailedAttemptStore();
        CountDownLatch start = new CountDownLatch(1);
        int threads = 8;
        Thread[] pool = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            pool[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                s.recordFailure("3.3.3.3");
            });
            pool[i].start();
        }
        start.countDown();
        for (Thread t : pool) {
            t.join();
        }
        assertThat(s.isBlocked("3.3.3.3")).isTrue();
        assertThat(s.remainingAttempts("3.3.3.3")).isZero();
    }

    @Test
    void differentIps_shouldNotAffectEachOther() {
        store.recordFailure("1.1.1.1");
        store.recordFailure("1.1.1.1");
        store.recordFailure("1.1.1.1");
        store.recordFailure("1.1.1.1");

        assertThat(store.remainingAttempts("1.1.1.1")).isEqualTo(1);
        assertThat(store.remainingAttempts("2.2.2.2")).isEqualTo(5);
        assertThat(store.isBlocked("2.2.2.2")).isFalse();
    }
}
