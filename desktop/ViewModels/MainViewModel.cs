using System;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using desktop.Models;

namespace desktop.ViewModels;

public partial class MainViewModel : ObservableObject
{
    public TaskCompletionSource<bool>? _decision;

    [ObservableProperty] private bool _isOfferVisible;
    [ObservableProperty] private string _offerText = "";

    public Task<bool> ShowOfferAsync(OfferInfo info, CancellationToken ct)
    {
        var tcs = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        _decision = tcs;

        ct.Register(() =>
        {
            tcs.TrySetCanceled(ct);
            Dispatcher.UIThread.Post(() => IsOfferVisible = false);
        });

        Dispatcher.UIThread.Post(() =>
        {
            OfferText = $"{info.DeviceName} wants to send {info.FileCount} files ({FormatSize(info.TotalSize)})";
            IsOfferVisible = true;
        });

        return tcs.Task;
    }

    [RelayCommand]
    private void Accept()
    {
        _decision?.TrySetResult(true);
        IsOfferVisible = false;
    }

    [RelayCommand]
    private void Reject()
    {
        _decision?.TrySetResult(false);
        IsOfferVisible = false;
    }

    private static string FormatSize(long bytes)
    {
        string[] units = { "B", "KB", "MB", "GB"};
        double v = bytes;
        var i = 0;
        while (v >= 1024 && i < units.Length -1) { v/= 1024; i++;}
        return $"{v:0.#} {units[i]}";
    }

}