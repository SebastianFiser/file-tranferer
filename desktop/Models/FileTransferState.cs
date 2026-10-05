namespace desktop.Server;

public class FileTransferState
{
    public string RelativePath { get; init; }
    public long Size { get; init; }
    public long Recive { get; set; }
    public bool Requested { get; set; }
}
