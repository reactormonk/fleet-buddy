alter table memberstatus
drop constraint memberstatus_shipid_fkey,
drop constraint memberstatus_solarsystemid_fkey,
drop constraint memberstatus_stationid_fkey,
add foreign key (shipId) references "invTypes" ("typeID"),
add foreign key (solarSystemId) references "mapSolarSystems" ("solarSystemID"),
add foreign key (stationId) references "staStations" ("stationID");

drop table ships;
drop table solarsystems;
drop table stations;
