using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using desktop.ViewModels;
using desktop.Views;
using System.Threading.Tasks;
using desktop.Server;

namespace desktop;

public partial class App : Application
{

    private TransferServer? _server;

    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            desktop.MainWindow = new MainWindow
            {
                DataContext = new MainViewModel(),
            };

            _server = new TransferServer();
            _ = StartServerAsync();

            desktop.Exit += (s, e) =>
            {
                _server?.StopAsync().GetAwaiter().GetResult();
            };
        }

        base.OnFrameworkInitializationCompleted();
    }

    private async Task StartServerAsync()
    {
        try
        {
            await _server!.StartAsync();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Server failed to start: {ex.Message}");
        }
    }
}
