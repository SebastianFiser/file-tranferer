namespace desktop.Server;

public enum TransferPhase {
    Started,
    Progress,
    Done,
    Cancelled
}

public record ReceivedFile(string Name, long Size, string FullPath);

public record TransferStatus(
    TransferPhase Phase, string Device, long Bytes, long Total, int FilesDone, int FileCount,
    string? Folder = null, List<ReceivedFile>? Files = null
);