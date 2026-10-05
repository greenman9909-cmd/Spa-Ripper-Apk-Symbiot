create function public.limit_comment_posts() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
  -- Serialize inserts from the same authenticated account across requests.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.author_id::text,0));
  if (select count(*) from public.app_comments where author_id=new.author_id and created_at>now()-interval '1 minute')>=20 then
    raise exception 'Comment rate limit exceeded' using errcode='P0001';
  end if;
  return new;
end;
$$;
revoke all on function public.limit_comment_posts() from public,anon,authenticated;
create trigger app_comments_rate_limit before insert on public.app_comments
for each row execute function public.limit_comment_posts();
