import { useState, useRef, useEffect } from 'react'
import './App.css'

function App() {
  const [count, setCount] = useState(0)
  const [api, setApi] = useState('Click below to fetch API')
  const [health, setHealth] = useState('Click below to fetch Health')
  const [seconds, setSeconds] = useState('Click below to connect to Seconds web socket')
  const wsRef = useRef<WebSocket | null>(null)

  // Disconnect specifically the seconds WebSocket and clean up handlers
  const disconnectSeconds = () => {
    if (wsRef.current) {
      wsRef.current.onopen = null
      wsRef.current.onmessage = null
      wsRef.current.onerror = null
      wsRef.current.onclose = null
      wsRef.current.close()
      wsRef.current = null
    }
  }

  // Disconnect all WebSockets
  const disconnectAllWebSockets = () => {
    disconnectSeconds()
  }

  // Cleanup WebSocket on component unmount
  useEffect(() => {
    return () => {
      disconnectAllWebSockets()
    }
  }, [])

  // Handler for WebSocket /ws/seconds
  const connectSeconds = () => {
    disconnectSeconds()

    try {
      setSeconds('Connecting...')
      const ws = new WebSocket('ws://localhost:8080/ws/seconds')
      wsRef.current = ws

      ws.onopen = () => {
        setSeconds('Connected. Waiting for seconds...')
      }

      ws.onmessage = (event: MessageEvent) => {
        setSeconds(event.data)
      }

      ws.onerror = (err) => {
        console.error('WebSocket error:', err)
        setSeconds('Error: Could not reach WebSocket')
      }

      ws.onclose = () => {
        wsRef.current = null
      }
    } catch (err) {
      console.error(err)
      setSeconds('Error: Could not connect to WebSocket')
    }
  }

  // Handler for /api
  const fetchApi = async () => {
    try {
      setApi('Loading...')
      const res = await fetch('/api')
      if (!res.ok) throw new Error(`HTTP error! Status: ${res.status}`)

      const data = await res.text()
      setApi(data)
    } catch (err) {
      console.error(err)
      setApi('Error: Could not reach /api')
    }
  }

  // Handler for /api/health
  const fetchHealth = async () => {
    try {
      setHealth('Loading...')
      const res = await fetch('/api/health')
      if (!res.ok) throw new Error(`HTTP error! Status: ${res.status}`)

      const data = await res.text()
      setHealth(data)
    } catch (err) {
      console.error(err)
      setHealth('Error: Could not reach /api/health')
    }
  }

  const resetButtons = () => {
    disconnectAllWebSockets()
    setCount(0)
    setApi('Click below to fetch API')
    setHealth('Click below to fetch Health')
    setSeconds('Click below to connect to Seconds web socket')
  }

  return (
    <>
      <section id="center">
        <button
          type="button"
          className="counter"
          onClick={() => setCount((count) => count + 1)}
        >
          Count is {count}
        </button>

        <button
          type="button"
          className="apiButton"
          onClick={fetchApi}
        >
          API: {api}
        </button>

        <button
          type="button"
          className="healthButton"
          onClick={fetchHealth}
        >
          Health: {health}
        </button>

        <button
          type="button"
          className="secondsButton"
          onClick={connectSeconds}
        >
          Connection Time: {seconds}
        </button>

        <button
          type="button"
          className="resetButton"
          onClick={resetButtons}
        >
          Reset Buttons
        </button>
      </section>
    </>
  )
}

export default App
