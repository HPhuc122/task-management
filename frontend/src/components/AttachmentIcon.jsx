import { File, FileArchive, FileText, Image } from 'lucide-react'
import { attachmentFormat } from '../lib/attachments'

export default function AttachmentIcon({ file, size = 20 }) {
  const { kind } = attachmentFormat(file)
  const Icon = kind === 'image' ? Image : kind === 'zip' ? FileArchive : ['pdf', 'docx'].includes(kind) ? FileText : File
  return <span className={`attachment-icon attachment-icon-${kind}`}><Icon size={size} aria-hidden="true" /></span>
}
