-- ============================================================================
-- Finto Supabase, per provare lo schema su un Postgres qualunque
--
-- Le migrazioni sono scritte per Supabase e danno per scontate tre cose che
-- Supabase mette da se: lo schema "auth" con la tabella degli utenti, la
-- funzione auth.uid() che dice chi sta chiamando, e i due ruoli "anon" e
-- "authenticated". Su un Postgres vuoto non esiste niente di tutto questo, e
-- 001 fallisce alla prima foreign key.
--
-- Questo file crea la versione minima di quelle tre cose. Serve SOLO in locale:
-- su Supabase non va eseguito mai, perche sovrascriverebbe roba vera.
--
--   createdb convocami
--   psql -d convocami -f db/locale/00_finta_supabase.sql
--   psql -d convocami -f db/migrations/001_schema.sql
--   psql -d convocami -f db/migrations/002_ingresso_e_realtime.sql
--   psql -d convocami -f db/migrations/003_email_privata.sql
--   psql -d convocami -f db/migrations/004_regole_di_partita.sql
--
-- Vale la pena tenerlo: e la differenza fra "lo schema sembra giusto" e "lo
-- schema e stato eseguito". Nel progetto, la seconda e l'unica che conta.
-- ============================================================================

create extension if not exists "pgcrypto";

create schema if not exists auth;

-- Supabase ci mette molte piu colonne. Queste sono quelle che le migrazioni
-- toccano davvero: l'id a cui punta profiles, l'email, e i metadati da cui
-- handle_new_user pesca il nome scelto alla registrazione.
create table if not exists auth.users (
  id                 uuid primary key default gen_random_uuid(),
  email              text unique,
  raw_user_meta_data jsonb not null default '{}'::jsonb,
  created_at         timestamptz not null default now()
);

-- Chi sta chiamando. Su Supabase viene dal JWT; qui da una variabile di
-- sessione che si imposta a mano con accedi_come().
create or replace function auth.uid() returns uuid
language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

-- Impersona un utente per il resto della sessione psql.
create or replace function accedi_come(utente uuid) returns void
language sql as $$
  select set_config('request.jwt.claim.sub', utente::text, false);
$$;

-- Crea un utente come farebbe la registrazione, e restituisce il suo id.
-- Il trigger handle_new_user di 001 fa il resto: riga in profiles, skill a 50.
create or replace function registra(indirizzo text, nome text) returns uuid
language plpgsql as $$
declare nuovo uuid;
begin
  insert into auth.users (email, raw_user_meta_data)
  values (indirizzo, jsonb_build_object('nome', nome))
  returning id into nuovo;
  return nuovo;
end $$;

do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
end $$;
