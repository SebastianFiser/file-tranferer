namespace desktop.Server;
using System.Security.Cryptography;

public class FileTransferState
{
    public string RelativePath { get; init; }
    public long Size { get; init; }
    public long Recive { get; set; }
    public bool Requested { get; set; }
    public string TargetPath  { get; init; } = "";
    public long BytesRecived { get; set; }
    public FileStream? Stream { get; set; }
    public IncrementalHash? Hasher { get; set; }
    public string? Sha256 { get; set; }
}
