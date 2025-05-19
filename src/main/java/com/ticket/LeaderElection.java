package com.ticket;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class LeaderElection extends LeaderSelectorListenerAdapter {

    private final String nodeId;
    private final int port; // NEW: Pass in the gRPC port of this node
    public static boolean isLeader = false;

    public LeaderElection(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
    }

    public void start() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(
                "localhost:2181", new ExponentialBackoffRetry(1000, 3));
        client.start();

        LeaderSelector selector = new LeaderSelector(client, "/ticket-leader", this);
        selector.autoRequeue();
        selector.start();
    }

    @Override
    public void takeLeadership(CuratorFramework client) {
        isLeader = true;
        String address = "localhost:" + port;

        // Register leader address in ZooKeeper
        try {
            if (client.checkExists().forPath("/current-leader") != null) {
                client.delete().forPath("/current-leader");
            }
            client.create()
                    .withMode(org.apache.zookeeper.CreateMode.EPHEMERAL)
                    .forPath("/current-leader", address.getBytes());

            System.out.println("👑 " + nodeId + " is now the LEADER");
            System.out.println("📌 Registered in ZooKeeper as leader: " + address);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {
        } finally {
            isLeader = false;
            System.out.println("⚠️ " + nodeId + " lost leadership");
        }
    }
}
