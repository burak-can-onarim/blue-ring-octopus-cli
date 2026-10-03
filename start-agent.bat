@echo off
title Code Analyzer AI
color 0A
cls

echo =========================================
echo      CODE ANALYZER AI - BASLATICI
echo =========================================
echo.
echo Kullanilabilir Yerel Modeller:
echo [1] qwen2.5-coder (Varsayilan - Hizli Kod)
echo [2] llama3.1 (Genel Amacli)
echo [3] codellama (Alternatif Kod Modeli)
echo.

set /p choice="Lutfen bir model secin (1/2/3 veya ozel model adi yazin) [Varsayilan: 1]: "

:: Varsayılan model tanımı
set AI_MODEL_NAME=qwen2.5-coder

:: Seçime göre ortam değişkenini güncelle
if "%choice%"=="2" set AI_MODEL_NAME=llama3.1
if "%choice%"=="3" set AI_MODEL_NAME=codellama
if not "%choice%"=="" if "%choice%" neq "1" if "%choice%" neq "2" if "%choice%" neq "3" set AI_MODEL_NAME=%choice%

echo.
echo %AI_MODEL_NAME% modeli ile ajan baslatiliyor, lutfen bekleyin...
echo =========================================

:: Ortam değişkenini Spring Boot'a aktararak jar'ı interaktif modda başlatıyoruz
java --enable-native-access=ALL-UNNAMED -jar target/code-analyzer-0.0.1-SNAPSHOT.jar