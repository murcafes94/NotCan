from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, content):
    (ROOT / path).write_text(content)

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing replacement: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Study maps: toolbar outside/above transformable canvas + unlimited text
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt')
s = read(p)

toolbar_block = '''        StudyMapToolbar(\n            map = map,\n            style = layoutStyle,\n            zoom = zoom,\n            onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest++ },\n            onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },\n            onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },\n            onFit = { fitRequest++ },\n            onCenter = { centerRequest++ },\n            onExport = ::exportAndShare\n        )\n'''

old_start = '''    Column(modifier.fillMaxSize()) {\n        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {\n'''
new_start = '''    Column(modifier.fillMaxSize()) {\n        // La barra vive fuera del lienzo transformable: mover/zoom nunca puede taparla.\n''' + toolbar_block + '''        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {\n'''
s = replace_once(s, old_start, new_start, 'map toolbar before canvas')

old_end = '''        }\n''' + toolbar_block + '''    }\n}\n\n@Composable\nprivate fun StudyMapToolbar'''
new_end = '''        }\n    }\n}\n\n@Composable\nprivate fun StudyMapToolbar'''
s = replace_once(s, old_end, new_end, 'remove toolbar below canvas')

s = s.replace('.widthIn(min = 54.dp, max = 118.dp),', '.widthIn(min = 54.dp, max = 220.dp),')
s = s.replace('''                                textAlign = TextAlign.Center,\n                                maxLines = 2,\n                                overflow = TextOverflow.Ellipsis,\n                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)\n''', '''                                textAlign = TextAlign.Center,\n                                softWrap = true,\n                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)\n''')
# Make node titles explicitly wrap without line limits.
s = s.replace('''                    fontWeight = if (node.level <= 1) FontWeight.SemiBold else FontWeight.Medium,\n                    modifier = Modifier.weight(1f)\n''', '''                    fontWeight = if (node.level <= 1) FontWeight.SemiBold else FontWeight.Medium,\n                    softWrap = true,\n                    modifier = Modifier.weight(1f)\n''')
write(p, s)

# Dynamic node height: no hard cap; long academic text remains visible and zoomable.
p = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapLayoutEngine.kt')
s = read(p)
s = replace_once(
    s,
    '''        val chars = node.title.length + (node.description?.length ?: 0)\n        val extra = (chars / 34).coerceAtMost(7) * 17f\n        val sourceExtra = if (node.sourceRefs.isNotEmpty()) 18f else 0f\n        return (base + extra + sourceExtra).coerceAtMost(220f)\n''',
    '''        val chars = node.title.length + (node.description?.length ?: 0)\n        // El mapa admite pan/zoom, así que el nodo crece todo lo necesario en vez de truncar.\n        val estimatedLines = (chars / 30).coerceAtLeast(0)\n        val extra = estimatedLines * 17f\n        val sourceExtra = if (node.sourceRefs.isNotEmpty()) 22f else 0f\n        return (base + extra + sourceExtra).coerceAtLeast(base)\n''',
    'unbounded node height'
)
write(p, s)

# ---------------------------------------------------------------------------
# 2) Inicio: dashboard real, distinct from Materias, with whole-app overview
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt')
s = read(p)

# Pass schedule/recording/time context to RootPage in both wide and phone branches.
old_args = '''                        tasks,\n                        cycle,\n                        subjects,\n                        { page = it },\n'''
new_args = '''                        tasks,\n                        cycle,\n                        subjects,\n                        schedules,\n                        recordingActive,\n                        now,\n                        { page = it },\n'''
if s.count(old_args) != 2:
    raise SystemExit(f'expected 2 RootPage call argument blocks, got {s.count(old_args)}')
s = s.replace(old_args, new_args)

s = replace_once(
    s,
    '''    tasks: List<TaskItemEntity>,\n    cycle: StudyCycleEntity?,\n    subjects: List<SubjectEntity>,\n    onNavigate: (Int) -> Unit,\n''',
    '''    tasks: List<TaskItemEntity>,\n    cycle: StudyCycleEntity?,\n    subjects: List<SubjectEntity>,\n    schedules: List<SubjectScheduleEntity>,\n    recordingActive: Boolean,\n    now: Long,\n    onNavigate: (Int) -> Unit,\n''',
    'RootPage signature summary context'
)
s = replace_once(
    s,
    '''            0 -> HomeDashboard(cycle, subjects, tasks, onNavigate)\n''',
    '''            0 -> HomeDashboard(cycle, subjects, schedules, tasks, recordingActive, now, onNavigate)\n''',
    'HomeDashboard invocation'
)

home_start = s.index('@Composable\nprivate fun HomeDashboard(')
home_end = s.index('\n@Composable\nprivate fun DashboardCard(', home_start)
new_home = r'''@Composable
private fun HomeDashboard(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    tasks: List<TaskItemEntity>,
    recordingActive: Boolean,
    now: Long,
    onNavigate: (Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val todayClasses = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules, zone)
    val nextClass = AcademicSchedule.nextOccurrence(now, cycle, subjects, schedules, zone, horizonDays = 8)
    val pending = tasks.filterNot { it.isCompleted }.sortedWith(
        compareBy<TaskItemEntity> {
            when (it.priority.lowercase()) {
                "alta" -> 0
                "normal" -> 1
                else -> 2
            }
        }.thenBy { it.dueAtEpochMs ?: Long.MAX_VALUE }
    )
    val completed = tasks.count { it.isCompleted }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Inicio",
                        color = NotCanOffWhite,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(cycle?.name ?: "Tu espacio académico en NotCan", color = NotCanGray)
                }
                Surface(
                    color = if (recordingActive) NotCanBlue.copy(alpha = 0.18f) else NotCanSurface,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        if (recordingActive) "Clase en curso" else "Todo listo",
                        color = if (recordingActive) NotCanBlue else NotCanGray,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Materias", subjects.size.toString(), NotCanIcons.Subjects, Modifier.weight(1f)) { onNavigate(1) }
                DashboardCard("Pendientes", pending.size.toString(), NotCanIcons.Tasks, Modifier.weight(1f)) { onNavigate(2) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Clases hoy", todayClasses.size.toString(), NotCanIcons.Calendar, Modifier.weight(1f)) { onNavigate(3) }
                DashboardCard("Horarios", schedules.size.toString(), NotCanIcons.Schedule, Modifier.weight(1f)) { onNavigate(3) }
            }
        }

        item {
            Card(
                onClick = { onNavigate(3) },
                colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(NotCanIcons.Calendar, null, tint = NotCanBlue)
                        Spacer(Modifier.width(9.dp))
                        Text("Próxima clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    }
                    if (nextClass == null) {
                        Text("No hay otra clase programada en los próximos días.", color = NotCanGray)
                    } else {
                        Text(nextClass.subject.name, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium)
                        val dateLabel = if (nextClass.date == today) "Hoy" else nextClass.date.format(DateTimeFormatter.ofPattern("EEE dd/MM"))
                        Text(
                            "$dateLabel · ${AcademicSchedule.formatMinutes(nextClass.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(nextClass.schedule.endMinuteOfDay)}",
                            color = NotCanGray
                        )
                    }
                }
            }
        }

        item {
            Text("Resumen académico", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Calificaciones", "Revisar", NotCanIcons.Grades, Modifier.weight(1f)) { onNavigate(4) }
                DashboardCard("TuNot", "Abrir", NotCanIcons.TuNot, Modifier.weight(1f)) { onNavigate(5) }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tareas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("${pending.size} pendientes · $completed completadas", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onNavigate(2) }) { Text("Ver todas") }
                }
            }
        }

        item { Text("Pendientes prioritarios", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold) }
        if (pending.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                    Text("No tienes tareas pendientes registradas.", color = NotCanGray, modifier = Modifier.padding(15.dp))
                }
            }
        } else {
            items(pending.take(5), key = { it.id }) { task ->
                Card(
                    onClick = { onNavigate(2) },
                    colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(task.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (task.priority.equals("Alta", ignoreCase = true)) {
                                Text("Alta", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val due = task.dueAtEpochMs?.let {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                        }
                        Text(
                            listOfNotNull(
                                subjects.firstOrNull { it.id == task.subjectId }?.name,
                                task.type,
                                due?.let { "Entrega $it" }
                            ).joinToString(" · "),
                            color = NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text("Accesos rápidos", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { onNavigate(1) }) { Text("Materias") }
                Button(onClick = { onNavigate(2) }) { Text("Tareas") }
                TextButton(onClick = { onNavigate(3) }) { Text("Calendario") }
                TextButton(onClick = { onNavigate(4) }) { Text("Calificaciones") }
                TextButton(onClick = { onNavigate(5) }) { Text("TuNot") }
            }
        }
    }
}
'''
s = s[:home_start] + new_home + s[home_end:]

# Home uses horizontalScroll/rememberScrollState.
if 'import androidx.compose.foundation.horizontalScroll' not in s:
    s = s.replace('import androidx.compose.foundation.layout.Arrangement\n', 'import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.layout.Arrangement\n')
if 'import androidx.compose.foundation.rememberScrollState' not in s:
    s = s.replace('import androidx.compose.foundation.lazy.items\n', 'import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.rememberScrollState\n')

write(p, s)

# ---------------------------------------------------------------------------
# 3) Version bump
# ---------------------------------------------------------------------------
p = Path('app/build.gradle.kts')
s = read(p)
s = replace_once(s, 'versionCode = 20', 'versionCode = 21', 'versionCode')
s = replace_once(s, 'versionName = "0.8.3"', 'versionName = "0.8.4"', 'versionName')
write(p, s)

print('Android 0.8.4 home/map patch applied')
