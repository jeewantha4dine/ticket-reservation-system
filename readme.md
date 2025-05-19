# Distributed Concert Ticket Reservation System

This is a distributed system written in Java using gRPC and ZooKeeper. It supports leader election, replication, and fault-tolerant ticket booking.

## Features
- Add & reserve tickets
- Optional after-party booking
- Leader election via ZooKeeper
- Replication across nodes
- CLI client for testing

## How to Run
1. Start ZooKeeper
2. Start server nodes with different ports (e.g., 50051, 50052)
3. Use the CLI client to interact

## Requirements
- Java 17+
- Maven
- Apache ZooKeeper
