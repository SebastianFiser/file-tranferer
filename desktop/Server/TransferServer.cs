using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using Microsoft.Extensions.Logging;
using System.Text.Json;


namespace desktop.Server;

public class TransferServer
{
    private WebApplication? _app;

    public async Task StartAsync()
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls("http://0.0.0.0:5000");


        _app = builder.Build();

        _app.UseWebSockets();

        _app.Map("/ws", async context =>
        {
            if (!context.WebSockets.IsWebSocketRequest)
            {
                context.Response.StatusCode = 400;
                return;
            }
            using var ws = await context.WebSockets.AcceptWebSocketAsync();
            var buffer = new byte[8];
            var handshakeDone = false;
            try
            {
                while (true)
                {

                    using var ms = new MemoryStream();
                    WebSocketReceiveResult result;

                    do
                    {
                        result = await ws.ReceiveAsync(buffer, CancellationToken.None);
                        if (result.MessageType == WebSocketMessageType.Close) {
                            await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None);
                            return;
                        }
                        ms.Write(buffer, 0, result.Count);
                    }
                    while (!result.EndOfMessage);

                    var text = Encoding.UTF8.GetString(ms.ToArray());
                    var options = new JsonSerializerOptions {
                        PropertyNameCaseInsensitive = true,
                        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                    };
                    var msg = JsonSerializer.Deserialize<Message>(text, options);
                    if (msg is null)
                    {
                        Console.WriteLine("Invalid message");
                        continue;
                    }
                    if(!handshakeDone && msg.Type != "hello")
                    {
                        Console.WriteLine("Handshake missing, ignoring message");
                        continue;
                    }
                    switch (msg.Type)
                    {
                        case "hello":
                            var deviceName = msg.Data.GetProperty("device_name").GetString();
                            handshakeDone = true;
                            Console.WriteLine($"device_name: {deviceName}");
                            var data = JsonSerializer.SerializeToElement(new { server_name = "PC", protocol_version = 1});
                            var reply = new Message(msg.Id, "hello_ack", data);
                            var json = JsonSerializer.Serialize(reply, options);
                            var bytes = Encoding.UTF8.GetBytes(json);
                            await ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);

                            break;
                        default:
                            Console.WriteLine($"Uknown type: {msg.Type}");
                            break;
                    }

                    Console.WriteLine(msg);
                }
            }
            catch (WebSocketException ex)
            {
                Console.WriteLine("Client crashed: {Msg}", ex.Message);
            }
            finally
            {
                Console.WriteLine("Client Disconnected");
            }
        });

        await _app.StartAsync();
    }

    public async Task StopAsync()
    {
        if (_app != null)
        {
            await _app.StopAsync();
            await _app.DisposeAsync();
            _app = null;
        }
    }
}

public record Message(
    string Id,
    string Type,
    JsonElement Data
);
