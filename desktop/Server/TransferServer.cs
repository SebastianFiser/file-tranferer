using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using Microsoft.Extensions.Logging;
using System.Text.Json;
using System.Buffers.Binary;
using System.IO;
using System.Security.Cryptography;
using Makaretu.Dns;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Linq;
using desktop.Models;
using System.Runtime.CompilerServices;

namespace desktop.Server;

public class TransferServer
{
    private WebApplication? _app;
    public static Func<OfferInfo, CancellationToken, Task<bool>>? ConfirmOfferAsync { get; set; }
    private int _port;
    private const int _maxFileCount = 10000;
    private const long _maxTotalSize = 20L * 1024 * 1024 * 1024;

    static readonly string _basePath = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
    static readonly string _appPath = Path.Combine(_basePath, "file-transferer");

    private const int MaxBatchFiles = 500;
    private const long MaxBatchBytes = 64L * 1024 * 1024;

    private ServiceDiscovery? _sd;
    public static event Action<TransferStatus>? StatusChanged;

    private sealed record ActiveTransfer(WebSocket Ws, ConnectionState State, JsonSerializerOptions Options);
    private static ActiveTransfer? _active;

    private static void Report(ConnectionState s, TransferPhase phase)
    {
        long bytes = 0, total = 0; var done = 0;
        foreach (var f in s.Files.Values)
        {
            total += f.Size;
            bytes += f.BytesRecived;
            if (f.BytesRecived >= f.Size) done++;
        }
        StatusChanged?.Invoke(new TransferStatus(phase, s.DeviceName ?? " ", bytes, total, done, s.Files.Count));
    }

    public static async Task CancelActiveTransferAsync()
    {
        var a = _active;
        if (a == null) return;
        a.State.CancelRequested = true;
        var data = JsonSerializer.SerializeToElement(new { transfer_id = a.State.TransferId});
        try { await SendAsync(a.Ws, new Message("s_" + Guid.NewGuid().ToString("N"), "cancel", data), a.Options); }
        catch { }
    }

    private async Task StartOnPortAsync(int port)
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls($"http://0.0.0.0:{port}");
        Directory.CreateDirectory(_appPath);


        var app = builder.Build();

        app.UseWebSockets();

        app.Map("/ws", async context =>
        {
            if (!context.WebSockets.IsWebSocketRequest)
            {
                context.Response.StatusCode = 400;
                return;
            }
            using var ws = await context.WebSockets.AcceptWebSocketAsync();
            var buffer = new byte[8];
            var state = new ConnectionState();
            var options = new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            };
            try
            {
                while (true)
                {

                    using var ms = new MemoryStream();
                    WebSocketReceiveResult result;

                    do
                    {
                        result = await ws.ReceiveAsync(buffer, CancellationToken.None);
                        if (result.MessageType == WebSocketMessageType.Close)
                        {
                            await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None);
                            return;
                        }
                        ms.Write(buffer, 0, result.Count);
                    }
                    while (!result.EndOfMessage);
                    //why you aint working wro

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Console.WriteLine("client sent close");
                    }
                    if (result.MessageType == WebSocketMessageType.Binary)
                    {
                        if (state.TransferId == null)
                        {
                            continue;
                        }
                        if (state.CancelRequested)
                        {
                            await CleanupTransferAsync(state);
                            continue;
                        }

                        byte[] frame = ms.ToArray();
                        bool ok = TryParseChunk(frame, out var fileId, out var offset, out var flags, out var dataStart);
                        if (ok == false)
                        {
                            await SendAsync(ws, MakeError("0", "invalid_binary", "parsing of this binary frame failed, please send valid data"), options);
                            break;
                        }
                        if (offset == 0) {
                            Console.WriteLine($"{DateTime.Now:HH:mm:ss.fff} first chunk arrived");
                        }

                        var data = frame.AsMemory(dataStart);


                        if (!state.Files.TryGetValue(fileId, out var file))
                        {
                            await SendAsync(ws, MakeError("0", "unknown_file", "no such file is being transferred"), options);
                            continue;
                        }
                        var partPath = file.TargetPath + ".part";

                        if (offset < 0 || offset + data.Length > file.Size)
                        {
                            await SendAsync(ws, MakeError("0", "bad_range", "offset is out of bounds"), options);
                            continue;
                        }
                        Console.WriteLine($"chunk file={fileId} offset={offset} received={file.BytesRecived} size={file.Size}");
                        if (offset != file.BytesRecived)
                        {
                            await SendAsync(ws, MakeError("0", "bad_offset", "chunk or offset does not match the receiving bytes"), options);
                            continue;
                        }


                        if (file.Stream == null)
                        {
                            Directory.CreateDirectory(Path.GetDirectoryName(file.TargetPath)!);
                            file.Stream = new FileStream(partPath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.None);
                            file.Hasher = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
                        }
                        file.Stream.Seek(offset, SeekOrigin.Begin);
                        await file.Stream.WriteAsync(data);
                        file.Hasher!.AppendData(data.Span);
                        file.BytesRecived += data.Length;

                        var tick = Environment.TickCount64;
                        if (tick - state.LastReportTick > 100)
                        {
                            state.LastReportTick = tick;
                            Report(state, TransferPhase.Progress);
                        }

                        if (file.BytesRecived >= file.Size)
                        {
                            file.Sha256 = Convert.ToHexString(file.Hasher!.GetHashAndReset());
                            file.Hasher.Dispose();
                            file.Hasher = null;
                            await file.Stream.DisposeAsync();
                            file.Stream = null;
                            File.Move(partPath, file.TargetPath, overwrite: true);
                            await CompleteIfDoneAsync(ws, options, state);
                            Console.WriteLine($"{DateTime.Now:HH:mm:ss.fff} file {fileId} finalized");

                        }


                        continue;
                    }

                    var text = Encoding.UTF8.GetString(ms.ToArray());
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
                    if (!state.HandshakeDone && msg.Type != "hello")
                    {
                        await SendAsync(ws, MakeError(msg.Id, "handshake_required", "send hello first"), options);
                        continue;
                    }
                    switch (msg.Type) //i just want to sleep my friend. yeah me too dw
                    {
                        case "hello":
                            if (state.HandshakeDone)
                            {
                                await SendAsync(ws, MakeError(msg.Id, "already_handshaken", "handshake already done"), options);
                                continue;
                            }
                            state.HandshakeDone = await HandleHelloAsync(ws, msg, options, state);
                            break;
                        case "offer_files":
                            _ = HandleOfferFilesAsync(ws, msg, options, state).ContinueWith(
                                t => Console.WriteLine($"offer handler failed: {t.Exception?.GetBaseException().Message}"),
                                TaskContinuationOptions.OnlyOnFaulted);
                            break;
                        case "cancel":
                            CancelOffer(state);
                            await CleanupTransferAsync(state);
                            await SendAsync(ws, new Message(msg.Id, "cancelled", JsonSerializer.SerializeToElement(new {})), options);
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
                Console.WriteLine($"Client crashed: {ex.Message}");
            }
            catch (OperationCanceledException)
            {
                Console.WriteLine("Client connection aborted");
            }
            finally
            {
                Console.WriteLine("Connection closed, cleaning up");
                CancelOffer(state);
                await CleanupTransferAsync(state);
            }
        });

        try
        {
            await app.StartAsync();
        }
        catch
        {
            await app.DisposeAsync();
            throw;
        }

        _app = app;
        _port = port;
        Console.WriteLine($"Listening on {string.Join(", ", app.Urls)}");

    }

    private static int GetFreePort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    
    public async Task StartAsync()
    {

        Console.WriteLine($"DEBUG:: __app is null = {_app == null}");

        if (_app == null)
        {
            try
            {
                await StartOnPortAsync(5000);
            }
            catch (IOException)
            {
                var port = GetFreePort();
                await StartOnPortAsync(port);
            }
        }


        ushort ushortPort = checked((ushort)_port);

        if (ushortPort == 0)
            throw new InvalidOperationException("Server port is 0.");

        IPAddress? wifiIp = null;
        NetworkInterface? wifiInterface = null;

        foreach (var network in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (network.OperationalStatus != OperationalStatus.Up)
                continue;

            var properties = network.GetIPProperties();

            if (!properties.GatewayAddresses.Any())
                continue;

            var address = properties.UnicastAddresses.FirstOrDefault(
                a => a.Address.AddressFamily == AddressFamily.InterNetwork
                    && !IPAddress.IsLoopback(a.Address));

            if (address == null)
                continue;

            wifiIp = address.Address;
            wifiInterface = network;
            break;
        }

        if (wifiIp == null || wifiInterface == null)
            throw new InvalidOperationException(
                "No active network interface with IPv4 and gateway found.");

        Console.WriteLine($"Advertising at: {wifiIp}:{ushortPort}");

        var profile = new ServiceProfile(
            Environment.MachineName,
            "_filetransferer._tcp",
            ushortPort,
            new[] { wifiIp });

        var mdns = new MulticastService(
            interfaces => interfaces.Where(i => i.Id == wifiInterface.Id));

        _sd = new ServiceDiscovery(mdns);
        mdns.Start();
        _sd.Advertise(profile);
    }


    public async Task StopAsync()
    {
        _sd?.Dispose();
        _sd = null;

        if (_app != null)
        {
            await _app.StopAsync();
            await _app.DisposeAsync();
            _app = null;
        }
    }

    private static bool TryParseChunk(
        ReadOnlySpan<byte> frame,
        out string fileId,
        out long offset,
        out byte flags,
        out int dataStart)
    {
        fileId = "";
        offset = 0;
        flags = 0;
        dataStart = 0;


        if (frame.Length < 1)
        {
            return false;
        }
        var idLen = frame[0];
        if (idLen == 0 || frame.Length < 1 + idLen + 8 + 1)
        {
            return false;
        }

        var idStart = 1;
        var offsetStart = idStart + idLen;
        var flagsIndex = offsetStart + 8;
        dataStart = flagsIndex + 1;

        fileId = Encoding.UTF8.GetString(frame.Slice(idStart, idLen));
        offset = BinaryPrimitives.ReadInt64BigEndian(frame.Slice(offsetStart, 8));
        flags = frame[flagsIndex];

        return true;
    }

    private static Message MakeTransferComplete(string transferId, ConnectionState state)
    {

        var hashes = state.Files.Select(kv => new { file_id = kv.Key, sha256 = kv.Value.Sha256 }).ToList();

        var data = JsonSerializer.SerializeToElement(new
        {
            transfer_id = transferId,
            hash = hashes
        });

        Console.WriteLine($"{DateTime.Now:HH:mm:ss.fff} sending transfer_complete");

        return new Message("0", "transfer_complete", data);
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
        var data = JsonSerializer.SerializeToElement(new { server_name = "PC", protocol_version = 1 });
        var reply = new Message(msg.Id, "hello_ack", data);
        await SendAsync(ws, reply, options);
        return true;
    }

    //time to rock today lowk

    private static async Task HandleOfferFilesAsync(WebSocket ws, Message msg, JsonSerializerOptions options, ConnectionState state)
    {

        if (!state.HandshakeDone)
        {
            await SendAsync(ws, MakeError(msg.Id, "handshake_required", "send hello first"), options);
            return;
        }
        if (state.TransferId != null || state.AwaitingDecision)
        {
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
        if (ConfirmOfferAsync != null) {
            var info = new OfferInfo(
                state.DeviceName ?? "unknown device",
                msg.Data.GetProperty("files").GetArrayLength(),
                fileSize
            );
            
            var offerCts = new CancellationTokenSource();
            state.OfferCts = offerCts;
            state.AwaitingDecision = true;
            bool accepted;
            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(offerCts.Token);
            try
            {
                using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(60));
                timeoutCts.CancelAfter(TimeSpan.FromSeconds(60));
                accepted = await ConfirmOfferAsync(info, cts.Token);
            }
            catch (OperationCanceledException)
            {
                if (!offerCts.IsCancellationRequested)
                    await SendAsync(ws, MakeError(msg.Id, "offer_timeout", "nobody confirmed the transfer in time"), options);
                return;
            }
            finally
            {
                state.AwaitingDecision = false;
                state.OfferCts = null;
                offerCts.Dispose();
            }

            if (!accepted)
            {
                await SendAsync(ws, MakeError(msg.Id, "offer_rejected", "the transfer was declined"), options);
                return;
            }

        }
        var baseDir = Path.GetFullPath(Path.Combine(_appPath, state.DevicePath));

        var files = new Dictionary<string, FileTransferState>();

        var reserved = new List<string>();
        void ReleaseReserved()
        {
            foreach (var p in reserved)
                try { File.Delete(p); } catch { }
        }
        try
        {
            foreach (var file in msg.Data.GetProperty("files").EnumerateArray())
            {
                var relativePath = file.GetProperty("relative_path").GetString();
                var fileId = file.GetProperty("file_id").GetString();
                var size = file.GetProperty("size").GetInt64();

                string target;
                try
                {
                    target = FileNaming.ResolveSafePath(baseDir, relativePath!);
                }
                catch (InvalidOperationException)
                {
                    ReleaseReserved();
                    await SendAsync(ws, MakeError(msg.Id, "invalid_path", "path is outside the device directory"), options);
                    return;
                }
                reserved.Add(target);

                files[fileId] = new FileTransferState
                {
                    RelativePath = relativePath!,
                    Size = size,
                    TargetPath = target,
                    Requested = size == 0,
                };
            }
        }
        catch
        {
            ReleaseReserved();
            throw;
        }

        Directory.CreateDirectory(baseDir);
        state.Files = files;
        state.TransferId = "t_" + Guid.NewGuid().ToString("N");
        _active = new ActiveTransfer(ws, state, options);
        Report(state, TransferPhase.Started);

        await sendOfferAckAsync(ws, msg.Id, options, state);
        await CompleteIfDoneAsync(ws, options, state);

    }//WHY ISNT IT COMPILING

    private static async Task CompleteIfDoneAsync(WebSocket ws, JsonSerializerOptions options, ConnectionState state)
    {
        if (state.Files.Values.All(f => f.BytesRecived >= f.Size))
        {
            var transferId = state.TransferId!;
            Console.WriteLine($"{DateTime.Now:HH:mm:ss.fff}, last cunk of {transferId} received");
            Report(state, TransferPhase.Done);
            await SendAsync(ws, MakeTransferComplete(transferId, state), options);
            state.TransferId = null;
            _active = null;
            state.Files = new Dictionary<string, FileTransferState>();
        }
        else if (state.Files.Values.Where(f => f.Requested).All(f => f.BytesRecived >= f.Size))
        {
            var batch = SelectBatch(state);
            if (batch.Count > 0)
                await SendAsync(ws, MakeRequestFiles(state.TransferId!, batch), options);
        }
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

    private static long CalculateSize(JsonElement data)
    {
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


        if (!data.TryGetProperty("files", out var files)
            || files.ValueKind != JsonValueKind.Array)
        {
            return new ValidationError("invalid_data", "files must be an array");
        }

        if (files.GetArrayLength() > _maxFileCount)
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

            if (!file.TryGetProperty("relative_path", out var rp)
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

            if (!relativePathHash.Add(path))
            {
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

            if (!fileIdHash.Add(id))
            {
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

    private static readonly ConditionalWeakTable<WebSocket, SemaphoreSlim> SendLocks = new();

    static async Task SendAsync(WebSocket ws, Message msg, JsonSerializerOptions options)
    {
        var json = JsonSerializer.Serialize(msg, options);
        var bytes = Encoding.UTF8.GetBytes(json);

        var gate = SendLocks.GetValue(ws, _ => new SemaphoreSlim(1, 1));
        await gate.WaitAsync();
        try 
        {
            await ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
        }
        finally { gate.Release(); }
    }

    private static async Task CleanupTransferAsync(ConnectionState state)
    {
        if (state.TransferId != null) Report(state, TransferPhase.Cancelled);
        if (_active?.State == state) _active = null;
        state.CancelRequested = false;
        foreach (var f in state.Files.Values)
        {
            if (f.Stream != null) { await f.Stream.DisposeAsync(); f.Stream = null;}
            f.Hasher?.Dispose();
            f.Hasher = null;

            if (f.BytesRecived >= f.Size) continue;

            try { File.Delete(f.TargetPath + ".part"); } catch { }
            try { File.Delete(f.TargetPath); } catch { }
        }
        state.Files = new Dictionary<string, FileTransferState>();
        state.TransferId = null;
    }

    private static void CancelOffer(ConnectionState state)
    {
        try { state.OfferCts?.Cancel(); } catch (ObjectDisposedException) { }
    }

}

public record ValidationError(
    string Code,
    string Message
);

