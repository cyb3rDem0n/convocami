-- ============================================================================
-- 004 — Le regole della partita
--
-- Quattro regole decise a voce e mai scritte da nessuna parte. Finche vivono
-- solo nella pagina, si aggirano con una chiamata diretta a PostgREST: qui
-- diventano vincoli del database.
--
--   1. La lista d'attesa si ferma a due. Il quindicesimo entra, il
--      diciassettesimo no.
--   2. Chi si ritira non paga pegno, ma il ritiro si conta.
--   3. La quota si mostra e basta. Nessun movimento di denaro, nessun IBAN:
--      qui si impone solo che il numero non sia negativo.
--   4. I gol dichiarati devono combaciare con il risultato finale.
--
-- Convenzione, usata da qui in avanti e nei messaggi d'errore:
-- **squadra A = bianca, squadra B = nera**. Sono i colori delle pettorine.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Il tetto alle riserve
--
-- E una colonna e non una costante nel codice perche il numero e una scelta
-- della comitiva, non una proprieta dell'universo: chi gioca in un campo con lo
-- spogliatoio grande puo volerne tre.
-- ---------------------------------------------------------------------------

alter table matches
  add column if not exists max_riserve int not null default 2;

alter table matches
  drop constraint if exists riserve_ragionevoli;
alter table matches
  add constraint riserve_ragionevoli check (max_riserve between 0 and 10);

alter table matches
  drop constraint if exists quota_non_negativa;
alter table matches
  add constraint quota_non_negativa check (quota_eur is null or quota_eur >= 0);

-- Quanti posti restano, convocati e riserve insieme. Serve al client per
-- scrivere "2 posti, poi lista d'attesa" invece di far scoprire il muro a chi
-- preme il pulsante.
create or replace function posti_liberi(partita uuid)
returns table (convocati int, riserve int, posti_convocato int, posti_riserva int)
language sql stable as $$
  select
    c.convocati,
    c.riserve,
    greatest(m.capienza - c.convocati, 0),
    greatest(m.max_riserve - c.riserve, 0)
  from matches m
  cross join lateral (
    select
      count(*) filter (where stato = 'convocato')::int as convocati,
      count(*) filter (where stato = 'riserva')::int   as riserve
    from match_signups where match_id = m.id
  ) c
  where m.id = partita;
$$;

-- assegna_posto torna qui per intero, con il muro in fondo. E lo stesso
-- trigger di 001 piu il controllo: la versione di 001 metteva in riserva
-- chiunque arrivasse, all'infinito.
create or replace function assegna_posto() returns trigger
language plpgsql as $$
declare
  v_capienza    int;
  v_max_riserve int;
  v_convocati   int;
  v_riserve     int;
begin
  select capienza, max_riserve into v_capienza, v_max_riserve
  from matches where id = new.match_id for update;

  select
    count(*) filter (where stato = 'convocato'),
    count(*) filter (where stato = 'riserva')
  into v_convocati, v_riserve
  from match_signups
  where match_id = new.match_id and profile_id <> new.profile_id;

  select coalesce(max(posto), 0) + 1 into new.posto
  from match_signups where match_id = new.match_id;

  if v_convocati >= v_capienza then
    if v_riserve >= v_max_riserve then
      raise exception 'Lista piena: % convocati e % in attesa, non si accettano altre iscrizioni',
        v_convocati, v_riserve
        using errcode = 'check_violation';
    end if;
    new.stato := 'riserva';
  end if;

  return new;
end $$;

-- Il muro vale anche per chi si era ritirato e ci ripensa: rientra dalla porta
-- dell'UPDATE, dove il trigger di cui sopra non passa. Senza questo, il tetto
-- lo scavalca chiunque si sia iscritto una volta.
create or replace function tetto_anche_al_rientro() returns trigger
language plpgsql as $$
declare
  v_capienza    int;
  v_max_riserve int;
  v_convocati   int;
  v_riserve     int;
begin
  if new.stato = 'ritirato' or old.stato <> 'ritirato' then
    return new;                      -- non e un rientro: non e affar mio
  end if;

  select capienza, max_riserve into v_capienza, v_max_riserve
  from matches where id = new.match_id for update;

  select
    count(*) filter (where stato = 'convocato'),
    count(*) filter (where stato = 'riserva')
  into v_convocati, v_riserve
  from match_signups
  where match_id = new.match_id and profile_id <> new.profile_id;

  if v_convocati >= v_capienza and v_riserve >= v_max_riserve then
    raise exception 'Lista piena: % convocati e % in attesa, non si rientra',
      v_convocati, v_riserve
      using errcode = 'check_violation';
  end if;

  return new;
end $$;

drop trigger if exists trg_tetto_anche_al_rientro on match_signups;
create trigger trg_tetto_anche_al_rientro
  before update of stato on match_signups
  for each row execute function tetto_anche_al_rientro();

-- ---------------------------------------------------------------------------
-- 2. Il contatore dei ritiri
--
-- Nessuna penalita: solo un numero accanto alle presenze, e i due si leggono
-- insieme. "Tre ritiri" da solo e un'accusa; "28 presenze e 3 ritiri" e un
-- fatto, e nel calcetto e pure un buon curriculum.
--
-- Due decisioni che non sono ovvie e che e giusto vedere scritte.
--
-- Si conta solo chi molla un posto da CONVOCATO. Una riserva che si sfila non
-- lascia nessuno a piedi: contarla sarebbe un rimprovero per un gesto senza
-- conseguenze.
--
-- Si conta una volta per partita. Chi si ritira, ci ripensa e si ritira di
-- nuovo ha mollato una partita, non due — ed e la stessa persona che, contando
-- ogni volta, si ritroverebbe il numero gonfiato per aver cambiato idea.
-- ---------------------------------------------------------------------------

alter table match_signups
  add column if not exists ritiro_contato boolean not null default false;

alter table group_members
  add column if not exists ritiri int not null default 0;

alter table group_members
  drop constraint if exists ritiri_non_negativi;
alter table group_members
  add constraint ritiri_non_negativi check (ritiri >= 0);

create or replace function conta_ritiro() returns trigger
language plpgsql as $$
declare
  v_gruppo uuid;
begin
  if new.stato = 'ritirato' and old.stato = 'convocato' and not old.ritiro_contato then
    select group_id into v_gruppo from matches where id = new.match_id;

    update group_members
       set ritiri = ritiri + 1
     where group_id = v_gruppo and profile_id = new.profile_id;

    update match_signups
       set ritiro_contato = true
     where match_id = new.match_id and profile_id = new.profile_id;
  end if;
  return null;
end $$;

drop trigger if exists trg_conta_ritiro on match_signups;
create trigger trg_conta_ritiro
  after update of stato on match_signups
  for each row execute function conta_ritiro();

-- Presenze e ritiri, che si leggono insieme. Le presenze si contano sul
-- momento e non si tengono in una colonna: sono gia scritte nelle iscrizioni, e
-- un contatore che duplica un dato che esiste prima o poi diverge da quel dato.
create or replace view contatori_giocatore
with (security_invoker = on) as
  select
    gm.group_id,
    gm.profile_id,
    coalesce(p.presenze, 0)::int as presenze,
    gm.ritiri
  from group_members gm
  left join lateral (
    select count(*) as presenze
    from match_signups s
    join matches m on m.id = s.match_id
    where s.profile_id = gm.profile_id
      and m.group_id   = gm.group_id
      and s.stato      = 'convocato'
      and m.stato      = 'giocata'
  ) p on true;

-- Ritirarsi diventa una funzione che dice di no quando serve. Prima era una
-- UPDATE che passava sempre, anche per chi non era iscritto e anche a partita
-- giocata: taceva e non faceva niente, che e il modo peggiore di fallire.
create or replace function ritirati(partita uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_stato_partita match_status;
  v_mio_stato     signup_state;
begin
  select stato into v_stato_partita from matches where id = partita;
  if v_stato_partita is null then
    raise exception 'Partita inesistente';
  end if;
  if v_stato_partita in ('giocata', 'annullata') then
    raise exception 'Questa partita e chiusa: non ci si puo piu ritirare';
  end if;

  select stato into v_mio_stato
  from match_signups where match_id = partita and profile_id = auth.uid();

  if v_mio_stato is null then
    raise exception 'Non risulti iscritto a questa partita';
  end if;
  if v_mio_stato = 'ritirato' then
    return;                          -- gia fatto: non e un errore, e un doppio tocco
  end if;

  update match_signups set stato = 'ritirato'
  where match_id = partita and profile_id = auth.uid();
end $$;

revoke all on function ritirati(uuid) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function ritirati(uuid) to authenticated;
  end if;
end $grant$;

-- ---------------------------------------------------------------------------
-- 4. Il referto: i gol dichiarati devono fare il risultato
--
-- Ognuno si prende i suoi gol dal telefono, e il risultato lo scrive chi
-- organizza. Se i conti non tornano, il referto non si chiude — non si
-- aggiusta, non si arrotonda, non passa "quasi giusto": qualcuno si e preso un
-- gol che non ha fatto, o se n'e dimenticato uno.
--
-- L'autogol conta per l'altra squadra. E l'unico punto in cui il conteggio non
-- e "somma i gol dei miei", ed e anche quello che si sbaglia scrivendolo di
-- fretta.
--
-- Il controllo sta qui e non in un CHECK perche guarda tre tabelle insieme, e
-- perche deve valere alla chiusura: durante la serata il conto e per forza
-- sbagliato, il primo che dichiara un gol lo dichiara quando il risultato
-- ancora non c'e.
-- ---------------------------------------------------------------------------

create or replace function gol_dichiarati(partita uuid)
returns table (squadra team_side, gol int)
language sql stable as $$
  select s.lato, coalesce(count(e.vale_per), 0)::int
  from (values ('A'::team_side), ('B'::team_side)) as s(lato)
  left join (
    select
      case when ev.tipo = 'autogol'
           then case when l.squadra = 'A' then 'B'::team_side else 'A'::team_side end
           else l.squadra
      end as vale_per
    from match_events ev
    join match_lineup l
      on l.match_id = ev.match_id and l.profile_id = ev.profile_id
    where ev.match_id = partita and ev.tipo in ('gol', 'autogol')
  ) e on e.vale_per = s.lato
  group by s.lato;
$$;

create or replace function chiudi_referto(partita uuid, gol_bianca int, gol_nera int)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  m            matches%rowtype;
  v_bianca     int;
  v_nera       int;
  v_intrusi    int;
begin
  select * into m from matches where id = partita for update;
  if m is null then raise exception 'Partita inesistente'; end if;

  if not exists (select 1 from group_members
                 where group_id = m.group_id and profile_id = auth.uid()
                   and ruolo = 'admin' and attivo) then
    raise exception 'Il referto lo chiude chi organizza';
  end if;

  if m.stato = 'giocata' then
    raise exception 'Il referto e gia chiuso. Per correggerlo, riaprilo';
  end if;
  if m.stato = 'annullata' then
    raise exception 'Questa partita e stata annullata';
  end if;
  if gol_bianca < 0 or gol_nera < 0 then
    raise exception 'Un risultato non ha numeri negativi';
  end if;

  if not exists (select 1 from match_lineup where match_id = partita) then
    raise exception 'Prima le squadre, poi il referto: qui non risulta nessuna formazione';
  end if;

  -- chi dichiara un gol senza essere in formazione non e un dettaglio da
  -- ignorare: o la formazione e sbagliata, o il gol e di un altro
  select count(*) into v_intrusi
  from match_events ev
  where ev.match_id = partita
    and ev.tipo in ('gol', 'autogol')
    and not exists (select 1 from match_lineup l
                    where l.match_id = ev.match_id and l.profile_id = ev.profile_id);

  if v_intrusi > 0 then
    raise exception 'Ci sono % gol dichiarati da chi non risulta in campo', v_intrusi
      using errcode = 'check_violation';
  end if;

  select
    max(gol) filter (where squadra = 'A'),
    max(gol) filter (where squadra = 'B')
  into v_bianca, v_nera
  from gol_dichiarati(partita);

  if v_bianca <> gol_bianca or v_nera <> gol_nera then
    raise exception
      'I gol dichiarati non fanno il risultato. Bianca: risultato %, dichiarati %. Nera: risultato %, dichiarati %',
      gol_bianca, v_bianca, gol_nera, v_nera
      using errcode = 'check_violation',
            hint = 'Autogol compresi: valgono per la squadra avversaria.';
  end if;

  update matches
     set gol_a = gol_bianca,
         gol_b = gol_nera,
         stato = 'giocata'
   where id = partita;
end $$;

-- Riaprire deve essere possibile, altrimenti il primo errore e per sempre e la
-- gente impara a non chiudere niente.
create or replace function riapri_referto(partita uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  m matches%rowtype;
begin
  select * into m from matches where id = partita for update;
  if m is null then raise exception 'Partita inesistente'; end if;

  if not exists (select 1 from group_members
                 where group_id = m.group_id and profile_id = auth.uid()
                   and ruolo = 'admin' and attivo) then
    raise exception 'Il referto lo riapre chi organizza';
  end if;

  if m.stato <> 'giocata' then
    raise exception 'Questo referto non e chiuso';
  end if;

  update matches
     set stato = 'squadre_fatte', gol_a = null, gol_b = null
   where id = partita;
end $$;

-- A referto chiuso i gol non si toccano piu: se si potessero aggiungere dopo,
-- il vincolo appena scritto durerebbe il tempo di una chiamata in piu.
create or replace function referto_congelato() returns trigger
language plpgsql as $$
declare
  v_stato match_status;
  v_partita uuid := coalesce(new.match_id, old.match_id);
begin
  select stato into v_stato from matches where id = v_partita;
  if v_stato = 'giocata' then
    raise exception 'Referto chiuso: per cambiare i gol, chi organizza deve riaprirlo'
      using errcode = 'check_violation';
  end if;
  return coalesce(new, old);
end $$;

drop trigger if exists trg_referto_congelato on match_events;
create trigger trg_referto_congelato
  before insert or update or delete on match_events
  for each row execute function referto_congelato();

revoke all on function chiudi_referto(uuid, int, int) from public;
revoke all on function riapri_referto(uuid) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function chiudi_referto(uuid, int, int) to authenticated;
    grant execute on function riapri_referto(uuid) to authenticated;
    grant execute on function posti_liberi(uuid) to authenticated;
    grant execute on function gol_dichiarati(uuid) to authenticated;
    grant select on contatori_giocatore to authenticated;
  end if;
end $grant$;
