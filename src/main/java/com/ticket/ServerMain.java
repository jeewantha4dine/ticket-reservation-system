package com.ticket;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import ticket.Ticket;
import ticket.TicketServiceGrpc;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 50051;
        String nodeId = "node-" + port;

        // 1. Start ZooKeeper Leader Election
        new Thread(() -> new LeaderElection(nodeId).start()).start();

        // 2. Start gRPC Server
        Server server = ServerBuilder
                .forPort(port)
                .addService(new TicketServiceImpl())
                .build();

        System.out.println("gRPC Server started on port " + port + "...");
        server.start();

        // 3. Wait a bit for leader election to settle
        Thread.sleep(3000);

        // 4. If NOT the leader, sync state from leader
        if (!LeaderElection.isLeader) {
            try {
                // Assuming leader runs on port 50051 — adjust if needed
                ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                        .usePlaintext()
                        .build();

                TicketServiceGrpc.TicketServiceBlockingStub stub = TicketServiceGrpc.newBlockingStub(channel);

                Ticket.SyncStateResponse response = stub.syncState(
                        Ticket.SyncStateRequest.newBuilder()
                                .setNodeId(nodeId)
                                .build());

                for (Ticket.ShowData data : response.getShowsList()) {
                    Ticket.ShowRequest show = Ticket.ShowRequest.newBuilder()
                            .setName(data.getName())
                            .setRegular(data.getRegular())
                            .setVip(data.getVip())
                            .setAfterParty(data.getAfterParty())
                            .build();
                    TicketServiceImpl.shows.put(data.getShowId(), show);
                }

                System.out.println("Synced full state from leader.");
                channel.shutdown();

            } catch (Exception e) {
                System.out.println("Failed to sync from leader: " + e.getMessage());
            }
        }

        // 5. Keep running
        server.awaitTermination();
    }
}
