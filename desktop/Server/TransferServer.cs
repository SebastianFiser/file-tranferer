using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using Microsoft.Extensions.Logging;
using System.Text.Json;
using System.IO;


namespace desktop.Server;

public class TransferServer
{
    private WebApplication? _app;
    private const int _maxFileCount = 10000;
    private const long _maxTotalSize = 50L * 1024 * 1024;

    static readonly string _basePath = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
    static readonly string _appPath = Path.Combine(_basePath, "file-transferer");

    private const int MaxBatchFiles = 500;
    private const long MaxBatchBytes = 64L * 1024 * 1024;

    public async Task StartAsync()
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls("http://0.0.0.0:5000");
        Directory.CreateDirectory(_appPath);


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
            var state = new ConnectionState();
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
                    if(!state.HandshakeDone && msg.Type != "hello")
                    {
                        await SendAsync(ws, MakeError(msg.Id, "handshake_required", "send hello first"), options);
                        continue;
                    } //why isnt this bullshit hackatime tracking my terminal??
                    switch (msg.Type) //i just want to sleep my friend.
                    {
                        case "hello":
                            if (state.HandshakeDone) {
                                await SendAsync(ws, MakeError(msg.Id, "already_handshaken", "handshake already done"), options);
                                continue;
                            }
                            state.HandshakeDone = await HandleHelloAsync(ws, msg, options, state);
                            break;
                        case "offer_files":
                            await HandleOfferFilesAsync(ws, msg, options, state);
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

    private static async Task<bool> HandleHelloAsync(WebSocket ws, Message msg, JsonSerializerOptions options, ConnectionState state)
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

        if (deviceName.Contains('/')
            || deviceName.Contains('\\')
            || deviceName.Contains(".."))
        {
            await SendAsync(ws, MakeError(msg.Id, "invalid_device_name", "device_name contains invalid characters"), options);
            return false;
        }
        state.DeviceName = deviceName;
        state.DevicePath = Path.Combine(_appPath, deviceName);

        Console.WriteLine($"device_name: {state.DeviceName}");
        Console.WriteLine($"device_path: {state.DevicePath}");
        var data = JsonSerializer.SerializeToElement(new { server_name = "PC", protocol_version = 1});
        var reply = new Message(msg.Id, "hello_ack", data);
        await SendAsync(ws, reply, options);
        return true;
    }

    private static async Task HandleOfferFilesAsync(WebSocket ws, Message msg, JsonSerializerOptions options, ConnectionState state)
    {

        if (!state.HandshakeDone) {
            await SendAsync(ws, MakeError(msg.Id, "handshake_required", "send hello first"), options);
            return;
        }
        if (state.TransferId != null) {
            await SendAsync(ws, MakeError(msg.Id, "transfer_in_progress", "a transfer is already in progress"), options);
            return;
        }

        var error = ValidateOffer(msg.Data);
        if (error != null)
        {
            await SendAsync(ws, MakeError(msg.Id, error.Code, error.Message), options);
            return;
        }
        long fileSize = CalculateSize(msg.Data);

        var drive = new DriveInfo(_appPath);
        long free = drive.AvailableFreeSpace;

        if (fileSize > free)
        {
            await SendAsync(ws, MakeError(msg.Id, "insufficient_space", "theres not enough space to transfer files"), options);
            return;
        }
        var baseDir = Path.GetFullPath(Path.Combine(_appPath, state.DevicePath));
        var baseDirWithSep = Path.TrimEndingDirectorySeparator(baseDir) + Path.DirectorySeparatorChar;

        var files = new Dictionary<string, FileTransferState>();

        foreach ( var file in msg.Data.GetProperty("files").EnumerateArray())
        {
            var relativePath = file.GetProperty("relative_path").GetString();
            var fileId = file.GetProperty("file_id").GetString();
            var size = file.GetProperty("size").GetInt64();

            var target = Path.GetFullPath(Path.Combine(baseDir, relativePath));

            if (!target.StartsWith(baseDirWithSep, StringComparison.Ordinal))
            {
                await SendAsync(ws, MakeError(msg.Id, "invalid_path", "path is outside the device directory"), options);
                return;
            }

            files[fileId] = new FileTransferState
            {
                RelativePath = relativePath,
                Size = size
            };

        }

        Directory.CreateDirectory(baseDir);
        state.Files = files;
        state.TransferId = "t_" + Guid.NewGuid().ToString("N");

        await sendOfferAckAsync(ws, msg.Id, options, state);

    }

    static async Task sendOfferAckAsync(WebSocket ws, string id, JsonSerializerOptions options, ConnectionState state)
    {

        await SendAsync(ws, MakeOfferAck(id, state.TransferId!), options);
        var batch = SelectBatch(state);

        if (batch.Count > 0)
            await SendAsync(ws, MakeRequestFiles(state.TransferId!, batch), options);

    }

    private static Message MakeOfferAck(string id, string transferId)
    {

        var data = JsonSerializer.SerializeToElement(new
        {
            accepted = true,
            transfer_id = transferId,
            max_batch_files = MaxBatchFiles,
            max_batch_bytes = MaxBatchBytes,
        });

        return new Message(id, "offer_ack", data);
    }

    private static List<string> SelectBatch(ConnectionState state)
    {
        var batch = new List<string>();
        long batchBytes = 0;

        foreach (var (id, f) in state.Files)
        {
            if (f.Requested) continue;

            if (batch.Count > 0 && batchBytes + f.Size > MaxBatchBytes) continue;
            if (batch.Count >= MaxBatchFiles) break;

            batch.Add(id);
            batchBytes += f.Size;
            f.Requested = true;
        }
        return batch;
    }

    private static Message MakeRequestFiles(string transferId, List<string> fileIds)
    {
        var data = JsonSerializer.SerializeToElement(new
        {
            transfer_id = transferId,
            file_ids = fileIds
        });

        return new Message("s_" + Guid.NewGuid().ToString("N"), "request_files", data);
    }

    private static long CalculateSize(JsonElement data) {
        long total = 0;
        foreach (var file in data.GetProperty("files").EnumerateArray())
        {
            total = checked(total + file.GetProperty("size").GetInt64());
        }

        return total;
    }

    private static ValidationError? ValidateOffer(JsonElement data)
    {
        if (data.ValueKind != JsonValueKind.Object)
            return new ValidationError("invalid_data", "data must be an object");


        if( !data.TryGetProperty("files", out var files)
            || files.ValueKind != JsonValueKind.Array)
        {
            return new ValidationError("invalid_data", "files must be an array");
        }

        if(files.GetArrayLength() > _maxFileCount)
            return new ValidationError("file_count_too_high", "file count must be less than or equal to " + _maxFileCount);

        long total = 0;
        var relativePathHash = new HashSet<string>();
        var fileIdHash = new HashSet<string>();
        var count = files.GetArrayLength();
        if (count > _maxFileCount)
            return new ValidationError("file_count_too_high", "file count must be less than or equal to " + _maxFileCount);
        for (var i = 0; i < count; i++)
        {
            var file = files[i];
            if (file.ValueKind != JsonValueKind.Object)
                return new ValidationError("invalid_data", $"files must be an array of objects at index {i}");

            if ( !file.TryGetProperty("relative_path", out var rp)
                || rp.ValueKind != JsonValueKind.String || string.IsNullOrWhiteSpace(rp.GetString()))
                return new ValidationError("invalid_filepath", $"relative_path must be a string at index {i}");

            var path = rp.GetString()!;
            var segments = path.Split('/');

            if (path.StartsWith('/')
                || path.Contains('\\')
                || segments.Contains("..")
                || segments.Contains(""))
            {
                return new ValidationError("invalid_path", $"relative_path is unsafe at index {i}");
            }

            if (!relativePathHash.Add(path)) {
                return new ValidationError("duplicate_path", $"duplicate relative path at index {i}");
            }

            if (!file.TryGetProperty("size", out var fs)
                || fs.ValueKind != JsonValueKind.Number
                || !fs.TryGetInt64(out var size)
                || size < 0)
            {
                return new ValidationError("invalid_size", $"size must be long 64 non negative number at index {i}");
            }

            if (size > _maxTotalSize - total)
                return new ValidationError("size_too_large", $"combine site exceeds limit at index {i}");
            total += size;

            if (!file.TryGetProperty("file_id", out var fileId)
                || fileId.ValueKind != JsonValueKind.String
                || string.IsNullOrWhiteSpace(fileId.GetString()!))
            {
                return new ValidationError("invalid_file_id", $"file_id must be a non empty string at index {i}");
            }

            var id = fileId.GetString()!;

            if (!fileIdHash.Add(id)) {
                return new ValidationError("duplicate_id", $"duplicate file id at index {i}");
            }



        }

        return null;
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

public record ValidationError(
    string Code,
    string Message
);
