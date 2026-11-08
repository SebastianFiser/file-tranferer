using System.Text.Json;

namespace desktop.Server;

class ConnectionState
{
    public bool HandshakeDone { get; set; }
    public string? DevicePath { get; set; }
    public string? TransferId { get; set; }
    public string? DeviceName { get; set; }
    public bool AwaitingDecision { get; set; }
    public CancellationTokenSource? OfferCts { get; set; }
    public bool CancelRequested { get; set; }
    public long LastReportTick { get; set; }
    public string? RemoteAddress { get; set; }
    public bool LZ4 { get; set; }
    public Dictionary<string, FileTransferState> Files { get; set; } = new();
}
