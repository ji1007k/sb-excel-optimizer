CREATE TABLE IF NOT EXISTS test_data
(
    id          bigserial
        primary key,
    category    varchar(255)   not null,
    created_at  timestamp(6),
    description varchar(1000),
    name        varchar(255)   not null,
    value       numeric(38, 2) not null
);

ALTER TABLE test_data
    OWNER TO ec_user;

