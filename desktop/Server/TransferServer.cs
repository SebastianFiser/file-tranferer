using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
namespace desktop.Server;

public class TransferServer
{
    private WebApplication? _app;

    public async Task StartAsync()
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls("http://0.0.0.0:5000");
        _app = builder.Build();
        await _app.RunAsync();
    }

    public async Task StopAsync()
    {
        if (_app != null)
        {
            await _app.StopAsync();
            await _app.DisposeAsync();
            _app = null;
        }
    }
}
