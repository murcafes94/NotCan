-- NotCan sync foundation
-- Run this file in a Supabase SQL editor for the first shared backend.
-- Client UUIDs are preserved so every Android device refers to the same records.
-- NotCan is app-only; Supabase remains the account and synchronization backend.

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

create index if not exists subjects_user_cycle_idx on public.subjects(user_id, cycle_id);
create index if not exists class_sessions_user_subject_idx on public.class_sessions(user_id, subject_id);
create index if not exists note_pages_user_class_idx on public.note_pages(user_id, class_session_id);
create index if not exists grade_items_user_subject_idx on public.grade_items(user_id, subject_id);
create index if not exists study_cycles_sync_idx on public.study_cycles(user_id, updated_at);
create index if not exists subjects_sync_idx on public.subjects(user_id, updated_at);
create index if not exists class_sessions_sync_idx on public.class_sessions(user_id, updated_at);
create index if not exists note_pages_sync_idx on public.note_pages(user_id, updated_at);
create index if not exists grade_items_sync_idx on public.grade_items(user_id, updated_at);

alter table public.study_cycles enable row level security;
alter table public.subjects enable row level security;
alter table public.class_sessions enable row level security;
alter table public.note_pages enable row level security;
alter table public.grade_items enable row level security;

create policy "study_cycles_owner_all" on public.study_cycles for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "subjects_owner_all" on public.subjects for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "class_sessions_owner_all" on public.class_sessions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "note_pages_owner_all" on public.note_pages for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "grade_items_owner_all" on public.grade_items for all using (auth.uid() = user_id) with check (auth.uid() = user_id);



-- App-only cross-device synchronization.
create table if not exists public.subject_schedules (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject_id uuid not null references public.subjects(id) on delete cascade,
  cycle_id uuid not null references public.study_cycles(id) on delete cascade,
  weekday_iso integer not null check (weekday_iso between 1 and 7),
  start_minute_of_day integer not null check (start_minute_of_day between 0 and 1439),
  end_minute_of_day integer not null check (end_minute_of_day between 1 and 1440),
  reminder_minutes_before integer not null default 1440,
  preview_minutes_before integer not null default 10,
  auto_stop_mode text not null default 'ASK',
  auto_stop_grace_minutes integer not null default 5,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

alter table public.class_sessions
  add column if not exists schedule_id uuid references public.subject_schedules(id) on delete set null,
  add column if not exists planned_start_epoch_ms bigint,
  add column if not exists planned_end_epoch_ms bigint;

create table if not exists public.transcripts (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  class_session_id uuid not null references public.class_sessions(id) on delete cascade,
  audio_id text,
  body text not null default '',
  status text not null default 'FINAL',
  model_name text,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.task_items (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  cycle_id uuid not null references public.study_cycles(id) on delete cascade,
  subject_id uuid references public.subjects(id) on delete set null,
  title text not null,
  type text not null default 'Tarea',
  due_at_epoch_ms bigint,
  priority text not null default 'Normal',
  notes text not null default '',
  is_completed boolean not null default false,
  revision bigint not null default 1,
  device_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists subject_schedules_sync_idx on public.subject_schedules(user_id, updated_at);
create index if not exists task_items_sync_idx on public.task_items(user_id, updated_at);
create index if not exists transcripts_sync_idx on public.transcripts(user_id, updated_at);

alter table public.subject_schedules enable row level security;
alter table public.transcripts enable row level security;
alter table public.task_items enable row level security;

create policy "subject_schedules_owner_all" on public.subject_schedules for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "transcripts_owner_all" on public.transcripts for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "task_items_owner_all" on public.task_items for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Audio and documents remain local-first until their metadata, quotas and backup/sync
-- policy are defined. This avoids accidentally uploading large recordings as part of
-- ordinary account synchronization.
