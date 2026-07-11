# ChinesePinyinIME PC Dictionary Manager

This local helper discovers phones running ChinesePinyinIME v0.02.0002 on the same LAN and serves the management page only at `127.0.0.1:37620`.

## Build and run

Requirements: JDK 17 or newer.

```powershell
.\build.ps1
.\dist\package\ChinesePinyinIME词库管理\ChinesePinyinIME词库管理.exe
```

The generated application image includes a private Java runtime. The browser opens automatically. On the phone, open ChinesePinyinIME and enable **电脑词库管理** before searching. Connection requests and destructive clears require phone notification confirmation.

The helper uses UDP port `37622` for discovery and the phone uses TCP port `37621` for dictionary operations. Windows Firewall may ask for permission the first time; allow private networks only.
