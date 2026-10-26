# File-transferer.
lightweight project conatining two apps, a c# server and kolin android client.

"app screenshots"

# Functions
Currently working:
-simple UI response
-device search in local networks
-mulotiple file ending at once
-integrity checking trough sha-256 hash
-biggest file sent currentlz 1.1GB

Not working:
-easy to use UI, smooth withou issues using
-idk like everzthing

# How to currently run.
download andrpoid client and server from the latest official release.
start the desktop app on your linux device
download and launch .apk on zour android device
follow instructions on the screen

# How doeas out protocol work
c is client s is server
S: offer itself on tcp trough NSD discoverz
C: finds server and saves its info

C: creates a websocket conncetion, sending "hello" *view in notes*
S: after reciewing hello, log device info, if its not connected and no transfer is currentlz running, respond "offer_ack"

C: only after offer_ack, can client select files he's going to send
C: picked files get gathered together and send with "offer_files* *view structure in notes*
S: check validation of offered files, size.
S: if seerzthings correct, respond "offer_ack" following with "request_files" witch usualz asks for as much files as it can to fit in one batch.

C: processes requested files, picks them FROM THE OFFERED LIST (if its not there it cant be sent)
C: slices file into bztefraes, start sending
S: server catches these bzteframes, and catches the path, creates it, and start assembeling the file. if the file isnt yet the size it should be, its currently as a ,part file.
S: affter recieving the last file, it sends "transfer_complete" witch disconnects the client, closes connection an continues with its existance.

# Protocols
-> binarz frames [1 B: lenght file_id][N B: filer_id][9 B: offset][1 B:flags][data...]
these binarz frames are sent trough the websocket connection making zour files

# Technologies

Server side was developed with dotnet 10 C# and Avalonia framework
Client side developed with jackpack compose, kotlin

# Roadmap
-no auth yet, locked to local network
- HMAC auth planning
- sophisticated UI with really intuitive use

# Time layouts
we have to get 117 hours on this project until 22 dec.
Currentlz that comes up tp 1.45h a daz, lets see hows it in two dazs after not coding because of work