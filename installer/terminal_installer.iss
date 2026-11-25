; Inno Setup: установщик пульта Юмани
[Setup]
AppName=TerminalControl
AppVersion=1.0
AppPublisher=Yumani
DefaultDirName={autopf}\TerminalControl
DefaultGroupName=TerminalControl
OutputDir=.
OutputBaseFilename=TerminalControlSetup
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\TerminalControl.exe

[Files]
Source: "..\dist\TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\TerminalControl"; Filename: "{app}\TerminalControl.exe"
Name: "{autodesktop}\TerminalControl"; Filename: "{app}\TerminalControl.exe"

[Run]
Filename: "{app}\TerminalControl.exe"; Description: "Запустить пульт"; Flags: nowait postinstall skipifsilent
