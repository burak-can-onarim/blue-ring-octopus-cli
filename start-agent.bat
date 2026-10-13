@echo off
chcp 65001 > nul

title Blue Ring Octopus CLI
color 0A
cls

echo =========================================
echo      BLUE RING OCTOPUS CLI - BASLATICI
echo =========================================
echo.
echo Olculmus yerel modeller (docs\PROMPTS.md, "Which model"):
echo [1] qwen2.5-coder  (Varsayilan - hizli, 8 GB ekran kartinda tam calisir)
echo [2] ornith:9b      (hizli kod incelemesi; urettigi kod sik derlenmez)
echo [3] gemma4:26b     (en iyi sonuc; yaklasik 18 GB bellek, daha yavas)
echo.

set /p choice="Lutfen bir model secin (1/2/3 veya ozel model adi yazin) [Varsayilan: 1]: "

:: Varsayılan model tanımı
set AI_MODEL_NAME=qwen2.5-coder

:: Seçime göre ortam değişkenini güncelle
if "%choice%"=="2" set AI_MODEL_NAME=ornith:9b
if "%choice%"=="3" set AI_MODEL_NAME=gemma4:26b
if not "%choice%"=="" if "%choice%" neq "1" if "%choice%" neq "2" if "%choice%" neq "3" set AI_MODEL_NAME=%choice%

echo.
echo %AI_MODEL_NAME% modeli ile ajan baslatiliyor, lutfen bekleyin...
echo =========================================

:: Ortam değişkenini Spring Boot'a aktararak jar'ı interaktif modda başlatıyoruz
start "" javaw -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar target\blue-ring-octopus-cli.jar ui