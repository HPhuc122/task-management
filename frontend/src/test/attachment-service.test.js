import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../services/api'
import { attachmentService } from '../services/resources'
import { clearSession, setSession } from '../services/session'
import { attachmentError } from '../lib/attachments'

const initialAdapter = api.defaults.adapter
afterEach(() => { api.defaults.adapter = initialAdapter; clearSession() })

describe('Attachment REST API contract', () => {
  it('uses Bearer JWT, multipart file field and a 60-second upload timeout', async () => {
    setSession('attachment-test-token', 3600)
    const file = new File(['zip'], 'Tệp có dấu.zip', { type: 'application/zip' })
    const controller = new AbortController()
    const metadata = { id: 8, fileName: file.name }
    api.defaults.adapter = vi.fn(async (config) => {
      expect(config.url).toBe('/tasks/15/attachments')
      expect(config.method).toBe('post')
      expect(config.data).toBeInstanceOf(FormData)
      expect(config.data.get('file')).toBe(file)
      expect(config.headers.Authorization).toBe('Bearer attachment-test-token')
      expect(config.timeout).toBe(60000)
      expect(config.signal).toBe(controller.signal)
      return { data: metadata, status: 201, config, headers: {} }
    })
    expect(await attachmentService.upload(15, file, controller.signal)).toEqual(metadata)
  })

  it('fetches binary content via Axios with JWT and an abort signal', async () => {
    setSession('attachment-test-token', 3600)
    const controller = new AbortController()
    const blob = new Blob(['PDF'], { type: 'application/pdf' })
    api.defaults.adapter = async (config) => {
      expect(config.url).toBe('/tasks/15/attachments/8/content')
      expect(config.responseType).toBe('blob')
      expect(config.headers.Authorization).toBe('Bearer attachment-test-token')
      expect(config.timeout).toBe(60000)
      expect(config.signal).toBe(controller.signal)
      return { data: blob, status: 200, config, headers: {} }
    }
    expect(await attachmentService.download(15, 8, controller.signal)).toBe(blob)
  })

  it('lists metadata and deletes by attachment ID', async () => {
    const calls = []
    api.defaults.adapter = async (config) => {
      calls.push([config.method, config.url])
      return { data: config.method === 'get' ? [{ id: 8 }] : '', status: config.method === 'get' ? 200 : 204, config, headers: {} }
    }
    expect(await attachmentService.list(15)).toEqual([{ id: 8 }])
    await attachmentService.remove(15, 8)
    expect(calls).toEqual([['get', '/tasks/15/attachments'], ['delete', '/tasks/15/attachments/8']])
  })

  it('maps connection and timeout failures without displaying exception internals', () => {
    expect(attachmentError({ code: 'ERR_NETWORK', message: 'secret' })).toContain('Kiểm tra kết nối')
    expect(attachmentError({ code: 'ECONNABORTED', message: 'secret' })).toContain('quá lâu')
    expect(attachmentError({ code: 'ETIMEDOUT' })).toContain('quá lâu')
    expect(attachmentError(new Error('secret'))).not.toContain('secret')
  })
})
