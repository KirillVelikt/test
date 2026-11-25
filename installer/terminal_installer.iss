[Setup]
AppName=Yumani Terminal Control
AppVersion=3.0
DefaultDirName={pf}\YumaniTerminal
DefaultGroupName=Yumani Terminal
OutputBaseFilename=TerminalControlSetup
Compression=lzma2
SolidCompression=yes

[Files]
Source: "TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{commondesktop}\Пульт Yumani"; Filename: "{app}\TerminalControl.exe"
Name: "{group}\Пульт Yumani"; Filename: "{app}\TerminalControl.exe"

[Run]
Filename: "{app}\TerminalControl.exe"; Description: "Запустить пульт"; Flags: nowait postinstall
