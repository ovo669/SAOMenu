package com.sao.saomenu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通知数据层回归:容量上限、过期清理。
 */
class SAONotificationTest {

    @AfterEach
    void cleanup() {
        SAONotification.clear();
    }

    @Test
    void queueIsCappedAtMaxEntries() {
        for (int i = 0; i < 6; i++) {
            SAONotification.push("t" + i, "m" + i);
        }
        assertEquals(4, SAONotification.size(), "最多保留 4 条通知");
    }

    @Test
    void pruneRemovesExpiredOnly() {
        SAONotification.push("fresh", "");
        long now = net.minecraft.Util.getMillis();
        SAONotification.prune(now);
        assertEquals(1, SAONotification.size(), "新通知不应被清理");
        // 停留 2.6s + 淡出 0.3s 后应被移除
        SAONotification.prune(now + 4000);
        assertEquals(0, SAONotification.size(), "过期通知应被移除");
    }

    @Test
    void clearEmptiesQueue() {
        SAONotification.push("a", "");
        SAONotification.push("b", "");
        SAONotification.clear();
        assertEquals(0, SAONotification.size());
    }
}
