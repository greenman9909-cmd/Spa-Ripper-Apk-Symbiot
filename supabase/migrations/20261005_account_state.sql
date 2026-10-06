create table public.account_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  snapshot jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now(),
  constraint account_state_snapshot_object check (jsonb_typeof(snapshot) = 'object'),
  constraint account_state_snapshot_size check (octet_length(snapshot::text) <= 1048576)
);
alter table public.account_state enable row level security;
revoke all on public.account_state from anon;
grant select, insert, update, delete on public.account_state to authenticated;
create policy account_state_select on public.account_state for select to authenticated
  using ((select auth.uid()) = user_id);
create policy account_state_insert on public.account_state for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy account_state_update on public.account_state for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy account_state_delete on public.account_state for delete to authenticated
  using ((select auth.uid()) = user_id);
