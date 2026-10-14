-- ShedLock 잠금 테이블(SchedulerLockConfig). 엔티티가 없어 Hibernate 가 만들지 않으므로 테스트(H2)는 여기서 만든다.
-- 개발·운영은 DBA 가 같은 DDL 로 만든다(modu_infra/data/mysql/schema).
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
