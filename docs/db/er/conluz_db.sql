CREATE TABLE "config" (
  "id" uuid PRIMARY KEY NOT NULL,
  "default_admin_user_initialized" boolean NOT NULL DEFAULT false
);

CREATE TABLE "databasechangelog" (
  "id" "character varying(255)" NOT NULL,
  "author" "character varying(255)" NOT NULL,
  "filename" "character varying(255)" NOT NULL,
  "dateexecuted" timestamp NOT NULL,
  "orderexecuted" integer NOT NULL,
  "exectype" "character varying(10)" NOT NULL,
  "md5sum" "character varying(35)",
  "description" "character varying(255)",
  "comments" "character varying(255)",
  "tag" "character varying(255)",
  "liquibase" "character varying(20)",
  "contexts" "character varying(255)",
  "labels" "character varying(255)",
  "deployment_id" "character varying(10)"
);

CREATE TABLE "databasechangeloglock" (
  "id" integer PRIMARY KEY NOT NULL,
  "locked" boolean NOT NULL,
  "lockgranted" timestamp,
  "lockedby" "character varying(255)"
);

CREATE TABLE "datadis_config" (
  "id" uuid PRIMARY KEY NOT NULL,
  "username" text,
  "password" text
);

CREATE TABLE "huawei_config" (
  "id" uuid PRIMARY KEY NOT NULL,
  "username" text,
  "password" text
);

CREATE TABLE "users" (
  "id" uuid PRIMARY KEY NOT NULL,
  "personal_id" "character varying(250)" NOT NULL,
  "number" integer NOT NULL,
  "password" "character varying(250)" NOT NULL,
  "full_name" "character varying(250)" NOT NULL,
  "address" "character varying(250)",
  "phone_number" "character varying(250)",
  "email" "character varying(250)" NOT NULL,
  "role" "character varying(250)" NOT NULL,
  "enabled" boolean NOT NULL
);

CREATE TABLE "plants" (
  "id" uuid PRIMARY KEY NOT NULL,
  "name" text NOT NULL,
  "code" text NOT NULL,
  "address" text NOT NULL,
  "description" text,
  "inverter_provider" text NOT NULL,
  "total_power" doubleprecision NOT NULL,
  "connection_date" date,
  "supply_id" uuid
);

CREATE TABLE "supplies" (
  "id" uuid PRIMARY KEY NOT NULL,
  "code" "character varying(250)" NOT NULL,
  "user_id" uuid,
  "name" "character varying(250)",
  "address" "character varying(250)" NOT NULL,
  "enabled" boolean NOT NULL,
  "is_third_party" bool
);

CREATE TABLE "supplies_shares" (
  "id" uuid PRIMARY KEY NOT NULL,
  "supply_id" uuid NOT NULL,
  "valid_date_from" date,
  "valid_date_to" date,
  "partition_coefficient" doubleprecision NOT NULL
);

CREATE TABLE "supplies_shelly" (
  "id" uuid PRIMARY KEY NOT NULL,
  "supply_id" uuid NOT NULL,
  "code" "character varying(250)" NOT NULL,
  "mac" "character varying(250)",
  "mqtt_prefix" "character varying(250)",
  "installation_date" date,
  "enabled" boolean NOT NULL
);

CREATE TABLE "supplies_datadis" (
  "id" uuid PRIMARY KEY NOT NULL,
  "supply_id" uuid NOT NULL,
  "valid_date_from" date,
  "valid_date_to" date,
  "distributor" "character varying(250)",
  "distributor_code" "character varying(250)",
  "point_type" integer
);

ALTER TABLE "plants" ADD CONSTRAINT "plants_supply_id_fk" FOREIGN KEY ("supply_id") REFERENCES "supplies" ("id");

ALTER TABLE "supplies" ADD CONSTRAINT "supplies_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "users" ("id");

ALTER TABLE "supplies_shares" ADD CONSTRAINT "supplies_shares_supply_id_fk" FOREIGN KEY ("supply_id") REFERENCES "supplies" ("id");

ALTER TABLE "supplies_shelly" ADD CONSTRAINT "supplies_shelly_supply_id_fk" FOREIGN KEY ("supply_id") REFERENCES "supplies" ("id");

ALTER TABLE "supplies_datadis" ADD CONSTRAINT "supplies_datadis_supply_id_fk" FOREIGN KEY ("supply_id") REFERENCES "supplies" ("id");
