from pathlib import Path

ROOT = Path('.')

def read(path): return (ROOT / path).read_text()
def write(path, text): (ROOT / path).write_text(text)
def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing marker: {label}')
    return text.replace(old, new, 1)

# App.tsx
p = Path('web/src/App.tsx')
s = read(p)

s = replace_once(s,
"""  type MouseEvent as ReactMouseEvent,\n  useEffect,""",
"""  type MouseEvent as ReactMouseEvent,\n  type PointerEvent as ReactPointerEvent,\n  type WheelEvent as ReactWheelEvent,\n  useEffect,""",
'import pointer types')

s = replace_once(s,
"""  const [page, setPage] = useState<Page>('home')\n  const [search, setSearch] = useState('')""",
"""  const [page, setPage] = useState<Page>('home')\n  const [sidebarOpen, setSidebarOpen] = useState(false)\n  const [search, setSearch] = useState('')""",
'sidebar state')

s = replace_once(s,
"""  const [mapTitle, setMapTitle] = useState('Mi mapa conceptual')\n  const [mapNodes, setMapNodes] = useState(['Idea central', 'Concepto 1', 'Concepto 2'])""",
"""  const [mapTitle, setMapTitle] = useState('Mi mapa conceptual')\n  const [mapNodes, setMapNodes] = useState(['Idea central', 'Concepto 1', 'Concepto 2'])\n  const [mapNodeDraft, setMapNodeDraft] = useState('')\n  const [mapZoom, setMapZoom] = useState(1)\n  const [mapPan, setMapPan] = useState({ x: 0, y: 0 })\n  const mapDragRef = useRef<{ pointerId: number; startX: number; startY: number; panX: number; panY: number } | null>(null)""",
'map interaction state')

s = replace_once(s,
"""  useEffect(() => {\n    localStorage.setItem('notcan-files-meta', JSON.stringify(localFiles))\n  }, [localFiles])\n\n  useEffect(() => {\n    if (!supabase) return""",
"""  useEffect(() => {\n    localStorage.setItem('notcan-files-meta', JSON.stringify(localFiles))\n  }, [localFiles])\n\n  useEffect(() => {\n    if (page !== 'home') setSidebarOpen(false)\n  }, [page])\n\n  useEffect(() => {\n    if (!supabase) return""",
'close sidebar on page navigation')

s = replace_once(s,
"""  function navigate(next: Page) {\n    setEditorOpen(false)\n    setPage(next)\n    setSearch('')\n  }""",
"""  function navigate(next: Page) {\n    setEditorOpen(false)\n    setPage(next)\n    setSidebarOpen(false)\n    setSearch('')\n  }""",
'navigate sidebar close')

marker = """  const navGroups: { label: string; items: { page: Page; icon: string; label: string }[] }[] = ["""
helpers = """  function addMapNode() {\n    const value = mapNodeDraft.trim()\n    if (!value) return\n    setMapNodes((prev) => [...prev, value])\n    setMapNodeDraft('')\n  }\n\n  function handleMapPointerDown(event: ReactPointerEvent<HTMLDivElement>) {\n    const target = event.target as HTMLElement\n    if (target.closest('button,input,textarea')) return\n    mapDragRef.current = {\n      pointerId: event.pointerId,\n      startX: event.clientX,\n      startY: event.clientY,\n      panX: mapPan.x,\n      panY: mapPan.y,\n    }\n    event.currentTarget.setPointerCapture(event.pointerId)\n  }\n\n  function handleMapPointerMove(event: ReactPointerEvent<HTMLDivElement>) {\n    const drag = mapDragRef.current\n    if (!drag || drag.pointerId !== event.pointerId) return\n    setMapPan({\n      x: drag.panX + event.clientX - drag.startX,\n      y: drag.panY + event.clientY - drag.startY,\n    })\n  }\n\n  function handleMapPointerEnd(event: ReactPointerEvent<HTMLDivElement>) {\n    if (mapDragRef.current?.pointerId === event.pointerId) mapDragRef.current = null\n    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)\n  }\n\n  function handleMapWheel(event: ReactWheelEvent<HTMLDivElement>) {\n    event.preventDefault()\n    const next = mapZoom * (event.deltaY > 0 ? 0.9 : 1.1)\n    setMapZoom(Math.min(2.8, Math.max(0.45, next)))\n  }\n\n  function resetMapView() {\n    setMapZoom(1)\n    setMapPan({ x: 0, y: 0 })\n  }\n\n"""
if marker not in s: raise SystemExit('missing navGroups marker')
s = s.replace(marker, helpers + marker, 1)

old_map = """      {page === 'maps' && <section className=\"section-card map-workspace\">\n        <input className=\"map-title-input\" value={mapTitle} onChange={(event) => setMapTitle(event.target.value)} />\n        <div className=\"map-canvas\"><div className=\"map-node root\">{mapNodes[0]}</div>{mapNodes.slice(1).map((node, index) => <div className={`map-node child child-${index}`} key={`${node}-${index}`}>{node}</div>)}</div>\n        <div className=\"map-controls\"><input placeholder=\"Nuevo concepto\" onKeyDown={(event) => { if (event.key === 'Enter') { const value = event.currentTarget.value.trim(); if (value) { setMapNodes((prev) => [...prev, value]); event.currentTarget.value = '' } } }} /><button onClick={() => setMapNodes((prev) => [...prev, `Concepto ${prev.length}`])}>＋ Nodo</button><button onClick={() => { setAiPrompt('Crea un mapa conceptual a partir de mis apuntes recientes.'); setAiMode('concept-map'); navigate('ai') }}>✦ Generar con IA</button></div>\n      </section>}"""
new_map = """      {page === 'maps' && <section className=\"section-card map-workspace\">\n        <input className=\"map-title-input\" value={mapTitle} onChange={(event) => setMapTitle(event.target.value)} />\n\n        <div className=\"map-toolbar\">\n          <div className=\"map-toolbar-group\">\n            <button onClick={() => setMapZoom((value) => Math.max(0.45, value / 1.15))} aria-label=\"Alejar mapa\">−</button>\n            <span>{Math.round(mapZoom * 100)}%</span>\n            <button onClick={() => setMapZoom((value) => Math.min(2.8, value * 1.15))} aria-label=\"Acercar mapa\">＋</button>\n            <button onClick={resetMapView}>Centrar</button>\n          </div>\n          <small>Arrastra para mover · rueda o pellizco del navegador para acercar y alejar</small>\n        </div>\n\n        <div\n          className=\"map-stage\"\n          onPointerDown={handleMapPointerDown}\n          onPointerMove={handleMapPointerMove}\n          onPointerUp={handleMapPointerEnd}\n          onPointerCancel={handleMapPointerEnd}\n          onWheel={handleMapWheel}\n        >\n          <div\n            className=\"map-surface\"\n            style={{ transform: `translate(${mapPan.x}px, ${mapPan.y}px) scale(${mapZoom})` }}\n          >\n            <div className=\"map-node root\">{mapNodes[0]}</div>\n            {mapNodes.slice(1).map((node, index) => {\n              const total = Math.max(1, mapNodes.length - 1)\n              const angle = (Math.PI * 2 * index) / total - Math.PI / 2\n              const radiusX = total > 8 ? 39 : 33\n              const radiusY = total > 8 ? 37 : 31\n              return <div\n                className=\"map-node child\"\n                key={`${node}-${index}`}\n                style={{\n                  left: `${50 + Math.cos(angle) * radiusX}%`,\n                  top: `${50 + Math.sin(angle) * radiusY}%`,\n                }}\n              >{node}</div>\n            })}\n          </div>\n        </div>\n\n        <div className=\"map-node-composer\">\n          <textarea\n            value={mapNodeDraft}\n            onChange={(event) => setMapNodeDraft(event.target.value)}\n            placeholder=\"Escribe un concepto, explicación o fragmento largo…\"\n            onKeyDown={(event) => {\n              if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {\n                event.preventDefault()\n                addMapNode()\n              }\n            }}\n          />\n          <button onClick={addMapNode}>＋ Nodo</button>\n          <button onClick={() => { setAiPrompt('Crea un mapa conceptual a partir de mis apuntes recientes.'); setAiMode('concept-map'); navigate('ai') }}>✦ Generar con IA</button>\n        </div>\n        <small className=\"map-composer-hint\">El texto de cada nodo se muestra completo. Ctrl/⌘ + Enter añade el nodo.</small>\n      </section>}"""
s = replace_once(s, old_map, new_map, 'map workspace')

s = replace_once(s,
"""  return <div className=\"app-shell\">\n    <aside className=\"sidebar\">""",
"""  return <div className={`app-shell ${page === 'home' ? 'sidebar-home' : sidebarOpen ? 'sidebar-open' : 'sidebar-hidden'}`}>\n    <aside className=\"sidebar\">""",
'app shell state')

s = replace_once(s,
"""    </aside>\n\n    <div className=\"page-area\">\n      <header className=\"global-topbar\">\n        <div className=\"search-wrap\">""",
"""    </aside>\n    {page !== 'home' && sidebarOpen && <button className=\"sidebar-scrim\" aria-label=\"Cerrar navegación\" onClick={() => setSidebarOpen(false)} />}\n\n    <div className=\"page-area\">\n      <header className=\"global-topbar\">\n        {page !== 'home' && <button className=\"sidebar-toggle\" aria-label=\"Abrir navegación\" onClick={() => setSidebarOpen(true)}>☰</button>}\n        <div className=\"search-wrap\">""",
'sidebar toggle')

write(p, s)

# features.css: append map interaction overrides
p = Path('web/src/features.css')
s = read(p)
if '/* map-workspace-v2 */' not in s:
    s += r'''

/* map-workspace-v2 */
.map-workspace{display:grid;gap:14px;padding:18px;overflow:visible}
.map-title-input{margin:0}
.map-toolbar{position:relative;z-index:3;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid #29364e;border-radius:12px;background:#111a27}
.map-toolbar-group{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.map-toolbar button{min-height:38px;border:1px solid #33415b;border-radius:9px;background:#172131;color:#dbe4f5;padding:7px 11px;cursor:pointer}
.map-toolbar span{min-width:54px;text-align:center;color:#aeb9ce;font-variant-numeric:tabular-nums}
.map-toolbar small{color:#7f8ca3;text-align:right}
.map-stage{height:min(62vh,620px);min-height:430px;position:relative;overflow:hidden;isolation:isolate;border:1px solid #27344a;border-radius:14px;background:radial-gradient(circle at 50% 45%,rgba(101,91,200,.14),transparent 30%),linear-gradient(#ffffff06 1px,transparent 1px),linear-gradient(90deg,#ffffff06 1px,transparent 1px),#0c131e;background-size:auto,32px 32px,32px 32px;cursor:grab;touch-action:none;user-select:none}
.map-stage:active{cursor:grabbing}
.map-surface{position:absolute;left:50%;top:50%;width:1600px;height:1000px;margin-left:-800px;margin-top:-500px;transform-origin:50% 50%;will-change:transform}
.map-node{position:absolute;min-width:170px;max-width:380px;width:max-content;height:auto;padding:13px 17px;border-radius:13px;border:1px solid #5265bb;background:#18254b;box-shadow:0 10px 26px rgba(0,0,0,.25);white-space:normal;overflow:visible;text-overflow:clip;line-height:1.45;word-break:break-word;overflow-wrap:anywhere;user-select:text;cursor:text}
.map-node.root{left:50%;top:50%;transform:translate(-50%,-50%);font-weight:700;max-width:430px;background:#1d2b59}
.map-node.child{transform:translate(-50%,-50%);background:#151f31;border-color:#354562}
.map-node-composer{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:8px;align-items:stretch}
.map-node-composer textarea{min-height:76px;resize:vertical;border:1px solid #2b3850;border-radius:10px;background:#0e1622;color:#eef3ff;padding:11px 12px;outline:0;line-height:1.45}
.map-node-composer button{border:1px solid #2d3a52;background:#151f2e;border-radius:9px;color:#c3cde0;padding:10px 12px;cursor:pointer}
.map-composer-hint{color:#7f8ca3;margin-top:-7px}
@media(max-width:720px){.map-toolbar{align-items:flex-start;flex-direction:column}.map-toolbar small{text-align:left}.map-stage{min-height:380px;height:54vh}.map-node{min-width:150px;max-width:300px}.map-node-composer{grid-template-columns:1fr 1fr}.map-node-composer textarea{grid-column:1/-1}.map-node-composer button{min-height:44px}}
'''
write(p, s)

# tablet-responsive.css: sidebar overlay rules after existing responsive rules
p = Path('web/src/tablet-responsive.css')
s = read(p)
if '/* collapsible-navigation-v2 */' not in s:
    s += r'''

/* collapsible-navigation-v2 */
.app-shell.sidebar-hidden,.app-shell.sidebar-open{grid-template-columns:1fr}
.app-shell.sidebar-hidden .sidebar,.app-shell.sidebar-open .sidebar{position:fixed;inset:0 auto 0 0;width:268px;height:100vh;z-index:70;transform:translateX(-102%);transition:transform .2s ease;box-shadow:none}
.app-shell.sidebar-open .sidebar{transform:translateX(0);box-shadow:22px 0 55px rgba(0,0,0,.4)}
.sidebar-scrim{position:fixed;inset:0;z-index:60;border:0;background:rgba(2,6,12,.58);backdrop-filter:blur(2px);cursor:default}
.sidebar-toggle{width:44px;height:44px;flex:0 0 auto;display:grid;place-items:center;border:1px solid #26344b;border-radius:11px;background:#111a27;color:#dbe4f5;font-size:21px;cursor:pointer}
@media(max-width:1100px){
  .app-shell.sidebar-hidden,.app-shell.sidebar-open{grid-template-columns:1fr}
  .app-shell.sidebar-hidden .sidebar,.app-shell.sidebar-open .sidebar{width:268px;padding:22px 16px;align-items:stretch}
  .app-shell.sidebar-open .brand-row{justify-content:flex-start;padding:2px 6px 20px}
  .app-shell.sidebar-open .brand-row>div:last-child{display:flex}
  .app-shell.sidebar-open .nav-group{padding:12px 0;border-top:1px solid #192230}
  .app-shell.sidebar-open .nav-group>p{display:block}
  .app-shell.sidebar-open .nav-item{min-height:0;justify-content:flex-start;padding:11px 12px;gap:12px;font-size:inherit}
  .app-shell.sidebar-open .nav-item>span{width:20px;font-size:16px}
  .app-shell.sidebar-open .sidebar-bottom .nav-item .dot{position:static;margin-left:auto}
}
@media(max-width:720px){
  .app-shell.sidebar-hidden .sidebar,.app-shell.sidebar-open .sidebar{top:0;left:0;right:auto;bottom:auto;width:min(84vw,300px);height:100vh;padding:22px 16px;display:flex;flex-direction:column;align-items:stretch;border-right:1px solid #1d2635;border-top:0}
  .app-shell.sidebar-open .brand-row,.app-shell.sidebar-open .nav-group,.app-shell.sidebar-open .sidebar-bottom{display:flex}
  .app-shell.sidebar-open .nav-group{display:grid;width:auto;grid-template-columns:1fr}
  .app-shell.sidebar-open .nav-group:first-of-type{display:grid;grid-template-columns:1fr;width:auto}
  .app-shell.sidebar-hidden .page-area,.app-shell.sidebar-open .page-area{padding-bottom:0}
  .sidebar-toggle{width:42px;height:42px}
}
'''
write(p, s)

print('web map/sidebar patch applied')
