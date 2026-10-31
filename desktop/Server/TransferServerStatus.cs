namespace desktop.Server;

public enum TransferPhase { Started, Progress, Done, Cancelled }
public record TransferStatus(TransferPhase Phase, string Device, long Bytes, long Total, int FilesDone, int FileCount);