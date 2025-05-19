package com.ticket;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ticket.Ticket;
import ticket.TicketServiceGrpc;

import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) throws Exception {
        LeaderWatcher leaderWatcher = new LeaderWatcher();
        Scanner sc = new Scanner(System.in);

        while (true) {
            String leaderAddress = leaderWatcher.getCurrentLeader();
            if (leaderAddress == null) {
                System.out.println("❌ No leader found. Retrying...");
                Thread.sleep(2000);
                continue;
            }

            ManagedChannel channel = ManagedChannelBuilder
                    .forTarget(leaderAddress)
                    .usePlaintext()
                    .build();

            TicketServiceGrpc.TicketServiceBlockingStub stub =
                    TicketServiceGrpc.newBlockingStub(channel);

            System.out.println("\n🎟️ Ticket Reservation Client");
            System.out.println("1. Add Show (Organizer)");
            System.out.println("2. Reserve Ticket (Customer)");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");
            int choice = sc.nextInt();

            try {
                if (choice == 1) {
                    sc.nextLine();  // flush newline
                    System.out.print("Show Name: ");
                    String name = sc.nextLine();
                    System.out.print("Regular seats: ");
                    int regular = sc.nextInt();
                    System.out.print("VIP seats: ");
                    int vip = sc.nextInt();
                    System.out.print("After-party tickets: ");
                    int party = sc.nextInt();

                    Ticket.ShowRequest request = Ticket.ShowRequest.newBuilder()
                            .setName(name)
                            .setRegular(regular)
                            .setVip(vip)
                            .setAfterParty(party)
                            .build();

                    Ticket.ShowResponse response = stub.addShow(request);
                    System.out.println("✅ " + response.getMessage());
                    System.out.println("Show ID: " + response.getShowId());

                } else if (choice == 2) {
                    sc.nextLine();  // flush newline
                    System.out.print("Enter Show ID: ");
                    String showId = sc.nextLine();
                    System.out.print("Include after-party? (true/false): ");
                    boolean withParty = sc.nextBoolean();
                    System.out.print("How many tickets?: ");
                    int count = sc.nextInt();

                    Ticket.BookingRequest bookReq = Ticket.BookingRequest.newBuilder()
                            .setShowId(showId)
                            .setIncludeAfterParty(withParty)
                            .setTicketCount(count)
                            .build();

                    Ticket.BookingResponse bookRes = stub.reserveTicket(bookReq);
                    System.out.println("🎫 " + bookRes.getMessage());

                } else if (choice == 0) {
                    channel.shutdown();
                    break;
                }

            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }

            channel.shutdown();
        }

        sc.close();
    }
}
