@echo off
cd /d "%~dp0"
echo HINATA diagnostics: http://127.0.0.1:8765/
echo Keep this window open while using the page.
python -m http.server 8765 --bind 127.0.0.1
pause
