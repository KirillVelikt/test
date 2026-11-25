[Setup]
AppName=Terminal Control
AppVersion=2.0
AppPublisher=Yumani
DefaultDirName={pf}\TerminalControl
DefaultGroupName=Terminal Control
OutputBaseFilename=TerminalControlSetup
Compression=lzma2
SolidCompression=yes

[Files]
Source: "dist\TerminalControl.exe"; DestDir: "{app}"; Flags: ignoreskip

[Icons]
Name: "{commondesktop}\Terminal Control"; Filename: "{app}\TerminalControl.exe"
Name: "{group}\Terminal Control"; Filename: "{app}\TerminalControl.exe"

[Run]
Filename: "{app}\TerminalControl.exe"; Description: "Запустить пульт"; Flags: postinstall nowait
