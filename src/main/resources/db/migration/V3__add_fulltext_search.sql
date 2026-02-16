ㅕㅕ져?
   -- tsvector 컬럼 추가 (키워드 검색용)
ALTER TABLE url_chunks ADD COLUMN content_tsv tsvector;

-- 기존 데이터 백필
UPDATE url_chunks SET content_tsv = to_tsvector('simple', content);

-- GIN 인덱스 생성
CREATE INDEX idx_url_chunks_tsv ON url_chunks USING gin(content_tsv);

-- INSERT/UPDATE 시 content_tsv 자동 갱신 트리거
CREATE OR REPLACE FUNCTION url_chunks_tsv_trigger() RETURNS trigger AS $$
BEGIN
  NEW.content_tsv := to_tsvector('simple', NEW.content);
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_url_chunks_tsv
  BEFORE INSERT OR UPDATE OF content ON url_chunks
  FOR EACH ROW EXECUTE FUNCTION url_chunks_tsv_trigger();
