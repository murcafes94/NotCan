from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
text = path.read_text(encoding="utf-8")
current = '''    fun deleteClass(classId: String) {\n        if (_selectedClassId.value == classId) {\n            _selectedClassId.value = null\n            _selectedNoteId.value = null\n        }\n        viewModelScope.launch(Dispatchers.IO) {\n            val paths = repository.classFilePaths(classId)\n            paths.filterNot { it.startsWith("content://") }.forEach { path -> runCatching { File(path).delete() } }\n            repository.deleteClassData(classId)\n            paths.filter { it.startsWith("content://") }.forEach { path ->\n                if (repository.documentReferenceCount(path) == 0) releasePersistedDocumentUri(path)\n            }\n        }\n    }\n'''
legacy_target = '''    fun deleteClass(classId: String) {\n        if (_selectedClassId.value == classId) {\n            _selectedClassId.value = null\n            _selectedNoteId.value = null\n        }\n        viewModelScope.launch(Dispatchers.IO) {\n            repository.classFilePaths(classId).forEach { path -> runCatching { File(path).delete() } }\n            repository.deleteClassData(classId)\n        }\n    }\n'''
if current not in text:
    raise RuntimeError("Refined deleteClass block not found")
path.write_text(text.replace(current, legacy_target, 1), encoding="utf-8")
print("deleteClass aligned for finalizer")
