using System.Text;


namespace desktop.Models;

public static class FileNaming
{
    private const int MaxLenght = 200;
    public static readonly char[] Forbidden=
        { '\\', '/', ':', '*', '?', '"', '<', '>', '|' };
    private static readonly HashSet<string> Reserved = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COPM6", "COM7", "COM8", "COM9",
        "LPT1", "LTP2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    public static string SanitizeFileName(string? input)
        => SanitizeSegment(Path.GetFileName(input ?? "").Replace('\\', '/'));


    public static string SanitizeSegment(string name)
    {
        var sb = new StringBuilder(name.Length);
        foreach (var c in name)
            sb.Append(c < 32 || Array.IndexOf(Forbidden, c) >= 0 ? '_' : c);

        name = sb.ToString().Trim(' ', '.');
        if (name.Length == 0) return "file";

        var dot = name.LastIndexOf('.');
        var head = dot < 0 ? name : name[..dot];
        if (Reserved.Contains(head)) name = "_" + name;

        var ext = Path.GetExtension(name);
        if (ext.Length > 20) ext = ext[..20];
        var stem = Path.GetFileNameWithoutExtension(name);
        var maxStem = MaxLenght - ext.Length;
        if (stem.Length > maxStem) stem = stem[..maxStem];
        return stem + ext;
    }

    public static string ResolveSafePath(string dir, string requestedName)
    {
        var root = Path.TrimEndingDirectorySeparator(Path.GetFullPath(dir));
        var relativePath = requestedName;

        var parts = (relativePath ?? "").Replace('\\', '/')
            .Split('/', StringSplitOptions.RemoveEmptyEntries)
            .Where(p => p != "." && p != "..")
            .Select(SanitizeSegment)
            .ToList();
        if (parts.Count == 0) parts.Add("file");

        var fileName = parts[^1];
        var dirPath = Path.GetFullPath(
            Path.Combine(new[] {root}.Concat(parts.Take(parts.Count - 1)).ToArray()));

        if (dirPath != root && !dirPath.StartsWith(root + Path.DirectorySeparatorChar, StringComparison.Ordinal))
            throw new InvalidOperationException("Path escapes target directory.");

        Directory.CreateDirectory(dirPath);

        var stem = Path.GetFileNameWithoutExtension(fileName);
        var ext = Path.GetExtension(fileName);

        for ( var n = 0; n < 10_000; n++)
        {
            var candidate = n == 0 ? fileName : $"{stem} ({n}){ext}";
            var full = Path.GetFullPath(Path.Combine(root, candidate));


            try
            {
                using var fs = new FileStream(full, FileMode.CreateNew, FileAccess.Write);
                return full;
            }
            catch (IOException) when (File.Exists(full)) {}
        }
        throw new IOException("could not find a free fileName");
    }
}