-- ============================================================================
-- 005 — Il secondo ruolo dichiarabile
--
-- All'iscrizione si indicano due ruoli, non uno. Con una casella sola, di
-- fronte a quattordici persone, si dichiarano tutti attaccanti e non resta
-- niente da usare; con due, chi si iscrive dice di fatto dove NON gioca, ed e
-- quella l'informazione che serve al primo sorteggio.
--
-- Restano intenzioni, non verdetti: quattro partite di voti le scavalcano
-- entrambe, esattamente come scavalcavano l'unica di prima.
-- ============================================================================

alter table player_roles
  add column if not exists profilo_secondario position_code;

-- Il secondo ruolo esiste solo dopo il primo, e dev'essere un altro.
alter table player_roles
  drop constraint if exists secondo_ruolo_sensato;
alter table player_roles
  add constraint secondo_ruolo_sensato check (
    profilo_secondario is null
    or (profilo_iniziale is not null and profilo_secondario <> profilo_iniziale)
  );

-- POR non puo comparire qui. Il portiere si dichiara con il flag, che solo chi
-- organizza puo alzare: se bastasse mettersi POR come ruolo di scorta, il
-- vincolo del portiere lo scavalcherebbe chiunque, dal proprio profilo.
alter table player_roles
  drop constraint if exists secondo_ruolo_non_e_la_porta;
alter table player_roles
  add constraint secondo_ruolo_non_e_la_porta check (
    profilo_secondario is null or profilo_secondario <> 'POR'
  );

-- Un portiere non ha un ruolo di riserva: se per una volta gioca in campo, e
-- l'organizzatore a spostarlo a mano, ed e un caso che quasi non esiste.
alter table player_roles
  drop constraint if exists portiere_senza_secondo_ruolo;
alter table player_roles
  add constraint portiere_senza_secondo_ruolo check (
    not portiere or profilo_secondario is null
  );

-- I pesi sono gli stessi del motore Kotlin (Propensione.PESO_PRIMO e
-- PESO_SECONDO). Qui servono a far partire le quattro propensioni coerenti con
-- quanto dichiarato, cosi il primo sorteggio di un gruppo nuovo non parte da
-- una tabella di uni.
create or replace function dichiara_ruoli(
  gruppo uuid,
  principale position_code,
  secondario position_code default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  utente uuid := auth.uid();
begin
  if utente is null then
    raise exception 'Devi accedere';
  end if;
  if principale = 'POR' or secondario = 'POR' then
    raise exception 'Il portiere lo indica chi organizza, non si sceglie da soli';
  end if;
  if secondario is not null and secondario = principale then
    raise exception 'I due ruoli devono essere diversi';
  end if;
  if not exists (select 1 from group_members
                 where group_id = gruppo and profile_id = utente and attivo) then
    raise exception 'Non fai parte di questo gruppo';
  end if;

  -- I ruoli dichiarati si possono cambiare sempre: sono cio che la persona
  -- dice di se. Le quattro propensioni invece si riscrivono solo finche non
  -- c'e una partita valutata — dopo, quei numeri sono il frutto dei voti dei
  -- compagni, e sovrascriverli col desiderio cancellerebbe l'unica cosa che
  -- il sistema ha imparato.
  update player_roles
     set profilo_iniziale   = principale,
         profilo_secondario = secondario,
         prop_dif = case when partite_valutate > 0 then prop_dif
                         when principale = 'DIF' then 1.8
                         when secondario = 'DIF' then 1.4 else 1.0 end,
         prop_cen = case when partite_valutate > 0 then prop_cen
                         when principale = 'CEN' then 1.8
                         when secondario = 'CEN' then 1.4 else 1.0 end,
         prop_att = case when partite_valutate > 0 then prop_att
                         when principale = 'ATT' then 1.8
                         when secondario = 'ATT' then 1.4 else 1.0 end,
         updated_at = now()
   where group_id = gruppo and profile_id = utente
     and not portiere;                  -- il portiere non ha ruoli di ripiego

  if not found then
    raise exception 'Non risulti in questo gruppo, o sei indicato come portiere';
  end if;
end $$;

revoke all on function dichiara_ruoli(uuid, position_code, position_code) from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function dichiara_ruoli(uuid, position_code, position_code) to authenticated;
  end if;
end $grant$;
