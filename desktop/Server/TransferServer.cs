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
                    Message? msg;
                    try
                    {
                        msg = JsonSerializer.Deserialize<Message>(text, options);
                    }
                    catch (JsonException)
                    {
                        await SendAsync(ws, MakeError("0", "invalid_message", "Not a valid Json"), options);
                        continue;
                    }
                    if (msg is null)
                    {
                        await SendAsync(ws, MakeError("0", "invalid_message", "invalid message"), options);
                        continue;
                    }
                    if(!handshakeDone && msg.Type != "hello")
                    {
                        await SendAsync(ws, MakeError(msg.Id, "handshake_required", "send hello first"), options);
                        continue;
                    }
                    switch (msg.Type)
                    {
                        case "hello":
                            if (handshakeDone) {
                                await SendAsync(ws, MakeError(msg.Id, "already_handshaken", "handshake already done"), options);
                                continue;
                            }
                            handshakeDone = await HandleHelloAsync(ws, msg, options);
                            break;
                        case "offer_files":
                            await HandleOfferFilesAsync(ws, msg, options);
                            break;
                        default:
                            await SendAsync(ws, MakeError(msg.Id, "unknow type", "send a valid type"), options);
                            continue;
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

    private static async Task<bool> HandleHelloAsync(WebSocket ws, Message msg, JsonSerializerOptions options)
    {
        if (msg.Data.ValueKind != JsonValueKind.Object)
        {
            await SendAsync(ws, MakeError(msg.Id, "invalid_data", "send a valid data"), options);
            return false;
        }
        if (!msg.Data.TryGetProperty("device_name", out var el)
            || el.ValueKind != JsonValueKind.String)
        {
            await SendAsync(ws, MakeError(msg.Id, "invalid_message", "device_name is required"), options);
            return false;
        }
        var deviceName = el.GetString();
        Console.WriteLine($"device_name: {deviceName}");
        var data = JsonSerializer.SerializeToElement(new { server_name = "PC", protocol_version = 1});
        var reply = new Message(msg.Id, "hello_ack", data);
        await SendAsync(ws, reply, options);
        return true;
    }

    private static async Task HandleOfferFilesAsync(WebSocket ws, Message msg, JsonSerializerOptions options)
    {
        var validate = await ValidateMessageAsync(msg, options);
        if (!validate)
        {
            await SendAsync(ws, MakeError(msg.Id, "invalid_data", "invalid data"), options);
            return;
        }

    }

    private static async Task<bool> ValidateMessageAsync(Message msg, JsonSerializerOptions options)
    {
        if (msg.ValueKind != Object)
        {
            return false;
        }

    }

    static Message MakeError(string id, string code, string message)
    {
        var data = JsonSerializer.SerializeToElement(new { code, message });
        return new Message(id, "error", data);
    }

    static async Task SendAsync(WebSocket ws, Message msg, JsonSerializerOptions options)
    {
        var json = JsonSerializer.Serialize(msg, options);
        var bytes = Encoding.UTF8.GetBytes(json);
        await ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
    }

}
