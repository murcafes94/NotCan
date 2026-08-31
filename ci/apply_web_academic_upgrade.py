from pathlib import Path

css_path = Path('web/src/features.css')
css = css_path.read_text()
css = css.replace('var(--surface-2)', 'var(--panel-2)')
css = css.replace('var(--surface)', 'var(--panel)')
css_path.write_text(css)
