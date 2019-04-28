CREATE KEYSPACE IF NOT EXISTS inventory_balance WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '3'}  AND durable_writes = true;

CREATE TABLE IF NOT EXISTS inventory_balance.current_item_balance_by_base_upc (
    facility_id uuid,
    base_upc text,
    location text,
    current_balance double,
    PRIMARY KEY ((facility_id, base_upc), location)
);

CREATE TABLE IF NOT EXISTS inventory_balance.facility_detail_by_facility_id (
    facility_id uuid PRIMARY KEY,
    division text,
    facility_name text,
    notes text,
    store text,
    updated_date timestamp
);

use inventory_balance;

insert into facility_detail_by_facility_id (facility_id, division, store) values (acf2e495-c2ab-4771-a4e0-a661cbcd3cf3, 'dallas', 'store_1');
insert into facility_detail_by_facility_id (facility_id, division, store) values (ae3df9f0-1d65-4146-bb32-8f59a1d9e5a1, 'dallas', 'store_2');

insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (acf2e495-c2ab-4771-a4e0-a661cbcd3cf3, '9321024d', 'a01');
insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (acf2e495-c2ab-4771-a4e0-a661cbcd3cf3, '9321024d', 'b02');
insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (acf2e495-c2ab-4771-a4e0-a661cbcd3cf3, '9321024d', 'e05');

insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (ae3df9f0-1d65-4146-bb32-8f59a1d9e5a1, '3433524e', 'b02');
insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (ae3df9f0-1d65-4146-bb32-8f59a1d9e5a1, '3433524e', 'e05');
insert into current_item_balance_by_base_upc (facility_id, base_upc, location) values (ae3df9f0-1d65-4146-bb32-8f59a1d9e5a1, '3433524e', 'l11');