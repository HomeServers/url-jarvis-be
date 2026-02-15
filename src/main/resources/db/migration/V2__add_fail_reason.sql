-- 크롤링 실패 사유 저장 컬럼
ALTER TABLE urls ADD COLUMN fail_reason TEXT;
