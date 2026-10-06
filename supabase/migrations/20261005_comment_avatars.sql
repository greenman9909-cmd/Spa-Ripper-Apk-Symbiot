-- Return profile artwork through the retained comment avatar image model.
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
      'comment_id',c.native_id::text,'guestbook_key',c.guestbook_key,'message',c.message,'parent_comment_id',(select p.native_id::text from public.app_comments p where p.id=c.parent_id),'locale','en-US',
      'created',c.created_at,'modified',c.created_at,'is_owner',coalesce(c.author_id=auth.uid(),false),
      'replies_count',(select count(*) from public.app_comments r where r.parent_id=c.id and r.guestbook_key=c.guestbook_key),
      'flags',case when c.spoiler then jsonb_build_array('SPOILER') else '[]'::jsonb end,
      'user',jsonb_build_object('user_key',c.author_id,'user_attributes',jsonb_build_object('username',c.author_name,'avatar',jsonb_build_object('locked','[]'::jsonb,'unlocked',jsonb_build_array(jsonb_build_object('source','https://ani.pm/api/anime/cover?anilistId='||case when c.avatar_id='default.png' then '21' else substring(c.avatar_id from 11) end,'width',128,'height',128)))))
    )||app_private.comment_votes(c.id)) from paged c),'[]'::jsonb));
$$;
