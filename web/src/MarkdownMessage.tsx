import type { ReactNode } from 'react'

function inlineNodes(text: string): ReactNode[] {
  const parts: ReactNode[] = []
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g
  let last = 0
  let match: RegExpExecArray | null
  let key = 0
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index))
    const token = match[0]
    if (token.startsWith('**')) parts.push(<strong key={key++}>{token.slice(2, -2)}</strong>)
    else if (token.startsWith('`')) parts.push(<code key={key++}>{token.slice(1, -1)}</code>)
    else parts.push(<em key={key++}>{token.slice(1, -1)}</em>)
    last = match.index + token.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return parts
}

function isTableSeparator(line: string) {
  const cells = line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())
  return cells.length > 1 && cells.every((cell) => /^:?-{3,}:?$/.test(cell))
}

function tableCells(line: string) {
  return line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())
}

export default function MarkdownMessage({ text }: { text: string }) {
  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  const nodes: ReactNode[] = []
  let i = 0

  while (i < lines.length) {
    const raw = lines[i]
    const line = raw.trim()

    if (!line) {
      i += 1
      continue
    }

    if (i + 1 < lines.length && raw.includes('|') && isTableSeparator(lines[i + 1])) {
      const header = tableCells(raw)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && lines[i].includes('|') && lines[i].trim()) {
        rows.push(tableCells(lines[i]))
        i += 1
      }
      nodes.push(
        <div className="tunot-table-wrap" key={`table-${i}`}>
          <table className="tunot-markdown-table">
            <thead><tr>{header.map((cell, index) => <th key={index}>{inlineNodes(cell)}</th>)}</tr></thead>
            <tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{header.map((_, cellIndex) => <td key={cellIndex}>{inlineNodes(row[cellIndex] ?? '')}</td>)}</tr>)}</tbody>
          </table>
        </div>,
      )
      continue
    }

    const heading = /^(#{1,4})\s+(.+)$/.exec(line)
    if (heading) {
      const level = heading[1].length
      const content = inlineNodes(heading[2])
      if (level === 1) nodes.push(<h2 key={`h-${i}`}>{content}</h2>)
      else if (level === 2) nodes.push(<h3 key={`h-${i}`}>{content}</h3>)
      else nodes.push(<h4 key={`h-${i}`}>{content}</h4>)
      i += 1
      continue
    }

    if (/^(-{3,}|\*{3,})$/.test(line)) {
      nodes.push(<hr key={`hr-${i}`} />)
      i += 1
      continue
    }

    if (/^>\s?/.test(line)) {
      const quote: string[] = []
      while (i < lines.length && /^>\s?/.test(lines[i].trim())) {
        quote.push(lines[i].trim().replace(/^>\s?/, ''))
        i += 1
      }
      nodes.push(<blockquote key={`q-${i}`}>{inlineNodes(quote.join(' '))}</blockquote>)
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''))
        i += 1
      }
      nodes.push(<ul key={`ul-${i}`}>{items.map((item, index) => <li key={index}>{inlineNodes(item)}</li>)}</ul>)
      continue
    }

    if (/^\d+[.)]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\d+[.)]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+[.)]\s+/, ''))
        i += 1
      }
      nodes.push(<ol key={`ol-${i}`}>{items.map((item, index) => <li key={index}>{inlineNodes(item)}</li>)}</ol>)
      continue
    }

    const paragraph: string[] = [line]
    i += 1
    while (i < lines.length) {
      const next = lines[i].trim()
      if (!next || /^(#{1,4})\s+/.test(next) || /^[-*]\s+/.test(next) || /^\d+[.)]\s+/.test(next) || /^>\s?/.test(next) || /^(-{3,}|\*{3,})$/.test(next)) break
      if (i + 1 < lines.length && lines[i].includes('|') && isTableSeparator(lines[i + 1])) break
      paragraph.push(next)
      i += 1
    }
    nodes.push(<p key={`p-${i}`}>{inlineNodes(paragraph.join(' '))}</p>)
  }

  return <div className="tunot-markdown">{nodes}</div>
}
