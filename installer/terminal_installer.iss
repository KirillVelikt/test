[Setup]
AppName=Yumani Пульт
AppVersion=1.0
DefaultDirName={pf}\YumaniPult
DefaultGroupName=Yumani Пульт
OutputDir=..\installer-output
OutputBaseFilename=TerminalControlSetup
Compression=lzma
SolidCompression=yes

[Files]
Source: "..\dist\TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Yumani Пульт"; Filename: "{app}\TerminalControl.exe"
Name: "{commondesktop}\Yumani Пульт"; Filename: "{app}\TerminalControl.exe"
