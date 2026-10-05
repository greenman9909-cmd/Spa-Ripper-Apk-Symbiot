create table public.app_comment_votes (
  comment_id uuid not null references public.app_comments(id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  vote_type text not null check (vote_type in ('like','spoiler','inappropriate')),
  primary key (comment_id,user_id,vote_type)
);
create index app_comment_votes_owner on public.app_comment_votes(user_id);
alter table public.app_comment_votes enable row level security;
revoke all on public.app_comment_votes from anon,authenticated;
grant select,delete on public.app_comment_votes to authenticated;
grant insert (comment_id,vote_type) on public.app_comment_votes to authenticated;
create policy vote_read_own on public.app_comment_votes for select to authenticated using ((select auth.uid())=user_id);
create policy vote_insert_own on public.app_comment_votes for insert to authenticated with check ((select auth.uid())=user_id);
create policy vote_delete_own on public.app_comment_votes for delete to authenticated using ((select auth.uid())=user_id);

-- The public feed returns aggregate votes and the caller's own flags only.
create schema if not exists app_private;
grant usage on schema app_private to anon,authenticated;
create function app_private.comment_votes(comment_uuid uuid) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object(
   'votes',jsonb_build_object('like',count(*) filter(where vote_type='like'),'spoiler',count(*) filter(where vote_type='spoiler'),'inappropriate',count(*) filter(where vote_type='inappropriate')),
   'user_votes',coalesce(jsonb_agg(upper(vote_type)) filter(where user_id=auth.uid()),'[]'::jsonb)
 ) from public.app_comment_votes where comment_id=comment_uuid;
$$;
revoke all on function app_private.comment_votes(uuid) from public;
grant execute on function app_private.comment_votes(uuid) to anon,authenticated;

create or replace function public.comment_feed(book text,parent uuid default null,page_number int default 1,page_size int default 50,ascending_order boolean default false,comment_uuid uuid default null)
returns jsonb language sql stable security invoker set search_path = '' as $$
  with selected as (
    select c.* from public.app_comments c
    where c.guestbook_key=book and ((comment_uuid is not null and c.id=comment_uuid) or (comment_uuid is null and c.parent_id is not distinct from parent))
  ), paged as (
    select * from selected
    order by case when ascending_order then created_at end asc, case when not ascending_order then created_at end desc,id
    limit greatest(1,least(100,page_size)) offset least(10000,greatest(0,page_number-1))*greatest(1,least(100,page_size))
  )
  select jsonb_build_object('total',(select count(*) from selected),'items',coalesce((
    select jsonb_agg(jsonb_build_object(
      'comment_id',c.id,'guestbook_key',c.guestbook_key,'message',c.message,'parent_comment_id',c.parent_id,'locale','en-US',
      'created',c.created_at,'modified',c.created_at,'is_owner',coalesce(c.author_id=auth.uid(),false),
      'replies_count',(select count(*) from public.app_comments r where r.parent_id=c.id and r.guestbook_key=c.guestbook_key),
      'flags',case when c.spoiler then jsonb_build_array('SPOILER') else '[]'::jsonb end,
      'user',jsonb_build_object('user_key',c.author_id,'user_attributes',jsonb_build_object('username',c.author_name))
    )||app_private.comment_votes(c.id)) from paged c),'[]'::jsonb));
$$;
