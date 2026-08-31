-- NotCan sync foundation
-- Client UUIDs are preserved so Android Room and NotCan Web refer to the same records.
-- Supabase stores structured data; large binary files are local-first and can be backed by Cloudflare R2.

create extension if not exists pgcrypto;

create table if not exists public.study_cycles (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  is_active boolean not null default true,
  start_epoch_day bigint not null default 0,
  end_epoch_day bigint not null default 0,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.subjects (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  cycle_id uuid not null references public.study_cycles(id) on delete cascade,
  name text not null,
  color_hex text,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.class_sessions (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject_id uuid not null references public.subjects(id) on delete cascade,
  title text not null,
  started_at_epoch_ms bigint not null,
  ended_at_epoch_ms bigint,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.note_pages (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  class_session_id uuid not null references public.class_sessions(id) on delete cascade,
  title text not null,
  body text not null default '',
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.grade_items (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject_id uuid not null references public.subjects(id) on delete cascade,
  title text not null,
  score double precision not null,
  max_score double precision not null,
  weight_percent double precision not null,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- Metadata for documents, images, backups and optional audio.
-- The binary never lives in this table. `local` means device-only; `r2` points to Cloudflare R2.
create table if not exists public.file_assets (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject_id uuid references public.subjects(id) on delete set null,
  class_session_id uuid references public.class_sessions(id) on delete set null,
  name text not null,
  mime_type text not null default 'application/octet-stream',
  size_bytes bigint not null default 0 check (size_bytes >= 0),
  storage_provider text not null default 'local' check (storage_provider in ('local', 'r2')),
  object_key text,
  sha256 text,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint file_assets_remote_key check (storage_provider = 'local' or object_key is not null)
);

create index if not exists subjects_user_cycle_idx on public.subjects(user_id, cycle_id);
create index if not exists class_sessions_user_subject_idx on public.class_sessions(user_id, subject_id);
create index if not exists note_pages_user_class_idx on public.note_pages(user_id, class_session_id);
create index if not exists grade_items_user_subject_idx on public.grade_items(user_id, subject_id);
create index if not exists study_cycles_sync_idx on public.study_cycles(user_id, updated_at);
create index if not exists subjects_sync_idx on public.subjects(user_id, updated_at);
create index if not exists class_sessions_sync_idx on public.class_sessions(user_id, updated_at);
create index if not exists note_pages_sync_idx on public.note_pages(user_id, updated_at);
create index if not exists grade_items_sync_idx on public.grade_items(user_id, updated_at);
create index if not exists file_assets_user_updated_idx on public.file_assets(user_id, updated_at);
create index if not exists file_assets_user_subject_idx on public.file_assets(user_id, subject_id);
create index if not exists file_assets_subject_id_idx on public.file_assets(subject_id);
create index if not exists file_assets_class_session_id_idx on public.file_assets(class_session_id);

alter table public.study_cycles enable row level security;
alter table public.subjects enable row level security;
alter table public.class_sessions enable row level security;
alter table public.note_pages enable row level security;
alter table public.grade_items enable row level security;
alter table public.file_assets enable row level security;

create policy "study_cycles_owner_all" on public.study_cycles for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "subjects_owner_all" on public.subjects for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "class_sessions_owner_all" on public.class_sessions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "note_pages_owner_all" on public.note_pages for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "grade_items_owner_all" on public.grade_items for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "file_assets_owner_all" on public.file_assets
for all to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

revoke all on table public.file_assets from anon;
grant select, insert, update, delete on table public.file_assets to authenticated;

-- Large-file policy:
-- 1. imported files are persisted locally in IndexedDB first;
-- 2. Supabase keeps only structured metadata and sync state;
-- 3. Cloudflare R2 is the intended remote store for PDF/DOCX/EPUB/images/backups/audio;
-- 4. recordings are never uploaded automatically without an explicit backup/sync action.
