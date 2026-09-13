-- ============================================================================
-- Le prove delle regole di 004, su un Postgres vero
--
--   psql -d callmeup -f db/locale/01_prova_regole.sql
--
-- Non e un test unitario e non finge di esserlo: e la serata tipo, fatta
-- succedere davvero. Venti iscritti su quattordici posti, uno che molla, uno
-- che ci ripensa, e un referto in cui i conti non tornano.
--
-- Ogni prova scrive un rigo in prova_esiti. Alla fine si guarda la tabella: se
-- c'e un "no", la regola non c'e, per quanto bello sia il codice che la scrive.
-- ============================================================================

set client_min_messages = warning;

drop table if exists prova_esiti;
drop table if exists prova_dati;
create table prova_esiti (n serial primary key, descrizione text, ok boolean, dettaglio text);
create table prova_dati  (chiave text primary key, valore uuid);

create or replace function annota(d text, esito boolean, dett text default '')
returns void language sql as $$
  insert into prova_esiti (descrizione, ok, dettaglio) values (d, esito, dett);
$$;

create or replace function salva(k text, v uuid) returns uuid language sql as $$
  insert into prova_dati values (k, v)
  on conflict (chiave) do update set valore = excluded.valore
  returning valore;
$$;

create or replace function dato(k text) returns uuid language sql stable as $$
  select valore from prova_dati where chiave = k;
$$;

-- ---------------------------------------------------------------------------
-- La comitiva
-- ---------------------------------------------------------------------------

do $$
declare i int;
begin
  for i in 1..20 loop
    perform salva('g' || i, registra('g' || i || '@prova.it', 'Giocatore ' || i));
  end loop;

  perform accedi_come(dato('g1'));
  perform salva('gruppo', crea_gruppo('Bicocca Region', 'Campo Pirelli'));

  for i in 2..20 loop
    perform accedi_come(dato('g' || i));
    perform entra_con_codice((select codice_invito from groups where id = dato('gruppo')));
  end loop;
end $$;

do $$ begin
  perform annota('venti iscritti al gruppo',
    (select count(*) from group_members where group_id = dato('gruppo')) = 20,
    (select count(*)::text from group_members where group_id = dato('gruppo')));
end $$;

-- ---------------------------------------------------------------------------
-- Prova 1 — la lista si ferma a due
-- ---------------------------------------------------------------------------

do $$
declare v_id uuid;
begin
  perform accedi_come(dato('g1'));
  insert into matches (group_id, inizio_at, durata_min, campo, formato, capienza, quota_eur, created_by)
  values (dato('gruppo'), now() + interval '3 days', 60, 'Campo Pirelli', 7, 14, 6.50, dato('g1'))
  returning id into v_id;
  perform salva('partita', v_id);
end $$;

do $$
declare i int;
begin
  for i in 1..14 loop
    perform accedi_come(dato('g' || i));
    perform iscriviti(dato('partita'));
  end loop;
end $$;

do $$ begin
  perform annota('quattordici convocati, partita al completo',
    (select stato from matches where id = dato('partita')) = 'al_completo',
    (select stato::text from matches where id = dato('partita')));

  perform annota('la quota resta un numero da mostrare',
    (select quota_eur from matches where id = dato('partita')) = 6.50,
    (select quota_eur::text from matches where id = dato('partita')));
end $$;

do $$
declare i int; esito signup_state;
begin
  for i in 15..16 loop
    perform accedi_come(dato('g' || i));
    esito := iscriviti(dato('partita'));
    perform annota('il ' || i || 'esimo entra in lista d''attesa', esito = 'riserva', esito::text);
  end loop;
end $$;

do $$ begin
  perform accedi_come(dato('g17'));
  begin
    perform iscriviti(dato('partita'));
    perform annota('il 17esimo viene respinto', false, 'e stato accettato');
  exception when others then
    perform annota('il 17esimo viene respinto', true, sqlerrm);
  end;
end $$;

do $$
declare r record;
begin
  select * into r from posti_liberi(dato('partita'));
  perform annota('posti_liberi dice 14 convocati, 2 riserve, 0 e 0 liberi',
    r.convocati = 14 and r.riserve = 2 and r.posti_convocato = 0 and r.posti_riserva = 0,
    format('%s convocati, %s riserve, %s + %s liberi',
           r.convocati, r.riserve, r.posti_convocato, r.posti_riserva));
end $$;

-- Il 17esimo prova a entrare scavalcando la funzione, con una insert diretta:
-- e cio che farebbe una chiamata a PostgREST scritta a mano.
do $$ begin
  begin
    insert into match_signups (match_id, profile_id) values (dato('partita'), dato('g17'));
    perform annota('il tetto regge anche alla insert diretta', false, 'e passata');
  exception when others then
    perform annota('il tetto regge anche alla insert diretta', true, sqlerrm);
  end;
end $$;

-- ---------------------------------------------------------------------------
-- Prova 2 — chi molla, e chi ci ripensa
-- ---------------------------------------------------------------------------

do $$ begin
  perform accedi_come(dato('g3'));
  perform ritirati(dato('partita'));

  perform annota('la prima riserva viene promossa',
    (select stato from match_signups where match_id = dato('partita') and profile_id = dato('g15')) = 'convocato',
    (select stato::text from match_signups where match_id = dato('partita') and profile_id = dato('g15')));

  perform annota('il ritiro viene contato una volta',
    (select ritiri from group_members where group_id = dato('gruppo') and profile_id = dato('g3')) = 1,
    (select ritiri::text from group_members where group_id = dato('gruppo') and profile_id = dato('g3')));

  perform annota('la promozione manda due notifiche',
    (select count(*) from notifications_outbox
      where profile_id = dato('g15') and template = 'promosso_da_riserva') = 2,
    (select count(*)::text from notifications_outbox
      where profile_id = dato('g15') and template = 'promosso_da_riserva'));
end $$;

-- Ora ci sono 14 convocati e 1 riserva: chi si e ritirato ci ripensa e rientra
-- in coda, non al suo vecchio posto.
do $$
declare esito signup_state;
begin
  perform accedi_come(dato('g3'));
  esito := iscriviti(dato('partita'));
  perform annota('chi ci ripensa rientra in coda, non al suo posto', esito = 'riserva', esito::text);

  perform annota('il rientro non fa scattare un secondo ritiro',
    (select ritiri from group_members where group_id = dato('gruppo') and profile_id = dato('g3')) = 1,
    (select ritiri::text from group_members where group_id = dato('gruppo') and profile_id = dato('g3')));
end $$;

-- La lista e di nuovo piena (14 + 2): il 17esimo che rientra dopo essersi
-- ritirato deve trovare lo stesso muro. E la porta di servizio, l'UPDATE.
do $$ begin
  perform accedi_come(dato('g17'));
  begin
    perform iscriviti(dato('partita'));
    perform annota('il muro vale anche per chi rientra', false, 'e rientrato con la lista piena');
  exception when others then
    perform annota('il muro vale anche per chi rientra', true, sqlerrm);
  end;
end $$;

-- Ritirarsi due volte non conta due volte, e non e un errore.
do $$ begin
  perform accedi_come(dato('g4'));
  perform ritirati(dato('partita'));
  perform ritirati(dato('partita'));
  perform annota('ritirarsi due volte conta una volta sola',
    (select ritiri from group_members where group_id = dato('gruppo') and profile_id = dato('g4')) = 1,
    (select ritiri::text from group_members where group_id = dato('gruppo') and profile_id = dato('g4')));
end $$;

-- Chi si sfila dalla lista d'attesa non lascia nessuno a piedi: non si conta.
-- Chi sia la riserva a questo punto lo decide la storia della serata, non il
-- test: si prende quella che c'e. (Alla prima stesura qui c'era un nome fisso,
-- e nel frattempo quel nome era stato promosso.)
do $$
declare in_attesa uuid; prima int; dopo int;
begin
  select profile_id into in_attesa from match_signups
  where match_id = dato('partita') and stato = 'riserva' order by posto limit 1;

  select ritiri into prima from group_members
  where group_id = dato('gruppo') and profile_id = in_attesa;

  perform accedi_come(in_attesa);
  perform ritirati(dato('partita'));

  select ritiri into dopo from group_members
  where group_id = dato('gruppo') and profile_id = in_attesa;

  perform annota('la riserva che si sfila non prende un ritiro',
    prima = dopo, format('da %s a %s', prima, dopo));
end $$;

do $$ begin
  perform accedi_come(dato('g18'));
  begin
    perform ritirati(dato('partita'));
    perform annota('chi non e iscritto non puo ritirarsi', false, 'e passata in silenzio');
  exception when others then
    perform annota('chi non e iscritto non puo ritirarsi', true, sqlerrm);
  end;
end $$;

-- ---------------------------------------------------------------------------
-- Prova 3 — il referto
--
-- Si fa la formazione con i quattordici che ci sono davvero, sette per parte,
-- e poi si prova a chiudere il referto in tutti i modi sbagliati.
-- ---------------------------------------------------------------------------

do $$
declare r record; i int := 0;
begin
  for r in
    select profile_id from match_signups
    where match_id = dato('partita') and stato = 'convocato' order by posto
  loop
    insert into match_lineup (match_id, profile_id, squadra, posizione)
    values (dato('partita'), r.profile_id,
            (case when i < 7 then 'A' else 'B' end)::team_side,
            (case when i % 7 = 0 then 'POR' when i % 7 < 3 then 'DIF'
                  when i % 7 < 6 then 'CEN' else 'ATT' end)::position_code);
    perform salva('campo' || i, r.profile_id);
    i := i + 1;
  end loop;

  update matches set stato = 'squadre_fatte' where id = dato('partita');
  perform annota('formazione da quattordici, sette per parte',
    (select count(*) from match_lineup where match_id = dato('partita')) = 14
    and (select count(*) from match_lineup where match_id = dato('partita') and squadra = 'A') = 7,
    (select count(*)::text from match_lineup where match_id = dato('partita')));
end $$;

-- Tre gol della bianca, uno della nera, piu un autogol di un giocatore della
-- nera: il risultato vero e 4 a 1.
do $$ begin
  insert into match_events (match_id, profile_id, tipo, minuto) values
    (dato('partita'), dato('campo3'),  'gol', 12),
    (dato('partita'), dato('campo5'),  'gol', 31),
    (dato('partita'), dato('campo3'),  'gol', 47),
    (dato('partita'), dato('campo9'),  'gol', 20),
    (dato('partita'), dato('campo11'), 'autogol', 55);
end $$;

do $$
declare b int; n int;
begin
  select max(gol) filter (where squadra = 'A'), max(gol) filter (where squadra = 'B')
  into b, n from gol_dichiarati(dato('partita'));
  perform annota('l''autogol conta per l''altra squadra: 4 a 1',
    b = 4 and n = 1, format('bianca %s, nera %s', b, n));
end $$;

do $$ begin
  perform accedi_come(dato('g1'));
  begin
    perform chiudi_referto(dato('partita'), 3, 1);
    perform annota('un risultato che non torna viene rifiutato', false, 'ha chiuso lo stesso');
  exception when others then
    perform annota('un risultato che non torna viene rifiutato', true, sqlerrm);
  end;
end $$;

do $$ begin
  perform accedi_come(dato('g5'));       -- non e l'organizzatore
  begin
    perform chiudi_referto(dato('partita'), 4, 1);
    perform annota('il referto lo chiude solo chi organizza', false, 'lo ha chiuso un giocatore');
  exception when others then
    perform annota('il referto lo chiude solo chi organizza', true, sqlerrm);
  end;
end $$;

-- Un gol dichiarato da chi non era in campo: uno del gruppo rimasto a casa.
do $$
declare fuori uuid := dato('g19');
begin
  insert into match_events (match_id, profile_id, tipo) values (dato('partita'), fuori, 'gol');

  perform accedi_come(dato('g1'));
  begin
    perform chiudi_referto(dato('partita'), 5, 1);
    perform annota('un gol di chi non era in campo blocca il referto', false, 'ha chiuso');
  exception when others then
    perform annota('un gol di chi non era in campo blocca il referto', true, sqlerrm);
  end;

  delete from match_events where match_id = dato('partita') and profile_id = fuori;
end $$;

do $$ begin
  perform accedi_come(dato('g1'));
  perform chiudi_referto(dato('partita'), 4, 1);
  perform annota('il referto giusto si chiude',
    (select stato from matches where id = dato('partita')) = 'giocata'
    and (select gol_a from matches where id = dato('partita')) = 4,
    (select format('%s, %s-%s', stato, gol_a, gol_b) from matches where id = dato('partita')));
end $$;

do $$ begin
  begin
    insert into match_events (match_id, profile_id, tipo) values (dato('partita'), dato('campo3'), 'gol');
    perform annota('a referto chiuso non si aggiungono gol', false, 'ne ho aggiunto uno');
  exception when others then
    perform annota('a referto chiuso non si aggiungono gol', true, sqlerrm);
  end;
end $$;

do $$ begin
  perform accedi_come(dato('g1'));
  perform riapri_referto(dato('partita'));
  insert into match_events (match_id, profile_id, tipo) values (dato('partita'), dato('campo5'), 'gol');
  perform chiudi_referto(dato('partita'), 5, 1);
  perform annota('riaperto, corretto e richiuso',
    (select gol_a from matches where id = dato('partita')) = 5,
    (select format('%s-%s', gol_a, gol_b) from matches where id = dato('partita')));
end $$;

do $$ begin
  perform accedi_come(dato('g5'));
  begin
    perform ritirati(dato('partita'));
    perform annota('a partita giocata non ci si ritira piu', false, 'si e ritirato a cose fatte');
  exception when others then
    perform annota('a partita giocata non ci si ritira piu', true, sqlerrm);
  end;
end $$;

-- Le presenze arrivano dalla partita giocata, i ritiri dal contatore.
do $$
declare r record;
begin
  select * into r from contatori_giocatore
  where group_id = dato('gruppo') and profile_id = dato('g1');
  perform annota('chi ha giocato ha una presenza e zero ritiri',
    r.presenze = 1 and r.ritiri = 0, format('%s presenze, %s ritiri', r.presenze, r.ritiri));

  select * into r from contatori_giocatore
  where group_id = dato('gruppo') and profile_id = dato('g3');
  perform annota('chi ha mollato ha zero presenze e un ritiro',
    r.presenze = 0 and r.ritiri = 1, format('%s presenze, %s ritiri', r.presenze, r.ritiri));
end $$;


-- ---------------------------------------------------------------------------
-- Prova 4 — i due ruoli dichiarabili (005)
-- ---------------------------------------------------------------------------

do $$ begin
  perform accedi_come(dato('g7'));
  perform dichiara_ruoli(dato('gruppo'), 'ATT', 'CEN');
  perform annota('si dichiarano due ruoli, e pesano diversamente',
    (select prop_att = 1.8 and prop_cen = 1.4 and prop_dif = 1.0
     from player_roles where group_id = dato('gruppo') and profile_id = dato('g7')),
    (select format('att %s, cen %s, dif %s', prop_att, prop_cen, prop_dif)
     from player_roles where group_id = dato('gruppo') and profile_id = dato('g7')));
end $$;

do $$ begin
  perform accedi_come(dato('g8'));
  begin
    perform dichiara_ruoli(dato('gruppo'), 'DIF', 'DIF');
    perform annota('i due ruoli devono essere diversi', false, 'accettati uguali');
  exception when others then
    perform annota('i due ruoli devono essere diversi', true, sqlerrm);
  end;

  begin
    perform dichiara_ruoli(dato('gruppo'), 'DIF', 'POR');
    perform annota('il portiere non si sceglie da soli', false, 'accettato POR');
  exception when others then
    perform annota('il portiere non si sceglie da soli', true, sqlerrm);
  end;
end $$;

-- Chi ha gia partite valutate puo cambiare quel che dichiara, ma le
-- propensioni imparate dai voti non si riscrivono.
do $$
declare prima numeric;
begin
  update player_roles set partite_valutate = 6, prop_dif = 2.4
   where group_id = dato('gruppo') and profile_id = dato('g9');
  select prop_dif into prima from player_roles
   where group_id = dato('gruppo') and profile_id = dato('g9');

  perform accedi_come(dato('g9'));
  perform dichiara_ruoli(dato('gruppo'), 'ATT');

  perform annota('con partite alle spalle i voti battono la dichiarazione',
    (select prop_dif = prima and profilo_iniziale = 'ATT'
     from player_roles where group_id = dato('gruppo') and profile_id = dato('g9')),
    (select format('dif %s, dichiarato %s', prop_dif, profilo_iniziale)
     from player_roles where group_id = dato('gruppo') and profile_id = dato('g9')));
end $$;

do $$ begin
  perform accedi_come(dato('g1'));
  update player_roles set portiere = true
   where group_id = dato('gruppo') and profile_id = dato('g10');
  perform accedi_come(dato('g10'));
  begin
    perform dichiara_ruoli(dato('gruppo'), 'DIF', 'CEN');
    perform annota('chi para non si dichiara ruoli di movimento', false, 'accettati');
  exception when others then
    perform annota('chi para non si dichiara ruoli di movimento', true, sqlerrm);
  end;
end $$;

-- ---------------------------------------------------------------------------
-- Esito
-- ---------------------------------------------------------------------------

select n,
       case when ok then 'si' else 'NO' end as passa,
       descrizione,
       left(dettaglio, 72) as dettaglio
from prova_esiti order by n;

select count(*) filter (where ok) as passate,
       count(*) filter (where not ok) as fallite,
       count(*) as totale
from prova_esiti;
