--
-- PostgreSQL database dump
--

\restrict vgTiQudK2UumbgYPtF8oIvEYhwoj8EyvaWRhgUUoD5Q67Ap6wmLyYdOlmZp5ZIe

-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 18.6 (Ubuntu 18.6-1.pgdg26.04+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: blacklisted_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blacklisted_token (
                                          jti character varying(64) NOT NULL,
                                          expiration timestamp without time zone NOT NULL,
                                          revoked_at timestamp without time zone NOT NULL
);


--
-- Name: communities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.communities (
                                    id uuid NOT NULL,
                                    name text NOT NULL,
                                    code text NOT NULL,
                                    legal_id text,
                                    address text,
                                    enabled boolean DEFAULT true NOT NULL
);


--
-- Name: community_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.community_memberships (
                                              id uuid NOT NULL,
                                              user_id uuid NOT NULL,
                                              community_id uuid NOT NULL,
                                              role text NOT NULL,
                                              enabled boolean DEFAULT true NOT NULL,
                                              investment_eur numeric(12,2),
                                              full_name character varying(250) NOT NULL,
                                              member_number integer,
                                              contact_email character varying(250),
                                              address character varying(250),
                                              phone_number character varying(250),
                                              accepted_at timestamp with time zone,
                                              invited_by uuid,
                                              invited_at timestamp with time zone
);


--
-- Name: COLUMN community_memberships.investment_eur; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.investment_eur IS 'The member''s initial contribution to the community, in euros. Only the current value is stored, never a history of changes. NULL means no investment has been recorded for this membership, which is not the same as an investment of zero.';


--
-- Name: COLUMN community_memberships.full_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.full_name IS 'The member''s full name, as the community recorded it.';


--
-- Name: COLUMN community_memberships.member_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.member_number IS 'The member''s number in the community. NULL means the community has not recorded one.';


--
-- Name: COLUMN community_memberships.contact_email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.contact_email IS 'The email the community recorded to contact the member. NULL means the community has not recorded one.';


--
-- Name: COLUMN community_memberships.address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.address IS 'The member''s address, as the community recorded it. NULL means the community has not recorded one.';


--
-- Name: COLUMN community_memberships.phone_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.phone_number IS 'The member''s phone number, as the community recorded it. NULL means the community has not recorded one.';


--
-- Name: COLUMN community_memberships.accepted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.accepted_at IS 'When the membership was accepted. NULL means the membership is pending: it has not been accepted.';


--
-- Name: COLUMN community_memberships.invited_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.invited_by IS 'The user who invited this membership. NULL means no inviting user is recorded: the membership was not created by an invitation, or the inviting user has been deleted.';


--
-- Name: COLUMN community_memberships.invited_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.community_memberships.invited_at IS 'When the membership was invited. NULL means it was not created by an invitation.';


--
-- Name: config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.config (
                               id uuid NOT NULL,
                               default_admin_user_initialized boolean DEFAULT false NOT NULL
);


--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangelog (
                                          id character varying(255) NOT NULL,
                                          author character varying(255) NOT NULL,
                                          filename character varying(255) NOT NULL,
                                          dateexecuted timestamp without time zone NOT NULL,
                                          orderexecuted integer NOT NULL,
                                          exectype character varying(10) NOT NULL,
                                          md5sum character varying(35),
                                          description character varying(255),
                                          comments character varying(255),
                                          tag character varying(255),
                                          liquibase character varying(20),
                                          contexts character varying(255),
                                          labels character varying(255),
                                          deployment_id character varying(10)
);


--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangeloglock (
                                              id integer NOT NULL,
                                              locked boolean NOT NULL,
                                              lockgranted timestamp without time zone,
                                              lockedby character varying(255)
);


--
-- Name: datadis_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.datadis_config (
                                       id uuid NOT NULL,
                                       username text,
                                       password text,
                                       base_url text DEFAULT 'http://localhost:5555'::text NOT NULL,
                                       enabled boolean DEFAULT false NOT NULL,
                                       community_id uuid NOT NULL
);


--
-- Name: huawei_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.huawei_config (
                                      id uuid NOT NULL,
                                      username text,
                                      password text,
                                      base_url text DEFAULT 'http://localhost:8000/thirdData'::text NOT NULL,
                                      enabled boolean DEFAULT false NOT NULL,
                                      plant_id uuid
);


--
-- Name: one_time_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.one_time_token (
                                       id uuid NOT NULL,
                                       user_id uuid NOT NULL,
                                       purpose character varying(32) NOT NULL,
                                       token_hash character varying(64) NOT NULL,
                                       created_at timestamp with time zone NOT NULL,
                                       expires_at timestamp with time zone NOT NULL,
                                       used_at timestamp with time zone,
                                       revoked_at timestamp with time zone,
                                       membership_id uuid
);


--
-- Name: TABLE one_time_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.one_time_token IS 'One-time tokens issued to a user for one purpose. Only the hash of each token is stored.';


--
-- Name: COLUMN one_time_token.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.user_id IS 'The user the token was issued to.';


--
-- Name: COLUMN one_time_token.purpose; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.purpose IS 'What the token was issued for.';


--
-- Name: COLUMN one_time_token.token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.token_hash IS 'Lowercase hex SHA-256 hash of the token.';


--
-- Name: COLUMN one_time_token.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.created_at IS 'When the token was issued.';


--
-- Name: COLUMN one_time_token.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.expires_at IS 'When the token stops being valid.';


--
-- Name: COLUMN one_time_token.used_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.used_at IS 'When the token was used. NULL if it has not been used.';


--
-- Name: COLUMN one_time_token.revoked_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.revoked_at IS 'When the token was revoked. NULL if it has not been revoked.';


--
-- Name: COLUMN one_time_token.membership_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.one_time_token.membership_id IS 'The membership the token was issued for. NULL means the token is bound only to its user.';


--
-- Name: plants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plants (
                               id uuid NOT NULL,
                               name text NOT NULL,
                               provider_code text NOT NULL,
                               address text NOT NULL,
                               description text,
                               inverter_provider text NOT NULL,
                               total_power double precision NOT NULL,
                               connection_date date,
                               supply_id uuid NOT NULL,
                               regulatory_code text
);


--
-- Name: COLUMN plants.regulatory_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plants.regulatory_code IS 'The identifier assigned by the regulator. In Spain this is the CAU (Codigo de Autoconsumo). It is not the provider''s station code (provider_code) and not a CUPS.';


--
-- Name: sharing_agreement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sharing_agreement (
                                          id uuid NOT NULL,
                                          plant_id uuid NOT NULL,
                                          name text NOT NULL,
                                          notes text,
                                          status text NOT NULL,
                                          installed_power_kw numeric,
                                          created_at timestamp with time zone NOT NULL,
                                          created_by uuid,
                                          updated_at timestamp with time zone,
                                          updated_by uuid,
                                          CONSTRAINT sharing_agreement_status_check CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'SUPERSEDED'::text])))
);


--
-- Name: TABLE sharing_agreement; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sharing_agreement IS 'An authoring event over the coefficient timeline in supply_partition_coefficient: it groups the coefficients that were set together, against a given plant, and keeps the evidence they were derived from.';


--
-- Name: COLUMN sharing_agreement.plant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.plant_id IS 'The plant this agreement distributes production from.';


--
-- Name: COLUMN sharing_agreement.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.name IS 'Human-readable label for the agreement.';


--
-- Name: COLUMN sharing_agreement.notes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.notes IS 'Free-text notes about the agreement. Optional.';


--
-- Name: COLUMN sharing_agreement.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.status IS 'DRAFT, PUBLISHED or SUPERSEDED. TEXT + CHECK rather than a native PostgreSQL ENUM: a native enum cannot have a value removed, and extending it (ALTER TYPE ... ADD VALUE) has historically been restricted inside transactions -- a poor fit for Liquibase and for a value set that will change. TEXT + CHECK is trivially alterable and reads identically.';


--
-- Name: COLUMN sharing_agreement.installed_power_kw; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.installed_power_kw IS 'A deliberate snapshot of the plant''s installed power at authoring time, not a join to plants.total_power: the plant''s installed power today is not the power the agreement was authored against.';


--
-- Name: COLUMN sharing_agreement.created_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.created_by IS 'NULL means the agreement was created by the system (a migration), not by a person -- there is no honest user id to record. Phase 2d creates exactly one such synthetic agreement to carry coefficients that predate this model. Do not backfill this with a synthetic ''system'' user row: it could not authenticate, it would appear in member listings, and someone would eventually delete it.';


--
-- Name: COLUMN sharing_agreement.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.updated_at IS 'When the agreement was last edited. NULL means it has never been edited since creation.';


--
-- Name: COLUMN sharing_agreement.updated_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement.updated_by IS 'The user who last edited the agreement. NULL means it has never been edited since creation.';


--
-- Name: sharing_agreement_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sharing_agreement_file (
                                               id uuid NOT NULL,
                                               sharing_agreement_id uuid NOT NULL,
                                               filename text NOT NULL,
                                               content bytea NOT NULL,
                                               content_hash text NOT NULL,
                                               uploaded_at timestamp with time zone NOT NULL,
                                               uploaded_by uuid NOT NULL
);


--
-- Name: TABLE sharing_agreement_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sharing_agreement_file IS 'Evidence file(s) uploaded for a sharing_agreement. Zero files is a valid state; when more than one exists, the most recently uploaded is authoritative.';


--
-- Name: COLUMN sharing_agreement_file.sharing_agreement_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement_file.sharing_agreement_id IS 'The agreement this file is evidence for.';


--
-- Name: COLUMN sharing_agreement_file.filename; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement_file.filename IS 'Original filename as uploaded.';


--
-- Name: COLUMN sharing_agreement_file.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement_file.content IS 'The raw file bytes. Stored verbatim; this phase does not parse the distributor file.';


--
-- Name: COLUMN sharing_agreement_file.content_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement_file.content_hash IS 'Hash of content, to detect re-uploads of the same file.';


--
-- Name: COLUMN sharing_agreement_file.uploaded_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sharing_agreement_file.uploaded_by IS 'The user who uploaded this file. Unlike sharing_agreement.created_by, this is never NULL: every file was uploaded by a real person, even when the owning agreement was created by the system.';


--
-- Name: shelly_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shelly_config (
                                      id uuid DEFAULT gen_random_uuid() NOT NULL,
                                      enabled boolean DEFAULT false NOT NULL,
                                      community_id uuid NOT NULL
);


--
-- Name: supplies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplies (
                                 id uuid NOT NULL,
                                 code character varying(250) NOT NULL,
                                 user_id uuid,
                                 name character varying(250),
                                 address character varying(250) NOT NULL,
                                 enabled boolean NOT NULL,
                                 address_ref character varying(250),
                                 community_id uuid NOT NULL
);


--
-- Name: supplies_contract; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplies_contract (
                                          supply_id uuid NOT NULL,
                                          valid_date_from date
);


--
-- Name: supplies_distributor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplies_distributor (
                                             supply_id uuid NOT NULL,
                                             name character varying(250),
                                             code character varying(250),
                                             point_type integer
);


--
-- Name: supplies_shelly; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplies_shelly (
                                        supply_id uuid NOT NULL,
                                        mac_address character varying(250),
                                        id character varying(250),
                                        mqtt_prefix character varying(250)
);


--
-- Name: supply_partition_coefficient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supply_partition_coefficient (
                                                     id uuid NOT NULL,
                                                     supply_id uuid NOT NULL,
                                                     coefficient numeric(18,6) NOT NULL,
                                                     valid_from timestamp with time zone,
                                                     valid_to timestamp with time zone,
                                                     created_at timestamp with time zone DEFAULT now() NOT NULL,
                                                     plant_id uuid NOT NULL,
                                                     sharing_agreement_id uuid NOT NULL
);


--
-- Name: COLUMN supply_partition_coefficient.valid_from; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.supply_partition_coefficient.valid_from IS 'Persisted as an instant, resolved from the administrator-authored LocalDate using                      the application-level conluz.time.zone.id at the moment of activation (see                      ZoneResolver). A persisted instant does not migrate: when the zone moves to                      community level, the backfill must seed every existing community with the                      application-level value in force at that moment, or older coefficients will keep                      the old instant while newer ones use the new one and the timeline becomes                      internally inconsistent by one hour, silently.';


--
-- Name: COLUMN supply_partition_coefficient.plant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.supply_partition_coefficient.plant_id IS 'The plant this coefficient participates in. Nullable here, made NOT NULL by make_supply_partition_coefficient_plant_and_sharing_agreement_not_null.xml once backfilled.';


--
-- Name: COLUMN supply_partition_coefficient.sharing_agreement_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.supply_partition_coefficient.sharing_agreement_id IS 'The agreement that authored this coefficient. Nullable here for the same reason as plant_id.';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
                              id uuid NOT NULL,
                              personal_id character varying(250) NOT NULL,
                              number integer NOT NULL,
                              password character varying(250),
                              full_name character varying(250) NOT NULL,
                              address character varying(250),
                              phone_number character varying(250),
                              email character varying(250),
                              enabled boolean NOT NULL,
                              is_platform_admin boolean DEFAULT false NOT NULL,
                              must_change_password boolean DEFAULT false NOT NULL,
                              password_changed_at timestamp with time zone,
                              disabled_at timestamp with time zone
);


--
-- Name: COLUMN users.must_change_password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.must_change_password IS 'Whether the user''s current password was chosen by someone else and should be changed by the user. Cleared when the user changes their password.';


--
-- Name: COLUMN users.password_changed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.password_changed_at IS 'When the user last changed their own password. Tokens issued before this instant are rejected. NULL means the password has never been changed, and places no restriction on issued tokens.';


--
-- Name: COLUMN users.disabled_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.disabled_at IS 'When the user was last disabled. Tokens issued before this instant are rejected, also after the user is enabled again. NULL means the user has not been disabled, and places no restriction on issued tokens.';


--
-- Name: blacklisted_token blacklisted_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blacklisted_token
    ADD CONSTRAINT blacklisted_token_pkey PRIMARY KEY (jti);


--
-- Name: communities communities_code_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT communities_code_uk UNIQUE (code);


--
-- Name: communities communities_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT communities_id_pk PRIMARY KEY (id);


--
-- Name: communities communities_legal_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.communities
    ADD CONSTRAINT communities_legal_id_uk UNIQUE (legal_id);


--
-- Name: community_memberships community_memberships_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_memberships
    ADD CONSTRAINT community_memberships_id_pk PRIMARY KEY (id);


--
-- Name: community_memberships community_memberships_user_community_uq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_memberships
    ADD CONSTRAINT community_memberships_user_community_uq UNIQUE (user_id, community_id);


--
-- Name: config config_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.config
    ADD CONSTRAINT config_id_pk PRIMARY KEY (id);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: datadis_config datadis_config_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datadis_config
    ADD CONSTRAINT datadis_config_id_pk PRIMARY KEY (id);


--
-- Name: huawei_config huawei_config_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.huawei_config
    ADD CONSTRAINT huawei_config_id_pk PRIMARY KEY (id);


--
-- Name: supply_partition_coefficient no_overlapping_coefficients; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supply_partition_coefficient
    ADD CONSTRAINT no_overlapping_coefficients EXCLUDE USING gist (plant_id WITH =, supply_id WITH =, tstzrange(valid_from, valid_to, '[)'::text) WITH &&) WHERE ((valid_from IS NOT NULL)) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: one_time_token one_time_token_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.one_time_token
    ADD CONSTRAINT one_time_token_id_pk PRIMARY KEY (id);


--
-- Name: one_time_token one_time_token_token_hash_uq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.one_time_token
    ADD CONSTRAINT one_time_token_token_hash_uq UNIQUE (token_hash);


--
-- Name: plants plants_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plants
    ADD CONSTRAINT plants_id_pk PRIMARY KEY (id);


--
-- Name: plants plants_regulatory_code_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plants
    ADD CONSTRAINT plants_regulatory_code_uk UNIQUE (regulatory_code);


--
-- Name: sharing_agreement_file sharing_agreement_file_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement_file
    ADD CONSTRAINT sharing_agreement_file_id_pk PRIMARY KEY (id);


--
-- Name: sharing_agreement sharing_agreement_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement
    ADD CONSTRAINT sharing_agreement_id_pk PRIMARY KEY (id);


--
-- Name: shelly_config shelly_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelly_config
    ADD CONSTRAINT shelly_config_pkey PRIMARY KEY (id);


--
-- Name: supplies_contract supplies_contract_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_contract
    ADD CONSTRAINT supplies_contract_pk PRIMARY KEY (supply_id);


--
-- Name: supplies_distributor supplies_distributor_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_distributor
    ADD CONSTRAINT supplies_distributor_pk PRIMARY KEY (supply_id);


--
-- Name: supplies supplies_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies
    ADD CONSTRAINT supplies_id_pk PRIMARY KEY (id);


--
-- Name: supplies_shelly supplies_shelly_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_shelly
    ADD CONSTRAINT supplies_shelly_pk PRIMARY KEY (supply_id);


--
-- Name: supply_partition_coefficient supply_partition_coefficient_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supply_partition_coefficient
    ADD CONSTRAINT supply_partition_coefficient_id_pk PRIMARY KEY (id);


--
-- Name: users users_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_id_pk PRIMARY KEY (id);


--
-- Name: users users_personal_id_uq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_personal_id_uq UNIQUE (personal_id);


--
-- Name: idx_supply_partition_coefficient_supply_id_valid_from; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supply_partition_coefficient_supply_id_valid_from ON public.supply_partition_coefficient USING btree (supply_id, valid_from);


--
-- Name: one_time_token_finished_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX one_time_token_finished_at_idx ON public.one_time_token USING btree (LEAST(expires_at, used_at, revoked_at));


--
-- Name: one_time_token_membership_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX one_time_token_membership_created_at_idx ON public.one_time_token USING btree (membership_id, created_at);


--
-- Name: one_time_token_membership_one_active_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX one_time_token_membership_one_active_uq ON public.one_time_token USING btree (membership_id) WHERE ((membership_id IS NOT NULL) AND (used_at IS NULL) AND (revoked_at IS NULL));


--
-- Name: one_time_token_one_active_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX one_time_token_one_active_uq ON public.one_time_token USING btree (user_id, purpose) WHERE ((membership_id IS NULL) AND (used_at IS NULL) AND (revoked_at IS NULL));


--
-- Name: one_time_token_user_purpose_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX one_time_token_user_purpose_created_at_idx ON public.one_time_token USING btree (user_id, purpose, created_at);


--
-- Name: community_memberships community_memberships_community_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_memberships
    ADD CONSTRAINT community_memberships_community_id_fk FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: community_memberships community_memberships_invited_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_memberships
    ADD CONSTRAINT community_memberships_invited_by_fk FOREIGN KEY (invited_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: community_memberships community_memberships_user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_memberships
    ADD CONSTRAINT community_memberships_user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: datadis_config datadis_config_community_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datadis_config
    ADD CONSTRAINT datadis_config_community_id_fk FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: huawei_config huawei_config_plant_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.huawei_config
    ADD CONSTRAINT huawei_config_plant_id_fk FOREIGN KEY (plant_id) REFERENCES public.plants(id);


--
-- Name: one_time_token one_time_token_membership_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.one_time_token
    ADD CONSTRAINT one_time_token_membership_id_fk FOREIGN KEY (membership_id) REFERENCES public.community_memberships(id) ON DELETE CASCADE;


--
-- Name: one_time_token one_time_token_user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.one_time_token
    ADD CONSTRAINT one_time_token_user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: plants plants_supply_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plants
    ADD CONSTRAINT plants_supply_id_fk FOREIGN KEY (supply_id) REFERENCES public.supplies(id);


--
-- Name: sharing_agreement sharing_agreement_created_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement
    ADD CONSTRAINT sharing_agreement_created_by_fk FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: sharing_agreement_file sharing_agreement_file_sharing_agreement_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement_file
    ADD CONSTRAINT sharing_agreement_file_sharing_agreement_id_fk FOREIGN KEY (sharing_agreement_id) REFERENCES public.sharing_agreement(id) ON DELETE CASCADE;


--
-- Name: sharing_agreement_file sharing_agreement_file_uploaded_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement_file
    ADD CONSTRAINT sharing_agreement_file_uploaded_by_fk FOREIGN KEY (uploaded_by) REFERENCES public.users(id);


--
-- Name: sharing_agreement sharing_agreement_plant_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement
    ADD CONSTRAINT sharing_agreement_plant_id_fk FOREIGN KEY (plant_id) REFERENCES public.plants(id);


--
-- Name: sharing_agreement sharing_agreement_updated_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_agreement
    ADD CONSTRAINT sharing_agreement_updated_by_fk FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: shelly_config shelly_config_community_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelly_config
    ADD CONSTRAINT shelly_config_community_id_fk FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: supplies supplies_community_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies
    ADD CONSTRAINT supplies_community_id_fk FOREIGN KEY (community_id) REFERENCES public.communities(id);


--
-- Name: supplies_contract supplies_contract_supply_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_contract
    ADD CONSTRAINT supplies_contract_supply_id_fk FOREIGN KEY (supply_id) REFERENCES public.supplies(id) ON DELETE CASCADE;


--
-- Name: supplies_distributor supplies_distributor_supply_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_distributor
    ADD CONSTRAINT supplies_distributor_supply_id_fk FOREIGN KEY (supply_id) REFERENCES public.supplies(id) ON DELETE CASCADE;


--
-- Name: supplies_shelly supplies_shelly_supply_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies_shelly
    ADD CONSTRAINT supplies_shelly_supply_id_fk FOREIGN KEY (supply_id) REFERENCES public.supplies(id) ON DELETE CASCADE;


--
-- Name: supplies supplies_user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplies
    ADD CONSTRAINT supplies_user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: supply_partition_coefficient supply_partition_coefficient_plant_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supply_partition_coefficient
    ADD CONSTRAINT supply_partition_coefficient_plant_id_fk FOREIGN KEY (plant_id) REFERENCES public.plants(id);


--
-- Name: supply_partition_coefficient supply_partition_coefficient_sharing_agreement_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supply_partition_coefficient
    ADD CONSTRAINT supply_partition_coefficient_sharing_agreement_id_fk FOREIGN KEY (sharing_agreement_id) REFERENCES public.sharing_agreement(id) ON DELETE CASCADE;


--
-- Name: supply_partition_coefficient supply_partition_coefficient_supply_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supply_partition_coefficient
    ADD CONSTRAINT supply_partition_coefficient_supply_id_fk FOREIGN KEY (supply_id) REFERENCES public.supplies(id);


--
-- PostgreSQL database dump complete
--

\unrestrict vgTiQudK2UumbgYPtF8oIvEYhwoj8EyvaWRhgUUoD5Q67Ap6wmLyYdOlmZp5ZIe

