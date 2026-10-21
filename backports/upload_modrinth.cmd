@echo off
setlocal
 title Upload NoMiss to Modrinth
 echo NoMiss 1.2 - upload verified editions to your Modrinth project
 echo.
 echo Create a Modrinth token with Create Versions permission at:
 echo https://modrinth.com/settings/pats
 echo Allow private project/version read access if available.
 echo A token window will open. Paste with Ctrl+V, then click Continue.
 echo Existing identical downloads will be skipped.
 echo.
 py -3 "%~dp0publish_modrinth.py" --project nomiss --publish --token-dialog
 if errorlevel 1 (
   echo.
   echo Upload stopped. Read the error above; rerun this launcher to resume.
 )
 echo.
 pause
endlocal
