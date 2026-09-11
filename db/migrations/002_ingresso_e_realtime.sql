-- ============================================================================
-- 002 — Ingresso nel gruppo, e realtime
--
-- Due cose che si scoprono solo scrivendo il client.
--
-- 1. Le policy di 001 dicono che un gruppo lo legge solo chi ne e membro. Ma
--    chi entra con un codice d'invito NON e ancora membro: non riesce a
--    trovare il gruppo, quindi non riesce a entrarci. Un classico stallo da
--    Row Level Security. Si risolve con una funzione security definer, che e
--    anche il posto giusto per creare le righe di profilo del giocatore.
--
-- 2. Il contatore delle iscrizioni in tempo reale ha bisogno che le tabelle
--    siano pubblicate sul canale realtime: su Supabase non lo sono per
--    impostazione predefinita.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Entrare in un gruppo con il codice
-- ---------------------------------------------------------------------------

create or replace function entra_con_codice(codice text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  g uuid;
  utente uuid := auth.uid();
begin
  if utente is null then
    raise exception 'Devi accedere prima di entrare in un gruppo';
  end if;

  select id into g from groups where codice_invito = upper(trim(codice));
  if g is null then
    raise exception 'Codice non valido';
  end if;

  insert into group_members (group_id, profile_id, ruolo)
  values (g, utente, 'giocatore')
  on conflict (group_id, profile_id) do update set attivo = true;

  -- ogni membro parte con le sue righe di valutazione: tutto a 50, nessun
  -- ruolo, nessun pregiudizio
  insert into player_ratings (group_id, profile_id) values (g, utente)
  on conflict do nothing;

  insert into player_roles (group_id, profile_id) values (g, utente)
  on conflict do nothing;

  return g;
end $$;

revoke all on function entra_con_codice(text) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function entra_con_codice(text) to authenticated;
  end if;
end $grant$;

-- ---------------------------------------------------------------------------
-- Creare un gruppo: stesso problema al contrario, e stesse righe da creare
-- ---------------------------------------------------------------------------

create or replace function crea_gruppo(nome text, campo text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  g uuid;
  utente uuid := auth.uid();
begin
  if utente is null then
    raise exception 'Devi accedere prima di creare un gruppo';
  end if;

  insert into groups (nome, campo_default, created_by)
  values (nome, campo, utente)
  returning id into g;

  insert into group_members (group_id, profile_id, ruolo)
  values (g, utente, 'admin');

  insert into player_ratings (group_id, profile_id) values (g, utente);
  insert into player_roles (group_id, profile_id) values (g, utente);

  return g;
end $$;

revoke all on function crea_gruppo(text, text) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function crea_gruppo(text, text) to authenticated;
  end if;
end $grant$;

-- ---------------------------------------------------------------------------
-- Anteprima di un gruppo dal codice, prima di entrarci
--
-- Restituisce il minimo indispensabile per far dire all'utente "si, e questo":
-- nome e quante persone ci sono. Niente altro, perche chi ha il codice non e
-- ancora nessuno.
-- ---------------------------------------------------------------------------

create or replace function anteprima_gruppo(codice text)
returns table (nome text, membri bigint)
language sql
security definer
set search_path = public
as $$
  select g.nome, count(m.profile_id)
  from groups g
  left join group_members m on m.group_id = g.id and m.attivo
  where g.codice_invito = upper(trim(codice))
  group by g.nome;
$$;

revoke all on function anteprima_gruppo(text) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function anteprima_gruppo(text) to authenticated;
  end if;
end $grant$;

-- ---------------------------------------------------------------------------
-- Iscriversi e ritirarsi
--
-- Incapsulate perche il ritiro deve poter essere rifatto (chi si ritira e poi
-- ci ripensa rientra in fondo alla fila, non al posto di prima) e perche cosi
-- il client non deve conoscere gli stati.
-- ---------------------------------------------------------------------------

create or replace function iscriviti(partita uuid)
returns signup_state
language plpgsql
security definer
set search_path = public
as $$
declare
  utente uuid := auth.uid();
  m matches%rowtype;
  esito signup_state;
begin
  select * into m from matches where id = partita;
  if m is null then raise exception 'Partita inesistente'; end if;

  if not exists (select 1 from group_members
                 where group_id = m.group_id and profile_id = utente and attivo) then
    raise exception 'Non fai parte di questo gruppo';
  end if;

  if m.stato not in ('aperta', 'al_completo') then
    raise exception 'Le iscrizioni per questa partita sono chiuse';
  end if;

  -- chi si era ritirato e ci ripensa torna in coda, non al suo vecchio posto:
  -- il posto l'aveva liberato per qualcun altro
  if exists (select 1 from match_signups where match_id = partita and profile_id = utente) then
    update match_signups
       set stato = (case
             when (select count(*) from match_signups
                   where match_id = partita and stato = 'convocato') < m.capienza
             then 'convocato' else 'riserva' end)::signup_state,
           posto = (select coalesce(max(posto), 0) + 1 from match_signups where match_id = partita),
           signed_at = now()
     where match_id = partita and profile_id = utente
     returning stato into esito;
  else
    insert into match_signups (match_id, profile_id)
    values (partita, utente)
    returning stato into esito;
  end if;

  return esito;
end $$;

revoke all on function iscriviti(uuid) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function iscriviti(uuid) to authenticated;
  end if;
end $grant$;

create or replace function ritirati(partita uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
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
-- Realtime
--
-- Senza questo il contatore delle iscrizioni non si aggiorna da solo e
-- bisogna ricaricare la pagina, che e proprio cio che rende un'app di questo
-- tipo fastidiosa da usare in quattordici sullo stesso gruppo WhatsApp.
-- ---------------------------------------------------------------------------

do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table match_signups;
    alter publication supabase_realtime add table matches;
  end if;
exception
  when duplicate_object then null;
end $$;
