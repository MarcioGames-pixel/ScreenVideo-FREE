-- ScreenVideo Hub public video storage.
-- Run in Supabase SQL Editor for a NEW project only after reviewing the risks.
-- Anonymous uploads are open to the public; set monitoring and abuse controls.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'videos',
  'videos',
  true,
  104857600,
  array['video/mp4', 'video/webm', 'video/quicktime']
)
on conflict (id) do update set
  public = true,
  file_size_limit = 104857600,
  allowed_mime_types = array['video/mp4', 'video/webm', 'video/quicktime'];

drop policy if exists "ScreenVideo anonymous upload" on storage.objects;
create policy "ScreenVideo anonymous upload"
on storage.objects
for insert
to anon
with check (bucket_id = 'videos');

drop policy if exists "ScreenVideo public list" on storage.objects;
create policy "ScreenVideo public list"
on storage.objects
for select
to anon
using (bucket_id = 'videos');

-- No anonymous UPDATE or DELETE policy is created.
-- This still permits public uploads; anyone with the public site and anon key
-- may upload files up to the bucket limit. For a production community site,
-- add authentication, quotas, moderation, and abuse prevention.