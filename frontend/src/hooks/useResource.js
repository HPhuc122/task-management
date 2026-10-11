import { useCallback, useEffect, useRef, useState } from 'react'
import { isCanceled } from '../services/api'
// Memoize loader at the call site. Obsolete responses never overwrite new data.
export default function useResource(loader) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const [version, setVersion] = useState(0)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    const controller = new AbortController()
    Promise.resolve().then(() => {
      if (controller.signal.aborted) return
      setState({ data: null, loading: true, error: null })
      return loader(controller.signal)
    }).then((data) => {
      if (!controller.signal.aborted) setState({ data, loading: false, error: null })
    }).catch((error) => {
      if (!controller.signal.aborted && !isCanceled(error)) setState({ data: null, loading: false, error })
    })
    return () => { mounted.current = false; controller.abort() }
  }, [loader, version])
  const reload = useCallback(() => { if (mounted.current) setVersion((v) => v + 1) }, [])
  return { ...state, reload }
}
