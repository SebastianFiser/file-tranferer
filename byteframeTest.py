import asyncio, json, struct, hashlib, websockets
from websockets.exceptions import ConnectionClosed

URI = "ws://localhost:5000/ws"
_id = 0

def chunk(file_id: str, offset: int, flags: int, data: bytes) -> bytes:
    fid = file_id.encode()
    return bytes([len(fid)]) + fid + struct.pack(">q", offset) + bytes([flags]) + data

async def send_json(ws, type_, data):
    global _id
    _id += 1
    print(f"  -> {type_}")
    await ws.send(json.dumps({"id": str(_id), "type": type_, "data": data}))

async def drain(ws):
    """Print everything the server sends until it is quiet for 1 second."""
    try:
        while True:
            print("  <-", await asyncio.wait_for(ws.recv(), 1))
    except (asyncio.TimeoutError, ConnectionClosed):
        pass

def scene(title):
    print(f"\n=== {title} ===")

async def send_chunk(ws, file_id, offset, data):
    print(f"  -> binary chunk: file={file_id} offset={offset} bytes={len(data)}")
    await ws.send(chunk(file_id, offset, 0, data))

async def main():
    async with websockets.connect(URI) as ws:

        scene("1. Handshake (hello)")
        await send_json(ws, "hello", {"device_name": "python_test"})
        await drain(ws)

        scene("2. Happy path: two files, one batch")
        await send_json(ws, "offer_files", {"files": [
            {"file_id": "a", "relative_path": "demo/a.txt", "size": 5},
            {"file_id": "b", "relative_path": "demo/b.txt", "size": 3},
        ]})
        await drain(ws)                      # offer_ack + request_files
        await send_chunk(ws, "a", 0, b"hello")
        await send_chunk(ws, "b", 0, b"abc")
        await drain(ws)                      # transfer_complete
        print("  local sha256('hello') =", hashlib.sha256(b"hello").hexdigest())

        scene("3. Duplicate chunk is rejected (bad_offset), then transfer finishes")
        await send_json(ws, "offer_files", {"files": [
            {"file_id": "c", "relative_path": "demo/c.txt", "size": 10},
        ]})
        await drain(ws)
        await send_chunk(ws, "c", 0, b"hello")
        await send_chunk(ws, "c", 0, b"WORLD")       # wrong offset
        await drain(ws)                              # expect bad_offset
        await send_chunk(ws, "c", 5, b"12345")       # correct continuation
        await drain(ws)                              # transfer_complete

        scene("4. Empty file (size 0) completes without any chunk")
        await send_json(ws, "offer_files", {"files": [
            {"file_id": "d", "relative_path": "demo/d.txt", "size": 0},
        ]})
        await drain(ws)                      # offer_ack + transfer_complete

        scene("5. Interrupted transfer leaves a .part file")
        await send_json(ws, "offer_files", {"files": [
            {"file_id": "e", "relative_path": "demo/e.txt", "size": 10},
        ]})
        await drain(ws)
        await send_chunk(ws, "e", 0, b"12345")       # only half of the file
        await drain(ws)
        print("  disconnecting early; demo/e.txt.part should remain on disk")

asyncio.run(main())
