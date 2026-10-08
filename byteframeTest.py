import asyncio, json, struct, websockets


def chunk(file_id: str, offset: int, flags: int, data: bytes) -> bytes:
    fid = file_id.encode()
    return bytes([len(fid)]) + fid + struct.pack(">q", offset) + bytes([flags]) + data

async def main():
    async with websockets.connect("ws://localhost:5000/ws") as ws:
        await ws.send(json.dumps({"id": "1", "type": "hello", "data": {"device_name": "python_test"}})) # hello
        print("hello ->", await ws.recv())

        await ws.send(json.dumps({
            "id": "78",
            "type": "offer_files",
            "data": {"files" : [
                {"file_id": "a", "relative_path": "test/a.txt", "size": 100}
            ]}
        }))


        ack = await ws.recv()
        print("ack ->", ack) #why is this fucker crashing, im not sending anything
        await ws.send(chunk("a", 0, 0, b"1234567890"))

        try:
            while True:
                print(await asyncio.wait_for(ws.recv(), 1))
        except (asyncio.TimeoutError, websockets.exceptions.ConnectionClosed):
            print("nic nepřišlo")

        await asyncio.sleep(0.5)

asyncio.run(main())
