create table users (
  id bigint primary key,
  name text not null,
  access_token text not null,
  token_type text not null,
  expires_in int not null,
  refresh_token text not null,
  generatedAt timestamp not null
);
