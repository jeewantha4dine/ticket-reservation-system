package com.ticket;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class LeaderWatcher {
    private final CuratorFramework client;
    private String currentLeader;

    public LeaderWatcher() throws Exception {
        client = CuratorFrameworkFactory.newClient("localhost:2181", new ExponentialBackoffRetry(1000, 3));
        client.start();

        NodeCache cache = new NodeCache(client, "/current-leader");
        cache.getListenable().addListener(() -> {
            if (cache.getCurrentData() != null) {
                currentLeader = new String(cache.getCurrentData().getData());
                System.out.println("🔁 New leader detected: " + currentLeader);
            }
        });
        cache.start(true);

        // Initial value
        if (client.checkExists().forPath("/current-leader") != null) {
            currentLeader = new String(client.getData().forPath("/current-leader"));
            System.out.println("✅ Initial leader: " + currentLeader);
        } else {
            System.out.println("⚠️ No leader available at startup.");
        }
    }

    public String getCurrentLeader() {
        return currentLeader;
    }
}
