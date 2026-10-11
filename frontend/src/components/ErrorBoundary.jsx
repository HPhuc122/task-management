import { Component } from 'react'
export default class ErrorBoundary extends Component {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    if (this.state.failed) return <main className="empty-state"><h1>Giao diện gặp sự cố</h1><p>Vui lòng tải lại trang và đăng nhập lại để tiếp tục.</p><button className="btn btn-primary" onClick={() => window.location.reload()}>Tải lại trang</button></main>
    return this.props.children
  }
}
