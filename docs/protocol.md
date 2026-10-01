hello handshake
  -device
  {
    "id":"0"
    "type": "hello"
    "data": {
      "device_name": "",
      "protocol_version": 0
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
