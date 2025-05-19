package com.ticket;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class LeaderElection extends LeaderSelectorListenerAdapter {

    private final String nodeId;
    public static boolean isLeader = false;

    public LeaderElection(String nodeId) {
        this.nodeId = nodeId;
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
        System.out.println(nodeId + " is now the LEADER");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {
        } finally {
            isLeader = false;
            System.out.println(nodeId + " lost leadership");
        }
    }
}

