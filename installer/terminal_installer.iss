; Inno Setup: установщик Юмани Пульта
[Setup]
AppName=Юмани Пульт
AppVersion=2.0
AppPublisher=Yumani
DefaultDirName={autopf}\YumaniPult
DefaultGroupName=Юмани Пульт
OutputBaseFilename=TerminalControlSetup
OutputDir=Output
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin

[Files]
Source: "..\dist\TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Юмани Пульт"; Filename: "{app}\TerminalControl.exe"
Name: "{autodesktop}\Юмани Пульт"; Filename: "{app}\TerminalControl.exe"

[Run]
Filename: "{app}\TerminalControl.exe"; Description: "Запустить Юмани Пульт"; Flags: nowait postinstall skipifsilent
