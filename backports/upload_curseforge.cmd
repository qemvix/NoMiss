@echo off
setlocal
 title Upload NoMiss to CurseForge
 echo NoMiss 1.2 - CurseForge project 1756374
 echo.
 echo Create an AUTHOR UPLOAD TOKEN at:
 echo https://authors.curseforge.com/#/api-tokens
 echo A Modrinth token or CFCore developer key will not work here.
 echo Paste into the masked window with Ctrl+V and click Continue.
 echo.
 py -3 "%~dp0publish_curseforge.py" --project-id 1756374 --publish --token-dialog
 if errorlevel 1 (
   echo.
   echo SUBMISSION STOPPED. Read the error dialog or saved error file.
 ) else (
   echo.
   echo SUBMISSIONS ACCEPTED. CurseForge moderation may still be pending.
 )
 echo.
 pause
endlocal
