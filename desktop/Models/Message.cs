using System.Text.Json;

namespace desktop.Server;

public record Message(
    string Id,
    string Type,
    JsonElement Data
);
