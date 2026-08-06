package com.hape.photogallery.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NonceStore：一次性消费（原子删除防 TOCTOU 重放）、TTL 过期。
 * TTL 用 package-private 构造器注入短时长验证过期（生产固定 60s）。
 */
class NonceStoreTest {

    @Test
    void generate_shouldReturnConsumableNonce() {
        NonceStore store = new NonceStore();
        String nonce = store.generate();

        assertThat(nonce).isNotBlank();
        assertThat(store.consume(nonce)).isTrue();
    }

    @Test
    void consume_shouldBeSingleUse() {
        NonceStore store = new NonceStore();
        String nonce = store.generate();

        assertThat(store.consume(nonce)).isTrue();
        assertThat(store.consume(nonce)).as("重复消费必须失败（防重放）").isFalse();
    }

    @Test
    void consume_unknownNonce_shouldReturnFalse() {
        NonceStore store = new NonceStore();

        assertThat(store.consume("never-generated")).isFalse();
    }

    @Test
    void consume_expiredNonce_shouldReturnFalse() throws InterruptedException {
        // 60ms TTL：写入后等待过期，consume 必须失败
        NonceStore store = new NonceStore(Duration.ofMillis(60));
        String nonce = store.generate();

        Thread.sleep(150);
        assertThat(store.consume(nonce)).as("过期 nonce 必须不可消费").isFalse();
    }

    @Test
    void concurrentConsume_shouldBeAtomic_singleWinner() throws InterruptedException {
        // 并发消费同一 nonce：asMap().remove 原子性保证恰好一个赢家
        NonceStore store = new NonceStore();
        String nonce = store.generate();

        int[] winners = {0};
        Thread[] threads = new Thread[8];
        for (int i = 0; i < 8; i++) {
            threads[i] = new Thread(() -> {
                if (store.consume(nonce)) {
                    synchronized (winners) {
                        winners[0]++;
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertThat(winners[0]).as("并发消费恰好一个赢家").isEqualTo(1);
    }
}
