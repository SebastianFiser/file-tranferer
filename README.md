# file-transferer

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Platform](https://img.shields.io/badge/Platform-Linux-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-3.0-blue)
![C#](https://img.shields.io/badge/C%23-10.0-purple?color=purple)
![License](https://img.shields.io/badge/License-GNU-red)

![Demo](none yet)

## How does it work ?
Your phone finds the PC trough mDNS discovery, connects trough a WebSocket, offers files.Pc confirms them, and requests them by batches. At the end is a SHA-256 checksum

## RUN
### PC (Avalonia)
    To run the transfer server on the PC side, download the file from official release, and install it. You can then run it as any normal application
### Android
    To start the client side, zyou have to download the official .APK file from releases, and install it. **YOU HAVE TO HAVE SIDELOADING ALLOWED**
    Run it as any other app on your phone.

## Protocol

|Type|Path|Data|
|---|---|---|
|hello|phone -> PC|device_name|
|---|---|---|
|hello_ack|PC -> phone|server_name, protocol_version (1)|
|---|---|---|
|offer_files|phone -> PC|files[]: file_id, relative_path, size|
|---|---|---|
|offer_ack|PC -> phone|accepted, ransfer_id, max_batch_files (500), max_batch_bytes (64MB)|
|---|---|---|
|request_files|PC -> phone|transfer_id, file_ids[]|
|---|---|---|
|transfer_complete|PC -> phone|transfer_id, hash[]: file_id, sha256|
|---|---|---|
|cancel/cancelled|both ways|transfer_id/empty|
|---|---|---|
|error|PC -> phone|code, message|
|---|---|---|

### Binary chunk
    [1 B id length][id UTF-8][8 B offset, big-endian int64][1 B flags][data]

### Constants 
    -mDNS dicovery name "_filetransferer._tcp"
    -default websocket path "ws://[discovered path]:5000*/ws"
    * -> server defautly brodcasts ip adress and port 5000 from default if 5000 isnt availible on mashine running server, it sellects and brodcasts different address

### Safety and Limits
    - *Path*
        i - server caches the path that phone sent, cleans it, and validates that its a real adress
        ii - Server can request only the cached paths
        iii - every incoming file is validated aginst the casched files, if it doesnt match its rejected

    - *Size*
        i - before transfer phone checks if the entirety of transfer is smaller than 50 GB
        ii - Server caches these sizes, and request __BATCHES__ of files of less than 64MB
        iii - Phone sends these byteframes with these byteframes of max size 1 MB
        iv - before the entire transfer, server itself, has got to be checking the byteframes, batch sizes, and the entire transfer.
        v - before the transfer, server does a precheck of disk space, and the same while transfering

    - *interruption, offset mismatch*
        i - if the server or client interrupted by cancell, server deletes the entirety of the transfer.
        ii - if transfer is interrupted by exception, websocket drop, or sudden error / disconnect, server stashes the last (currently transferrred) .part file.
        iii - if transferr is dropped by sudden loss of wifi, theres a timeout, witch gives devices space to recover connection and continue sending

## Roadmap
    //Only latest additions
    - [x] SHA-256 check, side panel, history, czech lang support
    - [ ] LZ4 Compression
    - [ ] Liquid glass look
    - [ ] Animations, detailed instriuictions
    - [ ] USB transfer
         
