using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Avalonia.Threading; 
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

            Console.CancelKeyPress += (s, e) =>
            {
                e.Cancel = true;
                Dispatcher.UIThread.Post(() => desktop.Shutdown());
            };

            var vm = new MainViewModel();
            desktop.MainWindow = new MainWindow
            {
                DataContext = vm,
            };
            TransferServer.ConfirmOfferAsync = vm.ShowOfferAsync;
            _server = new TransferServer();
            _ = StartServerAsync();

            desktop.Exit += (s, e) =>
            {
                if (_server != null)
                    Task.Run(() => _server.StopAsync()).Wait(TimeSpan.FromSeconds(5));
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
