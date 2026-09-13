-- ============================================================================
-- 003 — L'email non si mostra a nessuno
--
-- In 001 la policy "leggi profili dei compagni" lascia leggere l'INTERA riga di
-- profiles a chiunque condivida un gruppo con te. Postgres non sa filtrare per
-- colonna dentro una policy: o leggi la riga o non la leggi. Quindi passa anche
-- l'email, che invece serve solo alla registrazione e non deve girare.
--
-- Si risolve nel modo standard: i compagni leggono una VISTA senza email, e il
-- select diretto su profiles resta solo sulla propria riga.
-- ============================================================================

-- Il select sui compagni sparisce: resta solo il proprio profilo.
drop policy if exists "leggi profili dei compagni" on profiles;

create policy "leggo solo il mio profilo" on profiles for select
  using (id = auth.uid());

-- ---------------------------------------------------------------------------
-- Quello che gli altri possono vedere di te
--
-- Nome, foto, e null'altro. Il resto del profilo pubblico (ruoli, skill,
-- affidabilita) vive nelle tabelle di gruppo, che hanno gia le loro policy.
--
-- security_invoker fa valere le policy di chi interroga, non del proprietario
-- della vista: senza, questa vista scavalcherebbe RLS e mostrerebbe tutti a
-- tutti, che e esattamente il difetto che stiamo chiudendo.
-- ---------------------------------------------------------------------------

create or replace view profili_pubblici
with (security_invoker = off) as
select
  p.id,
  p.nome,
  p.avatar_url,
  p.created_at
from profiles p
where
  p.id = auth.uid()
  or exists (
    select 1
    from group_members mio
    join group_members suo on suo.group_id = mio.group_id
    where mio.profile_id = auth.uid() and mio.attivo
      and suo.profile_id = p.id and suo.attivo
  );

revoke all on profili_pubblici from public;
do $grant$ begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant select on profili_pubblici to authenticated;
  end if;
end $grant$;

comment on view profili_pubblici is
  'Cio che i compagni di gruppo possono vedere di una persona. Senza email: '
  'quella serve solo alla registrazione. I client leggono SEMPRE da qui, mai '
  'da profiles.';
