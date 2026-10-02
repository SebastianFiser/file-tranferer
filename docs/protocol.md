bad message
{
  "id": "1", //for unrreadeable message set to 0
  "type": "error",
  "data": {
    "code": "" //error name for ex. handshake required
    "message": "" //message
  }
}
-> general error message
-. used to respond with a cubśtom error message 
- sent from server to client


hello handshake
  -device
  {
    "id":"0",
    "type": "hello",
    "data": {
      "device_name": "", //required data 
      "protocol_version": 0 // currently not required data
    }
  }
  -server
  {
    "id" : "0",
    "type": "hello_ack",
    "data": {
      "server_name": "",
      "protocol_version": 0
    }
  }
-info -> from client to server
-response in succes (hello_ack)
-can cause -> bad message error, already handshaken error, and invalid data, handsk´hake required error responses
example 
´´´
VM880:2 server: {"id":"0","type":"error","data":{"code":"already_handshaken","message":"handshake already done"}}
´´´


file listing command
  json meesage sketch-
    {
      "id": "",
      "type": "list_files",
      "data": {
        "folder_id": "root
      }
    }
  json resopnse sketch- correct¨
    {
      "id": "",
      "type": "list_files_result",
      "ok": true,
      "data": {
        "folder_id": "root",
        "items": [
          "item_id": "",
          "name": "",
          "is_dir": false,
          "size": 0,
          "modified": 0
        ]
      }
    }

    bad reponse-
    {
      "id": "",
      "type": "list_files_result",
      "ok": false,
      "error": {
        "code": "",
        "message": ""
      }
    }

file download
  -request
  {
    "id": "",
    "type": "download_file",
    "data": {
      "item_id": "",
      "transfer_id": ""
    }
  }  -response bad
  {
    "id": "",
    "type": "download_file_result",
    "ok": false,
    "error": {
      "code": "",
      "message": ""
    }
  }
  -response good
  {
    "id": "",
    "type": "download_file_result",
    "ok": true,
    "data": {
      "item_id": "",
      "name": "",
      "size": 0,
      "modified": 0,
      "transfer_id": ""
    }
  }
-- mobile device/ phone (client) is sending requests at the laptop (server), less firewall issues etc.

File offer->
from phone to server, selected by user

{
  "id": "0",
  "type": "offer_files",
  "data": {
    "files": [
      {
        "relative_path": "",
        "size": 0,
        "file_id": ""
      },
      {
        "relative_path": "",
        "size": 0,
        "file_id": ""
      }
    ]
  }
}

two files: nhave to chceck -> path i string nonempty, check for malicius injection like ".." " &&" check if its a absoluthe path, and check if it starts at root 
size -> is it a nonnegative number? is it readable as an long 64 number
file_id: is it a string? is it uniqe, what to do with dupes:
limits: what if xlinet send bnilions of files, or size too larcǵe? -> check device, server should have a ceiling and send error
errors-> file not data "not_data", size too large "too_large", iknvalid data "invalid_data"

Control, code, whole messg
data is not an object -> invalid_data -> yes
files missing or isnt a field -> invalid_data -> yes
element in field is not an object -> invalid_data -> yes¨
relative path is missing, isnt a string is empty -> invalid_file -> yes
relative path contains .. segments, starts / or C\¨, constrains \ -> invalid_path -> yes
after path.GetfullPath the path misses the target  invalid_path -> yes
size missing, isnt number, unreadeble as long 64 number -> invalid_data -> yes
size is negative -> invalid_data -> yes
file_id missing, isnt a string -> invalid_data -> yes
file_id is duplicate -> invalid_data -> yes //hash setting 
two files have the same relative path -> invalid_data -> yes
file count is higher than Maxfiles -> invalid_data -> yes //around 10 000 files
whole size amount larger than MaxTotalSize -> invalid_data -> yes /around 50GB at once 
theres not enough space on disk -> invalid_data -> yes
