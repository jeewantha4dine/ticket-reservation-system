package com.ticket;

import io.grpc.stub.StreamObserver;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ticket.TicketServiceGrpc;
import ticket.Ticket;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TicketServiceImpl extends TicketServiceGrpc.TicketServiceImplBase {

    public static final Map<String, Ticket.ShowRequest> shows = new HashMap<>();

    // Addresses of other servers (followers) to replicate to
    private final List<String> followerAddresses = List.of(
            "localhost:50052",
            "localhost:50053"
    );

    @Override
    public void addShow(Ticket.ShowRequest request, StreamObserver<Ticket.ShowResponse> responseObserver) {
        if (!LeaderElection.isLeader) {
            responseObserver.onNext(Ticket.ShowResponse.newBuilder()
                    .setMessage("Not the leader — cannot add show")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        String showId = UUID.randomUUID().toString();
        shows.put(showId, request);

        // Replicate to followers
        replicateShow(showId, request);

        Ticket.ShowResponse response = Ticket.ShowResponse.newBuilder()
                .setShowId(showId)
                .setMessage("Show added successfully!")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveTicket(Ticket.BookingRequest request, StreamObserver<Ticket.BookingResponse> responseObserver) {
        if (!LeaderElection.isLeader) {
            responseObserver.onNext(Ticket.BookingResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Not the leader — cannot reserve tickets")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        Ticket.ShowRequest show = shows.get(request.getShowId());

        if (show == null) {
            responseObserver.onNext(Ticket.BookingResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Show not found")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        int regular = show.getRegular();
        int vip = show.getVip();
        int afterParty = show.getAfterParty();

        if (request.getIncludeAfterParty() && afterParty <= 0) {
            responseObserver.onNext(Ticket.BookingResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("No after-party tickets available")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        Ticket.ShowRequest updated = Ticket.ShowRequest.newBuilder()
                .setName(show.getName())
                .setRegular(regular - 1)
                .setVip(vip)
                .setAfterParty(request.getIncludeAfterParty() ? afterParty - 1 : afterParty)
                .build();

        shows.put(request.getShowId(), updated);

        for (String address : followerAddresses) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
                TicketServiceGrpc.TicketServiceBlockingStub stub = TicketServiceGrpc.newBlockingStub(channel);

                stub.syncReservation(Ticket.SyncReservationRequest.newBuilder()
                        .setShowId(request.getShowId())
                        .setRegular(updated.getRegular())
                        .setVip(updated.getVip())
                        .setAfterParty(updated.getAfterParty())
                        .build());

                channel.shutdown();
            } catch (Exception e) {
                System.out.println("Failed to replicate reservation to " + address + ": " + e.getMessage());
            }
        }


        // Replicate updated state to followers
        replicateShow(request.getShowId(), updated);

        responseObserver.onNext(Ticket.BookingResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Ticket reserved successfully")
                .build());
        responseObserver.onCompleted();
    }

    //Receive replicated show from leader
    @Override
    public void syncShow(Ticket.SyncRequest request, StreamObserver<Ticket.SyncResponse> responseObserver) {
        Ticket.ShowRequest show = Ticket.ShowRequest.newBuilder()
                .setName(request.getName())
                .setRegular(request.getRegular())
                .setVip(request.getVip())
                .setAfterParty(request.getAfterParty())
                .build();

        shows.put(request.getShowId(), show);
        System.out.println("Synced show from leader: " + request.getShowId());

        responseObserver.onNext(Ticket.SyncResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Show synced on follower")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void syncReservation(Ticket.SyncReservationRequest request, StreamObserver<Ticket.SyncResponse> responseObserver) {
        Ticket.ShowRequest updated = Ticket.ShowRequest.newBuilder()
                .setName(shows.get(request.getShowId()).getName())
                .setRegular(request.getRegular())
                .setVip(request.getVip())
                .setAfterParty(request.getAfterParty())
                .build();

        shows.put(request.getShowId(), updated);
        System.out.println("Synced reservation update for show: " + request.getShowId());

        responseObserver.onNext(Ticket.SyncResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Follower updated reservation")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void syncState(Ticket.SyncStateRequest request, StreamObserver<Ticket.SyncStateResponse> responseObserver) {
        List<Ticket.ShowData> syncedData = new ArrayList<>();

        for (Map.Entry<String, Ticket.ShowRequest> entry : shows.entrySet()) {
            Ticket.ShowRequest show = entry.getValue();
            syncedData.add(Ticket.ShowData.newBuilder()
                    .setShowId(entry.getKey())
                    .setName(show.getName())
                    .setRegular(show.getRegular())
                    .setVip(show.getVip())
                    .setAfterParty(show.getAfterParty())
                    .build());
        }

        Ticket.SyncStateResponse response = Ticket.SyncStateResponse.newBuilder()
                .addAllShows(syncedData)
                .build();

        System.out.println("Sending full state to " + request.getNodeId());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }





    //Helper method to replicate a show to followers
    private void replicateShow(String showId, Ticket.ShowRequest show) {
        for (String address : followerAddresses) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
                TicketServiceGrpc.TicketServiceBlockingStub stub = TicketServiceGrpc.newBlockingStub(channel);

                stub.syncShow(Ticket.SyncRequest.newBuilder()
                        .setShowId(showId)
                        .setName(show.getName())
                        .setRegular(show.getRegular())
                        .setVip(show.getVip())
                        .setAfterParty(show.getAfterParty())
                        .build());

                channel.shutdown();
            } catch (Exception e) {
                System.out.println("Failed to replicate to " + address + ": " + e.getMessage());
            }
        }
    }
}
