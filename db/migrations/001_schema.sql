-- ============================================================================
-- Organizza Partita — schema Postgres (Supabase)
-- Versione 1.0
--
-- Convenzioni:
--   * tutti gli id sono uuid, generati dal DB
--   * i timestamp sono timestamptz (UTC), la app converte in ora locale
--   * ogni tabella "di gruppo" porta group_id: l'app e multi-gruppo fin da subito
--   * le skill vivono per (gruppo, giocatore): lo stesso utente puo valere
--     diversamente in due comitive diverse
-- ============================================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Tipi
-- ---------------------------------------------------------------------------

create type position_code as enum ('POR', 'DIF', 'CEN', 'ATT');
create type match_status  as enum ('bozza', 'aperta', 'al_completo', 'squadre_fatte', 'giocata', 'annullata');
create type signup_state  as enum ('convocato', 'riserva', 'ritirato');
create type member_role   as enum ('admin', 'giocatore');
create type team_side     as enum ('A', 'B');
create type event_type    as enum ('gol', 'assist', 'autogol', 'mvp');
create type modalita_porta as enum ('regolare', 'uno_solo', 'a_turno');
create type livello_info   as enum ('etichette', 'rendimenti', 'sorteggio_puro');
create type fase_gioco     as enum ('difesa', 'attacco', 'regia', 'porta');
create type outbox_channel as enum ('push', 'email');
create type outbox_status  as enum ('in_coda', 'inviata', 'errore');

-- ---------------------------------------------------------------------------
-- Profili — 1:1 con auth.users di Supabase
-- ---------------------------------------------------------------------------

create table profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  email        text not null unique,
  nome         text not null,
  avatar_url   text,                      -- Supabase Storage, bucket "avatars"
  telefono     text,
  fcm_token    text,                      -- token push del dispositivo
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

-- Crea automaticamente il profilo alla registrazione
create function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, nome)
  values (new.id, new.email, coalesce(new.raw_user_meta_data->>'nome', split_part(new.email, '@', 1)));
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function handle_new_user();

-- ---------------------------------------------------------------------------
-- Gruppi (la comitiva che gioca insieme)
-- ---------------------------------------------------------------------------

create table groups (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null,
  codice_invito   text not null unique default upper(substr(encode(gen_random_bytes(6), 'hex'), 1, 8)),
  formato_default int  not null default 7 check (formato_default in (7, 8)),
  campo_default   text,
  indirizzo_default text,
  created_by      uuid not null references profiles(id),
  created_at      timestamptz not null default now()
);

create table group_members (
  group_id   uuid not null references groups(id) on delete cascade,
  profile_id uuid not null references profiles(id) on delete cascade,
  ruolo      member_role not null default 'giocatore',
  attivo     boolean not null default true,
  joined_at  timestamptz not null default now(),
  primary key (group_id, profile_id)
);

create index on group_members (profile_id);

-- ---------------------------------------------------------------------------
-- Skill — base 50 su tutto, l'admin puo alzare i valori iniziali
-- ---------------------------------------------------------------------------

create table player_ratings (
  group_id     uuid not null references groups(id) on delete cascade,
  profile_id   uuid not null references profiles(id) on delete cascade,

  velocita     numeric(5,2) not null default 50 check (velocita     between 1 and 99),
  tiro         numeric(5,2) not null default 50 check (tiro         between 1 and 99),
  passaggio    numeric(5,2) not null default 50 check (passaggio    between 1 and 99),
  tecnica      numeric(5,2) not null default 50 check (tecnica      between 1 and 99),
  difesa       numeric(5,2) not null default 50 check (difesa       between 1 and 99),
  fisico       numeric(5,2) not null default 50 check (fisico       between 1 and 99),
  parate       numeric(5,2) not null default 50 check (parate       between 1 and 99),

  -- forma a breve termine, -5..+5, ricalcolata sulle ultime 5 partite
  forma        numeric(4,2) not null default 0 check (forma between -5 and 5),

  -- medie mobili personali: servono all'evoluzione (confronto rendimento/attesa)
  gol_attesi   numeric(5,3) not null default 0.6,
  presenze     int not null default 0,
  assenze_consecutive int not null default 0,

  updated_at   timestamptz not null default now(),
  primary key (group_id, profile_id)
);

-- Nessuno ha un ruolo fisso.
--
-- Nel calcetto una persona fa l'attaccante una domenica e il difensore quella
-- dopo. Il ruolo quindi non e un dato anagrafico dichiarato all'iscrizione --
-- se lo fosse, si dichiarerebbero tutti attaccanti -- ma una PROPENSIONE
-- dedotta da come e andata in campo, piu una confidenza che cresce con le
-- partite valutate. A confidenza zero il sorteggio non usa affatto il ruolo.
--
-- profilo_iniziale e cio che la persona ha detto iscrivendosi ("parto
-- attaccante"): serve a non sorteggiare del tutto alla cieca la prima
-- domenica, viene mostrato come "di partenza", e i fatti lo scavalcano.
create table player_roles (
  group_id         uuid not null references groups(id) on delete cascade,
  profile_id       uuid not null references profiles(id) on delete cascade,

  prop_por         numeric(4,3) not null default 1.000 check (prop_por between 0 and 3),
  prop_dif         numeric(4,3) not null default 1.000 check (prop_dif between 0 and 3),
  prop_cen         numeric(4,3) not null default 1.000 check (prop_cen between 0 and 3),
  prop_att         numeric(4,3) not null default 1.000 check (prop_att between 0 and 3),

  partite_valutate int not null default 0 check (partite_valutate >= 0),
  profilo_iniziale position_code,

  updated_at       timestamptz not null default now(),
  primary key (group_id, profile_id)
);

-- ---------------------------------------------------------------------------
-- Partite
-- ---------------------------------------------------------------------------

create table matches (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references groups(id) on delete cascade,
  inizio_at    timestamptz not null,
  durata_min   int not null default 60,
  campo        text not null,
  indirizzo    text,
  maps_url     text,
  formato      int  not null check (formato in (7, 8)),
  capienza     int  not null,                  -- 14 per il 7v7, 16 per l'8v8
  quota_eur    numeric(6,2),                   -- costo a testa del campo
  stato        match_status not null default 'aperta',
  gol_a        int,
  gol_b        int,
  created_by   uuid not null references profiles(id),
  created_at   timestamptz not null default now(),

  constraint capienza_coerente check (capienza = formato * 2)
);

create index on matches (group_id, inizio_at desc);

create table match_signups (
  match_id    uuid not null references matches(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  stato       signup_state not null default 'convocato',
  posto       int not null,                    -- ordine di arrivo: 1..N
  signed_at   timestamptz not null default now(),
  primary key (match_id, profile_id)
);

create index on match_signups (match_id, stato, posto);

-- ---------------------------------------------------------------------------
-- Sorteggi — conservati con il seed, cosi un sorteggio e riproducibile
-- e si puo confrontare "prima/dopo" se qualcuno da forfait
-- ---------------------------------------------------------------------------

create table match_draws (
  id              uuid primary key default gen_random_uuid(),
  match_id        uuid not null references matches(id) on delete cascade,
  seed            bigint not null,
  algoritmo       text not null default 'balanced-annealing-v1',
  costo           numeric(8,3) not null,       -- valore della funzione di costo
  delta_ovr       numeric(6,2) not null,       -- scarto di forza tra le due squadre
  porta           modalita_porta not null,     -- come si e risolta la questione portiere
  livello         livello_info not null,       -- quanta informazione c'era davvero
  valido          boolean not null default true,
  created_at      timestamptz not null default now()
);

create unique index one_valid_draw_per_match
  on match_draws (match_id) where valido;

create table draw_slots (
  draw_id     uuid not null references match_draws(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  squadra     team_side not null,
  posizione   position_code not null,
  ovr_usato   numeric(5,2) not null,           -- OVR del giocatore in quel ruolo
  primary key (draw_id, profile_id)
);

-- Turnazione fra i pali. Popolata solo per le squadre senza portiere di ruolo:
-- nel calcetto e il caso normale, non l'eccezione, e nessuno deve ritrovarsi a
-- fare il portiere tutte le volte solo perche e capitato.
create table draw_keeper_shifts (
  draw_id     uuid not null references match_draws(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  squadra     team_side not null,
  dal_minuto  int not null,
  al_minuto   int not null,
  primary key (draw_id, profile_id),
  constraint turno_sensato check (al_minuto > dal_minuto and dal_minuto >= 0)
);

create index on draw_keeper_shifts (profile_id);

-- Quanti turni e quanti minuti ha gia fatto ciascuno fra i pali senza esserne
-- il ruolo. E l'ingresso che rende equa la rotazione al sorteggio successivo.
create view turni_porta as
select
  m.group_id,
  k.profile_id,
  count(*)                          as turni,
  sum(k.al_minuto - k.dal_minuto)   as minuti,
  max(m.inizio_at)                  as ultima_volta
from draw_keeper_shifts k
join match_draws d on d.id = k.draw_id and d.valido
join matches m on m.id = d.match_id
where m.stato in ('squadre_fatte', 'giocata')
group by 1, 2;

-- Quante volte due giocatori sono finiti nella stessa squadra: alimenta
-- la penalita anti-ripetizione del sorteggio.
create view teammate_history as
select
  m.group_id,
  least(a.profile_id, b.profile_id)  as p1,
  greatest(a.profile_id, b.profile_id) as p2,
  count(*) as insieme,
  max(m.inizio_at) as ultima_volta
from draw_slots a
join draw_slots b on a.draw_id = b.draw_id
                 and a.squadra = b.squadra
                 and a.profile_id < b.profile_id
join match_draws d on d.id = a.draw_id and d.valido
join matches m on m.id = d.match_id
where m.inizio_at > now() - interval '6 months'
group by 1, 2, 3;

-- ---------------------------------------------------------------------------
-- La formazione finale
--
-- Il sorteggio produce una PROPOSTA. Chi organizza la rimaneggia prima di
-- chiudere la partita, perche sa cose che l'app non sa. Le due formazioni si
-- conservano separate: se si tenesse solo quella sorteggiata, l'evoluzione
-- attribuirebbe i rendimenti alla casella sbagliata, e uno spostato in difesa
-- dall'admin risulterebbe un attaccante che non ha segnato.
-- ---------------------------------------------------------------------------

create table match_lineup (
  match_id    uuid not null references matches(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  squadra     team_side not null,
  posizione   position_code not null,
  -- vero se l'organizzatore ha cambiato qualcosa rispetto al sorteggio
  ritoccato   boolean not null default false,
  primary key (match_id, profile_id)
);

create index on match_lineup (match_id, squadra);

-- ---------------------------------------------------------------------------
-- Il voto di fine partita
--
-- Si vota per FASE DI GIOCO e non per ruolo: un difensore puo essere il miglior
-- regista in campo, e chiedere "chi e stato il miglior centrocampista" quel
-- dato lo perderebbe. Ed e una NOMINA, non un punteggio: dare un voto a tredici
-- persone per quattro fasi sono cinquantadue caselle, che nessuno compila dopo
-- la partita. Quattro nomi sono quattro tocchi.
--
-- votato_id a null significa "nessuno si e distinto", e dev'essere una risposta
-- legittima: obbligare a scegliere produce nomi a caso.
-- ---------------------------------------------------------------------------

create table match_votes (
  match_id   uuid not null references matches(id) on delete cascade,
  votante_id uuid not null references profiles(id) on delete cascade,
  fase       fase_gioco not null,
  votato_id  uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (match_id, votante_id, fase),
  constraint non_si_vota_se_stessi check (votato_id is null or votato_id <> votante_id)
);

create index on match_votes (match_id, fase);

-- Chi ha effettivamente passato minuti in porta: solo loro sono nominabili
-- nella fase 'porta'.
create view chi_ha_parato as
select distinct d.match_id, k.profile_id
from draw_keeper_shifts k
join match_draws d on d.id = k.draw_id and d.valido
union
select l.match_id, l.profile_id
from match_lineup l
where l.posizione = 'POR';

-- Nomine ricevute per fase: e l'ingresso dell'aggiornamento di skill e
-- propensione dopo ogni partita.
create view nomine_ricevute as
select
  m.group_id,
  v.match_id,
  v.votato_id as profile_id,
  v.fase,
  count(*) as nomine,
  (select count(distinct v2.votante_id)
   from match_votes v2
   where v2.match_id = v.match_id and v2.fase = v.fase) as votanti
from match_votes v
join matches m on m.id = v.match_id
where v.votato_id is not null
group by 1, 2, 3, 4;

-- ---------------------------------------------------------------------------
-- Eventi partita e storico skill
-- ---------------------------------------------------------------------------

create table match_events (
  id          uuid primary key default gen_random_uuid(),
  match_id    uuid not null references matches(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  tipo        event_type not null,
  minuto      int,
  created_at  timestamptz not null default now()
);

create index on match_events (match_id);
create index on match_events (profile_id, tipo);

create table rating_history (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references groups(id) on delete cascade,
  profile_id  uuid not null references profiles(id) on delete cascade,
  match_id    uuid references matches(id) on delete set null,
  attributo   text not null,
  valore_pre  numeric(5,2) not null,
  valore_post numeric(5,2) not null,
  motivo      text not null,
  created_at  timestamptz not null default now()
);

create index on rating_history (group_id, profile_id, created_at desc);

-- ---------------------------------------------------------------------------
-- Coda notifiche — push e email partono da qui, svuotata da una Edge Function
-- schedulata con pg_cron. Tenere la coda sul DB rende gli invii idempotenti
-- e ritentabili senza perdere nulla.
-- ---------------------------------------------------------------------------

create table notifications_outbox (
  id            uuid primary key default gen_random_uuid(),
  profile_id    uuid not null references profiles(id) on delete cascade,
  match_id      uuid references matches(id) on delete cascade,
  canale        outbox_channel not null,
  template      text not null,            -- 'conferma_convocazione', 'squadre_pronte', ...
  payload       jsonb not null default '{}'::jsonb,
  invia_dopo    timestamptz not null default now(),
  stato         outbox_status not null default 'in_coda',
  tentativi     int not null default 0,
  ultimo_errore text,
  sent_at       timestamptz,
  created_at    timestamptz not null default now()
);

create index outbox_da_inviare
  on notifications_outbox (invia_dopo)
  where stato = 'in_coda';

-- ============================================================================
-- Logica lato DB
-- ============================================================================

-- Assegna il numero d'ordine e mette in riserva chi arriva oltre capienza.
create function assegna_posto() returns trigger
language plpgsql as $$
declare
  v_capienza int;
  v_occupati int;
begin
  select capienza into v_capienza from matches where id = new.match_id for update;

  select count(*) into v_occupati
  from match_signups
  where match_id = new.match_id and stato = 'convocato';

  select coalesce(max(posto), 0) + 1 into new.posto
  from match_signups where match_id = new.match_id;

  if v_occupati >= v_capienza then
    new.stato := 'riserva';
  end if;

  return new;
end $$;

create trigger trg_assegna_posto
  before insert on match_signups
  for each row execute function assegna_posto();

-- Chiude le iscrizioni al raggiungimento della capienza e accoda le conferme.
create function chiudi_se_al_completo() returns trigger
language plpgsql as $$
declare
  m          matches%rowtype;
  v_convocati int;
  p          record;
begin
  select * into m from matches where id = new.match_id;

  select count(*) into v_convocati
  from match_signups
  where match_id = m.id and stato = 'convocato';

  if v_convocati >= m.capienza and m.stato = 'aperta' then
    update matches set stato = 'al_completo' where id = m.id;

    -- conferma immediata (push + email) a tutti i convocati
    for p in
      select profile_id from match_signups
      where match_id = m.id and stato = 'convocato'
    loop
      insert into notifications_outbox (profile_id, match_id, canale, template, payload)
      values
        (p.profile_id, m.id, 'push',  'conferma_convocazione',
         jsonb_build_object('inizio', m.inizio_at, 'campo', m.campo, 'indirizzo', m.indirizzo)),
        (p.profile_id, m.id, 'email', 'conferma_convocazione',
         jsonb_build_object('inizio', m.inizio_at, 'campo', m.campo, 'indirizzo', m.indirizzo,
                            'maps_url', m.maps_url, 'quota', m.quota_eur));

      -- promemoria 24h e 2h prima
      insert into notifications_outbox (profile_id, match_id, canale, template, payload, invia_dopo)
      values
        (p.profile_id, m.id, 'push', 'promemoria_24h', '{}'::jsonb, m.inizio_at - interval '24 hours'),
        (p.profile_id, m.id, 'push', 'promemoria_2h',  '{}'::jsonb, m.inizio_at - interval '2 hours');
    end loop;
  end if;

  return null;
end $$;

create trigger trg_chiudi_se_al_completo
  after insert or update of stato on match_signups
  for each row execute function chiudi_se_al_completo();

-- Chi si ritira libera il posto: la prima riserva viene promossa e avvisata.
create function promuovi_riserva() returns trigger
language plpgsql as $$
declare
  v_prima uuid;
begin
  if new.stato = 'ritirato' and old.stato = 'convocato' then
    select profile_id into v_prima
    from match_signups
    where match_id = new.match_id and stato = 'riserva'
    order by posto limit 1;

    if v_prima is not null then
      update match_signups set stato = 'convocato'
      where match_id = new.match_id and profile_id = v_prima;

      insert into notifications_outbox (profile_id, match_id, canale, template)
      values (v_prima, new.match_id, 'push',  'promosso_da_riserva'),
             (v_prima, new.match_id, 'email', 'promosso_da_riserva');
    else
      -- nessuna riserva: la partita torna aperta e il sorteggio va rifatto
      update matches set stato = 'aperta' where id = new.match_id;
      update match_draws set valido = false where match_id = new.match_id and valido;
    end if;
  end if;
  return null;
end $$;

create trigger trg_promuovi_riserva
  after update of stato on match_signups
  for each row execute function promuovi_riserva();

-- ============================================================================
-- Row Level Security — si vede solo cio che riguarda i propri gruppi
-- ============================================================================

alter table profiles            enable row level security;
alter table groups              enable row level security;
alter table group_members       enable row level security;
alter table player_ratings      enable row level security;
alter table player_roles        enable row level security;
alter table matches             enable row level security;
alter table match_signups       enable row level security;
alter table match_draws         enable row level security;
alter table draw_slots          enable row level security;
alter table draw_keeper_shifts  enable row level security;
alter table match_lineup        enable row level security;
alter table match_votes         enable row level security;
alter table match_events        enable row level security;
alter table rating_history      enable row level security;
alter table notifications_outbox enable row level security;

create function e_membro(g uuid) returns boolean
language sql security definer stable set search_path = public as $$
  select exists (
    select 1 from group_members
    where group_id = g and profile_id = auth.uid() and attivo
  );
$$;

create function e_admin(g uuid) returns boolean
language sql security definer stable set search_path = public as $$
  select exists (
    select 1 from group_members
    where group_id = g and profile_id = auth.uid() and attivo and ruolo = 'admin'
  );
$$;

-- Profili: leggo il mio e quelli di chi gioca con me
create policy "leggi profili dei compagni" on profiles for select using (
  id = auth.uid() or exists (
    select 1 from group_members a
    join group_members b on a.group_id = b.group_id
    where a.profile_id = auth.uid() and b.profile_id = profiles.id and a.attivo and b.attivo
  )
);
create policy "aggiorna il mio profilo" on profiles for update using (id = auth.uid());

create policy "leggi i miei gruppi"  on groups for select using (e_membro(id));
create policy "crea gruppo"          on groups for insert with check (created_by = auth.uid());
create policy "admin modifica gruppo" on groups for update using (e_admin(id));

create policy "leggi membri"    on group_members for select using (e_membro(group_id));
create policy "entra nel gruppo" on group_members for insert with check (profile_id = auth.uid());
create policy "admin gestisce membri" on group_members for update using (e_admin(group_id));

create policy "leggi skill"     on player_ratings for select using (e_membro(group_id));
create policy "admin tara skill" on player_ratings for all using (e_admin(group_id));

create policy "leggi ruoli"     on player_roles for select using (e_membro(group_id));
create policy "il profilo di partenza lo scelgo io" on player_roles for all
  using (profile_id = auth.uid() or e_admin(group_id));

create policy "leggi partite"   on matches for select using (e_membro(group_id));
create policy "admin crea partita" on matches for insert with check (e_admin(group_id));
create policy "admin modifica partita" on matches for update using (e_admin(group_id));

create policy "leggi iscrizioni" on match_signups for select
  using (exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "mi iscrivo io" on match_signups for insert
  with check (profile_id = auth.uid()
              and exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "mi ritiro io" on match_signups for update
  using (profile_id = auth.uid()
         or exists (select 1 from matches m where m.id = match_id and e_admin(m.group_id)));

create policy "leggi sorteggi" on match_draws for select
  using (exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "admin sorteggia" on match_draws for all
  using (exists (select 1 from matches m where m.id = match_id and e_admin(m.group_id)));

create policy "leggi formazioni" on draw_slots for select
  using (exists (select 1 from match_draws d join matches m on m.id = d.match_id
                 where d.id = draw_id and e_membro(m.group_id)));
create policy "admin scrive formazioni" on draw_slots for all
  using (exists (select 1 from match_draws d join matches m on m.id = d.match_id
                 where d.id = draw_id and e_admin(m.group_id)));

create policy "leggi turni porta" on draw_keeper_shifts for select
  using (exists (select 1 from match_draws d join matches m on m.id = d.match_id
                 where d.id = draw_id and e_membro(m.group_id)));
create policy "admin scrive turni porta" on draw_keeper_shifts for all
  using (exists (select 1 from match_draws d join matches m on m.id = d.match_id
                 where d.id = draw_id and e_admin(m.group_id)));

create policy "leggi formazione finale" on match_lineup for select
  using (exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "admin sistema le squadre" on match_lineup for all
  using (exists (select 1 from matches m where m.id = match_id and e_admin(m.group_id)));

create policy "leggi i voti" on match_votes for select
  using (exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "voto solo io per me" on match_votes for insert
  with check (votante_id = auth.uid()
              and exists (select 1 from match_signups s
                          where s.match_id = match_votes.match_id
                            and s.profile_id = auth.uid()
                            and s.stato = 'convocato'));
create policy "posso correggere il mio voto" on match_votes for update
  using (votante_id = auth.uid());

create policy "leggi eventi" on match_events for select
  using (exists (select 1 from matches m where m.id = match_id and e_membro(m.group_id)));
create policy "admin registra eventi" on match_events for all
  using (exists (select 1 from matches m where m.id = match_id and e_admin(m.group_id)));

create policy "leggi storico skill" on rating_history for select using (e_membro(group_id));

create policy "leggo solo le mie notifiche" on notifications_outbox for select
  using (profile_id = auth.uid());
