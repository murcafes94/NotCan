type StoredFile = {
  key: string
  name: string
  size: number
  type: string
  lastModified: number
  storedAt: number
  blob: Blob
}

const DB_NAME = 'notcan-web-files'
const DB_VERSION = 1
const STORE = 'files'

function fileKey(file: File) {
  return `${file.name}::${file.size}::${file.lastModified}`
}

function openFileDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = () => {
      const database = request.result
      const store = database.objectStoreNames.contains(STORE)
        ? request.transaction!.objectStore(STORE)
        : database.createObjectStore(STORE, { keyPath: 'key' })
      if (!store.indexNames.contains('name')) store.createIndex('name', 'name', { unique: false })
      if (!store.indexNames.contains('storedAt')) store.createIndex('storedAt', 'storedAt', { unique: false })
    }

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('No se pudo abrir el almacenamiento de archivos.'))
  })
}

async function withStore<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>) {
  const database = await openFileDb()
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(STORE, mode)
      const request = work(transaction.objectStore(STORE))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error || new Error('Error de IndexedDB.'))
      transaction.onerror = () => reject(transaction.error || new Error('Error de almacenamiento.'))
    })
  } finally {
    database.close()
  }
}

async function storeFile(file: File) {
  const record: StoredFile = {
    key: fileKey(file),
    name: file.name,
    size: file.size,
    type: file.type || 'application/octet-stream',
    lastModified: file.lastModified,
    storedAt: Date.now(),
    blob: file,
  }
  await withStore('readwrite', (store) => store.put(record))
}

async function filesByName(name: string): Promise<StoredFile[]> {
  const database = await openFileDb()
  try {
    return await new Promise<StoredFile[]>((resolve, reject) => {
      const transaction = database.transaction(STORE, 'readonly')
      const request = transaction.objectStore(STORE).index('name').getAll(name)
      request.onsuccess = () => resolve((request.result as StoredFile[]).sort((a, b) => b.storedAt - a.storedAt))
      request.onerror = () => reject(request.error || new Error('No se pudo leer el archivo.'))
    })
  } finally {
    database.close()
  }
}

async function deleteFilesByName(name: string) {
  const database = await openFileDb()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(STORE, 'readwrite')
      const index = transaction.objectStore(STORE).index('name')
      const cursorRequest = index.openCursor(IDBKeyRange.only(name))
      cursorRequest.onsuccess = () => {
        const cursor = cursorRequest.result
        if (!cursor) return
        cursor.delete()
        cursor.continue()
      }
      cursorRequest.onerror = () => reject(cursorRequest.error || new Error('No se pudo eliminar el archivo.'))
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error || new Error('No se pudo eliminar el archivo.'))
    })
  } finally {
    database.close()
  }
}

async function persistInputFiles(input: HTMLInputElement) {
  const files = Array.from(input.files || [])
  if (!files.length) return
  await Promise.all(files.map(storeFile))
}

function fileNameFromTile(tile: HTMLElement) {
  return tile.querySelector('strong')?.textContent?.trim() || ''
}

document.addEventListener('change', (event) => {
  const input = event.target
  if (!(input instanceof HTMLInputElement) || input.type !== 'file' || !input.files?.length) return
  void persistInputFiles(input).catch((error) => console.warn('NotCan: no se pudo guardar el archivo local.', error))
}, true)

document.addEventListener('click', (event) => {
  const target = event.target
  if (!(target instanceof Element)) return
  const tile = target.closest<HTMLElement>('.file-tile')
  if (!tile) return

  const name = fileNameFromTile(tile)
  if (!name) return

  const deleteButton = target.closest('button')
  if (deleteButton) {
    if (deleteButton.textContent?.toLocaleLowerCase('es').includes('eliminar')) {
      void deleteFilesByName(name).catch((error) => console.warn('NotCan: no se pudo borrar el archivo local.', error))
    }
    return
  }

  const viewer = window.open('about:blank', '_blank')
  void filesByName(name).then((matches) => {
    const record = matches[0]
    if (!record) {
      viewer?.close()
      window.alert('El archivo todavía no está disponible en este dispositivo.')
      return
    }

    const url = URL.createObjectURL(record.blob)
    if (viewer) viewer.location.href = url
    else window.location.href = url
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }).catch((error) => {
    viewer?.close()
    console.warn('NotCan: no se pudo abrir el archivo local.', error)
  })
})
