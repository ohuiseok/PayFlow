-- [H-1] settlement-service 초기 스키마
-- 현재 settlement-service는 확장 예정 서비스로 테이블이 없다.
-- Flyway가 빈 마이그레이션 파일도 버전 기록을 남기므로, 이후 V2 이상에서 테이블을 추가한다.
-- 빈 마이그레이션 파일은 실행해도 DB에 변화가 없으며 flyway_schema_history에만 기록된다.
SELECT 1;
