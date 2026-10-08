# 30.9
  -made android build work 
  -raisede compile SDK and fixed naming keys from build gradle.ktes

  -setup dotnet with avalonia template
  start more work


  ## Selected flow
    - decided to start with WIFI transmission- firstly set ip, when mDNS discovery, for sztarting purposes, android will use unsecured ws:// traffic, lter wss:// 
  - alsop have to have availible port on pc

  ## using localhostz or 0.0.0.0
  - we have to use 0.0.0.... because server has to be seen for the whole network, thats bit of an security issue, but localhost is unreachable from outside

# MIAN TODO NOTES 7.10
- when size of file is none, we need to write the file to the disk still, has to be done in "HandleOfferFilesAsync"
- add an cliemt-server interaction to let client klnow about the finish / recive of file transfer
- chunk twice or in the wrong order- has to be hgandled
