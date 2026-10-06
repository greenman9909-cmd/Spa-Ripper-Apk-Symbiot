-- Public replacement-app comments; account snapshots remain private.
create table public.app_comments (
  id uuid primary key default gen_random_uuid(),
  guestbook_key text not null check (guestbook_key ~ '^ANI[0-9]+(E[0-9]+D?)?$'),
  author_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  author_name text not null check (char_length(btrim(author_name)) between 1 and 32),
  avatar_id text not null default 'default.png' check (avatar_id = 'default.png' or avatar_id ~ '^ani-cover-[0-9]{1,9}$'),
  message text not null check (char_length(btrim(message)) between 1 and 2000),
  parent_id uuid,
  spoiler boolean not null default false,
  created_at timestamptz not null default now(),
  unique (guestbook_key,id),
  foreign key (guestbook_key,parent_id) references public.app_comments(guestbook_key,id) on delete cascade
);
create index app_comments_book_page on public.app_comments(guestbook_key,parent_id,created_at desc,id);
create index app_comments_author_page on public.app_comments(author_id,created_at);
alter table public.app_comments enable row level security;
revoke all on public.app_comments from anon,authenticated;
grant select on public.app_comments to anon,authenticated;
grant insert (guestbook_key,author_name,avatar_id,message,parent_id,spoiler),delete on public.app_comments to authenticated;
grant update (spoiler) on public.app_comments to authenticated;
create policy comments_read on public.app_comments for select to anon,authenticated using (true);
create policy comments_insert on public.app_comments for insert to authenticated with check ((select auth.uid())=author_id);
create policy comments_update on public.app_comments for update to authenticated using ((select auth.uid())=author_id) with check ((select auth.uid())=author_id);
create policy comments_delete on public.app_comments for delete to authenticated using ((select auth.uid())=author_id);

create function public.comment_feed(book text,parent uuid default null,page_number int default 1,page_size int default 50,ascending_order boolean default false,comment_uuid uuid default null)
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
      'flags',case when c.spoiler then jsonb_build_array('SPOILER') else '[]'::jsonb end,'user_votes','[]'::jsonb,
      'votes',jsonb_build_object('like',0,'spoiler',0,'inappropriate',0),
      'user',jsonb_build_object('user_key',c.author_id,'user_attributes',jsonb_build_object('username',c.author_name))
    )) from paged c),'[]'::jsonb));
$$;
revoke all on function public.comment_feed(text,uuid,int,int,boolean,uuid) from public;
grant execute on function public.comment_feed(text,uuid,int,int,boolean,uuid) to anon,authenticated;

create function public.comment_book(book text) returns jsonb language sql stable security invoker set search_path='' as $$
  select jsonb_build_object('guestbook_key',book,'total_comments',count(*)) from public.app_comments where guestbook_key=book;
$$;
revoke all on function public.comment_book(text) from public;
grant execute on function public.comment_book(text) to anon,authenticated;
