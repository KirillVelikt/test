[Setup]
AppName=TerminalControl
AppVersion=1.0
AppPublisher=Yumani
DefaultDirName={autopf}\TerminalControl
DefaultGroupName=TerminalControl
UninstallDisplayName=TerminalControl
OutputBaseFilename=TerminalControlSetup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Files]
Source: "..\pc-control\dist\TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\pc-control\README.md"; DestDir: "{app}"; Flags: ignoreversion recursesubdirectories createallsubdirs

[Icons]
Name: "{group}\TerminalControl"; Filename: "{app}\TerminalControl.exe"
Name: "{autodesktop}\TerminalControl"; Filename: "{app}\TerminalControl.exe"

[Run]
Filename: "{app}\TerminalControl.exe"; Description: "Запустить TerminalControl"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
