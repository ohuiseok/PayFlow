@echo off
chcp 65001 > nul
echo ====================================
echo  Docker 서비스 상태 확인 중...
echo ====================================
docker compose ps user-service banking-service
echo.
echo ====================================
echo  user-service, banking-service 재빌드 시작...
echo ====================================
docker compose up -d --build user-service banking-service
echo.
echo ====================================
echo  재빌드 완료. 현재 상태:
echo ====================================
docker compose ps user-service banking-service
echo.
pause
