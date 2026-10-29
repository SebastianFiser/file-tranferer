using System.Text.Json;

namespace desktop.Server;

class ConnectionState
{
    public bool HandshakeDone { get; set; }
    public string? DevicePath { get; set; }
    public string? TransferId { get; set; }
    public string? DeviceName { get; set; }
    public bool AwaitingDecision { get; set; }
    public Dictionary<string, FileTransferState> Files { get; set; } = new();
}
