create table users (
  id bigint primary key,
  name varchar(100) not null,
  access_token varchar(100) not null,     -- TODO exact length
  token_type varchar(20) not null,
  expires_in bigint not null,
  refresh_token varchar(100) not null,
  generatedAt timestamp not null
);
