create table fleetstatus (
  id bigint not null,
  recorded timestamp not null,
  isFreeMove boolean not null,
  isRegistered boolean not null,
  isVoiceEnabled boolean not null,
  motd text not null,
  serial_id bigserial primary key
);

create index if not exists fleetstatus_id on fleetstatus (id);
create index if not exists fleetstatus_recorded on fleetstatus (recorded);

create table characters (
  id bigint primary key,
  name text not null
);

create table stations (
  id bigint primary key,
  name text not null
);

create table solarsystems (
  id bigint primary key,
  name text not null
);

create table ships (
  id bigint primary key,
  name text not null
);

create table memberstatus (
  parentstatus bigint references fleetstatus not null,
  boosterId smallint not null,
  joinTime timestamp not null,
  roleId smallint not null,
  characterId bigint references characters not null,
  shipId bigint not null references ships not null,
  solarSystemId bigint references solarsystems not null,
  stationId bigint references stations,
  squadId bigint not null,
  takesFleetWarp boolean not null,
  wingId bigint not null
);

create table wingstatus (
  parentstatus bigint references fleetstatus not null,
  id bigint not null,
  name text not null,
  unique (parentstatus, id)
);

create table squadstatus (
  parentstatus bigint not null,
  wingid bigint not null,
  id bigint not null,
  name text not null,
  foreign key (parentstatus, wingid) references wingstatus (parentstatus, id)
);

create unique index if not exists characters_name on characters (name);

update characters
set
  id = users.id,
  name = users.name
from (select id, name from users) as users;

alter table users
drop name,
add constraint characterfk foreign key (id) references characters (id);