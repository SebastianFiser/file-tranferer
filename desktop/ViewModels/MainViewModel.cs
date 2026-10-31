using System;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using desktop.Models;
using desktop.Server;

namespace desktop.ViewModels;

public partial class MainViewModel : ObservableObject
{
    public TaskCompletionSource<bool>? _decision;

    [ObservableProperty] 
    [NotifyPropertyChangedFor(nameof(IsIdleVisible))]
    private bool _isOfferVisible;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsIdleVisible))]
    private bool _isTransferInProgress;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsIdleVisible))]
    private bool _isTransferVisible;

    [ObservableProperty] private string _statusText = "Waiting for device";
    [ObservableProperty] private string _transferText = "";
    [ObservableProperty] private double _transferProgress;

    [ObservableProperty] private string _offerText = "";

    private long _lastTick;
    private long _lastBytes;
    private long _startTick;
    private double _speed;

    public bool IsIdleVisible => !IsOfferVisible && !IsTransferVisible;

    public MainViewModel()
    {
        TransferServer.StatusChanged += OnStatus;
    }

    private void OnStatus(TransferStatus s)
    {
    
        string? detail = null;

        if (s.Phase == TransferPhase.Started)
        {
            _startTick = _lastTick = Environment.TickCount64;
            _lastBytes = 0;
            _speed = 0;
        }
        else if (s.Phase == TransferPhase.Progress)
        {
            var now = Environment.TickCount64;
            var dt = (now - _lastTick) / 1000.0;
            if (dt > 0)
            {
                var instant = (s.Bytes - _lastBytes) / dt;
                _speed = _speed == 0 ? instant : 0.1 * instant + 0.9 * _speed; 
            }
            _lastTick = now;
            _lastBytes = s.Bytes;

            if (_speed > 0)
            {
                detail = $"{FormatSize((long)_speed)}/s";
                if (now - _startTick > 2000)
                    detail += $", {FormatEta((long)((s.Total - s.Bytes) / _speed))} left";
            }
        }

        Dispatcher.UIThread.Post(() =>
        {
            switch (s.Phase)
            {
                case TransferPhase.Started:
                case TransferPhase.Progress:
                    IsTransferVisible = true;
                    TransferProgress = s.Total > 0 ? 100.0 * s.Bytes / s.Total : 0;
                    TransferText = $"{s.Device}: {s.FilesDone}/{s.FileCount} files ({FormatSize(s.Bytes)} of {FormatSize(s.Total)})"
                                   + (detail != null ? $"\n{detail}" : "");
                    break;
                case TransferPhase.Done:
                    IsTransferVisible = false;
                    StatusText = $"Done {s.FileCount} files ({FormatSize(s.Total)})";
                    break;
                case TransferPhase.Cancelled:
                    IsTransferVisible = false;
                    StatusText = $"Transfer cancelled";
                    break;
            }
        });
    }

    [RelayCommand]
    private Task CancelTransfer() => TransferServer.CancelActiveTransferAsync();

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

    private static string FormatEta(long seconds)
    { 
        var t = TimeSpan.FromSeconds(seconds);
        return t.TotalHours >= 1? t.ToString(@"h\:mm\:ss") : t.ToString(@"m\:ss");
    }

}