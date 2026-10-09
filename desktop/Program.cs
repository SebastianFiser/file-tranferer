using Avalonia;
using System;
using System.Linq;
using System.Threading.Tasks;
using desktop.Server;

namespace desktop;

sealed class Program
{
    // Initialization code. Don't use any Avalonia, third-party APIs or any
    // SynchronizationContext-reliant code before AppMain is called: things aren't initialized
    // yet and stuff might break.
    [STAThread]
    public static async Task Main(string[] args)
    {
        if(args.Contains("--headless"))
        {
            var server = new TransferServer();
            await server.StartAsync();
            Console.WriteLine("Server běží (headless).");
            await Task.Delay(Timeout.Infinite);
            return;
        }
        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }
    // Avalonia configuration, don't remove; also used by visual designer.
    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<App>()
            .UsePlatformDetect()
#if DEBUG
            .WithDeveloperTools()
#endif
            .WithInterFont()
            .LogToTrace();
}
